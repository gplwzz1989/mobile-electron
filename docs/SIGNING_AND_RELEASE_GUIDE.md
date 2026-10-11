# Mobile Electron 移动端正式发布与数字签名配置指南

> **适用版本**：Mobile Electron v2.0.0+  
> **适用平台**：Android (APK & AAB) / iOS (IPA & App Store)  
> **核心目标**：指导开发者从本地免签/调试包顺利平滑过渡到商业化带签名的正式发布包，满足各大应用商店（Google Play、国内各大厂商市场、Apple App Store）的严格上架与数字签名安全准则。

---

## 一、 为什么商业化发布必须进行数字签名？

1. **应用市场强制准入**：无论是苹果 App Store、Google Play，还是华为、小米等国内应用市场，均要求所有提交的安装包必须包含合法的商业数字证书签名，未经签名或使用默认调试签名的包将被直接拒绝上传；
2. **防篡改与完整性校验**：数字签名保证了安装包在传输与分发过程中没有被第三方逆向注入恶意代码；
3. **应用平滑升级（版本覆盖）**：操作系统依靠证书公钥鉴别 App 身份。如果后续版本证书不一致，系统会提示“签名不一致，无法覆盖安装”，导致老用户无法更新。

---

## 二、 Android 端正式签名与打包指南

Mobile Electron 内置了**智能签名检测引擎**：
* 当检测到根目录存在 `key.properties` 或系统环境变量时，自动启用正式证书并开启 **V1 (Jar Signature) + V2 (Full APK Signature)** 双重强签名；
* 当未检测到配置时，自动回退到开发调试签名，保证日常开发与测试零阻力。

### 1. 第一步：生成正式企业签名密钥库 (JKS)

打开终端执行以下命令（只需生成一次，**请务必备份妥善保管生成的 `release.jks` 文件及其密码**）：

```bash
keytool -genkey -v \
        -keystore release.jks \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -alias release-key
```

* **参数解析**：
  * `-keystore release.jks`：输出的密钥库文件名；
  * `-validity 10000`：证书有效期（建议设置 25 年以上，避免证书过期无法升级）；
  * `-alias release-key`：条目别名（后续配置需要用到）；
  * 执行过程中会提示设置密码（如 `12345678`）并填写机构名称（如公司/项目拼音）。

---

### 2. 第二步：配置 `key.properties`

在项目根目录下，执行签名初始化命令生成配置文件：

```bash
npm run sign:setup
# 或 npx mobile-electron sign:setup
```

该命令会在根目录下生成 `key.properties`（该文件已在 `.gitignore` 中被保护，密码绝不会被误提交至 Git 仓库）。打开并填入您在第一步中设置的参数：

```properties
# 密钥库密码
storePassword=your_store_password

# 别名条目密码 (若未单独设置通常与 storePassword 相同)
keyPassword=your_key_password

# 证书别名 (生成时指定的 -alias 参数)
keyAlias=release-key

# 密钥库路径 (支持相对于项目根目录的相对路径或绝对路径)
storeFile=release.jks
```

---

### 3. 第三步：一键打包正式商业发布包

#### 场景 A：打包国内各大应用市场通用的正式签名 APK
```bash
# 方式 1: 通过 npm scripts 一键打包
npm run build:apk:release

# 方式 2: 通过 CLI 一键构建
npx mobile-electron build --release

# 方式 3: 原生 Gradle 命令构建
./gradlew assembleRelease
```
* **输出产物路径**：`app/build/outputs/apk/release/app-release.apk`

#### 场景 B：打包 Google Play 强制要求的正式签名 AAB (Android App Bundle)
```bash
# 方式 1: 通过 npm scripts
npm run build:aab

# 方式 2: 通过 CLI
npx mobile-electron build --release --aab

# 方式 3: 原生 Gradle 命令
./gradlew bundleRelease
```
* **输出产物路径**：`app/build/outputs/bundle/release/app-release.aab`

---

### 4. 第四步：验证 APK 签名是否合规

使用 Android SDK 自带的 `apksigner` 工具验证生成的 Release APK：

```bash
apksigner verify -v app/build/outputs/apk/release/app-release.apk
```
* 若输出包含 `Verified using v1 scheme (JAR signing): true` 与 `Verified using v2 scheme (APK Signature Scheme v2): true`，即代表已完全符合各大应用市场的合规签名标准。

---

### 5. CI/CD 构建机签名（无文件环境变量注入）

在 GitHub Actions 或 Jenkins 等云端构建机上，若不希望将 `release.jks` 存入仓库，可通过环境变量安全注入：
* `KEYSTORE_PATH`：云端密钥库路径；
* `KEYSTORE_PASSWORD`：密钥库密码；
* `KEY_ALIAS`：证书别名；
* `KEY_PASSWORD`：别名密码；

Gradle 将自动识别这 4 个环境变量并完成正式签名！

---

## 三、 iOS 端正式签名与 App Store 发布指南

iOS 系统的签名由 **开发者证书 (Certificate)** 与 **描述文件 (Provisioning Profile)** 双重绑定保证。

### 方式 1：本地 Xcode 自动签名（推荐，最省心）

1. 用 Xcode 打开项目工程：[`ios/MobileElectron.xcodeproj`](ios/MobileElectron.xcodeproj)；
2. 在左侧文件导航栏点击根节点 **MobileElectron** 项目，进入 **Signing & Capabilities** 选项卡；
3. 勾选 **Automatically manage signing**（自动管理签名）；
4. 在 **Team** 下拉列表中登录并选择您的付费 Apple Developer 团队账号；
5. 在顶部设备列表选择 **Any iOS Device (arm64)**；
6. 顶部菜单点击 **Product** -> **Archive**；
7. 构建完成后在弹出的 Organizer 窗口点击 **Distribute App**，选择 **App Store Connect** 即可一键直接上传至苹果商店后台！

---

### 方式 2：XcodeGen 配置文件参数化指定

如果您通过自动化脚本由 `project.yml` 重新生成 Xcode 工程，可以在 `ios/project.yml` 中配置您的开发者 Team ID：

```yaml
targets:
  MobileElectron:
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.yourcompany.app
        DEVELOPMENT_TEAM: "XXXXXXXXXX" # 您的 10 位苹果开发者 Team ID
        CODE_SIGN_STYLE: "Automatic"
        CODE_SIGN_IDENTITY: "Apple Distribution"
        CODE_SIGNING_REQUIRED: "YES"
        CODE_SIGNING_ALLOWED: "YES"
```
保存后在 `ios` 目录下执行 `xcodegen generate`，即可生成携带团队签名的标准 Xcode 项目。

---

## 四、 常见问题与避坑指南 (FAQ)

### Q1: 安卓签名密码或 `.jks` 密钥文件丢失了怎么办？
* **应用尚未上架**：直接重新生成一个全新的 `release.jks`，更新 `key.properties` 即可；
* **已上架国内市场**：必须使用原证书才能发布新版本覆盖旧版本！若丢失通常需要向应用市场客服提交软著与营业执照申请重置签名，代价极大，**务必多地离线冷备份**；
* **已上架 Google Play**：若开启了 **Play App Signing**（Google 托管签名密钥），可在 Google Play Console 控制台联系在线支持协助重置上传密钥。

### Q2: 为什么执行 `npm run build:apk:release` 时提示“未检测到 key.properties”？
* 这是框架的**防呆优雅降级保护**，说明您尚未在根目录创建 `key.properties`；
* 您只需运行 `npm run sign:setup`，随后在生成的 `key.properties` 中填入证书路径与密码，重新执行即可。

### Q3: 为什么 iOS 导出的测试 IPA 无法在真机上安装？
* 若采用 Ad-Hoc 证书分发，目标 iPhone 的设备 UDID 必须预先添加到 Apple Developer 后台的 Devices 列表中；
* 若面向所有外部测试人员，推荐通过 App Store Connect 的 **TestFlight** 官方测试渠道分发，无需收集 UDID。
