# Cerberus 🛡️

**Cerberus** 是一款专注于极致隐私和本地安全的 Android 身份验证器 (TOTP)。

它旨在为用户提供一个完全离线、硬件级加密的令牌库。我们坚信：**您的安全密钥不应离开您的物理设备。**

---

## ✨ 核心特性

- 🚫 **零联网权限**：应用不申请 `INTERNET` 权限，从根源杜绝数据泄露。
- 🔐 **硬件级加密**：利用 Android Keystore 系统和 `EncryptedSharedPreferences` 对数据进行持久化存储。
- 📂 **加密备份 (.cerb)**：
  - 支持导出高度安全的加密备份文件。
  - **二次加密**：采用 AES-256-GCM 算法，结合用户自定义的备份密码（通过 PBKDF2 派生密钥）。
- 🧬 **生物识别**：集成指纹及面部识别，兼顾安全与便捷。
- ⌨️ **主密码保护**：所有敏感操作均受主密码校验，防止未授权访问。
- 🎨 **Modern UI**：基于 Jetpack Compose 和 Material 3 设计，支持动态配色。

---

## 🛠️ 技术栈

- **语言**: [Kotlin](https://kotlinlang.org/)
- **UI 框架**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **架构**: MVVM (ViewModel, Repository pattern)
- **安全库**: 
    - `androidx.security:security-crypto` (硬件加密存储)
    - `androidx.biometric:biometric` (生物识别)
- **数据处理**: [GSON](https://github.com/google/gson)
- **算法支持**: [kotlin-onetimepassword](https://github.com/turingcomplete/kotlin-onetimepassword) (TOTP/HOTP)

---

## 🚀 备份与恢复

### 导出备份
1. 进入“设置” -> “数据管理”。
2. 点击“导出加密备份”。
3. 设置一个强健的**备份密码**（该密码不存储在本地，请务必记住）。
4. 选择保存位置，生成 `.cerb` 文件。

### 导入恢复
1. 在新设备或重装应用后，进入“设置”。
2. 点击“导入加密备份”并选择 `.cerb` 文件。
3. 输入导出时设置的密码。
4. 验证通过后，令牌库将自动恢复。

---

## ⚠️ 免责声明

1. **主密码**：主密码是访问加密数据的唯一凭证。Cerberus 不设云端找回机制。**遗忘主密码 = 永久丢失数据**。
2. **备份责任**：备份文件及其密码由用户自行保管。若因备份文件丢失、损坏或遗忘备份密码导致无法恢复，开发者概不负责。
3. **数据安全**：本应用作为开源工具提供，用户需自行承担操作风险（如误删、刷机、设备损坏导致的数据丢失）。

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 授权。

---

**Cerberus** - *Guard your digital identity like a beast.* 🐺
