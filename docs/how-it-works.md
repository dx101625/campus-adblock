> **v1.6.2 更新**：功能 → 使用习惯仅保留“自动跳转订单预览”开关。开启后直达订单预览，关闭后仅去广告；不再执行报价核对和连续失败暂停。旧版本的三种模式、仅设备入口限制及报价确认说明不适用于当前版本。最终付款仍需手动确认。

# 工作原理

模块就是一个 libxposed（API 102）模块：LSPosed 在目标 App 进程启动早期把
`CampusAdBlockModule` 加载进来，调用 `onModuleLoaded` 与 `onPackageReady`，
我们在 `onPackageReady` 里按包名决定挂哪一套 hook。

```java
onPackageReady(param) {
    Cfg.load(getRemotePreferences("campus_cfg"));   // 读设置
    if (包名 == com.klcxkj.zqxy)  ZqxyHooks.install(this, cl);
    else if (包名 == com.qiekj.user) installHooks(cl);
}
```

## 1. 为什么「隐藏 View」就能去广告

绝大多数信息流/Banner 广告最终都要调 `View.setVisibility(VISIBLE)` 才能显示出来。
我们的 hook 把这个调用拦下来：命中广告资源名就改成 `GONE`，并且**之后任何想再把它
显示回来的调用也会被压回 `GONE`**。这样既不用管 SDK 什么时候塞数据，也不用管
它用了几个容器。

资源名判定是多级规则（全部小写比较）：

| 级别 | 例子 | 说明 |
| --- | --- | --- |
| 精确 | `banner_head`、`fl_native_flow` | 业务明确标注的广告位 |
| 前缀 | `ad_`、`qy_feed`、`ms_splash_ad` | 常见 SDK 布局前缀 |
| 包含 | `advert`、`interstitial` | 兜底 |
| 后缀 | `_ad_layout` | 另一家的命名习惯 |
| 白名单 | `banner_view`、`privacy_adv_recommendation_tv` | 长得像广告但其实是功能/隐私项 |

> 教训：`banner_view` 在胖乖里是首页业务功能栏，早期版本按名字隐藏过一次，
> 直接把首页清空了。所以白名单和规则表同等重要，改规则前务必看真机截图。

## 2. 开屏那 6 秒是怎么来的

反编译 291 版得到的链路：

```
SplashActivity.onCreate()      setContentView(new View(this))   // 什么都不画
  ↓ onWindowFocusChanged(true)
MainActivity.start(this, showSplash = true)
  ↓
MainActivity.initView()
  if (getIntent().getBooleanExtra("showSplash", false))
      new SplashDialogFragment().showNow(fm, "splash")          // 开屏对话框
  else
      postDelayed(() -> splashDismissEvent.setValue(TRUE), 500) // 正常路径
  ↓
SplashDialogFragment.initView()
  getMViewModel().getSplashAd();                                 // 去请求开屏广告
  mHandler.postDelayed(safeDismiss, maxTimeout = 6000)           // 唯一的兜底
  ↓
loadAd() → loadGroMoreSplash()
  onLoaded()  → splashAd.showAd(frameLayout)
  onAdClosed() → safeDismiss()      ← 广告正常关闭时走这里
  onAdFailed() → safeDismiss()      ← 广告加载失败时走这里
```

我们把 `showAd` 拦掉之后，**广告既没展示、也不会回调 `onAdClosed()`**；
偏偏 `onAdFailed()` 只在加载失败时才有，于是只剩 `maxTimeout` 这条 6 秒兜底。
表现就是：品牌图从 +2.4s 一直卡到 +5.5s，用户以为「在等跳过广告」。

**修法**：hook `SplashDialogFragment` 的 `initView` / `loadAd` /
`loadGroMoreSplash` / `loadTakuSplash`，一进来就
`dismissAllowingStateLoss()`（200ms 后再补一次，防它自己又建一遍），
并给 `com.qiekeji.pgad.manager.gromore.SplashAd.showAd` 补一个 `onAdClosed()`
回调。`onDismiss()` 由 App 自己发 `splashDismissEvent`，后续逻辑不受影响。

实测（同一台机、同一冷启动方式）：首页出现时间 +6.28s → +1.6s。

## 3. 自动扫码为什么可以不开相机

`HomeScanCodeAct`（扫一扫页）把识别结果统一交给 `scanCodeResult(HmsScan)`，
里面的核心只有两步：

```java
getLoadView().show();                       // 转圈对话框
((ScanCodeVm) getMViewModel()).scanV2("NQT", value);
```

`ScanCodeVm.scanV2()` 内部发网络请求，结果写进 `MutableLiveData scanV2Bean`，
页面自己的 observer 再接 `ScanAdxGate.intercept(...)` 决定跳设备页还是拉起支付宝。
所以模块完全不需要打开相机界面：

1. 反射 new 一个 `ScanCodeVm`；
2. 用 `Proxy` 实现 `androidx.lifecycle.Observer`，`observeForever` 挂到 `scanV2Bean`；
3. 调 `scanV2("NQT", value)`；
4. 拿到 `ScanV2Bean.getId()` 后，反射调 `LockingAct` 内部的 `Companion` 跳设备页。

> 坑：jadx 里看到的 `LockingAct.INSTANCE` 是元数据还原出来的名字，运行时字段被混淆了，
> `getField("INSTANCE")` 必抛 `NoSuchFieldException`。正确做法是遍历静态字段找类型名
> 以 `Companion` 结尾的那个，再按参数签名（Context, String, boolean）找方法。

## 4. 自动推进到订单预览

设备页 `HairGoodsSkuNewAct` 的提交流程是：点 `tvSubmit` → 置 `submitOrder = true`
→ 服务端校验 → observer 里判断有没有「强制买券弹窗」→ 没有才走 `toOrder()` →
拼一个 `OrderPreviewQuest` 序列化后启动 `OrderPayPreviewAct`。

模块只在**三个条件同时成立**时代点一次：

- `hairPosition == 1`（真的选中了右边 2 号）
- `timePosition >= 0`（档位已选定）
- `tvSubmit` 可见且可点

有一项不满足就退让，日志写「设备页没等到就绪，留给用户手动点」。
「立即使用」之后到的是预览页，**下单请求要等用户点「去支付」才发生**。

## 5. 覆盖的 hook 清单（胖乖侧）

| 入口 | 用途 |
| --- | --- |
| `View.setVisibility` | 广告位隐藏（核心） |
| `Activity.onCreate` / `onResume` | 扫树、注入、自动扫码状态机 |
| `Instrumentation.callActivityOnCreate/OnResume` | 子类不调 super 时的兜底 |
| `View.onAttachedToWindow` / `ViewStub.inflate` | H5 容器 inflate 当帧接住 |
| `Dialog.show` / `PopupWindow.show*` | 插屏、悬浮 |
| `WebView.<init>` / `loadUrl` / `WebViewClient.onPageFinished` | H5 内部营销位清理 |
| `SensorManager.registerListener` | 摇一摇 / 扭一扭 |
| `Instrumentation.execStartActivity` | 误触跳转 |
| `ATSplashAd.show*` / `SplashAD.show*` / `TTAdNative.loadSplashAd` | 开屏 SDK |
| `SplashDialogFragment.*` | 开屏不空等 |
| `HomeScanCodeAct.onResult/scanCodeResult` | 记住设备码 |
| `HairGoodsSkuNewAct.initView/initPrice/initAdapter/onResume` | 自动推进 |

## 6. 智趣校园侧（`com.klcxkj.zqxy`）

这一节不是推测，是对用户实机跑通的 `智趣校园去广告_1.0.apk`
（`com.zqxy.adblock`，module.prop 里 version=1.1 / versionCode=2，
sha256 `ac564ffbb1abd92b63a89c90605def0df19fdab3fb3c0a33df66b8f463daede5`）
反编译后的逐条比对，规则、延迟、类名、传感器类型全部对齐。

只挂 6 个入口：

| 入口 | 用途 |
| --- | --- |
| `View.setVisibility` | 广告位强制 GONE（`arg == GONE` 时放行，避免自锁） |
| `Activity.onCreate` / `onResume` | 开屏跳过 + 扫树 |
| `SensorManager.registerListener` | 摇一摇（加速度 1 / 陀螺仪 4 / 显著运动 17） |
| `Instrumentation.execStartActivity` | 误触跳转（18 个电商包 + 16 个 scheme） |
| `com.anythink.splashad.api.ATSplashAd` | `isAdReady` → false，`show*` → null |

**资源名规则**（`ZqxyHooks.isAdView`，与原生一致）：

- 精确：`privacy_adv_recommendation_tv`、`home_banner_iv`、`home_banner_view`、
  `home_activity_rv`、`home_meal_layout`、`home_meal_rv`、`home_hz_parent_layout`、
  `home_hz_container_layout`、`top_adv_layout`
- 前缀：`task_adv_`；包含：`ju_tui_`、`tian_mu`、`_adv_`
- 底部 tab：`main_scan`、`main_shopping` 只是**隐藏它们的父容器**，不隐藏自己

注意胖乖侧那套 `*_adv_layout` 后缀规则在智趣命中 0 —— 两边的命名风格完全不同，
所以规则表必须分开，不能合并成一张。

**扫树时机**：`0 / 400 / 1000 ms` 三次（原版就是这三档，不是越多越好，
扫太晚会把用户自己刚点出来的正常页面误判掉）。

**开屏**：`SplashActivity` 等 1000 ms，`TopOnSplashActivity` 与
`com.anythink.core.common.inner.ui.activity.*` 只等 60 ms（它们本身就是广告容器），
`isTaskRoot()` 时用 `NEW_TASK|CLEAR_TASK` 起 `MainActivity`，否则只 finish。

## 7. 调试技巧

```bash
# 只看模块自己的日志（LSPosed 框架日志在 LSPosedFramework）
adb logcat -d -s CampusAdBlock LSPosedFramework | tail -100
```

- `adb logcat -d > file` 落盘时缓冲区常被 TREE dump 冲掉，用 `-s` 过滤更可靠。
- PowerShell 5.1 的 `>` 重定向会把二进制写成 UTF-16；截图请用
  `adb shell screencap -p /sdcard/x.png` + `adb pull`。
- 判断「是不是模块干的」：用 `settings put`/文件开关临时关掉某段逻辑重测，
  比只看日志可信。