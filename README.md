# LynMusic_Car

基于 [LynMusic](https://github.com/wesley666/LynMusic)(GPL-3.0-or-later)复刻的**车机专用**本地音乐播放器。原始跨平台代码与上游特性版权归上游作者;本仓库面向 Android 车机做了裁剪与改造,遵循同一开源协议,发布需继续以 GPL-3.0-or-later 分发。

> 本仓库不与上游同步,独立演进;历史渊源见上游仓库与 LICENSE / THIRD_PARTY_LICENSES。

## 定位与适配

- 平台:Android 车机(通用 Android,非 AAOS 专属),最低 **Android 7.0(minSdk 24)**,分辨率 720P ~ 2.5K
- UI:竖屏(底部 Dock,沿用大屏手机逻辑)/ 横屏(宽侧栏,主驾优先布局)双预设,「使用设备」切换后自动重启套用
- 显示:界面缩放 70%~140%、字号 80%~200%、封面 60%~130%、四周边距 0%~30%,全屏模式隐藏系统栏(回到前台自动重套)

## 主要特性

- 本地文件夹导入 + Samba / WebDAV / Navidrome 私有来源
- **U 盘音乐读取**:直读路径 / SAF / MediaStore 三方案,挂载即自动重扫、拔出标记不可用,卷身份持久化(换挂载点不重复建源)
- 扫描:增量(默认,保护收藏/歌单引用)/ 全量 可选;支持启动 1 分钟后自动扫描 U 盘来源
- 歌词搜索 / 逐字歌词 / 歌词分享 / 标签编辑 / 均衡器 / DLNA 之外的完整本地播放能力
- 「我的」页电源按钮完全退出:先保存进度再结束服务与进程,下次启动从上次位置继续

## 构建

```shell
# Android 车机 debug
.\gradlew.bat :automotiveApp:assembleDebug
# Android 车机 release(4 ABI + universal)
.\gradlew.bat :automotiveApp:assembleRelease
# 全量测试(本地 Windows 下 androidHostTest 有环境问题,建议在 CI 跑)
.\gradlew.bat fullTest
```

- Gradle Wrapper 9.6.0 / AGP 9.4.1 / Kotlin 2.4.21 / JDK 21
- 产物命名:`LynMusic_Car-<版本>-release-<abi>.apk`,输出在 `automotiveApp/build/outputs/apk/release/`
- 签名:CI 从 `ANDROID_SIGNING_*` 环境变量(或本地 `local.properties`)读取;签名密钥持久不变,保证覆盖安装

## 发布流程

1. 在 `app-version.xcconfig` 更新 `APP_VERSION_CODE` / `APP_VERSION_NAME`
2. 在 `CHANGELOG.md` 顶部新增 `## <版本号>` 小节写更新日志
3. 打 tag(`v<版本号>`)推送 → Actions 自动构建签名 APK 并创建 Release(附 CHANGELOG 对应小节)

## 申明

1. 本项目**仅供学习与技术研究**，不得用于任何商业用途或非法用途。
2. 作者对使用本项目产生的任何后果**不承担责任**；因使用本软件导致的设备损坏、数据丢失、法律风险等，均由使用者自行承担。
3. 本项目基于 [LynMusic](https://github.com/wesley666/LynMusic) 修改而来，第三方依赖与数据的版权归各自作者所有，详见 [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)。
4. 本项目不提供任何音源内容，仅作为本地音乐播放工具；用户需自行确保所播放内容的合法性。

## 许可证

GPL-3.0-or-later。第三方组件见 [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)。

## 项目结构

```
LynMusic_Car/
├─ automotiveApp/          # 车机 APK 入口模块（打包产物在这里）
├─ app/runtime/            # 应用运行时装配
├─ android/runtime/        # Android 平台能力实现
├─ ui/common/              # 跨端共用 UI 组件与主题
├─ ui/automotive/          # 车机专用界面（竖/横屏、设置页）
├─ shared/core/            # 核心逻辑与版本常量
├─ shared/resources/       # 多语言文案与资源
├─ shared/data/            # Room 数据库与仓库实现
├─ shared/features/        # 业务功能（播放、扫描、歌词等）
├─ player/core/            # 播放器内核封装
├─ cast/api/               # 外部协议接口（已裁剪为最小集）
├─ buildSrc/               # 自定义 Gradle 构建逻辑
├─ tools/                  # Python 辅助校验脚本
├─ .github/workflows/      # CI：构建、签名、指纹校验、发 Release
└─ app-version.xcconfig    # 版本号唯一来源
```
