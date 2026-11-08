// Crimson Tools content script: dark mode for websites and in-page developer tools.
(function () {
  "use strict";

  const STYLE_ID = "crimson-force-dark-style";
  const DARK_CLASS = "crimson-dark";
  const isTop = window === window.top;

  const CSS =
    "html." + DARK_CLASS + "{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important;}" +
    "html." + DARK_CLASS + " img,html." + DARK_CLASS + " video,html." + DARK_CLASS + " picture," +
    "html." + DARK_CLASS + " canvas,html." + DARK_CLASS + " svg image," +
    "html." + DARK_CLASS + " [style*=\"background-image\"]" +
    "{filter:invert(1) hue-rotate(180deg)!important;}";

  const state = { forceDark: false, devMode: false };
  let erudaInjected = false;

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
    const els = [document.body, document.documentElement];
    for (const el of els) {
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
    if (document.readyState === "loading") {
      root.classList.add(DARK_CLASS);
    } else if (pageIsAlreadyDark()) {
      root.classList.remove(DARK_CLASS);
    } else {
      root.classList.add(DARK_CLASS);
    }
  }

  function addScript(file, onload) {
    const s = document.createElement("script");
    s.src = browser.runtime.getURL(file);
    s.onload = () => {
      s.remove();
      if (onload) onload();
    };
    (document.head || document.documentElement).appendChild(s);
  }

  function applyDev() {
    if (!isTop) return;
    if (state.devMode && !erudaInjected) {
      erudaInjected = true;
      addScript("eruda.js", () => addScript("eruda-init.js"));
    } else if (!state.devMode && erudaInjected) {
      erudaInjected = false;
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

  document.addEventListener("DOMContentLoaded", applyDark);
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
