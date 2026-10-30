// Run with: node docs/ui-prototype/check-pairing.cjs
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const source = fs.readFileSync(`${__dirname}/review.js`, "utf8").replace(
  /  render\(\);\s*\}\)\(\);\s*$/,
  "  globalThis.preview = { state, PAGES, DEVICES, TEST_OUTCOMES, render, pairingScanner, pairingConsent, onboardingConsent, pairingSuccess, deviceRows, homeDeviceSheet, handleTestMessage, resetHomeFixture, updatePairingAlias };\n})();"
);

function load(page, search = "") {
  const listeners = {};
  const timers = new Map();
  let timerId = 0;
  const app = { innerHTML: "" };
  const document = {
    body: { dataset: { page } },
    addEventListener(type, listener) { (listeners[type] ||= []).push(listener); },
    querySelectorAll() { return []; },
    getElementById(id) { return id === "app" ? app : { addEventListener() {} }; }
  };
  const context = {
    document, URLSearchParams, location: { search, protocol: "file:" },
    performance: { now: () => 1000 },
    setTimeout(callback) { timers.set(++timerId, callback); return timerId; },
    clearTimeout(id) { timers.delete(id); }
  };
  vm.runInNewContext(source, context);
  return { ...context.preview, document, app, listeners, location: context.location, timers };
}

for (const page of ["onboarding", "home", "pairing", "settings"]) {
  const preview = load(page);
  for (const screen of preview.PAGES[page].screens) {
    preview.state.screen = screen.id;
    if (page === "home") preview.resetHomeFixture(screen.id);
    assert.ok(screen.render("light").includes("<"), `${page}/${screen.id} renders`);
  }
}

const pairing = load("pairing");
assert.match(pairing.pairingConsent(), /data-href="02-home.html\?screen=ready"[^>]*aria-label="返回"/);
assert.match(pairing.pairingConsent(), /data-href="02-home.html\?screen=ready"[^>]*><span>取消/);
assert.match(pairing.pairingConsent({ alias: '"><img src=x>' }), /value="&quot;&gt;&lt;img src=x&gt;"/);
assert.match(load("onboarding").onboardingConsent(), /data-screen="scanner"[^>]*aria-label="返回"/);
assert.match(load("onboarding").pairingScanner(), /data-screen="pair-start"/);
assert.ok(!pairing.PAGES.pairing.screens.some((screen) => screen.id === "login-expired"), "standalone pairing reuses Home for unrecoverable login loss");
assert.ok(pairing.PAGES.home.screens.some((screen) => screen.id === "login-expired"), "Home retains the shared relogin flow");
const removedAuthLink = load("pairing", "?screen=login-expired");
removedAuthLink.render();
assert.equal(removedAuthLink.state.screen, "scanner", "removed review links fall back to the pairing entry");
assert.ok(!removedAuthLink.app.innerHTML.includes('data-review-screen="login-expired"'));
assert.ok(!pairing.PAGES.pairing.screens.some((screen) => ["receiver-limit", "re-pair", "owner-proof", "add-sender", "camera-permission"].includes(screen.id)));

for (const id of ["home", "office"]) {
  const home = load("home", `?screen=already-added&device=${id}&enabled=office`);
  const before = JSON.stringify([home.DEVICES, home.state.toggles, home.state.aliases]);
  const html = home.PAGES.home.screens.find((screen) => screen.id === "already-added").render();
  assert.match(html, new RegExp(`class="device-row is-highlighted"[^>]*data-device="${id}"`));
  assert.equal((html.match(/device-row is-highlighted/g) || []).length, 1);
  assert.match(html, /这台设备已添加/);
  assert.match(html, /刚刚扫描的设备/);
  assert.equal(JSON.stringify([home.DEVICES, home.state.toggles, home.state.aliases]), before, "scan feedback must not mutate pairings or receive preferences");
  home.state.screen = "ready";
  assert.ok(!home.deviceRows().includes("is-highlighted"));
}
const full = load("home", "?screen=full");
const fullHtml = full.PAGES.home.screens.find((screen) => screen.id === "full").render();
assert.match(fullHtml, /已满5台/);
assert.ok(!fullHtml.includes("extended-fab"));

pairing.state.screen = "scanner";
pairing.render();
assert.match(pairing.app.innerHTML, /data-scan-demo="office"/);
const rescan = load("pairing", "?screen=scanner&enabled=office&alias-home=Study");
rescan.render();
const duplicateHref = rescan.app.innerHTML.match(/data-scan-demo="home" href="([^"]+)"/)[1];
const returnedHome = load("home", duplicateHref.slice(duplicateHref.indexOf("?")));
assert.equal(returnedHome.state.toggles.home, false);
assert.equal(returnedHome.state.toggles.office, true);
assert.equal(returnedHome.state.aliases.home, "Study");
for (const screen of ["consent", "scanner", "success", "progress", "progress-key"]) {
  pairing.state.screen = screen;
  pairing.location.href = "";
  pairing.listeners.keydown.forEach((listener) => listener({ key: "Escape", preventDefault() {} }));
  assert.equal(Boolean(pairing.location.href), !screen.startsWith("progress"), `system Back in ${screen}`);
}

const inputs = [{ classList: { toggle() {} }, setAttribute() {}, setCustomValidity(value) { this.error = value; } }];
const confirm = {};
pairing.document.querySelectorAll = (selector) => selector.includes('input[name="alias"]') ? inputs : selector.includes('data-screen="progress"') ? [confirm] : [];
for (const [value, invalid] of [[" ", true], ["办公室电脑", true], ["x".repeat(41), true], ["客厅", false]]) {
  pairing.updatePairingAlias(value);
  assert.equal(confirm.disabled, invalid);
  assert.equal(Boolean(inputs[0].error), invalid);
}

function runTimer(preview) {
  const [id, callback] = preview.timers.entries().next().value;
  preview.timers.delete(id);
  callback();
}

for (const host of ["home", "pairing", "onboarding"]) {
  for (const outcome of Object.keys(pairing.TEST_OUTCOMES).concat("unknown")) {
    const screen = host === "home" ? "device-disabled" : "success";
    const test = load(host, `?screen=${screen}&testResult=${outcome}`);
    const before = JSON.stringify([test.DEVICES, test.state.toggles, test.state.aliases]);
    const event = { target: { closest: () => ({ disabled: false }) }, stopImmediatePropagation() {} };
    test.handleTestMessage(event);
    assert.equal(test.state.testPending, true);
    assert.equal(test.state.screen, screen, "test stays at its entry");
    assert.match(test.app.innerHTML, /data-test-message aria-busy="true" disabled/);
    assert.equal(test.state.testTarget.receiverId, host === "home" ? "receiver-home" : "receiver-new");
    assert.equal(test.state.testTarget.pairingId, host === "home" ? "pairing-home" : "pairing-new");
    test.handleTestMessage(event);
    assert.equal(test.timers.size, 1, "repeat taps do not start another test");
    runTimer(test);
    assert.equal(test.state.testPending, false);
    assert.equal(test.state.screen, screen);
    assert.match(test.app.innerHTML, /role="status"/);
    assert.ok(test.app.innerHTML.includes(test.TEST_OUTCOMES[outcome] || "投递未确认：未知投递结果"));
    const toast = test.app.innerHTML.match(/class="toast-message" role="status">([^<]+)</)[1];
    assert.equal(toast === "测试消息已发送", outcome === "received", "only confirmed arrival is success");
    assert.ok(!test.app.innerHTML.includes("04-delivery"));
    assert.equal(JSON.stringify([test.DEVICES, test.state.toggles, test.state.aliases]), before, "tests preserve pairing and receive settings");
    runTimer(test);
    assert.equal(test.state.testNotice, "", "Toast expires");
  }
}

const navigating = load("home", "?screen=device-sheet");
navigating.handleTestMessage({ target: { closest: () => ({ disabled: false }) }, stopImmediatePropagation() {} });
navigating.state.screen = "ready";
runTimer(navigating);
assert.equal(navigating.state.testNotice, "", "an old entry must not display a stale result");
assert.equal(navigating.state.testPending, false);
assert.match(load("home", "?screen=device-key-error").homeDeviceSheet(), /data-test-message[^>]*disabled/);
assert.ok(!pairing.PAGES.delivery, "dedicated delivery pages are removed");
console.log("Preview checks passed: all screens, pairing, duplicate scans, aliases, capacity, and inline single-target tests with every outcome.");
