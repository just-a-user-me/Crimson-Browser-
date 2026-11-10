// Talks to the Crimson app: pulls settings, reports videos found on pages.
const NATIVE = "crimsonNative";
let lastSettings = "";

async function pollSettings() {
  try {
    const reply = await browser.runtime.sendNativeMessage(NATIVE, { type: "settings" });
    if (reply && typeof reply === "object") {
      const text = JSON.stringify(reply);
      if (text !== lastSettings) {
        lastSettings = text;
        await browser.storage.local.set(reply);
      }
    }
  } catch (e) {
    // The app is not ready yet. Try again on the next tick.
  }
}

pollSettings();
setInterval(pollSettings, 1500);

function report(page, url, mime, size) {
  if (!page || !url) return;
  browser.runtime
    .sendNativeMessage(NATIVE, { type: "media", page: page, url: url, mime: mime || "", size: size || 0 })
    .catch(() => {});
}

function looksLikeMedia(url, type) {
  const clean = url.toLowerCase().split("?")[0];
  if (type.startsWith("video/")) return true;
  if (type.includes("mpegurl") || type.includes("dash+xml")) return true;
  return /\.(mp4|webm|m3u8|mpd|m4v|mov|mkv)$/.test(clean);
}

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    let type = "";
    let length = 0;
    for (const header of details.responseHeaders || []) {
      const name = header.name.toLowerCase();
      if (name === "content-type") type = (header.value || "").toLowerCase();
      else if (name === "content-length") length = parseInt(header.value, 10) || 0;
    }
    if (!looksLikeMedia(details.url, type)) return;
    if (type.startsWith("audio/") && length > 0 && length < 200000) return;

    let page = details.documentUrl || details.originUrl || "";
    if (details.frameAncestors && details.frameAncestors.length > 0) {
      page = details.frameAncestors[details.frameAncestors.length - 1].url || page;
    }
    report(page, details.url, type, length);
  },
  { urls: ["<all_urls>"], types: ["media", "xmlhttprequest", "other"] },
  ["responseHeaders"]
);

browser.runtime.onMessage.addListener((message, sender) => {
  if (message && message.type === "dom-media") {
    const page = (sender.tab && sender.tab.url) || message.page;
    report(page, message.url, message.mime || "video/mp4", 0);
  }
});
