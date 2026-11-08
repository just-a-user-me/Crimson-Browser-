// Receives settings from the Crimson app and stores them for the content script.
let port = null;

function connect() {
  try {
    port = browser.runtime.connectNative("crimsonTools");
    port.onMessage.addListener((msg) => {
      if (msg && typeof msg === "object") {
        browser.storage.local.set(msg);
      }
    });
    port.onDisconnect.addListener(() => {
      port = null;
      setTimeout(connect, 2000);
    });
  } catch (e) {
    port = null;
    setTimeout(connect, 2000);
  }
}

connect();
