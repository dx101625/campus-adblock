# 构建与发版

`python build.py` 是构建入口。界面使用 Kotlin / Compose 和 Miuix，业务 Hook 保持 Java 17。

## 工具链

| 组件 | 固定版本 |
| --- | --- |
| JDK | 21，设置 `JAVA_HOME` 或在 PATH 中提供 java |
| Gradle | 9.7.1 |
| Android Gradle Plugin | 9.4.1，内置 Kotlin 支持 |
| Compose compiler plugin | 2.4.20 |
| Android SDK / Build Tools | `platforms;android-37.0` / `build-tools;37.0.0` |
| SDK 声明 | compileSdk 37.0 / targetSdk 34 / minSdk 24 |
| libxposed 编译接口 | 102.0.0，compileOnly，不打包到模块 |
| 框架状态服务 | `io.github.libxposed:service:102.0.0` |
| Miuix | miuix-ui 与 miuix-preference 0.9.4 |
| 玻璃 | Backdrop 2.0.1，Shapes 1.2.1 |
| Activity | activity-compose 1.13.0 |
| 图片识码 | ZXing Core 3.5.3 |

## 新电脑准备

1. 安装 JDK21，设置 `JAVA_HOME`。
2. 通过 Android Studio 或[官方SDK命令行工具](https://developer.android.com/tools/sdkmanager)安装上述平台与Build Tools，自行处理SDK许可提示。
3. 设置 `CAMPUS_ANDROID_SDK`（或 `ANDROID_HOME`）为SDK根目录。
4. 可设置 `CAMPUS_GRADLE` 为Gradle可执行文件；缺失时，入口从官方源下载项目内固定Gradle并核对[官方SHA-256](https://docs.gradle.org/current/userguide/installation.html)。

`tools/prepare_miuix.py`只检查SDK、准备Gradle，不读取签名、不复制历史APK、不改源码或生成备份。编译接口缺失时，`tools/prepare_api.py`从Maven Central获取官方API，核对归档与classes.jar的固定SHA-256。

依赖从Google Maven、Maven Central和Gradle Plugin Portal解析。`GRADLE_USER_HOME`可指定依赖缓存目录；`CAMPUS_XPOSED_API`可指定已经校验的classes.jar。构建不依赖维护者电脑目录，也不依赖历史版本APK。

## 编译与测试

```shell
# 贡献者/CI构建，不需要维护者私钥
python build.py --no-sign

# 已有完整依赖缓存时
python build.py --no-sign --offline

# 对项目运行JVM测试；Gradle路径按自己的安装位置调整
gradle --no-daemon --console=plain :app:testDebugUnitTest
```

当前共29项JVM测试，覆盖配置导入、使用模式、支付预选、报价保护、短期放行、广告页面识别、框架状态和空配置默认行为。JUnit和org.json仅用于测试，不进入交付APK。空配置测试不能替代新安装Android设备的完整验收。

## 正式签名

维护者将原密钥放在 `signing/release.p12`，口令放在 `signing/password.txt`，别名 `campus`，然后运行：

```shell
python build.py
```

签名材料不提交、不随源码包分发。原证书SHA-256固定为：

```text
95462bfe83602a10471cc127a99ad27de4411eba1c00284844577260eda1f14d
```

缺少原密钥时入口停止，不生成替代密钥。Fork可使用自己的包名与签名策略，但不能用不同证书覆盖官方安装。维护者回退旧代码时，以更高versionCode重建覆盖安装，保留用户数据。

## 产物与版本

当前版本 `1.6.1` / versionCode `24`。默认值在 `build.py`、`app/build.gradle.kts` 和 `app/src/main/resources/META-INF/xposed/module.prop` 中同步。可通过 `CAMPUS_VERSION` / `CAMPUS_VERSION_CODE` 覆盖，入口同步模块元数据。

产物为 `dist/campus-adblock-v1.6.1.apk`（正式签名）或 `dist/campus-adblock-v1.6.1-unsigned.apk`，并附相邻 `.sha256`。构建输出大小、哈希和签名验证结果。正式包与未签名CI产物分别标注。

## Manifest与反射入口

构建入口为AGP生成等价Manifest副本，保留原Receiver、Provider、queries、设置Activity和设备入口。官方框架服务声明minSdk26；其Provider与客户端在API24–25禁用，主界面保留minSdk24。详见[激活状态](activation.md)。

`UiKit`会按名称查找资源，资源收缩保留规则位于 `app/src/main/res/raw/campus_resources_keep.xml`。`androidx.lifecycle.Observer`是目标App直达路径查找的接口，必须保持原名及回调方法。入口检查R8映射，拒绝交付其被重命名的包。

## 自动构建与发布

[GitHub Actions](../.github/workflows/build.yml)对PR、main提交及手动触发运行未签名构建和JVM测试，不使用签名材料、设备码或账号令牌。当前远端是否执行，以GitHub运行记录为准；本地构建成功不能代替远端执行结果。

发布前核对正式签名、版本、SHA-256、README图片与适配范围，并在独立环境检查首次使用，在原手机检查实际使用流程。真机测试全程不提交订单、不付款、不启动设备。安装命令中的SERIAL由使用者选择：

```shell
adb -s SERIAL install --no-incremental -r dist/campus-adblock-v1.6.1.apk
```

软件图标源为 `app/artwork/launcher.svg`，可用 `tools/generate_launcher.py` 生成；该开发脚本需要Pillow。功能图标由 `tools/generate_icons.py` 和固定的 `tools/lucide-icons.json` 生成。两者都不需要设备数据。
