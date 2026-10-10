# pis' input method

pis 是基于 Fcitx5 Android 0.1.3 固定底座修改的 Android 输入法。版本 2.0.1 使用独立包名 `io.pis.inputmethod`；上游版权和第三方许可保留。

## 特点与亮点

- **离线输入与本地 AI**：拼音候选、词义查询、整句翻译、语音识别和标点处理都在手机上完成，日常使用无需联网或登录账号。
- **全拼与首道双拼**：支持中英切换、声韵母角标、模糊音和个人词库学习；提供 QWERTY 与纯数字九宫格。
- **候选栏横向浏览**：左右滑动查看更多中文候选和英文单词，点击选词、长按查看词义，右侧状态灯保持固定。
- **数字与符号快捷输入**：26 个字母键长按或上滑输入角标数字/符号；句号长按离线翻译，空格长按语音输入，逗号长按剪贴板。
- **可管理的个人词频**：搜索、添加词条，调整近期次数与固定优先级，清零或删除学习记录。
- **简洁外观与触感**：冷灰键帽、微玻璃输出栏、24 灯反馈；支持主题颜色、键盘高度、底部上移、按键音和震动设置。

## 本地处理与隐私

- **应用无联网权限**：正式 APK 不申请 `INTERNET` 权限，输入文本、翻译内容、录音和剪贴板内容不通过应用网络上传。
- **录音在内存中处理**：长按空格启动语音输入，识别由本地模型完成；结束或取消后释放麦克风，不保存录音文件。
- **剪贴板历史在本机管理**：当前默认开启剪贴板监听，读取并保存复制的文本，供粘贴、固定和删除历史条目使用。
- **个人学习由本地数据驱动**：词频、热词和纠正规则在设备上保存；语音个人词典已排除 Android 云备份。
- **密码输入保护**：密码字段禁用翻译和录音；个人学习遵循编辑器提供的限制。

## 当前版本

2.0.1 修复候选分页崩溃，首道双拼按准确编码匹配，字母键快捷输入改为上滑。零声母 `UE → e`、`UI → ei`、`UF → eng`；带声母时 `M` 仍对应 `ei`，`V` 对应 `ui`。

- [下载最新 ARM64 APK](https://github.com/pipi-433/pis-input-method/releases/latest)
- [当前实现状态](PIS_MIGRATION_STATUS.md) · [候选栏滑动说明](docs/releases/pis-horizontal-candidates.md)
- [2.0.1 修复与验证记录](docs/releases/pis-2.0.1.md) · [此前 2.0.0 交付记录](docs/releases/pis-2.0.0.md)
- [许可范围](LICENSING.md) · [第三方声明](THIRD_PARTY_NOTICES.md) · [完整许可文本](LICENSES/) · [NOTICE](NOTICE)
- [修改记录](CHANGES.md) · [构建与对应源码](BUILD.md) · [许可资料索引](docs/compliance/README.md)

| 内容 | 当前状态 |
|---|---|
| 当前 GitHub 源码 | 包含候选分页修复、准确首道编码和字母上滑快捷输入 |
| 2.0.1 ARM64 APK | 修复版，包信息见 2.0.1 验证记录 |
| 此前 2.0.0 APK / 本地源码 ZIP | 历史交付，不包含此次候选栏滑动 |

以下保留上游原 README 供参考，其中下载链接、签名、功能和环境说明属于上游项目；pis 的版本、构建与发行信息见以上文档。

---

## 上游原始说明

[Fcitx5](https://github.com/fcitx/fcitx5) input method framework and engines ported to Android.

## Download

[<img src="https://github.com/rubenpgrady/get-it-on-github/raw/refs/heads/main/get-it-on-github.png" alt="Git it on GitHub" width="207" height="80">](https://github.com/fcitx5-android/fcitx5-android/releases/latest)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" width="207" height="80">](https://f-droid.org/packages/org.fcitx.fcitx5.android)
[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="207" height="80">](https://play.google.com/store/apps/details?id=org.fcitx.fcitx5.android)

You can also download the **latest CI build** on our Jeninks server: [![build status](https://img.shields.io/jenkins/build.svg?jobUrl=https://jenkins.fcitx-im.org/job/android/job/fcitx5-android/)](https://jenkins.fcitx-im.org/job/android/job/fcitx5-android/)

> [!NOTE]
> APKs downloaded from GitHub Release/F-Droid/Jenkins have the same signature, which means they're compatible when upgrading, but Google Play's do not.
> <details>
> <summary>(click here for detailed signature info)</summary>
> <ul>
> <li>Package Name: <code>org.fcitx.fcitx5.android</code></li>
> <li>Certificate SHA-256 fingerprint:</li>
> <ul>
> <li>GitHub Release/Jenkins/F-Droid</li>
> <code>E4:DB:1E:9E:DF:F1:36:29:D0:7D:E4:BB:F8:16:5F:E9:BD:85:57:AB:55:09:26:72:DA:8E:40:DB:E4:84:EC:D7</code>
> <li>Google Play</li>
> <code>06:53:6F:F6:E8:76:C0:14:E1:4B:44:6F:61:FA:2B:80:9E:06:67:39:A1:D1:17:0D:0A:7A:89:88:4C:48:00:33</code>
> </ul>
> </ul>
> </details>

In case you want Fcitx5 on other platforms: [macOS](https://github.com/fcitx-contrib/fcitx5-macos), [iOS](https://github.com/fcitx-contrib/fcitx5-ios), [HarmonyOS](https://github.com/fcitx-contrib/fcitx5-harmony), [ChromeOS](https://github.com/fcitx-contrib/fcitx5-chrome), [Windows](https://github.com/fcitx-contrib/fcitx5-windows); or [try Fcitx5 in the browser](https://fcitx-contrib.github.io/online/index.html)

## Project status

### Supported Languages

- English (with spell check)
- Chinese
  - Pinyin, Shuangpin, Wubi, Cangjie and custom tables (built-in, powered by [fcitx5-chinese-addons](https://github.com/fcitx/fcitx5-chinese-addons))
  - Zhuyin/Bopomofo (via [Chewing Plugin](./plugin/chewing))
  - Jyutping (via [Jyutping Plugin](./plugin/jyutping/), powered by [libime-jyutping](https://github.com/fcitx/libime-jyutping))
- Vietnamese (via [UniKey Plugin](./plugin/unikey), supports Telex, VNI and VIQR)
- Japanese (via [Anthy Plugin](./plugin/anthy))
- Korean (via [Hangul Plugin](./plugin/hangul))
- Sinhala (via [Sayura Plugin](./plugin/sayura))
- Thai (via [Thai Plugin](./plugin/thai))
- Generic (via [RIME Plugin](./plugin/rime), supports importing custom schemas)

### Implemented Features

- Virtual Keyboard (layout not customizable yet)
- Expandable candidate view
- Clipboard management (plain text only)
- Theming (custom color scheme, background image and dynamic color aka monet color after Android 12)
- Popup preview on key press
- Long press popup keyboard for convenient symbol input
- Symbol and Emoji picker
- Plugin System for loading addons from other installed apk
- Floating candidates panel when using physical keyboard

### Planned Features

- Customizable keyboard layout
- More input methods (via plugin)

## Screenshots

|拼音, Material Light theme, key border enabled|自然码双拼, Pixel Dark theme, key border disabled|
|:-:|:-:|
|<img src="https://github.com/fcitx5-android/fcitx5-android/assets/13914967/bd429247-62d9-4c78-bab8-70ef3ce47588" width="360px">|<img src="https://github.com/fcitx5-android/fcitx5-android/assets/13914967/3ae969c1-7ed0-4f92-a5df-19dc8c90a8c3" width="360px">|

|Emoji picker, Pixel Light theme, key border enabled|Symbol picker, Material Dark theme, key border disabled|
|:-:|:-:|
|<img src="https://user-images.githubusercontent.com/13914967/202181845-6a5f6bb2-a877-468c-851a-fd7e66e64ed4.png" width="360px">|<img src="https://user-images.githubusercontent.com/13914967/202181861-dd253439-1d5e-4f5f-9535-934f28796a6b.png" width="360px">|

## Get involved

Trello kanban: https://trello.com/b/gftk6ZdV/kanban

Matrix Room: https://matrix.to/#/#fcitx5-android:mozilla.org

Discuss on Telegram: [@fcitx5_android_group](https://t.me/fcitx5_android_group) ([@fcitx5_android](https://t.me/fcitx5_android) originally)

## Build

### Dependencies

- Android SDK Platform & Build-Tools 35.
- Android NDK (Side by side) 25 & CMake 3.22.1, they can be installed using SDK Manager in Android Studio or `sdkmanager` command line.
- [KDE/extra-cmake-modules](https://github.com/KDE/extra-cmake-modules)
- GNU Gettext >= 0.20 (for `msgfmt` binary; or install `appstream` if you really have to use gettext <= 0.19.)

### How to set up development environment

<details>
<summary>Prerequisites for Windows</summary>

- Enable [Developer Mode](https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) so that symlinks can be created without administrator privilege.

- Enable symlink support for `git`:

    ```shell
    git config --global core.symlinks true
    ```

</details>

First, clone this repository and fetch all submodules:

```shell
git clone git@github.com:fcitx5-android/fcitx5-android.git
git submodule update --init --recursive
```

Install `extra-cmake-modules` and `gettext` with your system package manager:

```shell
# For Arch Linux (Arch has gettext in it's base meta package)
sudo pacman -S extra-cmake-modules

# For Debian/Ubuntu
sudo apt install extra-cmake-modules gettext

# For macOS
brew install extra-cmake-modules gettext

# For Windows, install MSYS2 and execute in its shell (UCRT64)
pacman -S mingw-w64-ucrt-x86_64-extra-cmake-modules mingw-w64-ucrt-x86_64-gettext
# then add C:\msys64\ucrt64\bin to PATH
```

Install Android SDK Platform, Android SDK Build-Tools, Android NDK and cmake via SDK Manager in Android Studio:

<details>
<summary>Detailed steps (screenshots)</summary>

**Note:** These screenshots are for references and the versions in them may be out of date.
The current recommended versions are recorded in [Versions.kt](build-logic/convention/src/main/kotlin/Versions.kt) file.

![Open SDK Manager](https://user-images.githubusercontent.com/13914967/202184493-3ee1546b-0a83-4cc9-9e41-d20b0904a0cf.png)

![Install SDK Platform](https://user-images.githubusercontent.com/13914967/202184534-340a9e7c-7c42-49bd-9cf5-1ec9dcafcf32.png)

![Install SDK Build-Tools](https://user-images.githubusercontent.com/13914967/202185945-0c7a9f39-1fcc-4018-9c81-b3d2bf1c2d3f.png)

![Install NDK](https://user-images.githubusercontent.com/13914967/202185601-0cf877ea-e148-4b88-bd2f-70533189b3d4.png)

![Install CMake](https://user-images.githubusercontent.com/13914967/202184655-3c1ab47c-432f-4bd7-a508-92096482de50.png)

</details>

### Trouble-shooting

- Android Studio indexing takes forever to complete and cosumes a lot of memory.

    Switch to "Project" view in the "Project" tool window (namely the file tree side bar), right click `lib/fcitx5/src/main/cpp/prebuilt` directory, then select "Mark Directory as > Excluded". You may also need to restart the IDE to interrupt ongoing indexing process.

- Gradle error: "No variants found for ':app'. Check build files to ensure at least one variant exists." or "[CXX1210] <whatever>/CMakeLists.txt debug|arm64-v8a : No compatible library found"

    Examine if there are environment variables set such as `_JAVA_OPTIONS` or `JAVA_TOOL_OPTIONS`. You might want to clear them (maybe in the startup script `studio.sh` of Android Studio), as some gradle plugin treats anything in stderr as errors and aborts.

## Nix

Appropriate Android SDK with NDK is available in the development shell.  The `gradlew` should work out-of-the-box, so you can install the app to your phone with `./gradlew installDebug` after applying the patch mentioned above. For development, you may want to install the unstable version of Android Studio, and point the project SDK path to `$ANDROID_SDK_ROOT` defined in the shell. Notice that Android Studio may generate wrong `local.properties` which sets the SDK location to `~/Android/SDK` (installed by SDK Manager). In such case, you need specify `sdk.dir` as the project SDK in that file manually, in case Android Studio sticks to the wrong global SDK.
