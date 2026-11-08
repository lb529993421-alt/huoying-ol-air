# 第三方许可与来源

本仓库的 `LICENSE` 只许可项目自有代码及文档。下列组件由构建者另行取得，归档仅保留其许可或来源说明。

| 组件 | 用途与版本 | 许可及来源 |
| --- | --- | --- |
| AndroidX WebKit | `androidx.webkit:webkit:1.14.0`，构建 Android 原生扩展 | Android Open Source Project，Apache-2.0。[AndroidX](https://developer.android.com/jetpack/androidx) · [许可全文](licenses/Apache-2.0.txt) |
| AndroidX Core | `androidx.core:core:1.13.1`，构建 Android 原生扩展 | Android Open Source Project，Apache-2.0。[AndroidX](https://developer.android.com/jetpack/androidx) · [许可全文](licenses/Apache-2.0.txt) |
| AndroidX Annotation JVM | `androidx.annotation:annotation-jvm:1.9.1`，Java 编译依赖 | Android Open Source Project，Apache-2.0。[AndroidX](https://developer.android.com/jetpack/androidx) · [许可全文](licenses/Apache-2.0.txt) |
| Apache Flex ASC | ActionScript 编译器，由 Ruffle 工具目录提供 | Apache-2.0，包含 Adobe 与 Mountain View Compiler Company 版权声明。[Apache Flex](https://flex.apache.org/) · [许可全文](licenses/Apache-2.0.txt) · [ASC 原始 NOTICE](licenses/ASC-NOTICE.txt) |
| Ruffle | 构建时生成 `playerglobal_import.abc` 的上游项目 | MIT 或 Apache-2.0。依赖及生成文件按所用上游版本的条款取得。[Ruffle 源码和许可证](https://github.com/ruffle-rs/ruffle) |
| Android SDK、Build Tools、JDK | 编译、对齐和签名工具 | 以自行安装的发行版及其随附条款为准。[Android 工具](https://developer.android.com/tools) · [OpenJDK](https://openjdk.org/) |
| Adobe AIR SDK 和运行时 | 51.5.1.1，编译扩展并以 captive runtime 打包 | 专有授权，以官方 SDK 的随附许可为准。[AIR 下载和授权](https://airsdk.wxlift.com/download) |
| 《火影忍者 OL》和 QQ 登录 | 官方网页、游戏 SWF、图片、音频与登录服务 | 归腾讯及相关权利人所有。代码中的服务地址和兼容符号用于说明互操作对象。[游戏官网](https://huoying.qq.com/) · [腾讯游戏协议](https://game.qq.com/contract.shtml) |

构建脚本将 AndroidX 许可正文和组件声明写入 APK。若重新分发任何第三方源码或二进制，应另外保留对应的许可证及上游原有的版权声明和 `NOTICE`。本仓库不包含 Windows 客户端的 Wry、Tauri 或字体副本。
