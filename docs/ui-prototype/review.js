(() => {
  const PAGE = document.body.dataset.page;
  const params = new URLSearchParams(location.search);
  const state = {
    page: PAGE,
    screen: params.get("screen") || "",
    toggles: {
      home: true,
      office: true
    }
  };
  const PAIRING_PROGRESS_STEPS = [
    "正在打开配对通道",
    "正在生成设备身份",
    "正在交换加密密钥",
    "正在确认配对信息"
  ];

  const ICONS = {
    settings: '<path d="M19.43 12.98c.04-.32.07-.65.07-.98s-.03-.66-.08-.98l2.11-1.65a.5.5 0 0 0 .12-.64l-2-3.46a.5.5 0 0 0-.61-.22l-2.49 1a7.3 7.3 0 0 0-1.69-.98L14.5 2.42A.49.49 0 0 0 14 2h-4a.49.49 0 0 0-.49.42L9.12 5.07c-.61.25-1.17.59-1.69.98l-2.49-1a.49.49 0 0 0-.61.22l-2 3.46a.5.5 0 0 0 .12.64l2.11 1.65c-.04.32-.08.66-.08.98s.03.66.08.98l-2.11 1.65a.5.5 0 0 0-.12.64l2 3.46c.13.23.39.32.61.22l2.49-1c.52.4 1.08.73 1.69.98l.39 2.65c.04.24.25.42.49.42h4c.24 0 .45-.18.49-.42l.39-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.22.09.48 0 .61-.22l2-3.46a.5.5 0 0 0-.12-.64l-2.11-1.65ZM12 15.5A3.5 3.5 0 1 1 12 8a3.5 3.5 0 0 1 0 7.5Z"/>',
    more: '<circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/>',
    add: '<path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2Z"/>',
    chevron: '<path d="m9.29 6.71 5.3 5.29-5.3 5.29-1.42-1.42L11.46 12 7.87 8.13l1.42-1.42Z"/>',
    back: '<path d="m15.41 7.41-1.42-1.42L8 12l5.99 6.01 1.42-1.42L10.84 12l4.57-4.59Z"/>',
    close: '<path d="M6.4 5 5 6.4l5.6 5.6L5 17.6 6.4 19l5.6-5.6 5.6 5.6 1.4-1.4-5.6-5.6L19 6.4 17.6 5 12 10.6 6.4 5Z"/>',
    sms: '<path d="M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2Zm-2 11H6V9h12v4Zm-9-5H6V5h3v3Zm8 0h-6V5h6v3Z"/>',
    battery: '<path d="M15.67 4H14V2h-4v2H8.33A1.83 1.83 0 0 0 6.5 5.83v14.34C6.5 21.18 7.32 22 8.33 22h7.34a1.83 1.83 0 0 0 1.83-1.83V5.83C17.5 4.82 16.68 4 15.67 4Zm-1.17 13H9v-2h5.5v2Zm0-4H9v-2h5.5v2Z"/>',
    account: '<path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 4a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7Zm0 14a8 8 0 0 1-5.5-2.2c.4-1.8 3.1-2.8 5.5-2.8s5.1 1 5.5 2.8A8 8 0 0 1 12 20Z"/>',
    computer: '<path d="M4 4h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-6v2h3v2H7v-2h3v-2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm0 2v10h16V6H4Z"/>',
    qr: '<path d="M3 3h8v8H3V3Zm2 2v4h4V5H5Zm8-2h8v8h-8V3Zm2 2v4h4V5h-4ZM3 13h8v8H3v-8Zm2 2v4h4v-4H5Zm8-2h3v3h-3v-3Zm5 0h3v3h-3v-3Zm-5 5h3v3h-3v-3Zm5 0h3v3h-3v-3Z"/>',
    send: '<path d="m2.01 21 20.99-9-20.99-9L2 10l15 2-15 2 .01 7Z"/>',
    shield: '<path d="M12 2 4 5v6c0 5.05 3.41 9.76 8 11 4.59-1.24 8-5.95 8-11V5l-8-3Zm0 17.8C8.62 18.66 6 14.99 6 11.05V6.77l6-2.25 6 2.25v4.28c0 3.94-2.62 7.61-6 8.75Z"/><path d="m10.9 14.3-2.2-2.2 1.4-1.4 1.1 1.1 3-3 1.4 1.4-4.7 4.1Z"/>',
    language: '<path d="M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17A16.9 16.9 0 0 1 9 10.35 16.6 16.6 0 0 1 6.69 6H4.69a18.57 18.57 0 0 0 3.15 5.66l-5.37 5.3L4 18.38l5.37-5.3 3.34 3.34 1.16-1.35ZM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12Zm-2.62 7 1.62-4.33L19.12 17h-3.24Z"/>',
    theme: '<path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 2v16a8 8 0 0 1 0-16Z"/>',
    logout: '<path d="M10 17v3H4V4h6v3h2V2H2v20h10v-5h-2Zm9-6-4-4v3H8v2h7v3l4-4Z"/>',
    key: '<path d="M7 14a5 5 0 1 1 4.9-6H22v4h-2v2h-2v2h-4v-2h-2.1A5 5 0 0 1 7 14Zm0-7a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z"/>',
    check: '<path d="m9.2 16.6-4.3-4.3 1.4-1.4 2.9 2.9 8.5-8.5 1.4 1.4-9.9 9.9Z"/>',
    warning: '<path d="M1 21h22L12 2 1 21Zm12-3h-2v-2h2v2Zm0-4h-2V9h2v5Z"/>',
    info: '<path d="M11 17h2v-6h-2v6Zm1-15a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16ZM11 9h2V7h-2v2Z"/>',
    error: '<path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 15h-2v-2h2v2Zm0-4h-2V7h2v6Z"/>',
    edit: '<path d="M3 17.25V21h3.75L17.8 9.94l-3.75-3.75L3 17.25ZM20.7 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83Z"/>',
    trash: '<path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12ZM8 9h8v10H8V9Zm7.5-5-1-1h-5l-1 1H5v2h14V4h-3.5Z"/>',
    refresh: '<path d="M17.65 6.35A8 8 0 1 0 20 12h-2a6 6 0 1 1-1.76-4.24L13 11h8V3l-3.35 3.35Z"/>',
    clock: '<path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16Zm1-13h-2v6l5 3 1-1.73-4-2.27V7Z"/>',
    wifi: '<path d="M1 9a15.9 15.9 0 0 1 22 0l-2 2a13 13 0 0 0-18 0L1 9Zm4 4a10.1 10.1 0 0 1 14 0l-2 2a7.2 7.2 0 0 0-10 0l-2-2Zm4 4a4.2 4.2 0 0 1 6 0l-3 3-3-3Z"/>',
    phone: '<path d="M17.6 14.8a12.8 12.8 0 0 1-4 1.2 11.9 11.9 0 0 1-5.6-1.2 12.8 12.8 0 0 1-4-2.8l1.8-1.8c.4-.4.5-1 .3-1.5A12.2 12.2 0 0 1 5 5.3C5 4.6 4.4 4 3.7 4H1v2.7A17.3 17.3 0 0 0 18.3 24H21v-2.7c0-.7-.6-1.3-1.3-1.3a12 12 0 0 1-2.1-2.2c-.3-.2-.9-.1-1.3.3l-1.8 1.8-1.9.8 4.8-8.1Z"/>',
    lock: '<path d="M18 8h-1V6a5 5 0 0 0-10 0v2H6a2 2 0 0 0-2 2v10h16V10a2 2 0 0 0-2-2Zm-8-2a3 3 0 0 1 6 0v2h-6V6Zm4 10h-2v-3h2v3Z"/>',
    camera: '<path d="M9 3 7.17 5H4a2 2 0 0 0-2 2v12h20V7a2 2 0 0 0-2-2h-3.17L15 3H9Zm3 15a5 5 0 1 1 0-10 5 5 0 0 1 0 10Zm0-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"/>',
    swap: '<path d="M7 7h11l-3-3 1.4-1.4L21.8 8l-5.4 5.4L15 12l3-3H7V7Zm10 10H6l3 3-1.4 1.4L2.2 16l5.4-5.4L9 12l-3 3h11v2Z"/>',
    copy: '<path d="M16 1H4a2 2 0 0 0-2 2v14h2V3h12V1Zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2Zm0 16H8V7h11v14Z"/>',
    cloud_off: '<path d="M3.27 2 2 3.27l3.2 3.2A6 6 0 0 0 6 20h11.73L21 23.27 22.27 22 3.27 2ZM6 18a4 4 0 0 1-.29-7.99l1.04.08.31-.63L6 8.34V18Zm13.17-8A7.5 7.5 0 0 0 7.52 7.88l1.45 1.45A5.5 5.5 0 0 1 17.5 11l.2 1.02 1.02.18A3 3 0 0 1 18 18h-3.73l2 2H18a5 5 0 0 0 1.17-9.99Z"/>',
    person_check: '<path d="M10 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-3.31 0-6 1.79-6 4v2h9.2a6.5 6.5 0 0 1-3.2-6Zm8.5 0-3.5 3.5-1.5-1.5-1.5 1.5 3 3 5-5-1.5-1.5Z"/>',
    visibility: '<path d="M12 4C7 4 2.73 7.11 1 12c1.73 4.89 6 8 11 8s9.27-3.11 11-8c-1.73-4.89-6-8-11-8Zm0 14a6 6 0 1 1 0-12 6 6 0 0 1 0 12Zm0-10a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z"/>'
  };

  const DEVICES = [
    { id: "home", name: "我的电脑", online: true, changedAt: "刚刚" },
    { id: "office", name: "办公室电脑", online: true, changedAt: "2 分钟前" }
  ];

  const PAGES = {
    onboarding: {
      number: "01",
      title: "设置向导",
      description: "首次启动、系统权限、QQ 登录与设备配对",
      screens: [
        { id: "welcome", label: "欢迎", title: "欢迎使用鹊信", render: onboardingWelcome },
        { id: "privacy", label: "隐私", title: "隐私与数据处理", render: onboardingPrivacy },
        { id: "sms", label: "短信权限", title: "允许接收短信", render: onboardingSms },
        { id: "sms-system", label: "短信系统弹窗", title: "Android 短信权限", render: onboardingSmsPrompt },
        { id: "sms-denied", label: "短信被拒绝", title: "短信权限未开启", render: onboardingSmsDenied },
        { id: "battery", label: "后台准备", title: "关闭电池优化", render: onboardingBattery },
        { id: "qq-login", label: "QQ 登录", title: "连接你的 QQ", render: onboardingQq },
        { id: "qq-loading", label: "登录中", title: "正在验证授权", render: onboardingQqLoading },
        { id: "qq-error", label: "登录失败", title: "登录未完成", render: onboardingQqError },
        { id: "pair-start", label: "连接设备", title: "连接第一台设备", render: onboardingPairStart },
        { id: "camera-system", label: "相机系统弹窗", title: "Android 相机权限", render: onboardingCameraPrompt },
        { id: "camera-denied", label: "相机被拒绝", title: "相机权限未开启", render: onboardingCameraDenied },
        { id: "camera-blocked", label: "相机永久拒绝", title: "需要前往系统设置", render: onboardingCameraBlocked },
        { id: "scanner", label: "扫描二维码", title: "扫描电脑二维码", render: pairingScanner },
        { id: "scanner-invalid", label: "无效二维码", title: "不是鹊信二维码", render: onboardingScannerInvalid },
        { id: "session-expired", label: "会话失效", title: "二维码已失效", render: onboardingSessionExpired },
        { id: "consent", label: "确认设备", title: "确认连接设备", render: onboardingConsent },
        { id: "consent-blank", label: "名称为空", title: "设备名称不能为空", render: onboardingConsentBlank },
        { id: "consent-duplicate", label: "名称重复", title: "设备名称已存在", render: onboardingConsentDuplicate },
        { id: "progress-opening", label: "打开通道", title: "建立加密连接", render: onboardingProgressOpening },
        { id: "progress-identity", label: "生成身份", title: "建立加密连接", render: onboardingProgressIdentity },
        { id: "progress-key", label: "交换密钥", title: "建立加密连接", render: onboardingProgressKey },
        { id: "progress-confirm", label: "确认配对", title: "建立加密连接", render: onboardingProgressConfirm },
        { id: "pairing-login-expired", label: "配对时登录失效", title: "需要重新登录", render: onboardingPairingLoginExpired },
        { id: "pairing-failed", label: "建立失败", title: "配对未完成", render: onboardingPairingFailed },
        { id: "success", label: "配对完成", title: "配对完成", render: pairingSuccess }
      ]
    },
    home: {
      number: "02",
      title: "首页与设备",
      description: "转发状态、最近结果与 Receiver 管理",
      screens: [
        { id: "ready", label: "已就绪", title: "转发已就绪", render: homeReady },
        { id: "permission-lost", label: "短信权限失效", title: "转发已暂停", render: homePermissionLost },
        { id: "battery-lost", label: "后台准备失效", title: "后台处理不可用", render: homeBatteryLost },
        { id: "login-expired", label: "登录失效", title: "需要重新登录", render: homeLoginExpired },
        { id: "offline", label: "电脑离线", title: "有一台电脑离线", render: homeOffline },
        { id: "empty", label: "无设备", title: "等待第一台电脑", render: homeEmpty },
        { id: "menu", label: "更多菜单", title: "主页菜单", render: homeMenu },
        { id: "device-sheet", label: "设备详情", title: "管理电脑", render: homeDeviceSheet },
        { id: "rename-device", label: "重命名", title: "重命名电脑", render: homeRenameDevice },
        { id: "remove-confirm", label: "移除配对", title: "移除配对确认", render: homeRemoveConfirm },
        { id: "removed", label: "移除完成", title: "配对已移除", render: homeRemoved },
        { id: "revoked", label: "已撤销", title: "设备注册已撤销", render: homeRevoked }
      ]
    },
    pairing: {
      number: "03",
      title: "电脑配对",
      description: "扫码、模式确认、所有者验证与密钥进度",
      screens: [
        { id: "camera-permission", label: "相机权限", title: "允许扫描二维码", render: pairingCameraPermission },
        { id: "scanner", label: "扫描二维码", title: "扫描电脑二维码", render: pairingScanner },
        { id: "invalid-qr", label: "无效二维码", title: "不是鹊信二维码", render: pairingInvalidQr, media: true },
        { id: "consent", label: "新电脑确认", title: "确认连接电脑", render: pairingConsent },
        { id: "add-sender", label: "添加手机", title: "添加到已有电脑", render: pairingAddSender },
        { id: "re-pair", label: "更新密钥", title: "更新已有配对", render: pairingRePair },
        { id: "owner-proof", label: "所有者验证", title: "恢复电脑访问", render: pairingOwnerProof },
        { id: "recovery-warning", label: "恢复警告", title: "中断共享连接", render: pairingRecoveryWarning },
        { id: "progress", label: "密钥进度", title: "建立加密连接", render: pairingProgress },
        { id: "expired", label: "会话结束", title: "二维码已失效", render: pairingExpired },
        { id: "receiver-limit", label: "设备上限", title: "已达到电脑上限", render: pairingReceiverLimit },
        { id: "failure-preserved", label: "保留旧配对", title: "配对未完成", render: pairingFailurePreserved },
        { id: "success", label: "完成", title: "配对完成", render: pairingSuccess }
      ]
    },
    delivery: {
      number: "04",
      title: "投递结果",
      description: "测试消息、逐目标状态、不确定结果与空 OTP",
      screens: [
        { id: "test-start", label: "测试消息", title: "发送测试消息", render: deliveryTestStart },
        { id: "processing", label: "处理中", title: "正在准备测试消息", render: deliveryProcessing },
        { id: "result-all", label: "全部到达", title: "测试消息结果", render: deliveryResultAll },
        { id: "result-mixed", label: "混合结果", title: "部分电脑离线", render: deliveryResultMixed },
        { id: "result-uncertain", label: "送达未确认", title: "投递结果不确定", render: deliveryResultUncertain },
        { id: "result-empty-otp", label: "未识别码", title: "短信已发送", render: deliveryResultEmptyOtp },
        { id: "result-rejected", label: "目标被拒绝", title: "部分目标不可用", render: deliveryResultRejected },
        { id: "no-target", label: "无接收目标", title: "没有接收电脑", render: deliveryNoTarget },
        { id: "auth-expired", label: "授权失效", title: "登录状态已失效", render: deliveryAuthExpired }
      ]
    },
    settings: {
      number: "05",
      title: "设置与账号",
      description: "语言、QQ 账号、退出、安全撤销与隐私",
      screens: [
        { id: "settings", label: "设置", title: "设置", render: settingsMain },
        { id: "language", label: "语言", title: "选择语言", render: settingsLanguage },
        { id: "account", label: "QQ 账号", title: "QQ 账号", render: settingsAccount },
        { id: "switch-account", label: "切换账号", title: "切换 QQ 账号", render: settingsSwitchAccount },
        { id: "logout-confirm", label: "退出登录", title: "退出当前账号", render: settingsLogoutConfirm },
        { id: "logout-unconfirmed", label: "撤销未确认", title: "服务端撤销未确认", render: settingsLogoutUnconfirmed },
        { id: "security", label: "安全撤销", title: "设备访问安全", render: settingsSecurity },
        { id: "owner-proof", label: "QQ 验证", title: "验证账号所有者", render: settingsOwnerProof },
        { id: "revoke-confirm", label: "撤销确认", title: "永久撤销本机", render: settingsRevokeConfirm },
        { id: "revoked", label: "已撤销", title: "本机注册已撤销", render: settingsRevoked },
        { id: "privacy", label: "隐私", title: "隐私与数据", render: settingsPrivacy }
      ]
    }
  };

  const PAGE_ORDER = ["onboarding", "home", "pairing", "delivery", "settings"];
  const PAGE_FILES = {
    onboarding: "01-onboarding.html",
    home: "02-home.html",
    pairing: "03-pairing.html",
    delivery: "04-delivery.html",
    settings: "05-settings.html"
  };

  function icon(name, className = "icon") {
    return `<svg class="${className}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ICONS.info}</svg>`;
  }

  function appIcon(size = 36) {
    return `<img class="app-icon" src="app-icon.png" alt="" style="width:${size}px;height:${size}px;flex-basis:${size}px;border-radius:${Math.round(size * 0.31)}px;">`;
  }

  function qqMark(size = 46) {
    return `<span class="qq-mark" aria-hidden="true" style="--qq-size:${size}px"></span>`;
  }

  function screenHref(page, screen) {
    return `${PAGE_FILES[page]}?screen=${encodeURIComponent(screen)}`;
  }

  function actionAttrs(target) {
    if (!target) return "";
    return target.startsWith("page:")
      ? `data-href="${target.slice(5)}"`
      : `data-screen="${target}"`;
  }

  function iconButton(iconName, label, target, extraClass = "") {
    return `<button class="icon-button ${extraClass}" ${actionAttrs(target)} aria-label="${label}">${icon(iconName)}</button>`;
  }

  function button(kind, label, target, options = {}) {
    const cls = [kind, options.full ? "full" : ""].filter(Boolean).join(" ");
    const iconMarkup = options.iconName ? icon(options.iconName) : "";
    const disabled = options.disabled ? 'disabled aria-disabled="true"' : "";
    return `<button class="${cls}" ${actionAttrs(target)} ${disabled}>${iconMarkup}<span>${label}</span></button>`;
  }

  function statusBar(media = false) {
    return `
      <div class="status-bar ${media ? "is-on-media" : ""}">
        <span>9:41</span>
        <span class="status-icons">
          ${icon("wifi")}
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
            <rect x="2" y="14" width="3" height="6" rx="1"></rect>
            <rect x="7" y="11" width="3" height="9" rx="1"></rect>
            <rect x="12" y="7" width="3" height="13" rx="1"></rect>
            <rect x="17" y="3" width="3" height="17" rx="1"></rect>
          </svg>
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
            <rect x="3" y="7" width="17" height="10" rx="2"></rect>
            <rect x="21" y="10" width="2" height="4" rx="1"></rect>
          </svg>
        </span>
      </div>
    `;
  }

  function appBar(options = {}) {
    const {
      title = "鹊信",
      subtitle = "PIN GLIDE",
      back = false,
      backTarget = "home",
      more = null,
      settings = true,
      add = null
    } = options;

    const extras = [];
    if (add) extras.push(iconButton("add", "添加电脑", add, "filled"));
    if (more) extras.push(iconButton("more", "更多", more));
    if (settings) extras.push(iconButton("settings", "设置", "page:05-settings.html?screen=settings"));

    return `
      <div class="app-bar">
        <div class="app-bar-main">
          ${back ? iconButton("back", "返回", backTarget) : appIcon()}
          <div>
            <h1 class="app-title">${title}</h1>
            ${subtitle ? `<span class="app-subtitle">${subtitle}</span>` : ""}
          </div>
        </div>
        <div class="app-actions">${extras.join("")}</div>
      </div>
    `;
  }

  function indicator(step, total = 4) {
    return `<span class="step-indicator">${Array.from({ length: total }, (_, index) => `<span class="${index < step ? "done" : ""}"></span>`).join("")}</span>`;
  }

  function wizardAppBar(step, title = "完成准备") {
    return `
      <div class="app-bar">
        <div class="app-bar-main">
          ${appIcon(34)}
          <div>
            <h1 class="app-title" style="font-size:18px">${title}</h1>
            <span class="app-subtitle">${step} / 4</span>
          </div>
        </div>
        ${indicator(step)}
      </div>
    `;
  }

  function wizardScreen({ step, iconName, brand = false, title, copy, primary, secondary, trust = "短信内容不会上传到鹊信服务器", secondaryKind = "text-button" }) {
    return `
      <div class="screen-fill">
        ${wizardAppBar(step)}
        <div class="wizard-content">
          <div class="wizard-emblem ${brand ? "brand" : ""}">
            ${brand ? qqMark(92) : icon(iconName)}
          </div>
          <h2 class="title-lg">${title}</h2>
          <p class="body wizard-copy">${copy}</p>
          ${trust ? `<span class="trust-line">${icon("shield")}${trust}</span>` : ""}
        </div>
        <div class="wizard-actions">
          ${button("filled-button", primary.label, primary.target, { full: true, iconName: primary.icon })}
          ${secondary ? button(secondaryKind, secondary.label, secondary.target, { full: true }) : ""}
        </div>
      </div>
    `;
  }

  function phoneShell(theme, config) {
    return `
      <div class="phone">
        <div class="phone-screen" data-theme="${theme}">
          ${statusBar(Boolean(config.media))}
          <div class="app-viewport">${config.render(theme)}</div>
        </div>
      </div>
    `;
  }

  function navMarkup(currentPage) {
    return PAGE_ORDER.map((page) => {
      const item = PAGES[page];
      return `<a class="page-link ${page === currentPage ? "is-current" : ""}" href="${PAGE_FILES[page]}">${item.number} ${item.title}</a>`;
    }).join("");
  }

  function render() {
    const page = PAGES[state.page];
    if (!page) return;
    if (!page.screens.some((item) => item.id === state.screen)) {
      state.screen = page.screens[0].id;
    }
    const index = page.screens.findIndex((item) => item.id === state.screen);
    const screen = page.screens[index];
    const app = document.getElementById("app");
    app.innerHTML = `
      <header class="review-header">
        <div class="review-brand">
          <img src="app-icon.png" alt="">
          <div>
            <div class="review-kicker">Compose Material 3 · UI Review</div>
            <h1 class="review-title">${page.number} · ${page.title}</h1>
            <p class="review-subtitle">${page.description}</p>
          </div>
        </div>
        <nav class="page-nav" aria-label="功能页面">${navMarkup(state.page)}</nav>
      </header>

      <section class="screen-toolbar">
        <button class="toolbar-arrow" id="previousScreen" aria-label="上一个界面">${icon("back")}</button>
        <div class="toolbar-copy">
          <span class="toolbar-eyebrow">${index + 1} / ${page.screens.length} · ${screen.id}</span>
          <h2 class="toolbar-title">${screen.title}</h2>
        </div>
        <button class="toolbar-arrow" id="nextScreen" aria-label="下一个界面" style="transform:rotate(180deg)">${icon("back")}</button>
      </section>

      <div class="screen-tabs" aria-label="界面状态">
        ${page.screens.map((item) => `<button class="screen-tab ${item.id === state.screen ? "is-active" : ""}" data-review-screen="${item.id}">${item.label}</button>`).join("")}
      </div>

      <main class="preview-grid">
        <section class="preview-column">
          <div class="preview-label">LIGHT <span>Material 3</span></div>
          ${phoneShell("light", screen)}
        </section>
        <section class="preview-column">
          <div class="preview-label dark">DARK <span>Material 3</span></div>
          ${phoneShell("dark", screen)}
        </section>
      </main>

      <div class="format-note"><strong>390 × 844</strong><span>·</span><span>48dp touch targets</span><span>·</span><span>16 / 28dp shapes</span><span>·</span><span>中文预览</span></div>
    `;
    document.title = `${page.number} ${page.title} · 鹊信`;
    if (location.protocol !== "file:") {
      history.replaceState(null, "", `${location.pathname}?screen=${encodeURIComponent(state.screen)}`);
    }
    document.getElementById("previousScreen").addEventListener("click", () => moveScreen(-1));
    document.getElementById("nextScreen").addEventListener("click", () => moveScreen(1));
    document.querySelectorAll("[data-review-screen]").forEach((button) => {
      button.addEventListener("click", () => {
        state.screen = button.dataset.reviewScreen;
        render();
      });
    });
  }

  function moveScreen(delta) {
    const screens = PAGES[state.page].screens;
    const current = screens.findIndex((item) => item.id === state.screen);
    const next = (current + delta + screens.length) % screens.length;
    state.screen = screens[next].id;
    render();
    window.scrollTo({ top: 0 });
  }

  /* Onboarding */
  function onboardingWelcome(theme) {
    return `
      <div class="screen-fill" style="justify-content:space-between;padding-top:30px;">
        <div style="display:flex;align-items:center;gap:10px;">
          ${appIcon(38)}
          <div><span class="app-title" style="font-size:19px">鹊信</span><span class="app-subtitle">PIN GLIDE</span></div>
        </div>
        <div class="welcome-hero">
          <div class="cross-feed" aria-hidden="true">
            <img class="hero-animation" src="assets/quexin-notification-push-${theme}.svg" alt="" draggable="false">
          </div>
          <div class="welcome-copy-block">
            <h2 class="welcome-headline"><span>消息跨屏，</span><span>实时无界</span></h2>
            <p class="body wizard-copy">消息自动推送到目标设备，全程安全加密。</p>
            <button class="privacy-link">${icon("shield")}<span>隐私与数据处理</span>${icon("chevron", "chevron")}</button>
          </div>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "开始设置", "sms", { full: true, iconName: "chevron" })}
        </div>
      </div>
    `;
  }

  function onboardingPrivacy() {
    return `
      <div class="screen-fill privacy-screen">
        <div class="app-bar">
          <div class="app-bar-main">
            ${iconButton("close", "关闭", "welcome")}
            <div>
              <h1 class="app-title" style="font-size:18px">隐私与数据处理</h1>
            </div>
          </div>
        </div>
        <div class="screen-scroll">
          ${privacyContent()}
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "知道了", "welcome", { full: true })}
        </div>
      </div>
    `;
  }

  function onboardingSms() {
    return wizardScreen({
      step: 1,
      iconName: "sms",
      title: "接收短信",
      copy: "允许鹊信读取到达的新短信。",
      primary: { label: "允许接收短信", target: "battery", icon: "check" },
      trust: ""
    });
  }

  function systemPermissionPrompt({ title, iconName }) {
    return `
      <div class="scrim dialog-scrim system-permission-scrim">
        <div class="system-permission-dialog" role="dialog" aria-modal="true">
          <span class="system-permission-icon">${icon(iconName)}</span>
          <h2>${title}</h2>
          <div class="system-permission-actions">
            <button class="system-permission-button">允许</button>
            <button class="system-permission-button">不允许</button>
          </div>
        </div>
      </div>
    `;
  }

  function toastMessage(message) {
    return `<div class="toast-message" role="status">${message}</div>`;
  }

  function snackbarMessage(message, actionLabel = "") {
    return `
      <div class="snackbar above-actions" role="status">
        ${icon("info")}
        <span style="flex:1">${message}</span>
        ${actionLabel ? `<button class="snackbar-action">${actionLabel}</button>` : ""}
      </div>
    `;
  }

  function onboardingSmsPrompt() {
    return onboardingSms() + systemPermissionPrompt({
      title: "要允许“鹊信”发送和查看短信吗？",
      iconName: "sms"
    });
  }

  function onboardingSmsDenied() {
    return `
      <div class="screen-fill">
        ${wizardAppBar(1)}
        <div class="wizard-content">
          <div class="wizard-emblem" style="background:var(--md-error-container);color:var(--md-error);">${icon("sms")}</div>
          <h2 class="title-lg">短信权限未开启</h2>
          <p class="body wizard-copy">在系统设置中允许“短信”权限后才能继续。权限关闭期间，转发会保持暂停。</p>
          <div class="info-card warning" style="width:100%;text-align:left;">${icon("info")}<span>系统可能不会再次弹出权限窗口，需要从应用设置中手动开启。</span></div>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "前往系统设置", "battery", { full: true, iconName: "settings" })}
          ${button("text-button", "重新检查", "sms", { full: true })}
        </div>
      </div>
    `;
  }

  function onboardingBattery() {
    return wizardScreen({
      step: 2,
      iconName: "battery",
      title: "让后台处理更可靠",
      copy: "建议在系统设置中找到鹊信并关闭电池优化。新短信到达时，鹊信只会启动一次短时处理，完成或失败后立即停止。",
      primary: { label: "打开电池优化设置", target: "external:battery-optimization", icon: "settings" },
      secondary: { label: "下一步", target: "qq-login" },
      secondaryKind: "outlined-button",
      trust: ""
    });
  }

  function onboardingQq() {
    return wizardScreen({
      step: 3,
      iconName: "account",
      brand: true,
      title: "连接你的 QQ",
      copy: "使用 QQ 登录账号、管理电脑和授权加密转发。",
      primary: { label: "使用 QQ 登录", target: "qq-loading", icon: "chevron" },
      trust: ""
    });
  }

  function onboardingQqLoading() {
    return `
      <div class="screen-fill">
        <div class="wizard-content">
          <div class="spinner">${qqMark(92)}</div>
          <h2 class="title-lg">正在登录 QQ</h2>
        </div>
        <div class="wizard-actions">
          ${button("text-button", "取消", "qq-login", { full: true })}
        </div>
      </div>
    `;
  }

  function onboardingQqError() {
    return `
      <div class="screen-fill">
        <div class="wizard-content">
          <div class="wizard-emblem" style="background:var(--md-error-container);color:var(--md-error);">${icon("error")}</div>
          <h2 class="title-lg">登录未完成</h2>
          <p class="body-small" style="max-width:286px;margin-top:10px;">QQ 授权已取消或服务暂时不可用。当前设备未成功注册。</p>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "重新登录", "qq-loading", { full: true, iconName: "refresh" })}
          ${button("text-button", "返回", "qq-login", { full: true })}
        </div>
      </div>
    `;
  }

  function onboardingPairStart() {
    return wizardScreen({
      step: 4,
      iconName: "qr",
      title: "连接第一台设备",
      copy: "在接收端打开鹊信并显示二维码，然后扫描并确认。完成后，这台手机就可以开始转发短信。",
      primary: { label: "扫描二维码", target: "scanner", icon: "qr" },
      secondary: { label: "稍后配对", target: "page:02-home.html?screen=empty" },
      trust: ""
    });
  }

  function onboardingCameraPrompt() {
    return onboardingPairStart() + systemPermissionPrompt({
      title: "要允许“鹊信”拍照和录制视频吗？",
      iconName: "camera"
    });
  }

  function onboardingCameraDenied() {
    return onboardingPairStart() + toastMessage("相机权限未开启");
  }

  function onboardingCameraBlocked() {
    return onboardingPairStart() + snackbarMessage("相机权限已被永久拒绝", "去设置");
  }

  function onboardingScannerInvalid() {
    return pairingScanner() + `<div class="snackbar above-actions" role="status">${icon("error")}<span>不是鹊信配对码，请继续对准二维码</span></div>`;
  }

  function onboardingPairingError({ iconName, eyebrow, title, copy, primary, secondary = "" }) {
    return `
      <div class="screen-fill">
        <div class="wizard-content">
          <div class="wizard-emblem" style="background:var(--md-warning-container);color:var(--md-warning);">${icon(iconName)}</div>
          <div class="eyebrow" style="color:var(--md-warning)">${eyebrow}</div>
          <h2 class="title-lg">${title}</h2>
          <p class="body-small" style="max-width:306px;margin-top:10px;">${copy}</p>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", primary.label, primary.target, { full: true, iconName: primary.icon })}
          ${secondary ? button("text-button", secondary.label, secondary.target, { full: true }) : ""}
        </div>
      </div>
    `;
  }

  function onboardingSessionExpired() {
    return onboardingPairingError({
      iconName: "clock",
      eyebrow: "会话已结束",
      title: "二维码已失效",
      copy: "配对会话已过期、被占用或已被取消。请让接收端重新显示二维码。",
      primary: { label: "扫描新二维码", target: "scanner", icon: "qr" },
      secondary: { label: "稍后配对", target: "page:02-home.html?screen=empty" }
    });
  }

  function onboardingPairingLoginExpired() {
    return onboardingPairingError({
      iconName: "account",
      eyebrow: "登录状态已失效",
      title: "需要重新登录",
      copy: "配对尚未建立。重新登录 QQ 后，从扫描二维码重新开始。",
      primary: { label: "重新登录 QQ", target: "qq-login", icon: "account" }
    });
  }

  function onboardingPairingFailed() {
    return onboardingPairingError({
      iconName: "cloud_off",
      eyebrow: "未提交新配对",
      title: "配对未完成",
      copy: "连接中断或确认超时。原来的配对与密钥仍然有效，不会留下半完成的关联。",
      primary: { label: "重新扫描", target: "scanner", icon: "refresh" },
      secondary: { label: "稍后配对", target: "page:02-home.html?screen=empty" }
    });
  }

  function onboardingConsent({
    alias = "我的设备",
    error = "",
    confirmDisabled = false,
    secondaryLabel = "取消",
    secondaryTarget = "pair-start"
  } = {}) {
    return pairConsentBase({
      title: "连接新设备",
      copy: "确认后，这台设备会开始接收你选中的短信，双方会建立一条独立的加密配对。",
      primary: "确认并建立密钥",
      backTarget: "scanner",
      alias,
      error,
      confirmDisabled,
      secondaryLabel,
      secondaryTarget
    });
  }

  function onboardingConsentBlank() {
    return onboardingConsent({ alias: "", error: "请输入设备名称", confirmDisabled: true });
  }

  function onboardingConsentDuplicate() {
    return onboardingConsent({ alias: "办公室电脑", error: "已有一台同名设备，请换一个名称", confirmDisabled: true });
  }

  function onboardingProgressOpening() {
    return pairingProgress(0);
  }

  function onboardingProgressIdentity() {
    return pairingProgress(1);
  }

  function onboardingProgressKey() {
    return pairingProgress(2);
  }

  function onboardingProgressConfirm() {
    return pairingProgress(3);
  }

  /* Home */
  function deviceRows({ officeOffline = false } = {}) {
    return `<div class="device-list">${DEVICES.map((device) => {
      const online = officeOffline && device.id === "office" ? false : device.online;
      return `
        <div class="device-row">
          <span class="device-icon">${icon("computer")}</span>
          <span class="device-copy">
            <span class="device-name">${device.name}</span>
            <span class="device-meta"><span class="presence ${online ? "" : "offline"}"></span>${online ? "在线" : "离线"} · ${device.changedAt}</span>
          </span>
          <button class="switch-control" role="switch" aria-checked="${state.toggles[device.id]}" aria-label="${device.name}接收短信" data-toggle="${device.id}"></button>
          <button class="row-action" data-screen="device-sheet" aria-label="${device.name}更多操作">${icon("more")}</button>
        </div>
      `;
    }).join("")}</div>`;
  }

  function summaryButton({ value = "刚刚 · 2 台电脑已到达", tone = "success", label = "已到达", target = "page:04-delivery.html?screen=result-all" } = {}) {
    return `
      <button class="summary-button" ${actionAttrs(target)}>
        <span class="summary-icon">${icon("send")}</span>
        <span class="summary-copy"><span class="summary-label">最近一次转发</span><span class="summary-value">${value}</span></span>
        <span class="pill ${tone}">${label}</span>
        ${icon("chevron", "chevron")}
      </button>
    `;
  }

  function hero({
    title,
    copy,
    tone = "success",
    status = "全部在线",
    media = false,
    iconName = "check"
  }) {
    const background = tone === "warning"
      ? "background:linear-gradient(135deg,var(--md-warning-container),var(--md-surface-2));"
      : tone === "error"
        ? "background:linear-gradient(135deg,var(--md-error-container),var(--md-surface-2));"
        : "";
    return `
      <div class="status-hero" style="${background}">
        <div class="status-hero-head">
          <div><h2>${title}</h2><p>${copy}</p></div>
          <span class="pill ${tone}">${status}</span>
        </div>
        <div class="bridge-visual">
          <span class="bridge-node">${icon("phone")}</span><span class="bridge-line"></span><span class="bridge-node">${icon("computer")}</span>
        </div>
      </div>
    `;
  }

  function homeBase({
    heroMarkup,
    content = summaryButton() + `<div class="section-head"><h3>我的电脑</h3><span class="section-count">2 台</span></div>` + deviceRows(),
    menu = "",
    overlay = "",
    fab = true,
    appOptions = {}
  }) {
    return `
      <div class="app-scroll">
        <div class="screen-pad ${fab ? "with-fab" : ""}">
          ${appBar({ more: "menu", settings: true, ...appOptions })}
          ${heroMarkup}
          ${content}
        </div>
        ${fab ? `<button class="extended-fab" data-href="${screenHref("pairing", "scanner")}">${icon("add")}添加电脑</button>` : ""}
        ${menu}
      </div>
      ${overlay}
    `;
  }

  function homeReady() {
    return homeBase({
      heroMarkup: hero({
        title: "转发已就绪",
        copy: "新短信会被端到端加密后送往选中的电脑。",
        status: "全部在线"
      })
    });
  }

  function homePermissionLost() {
    return homeBase({
      heroMarkup: hero({
        title: "转发已暂停",
        copy: "短信权限已被关闭，新短信不会进入处理流程。",
        tone: "warning",
        status: "权限失效",
        iconName: "sms"
      }),
      content: `
        <div class="info-card warning">${icon("warning")}<span>重新开启短信权限后，转发会自动恢复；无需重新登录或重新配对。</span></div>
        <div style="margin-top:12px">${button("filled-button", "重新开启短信权限", "page:01-onboarding.html?screen=sms-denied", { full: true, iconName: "settings" })}</div>
        <div class="section-head"><h3>我的电脑</h3><span class="section-count">2 台</span></div>
        ${deviceRows()}
      `
    });
  }

  function homeBatteryLost() {
    return homeBase({
      heroMarkup: hero({
        title: "后台处理不可用",
        copy: "电池优化已重新开启，系统可能阻止短信到达后的短时处理。",
        tone: "warning",
        status: "后台受限",
        iconName: "battery"
      }),
      content: `
        <div class="info-card warning">${icon("warning")}<span>Android 可能在后台限制鹊信。重新关闭电池优化后，转发会自动恢复。</span></div>
        <div style="margin-top:12px">${button("filled-button", "打开电池优化设置", "page:01-onboarding.html?screen=battery", { full: true, iconName: "settings" })}</div>
        <div class="section-head"><h3>我的电脑</h3><span class="section-count">2 台</span></div>
        ${deviceRows()}
      `
    });
  }

  function homeLoginExpired() {
    return homeBase({
      heroMarkup: hero({
        title: "需要重新登录",
        copy: "QQ 授权窗口已结束。电脑配对会保留，重新登录后即可继续。",
        tone: "warning",
        status: "登录失效",
        iconName: "account"
      }),
      content: `
        <div style="margin-top:12px">${button("filled-button", "使用 QQ 重新登录", "page:01-onboarding.html?screen=qq-login", { full: true, iconName: "account" })}</div>
        <div class="section-head"><h3>我的电脑</h3><span class="section-count">2 台</span></div>
        ${deviceRows()}
      `
    });
  }

  function homeOffline() {
    return homeBase({
      heroMarkup: hero({
        title: "转发已就绪",
        copy: "办公室电脑当前离线。开启接收的离线电脑仍会提交给服务端并返回离线结果。",
        status: "1 台离线",
        tone: "warning"
      }),
      content: summaryButton({
        value: "刚刚 · 1 台已到达 · 1 台离线",
        tone: "warning",
        label: "部分离线",
        target: "page:04-delivery.html?screen=result-mixed"
      }) + `<div class="section-head"><h3>我的电脑</h3><span class="section-count">2 台</span></div>` + deviceRows({ officeOffline: true })
    });
  }

  function homeEmpty() {
    return homeBase({
      heroMarkup: hero({
        title: "账号已连接",
        copy: "添加第一台电脑后，鹊信才会开始转发新短信。",
        status: "等待电脑"
      }),
      content: `
        <div class="empty-state" style="margin-top:14px;">
          <span class="empty-icon">${icon("computer")}</span>
          <h3>还没有电脑</h3>
          <p>打开 Windows 端鹊信并显示二维码，然后用这台手机扫描。</p>
          ${button("tonal-button", "添加第一台电脑", "page:03-pairing.html?screen=scanner", { iconName: "qr" })}
        </div>
      `
    });
  }

  function homeMenu() {
    return homeBase({
      heroMarkup: hero({ title: "转发已就绪", copy: "新短信会被端到端加密后送往选中的电脑。", status: "全部在线" }),
      menu: `
        <div class="menu">
          <button data-href="${screenHref("delivery", "test-start")}">${icon("send")}发送测试消息</button>
          <button data-href="${screenHref("settings", "settings")}">${icon("settings")}设置</button>
        </div>
      `
    });
  }

  function homeDeviceSheet() {
    return homeBase({
      heroMarkup: hero({ title: "转发已就绪", copy: "新短信会被端到端加密后送往选中的电脑。", status: "全部在线" }),
      overlay: `
        <div class="scrim sheet-scrim">
          <div class="sheet">
            <div class="sheet-handle"></div>
            <div class="sheet-head">
              <div><h2 class="sheet-title">我的电脑</h2><p class="body-small" style="margin-top:5px;">在线 · 刚刚更新</p></div>
              <span class="device-icon">${icon("computer")}</span>
            </div>
            <div class="list-group" style="margin-top:0">
              <div class="list-item">
                ${icon("send")}
                <span><span class="list-item-title">接收短信</span><span class="list-item-desc">关闭后仍保留这台电脑的配对</span></span>
                <button class="switch-control" role="switch" aria-checked="${state.toggles.home}" aria-label="接收短信" data-toggle="home"></button>
              </div>
              <button class="list-item" data-screen="rename-device">${icon("edit")}<span class="list-item-title">重命名电脑</span>${icon("chevron")}</button>
              <button class="list-item danger-label" data-screen="remove-confirm">${icon("trash")}<span class="list-item-title">移除这台电脑的配对</span>${icon("chevron")}</button>
            </div>
            ${button("outlined-button", "关闭", "ready", { full: true })} 
          </div>
        </div>
      `
    });
  }

  function homeRenameDevice() {
    return homeBase({
      heroMarkup: hero({ title: "转发已就绪", copy: "新短信会被端到端加密后送往选中的电脑。", status: "全部在线" }),
      overlay: `
        <div class="scrim sheet-scrim">
          <div class="sheet">
            <div class="sheet-handle"></div>
            <div class="sheet-head">
              <div><h2 class="sheet-title">重命名电脑</h2><p class="body-small" style="margin-top:5px;">别名只对当前 Sender 可见</p></div>
              <span class="device-icon">${icon("computer")}</span>
            </div>
            <label class="alias-label" for="deviceAlias">电脑名称</label>
            <input class="alias-input" id="deviceAlias" value="我的电脑" maxlength="40">
            <p class="body-small" style="margin-top:8px;">其他 Sender 对这台电脑的称呼不会改变。</p>
            <div class="half-row" style="margin-top:18px;">
              ${button("outlined-button", "取消", "device-sheet", { full: true })}
              ${button("filled-button", "保存", "device-sheet", { full: true })}
            </div>
          </div>
        </div>
      `
    });
  }

  function homeRemoveConfirm() {
    return homeBase({
      heroMarkup: hero({ title: "转发已就绪", copy: "新短信会被端到端加密后送往选中的电脑。", status: "全部在线" }),
      overlay: `
        <div class="scrim dialog-scrim">
          <div class="dialog">
            <div class="dialog-icon error">${icon("trash")}</div>
            <h2>移除“我的电脑”？</h2>
            <p>这台手机会停止向它发送短信。接收端和其他 Sender 的配对不会被关闭。</p>
            <div class="dialog-actions">
              ${button("text-button", "取消", "device-sheet")}
              ${button("danger-button", "移除配对", "removed")}
            </div>
          </div>
        </div>
      `
    });
  }

  function homeRemoved() {
    return homeBase({
      heroMarkup: hero({ title: "转发已就绪", copy: "新短信仍会送往其他已开启接收的电脑。", status: "全部在线" }),
      content: `
        ${summaryButton({ value: "刚刚 · 2 台电脑已到达", tone: "success", label: "已到达" })}
        <div class="section-head"><h3>我的电脑</h3><span class="section-count">1 台</span></div>
        <div class="device-list">
          <div class="device-row">
            <span class="device-icon">${icon("computer")}</span>
            <span class="device-copy">
              <span class="device-name">办公室电脑</span>
              <span class="device-meta"><span class="presence"></span>在线 · 2 分钟前</span>
            </span>
            <button class="switch-control" role="switch" aria-checked="${state.toggles.office}" aria-label="办公室电脑接收短信" data-toggle="office"></button>
            <button class="row-action" data-screen="device-sheet" aria-label="办公室电脑更多操作">${icon("more")}</button>
          </div>
        </div>
      `,
      overlay: `<div class="snackbar">${icon("check")}已移除“我的电脑”的配对</div>`
    });
  }

  function homeRevoked() {
    return `
      <div class="revoked-screen">
        <span class="empty-icon">${icon("lock")}</span>
        <h2>设备注册已撤销</h2>
        <p>这台手机的旧注册已被安全撤销，不能恢复。重新登录会创建新的设备注册，之后需要重新配对电脑。</p>
        ${button("filled-button", "退出并重新登录", "page:01-onboarding.html?screen=qq-login", { full: true, iconName: "account" })}
        ${button("text-button", "查看隐私与数据", "page:05-settings.html?screen=privacy", { full: true })}
      </div>
    `;
  }

  /* Pairing */
  function pairingCameraPermission() {
    return `
      <div class="screen-fill">
        ${appBar({ title: "扫描二维码", subtitle: "", back: true, backTarget: "page:02-home.html?screen=ready", settings: false })}
        <div class="wizard-content">
          <div class="wizard-emblem">${icon("camera")}</div>
          <div class="eyebrow">相机权限</div>
          <h2 class="title-lg">扫描电脑上的二维码</h2>
          <p class="body wizard-copy">相机只用于本次配对。鹊信不会保存画面，也不会在后台使用相机。</p>
          <span class="trust-line">${icon("shield")}识别完成后立即关闭相机</span>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "允许使用相机", "scanner", { full: true, iconName: "camera" })}
          ${button("text-button", "取消", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  function mockQr() {
    const pattern = [
      1,1,1,0,1,0,1,1,1,
      1,0,1,0,0,1,1,0,1,
      1,1,1,0,1,0,1,1,1,
      0,0,0,1,0,1,0,0,0,
      1,1,0,1,1,0,1,0,1,
      0,1,1,0,1,1,0,1,0,
      1,1,1,1,0,0,1,1,1,
      1,0,1,0,1,0,1,0,1,
      1,1,1,0,0,1,1,1,1
    ];
    return `<div class="mock-qr">${pattern.map((on) => `<i class="${on ? "" : "blank"}"></i>`).join("")}</div>`;
  }

  function pairingScanner() {
    return `
      <div class="scanner">
        <div class="scanner-grid"></div>
        <div class="scanner-content">
          <div class="scanner-top">
            ${iconButton("close", "取消", "page:02-home.html?screen=ready")}
            <div style="flex:1;text-align:center"><span class="app-title" style="font-size:18px">扫描二维码</span></div>
            <span style="width:48px"></span>
          </div>
          <h2 class="scanner-title">对准电脑上的二维码</h2>
          <p class="scanner-copy">二维码 5 分钟内有效。请确认电脑屏幕上的鹊信窗口处于打开状态。</p>
          <div class="viewfinder">
            <span class="corner tl"></span><span class="corner tr"></span><span class="corner bl"></span><span class="corner br"></span>
            <span class="scan-line"></span>
            ${mockQr()}
          </div>
          <div class="scanner-actions">
            ${button("text-button", "取消", "page:02-home.html?screen=ready", { full: true })}
          </div>
        </div>
      </div>
    `;
  }

  function pairingInvalidQr() {
    return `
      <div class="scanner">
        <div class="scanner-grid"></div>
        <div class="scanner-content" style="justify-content:center;">
          <div class="empty-icon" style="width:92px;height:92px;background:rgba(255,180,171,.16);color:#ffb4ab;">${icon("error")}</div>
          <h2 class="scanner-title" style="margin-top:28px;">不是鹊信配对码</h2>
          <p class="scanner-copy">请回到 Windows 端鹊信，重新显示配对二维码后再扫描。</p>
          <div class="scanner-actions" style="margin-top:34px;">
            ${button("filled-button", "重新扫描", "scanner", { full: true, iconName: "camera" })}
            ${button("text-button", "取消", "page:02-home.html?screen=ready", { full: true })}
          </div>
        </div>
      </div>
    `;
  }

  function pairConsentBase({
    title,
    copy,
    warning = "",
    primary,
    backTarget,
    alias = "我的设备",
    error = "",
    confirmDisabled = false,
    secondaryLabel = "取消",
    secondaryTarget = "page:02-home.html?screen=ready"
  }) {
    return `
      <div class="screen-fill pair-screen">
        ${appBar({ title: "确认配对", subtitle: "", back: true, backTarget, settings: false })}
        <div class="screen-scroll">
          <div class="pair-visual">
            <span class="pair-node">${appIcon(70)}</span><span class="pair-link"></span><span class="pair-node">${icon("computer")}</span>
          </div>
          <h2 class="title-lg">${title}</h2>
          <p class="body muted" style="margin-top:10px;">${copy}</p>
          ${warning ? `<div class="info-card warning">${icon("warning")}<span>${warning}</span></div>` : ""}
          <label class="alias-field">
            <span class="alias-label">设备名称</span>
            <input class="alias-input ${error ? "is-invalid" : ""}" name="alias" value="${alias}" maxlength="40" autocomplete="off" spellcheck="false" aria-label="设备名称" aria-invalid="${Boolean(error)}" required>
            <span class="${error ? "alias-error" : "alias-help"}">${error || "不能为空，也不能与本机已配对的设备重名。"}</span>
          </label>
          <div class="list-group" style="margin-top:12px">
            <div class="list-item">${icon("key")}<span class="list-item-title">配对会话剩余时间</span><span class="list-item-value">04:31</span></div>
          </div>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", primary, "progress", { full: true, iconName: "key", disabled: confirmDisabled })}
          ${button("text-button", secondaryLabel, secondaryTarget, { full: true })}
        </div>
      </div>
    `;
  }

  function pairingConsent() {
    return pairConsentBase({
      title: "连接新设备",
      copy: "确认后，这台设备会开始接收你选中的短信，双方会建立一条独立的加密配对。",
      primary: "确认并建立密钥",
      backTarget: "scanner"
    });
  }

  function pairingAddSender() {
    return `
      <div class="app-scroll">
        <div class="screen-pad">
          ${appBar({ title: "确认配对", subtitle: "", back: true, backTarget: "scanner", settings: false })}
          <div class="pair-visual">
            <span class="pair-node">${appIcon(70)}</span><span class="pair-link"></span><span class="pair-node">${icon("computer")}</span>
          </div>
          <div class="eyebrow">已有电脑</div>
          <h2 class="title-lg">把这台手机添加到“我的电脑”？</h2>
          <p class="body muted" style="margin-top:10px;">这台电脑已有接收连接。添加新的 Sender 不会中断现有连接，也不会替换其他手机的配对。</p>
          <div class="list-group" style="margin-top:18px">
            <div class="list-item">${icon("computer")}<span><span class="list-item-title">我的电脑</span><span class="list-item-desc">已有 Windows 接收连接</span></span><span class="list-item-value">保持在线</span></div>
            <div class="list-item">${icon("key")}<span><span class="list-item-title">新配对</span><span class="list-item-desc">为这台手机单独建立密钥</span></span><span class="list-item-value">04:31</span></div>
          </div>
          ${button("filled-button", "确认添加这台手机", "progress", { full: true, iconName: "key" })}
          ${button("text-button", "取消", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  function pairingRePair() {
    return pairConsentBase({
      title: "更新这台电脑的密钥",
      copy: "旧配对会持续可用，直到双方确认新密钥。若未完成，旧配对保持不变。",
      warning: "只有双方都完成确认后，服务才会原子切换到新的 Pairing ID。",
      primary: "确认并更新密钥",
      backTarget: "scanner"
    });
  }

  function pairingOwnerProof() {
    return `
      <div class="screen-fill">
        ${appBar({ title: "恢复电脑访问", subtitle: "", back: true, backTarget: "scanner", settings: false })}
        <div class="wizard-content">
          <div class="wizard-emblem" style="background:var(--md-warning-container);color:var(--md-warning);">${icon("person_check")}</div>
          <div class="eyebrow" style="color:var(--md-warning)">需要所有者验证</div>
          <h2 class="title-lg">重新验证你的 QQ</h2>
          <p class="body wizard-copy">恢复 Receiver 凭据需要一次新的 QQ 账号所有者证明。普通的访问令牌不能代替这一步。</p>
          <div class="info-card warning" style="width:100%;text-align:left;">${icon("warning")}<span>恢复会替换电脑的现有接收凭据，并中断所有 Sender 的共享连接。</span></div>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "验证 QQ 并继续", "recovery-warning", { full: true, iconName: "account" })}
          ${button("text-button", "取消", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  function pairingRecoveryWarning() {
    return `
      <div class="screen-fill">
        ${appBar({ title: "恢复确认", subtitle: "", back: true, backTarget: "owner-proof", settings: false })}
        <div class="wizard-content">
          <div class="wizard-emblem" style="background:var(--md-error-container);color:var(--md-error);">${icon("cloud_off")}</div>
          <div class="eyebrow" style="color:var(--md-error)">共享连接会中断</div>
          <h2 class="title-lg">所有 Sender 会暂时断开</h2>
          <p class="body wizard-copy">恢复成功后，电脑需要使用新的接收凭据重新连接。只有双方仍持有可用密钥的配对会被保留。</p>
          <div class="info-card error" style="width:100%;text-align:left;">${icon("warning")}<span>不会创建新的 Pairing，也不会占用额外设备名额。</span></div>
          <div class="info-card" style="width:100%;text-align:left;">
            <span class="checkbox checked">${icon("check")}</span><span>我已了解这会中断其他 Sender 到这台电脑的连接。</span>
          </div>
        </div>
        <div class="wizard-actions">
          ${button("danger-button", "继续恢复", "progress", { full: true, iconName: "warning" })}
          ${button("text-button", "取消", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  function pairingProgress(stageIndex = 0) {
    const stage = PAIRING_PROGRESS_STEPS[Math.max(0, Math.min(stageIndex, PAIRING_PROGRESS_STEPS.length - 1))];
    return `
      <div class="app-scroll">
        <div class="pair-progress">
          <div class="spinner">${appIcon(92)}</div>
          <h2 class="title-lg pair-progress-title" aria-live="polite">${stage}</h2>
          <p class="body-small" style="max-width:286px;margin-top:10px;">保持接收端上的鹊信窗口打开。完成后会自动进入配对完成页面。</p>
        </div>
      </div>
    `;
  }

  function pairingExpired() {
    return `
      <div class="screen-fill" style="align-items:center;justify-content:center;text-align:center;">
        <div class="wizard-emblem" style="background:var(--md-warning-container);color:var(--md-warning);">${icon("clock")}</div>
        <div class="eyebrow" style="color:var(--md-warning)">会话已结束</div>
        <h2 class="title-lg">二维码已失效</h2>
        <p class="body-small" style="max-width:300px;margin-top:10px;">配对会话已过期、取消或被新的会话取代。请让 Windows 端重新显示二维码。</p>
        <div class="wizard-actions" style="width:100%;margin-top:28px;">
          ${button("filled-button", "扫描新二维码", "scanner", { full: true, iconName: "camera" })}
          ${button("text-button", "返回首页", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  function pairingReceiverLimit() {
    return `
      <div class="screen-fill" style="align-items:center;justify-content:center;text-align:center;">
        <div class="wizard-emblem" style="background:var(--md-warning-container);color:var(--md-warning);">${icon("computer")}</div>
        <div class="eyebrow" style="color:var(--md-warning)">已达到 5 台</div>
        <h2 class="title-lg">电脑数量已满</h2>
        <p class="body-small" style="max-width:306px;margin-top:10px;">一个 Sender 最多关联 5 台不同的电脑。移除一台电脑的配对后，可以重新扫描这台新电脑。</p>
        <div class="wizard-actions" style="width:100%;margin-top:28px;">
          ${button("filled-button", "管理电脑配对", "page:02-home.html?screen=ready", { full: true, iconName: "computer" })}
          ${button("text-button", "取消", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  function pairingFailurePreserved() {
    return `
      <div class="screen-fill" style="align-items:center;justify-content:center;text-align:center;">
        <div class="wizard-emblem" style="background:var(--md-error-container);color:var(--md-error);">${icon("cloud_off")}</div>
        <div class="eyebrow" style="color:var(--md-error)">未提交新配对</div>
        <h2 class="title-lg">配对未完成</h2>
        <p class="body-small" style="max-width:306px;margin-top:10px;">连接中断或确认超时。原来的配对与密钥仍然有效，不会留下半完成的关联。</p>
        <div class="wizard-actions" style="width:100%;margin-top:28px;">
          ${button("filled-button", "重新扫描", "scanner", { full: true, iconName: "refresh" })}
          ${button("text-button", "返回首页", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  function pairingSuccess() {
    return `
      <div class="screen-fill">
        <div class="wizard-content">
          <div class="wizard-emblem" style="background:var(--md-success-container);color:var(--md-success);">${icon("check")}</div>
          <h2 class="title-xl">配对完成</h2>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "发送测试消息", "page:04-delivery.html?screen=test-start", { full: true, iconName: "send" })}
          ${button("text-button", "进入首页", "page:02-home.html?screen=ready", { full: true })}
        </div>
      </div>
    `;
  }

  /* Delivery */
  function deliveryHomeBackground() {
    return `
      <div class="app-scroll">
        <div class="screen-pad">
          ${appBar({ more: "page:02-home.html?screen=menu", settings: true })}
          ${hero({ title: "转发已就绪", copy: "新短信会被端到端加密后送往选中的电脑。", status: "全部在线" })}
          ${summaryButton({ target: "page:04-delivery.html?screen=result-all" })}
          <div class="section-head"><h3>我的电脑</h3><span class="section-count">2 台</span></div>
          ${deviceRows({ officeOffline: false })}
        </div>
      </div>
    `;
  }

  function deliveryTestStart() {
    return `
      <div class="app-scroll">
        <div class="screen-pad">
          ${appBar({ title: "发送测试消息", subtitle: "", back: true, backTarget: "page:02-home.html?screen=ready", settings: false })}
          <div class="eyebrow">正常发送链路</div>
          <h2 class="title-lg">验证手机与电脑</h2>
          <p class="body muted" style="margin-top:8px;">测试消息会经过与真实短信相同的提取、验证、加密和上传路径。</p>
          <div class="simulated-sms">
            <div style="display:flex;align-items:center;justify-content:space-between;"><span class="label">模拟短信</span><span class="pill neutral">仅测试</span></div>
            <div class="sms-bubble">【鹊信】这是一条测试短信，验证码 482913。请勿用于真实登录。</div>
          </div>
          <div class="section-head"><h3>发送到</h3><span class="section-count">2 台</span></div>
          <div class="target-chips">
            <span class="target-chip">${icon("computer")}我的电脑</span>
            <span class="target-chip">${icon("computer")}办公室电脑</span>
          </div>
          <div class="info-card">${icon("info")}<span>“已到达电脑”只表示 Windows 收到消息，不代表已经弹窗或复制验证码。</span></div>
          ${button("filled-button", "开始测试", "processing", { full: true, iconName: "send" })}
        </div>
      </div>
    `;
  }

  function deliveryProcessing() {
    return `
      <div class="app-scroll">
        <div class="pair-progress">
          <div class="spinner">${appIcon(92)}</div>
          <h2 class="title-lg">正在准备测试消息</h2>
          <p class="body-small" style="max-width:286px;margin-top:10px;">请保持网络连接。完成后会按电脑显示到达状态。</p>
          <div class="progress-list">
            <div class="progress-row">${icon("check")}准备临时模型凭据</div>
            <div class="progress-row active">${icon("refresh")}提取并验证测试验证码</div>
            <div class="progress-row">${icon("lock")}逐台加密并上传</div>
          </div>
          ${button("text-button", "查看结果", "result-all")}
        </div>
      </div>
    `;
  }

  function deliverySheet({ title, status, tone, copy, rows, extra = "", actions }) {
    return `
      <div class="scrim sheet-scrim">
        <div class="sheet">
          <div class="sheet-handle"></div>
          <div class="sheet-head">
            <div><h2 class="sheet-title">${title}</h2><p class="body-small" style="margin-top:5px;">${copy}</p></div>
            <span class="pill ${tone}">${status}</span>
          </div>
          <div class="result-list">${rows}</div>
          ${extra}
          <div style="margin-top:16px">${actions}</div>
        </div>
      </div>
    `;
  }

  function resultRow({ name, online = true, status, tone = "success" }) {
    return `<div class="result-row"><span class="result-icon">${icon("computer")}</span><span><span class="device-name">${name}</span><span class="device-meta"><span class="presence ${online ? "" : "offline"}"></span>${online ? "在线" : "离线"}</span></span><span class="result-status ${tone}">${status}</span></div>`;
  }

  function deliveryOverlay(options) {
    return deliveryHomeBackground() + deliverySheet(options);
  }

  function deliveryResultAll() {
    return deliveryOverlay({
      title: "测试消息结果",
      status: "已到达",
      tone: "success",
      copy: "2 台电脑均已到达",
      rows: `
        ${resultRow({ name: "我的电脑", status: "已到达电脑" })}
        ${resultRow({ name: "办公室电脑", status: "已到达电脑" })}
      `,
      extra: `<div class="info-card">${icon("info")}<span>到达状态不等于 Windows 已解密、弹窗或复制。请在电脑上确认测试消息是否出现。</span></div>`,
      actions: button("filled-button", "知道了", "page:02-home.html?screen=ready", { full: true })
    });
  }

  function deliveryResultMixed() {
    return deliveryOverlay({
      title: "测试消息结果",
      status: "部分离线",
      tone: "warning",
      copy: "1 台到达 · 1 台离线",
      rows: `
        ${resultRow({ name: "我的电脑", status: "已到达电脑" })}
        ${resultRow({ name: "办公室电脑", online: false, status: "离线", tone: "warning" })}
      `,
      extra: `<div class="info-card warning">${icon("warning")}<span>离线是本次投递的最终结果，不会自动补发。点击“知道了”后不会创建新的消息。</span></div>`,
      actions: button("filled-button", "知道了", "page:02-home.html?screen=offline", { full: true })
    });
  }

  function deliveryResultUncertain() {
    return deliveryOverlay({
      title: "投递结果不确定",
      status: "送达未确认",
      tone: "warning",
      copy: "电脑可能已经收到消息",
      rows: `
        ${resultRow({ name: "我的电脑", status: "已到达电脑" })}
        ${resultRow({ name: "办公室电脑", status: "确认超时", tone: "warning" })}
      `,
      extra: `
        <div class="info-card warning">${icon("warning")}<span>这项结果不能证明消息未到达。鹊信不会自动创建新的消息或提供重试按钮。</span></div>
        <dl class="diagnostic"><div><dt>请求 ID</dt><dd>req_8f2c…</dd></div><div><dt>目标数量</dt><dd>2</dd></div><div><dt>结果类别</dt><dd>ack_timeout</dd></div></dl>
      `,
      actions: button("filled-button", "知道了", "page:02-home.html?screen=offline", { full: true })
    });
  }

  function deliveryResultEmptyOtp() {
    return deliveryOverlay({
      title: "最近一次转发",
      status: "未识别码",
      tone: "neutral",
      copy: "短信全文已到达 2 台电脑",
      rows: `
        ${resultRow({ name: "我的电脑", status: "已到达电脑" })}
        ${resultRow({ name: "办公室电脑", status: "已到达电脑" })}
      `,
      extra: `<div class="info-card">${icon("info")}<span><strong>未识别验证码不是发送失败。</strong> Windows 仍会显示短信全文，但这条消息没有可复制的 OTP。</span></div>`,
      actions: button("filled-button", "知道了", "page:02-home.html?screen=ready", { full: true })
    });
  }

  function deliveryResultRejected() {
    return deliveryOverlay({
      title: "测试消息结果",
      status: "部分失败",
      tone: "error",
      copy: "1 台到达 · 1 台目标不可用",
      rows: `
        ${resultRow({ name: "我的电脑", status: "已到达电脑" })}
        ${resultRow({ name: "办公室电脑", online: false, status: "配对已移除", tone: "error" })}
      `,
      extra: `<div class="info-card error">${icon("error")}<span>这台电脑的配对已不存在。请返回设备列表刷新后重新配对。</span></div>`,
      actions: button("filled-button", "返回设备列表", "page:02-home.html?screen=ready", { full: true })
    });
  }

  function deliveryNoTarget() {
    return deliveryHomeBackground() + `
      <div class="scrim dialog-scrim">
        <div class="dialog">
          <div class="dialog-icon">${icon("computer")}</div>
          <h2>没有接收电脑</h2>
          <p>所有电脑的接收开关都已关闭。打开至少一台电脑后，才能发送测试消息。</p>
          <div class="dialog-actions">${button("filled-button", "返回首页", "page:02-home.html?screen=ready")}</div>
        </div>
      </div>
    `;
  }

  function deliveryAuthExpired() {
    return deliveryHomeBackground() + `
      <div class="scrim dialog-scrim">
        <div class="dialog">
          <div class="dialog-icon error">${icon("account")}</div>
          <h2>登录状态已失效</h2>
          <p>发布权限已过期，自动刷新也未能完成。重新登录不会删除现有电脑配对。</p>
          <div class="dialog-actions">
            ${button("text-button", "取消", "page:02-home.html?screen=ready")}
            ${button("filled-button", "重新登录", "page:01-onboarding.html?screen=qq-login")}
          </div>
        </div>
      </div>
    `;
  }

  /* Settings */
  function settingsBase(content, overlay = "") {
    return `
      <div class="app-scroll">
        <div class="screen-pad">
          ${appBar({ title: "设置", subtitle: "", back: true, backTarget: "page:02-home.html?screen=ready", settings: false })}
          ${content}
        </div>
      </div>
      ${overlay}
    `;
  }

  function settingsMain() {
    return settingsBase(`
      <div class="group-label">账户</div>
      <button class="account-card" data-screen="account" style="width:100%;border:0;color:inherit;text-align:left;">
        <span class="qq-badge">QQ</span>
        <span style="min-width:0;flex:1"><span class="device-name">QQ 账号</span><span class="device-meta">已连接 · 设备注册有效</span></span>
        ${icon("chevron", "chevron")}
      </button>
      <div class="list-group">
        <button class="list-item" data-screen="account">${icon("account")}<span><span class="list-item-title">账号与设备</span><span class="list-item-desc">切换账号、退出登录</span></span>${icon("chevron")}</button>
      </div>
      <div class="group-label">显示</div>
      <div class="list-group">
        <button class="list-item" data-screen="language">${icon("language")}<span><span class="list-item-title">语言</span><span class="list-item-desc">默认跟随 Android 系统</span></span><span class="list-item-value">简体中文</span></button>
        <div class="list-item">${icon("theme")}<span><span class="list-item-title">外观</span><span class="list-item-desc">使用系统浅色或深色方案</span></span><span class="list-item-value">跟随系统</span></div>
      </div>
      <div class="group-label">安全与隐私</div>
      <div class="list-group">
        <button class="list-item" data-screen="security">${icon("shield")}<span><span class="list-item-title">设备访问安全</span><span class="list-item-desc">永久撤销这台手机的注册</span></span>${icon("chevron")}</button>
        <button class="list-item" data-screen="privacy">${icon("lock")}<span><span class="list-item-title">隐私与数据</span><span class="list-item-desc">短信、OTP 与本地密钥说明</span></span>${icon("chevron")}</button>
      </div>
      <div class="group-label">关于</div>
      <div class="list-group">
        <div class="list-item">${icon("info")}<span><span class="list-item-title">鹊信</span><span class="list-item-desc">Pin Glide Android Sender</span></span><span class="list-item-value">1.0</span></div>
      </div>
    `);
  }

  function settingsLanguage() {
    return settingsBase(`
      <div class="group-label" style="margin-top:4px">应用语言</div>
      <div class="list-group">
        <button class="list-item" data-screen="settings"><span class="radio checked"></span><span><span class="list-item-title">跟随系统</span><span class="list-item-desc">使用 Android 系统语言</span></span><span class="list-item-value">推荐</span></button>
        <button class="list-item" data-screen="settings"><span class="radio"></span><span><span class="list-item-title">简体中文</span><span class="list-item-desc">中文界面</span></span></button>
        <button class="list-item" data-screen="settings"><span class="radio"></span><span><span class="list-item-title">English</span><span class="list-item-desc">English interface</span></span></button>
      </div>
      <div class="info-card">${icon("info")}<span>切换语言不会改变账号、配对或设备状态。</span></div>
    `);
  }

  function settingsAccount() {
    return settingsBase(`
      <div class="account-card">
        <span class="qq-badge">QQ</span>
        <span style="min-width:0;flex:1"><span class="device-name">QQ 账号</span><span class="device-meta">已连接 · 服务端已验证</span></span>
        <span class="pill success">有效</span>
      </div>
      <div class="list-group">
        <button class="list-item" data-screen="switch-account">${icon("swap")}<span><span class="list-item-title">切换 QQ 账号</span><span class="list-item-desc">清除本机旧身份与旧配对</span></span>${icon("chevron")}</button>
        <button class="list-item danger-label" data-screen="logout-confirm">${icon("logout")}<span><span class="list-item-title">退出登录</span><span class="list-item-desc">立即停止转发，但保留电脑配对</span></span>${icon("chevron")}</button>
      </div>
      <div class="info-card">${icon("shield")}<span>退出登录会撤销这台 Sender 的访问凭据。电脑之间的共享接收连接不会被关闭。</span></div>
    `);
  }

  function settingsSwitchAccount() {
    return settingsBase(settingsAccountContent(), `
      <div class="scrim dialog-scrim">
        <div class="dialog">
          <div class="dialog-icon error">${icon("swap")}</div>
          <h2>切换 QQ 账号？</h2>
          <p>这台手机的旧身份、旧配对密钥和本地设备关联会被清除。其他 Sender 与电脑的连接不受影响。</p>
          <div class="dialog-actions">
            ${button("text-button", "取消", "account")}
            ${button("danger-button", "继续切换", "page:01-onboarding.html?screen=qq-login")}
          </div>
        </div>
      </div>
    `);
  }

  function settingsAccountContent() {
    return `
      <div class="account-card">
        <span class="qq-badge">QQ</span>
        <span style="min-width:0;flex:1"><span class="device-name">QQ 账号</span><span class="device-meta">已连接 · 服务端已验证</span></span>
        <span class="pill success">有效</span>
      </div>
      <div class="list-group">
        <button class="list-item" data-screen="switch-account">${icon("swap")}<span><span class="list-item-title">切换 QQ 账号</span><span class="list-item-desc">清除本机旧身份与旧配对</span></span>${icon("chevron")}</button>
        <button class="list-item danger-label" data-screen="logout-confirm">${icon("logout")}<span><span class="list-item-title">退出登录</span><span class="list-item-desc">立即停止转发，但保留电脑配对</span></span>${icon("chevron")}</button>
      </div>
      <div class="info-card">${icon("shield")}<span>退出登录会撤销这台 Sender 的访问凭据。电脑之间的共享接收连接不会被关闭。</span></div>
    `;
  }

  function settingsLogoutConfirm() {
    return settingsBase(settingsAccountContent(), `
      <div class="scrim dialog-scrim">
        <div class="dialog">
          <div class="dialog-icon">${icon("logout")}</div>
          <h2>退出登录？</h2>
          <p>鹊信会立即停止本机转发，并尝试撤销服务端凭据。电脑配对和本地配对密钥会保留。</p>
          <div class="dialog-actions">
            ${button("text-button", "取消", "account")}
            ${button("filled-button", "退出登录", "logout-unconfirmed")}
          </div>
        </div>
      </div>
    `);
  }

  function settingsLogoutUnconfirmed() {
    return `
      <div class="screen-fill" style="align-items:center;justify-content:center;text-align:center;">
        <div class="wizard-emblem" style="background:var(--md-warning-container);color:var(--md-warning);">${icon("cloud_off")}</div>
        <div class="eyebrow" style="color:var(--md-warning)">本机已退出</div>
        <h2 class="title-lg">服务端撤销未确认</h2>
        <p class="body-small" style="max-width:312px;margin-top:10px;">这台手机已停止转发，但网络不可用，无法确认服务端是否已撤销访问凭据。请稍后重试。</p>
        <div class="wizard-actions" style="width:100%;margin-top:26px;">
          ${button("filled-button", "重试撤销", "settings", { full: true, iconName: "refresh" })}
          ${button("text-button", "保留本机退出状态", "page:01-onboarding.html?screen=welcome", { full: true })}
        </div>
      </div>
    `;
  }

  function settingsSecurity() {
    return settingsBase(`
      <div class="info-card warning">${icon("warning")}<span>安全撤销需要新的 QQ 账号所有者验证。撤销后，这台手机的旧 Sender 注册不能恢复。</span></div>
      <div class="list-group">
        <button class="list-item danger-label" data-screen="owner-proof">${icon("lock")}<span><span class="list-item-title">永久撤销本机注册</span><span class="list-item-desc">关闭设备注册并移除这台 Sender 的所有 Pairing</span></span>${icon("chevron")}</button>
      </div>
      <div class="group-label">普通管理</div>
      <div class="list-group">
        <button class="list-item" data-href="02-home.html?screen=ready">${icon("computer")}<span><span class="list-item-title">管理电脑配对</span><span class="list-item-desc">移除单台电脑只影响当前 Sender</span></span>${icon("chevron")}</button>
      </div>
    `);
  }

  function settingsOwnerProof() {
    return `
      <div class="screen-fill">
        ${appBar({ title: "安全撤销", subtitle: "", back: true, backTarget: "security", settings: false })}
        <div class="wizard-content">
          <div class="wizard-emblem brand">${appIcon(92)}</div>
          <div class="eyebrow">需要所有者证明</div>
          <h2 class="title-lg">重新验证你的 QQ</h2>
          <p class="body wizard-copy">安全撤销会永久关闭这台 Sender。必须使用与当前账号一致的 QQ 所有者证明，普通访问令牌不能继续。</p>
          <div class="info-card warning" style="width:100%;text-align:left;">${icon("warning")}<span>验证通过后仍需再一次明确确认，才会执行撤销。</span></div>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "使用 QQ 验证", "revoke-confirm", { full: true, iconName: "account" })}
          ${button("text-button", "取消", "security", { full: true })}
        </div>
      </div>
    `;
  }

  function settingsRevokeConfirm() {
    return settingsBase(settingsSecurityContent(), `
      <div class="scrim dialog-scrim">
        <div class="dialog">
          <div class="dialog-icon error">${icon("lock")}</div>
          <h2>永久撤销这台手机？</h2>
          <p>这会关闭当前 Sender 注册，移除它的所有电脑配对并立即停止转发。旧注册不能恢复，只能重新登录并重新配对。</p>
          <div class="dialog-actions">
            ${button("text-button", "取消", "security")}
            ${button("danger-button", "永久撤销", "revoked")}
          </div>
        </div>
      </div>
    `);
  }

  function settingsSecurityContent() {
    return `
      <div class="info-card warning">${icon("warning")}<span>安全撤销需要新的 QQ 账号所有者验证。撤销后，这台手机的旧 Sender 注册不能恢复。</span></div>
      <div class="list-group">
        <button class="list-item danger-label" data-screen="owner-proof">${icon("lock")}<span><span class="list-item-title">永久撤销本机注册</span><span class="list-item-desc">关闭设备注册并移除这台 Sender 的所有 Pairing</span></span>${icon("chevron")}</button>
      </div>
    `;
  }

  function settingsRevoked() {
    return `
      <div class="revoked-screen">
        <span class="empty-icon">${icon("lock")}</span>
        <h2>本机注册已撤销</h2>
        <p>服务端已经关闭这台 Sender 的注册并移除它的配对。旧注册不能恢复，重新登录会创建新的安装注册。</p>
        ${button("filled-button", "重新登录", "page:01-onboarding.html?screen=qq-login", { full: true, iconName: "account" })}
        ${button("text-button", "返回设置", "page:05-settings.html?screen=settings", { full: true })}
      </div>
    `;
  }

  function privacyContent() {
    return `
      <h2 class="title-lg">零数据保留</h2>
      <p class="body muted" style="margin-top:8px;">鹊信不会创建短信或 OTP 的转发历史，也不会把明文上传到鹊信服务。</p>
      <div class="privacy-list">
        <div class="privacy-row">${icon("sms")}<span><strong>短信正文</strong><br>只在当前处理过程中存在于内存，完成或失败后释放。</span></div>
        <div class="privacy-row">${icon("key")}<span><strong>配对密钥与模型凭据</strong><br>由 Android Keystore 保护，不会写入日志、URL 或服务端请求。</span></div>
        <div class="privacy-row">${icon("computer")}<span><strong>服务端只路由密文</strong><br>服务不会读取短信正文、OTP 或客户端加密密钥。</span></div>
        <div class="privacy-row">${icon("info")}<span><strong>本地诊断</strong><br>只保留时间、结果类别、状态码、请求 ID 和目标数量等非内容元数据。</span></div>
      </div>
      <div class="info-card">${icon("shield")}<span>卸载应用或清除应用数据会丢失本机身份与配对密钥，需要重新进行设备配对。</span></div>
    `;
  }

  function settingsPrivacy() {
    return settingsBase(privacyContent());
  }

  if (!PAGES[state.page]) return;
  render();
})();
