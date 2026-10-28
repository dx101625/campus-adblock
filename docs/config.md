# 配置项

配置文件：`SharedPreferences("campus_cfg")`，存在模块 App 自己的数据目录。
hook端在目标App取得Context后，通过校验调用方UID的模块配置接口读取同一份文件；
框架`getRemotePreferences("campus_cfg")`仅作不可用时的兜底。因此**改配置不需要重装模块**，
但需要让目标 App 完全退出后重新打开（或从 LSPosed 里重载模块）。

开关和分段选择会自动保存；手填设备码需点“保存”，收藏编辑与配置恢复也各有确认。下表是键名与默认值。

## 胖乖生活 `com.qiekj.user`

| 键 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `q.ads` | boolean | `true` | 去广告总开关（View 隐藏 / 弹窗 / 悬浮 / H5 / 开屏 SDK） |
| `q.splash` | boolean | `true` | 开屏对话框一初始化就关掉，不再等 6 秒 |
| `q.scan` | boolean | `true` | 打开 App 自动识别记住的设备码（不开相机） |
| `q.advance` | boolean | `true` | 自动选机 + 自动按「立即使用」，停在订单预览 |
| `q.machine` | string | `right2` | `left1` / `right2` / `auto` / `none` |
| `q.tier` | string | `shortest` | `shortest`（最便宜）/ `longest` |
| `q.pay` | string | `unionpay` | `unionpay` / `alipay` / `wechat` / `none` |
| `q.coupon` | boolean | `false` | 允许原App自动选择可用优惠券 |
| `q.points` | boolean | `false` | 允许原App自动选择可用积分 |
| `q.qr` | string | （空） | 手填的二维码文本；非空时优先于「扫过的那个」 |

`q.machine` 与 `q.tier` 已在 `DeviceDefaults` 里生效：`right2` 点右 2 号机、`left1` 点左 1 号机、
`auto` 有两台选右 2 / 只有一台选左 1、`none` 完全不选交给手点；`shortest` 取最短时长（最便宜），
`longest` 取最长时长。自v1.2.0起，`q.pay`会选择原生可用通道：银联52、支付宝13、微信32；
`none`不改变原App默认选项。所选通道不可用时保留原选项并记录原因，不切换到另一支付方式。

`q.coupon`与`q.points`为独立的自动选择许可：关闭时过滤该类默认选择；开启时允许原App
自动选择可用权益，继续遵循原有可用性及验证流程。它们不会强制使用不存在或不可用的权益，
也不会允许购买券包、会员或观看营销任务。用户在订单页主动改变选择后，模块不再覆盖该次选择。

## 智趣校园 `com.klcxkj.zqxy`

| 键 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `z.ads` | boolean | `true` | 去广告（开屏 / 横幅 / 信息流 / 插屏） |
| `z.tab` | boolean | `true` | 隐藏底部运营 tab（商城、赚币等） |

## 全局

| 键 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `g.shake` | boolean | `true` | 拦摇一摇 / 扭一扭（屏蔽加速度监听） |
| `g.jump` | boolean | `true` | 拦误触跳转（应用商店 / 浏览器 / 电商 scheme） |
| `g.log` | boolean | `true` | 输出 `CampusAdBlock` 日志 |

v1.2.0新增设置不改变上述15项键名与默认值：

| 键 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `g.jump.mode` | string | `standard` | `standard`仅拦识别到广告来源的名单内跳转；`strict`拦所有名单内跳转。v1.6.1不再展示强度选择，保留旧值和备份兼容 |
| `g.jump.allowed` | string | （空） | 允许列表，使用逗号分隔的包名；在界面按应用名称勾选 |
| `ui.devices` | string | `[]` | 常用设备JSON数组，每项包含id/name/qr，最多30项 |

选择收藏设备会更新原有`q.qr`覆盖值；编辑当前默认收藏会同步更新该值。删除收藏仅移除列表项，
不清除当前设备码；清空手填设备码仍需确认，之后回退到目标App曾记住的码。

旧版根据自动识别/推进开关决定停留位置；v1.3.0由下方使用模式控制。仅去广告时，机器、档位、支付和权益选择全部保留原App行为；设备页模式仍按偏好预选。
自动识别失败、超时或需要特殊处理时留在当前页并提示，不再自动打开扫码取景框。
未验证的胖乖版本会暂停自动直达，去广告规则继续按匹配结果执行。

`g.log`同时控制模块logcat输出和新增运行摘要。摘要在模块自己的`campus_events`中保留最近30条，
用于区分加载与执行结果；不记录设备码、账号、订单号或支付信息。关闭后停止新增摘要，不影响原心跳。

配置备份采用`campus-adblock-config` / schema2 JSON，兼容schema1。默认不包含设备码；可选择包含`q.qr`、`q.profile`与收藏。
导入先完整校验格式、类型、枚举和设备数据，显示覆盖项数，用户确认后统一写入；未包含的键保持原值。

## v1.3.0习惯与档案

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `q.mode` | 按原`q.scan/q.advance`推导 | `ads`仅去广告 / `device`停在设备页 / `preview`订单预览 |
| `q.launch` | `direct` | `direct`普通打开自动处理 / `shortcut`仅设备入口处理 |
| `q.profile` | 空 | 默认档案ID，与设备码一起选择，设备码导出选项控制其导出 |
| `q.pause` | `true` | 同一设备连续识别失败3次后暂停，成功或手动恢复重置计数 |
| `q.price.guard` | `true` | 同设备、机号、档位偏好的报价或时长变化时停止自动推进 |
| `ui.theme` | `system` | `system/light/dark` |
| `ui.glass` | `liquid` | `liquid/clear`，清晰模式关闭光学采样效果 |
| `ui.motion` | `true` | 页面与透镜动画，继续尊重系统关闭动画的设置 |
| `ui.haptics` | `true` | 切换导航与使用习惯时的轻触反馈，继续尊重系统触感开关 |

设置全局模式时同时保存原`q.scan/q.advance`，保留原15项键名及默认值；仅去广告的原生预选入口会直接放行原App。

`ui.devices`仍为JSON字符串。旧的id/name/qr项会补充继承选项；新增group、icon和options。options包含mode/machine/tier/pay/coupon/points，`inherit`表示继承；coupon/points还可选`yes/no`。档案中未验证的值会在保存或导入前被拒绝。

普通打开的全局`ads`或`q.launch=shortcut`均不自动操作；主动设备入口可按其独立mode进入。本次入口不改写默认设备、机号或全局模式，配置保留到目标进程退出或选择新的设备入口。入口令牌只允许领取一次，未领取120秒失效。

控制状态存在模块自己的`campus_runtime`：失败计数、最多60项报价参考、最多20条跳转目标记录、兼容性安装记录及短期允许。设备使用SHA256键关联报价/失败数据，不记录账号或订单；这些控制状态不包含在配置导出中。

临时允许按来源App与目标应用隔离，一次模式消费后删除；两种模式都最迟5分钟失效。读取控制状态与消费允许沿用验证调用UID的Provider，无关应用不能读取配置或使用控制接口。

## 状态（`campus_status`，模块自己写，供界面显示）

| 键 | 类型 | 说明 |
| --- | --- | --- |
| `p.<包名>.ver` | string | 目标App版本名 |
| `p.<包名>.ts` | long | 该App最近加载模块的心跳时间戳（毫秒） |

心跳仅表示模块已加载，不能据此断言所有功能执行成功。具体执行摘要在独立的`campus_events`中。

## 目标 App 里的另一个文件

`shared_prefs/pg_auto_qr.xml` 的 `qr` 是「扫一次就记住」的设备码，写在**目标 App**
自己的数据目录（不是模块 App），因为要在目标进程里读。设置界面填了 `q.qr` 时以它为准。
