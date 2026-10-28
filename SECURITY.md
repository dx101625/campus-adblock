# 安全说明

## 这个项目会碰你的什么数据

模块运行在**目标 App 进程内**（LSPosed 注入），因此理论上能读到该 App 的一切。
当前代码实际读写的东西只有这些：

| 位置 | 目的 |
| --- | --- |
| 目标 App `shared_prefs/pg_auto_qr.xml` | 存「记住的二维码」 |
| 模块 App `shared_prefs/campus_cfg.xml` | 功能配置、设备码与设备档案 |
| 模块 App `shared_prefs/campus_status.xml` | 目标进程上次加载时间及版本 |
| 模块 App `shared_prefs/campus_events.xml` | 最近运行摘要 |
| 模块 App `shared_prefs/campus_runtime.xml` | 报价参考、连续失败与短期放行等本机运行状态 |
| 目标 App 的 View 树 / Activity / Intent | 隐藏广告、判断跳转、选机选档 |

**不联网**：模块自身没有任何网络代码，也不上报任何数据。
导出日志时请注意 `logcat` 里可能包含目标 App 的页面文案、设备码片段，发 issue 前自行打码。

## 私钥

`signing/` 目录在 `.gitignore` 里。如果你 fork 后自己生成密钥：

- 不要提交 `release.p12` / `password.txt`。
- 备份这两份文件：丢了就无法给已安装用户发升级包（只能换包名重发）。

## 已知风险

- **同签名的伪造包**：任何拿到你私钥的人都能做出可覆盖升级的 APK。
  本项目历史上的旧恢复版密钥已按泄露处理，公开版使用新包名 `com.campus.adblock` + 新密钥。
- **误触下单**：自动推进只到订单预览页，理论上不会产生订单；
  但如果你把 `q.advance` 打开后手动点了「去支付」，那就是真实订单。
- **共用设备码**：把同一个设备码给别人用，会往同一台机器下单，
  未支付订单会锁设备。

## 报告问题

普通问题可通过 [GitHub Issues](https://github.com/dx101625/campus-adblock/issues) 反馈。涉及敏感信息的问题，请先提交不含敏感内容的简短说明，与维护者确认后续沟通渠道；目前尚未公布专用私密联系方式，不要直接贴设备码、账号、签名材料或利用细节。
