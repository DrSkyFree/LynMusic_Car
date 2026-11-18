# LynMusic_Car 项目级规范（RULES）

> 本文件是 `LynMusic_Car` 项目的**项目级约束**，继承工作区 `D:\AppData\DSH_Work\RULES.md`（下称"工作区规范"）的全部条款。
> 两者冲突时以更严格的一方为准；本文件只负责补充本项目特有的、不可变的技术事实。
> AI 每次在 `LynMusic_Car` 目录下开工前，必须先读工作区规范，再读本文件。

---

## 一、身份与不可变量（改了就会出事的四条）

| 项 | 固定值 | 说明 |
| --- | --- | --- |
| 应用标识（applicationId） | `top.iwesley.lyn.music.automotive` | **永久禁止更改**，改了等于换一个 App，用户数据全丢 |
| 命名空间（namespace） | `top.iwesley.lyn.music.automotive` | 同上 |
| 最低系统版本（minSdk） | `24`（Android 7.0） | 不得上调，否则老车机装不上 |
| 签名密钥 | 现有一份，永久固定 | **禁止重新生成、禁止更换** |

版本号文件：`app-version.xcconfig`

| 键 | 当前值 | 规则 |
| --- | --- | --- |
| `APP_VERSION_CODE` | `120` | **每次发布必须严格递增**，否则系统拒绝覆盖安装 |
| `APP_VERSION_NAME` | `1.2.0` | 语义化版本；破坏性改动升主版本号，由 AI 建议、用户确认 |

当前已发布标签：`v1.1.5`、`v1.2.0`。

---

## 二、技术栈清单（禁止擅自更换或升级版本）

### 2.1 语言与构建工具

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Kotlin | `2.4.21` | 主力开发语言，跨平台共用一套代码 |
| Compose Multiplatform | `1.12.2` | 一套 UI 代码复用多端 |
| Android Gradle Plugin（AGP） | `9.4.1` | 安卓构建插件 |
| Gradle Wrapper | `9.6.0` | 构建工具本体 |
| JDK | `21` | 编译所需 Java 版本 |
| KSP | `2.3.12` | Kotlin 注解处理器（Room 用到） |

### 2.2 Android SDK 版本

| 项 | 值 |
| --- | --- |
| compileSdk | `37` |
| targetSdk | `36` |
| minSdk | `24`（不可上调） |
| NDK | `28.2.13676358` |

### 2.3 核心依赖（`gradle/libs.versions.toml` 为准）

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Room | `2.8.5` | 本地数据库（曲库、收藏、歌单） |
| Media3 | `1.11.1` | 音频播放内核（ExoPlayer） |
| Ktor | `3.6.0` | 网络请求（Samba / WebDAV / Navidrome 来源） |
| Coil | `3.6.3` | 图片加载（专辑封面） |
| Kotlinx Coroutines | `1.11.0` | 协程（异步任务） |
| Kotlinx Serialization | `1.12.0` | JSON 序列化 |
| SQLite（bundled） | `2.7.1` | Room 底层数据库引擎 |
| Compose Material3 | `1.12.0-alpha03` | UI 组件库 |
| SMBJ | `0.15.0` | SMB/Samba 共享协议支持 |
| Sardine（Android） | `0.9` | WebDAV 协议支持 |
| jaudiotagger | `3.0.1` | 音乐标签（ID3）读写 |
| XMLUtil | `1.0.2.1` | XML 解析 |

**修改任何版本都必须先列出"包名 + 用途 + 版本"并经用户确认**（工作区规范第二节）。

---

## 三、项目结构（新增文件必须放入下列对应模块）

```
LynMusic_Car/
├─ .github/workflows/        # CI：构建、签名、指纹校验、发 Release
│  ├─ build_kmp.yml          # 主构建流水线（打 v* 标签触发）
│  └─ verify_gradle.yml      # Gradle 配置校验
├─ automotiveApp/            # 【入口】车机 APK 应用模块（打包成安装包的地方）
├─ app/runtime/              # 应用运行时（Android 侧启动装配）
├─ android/runtime/          # Android 平台能力实现（文件、存储、系统服务）
├─ ui/
│  ├─ common/                # 跨端共用 UI 基础组件与主题
│  └─ automotive/            # 车机专用界面（竖屏/横屏、主驾布局、设置页）
├─ shared/
│  ├─ core/                  # 核心逻辑（不含界面、不含 IO）；版本常量在此生成
│  ├─ resources/             # 多语言文案、图片等资源
│  ├─ data/                  # 数据层：Room 数据库、仓库实现（含 schemas/ 迁移快照）
│  └─ features/              # 业务功能模块（播放、扫描、歌词等）
├─ player/core/              # 播放器内核封装（Media3 之上的业务封装）
├─ cast/api/                 # 投屏等外部协议接口（车机版已裁剪为最小集）
├─ buildSrc/                 # 自定义 Gradle 构建逻辑（签名配置等）
├─ gradle/libs.versions.toml # 依赖版本总表（改版本只许改这里）
├─ tools/                    # Python 辅助脚本（UI 文案/资源键校验）
├─ app-version.xcconfig      # 版本号唯一来源
├─ README.md / CHANGELOG.md / LICENSE / THIRD_PARTY_LICENSES.md
└─ RULES.md                  # 本文件
```

规则：

1. 新增 Kotlin 代码放进对应模块的 `src/commonMain` 或 `src/androidMain`，**禁止在根目录直接建 `.kt` 文件**。
2. `tools/` 下只放校验脚本，**禁止提交 `__pycache__/`、`*.pyc`**。
3. `local.properties`、任何 `*.jks` / `*.keystore` **禁止入库**。

---

## 四、构建与签名

### 4.1 常用命令

```shell
# 车机 debug 版
.\gradlew.bat :automotiveApp:assembleDebug

# 车机 release 版（4 个 ABI + universal）
.\gradlew.bat :automotiveApp:assembleRelease

# 全量测试（本地 Windows 下 androidHostTest 有环境问题，建议在 CI 跑）
.\gradlew.bat fullTest
```

产物位置：`automotiveApp/build/outputs/apk/release/`
产物命名：`LynMusic_Car-<版本>-release-<abi>.apk`

### 4.2 签名凭据（全部走 secrets，禁止明文）

CI 通过以下 4 个 GitHub Secrets 注入，**名称不可更改**：

| Secret 名 | 用途 |
| --- | --- |
| `ANDROID_SIGNING_STORE_FILE_B64` | keystore 文件的 base64 内容 |
| `ANDROID_SIGNING_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_SIGNING_KEY_ALIAS` | 密钥别名 |
| `ANDROID_SIGNING_KEY_PASSWORD` | 密钥密码 |

本地构建时可从 `local.properties` 读取（该文件已在 `.gitignore` 中，**禁止提交**）。

### 4.3 签名校验（已由 CI 自动执行，不可删除该步骤）

`build_kmp.yml` 中的 `Verify APK signatures` 步骤会用 `apksigner verify --print-certs` 打印证书信息，遇到未签名包直接失败。

**每次发布前必须人工比对证书 SHA-256 指纹与上一版一致**；不一致时立即停止发布并询问用户（工作区规范第七节第 5 条）。

---

## 五、发布流程（本项目实际操作步骤）

1. 修改 `app-version.xcconfig`：`APP_VERSION_CODE` **严格 +1**，`APP_VERSION_NAME` 按语义化版本更新。
2. 在 `CHANGELOG.md` 顶部新增 `## <版本号> - <日期>` 小节，分"新增 / 修复 / 变更"三栏。
3. 更新 `README.md` 中受影响的说明（功能、结构、依赖版本）。
4. 代码审计（见工作区规范第四节、第五节）。
5. **向用户列出改动文件清单与原因，确认后**再提交、打标签。
6. 推送 `v<版本号>` 标签 → Actions 自动构建、签名、校验指纹、上传 Release。
7. 跟进 Actions 结果直到绿灯；失败时读日志分析原因给出方案，**禁止盲目重跑**。

---

## 六、数据兼容性（覆盖安装不丢数据）

1. Room 数据库文件位于应用私有目录，**升级只允许加字段、加表**。
2. 禁止直接删除或重命名已有字段；改变字段语义必须通过迁移脚本（Migration）显式转换。
3. `shared/data/schemas/` 下保存 Room schema 快照，改动实体后必须一并提交新快照。
4. 任何涉及数据结构的改动，必须在发布说明中标注"**数据兼容性：已验证可覆盖升级**"。

---

## 七、远端与仓库信息

| 项 | 值 |
| --- | --- |
| GitHub 仓库 | `https://github.com/DrSkyFree/LynMusic_Car` |
| 默认分支 | `main` |
| 上游来源 | `https://github.com/wesley666/LynMusic`（GPL-3.0-or-later） |
| 本地 `origin` 备注 | 当前指向本地目录 `D:\AppData\DSH_Work\LynMusic`（上游副本），**推送 GitHub 需显式指定远程地址** |

> 注意：本地 `origin` 是上游副本目录，不是 GitHub。直接 `git push origin` 不会推到 GitHub。

---

## 八、其他

1. 本项目遵循 GPL-3.0-or-later，发布需继续以同一协议分发；第三方组件见 `THIRD_PARTY_LICENSES.md`。
2. `README.md` 必须包含申明章节（仅供学习研究、作者不担责、第三方版权归原作者），缺失即补。
3. 本文件中的版本号、模块名在发生变更时必须同步更新，禁止留下过期描述。
