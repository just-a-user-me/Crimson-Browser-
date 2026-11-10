// Crimson content script: dark mode for websites, developer panel, and video detection.
(function () {
  "use strict";

  const isTop = window === window.top;
  const STYLE_ID = "crimson-force-dark-style";
  const DARK_CLASS = "crimson-dark";

  const CSS =
    "html." + DARK_CLASS + "{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important;}" +
    "html." + DARK_CLASS + " img,html." + DARK_CLASS + " video,html." + DARK_CLASS + " picture," +
    "html." + DARK_CLASS + " canvas,html." + DARK_CLASS + " svg image," +
    "html." + DARK_CLASS + " [style*=\"background-image\"]" +
    "{filter:invert(1) hue-rotate(180deg)!important;}";

  const state = { forceDark: false, devMode: false };
  let erudaOn = false;

  // ---- Videos on the page ----
  const seen = new Set();
  function scanMedia() {
    const urls = [];
    document.querySelectorAll("video, audio").forEach((el) => {
      if (el.currentSrc) urls.push(el.currentSrc);
      else if (el.src) urls.push(el.src);
      el.querySelectorAll("source").forEach((s) => {
        if (s.src) urls.push(s.src);
      });
    });
    urls.forEach((url) => {
      if (!url || url.startsWith("blob:") || url.startsWith("data:") || seen.has(url)) return;
      seen.add(url);
      browser.runtime.sendMessage({ type: "dom-media", page: location.href, url: url }).catch(() => {});
    });
  }

  function startMediaScan() {
    scanMedia();
    new MutationObserver(scanMedia).observe(document.documentElement, { childList: true, subtree: true });
    document.addEventListener("loadeddata", scanMedia, true);
    document.addEventListener("play", scanMedia, true);
  }

  // ---- Dark mode ----
  function ensureStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = CSS;
    (document.head || document.documentElement).appendChild(style);
  }

  function luminance(color) {
    const m = color && color.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(",").map(parseFloat);
    const alpha = p.length > 3 ? p[3] : 1;
    if (alpha < 0.1) return null;
    return (0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]) / 255;
  }

  function pageIsAlreadyDark() {
    for (const el of [document.body, document.documentElement]) {
      if (!el) continue;
      const l = luminance(getComputedStyle(el).backgroundColor);
      if (l !== null) return l < 0.35;
    }
    return false;
  }

  function applyDark() {
    const root = document.documentElement;
    if (!root || !isTop) return;
    if (!state.forceDark) {
      root.classList.remove(DARK_CLASS);
      return;
    }
    ensureStyle();
    if (document.readyState === "loading") root.classList.add(DARK_CLASS);
    else if (pageIsAlreadyDark()) root.classList.remove(DARK_CLASS);
    else root.classList.add(DARK_CLASS);
  }

  // ---- Developer panel ----
  function addScript(file, done) {
    const s = document.createElement("script");
    s.src = browser.runtime.getURL(file);
    s.onload = () => {
      s.remove();
      if (done) done();
    };
    (document.head || document.documentElement).appendChild(s);
  }

  function applyDev() {
    if (!isTop) return;
    if (state.devMode && !erudaOn) {
      erudaOn = true;
      addScript("eruda.js", () => addScript("eruda-init.js"));
    } else if (!state.devMode && erudaOn) {
      erudaOn = false;
      addScript("eruda-destroy.js");
    }
  }

  function applyAll() {
    applyDark();
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", applyDev, { once: true });
    } else {
      applyDev();
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    applyDark();
    startMediaScan();
  });
  window.addEventListener("load", applyDark);

  browser.storage.local.get(["forceDark", "devMode"]).then((r) => {
    state.forceDark = !!r.forceDark;
    state.devMode = !!r.devMode;
    applyAll();
  });

  browser.storage.onChanged.addListener((changes) => {
    if (changes.forceDark) state.forceDark = !!changes.forceDark.newValue;
    if (changes.devMode) state.devMode = !!changes.devMode.newValue;
    applyAll();
  });
})();
