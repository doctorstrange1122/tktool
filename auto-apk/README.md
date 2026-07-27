# 过肥（自动更新）APK

这是 `tktool` 的自动更新版 APK 源码。与静态版不同，此 APK 打开后会自动跳转到 GitHub Pages (`https://doctorstrange1122.github.io/tktool/`)，因此修改网页内容无需重新打包 APK。

## 目录结构

```
auto-apk/
├── build.sh                          # 一键构建脚本
├── src/main/
│   ├── AndroidManifest.xml           # 应用清单
│   ├── assets/
│   │   └── index.html                # 本地启动页（500ms 后跳转 GitHub Pages）
│   └── java/com/tktool/auto/
│       ├── MainActivity.java         # 主 Activity（WebView + JSBridge）
│       └── ScanActivity.java         # 原生扫码（Camera1 + ZXing）
└── README.md
```

## 构建要求

- JDK 8+
- Android SDK (build-tools 29.0.3+, platform android-29)
- curl, keytool

## 构建

```bash
# 设置环境变量（可选，默认路径见脚本）
export ANDROID_SDK=/path/to/android-sdk

# 构建
bash build.sh
```

构建产物：`build/tktool_auto.apk`

## 工作原理

1. APK 启动 → WebView 加载本地 `assets/index.html`
2. 本地 HTML 显示 "正在加载..."，500ms 后 `window.location.href` 跳转到 GitHub Pages
3. GitHub Pages 内容自动同步到 APK（无需重新打包）
4. 网页中的 `NativeBridge.startNativeScan()` 调用原生 Camera1 + ZXing 扫码

## 与网页版的关系

- **网页版**（仓库根目录）：`index.html` 等文件，部署在 GitHub Pages
- **自动更新 APK**（本目录）：独立构建，只包含启动页和原生扫码逻辑，内容从 GitHub Pages 加载
- 两者互不影响，修改网页版会自动同步到已安装的 APK
