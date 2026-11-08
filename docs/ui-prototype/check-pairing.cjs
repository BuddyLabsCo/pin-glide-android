// Run with: node docs/ui-prototype/check-pairing.cjs
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const source = fs.readFileSync(`${__dirname}/review.js`, "utf8").replace(
  /  render\(\);\s*\}\)\(\);\s*$/,
  "  globalThis.preview = { state, params, PAGES, DEVICES, SENDERS, TEST_OUTCOMES, render, pairingScanner, pairingConsent, onboardingConsent, pairingSuccess, deviceRows, homeDeviceSheet, handleTestMessage, resetHomeFixture, updatePairingAlias, handleSettingsAction, settingsSecurity, settingsRevokeConfirm, settingsRevoked };\n})();"
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
    for (const theme of ["light", "dark"]) assert.ok(screen.render(theme).includes("<"), `${page}/${screen.id}/${theme} renders`);
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
function settingsClick(preview, dataset) {
  preview.handleSettingsAction({ target: { closest: () => ({ dataset, disabled: false }) } });
}

for (const senderId of ["sender-self", "sender-pixel"]) {
  for (const retryResult of ["revoked", "active", "failed", "missing"]) {
    const preview = load("settings", `?screen=security&sender=${senderId}&retryResult=${retryResult}`);
    const senderName = preview.SENDERS.find((sender) => sender.id === senderId).name;
    settingsClick(preview, { settingsAction: "select", sender: senderId });
    assert.equal(preview.state.screen, "revoke-confirm");
    assert.ok(preview.app.innerHTML.includes(`撤销「${senderName}」`));
    assert.equal(preview.timers.size, 0, "selection and confirmation never submit revocation");
    settingsClick(preview, { screen: "owner-proof" });
    assert.equal(preview.state.screen, "owner-proof");
    settingsClick(preview, { settingsAction: "verify" });
    assert.equal(preview.state.screen, "revoke-submitting");
    settingsClick(preview, { settingsAction: "verify" });
    assert.equal(preview.timers.size, 1, "repeat taps cannot submit twice");
    runTimer(preview);
    assert.equal(preview.state.screen, "logout-unconfirmed");
    assert.ok(!preview.app.innerHTML.includes("本机已退出"), "permanent uncertainty is not ordinary logout");
    preview.params.set("revokeResult", "success");
    settingsClick(preview, { settingsAction: "retry" });
    assert.equal(preview.state.screen, "logout-unconfirmed", "retry stays on the original page");
    assert.ok(preview.state.revokePending);
    assert.match(preview.app.innerHTML, /disabled[^>]*><[^>]*>[\s\S]*?重试中/);
    settingsClick(preview, { settingsAction: "retry" });
    assert.equal(preview.timers.size, 1, "repeat retry taps cannot create another operation");
    assert.equal(preview.state.revokeTrace.filter((step) => step === "提交所选设备撤销").length, 1, "retry reads before submitting");
    runTimer(preview);
    if (retryResult === "active") {
      assert.equal(preview.state.screen, "logout-unconfirmed", "resubmission is also invisible");
      runTimer(preview);
    }
    assert.equal(preview.state.revokeTrace.filter((step) => step === "QQ 账号验证").length, 1, "retry never repeats QQ in the proposed interaction");
    assert.equal(preview.state.revokeTrace.filter((step) => step === "提交所选设备撤销").length, retryResult === "active" ? 2 : 1);
    if (["active", "revoked", "missing"].includes(retryResult)) {
      assert.deepEqual(Array.from(preview.state.revokedSenderIds), [senderId], "only the selected registration changes");
      if (senderId === "sender-self") {
        assert.equal(preview.location.href, "01-onboarding.html?screen=welcome", "confirmed self revocation goes directly to welcome");
        assert.ok(!preview.app.innerHTML.includes(`「${senderName}」已撤销`), "self has no success screen");
        assert.equal(preview.settingsSecurity(), "", "self revocation cannot return to an authenticated list");
      } else {
        assert.equal(preview.state.screen, "revoked");
        assert.ok(preview.app.innerHTML.includes(`「${senderName}」已撤销`));
        assert.equal(preview.location.href, undefined, "other-Sender success does not log out the actor");
        assert.ok(!preview.settingsSecurity().includes('data-sender="sender-pixel"'), "revoked rows disappear");
      }
    } else {
      assert.equal(preview.state.screen, "logout-unconfirmed");
      assert.equal(preview.state.revokedSenderIds.length, 0, "a failed list request never implies success");
    }
  }
}

for (const proofResult of ["cancelled", "failure"]) {
  const preview = load("settings", `?screen=owner-proof&proofResult=${proofResult}`);
  settingsClick(preview, { settingsAction: "verify" });
  assert.equal(preview.state.screen, "owner-proof");
  assert.equal(preview.timers.size, 0, "unsuccessful QQ authorization never submits revocation");
  assert.equal(preview.state.revokedSenderIds.length, 0);
}
const senderCopy = load("settings", "?sender=sender-pixel");
senderCopy.SENDERS[1].name = '<img src=x onerror="bad()">';
assert.match(senderCopy.settingsRevokeConfirm(), /&lt;img/);
const settingsScreenIds = senderCopy.PAGES.settings.screens.map((screen) => screen.id);
assert.ok(settingsScreenIds.indexOf("revoke-confirm") < settingsScreenIds.indexOf("owner-proof"));
assert.ok(settingsScreenIds.indexOf("owner-proof") < settingsScreenIds.indexOf("logout-unconfirmed"));
assert.ok(!settingsScreenIds.includes("logout-pending"), "ordinary logout goes directly to onboarding welcome");
assert.ok(!settingsScreenIds.includes("revoke-checking"), "retry reconciliation has no separate page");
assert.ok(!settingsScreenIds.includes("revoked-self"), "self success has no review page");
assert.ok(settingsScreenIds.includes("owner-proof-cancelled") && settingsScreenIds.includes("owner-proof-failed"), "visible QQ error states have flat review entries");
const logout = load("settings", "?screen=logout-confirm");
logout.render();
assert.match(logout.app.innerHTML, /data-href="01-onboarding.html\?screen=welcome"/);
const inventory = load("settings", "?screen=security");
inventory.render();
assert.ok(!inventory.app.innerHTML.includes("Galaxy S24"), "revoked service fixtures are excluded");
assert.ok(!inventory.app.innerHTML.includes("发送端撤销流程演示"));
assert.ok(!inventory.app.innerHTML.includes("data-settings-fixture"));
assert.match(inventory.settingsSecurity(), /class="danger-button"[^>]*data-sender="sender-self"[^>]*>撤销/);
settingsClick(inventory, { settingsAction: "select", sender: "sender-galaxy" });
assert.equal(inventory.state.screen, "security", "revoked targets cannot be selected");
const unknownResult = load("settings", "?screen=logout-unconfirmed");
unknownResult.render();
assert.match(unknownResult.app.innerHTML, /服务器未返回预期结果/);
assert.ok(!unknownResult.app.innerHTML.includes("重试会先"));
settingsClick(unknownResult, { settingsAction: "retry" });
runTimer(unknownResult);
assert.equal(unknownResult.state.screen, "revoked", "a successful complete active-only list without the selected target confirms revocation");
assert.deepEqual(Array.from(unknownResult.state.revokedSenderIds), ["sender-pixel"]);
const selfSuccess = load("settings", "?screen=owner-proof&sender=sender-self&revokeResult=success");
settingsClick(selfSuccess, { settingsAction: "verify" });
runTimer(selfSuccess);
assert.equal(selfSuccess.location.href, "01-onboarding.html?screen=welcome", "direct self success also goes to welcome");
assert.ok(!selfSuccess.app.innerHTML.includes("已撤销</h2>"));
const selfResultLink = load("settings", "?screen=revoked&sender=sender-self");
assert.equal(selfResultLink.settingsRevoked(), "");
assert.equal(selfResultLink.location.href, "01-onboarding.html?screen=welcome", "self success deep links cannot display the other-Sender result");
const settingsMain = load("settings", "?screen=settings");
settingsMain.render();
assert.match(settingsMain.app.innerHTML, /鹊信需要关闭电池优化才能持续工作/);
assert.match(settingsMain.app.innerHTML, /data-screen="external:battery-optimization"/);
settingsClick(settingsMain, { screen: "external:battery-optimization" });
assert.equal(settingsMain.state.screen, "settings", "system settings do not create an app-owned page");
for (const language of ["system", "zh", "en"]) {
  settingsClick(settingsMain, { settingsAction: "language", language });
  assert.equal(settingsMain.state.screen, "settings");
  assert.equal(settingsMain.params.get("appLanguage"), language);
  settingsMain.state.screen = "language";
  settingsMain.render();
  assert.match(settingsMain.app.innerHTML, new RegExp(`data-language="${language}" role="radio" aria-checked="true"`));
}
const invalidLanguage = settingsMain.state.settingsLanguage;
settingsClick(settingsMain, { settingsAction: "language", language: "<bad>" });
assert.equal(settingsMain.state.settingsLanguage, invalidLanguage);
for (const child of ["language", "account", "privacy", "security", "security-empty", "security-error"]) {
  const preview = load("settings", `?screen=${child}`);
  preview.render();
  assert.match(preview.app.innerHTML, /data-screen="settings"[^>]*aria-label="返回"/, `${child} returns to Settings`);
  for (const handler of preview.listeners.keydown) handler({ key: "Escape", preventDefault() {} });
  assert.equal(preview.state.screen, "settings", `${child} hardware Back returns to Settings`);
}
for (const kind of ["account", "security"]) {
  const preview = load("settings", "?screen=settings");
  settingsClick(preview, { screen: kind });
  assert.equal(preview.state.screen, kind === "account" ? "account-loading" : "security-loading");
  runTimer(preview);
  assert.equal(preview.state.screen, kind);
}
const profileError = load("settings", "?screen=settings&profileResult=failure");
settingsClick(profileError, { screen: "account" });
runTimer(profileError);
assert.equal(profileError.state.screen, "account-error");
assert.match(profileError.app.innerHTML, /role="status">网络错误，请稍后重试/);
assert.ok(!profileError.app.innerHTML.includes("示例昵称"), "failed reads never use a client nickname cache");
assert.match(profileError.app.innerHTML, /data-screen="logout-confirm"/, "profile failure does not block logout");
for (const initial of ["security-error", "security-refresh-error"]) {
  const preview = load("settings", `?screen=${initial}&inventoryResult=failure`);
  preview.render();
  assert.ok(!preview.app.innerHTML.includes("暂无其他发送端设备"), "failed inventory reads are not empty results");
  assert.ok(!preview.app.innerHTML.includes("data-refresh-list"), "Settings has no pull-to-refresh");
  if (initial === "security-refresh-error") assert.match(preview.settingsSecurity(), /data-sender="sender-pixel"[^>]*disabled/);
  settingsClick(preview, { settingsAction: "select", sender: "sender-pixel" });
  assert.equal(preview.state.screen, initial, "stale rows cannot revoke");
  settingsClick(preview, { settingsAction: "reload-senders" });
  assert.equal(preview.state.screen, initial === "security-error" ? "security-loading" : "security-refreshing");
  runTimer(preview);
  assert.equal(preview.state.screen, initial);
  preview.params.delete("inventoryResult");
  settingsClick(preview, { settingsAction: "reload-senders" });
  runTimer(preview);
  assert.equal(preview.state.screen, "security", "failure Retry can restore fresh management");
}
const abandonedRead = load("settings", "?screen=settings");
settingsClick(abandonedRead, { screen: "account" });
settingsClick(abandonedRead, { screen: "settings" });
assert.equal(abandonedRead.timers.size, 0, "leaving a read cancels its callback");
const abandonedRevocation = load("settings", "?screen=owner-proof");
settingsClick(abandonedRevocation, { settingsAction: "verify" });
abandonedRevocation.state.screen = "security";
abandonedRevocation.render();
assert.equal(abandonedRevocation.timers.size, 0, "leaving an operation fixture cancels its stale response");
console.log("Preview checks passed: all screens, pairing, inline delivery, selected-Sender revocation, ordered retry reconciliation, cancellation, and escaped device names.");
