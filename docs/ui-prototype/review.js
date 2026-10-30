(() => {
  const PAGE = document.body.dataset.page;
  const params = new URLSearchParams(location.search);
  const state = {
    page: PAGE,
    screen: params.get("screen") || "",
    toggles: {
      home: true,
      office: true
    },
    deviceId: "home",
    homeReturn: "ready",
    removedDeviceIds: [],
    homePresence: "online",
    homeUnknown: false,
    cameraPermission: "granted",
    pairingAlias: params.get("alias") || "我的设备",
    keyFailureId: "",
    aliases: {},
    renameDraft: undefined,
    testPending: params.get("testPreview") === "pending",
    testNotice: ""
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
    { id: "home", receiverId: "receiver-home", pairingId: "pairing-home", name: "我的电脑", online: true, changedAt: "刚刚" },
    { id: "office", receiverId: "receiver-office", pairingId: "pairing-office", name: "办公室电脑", online: true, changedAt: "2 分钟前" }
  ];
  const EXTRA_DEVICES = [
    { id: "study", receiverId: "receiver-study", pairingId: "pairing-study", name: "书房设备", online: true, changedAt: "刚刚" },
    { id: "spare", receiverId: "receiver-spare", pairingId: "pairing-spare", name: "备用设备", online: true, changedAt: "刚刚" },
    { id: "living", receiverId: "receiver-living", pairingId: "pairing-living", name: "客厅设备", online: true, changedAt: "刚刚" }
  ];
  const PAIRED_DEVICE = { id: "new", receiverId: "receiver-new", pairingId: "pairing-new", name: "我的设备" };
  const TEST_OUTCOMES = {
    received: "测试消息已发送",
    offline: "测试消息未发送：目标设备离线",
    rejected: "测试消息未发送：配对已移除",
    send_failed: "投递未确认：发送连接中断",
    ack_timeout: "投递未确认：等待设备确认超时",
    interrupted: "投递未确认：服务处理被中断",
    mixed: "办公室电脑：目标设备离线",
    request_error: "测试消息未发送：请求参数无效",
    uncertain: "投递未确认：无法获取投递结果",
    no_target: "测试消息未发送：没有可用的目标设备",
    auth_expired: "测试消息未发送：登录已失效，请重新登录"
  };
  state.testNotice = TEST_OUTCOMES[params.get("testPreview")] || "";

  const HOME_GROUPS = [
    { id: "overview", label: "首页状态" },
    { id: "permission", label: "短信权限" },
    { id: "camera", label: "相机权限" },
    { id: "login", label: "重新登录" },
    { id: "device", label: "设备管理" },
    { id: "rename", label: "重命名" },
    { id: "remove", label: "移除配对" },
    { id: "revocation", label: "注册撤销" }
  ];
  const HOME_READY_COPY = "新短信会被端到端加密后送往选中的设备。";
  const HOME_HERO_STATES = {
    ready: { title: "转发已就绪", copy: HOME_READY_COPY },
    disabled: { title: "尚未选择接收设备", copy: "开启至少一台设备的接收开关后，鹊信才会转发新短信。", tone: "warning" },
    offline: { title: "接收目标均已离线", copy: "已开启接收的设备当前均离线。目标设备上线后才能接收新短信，离线期间的短信不会补发。", tone: "warning", status: "全部离线" }
  };

  const PAGES = {
    onboarding: {
      number: "01",
      title: "设置向导",
      description: "首次启动、系统权限、QQ 登录与设备配对",
      screens: [
        { id: "welcome", label: "欢迎", title: "欢迎使用鹊信", render: onboardingWelcome },
        { id: "privacy", label: "隐私", title: "隐私与数据处理", render: onboardingPrivacy },
        { id: "sms", label: "短信权限", title: "允许接收短信", render: onboardingSms },
        { id: "sms-system", label: "短信系统弹窗", title: "Android 短信权限", render: () => onboardingSmsPrompt() },
        { id: "sms-denied", label: "短信被拒绝", title: "短信权限未开启", render: onboardingSmsDenied },
        { id: "battery", label: "后台准备", title: "关闭电池优化", render: onboardingBattery },
        { id: "qq-login", label: "QQ 登录", title: "连接你的 QQ", render: onboardingQq },
        { id: "qq-loading", label: "登录中", title: "正在验证授权", render: onboardingQqLoading },
        { id: "qq-error", label: "登录失败", title: "登录未完成", render: onboardingQqError },
        { id: "pair-start", label: "连接设备", title: "连接第一台设备", render: onboardingPairStart },
        { id: "camera-system", label: "相机系统弹窗", title: "Android 相机权限", render: onboardingCameraPrompt },
        { id: "camera-denied", label: "相机被拒绝", title: "相机权限未开启", render: onboardingCameraDenied },
        { id: "camera-blocked", label: "相机永久拒绝", title: "需要前往系统设置", render: onboardingCameraBlocked },
        { id: "scanner", label: "扫描二维码", title: "扫描设备二维码", render: pairingScanner },
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
      description: "转发状态与设备管理",
      screens: [
        { id: "ready", group: "overview", label: "已就绪", title: "转发已就绪", render: homeReady },
        { id: "full", group: "overview", label: "已满5台", title: "我的设备 · 已满5台", render: homeReady },
        { id: "already-added", group: "overview", label: "重复扫码返回", title: "这台设备已添加", render: () => homeReady({ notice: "这台设备已添加" }) },
        { id: "loading", group: "overview", label: "首次加载", title: "正在获取设备", render: () => homeLoading() },
        { id: "pull-down", group: "overview", label: "下拉提示", title: "下拉刷新设备列表", render: () => homeReady({ refreshState: "pulling" }) },
        { id: "pull-release", group: "overview", label: "松开刷新", title: "松开刷新设备列表", render: () => homeReady({ refreshState: "release" }) },
        { id: "refreshing", group: "overview", label: "正在刷新", title: "正在刷新设备列表", render: () => homeLoading(true) },
        { id: "load-error", group: "overview", label: "加载失败", title: "暂时无法获取设备", render: () => homeLoadError() },
        { id: "refresh-error", group: "overview", label: "更新失败", title: "设备状态未更新", render: () => homeLoadError(true) },
        { id: "offline", group: "overview", label: "部分离线", title: "有设备离线", render: homeReady },
        { id: "all-offline", group: "overview", label: "全部离线", title: "所有设备离线", render: homeReady },
        { id: "all-disabled", group: "overview", label: "全部关闭接收", title: "尚未选择接收设备", render: homeReady },
        { id: "key-error", group: "overview", label: "本地密钥异常", title: "密钥丢失或损坏", render: homeReady },
        { id: "empty", group: "overview", label: "无设备", title: "等待第一台设备", render: () => homeEmpty() },
        { id: "permission-lost", group: "permission", label: "权限失效", title: "转发已暂停", render: homePermissionLost },
        { id: "permission-system", group: "permission", label: "系统授权", title: "Android 短信权限（系统界面示意）", render: () => onboardingSmsPrompt(homePermissionLost(), "permission-restored", "home:sms-denied") },
        { id: "permission-no-prompt", group: "permission", label: "不再询问", title: "权限失效 · 直接前往系统设置", render: () => homePermissionLost({ noPrompt: true }) },
        { id: "permission-denied-toast", group: "permission", label: "未授权提示", title: "权限失效 · 短信权限未开启", render: () => homePermissionLost({ notice: "短信权限未开启" }) },
        { id: "permission-settings-error-toast", group: "permission", label: "设置失败提示", title: "权限失效 · 手动开启权限", render: () => homePermissionLost({ notice: "请手动打开鹊信应用设置，授予短信权限", noPrompt: true }) },
        { id: "permission-restored", group: "permission", label: "权限已恢复", title: "短信权限已恢复", render: () => homeReady({ notice: "短信权限已开启，转发已恢复" }) },
        { id: "camera-system", group: "camera", label: "相机系统弹窗", title: "Android 相机权限（系统界面示意）", render: () => homeCameraPermission("system") },
        { id: "camera-denied", group: "camera", label: "相机被拒绝", title: "相机权限未开启 · 留在首页", render: () => homeCameraPermission("denied") },
        { id: "camera-blocked", group: "camera", label: "相机永久拒绝", title: "需要前往系统设置 · 留在首页", render: () => homeCameraPermission("blocked") },
        { id: "login-expired", group: "login", label: "登录失效", title: "需要重新登录", render: homeLoginExpired },
        { id: "login-loading", group: "login", label: "验证中", title: "正在验证 QQ 授权", render: () => homeLoginFlow("loading") },
        { id: "login-cancelled", group: "login", label: "授权取消", title: "登录未完成", render: () => homeLoginFlow("cancelled") },
        { id: "login-error", group: "login", label: "登录失败", title: "暂时无法登录", render: () => homeLoginFlow("error") },
        { id: "login-account-mismatch", group: "login", label: "账号不一致", title: "请使用原 QQ 账号", render: () => homeLoginFlow("mismatch") },
        { id: "login-restored", group: "login", label: "登录已恢复", title: "原有配对已保留", render: () => homeReady({ notice: "登录已恢复，原有配对已保留" }) },
        { id: "device-sheet", group: "device", label: "设备详情", title: "管理设备", render: () => homeDeviceSheet() },
        { id: "device-offline", group: "device", label: "离线设备", title: "管理离线设备", render: () => homeDeviceSheet({ offline: true }) },
        { id: "device-disabled", group: "device", label: "关闭接收", title: "已关闭这台设备的接收", render: () => homeDeviceSheet({ receiving: false }) },
        { id: "device-key-error", group: "device", label: "本地密钥异常", title: "密钥丢失或损坏", render: homeDeviceSheet },
        { id: "device-gone", group: "device", label: "配对已失效", title: "这台设备的配对已不存在", render: homeDeviceGone },
        { id: "rename-device", group: "rename", label: "编辑名称", title: "重命名设备", render: () => homeRenameDevice() },
        { id: "rename-blank", group: "rename", label: "名称为空", title: "设备名称不能为空", render: () => homeRenameDevice({ value: "", error: "请输入设备名称" }) },
        { id: "rename-too-long", group: "rename", label: "名称过长", title: "设备名称不能超过 40 字", render: () => homeRenameDevice({ value: "a".repeat(41), error: "设备名称不能超过 40 字" }) },
        { id: "rename-duplicate", group: "rename", label: "名称重复", title: "设备名称已存在", render: () => homeRenameDevice({ value: "办公室电脑", error: "已有一台同名设备，请换一个名称" }) },
        { id: "rename-saving", group: "rename", label: "保存中", title: "正在保存名称", render: () => homeRenameDevice({ value: "家用设备", saving: true }) },
        { id: "rename-error", group: "rename", label: "保存失败", title: "名称未保存", render: () => homeRenameDevice({ value: "家用设备", failed: true }) },
        { id: "rename-saved", group: "rename", label: "保存成功", title: "设备名称已更新", render: () => homeDeviceSheet({ renamed: true }) },
        { id: "remove-confirm", group: "remove", label: "移除确认", title: "移除配对确认", render: () => homeRemoveConfirm() },
        { id: "removing", group: "remove", label: "移除中", title: "正在移除配对", render: () => homeRemoveConfirm({ status: "pending" }) },
        { id: "remove-error", group: "remove", label: "移除失败", title: "移除失败", render: () => homeRemoveConfirm({ status: "failed" }) },
        { id: "removed", group: "remove", label: "移除完成", title: "配对已移除", render: homeRemoved },
        { id: "removed-last", group: "remove", label: "最后一台已移除", title: "已无接收设备", render: homeRemoved },
        { id: "revoked", group: "revocation", label: "已撤销", title: "设备注册已撤销", render: homeRevoked }
      ]
    },
    pairing: {
      number: "03",
      title: "设备配对",
      description: "扫码添加设备、配对确认与连接进度",
      screens: [
        { id: "scanner", label: "扫描二维码", title: "扫描设备二维码", render: pairingScanner },
        { id: "invalid-qr", label: "无效二维码", title: "不是鹊信二维码", render: pairingInvalidQr },
        { id: "consent", label: "配对确认", title: "确认添加新设备", render: pairingConsent },
        { id: "consent-blank", label: "名称为空", title: "设备名称不能为空", render: () => pairingConsent({ alias: "", error: "请输入设备名称", confirmDisabled: true }) },
        { id: "consent-duplicate", label: "名称重复", title: "设备名称已存在", render: () => pairingConsent({ alias: "办公室电脑", error: "已有一台同名设备，请换一个名称", confirmDisabled: true }) },
        { id: "progress", label: "打开通道", title: "建立加密连接", render: () => pairingProgress() },
        { id: "progress-identity", label: "生成身份", title: "建立加密连接", render: () => pairingProgress(1) },
        { id: "progress-key", label: "交换密钥", title: "建立加密连接", render: () => pairingProgress(2) },
        { id: "progress-confirm", label: "确认配对", title: "建立加密连接", render: () => pairingProgress(3) },
        { id: "expired", label: "会话结束", title: "二维码已失效", render: pairingExpired },
        { id: "failure-preserved", label: "配对失败", title: "配对未完成", render: pairingFailurePreserved },
        { id: "success", label: "完成", title: "配对完成", render: pairingSuccess }
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

  const PAGE_ORDER = ["onboarding", "home", "pairing", "settings"];
  const PAGE_FILES = {
    onboarding: "01-onboarding.html",
    home: "02-home.html",
    pairing: "03-pairing.html",
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
    if (target.startsWith("home:")) return `data-home-action="${target.slice(5)}"`;
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
    if (add) extras.push(iconButton("add", "添加设备", add, "filled"));
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

  function scanDemo() {
    if (state.page !== "pairing" || state.screen !== "scanner") return "";
    const scanQuery = new URLSearchParams({ screen: "consent" });
    params.forEach((value, key) => {
      if (key === "enabled" || key.startsWith("alias-")) scanQuery.set(key, value);
    });
    return `<div class="scan-demo" aria-label="扫码结果演示">
      <span>扫码结果演示</span>
      <a class="screen-tab" href="03-pairing.html?${scanQuery}">新设备</a>
      ${DEVICES.map((device) => {
        const query = new URLSearchParams(scanQuery);
        query.set("screen", "already-added");
        query.set("device", device.id);
        return `<a class="screen-tab" data-scan-demo="${device.id}" href="02-home.html?${query}">已添加：${escapeHtml(params.get(`alias-${device.id}`) || device.name)}</a>`;
      }).join("")}
    </div>`;
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

      ${state.page === "home" ? `<div class="screen-groups" aria-label="操作分组">${HOME_GROUPS.map((group) => `<button class="screen-tab ${group.id === screen.group ? "is-active" : ""}" data-review-screen="${page.screens.find((item) => item.group === group.id).id}">${group.label}</button>`).join("")}</div>` : ""}
      <div class="screen-tabs" aria-label="界面状态">
        ${page.screens.filter((item) => state.page !== "home" || item.group === screen.group).map((item) => `<button class="screen-tab ${item.id === state.screen ? "is-active" : ""}" data-review-screen="${item.id}">${item.label}</button>`).join("")}
      </div>${scanDemo()}${testMessageDemo()}
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
      params.set("screen", state.screen);
      history.replaceState(null, "", `${location.pathname}?${params}`);
    }
    document.getElementById("previousScreen").addEventListener("click", () => moveScreen(-1));
    document.getElementById("nextScreen").addEventListener("click", () => moveScreen(1));
    document.querySelectorAll("[data-review-screen]").forEach((button) => {
      button.addEventListener("click", () => {
        state.screen = button.dataset.reviewScreen;
        if (state.page === "home") resetHomeFixture(state.screen);
        render();
      });
    });
  }

  function moveScreen(delta) {
    const screens = PAGES[state.page].screens;
    const current = screens.findIndex((item) => item.id === state.screen);
    const next = (current + delta + screens.length) % screens.length;
    state.screen = screens[next].id;
    if (state.page === "home") resetHomeFixture(state.screen);
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

  function systemPermissionPrompt({ title, iconName, allowTarget, denyTarget }) {
    return `
      <div class="scrim dialog-scrim system-permission-scrim">
        <div class="system-permission-dialog" role="dialog" aria-modal="true">
          <span class="system-permission-icon">${icon(iconName)}</span>
          <h2>${title}</h2>
          <div class="system-permission-actions">
            <button class="system-permission-button" ${actionAttrs(allowTarget)}>允许</button>
            <button class="system-permission-button" ${actionAttrs(denyTarget)}>不允许</button>
          </div>
        </div>
      </div>
    `;
  }

  function toastMessage(message) {
    return `<div class="toast-message" role="status">${message}</div>`;
  }

  function snackbarMessage(message, actionLabel = "", actionTarget = "") {
    return `
      <div class="snackbar above-actions" role="status">
        ${icon("info")}
        <span style="flex:1">${message}</span>
        ${actionLabel ? `<button class="snackbar-action"${actionTarget ? ` ${actionAttrs(actionTarget)}` : ""}>${actionLabel}</button>` : ""}
      </div>
    `;
  }

  function onboardingSmsPrompt(background = onboardingSms(), allowTarget, denyTarget) {
    return background + systemPermissionPrompt({
      title: "要允许“鹊信”发送和查看短信吗？",
      iconName: "sms",
      allowTarget,
      denyTarget
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
      copy: "使用 QQ 登录账号、管理设备和授权加密转发。",
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
      copy: "在接收端打开鹊信并显示二维码，然后扫描并确认。完成后，当前设备就可以开始转发短信。",
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
      copy: "确认后，目标设备会开始接收你选中的短信，双方会建立一条独立的加密配对。",
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
  function deviceRows({ devices = DEVICES, officeOffline = false, allOffline = false, allDisabled = false, unknown = false, disabled = false, highlightedDeviceId = "" } = {}) {
    return `<div class="device-list">${devices.map((device) => {
      const online = !allOffline && !(officeOffline && device.id === "office") && device.online;
      const name = escapeHtml(state.aliases[device.id] || device.name);
      const highlighted = device.id === highlightedDeviceId;
      return `
        <div class="device-row${highlighted ? " is-highlighted" : ""}" ${disabled ? "" : `data-screen="device-sheet" data-device="${device.id}"`}>
          <button class="device-detail" data-screen="device-sheet" data-device="${device.id}" aria-label="管理${name}" ${disabled ? "disabled" : ""}>
            <span class="device-icon">${icon("computer")}</span>
            <span class="device-copy">
              <span class="device-name">${name}</span>
              <span class="device-meta">${unknown ? "状态待更新" : `<span class="presence ${online ? "" : "offline"}"></span>${online ? "在线" : "离线"} · 状态变化：${device.changedAt}`}</span>
              <span class="device-scan-hint">刚刚扫描的设备</span>
              ${state.keyFailureId === device.id ? '<span class="device-key-error">密钥丢失或损坏</span>' : ""}
            </span>
          </button>
          <button class="switch-control" role="switch" aria-checked="${!allDisabled && state.toggles[device.id]}" aria-label="${name}接收短信" data-toggle="${device.id}" ${disabled || state.keyFailureId === device.id ? "disabled" : ""}></button>
        </div>
      `;
    }).join("")}</div>`;
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
  }

  function homeDeviceList(options = {}) {
    options = {
      devices: DEVICES.filter((device) => !state.removedDeviceIds.includes(device.id)),
      officeOffline: state.homePresence === "partial",
      allOffline: state.homePresence === "offline",
      unknown: state.homeUnknown,
      highlightedDeviceId: state.page === "home" && state.screen === "already-added" ? state.deviceId : "",
      ...options
    };
    const mode = options.refreshState || "";
    const count = options.devices.length;
    const full = count >= 5;
    const labels = { pulling: "下拉刷新", release: "松开刷新", refreshing: "正在刷新" };
    return `
      <section class="device-refresh" ${mode === "refreshing" ? 'data-refreshing="true" aria-busy="true"' : ""} ${state.page === "home" && !options.disabled ? 'data-refresh-list tabindex="0" role="region" aria-label="设备列表，可下拉刷新或按 Enter 刷新"' : ""}>
        <div class="pull-refresh-hint" data-pull-hint role="status" ${mode ? "" : "hidden"}>
          ${mode === "refreshing" ? '<span class="small-spinner" aria-hidden="true"></span>' : icon("refresh")}
          <span data-pull-label>${labels[mode] || "下拉刷新"}</span>
        </div>
        <div class="section-head"><h3>我的设备</h3><span class="section-count${full ? " is-full" : ""}">${full ? "已满5台" : `${count} 台`}</span></div>
        ${deviceRows(options)}
      </section>
    `;
  }

  function hero({
    title,
    copy,
    tone = "success",
    status = ""
  }) {
    const background = tone === "warning"
      ? "background:linear-gradient(135deg,var(--md-warning-container),var(--md-surface-2));"
      : tone === "error"
        ? "background:linear-gradient(135deg,var(--md-error-container),var(--md-surface-2));"
        : "";
    return `
      <div class="status-hero" data-tone="${tone}" style="${background}">
        <div class="status-hero-head">
          <div><h2>${title}</h2><p>${copy}</p></div>
          ${status ? `<span class="pill ${tone}">${status}</span>` : ""}
        </div>
      </div>
    `;
  }

  function homeBase({
    heroMarkup,
    content = homeDeviceList(),
    overlay = "",
    fab = DEVICES.filter((device) => !state.removedDeviceIds.includes(device.id)).length < 5,
    appOptions = {}
  }) {
    return `
      <div class="app-scroll">
        <div class="screen-pad ${fab ? "with-fab" : ""}">
          ${appBar({ settings: true, ...appOptions })}
          ${heroMarkup}
          ${content}
        </div>
        ${fab ? `<button class="extended-fab" data-home-action="add-device">${icon("add")}添加设备</button>` : ""}
      </div>
      ${overlay}
    `;
  }

  function homeReady({ notice = "", refreshState = "" } = {}) {
    const devices = DEVICES.filter((device) => !state.removedDeviceIds.includes(device.id));
    if (!devices.length) return homeEmpty(notice);
    const selected = devices.filter((device) => state.toggles[device.id]);
    const allDisabled = !selected.length;
    const allOffline = selected.length > 0 && selected.every((device) => state.homePresence === "offline" || (state.homePresence === "partial" && device.id === "office"));
    return homeBase({
      heroMarkup: hero(HOME_HERO_STATES[allDisabled ? "disabled" : allOffline ? "offline" : "ready"]),
      content: homeDeviceList({ allDisabled, refreshState }),
      overlay: notice ? toastMessage(notice) : ""
    });
  }

  function homeCameraPermission(status) {
    const background = state.homeReturn === "empty" ? homeEmpty() : homeBackground();
    if (status === "system") {
      return background + systemPermissionPrompt({
        title: "要允许“鹊信”拍照和录制视频吗？",
        iconName: "camera",
        allowTarget: "home:camera-granted",
        denyTarget: "home:camera-denied"
      });
    }
    return background + (status === "denied"
      ? toastMessage("相机权限未开启")
      : snackbarMessage("相机权限已被永久拒绝", "去设置", "home:open-camera-settings"));
  }

  function homePermissionLost({ notice = "", noPrompt = false } = {}) {
    return homeBase({
      heroMarkup: hero({
        title: "转发已暂停",
        copy: "短信权限已被关闭，新短信不会进入处理流程。",
        tone: "warning",
        status: "权限失效"
      }),
      content: `
        <div class="info-card warning">${icon("warning")}<span>重新开启短信权限后，转发会自动恢复；无需重新登录或重新配对。</span></div>
        <div style="margin-top:12px">${button("filled-button", "重新开启短信权限", noPrompt ? "home:open-sms-settings" : "home:request-sms", { full: true, iconName: "settings" })}</div>
        ${homeDeviceList()}
      `,
      overlay: notice ? toastMessage(notice) : ""
    });
  }

  function homeLoginExpired() {
    return homeBase({
      heroMarkup: hero({
        title: "需要重新登录",
        copy: "登录凭据已失效，无法自动续期。使用原 QQ 账号重新登录，可保留当前设备已有的配对。",
        tone: "warning",
        status: "登录失效"
      }),
      content: `
        <div style="margin-top:12px">${button("filled-button", "使用 QQ 重新登录", "login-loading", { full: true, iconName: "account" })}</div>
        ${homeDeviceList({ unknown: true, disabled: true })}
      `,
      fab: false
    });
  }

  function homeEmpty(notice = "") {
    return homeBase({
      heroMarkup: hero({
        title: "账号已连接",
        copy: "添加第一台设备后，鹊信才会开始转发新短信。",
        status: "等待设备",
        tone: "warning"
      }),
      content: `
        <div class="empty-state" style="margin-top:14px;">
          <span class="empty-icon">${icon("computer")}</span>
          <h3>还没有设备</h3>
          <p>打开目标设备上的鹊信并显示二维码，然后用当前设备扫描。</p>
          ${button("tonal-button", "添加第一台设备", "home:add-device", { iconName: "qr" })}
        </div>
      `,
      fab: false,
      overlay: notice ? toastMessage(notice) : ""
    });
  }

  function homeDeviceSheet({ offline = state.homeReturn === "all-offline" || (state.homeReturn === "offline" && state.deviceId === "office"), receiving = state.toggles[state.deviceId], renamed = false } = {}) {
    const device = DEVICES.find((item) => item.id === state.deviceId);
    const name = escapeHtml(renamed ? state.aliases[device.id] || "家用设备" : state.aliases[device.id] || device.name);
    return homeBackground() + `
        <div class="scrim sheet-scrim">
          <div class="sheet" role="dialog" aria-modal="true" aria-label="${name}">
            <div class="sheet-handle"></div>
            <div class="sheet-head">
              <div><h2 class="sheet-title">${name}</h2><p class="body-small" style="margin-top:5px;">${state.homeUnknown ? "状态待更新" : `${offline ? "离线" : "在线"} · 状态变化：${device.changedAt}`}</p></div>
              <span class="device-icon">${icon("computer")}</span>
            </div>
            ${state.keyFailureId === device.id ? `<div class="info-card error">${icon("error")}<span>密钥丢失或损坏，已关闭此设备转发。</span></div>` : ""}
            <div class="list-group" style="margin-top:0">
              <div class="list-item">
                ${icon("sms")}
                <span><span class="list-item-title">接收短信</span><span class="list-item-desc">关闭后仍保留这台设备的配对</span></span>
                <button class="switch-control" role="switch" aria-checked="${receiving}" aria-label="接收短信" data-toggle="${device.id}" ${state.keyFailureId === device.id ? "disabled" : ""}></button>
              </div>
              <button class="list-item" data-test-message aria-busy="${state.testPending}" ${state.keyFailureId === device.id || state.testPending ? 'disabled aria-disabled="true"' : ""}>${icon("send")}<span class="list-item-title">${state.testPending ? "正在发送…" : "发送测试消息"}</span></button>
              <button class="list-item" data-screen="rename-device">${icon("edit")}<span class="list-item-title">重命名设备</span>${icon("chevron")}</button>
              <button class="list-item danger-label" data-screen="remove-confirm">${icon("trash")}<span class="list-item-title">移除这台设备的配对</span>${icon("chevron")}</button>
            </div>
            ${button("outlined-button", "关闭", state.homeReturn, { full: true })}
            ${state.testNotice ? toastMessage(escapeHtml(state.testNotice)) : ""}
          </div>
        </div>
        ${renamed ? toastMessage("设备名称已更新") : ""}
      `;
  }

  function homeRenameDevice({ value = state.renameDraft ?? state.aliases[state.deviceId] ?? DEVICES.find((item) => item.id === state.deviceId).name, error = "", saving = false, failed = false } = {}) {
    return homeBackground() + `
        <div class="scrim sheet-scrim">
          <div class="sheet" role="dialog" aria-modal="true" aria-label="重命名设备">
            <div class="sheet-handle"></div>
            <div class="sheet-head">
              <div><h2 class="sheet-title">重命名设备</h2><p class="body-small" style="margin-top:5px;">别名只对当前设备可见</p></div>
              <span class="device-icon">${icon("computer")}</span>
            </div>
            <label class="alias-field">
              <span class="alias-label">设备名称</span>
              <input class="alias-input ${error ? "is-invalid" : ""}" data-rename-input value="${escapeHtml(value)}" autocomplete="off" spellcheck="false" aria-label="设备名称" aria-invalid="${Boolean(error)}" required ${saving ? "disabled" : ""}>
              <span class="${error ? "alias-error" : "alias-help"}" data-rename-help aria-live="polite">${error || "不能为空，也不能与本机已配对的设备重名。"}</span>
            </label>
            ${failed ? `<div class="info-card error">${icon("error")}<span>名称保存失败，输入已保留。请检查网络后重试；设备列表会核对实际保存结果。</span></div>` : ""}
            <div class="half-row" style="margin-top:18px;">
              ${button("outlined-button", "取消", "device-sheet", { full: true, disabled: saving })}
              <button class="filled-button full" data-home-action="save-alias" ${error || saving ? "disabled" : ""}>${saving ? `<span class="small-spinner" aria-hidden="true"></span>` : ""}<span>${saving ? "正在保存" : failed ? "重新保存" : "保存"}</span></button>
            </div>
          </div>
        </div>
      `;
  }

  function homeRemoveConfirm({ status = "confirm" } = {}) {
    const pending = status === "pending";
    const failed = status === "failed";
    const name = escapeHtml(state.aliases[state.deviceId] || DEVICES.find((item) => item.id === state.deviceId).name);
    return homeBackground() + `
        <div class="scrim dialog-scrim">
          <div class="dialog" role="dialog" aria-modal="true" aria-label="移除配对">
            <div class="dialog-icon error">${icon("trash")}</div>
            <h2>${pending ? "正在移除配对" : `移除“${name}”？`}</h2>
            <p>当前设备会停止向目标设备发送短信。目标设备和其他设备的配对不会被关闭。</p>
            ${failed ? `<div class="info-card error">${icon("error")}<span>移除失败，已关闭此设备转发。请检查网络后重试；设备列表会在下次刷新时核对配对状态。</span></div>` : ""}
            ${pending ? `<div class="home-pending" role="status"><span class="small-spinner" aria-hidden="true"></span>正在提交，请稍候</div>` : ""}
            <div class="dialog-actions">
              ${button("text-button", "取消", "device-sheet", { disabled: pending })}
              ${button("danger-button", failed ? "重试移除" : "移除配对", "home:remove-pairing", { disabled: pending })}
            </div>
          </div>
        </div>
      `;
  }

  function homeRemoved() {
    return homeBackground("配对已移除");
  }

  function homeRevoked() {
    return `
      <div class="revoked-screen home-revoked">
        <div class="home-revoked-content">
          <span class="empty-icon">${icon("lock")}</span>
          <h2>设备注册已撤销</h2>
          <p>当前设备的旧注册已被安全撤销，不能恢复。重新登录会创建新的设备注册，之后需要重新配对设备。</p>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", "退出并重新登录", "page:01-onboarding.html?screen=qq-login", { full: true, iconName: "account" })}
        </div>
      </div>
    `;
  }

  function homeBackground(notice = "") {
    if (["permission-lost", "permission-no-prompt", "permission-denied-toast", "permission-settings-error-toast"].includes(state.homeReturn)) {
      return homePermissionLost({ notice, noPrompt: state.homeReturn !== "permission-lost" && state.homeReturn !== "permission-denied-toast" });
    }
    if (state.removedDeviceIds.length === DEVICES.length) return homeEmpty(notice);
    if (state.homeReturn === "refreshing") return homeLoading(true) + (notice ? toastMessage(notice) : "");
    if (state.homeReturn === "refresh-error") return homeLoadError(true) + (notice ? toastMessage(notice) : "");
    return homeReady({ notice });
  }

  function homeFlowPage({ title, copy, iconName = "info", busy = false, brand = false, back = "ready", actions = "" }) {
    return `
      <div class="screen-fill">
        ${appBar({ title: "鹊信", subtitle: "", back: true, backTarget: back, settings: false })}
        <div class="wizard-content">
          <div class="wizard-emblem ${busy ? "spinner" : ""}">${brand ? qqMark(92) : icon(iconName)}</div>
          <h2 class="title-lg">${title}</h2>
          <p class="body wizard-copy">${copy}</p>
        </div>
        <div class="wizard-actions">${actions}</div>
      </div>
    `;
  }

  function homeLoading(cached = false) {
    if (cached) {
      return homeReady({ refreshState: "refreshing" });
    }
    return homeBase({
      heroMarkup: hero({ title: "正在准备转发", copy: "正在获取服务端返回的设备和配对状态。" }),
      content: `<div class="empty-state"><span class="small-spinner" aria-hidden="true"></span><p role="status">正在获取设备列表</p></div>`,
      fab: false
    });
  }

  function homeLoadError(cached = false) {
    const devices = DEVICES.filter((device) => !state.removedDeviceIds.includes(device.id));
    const allDisabled = cached && devices.length > 0 && devices.every((device) => !state.toggles[device.id]);
    return homeBase({
      heroMarkup: hero(allDisabled ? HOME_HERO_STATES.disabled : { title: cached ? "设备状态未更新" : "暂时无法获取设备", copy: cached ? "网络或服务暂时不可用。请检查网络后下拉设备列表重试。" : "网络或服务暂时不可用。请检查网络，回到前台时会自动重新获取。", tone: "warning" }),
      content: cached
        ? `<div class="info-card warning">${icon("info")}<span>已有配对仍保留。当前无法确认设备是否在线。</span></div>${homeDeviceList({ unknown: true })}`
        : `<section class="device-refresh" data-refresh-list tabindex="0" role="region" aria-label="设备列表，可下拉刷新或按 Enter 刷新"><div class="pull-refresh-hint" data-pull-hint role="status" hidden>${icon("refresh")}<span data-pull-label>下拉刷新</span></div><div class="empty-state"><span class="empty-icon">${icon("cloud_off")}</span><h3>设备列表暂不可用</h3><p>下拉刷新设备列表。这不代表配对已被删除，也不代表登录失效。</p></div></section>`,
      fab: false
    });
  }

  function homeLoginFlow(stage) {
    const busy = stage === "loading";
    const views = {
      loading: ["正在验证 QQ 授权", "请稍候，原有配对会保留。"],
      cancelled: ["登录未完成", "QQ 授权已取消，转发仍暂停。原有配对和名称未改变。"],
      error: ["暂时无法登录", "网络或服务暂时不可用。原有配对仍保留，请稍后重新登录。"],
      mismatch: ["请使用原 QQ 账号", "本次授权无法恢复当前设备的原注册。请确认使用原 QQ 账号重试；若要切换账号，请返回设置操作。"]
    };
    const [title, copy] = views[stage];
    return homeFlowPage({
      title,
      copy,
      brand: true,
      busy,
      back: "login-expired",
      actions: busy
        ? button("text-button", "取消", "login-cancelled", { full: true })
        : button("filled-button", "使用 QQ 重新登录", "login-loading", { full: true, iconName: "account" })
    });
  }

  function homeDeviceGone() {
    return homeFlowPage({
      title: "这台设备的配对已不存在",
      copy: "配对可能已在其他位置被移除或撤销。当前操作没有继续执行，需要重新配对后才能向它转发。",
      iconName: "computer",
      back: "removed",
      actions: button("filled-button", "返回设备列表", "removed", { full: true }) + button("text-button", "重新添加设备", "page:03-pairing.html?screen=scanner", { full: true })
    });
  }

  function validateHomeAlias(value) {
    const name = value.trim();
    if (!name) return "请输入设备名称";
    if (Array.from(name).length > 40) return "设备名称不能超过 40 字";
    if (DEVICES.some((device) => !state.removedDeviceIds.includes(device.id) && device.id !== state.deviceId && (state.aliases[device.id] || device.name).toLowerCase() === name.toLowerCase())) {
      return "已有一台同名设备，请换一个名称";
    }
    return "";
  }

  function resetHomeFixture(screen) {
    DEVICES.splice(2, 3, ...(screen === "full" ? EXTRA_DEVICES : []));
    state.cameraPermission = { "camera-system": "prompt", "camera-denied": "prompt", "camera-blocked": "blocked" }[screen] || "granted";
    state.deviceId = ["device-offline", "already-added"].includes(screen) ? "office" : "home";
    state.homeReturn = screen === "device-offline" ? "offline" : "ready";
    state.removedDeviceIds = screen === "removed-last" ? ["home", "office"] : screen === "removed" ? ["home"] : [];
    state.homePresence = screen === "all-offline" ? "offline" : ["offline", "device-offline"].includes(screen) ? "partial" : "online";
    state.homeUnknown = ["refreshing", "refresh-error"].includes(screen);
    state.keyFailureId = ["key-error", "device-key-error"].includes(screen) ? "home" : "";
    if (screen === "device-key-error") state.homeReturn = "key-error";
    state.aliases = screen === "rename-saved" ? { home: "家用设备" } : {};
    state.toggles = Object.fromEntries(DEVICES.map((device) => [device.id, screen !== "all-disabled" && !(screen === "device-disabled" && device.id === "home")]));
    if (state.keyFailureId || ["removing", "remove-error"].includes(screen)) state.toggles.home = false;
    state.renameDraft = undefined;
  }

  function updateHomeAlias(value) {
    state.renameDraft = value;
    const error = validateHomeAlias(value);
    document.querySelectorAll("[data-rename-input]").forEach((input) => {
      input.value = value;
      input.classList.toggle("is-invalid", Boolean(error));
      input.setAttribute("aria-invalid", String(Boolean(error)));
    });
    document.querySelectorAll("[data-rename-help]").forEach((help) => {
      help.textContent = error || "不能为空，也不能与本机已配对的设备重名。";
      help.className = error ? "alias-error" : "alias-help";
    });
    document.querySelectorAll('[data-home-action="save-alias"]').forEach((save) => { save.disabled = Boolean(error); });
  }

  function handleHomePermissionAction(action) {
    // Android settings are an external destination, not another app-owned preview page.
    if (action === "open-sms-settings" || action === "open-camera-settings") return true;
    if (action === "camera-granted" || (action === "add-device" && state.cameraPermission === "granted")) {
      state.cameraPermission = "granted";
      const query = new URLSearchParams({ screen: "scanner", enabled: DEVICES.filter((device) => state.toggles[device.id]).map((device) => device.id).join(",") });
      DEVICES.forEach((device) => {
        if (state.aliases[device.id]) query.set(`alias-${device.id}`, state.aliases[device.id]);
      });
      location.href = `${PAGE_FILES.pairing}?${query}`;
      return true;
    }
    if (action === "add-device") {
      if (!state.screen.startsWith("camera-")) state.homeReturn = state.screen;
      state.screen = state.cameraPermission === "blocked" ? "camera-blocked" : "camera-system";
      return true;
    }
    const destinations = {
      "request-sms": "permission-system",
      "sms-granted": "permission-restored",
      "sms-denied": "permission-denied-toast",
      "sms-settings-error": "permission-settings-error-toast",
      "camera-denied": "camera-denied"
    };
    if (!destinations[action]) return false;
    state.screen = destinations[action];
    return true;
  }

  // Preview actions only change in-memory fixtures; no service or device permission is changed.
  function handleHomeAction(event) {
    if (state.page !== "home") return;
    if (state.page === "home" && performance.now() < homeIgnoreClickUntil) return;
    if (state.page === "home" && event.target.matches(".scrim")) {
      dismissHomeOverlay();
      return;
    }
    const control = event.target.closest(".app-viewport button, .app-viewport .device-row[data-device]");
    if (!control || control.disabled) return;
    if (control.dataset.href) {
      location.href = control.dataset.href;
      return;
    }
    if (state.page !== "home") return;
    if (handleHomePermissionAction(control.dataset.homeAction)) {
      render();
      return;
    }
    if (control.dataset.toggle) {
      if (state.screen === "all-disabled") state.toggles = { home: false, office: false };
      state.toggles[control.dataset.toggle] = control.getAttribute("aria-checked") !== "true";
      if (state.screen === "device-disabled") state.screen = "device-sheet";
      if (state.screen === "all-disabled") state.screen = "ready";
      render();
      return;
    }
    if (control.dataset.homeAction === "remove-pairing") {
      state.toggles[state.deviceId] = false;
      state.removedDeviceIds.push(state.deviceId);
      state.screen = state.removedDeviceIds.length === DEVICES.length ? "removed-last" : "removed";
    } else if (control.dataset.homeAction === "save-alias") {
      const value = control.closest(".sheet").querySelector("[data-rename-input]").value;
      updateHomeAlias(value);
      if (validateHomeAlias(value)) return;
      state.aliases[state.deviceId] = value.trim();
      state.screen = "rename-saved";
    } else if (control.dataset.screen) {
      if (control.dataset.screen === "removed" && state.screen === "device-gone") state.removedDeviceIds.push(state.deviceId);
      if (control.dataset.device) {
        state.deviceId = control.dataset.device;
        state.homeReturn = state.screen;
      }
      state.screen = control.dataset.screen;
      state.renameDraft = undefined;
    } else {
      return;
    }
    render();
  }

  function dismissHomeOverlay() {
    if (["rename-saving", "removing"].includes(state.screen)) return;
    if (["rename-device", "rename-blank", "rename-too-long", "rename-duplicate", "rename-error", "remove-confirm", "remove-error"].includes(state.screen)) {
      state.screen = "device-sheet";
    } else if (["device-sheet", "device-offline", "device-disabled", "device-key-error", "rename-saved"].includes(state.screen)) {
      state.screen = state.homeReturn;
    } else {
      return;
    }
    state.renameDraft = undefined;
    render();
  }

  let homePull;
  let homeIgnoreClickUntil = 0;

  function startHomePull(event) {
    if (state.page !== "home" || (event.type === "pointerdown" && (event.pointerType === "touch" || event.button !== 0))) return;
    const list = event.target.closest("[data-refresh-list]");
    if (!list || list.dataset.refreshing || event.target.closest("input,[data-toggle]") || list.closest(".app-scroll").scrollTop > 0) return;
    homePull = { list, start: event.touches?.[0]?.clientY ?? event.clientY, distance: 0 };
  }

  function moveHomePull(event) {
    if (!homePull || (event.type === "pointermove" && event.pointerType === "touch")) return;
    homePull.distance = Math.max(0, (event.touches?.[0]?.clientY ?? event.clientY) - homePull.start);
    if (!homePull.distance) return;
    if (event.cancelable) event.preventDefault();
    const hint = homePull.list.querySelector("[data-pull-hint]");
    hint.hidden = false;
    hint.style.height = `${Math.min(homePull.distance, 64)}px`;
    hint.querySelector("[data-pull-label]").textContent = homePull.distance >= 64 ? "松开刷新" : "下拉刷新";
  }

  function endHomePull(event) {
    if (!homePull) return;
    const refresh = homePull.distance >= 64 && !event.type.endsWith("cancel");
    const hint = homePull.list.querySelector("[data-pull-hint]");
    // A pull starting on a device row must not also open its details.
    if (homePull.distance > 8) homeIgnoreClickUntil = performance.now() + 350;
    homePull = undefined;
    if (refresh) {
      beginHomeRefresh(hint.parentElement);
    } else {
      hint.hidden = true;
      hint.style.height = "";
    }
  }

  function beginHomeRefresh(list) {
    if (list.dataset.refreshing) return;
    list.dataset.refreshing = "true";
    list.setAttribute("aria-busy", "true");
    const hint = list.querySelector("[data-pull-hint]");
    hint.hidden = false;
    hint.style.height = "48px";
    hint.innerHTML = '<span class="small-spinner" aria-hidden="true"></span><span data-pull-label>正在刷新</span>';
  }

  /* Pairing */
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
    const exit = state.page === "onboarding" ? "pair-start" : "page:02-home.html?screen=ready";
    return `
      <div class="scanner">
        <div class="scanner-grid"></div>
        <div class="scanner-content">
          <div class="scanner-top">
            ${iconButton("close", "取消", exit)}
            <div style="flex:1;text-align:center"><span class="app-title" style="font-size:18px">扫描二维码</span></div>
            <span style="width:48px"></span>
          </div>
          <h2 class="scanner-title">对准目标设备上的二维码</h2>
          <p class="scanner-copy">二维码 5 分钟内有效。请确认目标设备屏幕上的鹊信窗口处于打开状态。</p>
          <div class="viewfinder">
            <span class="corner tl"></span><span class="corner tr"></span><span class="corner bl"></span><span class="corner br"></span>
            <span class="scan-line"></span>
            ${mockQr()}
          </div>
          <div class="scanner-actions">
            ${button("text-button", "取消", exit, { full: true })}
          </div>
        </div>
      </div>
    `;
  }

  function pairingInvalidQr() {
    return onboardingScannerInvalid();
  }

  function pairConsentBase({
    title,
    copy,
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
          <label class="alias-field">
            <span class="alias-label">设备名称</span>
            <input class="alias-input ${error ? "is-invalid" : ""}" name="alias" value="${escapeHtml(alias)}" maxlength="40" autocomplete="off" spellcheck="false" aria-label="设备名称" aria-invalid="${Boolean(error)}" required>
            <span class="${error ? "alias-error" : "alias-help"}">${error || "不能为空，也不能与本机已配对的设备重名。"}</span>
          </label>
          <div class="list-group" style="margin-top:12px">
            <div class="list-item">${icon("key")}<span class="list-item-title">配对会话剩余时间</span><span class="list-item-value">04:31</span></div>
          </div>
        </div>
        <div class="wizard-actions">
          ${button("filled-button", primary, state.page === "onboarding" ? "progress-opening" : "progress", { full: true, iconName: "key", disabled: confirmDisabled })}
          ${button("text-button", secondaryLabel, secondaryTarget, { full: true })}
        </div>
      </div>
    `;
  }

  function pairingConsent(options = {}) {
    return pairConsentBase({
      title: "添加新设备",
      copy: "确认后，目标设备会开始接收你选中的短信，双方会建立一条独立的加密配对。",
      primary: "确认添加设备",
      backTarget: "page:02-home.html?screen=ready",
      alias: state.pairingAlias,
      ...options
    });
  }

  function updatePairingAlias(value) {
    state.pairingAlias = value;
    const name = value.trim();
    const error = !name ? "请输入设备名称"
      : Array.from(name).length > 40 ? "设备名称不能超过 40 字"
      : DEVICES.some((device) => (params.get(`alias-${device.id}`) || device.name).toLowerCase() === name.toLowerCase()) ? "已有一台同名设备，请换一个名称" : "";
    document.querySelectorAll('.pair-screen input[name="alias"]').forEach((input) => {
      input.value = value;
      input.classList.toggle("is-invalid", Boolean(error));
      input.setAttribute("aria-invalid", String(Boolean(error)));
      input.setCustomValidity(error);
    });
    document.querySelectorAll(".pair-screen .alias-help, .pair-screen .alias-error").forEach((help) => {
      help.textContent = error || "不能为空，也不能与本机已配对的设备重名。";
      help.className = error ? "alias-error" : "alias-help";
    });
    document.querySelectorAll('.pair-screen [data-screen="progress"]').forEach((confirm) => { confirm.disabled = Boolean(error); });
  }

  function handlePairingAction(event) {
    if (state.page !== "pairing" && !(state.page === "onboarding" && state.screen === "success")) return;
    const control = event.target.closest(".app-viewport button");
    if (!control || control.disabled) return;
    if (control.dataset.href) {
      location.href = control.dataset.href;
    } else if (control.dataset.screen) {
      if (control.dataset.screen === "scanner") state.pairingAlias = "我的设备";
      if (control.dataset.screen === "progress") {
        const input = control.closest(".pair-screen").querySelector('input[name="alias"]');
        updatePairingAlias(input.value);
        if (!input.reportValidity()) return;
        state.pairingAlias = state.pairingAlias.trim();
      }
      state.screen = control.dataset.screen;
      render();
    }
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
    return onboardingPairingError({
      iconName: "clock",
      eyebrow: "会话已结束",
      title: "二维码已失效",
      copy: "配对会话已过期、被占用或已被取消。请让接收端重新显示二维码。",
      primary: { label: "扫描新二维码", target: "scanner", icon: "qr" },
      secondary: { label: "返回首页", target: "page:02-home.html?screen=ready" }
    });
  }

  function pairingFailurePreserved() {
    return onboardingPairingError({
      iconName: "cloud_off",
      eyebrow: "配对尚未生效",
      title: "配对未完成",
      copy: "连接中断或确认超时，未能添加这台设备。已有设备的配对不受影响，请重新扫描后再试。",
      primary: { label: "重新扫描", target: "scanner", icon: "refresh" },
      secondary: { label: "返回首页", target: "page:02-home.html?screen=ready" }
    });
  }

  function pairingSuccess() {
    return `
      <div class="screen-fill">
        <div class="wizard-content">
          <div class="wizard-emblem" style="background:var(--md-success-container);color:var(--md-success);">${icon("check")}</div>
          <h2 class="title-xl">配对完成</h2>
        </div>
        <div class="wizard-actions">
          <button class="filled-button full" data-test-message aria-busy="${state.testPending}" ${state.testPending ? 'disabled aria-disabled="true"' : ""}>${icon("send")}<span>${state.testPending ? "正在发送…" : "发送测试消息"}</span></button>
          ${button("text-button", "进入首页", "page:02-home.html?screen=ready", { full: true })}
        </div>
        ${state.testNotice ? toastMessage(escapeHtml(state.testNotice)) : ""}
      </div>
    `;
  }

  /* Inline test-message preview; no network requests are made. */
  let testMessageTimer;

  function testMessageDemo() {
    const homeEntry = state.page === "home" && ["device-sheet", "device-offline", "device-disabled"].includes(state.screen);
    const pairingEntry = ["onboarding", "pairing"].includes(state.page) && state.screen === "success";
    if (!homeEntry && !pairingEntry) return "";
    return `<div class="scan-demo" aria-label="测试消息反馈演示">
      <label>测试结果演示 <select id="testOutcome">${Object.entries(TEST_OUTCOMES).map(([key, copy]) => `<option value="${key}" ${key === (params.get("testResult") || params.get("testPreview") || "received") ? "selected" : ""}>${copy}</option>`).join("")}</select></label>
      <span>点击手机内的“发送测试消息”查看原地反馈</span>
    </div>`;
  }

  function handleTestMessage(event) {
    const control = event.target.closest(".app-viewport [data-test-message]");
    if (!control || control.disabled || state.testPending) return;
    event.stopImmediatePropagation();
    const origin = state.screen;
    const target = state.page === "home" ? DEVICES.find((device) => device.id === state.deviceId) : PAIRED_DEVICE;
    state.testTarget = { receiverId: target.receiverId, pairingId: target.pairingId };
    const deviceId = state.deviceId;
    const outcome = params.get("testResult") || "received";
    clearTimeout(testMessageTimer);
    state.testPending = true;
    state.testNotice = "";
    render();
    testMessageTimer = setTimeout(() => {
      state.testPending = false;
      if (state.screen === origin && state.deviceId === deviceId) {
        state.testNotice = TEST_OUTCOMES[outcome] || "投递未确认：未知投递结果";
      }
      render();
      testMessageTimer = setTimeout(() => {
        state.testNotice = "";
        render();
      }, 4000);
    }, 1200);
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
        <button class="list-item" data-screen="security">${icon("shield")}<span><span class="list-item-title">设备访问安全</span><span class="list-item-desc">永久撤销当前设备的注册</span></span>${icon("chevron")}</button>
        <button class="list-item" data-screen="privacy">${icon("lock")}<span><span class="list-item-title">隐私与数据</span><span class="list-item-desc">短信、OTP 与本地密钥说明</span></span>${icon("chevron")}</button>
      </div>
      <div class="group-label">关于</div>
      <div class="list-group">
        <div class="list-item">${icon("info")}<span><span class="list-item-title">鹊信</span><span class="list-item-desc">Pin Glide Android</span></span><span class="list-item-value">1.0</span></div>
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
        <button class="list-item danger-label" data-screen="logout-confirm">${icon("logout")}<span><span class="list-item-title">退出登录</span><span class="list-item-desc">立即停止转发，但保留设备配对</span></span>${icon("chevron")}</button>
      </div>
      <div class="info-card">${icon("shield")}<span>退出登录会撤销当前设备 的访问凭据。目标设备的共享接收连接不会被关闭。</span></div>
    `);
  }

  function settingsSwitchAccount() {
    return settingsBase(settingsAccountContent(), `
      <div class="scrim dialog-scrim">
        <div class="dialog">
          <div class="dialog-icon error">${icon("swap")}</div>
          <h2>切换 QQ 账号？</h2>
          <p>当前设备的旧身份、旧配对密钥和本地设备关联会被清除。其他设备 与设备的连接不受影响。</p>
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
        <button class="list-item danger-label" data-screen="logout-confirm">${icon("logout")}<span><span class="list-item-title">退出登录</span><span class="list-item-desc">立即停止转发，但保留设备配对</span></span>${icon("chevron")}</button>
      </div>
      <div class="info-card">${icon("shield")}<span>退出登录会撤销当前设备 的访问凭据。目标设备的共享接收连接不会被关闭。</span></div>
    `;
  }

  function settingsLogoutConfirm() {
    return settingsBase(settingsAccountContent(), `
      <div class="scrim dialog-scrim">
        <div class="dialog">
          <div class="dialog-icon">${icon("logout")}</div>
          <h2>退出登录？</h2>
          <p>鹊信会立即停止本机转发，并尝试撤销服务端凭据。设备配对和本地配对密钥会保留。</p>
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
        <p class="body-small" style="max-width:312px;margin-top:10px;">当前设备已停止转发，但网络不可用，无法确认服务端是否已撤销访问凭据。请稍后重试。</p>
        <div class="wizard-actions" style="width:100%;margin-top:26px;">
          ${button("filled-button", "重试撤销", "settings", { full: true, iconName: "refresh" })}
          ${button("text-button", "保留本机退出状态", "page:01-onboarding.html?screen=welcome", { full: true })}
        </div>
      </div>
    `;
  }

  function settingsSecurity() {
    return settingsBase(`
      <div class="info-card warning">${icon("warning")}<span>安全撤销需要新的 QQ 账号所有者验证。撤销后，当前设备的旧注册不能恢复。</span></div>
      <div class="list-group">
        <button class="list-item danger-label" data-screen="owner-proof">${icon("lock")}<span><span class="list-item-title">永久撤销本机注册</span><span class="list-item-desc">关闭设备注册并移除当前设备 的所有配对</span></span>${icon("chevron")}</button>
      </div>
      <div class="group-label">普通管理</div>
      <div class="list-group">
        <button class="list-item" data-href="02-home.html?screen=ready">${icon("computer")}<span><span class="list-item-title">管理设备配对</span><span class="list-item-desc">移除单台设备只影响当前设备</span></span>${icon("chevron")}</button>
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
          <p class="body wizard-copy">安全撤销会永久关闭当前设备。必须使用与当前账号一致的 QQ 所有者证明，普通访问令牌不能继续。</p>
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
          <h2>永久撤销当前设备？</h2>
          <p>这会关闭当前设备 注册，移除它的所有设备配对并立即停止转发。旧注册不能恢复，只能重新登录并重新配对。</p>
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
      <div class="info-card warning">${icon("warning")}<span>安全撤销需要新的 QQ 账号所有者验证。撤销后，当前设备的旧注册不能恢复。</span></div>
      <div class="list-group">
        <button class="list-item danger-label" data-screen="owner-proof">${icon("lock")}<span><span class="list-item-title">永久撤销本机注册</span><span class="list-item-desc">关闭设备注册并移除当前设备 的所有配对</span></span>${icon("chevron")}</button>
      </div>
    `;
  }

  function settingsRevoked() {
    return `
      <div class="revoked-screen">
        <span class="empty-icon">${icon("lock")}</span>
        <h2>本机注册已撤销</h2>
        <p>服务端已经关闭当前设备 的注册并移除它的配对。旧注册不能恢复，重新登录会创建新的安装注册。</p>
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
  if (state.page === "home") {
    resetHomeFixture(params.get("origin") === "full" ? "full" : state.screen);
    const device = DEVICES.find((item) => item.id === params.get("device"));
    if (device) {
      const origin = params.get("origin");
      if (PAGES.home.screens.some((item) => item.id === origin && ["overview", "permission"].includes(item.group))) {
        resetHomeFixture(origin);
        state.homeReturn = origin;
      }
      state.deviceId = device.id;
      if (params.has("alias")) state.aliases[device.id] = params.get("alias");
      if (params.has("enabled")) {
        const enabled = params.get("enabled").split(",");
        DEVICES.forEach((item) => { state.toggles[item.id] = enabled.includes(item.id) && item.id !== state.keyFailureId; });
      }
    }
    DEVICES.forEach((item) => {
      if (params.has(`alias-${item.id}`)) state.aliases[item.id] = params.get(`alias-${item.id}`);
    });
  }
  document.addEventListener("click", handleTestMessage);
  document.addEventListener("change", (event) => {
    if (event.target.id === "testOutcome") params.set("testResult", event.target.value);
  });
  document.addEventListener("click", handleHomeAction);
  document.addEventListener("click", handlePairingAction);
  document.addEventListener("input", (event) => {
    if (state.page === "home" && event.target.matches("[data-rename-input]")) updateHomeAlias(event.target.value);
    if (state.page === "pairing" && event.target.matches('.pair-screen input[name="alias"]')) updatePairingAlias(event.target.value);
  });
  document.addEventListener("pointerdown", startHomePull);
  document.addEventListener("pointermove", moveHomePull);
  document.addEventListener("pointerup", endHomePull);
  document.addEventListener("pointercancel", endHomePull);
  document.addEventListener("touchstart", startHomePull, { passive: true });
  document.addEventListener("touchmove", moveHomePull, { passive: false });
  document.addEventListener("touchend", endHomePull);
  document.addEventListener("touchcancel", endHomePull);
  let sheetDrag;
  document.addEventListener("pointerdown", (event) => {
    if (state.page === "home" && event.target.matches(".sheet-handle")) sheetDrag = event.clientY;
  });
  document.addEventListener("pointerup", (event) => {
    if (sheetDrag !== undefined && event.clientY - sheetDrag >= 64) dismissHomeOverlay();
    sheetDrag = undefined;
  });
  document.addEventListener("pointercancel", () => { sheetDrag = undefined; });
  document.addEventListener("keydown", (event) => {
    // Escape stands in for Android system Back in the desktop pairing preview.
    if (state.page === "pairing" && event.key === "Escape") {
      event.preventDefault();
      if (!state.screen.startsWith("progress")) location.href = screenHref("home", "ready");
      return;
    }
    if (state.page === "home" && event.key === "Escape") {
      dismissHomeOverlay();
      return;
    }
    if (state.page !== "home" || !event.target.matches("[data-refresh-list]") || !["Enter", " "].includes(event.key)) return;
    event.preventDefault();
    beginHomeRefresh(event.target);
  });
  render();
})();
