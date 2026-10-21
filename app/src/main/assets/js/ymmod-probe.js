/*
 * YM Mod — диагностика страницы. Внедряется всегда, даже в режиме «без мода»,
 * и ничего не меняет на странице: только запоминает ошибки и по запросу
 * приложения (evaluateJavascript) описывает, что сейчас на экране.
 */
(function () {
  "use strict";

  if (window.__ymmodProbe) return;

  var MAX_ERRORS = 15;
  var errors = [];

  function remember(text) {
    if (errors.length >= MAX_ERRORS) return;
    errors.push(String(text).slice(0, 300));
  }

  window.addEventListener(
    "error",
    function (event) {
      var target = event.target;
      // Ошибки загрузки <script>/<link>/<img> не всплывают, но видны в фазе перехвата.
      if (target && target !== window && (target.src || target.href)) {
        remember("load: " + (target.tagName || "?").toLowerCase() + " " + (target.src || target.href));
        return;
      }
      remember("js: " + (event.message || "error") + (event.filename ? " @ " + event.filename + ":" + event.lineno : ""));
    },
    true,
  );

  window.addEventListener("unhandledrejection", function (event) {
    var reason = event.reason;
    remember("promise: " + (reason && reason.message ? reason.message : String(reason)));
  });

  function visibleText() {
    var body = document.body;
    if (!body) return "";
    return (body.innerText || "").replace(/\s+/g, " ").trim();
  }

  Object.defineProperty(window, "__ymmodProbe", {
    configurable: false,
    enumerable: false,
    writable: false,
    value: function () {
      var text = visibleText();
      var body = document.body;
      return JSON.stringify({
        url: location.href,
        title: document.title,
        readyState: document.readyState,
        elements: document.getElementsByTagName("*").length,
        textLength: text.length,
        text: text.slice(0, 200),
        loader: !!document.querySelector('[class*="MainSuspenseLoader"]'),
        background: body ? getComputedStyle(body).backgroundColor : "",
        width: window.innerWidth,
        height: window.innerHeight,
        modLoaded: !!window.__ymmod,
        errors: errors.slice(),
      });
    },
  });
})();
