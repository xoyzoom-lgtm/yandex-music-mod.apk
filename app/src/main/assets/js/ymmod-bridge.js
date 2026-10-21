/*
 * YM Mod — скрипт, внедряемый в веб-версию Яндекс Музыки.
 *
 * Задачи:
 *  1. Следить за состоянием плеера (трек, исполнитель, обложка, позиция) и
 *     отправлять его в приложение через WebViewCompat.addWebMessageListener.
 *  2. Управлять плеером из уведомления и с гарнитуры (play/pause/next/prev/like).
 *  3. Функции мода: шрифт, «высокое качество» по умолчанию, скрытие окна
 *     «Скачайте приложение», снег, пункт «YM Mod» в настройках сайта,
 *     перенос лайков из плейлиста, снимок разметки для отладки.
 *
 * Идеи взяты из Vzlomhik2005/Yandex-Music-Mod (MIT): src/mod/features/utils/player.ts,
 * font-changer, auto-best-quality, auto-liker. Там используется React Fiber и
 * десктопная разметка; здесь — Media Session API, HTMLMediaElement и data-test-id,
 * которые есть и в мобильной вёрстке.
 *
 * Скрипт не меняет ответы API и не трогает ограничения подписки: качество звука,
 * реклама и доступ к трекам по-прежнему определяет Яндекс.
 */
(function () {
  "use strict";

  if (window.__ymmod) return;

  var BRIDGE_NAME = "YmModAndroid";
  var POLL_INTERVAL_MS = 1000;
  var POSITION_DRIFT_MS = 2000;
  var FONT_LINK_ID = "ymmod-font-link";
  var FONT_STYLE_ID = "ymmod-font-style";
  var SETTINGS_ITEM_ID = "ymmod-settings-item";
  var SNOW_ID = "ymmod-snow";
  var API_BASE = "https://api.music.yandex.net";
  var LIKES_BATCH = 50;

  var config = readInitialConfig();
  var actionHandlers = {};
  var currentElement = null;
  var lastSent = null;
  var lastTheme = null;
  var checkScheduled = false;
  var domScheduled = false;
  var font = null;
  var fontSheet = null;
  var likesJob = null;

  function readInitialConfig() {
    var initial = window.__ymmodConfig || {};
    return {
      autoBestQuality: initial.autoBestQuality !== false,
      hideAppPromo: initial.hideAppPromo !== false,
      snow: initial.snow === true,
    };
  }

  function send(type, payload) {
    var bridge = window[BRIDGE_NAME];
    if (!bridge || typeof bridge.postMessage !== "function") return;
    try {
      bridge.postMessage(JSON.stringify({ type: type, payload: payload || {} }));
    } catch (e) {
      // Мост недоступен (например, страница выгружается) — просто пропускаем.
    }
  }

  function scheduleCheck() {
    if (checkScheduled) return;
    checkScheduled = true;
    setTimeout(function () {
      checkScheduled = false;
      check(false);
    }, 50);
  }

  // --- Хранилище сайта -------------------------------------------------------
  // Сайт хранит настройки в localStorage как {"value": ..., "expires": ...},
  // а в sessionStorage — как {"value": ...}.

  function yearFromNow() {
    return new Date(Date.now() + 365 * 24 * 3600 * 1000).toISOString();
  }

  function readStored(storage, key) {
    try {
      var raw = window[storage].getItem(key);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" && "value" in parsed ? parsed.value : null;
    } catch (e) {
      return null;
    }
  }

  function writeStored(storage, key, value) {
    try {
      var data = { value: value };
      if (storage === "localStorage") data.expires = yearFromNow();
      window[storage].setItem(key, JSON.stringify(data));
    } catch (e) {
      // Хранилище недоступно — функция просто не сработает.
    }
  }

  function readCookie(name) {
    var parts = document.cookie ? document.cookie.split("; ") : [];
    for (var i = 0; i < parts.length; i++) {
      var eq = parts[i].indexOf("=");
      if (eq > 0 && decodeURIComponent(parts[i].slice(0, eq)) === name) return parts[i].slice(eq + 1);
    }
    return null;
  }

  /**
   * «Высокое качество по умолчанию» (auto-best-quality оригинала). Это та же настройка,
   * что и в меню качества плеера; для аккаунтов без подписки сайт всё равно выберет
   * доступное качество сам.
   */
  function applyQualityPreference() {
    if (!config.autoBestQuality) return;
    var key = "ymPlayerQuality";
    var target = "high_quality";
    if (readStored("localStorage", key) !== target) writeStored("localStorage", key, target);

    var cookie = readCookie(key);
    var cookieValue = null;
    try {
      cookieValue = cookie ? JSON.parse(decodeURIComponent(cookie)).value : null;
    } catch (e) {
      cookieValue = null;
    }
    if (cookieValue !== target) {
      document.cookie =
        key + "=" + encodeURIComponent(JSON.stringify({ value: target })) + "; path=/; max-age=31536000; secure";
    }
  }

  /** Сайт показывает окно «Скачайте приложение» один раз за сессию — помечаем его как показанное. */
  function suppressAppPromo() {
    if (!config.hideAppPromo) return;
    writeStored("sessionStorage", "downloadMobileApp", { shown: true });
    writeStored("localStorage", "downloadMobileApp", { shown: true });
  }

  applyQualityPreference();
  suppressAppPromo();

  // --- Media Session --------------------------------------------------------

  var session = navigator.mediaSession;

  // Запоминаем обработчики, которые сайт регистрирует для системных кнопок,
  // чтобы вызывать их из уведомления приложения.
  if (session && typeof session.setActionHandler === "function") {
    var originalSetActionHandler = session.setActionHandler;
    session.setActionHandler = function (action, handler) {
      actionHandlers[action] = handler;
      return originalSetActionHandler.call(session, action, handler);
    };
  }

  // Узнаём о смене трека сразу, а не по таймеру.
  function hookSetter(proto, name) {
    if (!proto) return;
    var descriptor = Object.getOwnPropertyDescriptor(proto, name);
    if (!descriptor || !descriptor.set || !descriptor.configurable) return;
    Object.defineProperty(proto, name, {
      configurable: true,
      enumerable: descriptor.enumerable,
      get: descriptor.get,
      set: function (value) {
        descriptor.set.call(this, value);
        scheduleCheck();
      },
    });
  }

  if (window.MediaSession) {
    hookSetter(window.MediaSession.prototype, "metadata");
    hookSetter(window.MediaSession.prototype, "playbackState");
  }

  // --- Медиа-элементы -------------------------------------------------------

  var MEDIA_EVENTS = ["play", "playing", "pause", "ended", "loadedmetadata", "durationchange", "seeked", "emptied"];

  function track(element) {
    if (!element) return;
    currentElement = element;
    if (element.__ymmodTracked) return;
    element.__ymmodTracked = true;
    MEDIA_EVENTS.forEach(function (name) {
      element.addEventListener(name, function () {
        if (name === "play" || name === "playing") currentElement = element;
        scheduleCheck();
      });
    });
  }

  // Плеер может создавать Audio() без добавления в DOM — ловим его в момент play().
  var originalPlay = HTMLMediaElement.prototype.play;
  HTMLMediaElement.prototype.play = function () {
    track(this);
    return originalPlay.apply(this, arguments);
  };

  // Медиа-события не всплывают, но проходят фазу перехвата на document.
  document.addEventListener(
    "play",
    function (event) {
      if (event.target instanceof HTMLMediaElement) track(event.target);
    },
    true,
  );

  function findMediaElement() {
    if (currentElement) return currentElement;
    var elements = document.querySelectorAll("audio, video");
    for (var i = 0; i < elements.length; i++) {
      if (!elements[i].paused) return elements[i];
    }
    return elements.length > 0 ? elements[0] : null;
  }

  // --- Снимок состояния -----------------------------------------------------

  function pickArtwork(artwork) {
    if (!artwork || !artwork.length) return "";
    var best = artwork[0];
    var bestSize = 0;
    for (var i = 0; i < artwork.length; i++) {
      var sizes = String(artwork[i].sizes || "");
      var match = /(\d+)x(\d+)/.exec(sizes);
      var size = match ? parseInt(match[1], 10) : 0;
      if (size > bestSize) {
        best = artwork[i];
        bestSize = size;
      }
    }
    return best && best.src ? String(best.src) : "";
  }

  var PLAYER_ROOTS = [
    '[data-test-id="MOBILE_FULLSCREEN_PLAYER_MODAL"]',
    '[data-test-id="MOBILE_PLAYERBAR"]',
    '[data-test-id="FULLSCREEN_PLAYER_MODAL"]',
    '[data-test-id="PLAYERBAR_DESKTOP"]',
    '[data-test-id="VIBE_PLAYERBAR"]',
  ];

  function findInPlayer(testId) {
    for (var i = 0; i < PLAYER_ROOTS.length; i++) {
      var root = document.querySelector(PLAYER_ROOTS[i]);
      var button = root && root.querySelector('[data-test-id="' + testId + '"]');
      if (button) return button;
    }
    return null;
  }

  /** true/false — лайк стоит/нет, null — неизвестно (кнопки нет или у неё нет aria-pressed). */
  function likedState() {
    var button = findInPlayer("LIKE_BUTTON");
    if (!button) return null;
    var pressed = button.getAttribute("aria-pressed");
    if (pressed === "true") return true;
    if (pressed === "false") return false;
    return null;
  }

  function snapshot() {
    var metadata = session ? session.metadata : null;
    var element = findMediaElement();
    var hasPosition = !!element && isFinite(element.currentTime);
    var playing = element
      ? !element.paused && !element.ended
      : !!session && session.playbackState === "playing";
    var liked = likedState();

    return {
      title: metadata && metadata.title ? String(metadata.title) : "",
      artist: metadata && metadata.artist ? String(metadata.artist) : "",
      album: metadata && metadata.album ? String(metadata.album) : "",
      artworkUrl: metadata ? pickArtwork(metadata.artwork) : "",
      playing: playing,
      hasPosition: hasPosition,
      positionMs: hasPosition ? Math.round(element.currentTime * 1000) : 0,
      durationMs: element && isFinite(element.duration) ? Math.round(element.duration * 1000) : 0,
      liked: liked === null ? "unknown" : liked ? "yes" : "no",
    };
  }

  function sameTrackState(a, b) {
    return (
      a.title === b.title &&
      a.artist === b.artist &&
      a.album === b.album &&
      a.artworkUrl === b.artworkUrl &&
      a.playing === b.playing &&
      a.durationMs === b.durationMs &&
      a.liked === b.liked
    );
  }

  function check(force) {
    var state = snapshot();
    var now = Date.now();

    if (!force && lastSent && sameTrackState(lastSent.state, state)) {
      if (!state.hasPosition) return;
      // Позиция меняется постоянно — отправляем только при перемотке.
      var expected = lastSent.state.positionMs + (lastSent.state.playing ? now - lastSent.at : 0);
      if (Math.abs(expected - state.positionMs) < POSITION_DRIFT_MS) return;
    }

    lastSent = { state: state, at: now };
    send("state", state);
  }

  setInterval(function () {
    check(false);
    reportTheme(false);
  }, POLL_INTERVAL_MS);

  // --- Цвет страницы для системных панелей ------------------------------------

  function reportTheme(force) {
    var target = document.body || document.documentElement;
    if (!target) return;
    var background = getComputedStyle(target).backgroundColor;
    if (!background || background === "rgba(0, 0, 0, 0)" || background === "transparent") {
      background = getComputedStyle(document.documentElement).backgroundColor;
    }
    if (!background || (!force && background === lastTheme)) return;
    lastTheme = background;
    send("theme", { background: background });
  }

  // --- Управление -----------------------------------------------------------

  function clickFirst(testIds) {
    for (var i = 0; i < testIds.length; i++) {
      var button = findInPlayer(testIds[i]) || document.querySelector('[data-test-id="' + testIds[i] + '"]');
      if (button) {
        button.click();
        return true;
      }
    }
    return false;
  }

  function control(action) {
    var element = findMediaElement();
    if (action === "toggle") {
      action = element && !element.paused ? "pause" : "play";
    }

    if (action === "like") {
      var like = findInPlayer("LIKE_BUTTON");
      if (!like) return false;
      like.click();
      setTimeout(scheduleCheck, 300);
      return true;
    }

    var handler = actionHandlers[action];
    if (typeof handler === "function") {
      try {
        handler({ action: action });
        return true;
      } catch (e) {
        // Пробуем запасные способы ниже.
      }
    }

    switch (action) {
      case "play":
        if (clickFirst(["PLAY_BUTTON"])) return true;
        if (element) {
          var promise = element.play();
          if (promise && typeof promise.catch === "function") promise.catch(function () {});
          return true;
        }
        return false;
      case "pause":
        if (clickFirst(["PAUSE_BUTTON"])) return true;
        if (element) {
          element.pause();
          return true;
        }
        return false;
      case "nexttrack":
        return clickFirst(["MOBILE_NEXT_TRACK_BUTTON", "NEXT_TRACK_BUTTON"]);
      case "previoustrack":
        return clickFirst(["MOBILE_PREVIOUS_TRACK_BUTTON", "PREVIOUS_TRACK_BUTTON"]);
      default:
        return false;
    }
  }

  function seek(positionMs) {
    var handler = actionHandlers.seekto;
    if (typeof handler === "function") {
      try {
        handler({ action: "seekto", seekTime: positionMs / 1000 });
        return true;
      } catch (e) {
        // Пробуем напрямую.
      }
    }
    var element = findMediaElement();
    if (!element) return false;
    element.currentTime = positionMs / 1000;
    return true;
  }

  // --- Шрифты (аналог src/mod/features/font-changer оригинального проекта) --

  function removeFont() {
    var link = document.getElementById(FONT_LINK_ID);
    if (link) link.remove();
    var style = document.getElementById(FONT_STYLE_ID);
    if (style) style.remove();
    if (fontSheet && document.adoptedStyleSheets) {
      document.adoptedStyleSheets = document.adoptedStyleSheets.filter(function (sheet) {
        return sheet !== fontSheet;
      });
    }
    fontSheet = null;
  }

  function applyFont() {
    removeFont();
    if (!font || !font.family) return;

    var root = document.head || document.documentElement;
    if (!root) return;

    if (font.stylesheetUrl) {
      var link = document.createElement("link");
      link.id = FONT_LINK_ID;
      link.rel = "stylesheet";
      link.href = font.stylesheetUrl;
      root.appendChild(link);
    }

    var css = "* { font-family: " + font.family + " !important; }";

    // Constructable stylesheet не блокируется CSP так, как inline <style>.
    try {
      var sheet = new CSSStyleSheet();
      sheet.replaceSync(css);
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
      fontSheet = sheet;
      return;
    } catch (e) {
      // Старый WebView — используем обычный <style>.
    }

    var style = document.createElement("style");
    style.id = FONT_STYLE_ID;
    style.textContent = css;
    root.appendChild(style);
  }

  function setFont(family, stylesheetUrl) {
    font = family ? { family: String(family), stylesheetUrl: stylesheetUrl ? String(stylesheetUrl) : null } : null;
    applyFont();
  }

  // --- Изменения DOM: пункт меню мода и окно «Скачайте приложение» ----------

  function scheduleDomWork() {
    if (domScheduled) return;
    domScheduled = true;
    setTimeout(function () {
      domScheduled = false;
      injectSettingsItem();
      dismissAppPromo();
    }, 200);
  }

  function startObserver() {
    if (!document.documentElement) return;
    new MutationObserver(scheduleDomWork).observe(document.documentElement, { childList: true, subtree: true });
    scheduleDomWork();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", startObserver);
  } else {
    startObserver();
  }

  /** Если окно всё-таки открылось (например, другая вкладка уже показала его), закрываем кнопкой «Остаться». */
  function dismissAppPromo() {
    if (!config.hideAppPromo) return;
    var button = document.querySelector(
      '[class*="DownloadMobileApp_stayButton"], [class*="DownloadMobileApp_closeButton"]',
    );
    if (button && !button.__ymmodClicked) {
      button.__ymmodClicked = true;
      button.click();
    }
  }

  function createSettingsItem() {
    var item = document.createElement("button");
    item.id = SETTINGS_ITEM_ID;
    item.type = "button";
    item.textContent = "YM Mod — настройки мода";
    item.setAttribute(
      "style",
      [
        "display:flex",
        "align-items:center",
        "justify-content:center",
        "width:calc(100% - 16px)",
        "margin:8px",
        "padding:12px 16px",
        "border-radius:999px",
        "border:2px solid var(--ym-outline-color-primary-disabled, rgba(127,127,127,.4))",
        "background:transparent",
        "color:var(--ym-controls-color-primary-text-enabled_variant, inherit)",
        "font:inherit",
        "font-weight:600",
        "cursor:pointer",
      ].join(";"),
    );
    item.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      send("openModSettings");
    });
    return item;
  }

  /** Добавляет кнопку меню мода в меню настроек сайта (как «Меню мода» в оригинале). */
  function injectSettingsItem() {
    var lists = document.querySelectorAll('[class*="Settings_list__"], [class*="Settings_menuContent__"]');
    var target = lists.length > 0 ? lists[0] : null;
    if (!target && /^\/settings(\/|$)/.test(location.pathname)) target = document.querySelector("main");
    var existing = document.getElementById(SETTINGS_ITEM_ID);
    if (!target) {
      if (existing) existing.remove();
      return;
    }
    if (existing && target.contains(existing)) return;
    if (existing) existing.remove();
    target.insertBefore(createSettingsItem(), target.firstChild);
  }

  // --- Снег (как в релизах оригинала) ---------------------------------------

  var snowState = null;

  function startSnow() {
    if (snowState || !document.body) return;
    var canvas = document.createElement("canvas");
    canvas.id = SNOW_ID;
    canvas.setAttribute(
      "style",
      "position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:2147483646",
    );
    document.body.appendChild(canvas);
    var context = canvas.getContext("2d");
    var flakes = [];
    var ratio = window.devicePixelRatio || 1;

    function resize() {
      canvas.width = window.innerWidth * ratio;
      canvas.height = window.innerHeight * ratio;
    }
    resize();
    window.addEventListener("resize", resize);

    for (var i = 0; i < 70; i++) {
      flakes.push({
        x: Math.random(),
        y: Math.random(),
        r: 1 + Math.random() * 2.5,
        speed: 0.0008 + Math.random() * 0.0015,
        drift: Math.random() * Math.PI * 2,
      });
    }

    snowState = { canvas: canvas, frame: 0, resize: resize };

    function draw() {
      if (!snowState) return;
      snowState.frame = requestAnimationFrame(draw);
      if (document.hidden) return;
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.fillStyle = "rgba(255,255,255,0.8)";
      for (var j = 0; j < flakes.length; j++) {
        var flake = flakes[j];
        flake.y += flake.speed;
        flake.drift += 0.01;
        if (flake.y > 1.02) {
          flake.y = -0.02;
          flake.x = Math.random();
        }
        context.beginPath();
        context.arc(
          (flake.x + Math.sin(flake.drift) * 0.01) * canvas.width,
          flake.y * canvas.height,
          flake.r * ratio,
          0,
          Math.PI * 2,
        );
        context.fill();
      }
    }
    draw();
  }

  function stopSnow() {
    if (!snowState) return;
    cancelAnimationFrame(snowState.frame);
    window.removeEventListener("resize", snowState.resize);
    snowState.canvas.remove();
    snowState = null;
  }

  function applySnow() {
    if (config.snow) {
      if (document.body) startSnow();
      else document.addEventListener("DOMContentLoaded", startSnow);
    } else {
      stopSnow();
    }
  }

  applySnow();

  function configure(next) {
    if (!next || typeof next !== "object") return;
    config = {
      autoBestQuality: next.autoBestQuality !== false,
      hideAppPromo: next.hideAppPromo !== false,
      snow: next.snow === true,
    };
    applyQualityPreference();
    suppressAppPromo();
    applySnow();
    scheduleDomWork();
  }

  // --- API Яндекс Музыки от имени вошедшего пользователя (auto-liker оригинала) ---

  function oauthToken() {
    try {
      var stored = JSON.parse(localStorage.getItem("oauth") || "null");
      return stored && stored.value ? String(stored.value) : null;
    } catch (e) {
      return null;
    }
  }

  function api(method, path, form) {
    var token = oauthToken();
    if (!token) return Promise.reject(new Error("Войдите в аккаунт Яндекса"));
    var init = { method: method, headers: { Authorization: "OAuth " + token } };
    if (form) {
      init.headers["Content-Type"] = "application/x-www-form-urlencoded";
      init.body = Object.keys(form)
        .map(function (key) {
          return encodeURIComponent(key) + "=" + encodeURIComponent(form[key]);
        })
        .join("&");
    }
    return fetch(API_BASE + path, init)
      .then(function (response) {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.json();
      })
      .then(function (data) {
        return data && data.result !== undefined ? data.result : data;
      });
  }

  function playlistIdFromUrl(url) {
    var match = /playlists\/([A-Za-z0-9.\-]+)/.exec(String(url || ""));
    if (match) return match[1];
    var legacy = /users\/([^/]+)\/playlists\/(\d+)/.exec(String(url || ""));
    return legacy ? legacy[1] + ":" + legacy[2] : null;
  }

  function delay(ms) {
    return new Promise(function (resolve) {
      setTimeout(resolve, ms);
    });
  }

  function likesProgress(status, done, total, message) {
    send("likes", { status: status, done: done, total: total, message: message || "" });
  }

  /** Ставит лайк каждому треку плейлиста — для переноса музыки с другого аккаунта. */
  function transferLikes(url) {
    if (likesJob) return;
    var id = playlistIdFromUrl(url);
    if (!id) {
      likesProgress("error", 0, 0, "Не удалось распознать ссылку на плейлист");
      return;
    }

    var job = { stopped: false };
    likesJob = job;
    likesProgress("progress", 0, 0, "Получение плейлиста");

    var playlistPath = id.indexOf(":") > 0
      ? "/users/" + encodeURIComponent(id.split(":")[0]) + "/playlists/" + encodeURIComponent(id.split(":")[1])
      : "/playlist/" + encodeURIComponent(id) + "?resumeStream=false&richTracks=false";

    Promise.all([api("GET", playlistPath), api("GET", "/account/status")])
      .then(function (results) {
        var playlist = results[0] || {};
        var status = results[1] || {};
        var uid = (status.account && status.account.uid) || status.uid;
        if (!uid) throw new Error("Не удалось получить ID аккаунта");
        var tracks = (playlist.tracks || [])
          .map(function (item) {
            var full = item.track || {};
            var trackId = item.id || full.id;
            var album = item.albumId || (full.albums && full.albums[0] && full.albums[0].id);
            if (!trackId) return null;
            return album ? trackId + ":" + album : String(trackId);
          })
          .filter(Boolean);
        if (tracks.length === 0) throw new Error("В плейлисте нет треков");

        // Как users_likes_tracks_add в yandex-music-api: пачками, с паузой между запросами.
        var index = 0;
        var errors = 0;
        function next() {
          if (job.stopped) return likesProgress("stopped", index, tracks.length, "Остановлено");
          if (index >= tracks.length) {
            return likesProgress("done", index, tracks.length, errors > 0 ? "Готово, ошибок: " + errors : "Готово");
          }
          likesProgress("progress", index, tracks.length, "Добавление треков");
          var batch = tracks.slice(index, index + LIKES_BATCH);
          return api("POST", "/users/" + encodeURIComponent(uid) + "/likes/tracks/add-multiple", {
            "track-ids": batch.join(","),
          })
            .catch(function () {
              errors += batch.length;
            })
            .then(function () {
              index += batch.length;
              return delay(500);
            })
            .then(next);
        }
        return next();
      })
      .catch(function (error) {
        likesProgress("error", 0, 0, error && error.message ? error.message : String(error));
      })
      .then(function () {
        if (likesJob === job) likesJob = null;
      });
  }

  function stopLikes() {
    if (likesJob) likesJob.stopped = true;
  }

  /** Делает «Мне нравится» публичной и возвращает ссылку на неё (как в оригинале). */
  function collectionLink() {
    Promise.all([
      api("GET", "/landing-blocks/likes-and-history"),
      api("POST", "/account/settings?userMusicVisibility=PUBLIC"),
    ])
      .then(function (results) {
        var uuid = results[0] && results[0].favorites && results[0].favorites.playlistUuid;
        if (!uuid) throw new Error("Не удалось получить плейлист «Мне нравится»");
        send("collectionLink", { url: location.origin + "/playlists/" + uuid });
      })
      .catch(function (error) {
        send("collectionLink", { error: error && error.message ? error.message : String(error) });
      });
  }

  // --- Снимок разметки для отладки -------------------------------------------

  /**
   * HTML текущего экрана без скриптов, значений полей и ссылок на медиа — чтобы
   * разработчик мог подогнать селекторы под мобильную вёрстку, не видя сайт.
   */
  function pageSnapshot() {
    var clone = document.documentElement.cloneNode(true);
    var removable = clone.querySelectorAll("script, noscript, iframe, canvas, svg defs, link[rel=preload]");
    for (var i = 0; i < removable.length; i++) removable[i].remove();
    var inputs = clone.querySelectorAll("input, textarea");
    for (var j = 0; j < inputs.length; j++) {
      inputs[j].removeAttribute("value");
      inputs[j].textContent = "";
    }
    var media = clone.querySelectorAll("audio, video, source");
    for (var k = 0; k < media.length; k++) media[k].removeAttribute("src");
    send("snapshot", {
      url: location.pathname,
      width: window.innerWidth,
      height: window.innerHeight,
      html: "<!doctype html>\n" + clone.outerHTML,
    });
  }

  Object.defineProperty(window, "__ymmod", {
    configurable: false,
    enumerable: false,
    writable: false,
    value: Object.freeze({
      version: 2,
      control: control,
      seek: seek,
      setFont: setFont,
      configure: configure,
      transferLikes: transferLikes,
      stopLikes: stopLikes,
      collectionLink: collectionLink,
      pageSnapshot: pageSnapshot,
      refresh: function () {
        check(true);
        reportTheme(true);
        scheduleDomWork();
      },
    }),
  });
})();
