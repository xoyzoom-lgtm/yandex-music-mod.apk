/*
 * YM Mod — скрипт, внедряемый в веб-версию Яндекс Музыки.
 *
 * Задачи:
 *  1. Следить за состоянием плеера (трек, исполнитель, обложка, позиция) и
 *     отправлять его в приложение через WebViewCompat.addWebMessageListener.
 *  2. Управлять плеером из уведомления и с гарнитуры (play/pause/next/prev).
 *  3. Подменять шрифт страницы.
 *
 * Идея чтения состояния плеера взята из src/mod/features/utils/player.ts проекта
 * Vzlomhik2005/Yandex-Music-Mod (MIT). Там используется React Fiber и десктопная
 * разметка; здесь вместо этого используется стандартный Media Session API и
 * HTMLMediaElement, которые одинаково работают в мобильной и десктопной вёрстке.
 *
 * Скрипт ничего не меняет в запросах к API и не трогает ограничения подписки.
 */
(function () {
  "use strict";

  if (window.__ymmod) return;

  var BRIDGE_NAME = "YmModAndroid";
  var POLL_INTERVAL_MS = 1000;
  var POSITION_DRIFT_MS = 2000;
  var FONT_LINK_ID = "ymmod-font-link";
  var FONT_STYLE_ID = "ymmod-font-style";

  var actionHandlers = {};
  var currentElement = null;
  var lastSent = null;
  var checkScheduled = false;
  var font = null;
  var fontSheet = null;

  function send(type, payload) {
    var bridge = window[BRIDGE_NAME];
    if (!bridge || typeof bridge.postMessage !== "function") return;
    try {
      bridge.postMessage(JSON.stringify({ type: type, payload: payload }));
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

  function snapshot() {
    var metadata = session ? session.metadata : null;
    var element = findMediaElement();
    var hasPosition = !!element && isFinite(element.currentTime);
    var playing = element
      ? !element.paused && !element.ended
      : !!session && session.playbackState === "playing";

    return {
      title: metadata && metadata.title ? String(metadata.title) : "",
      artist: metadata && metadata.artist ? String(metadata.artist) : "",
      album: metadata && metadata.album ? String(metadata.album) : "",
      artworkUrl: metadata ? pickArtwork(metadata.artwork) : "",
      playing: playing,
      hasPosition: hasPosition,
      positionMs: hasPosition ? Math.round(element.currentTime * 1000) : 0,
      durationMs: element && isFinite(element.duration) ? Math.round(element.duration * 1000) : 0,
    };
  }

  function sameTrackState(a, b) {
    return (
      a.title === b.title &&
      a.artist === b.artist &&
      a.album === b.album &&
      a.artworkUrl === b.artworkUrl &&
      a.playing === b.playing &&
      a.durationMs === b.durationMs
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
  }, POLL_INTERVAL_MS);

  // --- Управление -----------------------------------------------------------

  function clickFirst(testIds) {
    for (var i = 0; i < testIds.length; i++) {
      var button = document.querySelector('[data-test-id="' + testIds[i] + '"]');
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
        return clickFirst(["NEXT_TRACK_BUTTON", "NEXT_BUTTON"]);
      case "previoustrack":
        return clickFirst(["PREV_TRACK_BUTTON", "PREVIOUS_TRACK_BUTTON", "PREV_BUTTON"]);
      default:
        return false;
    }
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

  Object.defineProperty(window, "__ymmod", {
    configurable: false,
    enumerable: false,
    writable: false,
    value: Object.freeze({
      version: 1,
      control: control,
      setFont: setFont,
      refresh: function () {
        check(true);
      },
    }),
  });
})();
