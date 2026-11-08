# 火影 OL AIR 客户端

非官方 Android ARM64 客户端实验项目。Android WebView 显示官方登录及选区页面，AIR 负责运行游戏的 Flash 内容。当前源码版本为 `2.1.2`。默认免客户端激活，进入游戏需使用自己的 QQ 账号。

- `*.as` 是客户端入口、资源和界面适配、游戏桥接代码。
- `native-qq/` 是 Android 宿主、QQ 登录衔接、账号页签与诊断代码。
- `build.py` 和 `native-qq/build_native.py` 分别构建 APK 与原生扩展。

## 2.1.2

- 顶栏倍速菜单提供 0.5×、1×、2×、3×、5×。可调当前账号，也可统一设置。
- 诊断 ZIP 可保存到用户选定的目录，也可通过 Android 分享。应用内部日志和分享缓存保留 7 天。
- 兼容选区网页将游戏入口和启动参数分开提供的情况，并在校验失败时显示具体阶段。

## 在 Windows 构建 ARM64 APK

准备 Python 3.10 或更新版本、JDK、Android SDK Platform 36 和 Build Tools 36.0.0。使用官方 AIR SDK 51.5.1.1，并让编译及打包共用同一个 SDK 目录。[AIR 官方下载](https://airsdk.wxlift.com/download) · [Android SDK 工具](https://developer.android.com/tools)

用 Ruffle 的 `tools/asc/asc.jar` 编译 ActionScript。`playerglobal_import.abc` 由 Ruffle 的 `ruffle_core` 构建脚本生成，通常位于 `target/debug/build/ruffle_core-*/out/`。已知构建使用工作区版本标记为 0.7.1 的 Ruffle 工具链。归档里不含这些编译器和编译产物。[Ruffle 上游](https://github.com/ruffle-rs/ruffle) · [playerglobal 构建入口](https://github.com/ruffle-rs/ruffle/blob/master/core/build.rs)

用 Gradle 解析以下 Google Maven 工件，或自行下载并按 `组织/名称/版本/任意子目录/文件` 放入同一个缓存目录。典型路径为 `androidx.webkit/webkit/1.14.0/local/webkit-1.14.0.aar`。

- `androidx.webkit:webkit:1.14.0`（AAR）
- `androidx.core:core:1.13.1`（AAR）
- `androidx.annotation:annotation-jvm:1.9.1`（JAR，仅编译）

在解压后的源码目录设置以下环境变量。路径示例需替换成自己的安装位置。

```powershell
$env:AIR_SDK = 'C:/tools/AIRSDK-51.5.1.1'
$env:AIR_JDK = 'C:/tools/jdk'
$env:ANDROID_SDK_ROOT = 'C:/tools/Android/Sdk'
$env:ANDROIDX_CACHE = 'C:/tools/gradle-cache/caches/modules-2/files-2.1'
$env:AIR_ASC_JAR = 'C:/tools/ruffle/tools/asc/asc.jar'
$env:AIR_PLAYERGLOBAL_ABC = 'C:/tools/ruffle/target/debug/build/ruffle_core-EXAMPLE/out/playerglobal_import.abc'
$env:AIR_SIGNING_KEYSTORE = 'C:/local-keys/own-release.p12'

python native-qq/build_native.py
python build.py
```

使用自己的 PKCS#12 APK 签名文件，脚本会交互询问密码。自动化构建可由本地环境提供 `AIR_SIGNING_PASSWORD`。生成的 APK 位于 `build-*/`，包名为 `air.local.huoying.airgame`。签名必须相同才能覆盖安装，使用自己新生成的签名时需在独立测试设备或配置中安装。源码构建采用 AIR SDK 默认图标。

`build_native.py` 默认运行 QQ 回跳规则和画布布局检查。网页登录脚本另有离线测试，运行 `node native-qq/qq-return.test.cjs`。测试只使用合成登录数据。

## 许可与发布范围

本项目的自有源码及文档采用 MIT。第三方依赖及来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。AIR SDK、AIR 运行时、游戏资源、反编译输出、签名及签发私钥、QQ 登录缓存和原始诊断记录不属于本仓库的发布内容。

公开分发 APK 前，需分别核对 AIR SDK 的分发条件和腾讯游戏使用条款。当前 Android 画布兼容代码还会访问 AIR 的私有绘图字段，源码开源并不授予这一运行时用法或腾讯游戏接入、素材的权限。[AIR SDK 下载及授权](https://airsdk.wxlift.com/download) · [腾讯游戏许可及服务协议](https://game.qq.com/contract.shtml)
