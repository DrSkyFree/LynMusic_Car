# 车机界面重构设计方案(7 项)

- 版本:v1.2.2(待发布)
- 基线:main = 914f867
- 目标:按用户 m02508 + m02507 拍板意见重排设置与「我的」页,收敛到车机使用场景

## 一、需求与拍板结论

| # | 需求 | 拍板结论(m02507) |
|---|---|---|
| 1 | 设置-通用里的「使用设备」「屏幕缩放与边距调整」搬到设置-主题,「主题」改名「主题与缩放」 | 采纳 |
| 2 | 边距改成上/下分别可调 | **横向 padding 不动**,只纵向拆上下 |
| 3 | 曲库扫描单独成设置一级项「曲库管理」 | 采纳 |
| 4 | 歌词项去掉自填 API/Key,改为公开渠道获取歌词与封面,改名「歌词与封面」,歌词/封面各一个开关 | **只用 LRCLIB** |
| 5 | 空间管理去掉播放缓存/离线音乐/歌词分享/标签编辑 | 播放缓存/离线音乐**保留**;歌词分享临时、标签编辑临时**去掉** |
| 6 | 我的页去掉每日推荐、最近播放专辑,增加专辑/歌手索引 | 「我的」页直接跳到曲库 Album/Artist tab |
| 7 | 歌单、喜欢放进「我的」里面 | 采纳(导航栏一级项收敛) |
| 8 | 关于本机去掉 | 采纳 |

## 二、分区结构(改后)

```
设置
├─ 通用      语言 / 启动自动播放 / 启动进入全屏播放 / 桌面歌词 / 播放页封面样式 / 扩展解码器 / Navidrome 音质
├─ 主题与缩放 主题预设 / 文字配色 / 自定义配色 / 【使用设备】/ 【屏幕缩放与边距(全屏+缩放四档+上边距+下边距)】
├─ 曲库管理   【歌曲来源】(本地/U盘/Samba/WebDAV/Navidrome/Subsonic)/【曲库扫描】
├─ 歌词与封面 【歌词开关】/【封面开关】
├─ 空间管理   封面缓存 / 播放缓存 / 离线音乐
└─ 关于应用
```

删除:`AboutDevice`(关于本机)整个分区。

## 三、逐项实现

### 项 1 / 项 3:设置分区重排

**改动文件**
- `ui/automotive/src/commonMain/kotlin/top/iwesley/lyn/music/automotive/ui/SettingsNavigation.kt:7-15` 枚举改为
  `General, Theme, Library, Lyrics, Storage, AboutApp, Help`
- `SettingsUi.kt:281-350`(横屏)、`:381-444`(移动竖屏)两处 `when(section)` 去掉 `AboutDevice`、新增 `Library`
- `SettingsUi.kt:1924-1959` `settingsSectionTitle` / `settingsSectionSubtitle` / `settingsSectionIcon` 同步
- `SettingsUi.kt:1000-1007` 通用页尾部移走三张卡
- `SettingsUi.kt:1304-1419` `ThemeSettingsPane` 尾部追加 `CarDisplayDeviceSettingsCard` + `CarDisplayScalingSettingsCard`
- 新增 `LibrarySettingsPane`,只放 `LibraryScanSettingsCard`
- 删除 `AboutDeviceSettingsPane`(:1962)及其专用 composable

**文案**:新增 `settings_section_library_title` / `settings_section_library_subtitle`,`settings_section_theme_title` 中文改「主题与缩放」,删除 `settings_section_about_device_*`。三语同步。

### 项 2:边距上下拆分

**数据层**(`shared/core/.../model/Preferences.kt`)
- `CarDisplayPreferencesStore` 用两个字段替换一个:
  ```
  val carContentMarginTop: StateFlow<Float>
  val carContentMarginBottom: StateFlow<Float>
  suspend fun setCarContentMarginTop(value: Float)
  suspend fun setCarContentMarginBottom(value: Float)
  ```
- 常量:`DEFAULT_CAR_CONTENT_MARGIN_TOP/BOTTOM = 0.0f`,范围沿用 `CAR_CONTENT_MARGIN_MIN..MAX`(0~0.3)

**迁移**:`AndroidPlatform.kt` 读取时,若新键不存在而旧键 `car_content_margin` 存在,则 top=bottom=旧值;旧键保留不删(只加不删原则)。

**渲染**(`AutomotiveShell.kt:795-829`)
`Modifier.proportionalShellContentMargin(fraction)` 改为:
```kotlin
private fun Modifier.proportionalShellContentMargin(topFraction: Float, bottomFraction: Float): Modifier
```
只扣纵向:top = maxHeight×topFraction,bottom = maxHeight×bottomFraction,**横向不再扣减**(沿用各页面原有 horizontal padding)。
调用点 `:509`、`:718`;shell 参数 `:310`、`:678` 由 `carContentMargin: Float` 改两个。

**链路同步**(8 文件 11 处):`SettingsStore.kt`(state/intent/collect/reduce)、`SharedGraph.kt`、`LynMusicAppComponent.kt`、`AndroidPlatform.kt`、`Repositories.kt`、`AutomotiveApp.kt`、`SettingsUi.kt`、`SettingsStoreTest.kt`。

**UI**:`CarDisplayScalingSettingsCard` 里 `car_display_margin` 一行拆成两行 `car_display_margin_top` / `car_display_margin_bottom`,仍受全屏开关置灰。

### 项 4:歌词页改「歌词与封面」

**UI 删除**:`LyricsSettingsPane` 里的 LrcAPI 卡、Musicmatch 卡、自定义源卡(含其输入框与保存/清除按钮)整段删除。保留 `LyricsShareFontImportCard`(歌词分享字体,与 API 无关)。

**新增两张开关卡**
- 歌词:开关绑 `lrclib` 源的 enabled(`Repositories.LRCLIB_SOURCE_ID`),复用 `SettingsIntent.ToggleSourceEnabled`
- 封面:开关绑内置 workflow 源(oiapi 预设)的 enabled

**State 增补**:`SettingsState` 增加 `lyricsSourceEnabled: Boolean` / `coverSourceEnabled: Boolean`(从 `repository.lyricsSources` 里筛出上述两个 id 的 enabled)。`SettingsStore` 增加 `data class SetLyricsSourceEnabled(val enabled: Boolean)` / `SetCoverSourceEnabled(val enabled: Boolean)` 两个 intent,内部转成 `setLyricsSourceEnabled(id, enabled)`。

**保留**:repository 层 LrcAPI / Musicmatch / 自定义源能力全部保留(不改数据、不删表),只是 UI 不再暴露入口。

### 项 5:空间管理精简

- `shared/core/.../model/AppStorage.kt:5-11` `AppStorageCategory` 删除 `LyricsShareTemp`、`TagEditTemp`
- 同步 6 处:`SettingsUi.kt:2314-2322` categoryOrder、`:2324-2326` 过滤、`:2775-2787` 文案映射;`SettingsStore.kt:1670-1671` 文案;`AndroidAppStorageGateway.kt:40-44`、`:83-90`;`SettingsStoreTest.kt:2831-2832`
- 对应文案键删除:三语

### 项 6 / 项 7:「我的」页改造与导航收敛

**我的页内容(改后)**
```
[电源按钮(右上,保留)]
[消息 Banner]
[专辑]   → 跳曲库 Albums tab
[歌手]   → 跳曲库 Artists tab
[歌单]   → 打开歌单页
[喜欢]   → 打开喜欢页
[最近播放(单曲)]
```

**删除**:`DailyRecommendationCard`(:211)、`DailyRecommendationDetail`(:546)、`RecentAlbumsSection`(:872)、`RecentAlbumsDetail`(:668)、`buildDailyRecommendationPlayIntent`(:1278)、`MyDetailPage.DailyRecommendation` / `.RecentAlbums` 两个枚举项。
**保留**:`DailyRecommendationArtwork`(被 `RecentTracksSection` 复用)、`RecentTracksSection` / `RecentTracksDetail`。

**跳转实现**
- `MyTab` 增加回调 `onOpenLibraryRoot: (LibraryBrowserRootView) -> Unit` 与 `onOpenTab: (AppTab) -> Unit`
- 「专辑」/「歌手」→ `onOpenLibraryRoot(Albums)` / `(Artists)`,由 `AutomotiveShell` 设 `rootState.value` + `rootClickRequestKey += 1` 并切换 `selectedTab = AppTab.Library`
- 「歌单」/「喜欢」→ `onOpenTab(AppTab.Playlists)` / `(AppTab.Favorites)`

**导航收敛**(用户 m03233 最终拍板)
- 主导航只留三项:`automotiveLandscapeNavigationTabs = listOf(AppTab.My, AppTab.Library, AppTab.Settings)`
- `automotivePortraitNavigationTabs = listOf(AppTab.My, AppTab.Library, AppTab.Settings)`,`mobilePrimaryNavigationTabs` 与之一致
- 删除 `automotiveMoreNavigationTabs`、`mobileLibraryHubTabs`、`libraryHubTabs(...)`;`automotiveCollectionTabs = listOf(AppTab.Favorites, AppTab.Playlists)` 保留,作为「我的」页内的集合入口
- `automotiveNavigationGroup` 折叠映射:收藏/歌单 → 我的,来源 → 设置,标签编辑 → 曲库
- `resolveAutomotiveTab`:`Tags → Library`、`Sources → Settings`(旧状态恢复时不会落到空白页)
- `LiquidGlassDock` 由 4 项硬编码改造为 `icons`/`labels`/`badgeIndex` 参数化,支持可变项数
- 删除 `AutomotiveMoreSheet` 与底部「更多」入口
- 来源页(SourcesTab)整块嵌入「设置 → 曲库管理」,`SettingsTab` 新增 `importState`/`onImportIntent` 两个参数

### 项 8:关于本机删除

`SettingsSection.AboutDevice` 移除后,`AboutDeviceSettingsPane`、`settingsSectionTitle/Subtitle/Icon` 的 `AboutDevice` 分支、`SettingsDeviceInfoFormattingTest.kt` 相关断言一并处理(若测试只测格式化函数则保留)。

## 四、验证计划

1. `:shared:core:compileKotlinJvm`、`:android:runtime:compileDebugKotlin`、`:ui:automotive:compileKotlinJvm`
2. `python tools/check_ui_localization.py` —— 三语 key 数一致
3. `:shared:features:jvmTest`(SettingsStoreTest 96 用例需同步修改)
4. `:ui:automotive:jvmTest`(导航/Shell 相关测试)
5. `:android:runtime:lintDebug`、`:automotiveApp:assembleDebug`
6. 模拟器 car720p 实机截图复核布局

## 五、风险与取舍

| 风险 | 说明 | 应对 |
|---|---|---|
| 导航项收敛影响现有测试 | `AutomotiveNavigationTest`、`AutomotiveNavigationRailTest`、`AutomotiveShellCompositionTest` 等断言导航项数量与内容 | 同步更新断言,不做兼容分支 |
| 边距语义变化 | 现有用户若已设置 carContentMargin>0,迁移后横向不再缩放,视觉会变宽 | 迁移时 top=bottom=旧值,横向变化在 CHANGELOG 说明 |
| 歌词页删卡后旧配置残留 | 用户已配置的 LrcAPI 源仍在库里且默认 enabled | 保留数据;若 lrclib 被用户关掉,歌词开关会显示关闭,符合预期 |
| AboutDevice 删除 | 设备信息无处可看 | 保留格式化函数与测试,信息不再在 UI 暴露(用户明确要求) |
