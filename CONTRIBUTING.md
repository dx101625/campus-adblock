# 参与贡献

## 能提什么

- 新的广告资源名 / 布局名（请说明是哪个 App、哪个版本、出现在哪个页面）。
- 目标 App 大版本更新后的失效点（贴 `adb logcat -d -s CampusAdBlock` 的关键行）。
- 构建与文档的修正。

**不要**提这些：

- 任何绕过支付、自动付款、伪造订单的功能。
- 提交目标 App 的反编译产物（那是别人的版权代码）。
- 提交真实设备码、订单号、手机号、截图里带账号信息的材料。

## 改资源名规则的注意事项

规则表在：

- 胖乖：`app/src/main/java/com/campus/adblock/CampusAdBlockModule.java`，
  搜 `EXACT` / `PREFIX` / `CONTAINS` / `SUFFIX` / `EXCLUDE`。
- 智趣：`ZqxyHooks.AD_KEYWORDS`。

两条硬规矩：

1. **先确认它真不是业务视图**。历史事故：`banner_view` 名字像广告，
   实际是首页功能栏，按名字隐藏后首页直接空白。
2. 加一条就写一条注释，注明来自哪个版本、哪个页面。

## 提交前自检

```bash
python build.py --no-sign  # 不需要维护者的签名私钥
# 准备好JAVA_HOME、CAMPUS_GRADLE、CAMPUS_ANDROID_SDK后：
gradle --offline :app:testDebugUnitTest
```

- 日志里不应该出现新的 `[skip] ... ClassNotFoundException`（除非确实可选）。
- 改动涉及行为时，在 PR 描述里写清楚「怎么验证的」：哪台设备、哪个 App 版本、
  冷启动几次、看到什么、logcat 关键行。

## 代码风格

- 4 空格缩进，UTF-8，中文注释。
- 新增 hook 必须在 `installHooks` / `ZqxyHooks.install` 里用 `step(...)` 包一层，
  这样单个 hook 失败不会把整个模块带崩。
- 任何「替用户点按钮」的逻辑都必须有可退让的条件判断和日志。
