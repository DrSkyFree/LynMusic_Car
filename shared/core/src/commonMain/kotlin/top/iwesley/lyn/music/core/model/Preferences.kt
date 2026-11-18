package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppDisplayScalePreset(
    val scale: Float,
) {
    ExtraCompact(0.8f),
    Compact(0.9f),
    Default(1.0f),
    Large(1.1f),
    ExtraLarge(1.2f),
}

enum class NavidromeAudioQuality(
    val maxBitRateKbps: Int?,
) {
    Original(null),
    Kbps320(320),
    Kbps192(192),
    Kbps128(128),
}

enum class PlayerArtworkStyle {
    VINYL,
    HALF_RECORD,
    MINIMAL_COVER,
}

val DEFAULT_NAVIDROME_WIFI_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Original
val DEFAULT_NAVIDROME_MOBILE_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Kbps192
const val DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED: Boolean = false
const val DEFAULT_MINIMIZE_WINDOW_ON_CLOSE: Boolean = true
val DEFAULT_PLAYER_ARTWORK_STYLE: PlayerArtworkStyle = PlayerArtworkStyle.VINYL

/** Incremental diff application is the default scan mode; the full rebuild stays an explicit opt-out. */
const val DEFAULT_LIBRARY_SCAN_INCREMENTAL: Boolean = true

/** USB library sources are rescanned once, one minute after startup, unless this is turned off. */
const val DEFAULT_LIBRARY_SCAN_AUTO_ON_STARTUP: Boolean = true

fun appDisplayScalePresetOrDefault(name: String?): AppDisplayScalePreset {
    return AppDisplayScalePreset.entries.firstOrNull { it.name == name } ?: AppDisplayScalePreset.Default
}

fun navidromeAudioQualityOrDefault(
    name: String?,
    default: NavidromeAudioQuality,
): NavidromeAudioQuality {
    return NavidromeAudioQuality.entries.firstOrNull { it.name == name } ?: default
}

fun playerArtworkStyleOrDefault(name: String?): PlayerArtworkStyle {
    return PlayerArtworkStyle.entries.firstOrNull { it.name == name } ?: DEFAULT_PLAYER_ARTWORK_STYLE
}

fun effectiveAppDisplayDensity(
    baseDensity: Float,
    preset: AppDisplayScalePreset,
): Float {
    if (!baseDensity.isFinite() || baseDensity <= 0f) return baseDensity
    return baseDensity * preset.scale
}

interface SambaCachePreferencesStore {
    val useSambaCache: StateFlow<Boolean>

    suspend fun setUseSambaCache(enabled: Boolean)
}

interface ThemePreferencesStore {
    val selectedTheme: StateFlow<AppThemeId>
    val customThemeTokens: StateFlow<AppThemeTokens>
    val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences>

    suspend fun setSelectedTheme(themeId: AppThemeId)
    suspend fun setCustomThemeTokens(tokens: AppThemeTokens)
    suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette)
}

interface CompactPlayerLyricsPreferencesStore {
    val showCompactPlayerLyrics: StateFlow<Boolean>

    suspend fun setShowCompactPlayerLyrics(enabled: Boolean)
}

interface DesktopLyricsPreferencesStore {
    val showDesktopLyrics: StateFlow<Boolean>

    suspend fun setShowDesktopLyrics(enabled: Boolean)
}

interface DesktopLyricsPositionPreferencesStore {
    val desktopLyricsPosition: StateFlow<DesktopLyricsPosition?>

    suspend fun setDesktopLyricsPosition(position: DesktopLyricsPosition)
}

interface MenuBarLyricsControlsPreferencesStore {
    val showMenuBarLyricsControls: StateFlow<Boolean>

    suspend fun setShowMenuBarLyricsControls(enabled: Boolean)
}

interface AutoPlayOnStartupPreferencesStore {
    val autoPlayOnStartup: StateFlow<Boolean>

    suspend fun setAutoPlayOnStartup(enabled: Boolean)
}

interface AutoOpenPlayerOnStartupPreferencesStore {
    val autoOpenPlayerOnStartup: StateFlow<Boolean>

    suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean)
}

interface WindowClosePreferencesStore {
    val minimizeWindowOnClose: StateFlow<Boolean>

    suspend fun setMinimizeWindowOnClose(enabled: Boolean)
}

interface AppDisplayPreferencesStore {
    val appDisplayScalePreset: StateFlow<AppDisplayScalePreset>

    suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset)
}

enum class CarDisplayMode {
    FOLLOW_WINDOW,
    PORTRAIT,
    LANDSCAPE,
}

val DEFAULT_CAR_DISPLAY_MODE: CarDisplayMode = CarDisplayMode.FOLLOW_WINDOW
const val DEFAULT_CAR_UI_SCALE = 1.0f
const val DEFAULT_CAR_FONT_SCALE = 1.0f
const val DEFAULT_CAR_COVER_SCALE = 1.0f
const val DEFAULT_CAR_CONTENT_MARGIN = 0.0f
const val DEFAULT_CAR_IMMERSIVE_MODE = false

const val CAR_UI_SCALE_MIN = 0.7f
const val CAR_UI_SCALE_MAX = 1.4f
const val CAR_FONT_SCALE_MIN = 0.8f
const val CAR_FONT_SCALE_MAX = 2.0f
const val CAR_COVER_SCALE_MIN = 0.6f
const val CAR_COVER_SCALE_MAX = 1.3f
const val CAR_CONTENT_MARGIN_MIN = 0.0f
const val CAR_CONTENT_MARGIN_MAX = 0.3f

fun carDisplayModeOrDefault(name: String?): CarDisplayMode {
    return CarDisplayMode.entries.firstOrNull { it.name == name } ?: DEFAULT_CAR_DISPLAY_MODE
}

fun normalizedCarDisplayValue(value: Float, min: Float, max: Float, fallback: Float): Float {
    if (!value.isFinite()) return fallback
    return value.coerceIn(min, max)
}

interface CarDisplayPreferencesStore {
    val carDisplayMode: StateFlow<CarDisplayMode>
    val carUiScale: StateFlow<Float>
    val carFontScale: StateFlow<Float>
    val carCoverScale: StateFlow<Float>
    val carContentMargin: StateFlow<Float>
    val carImmersiveMode: StateFlow<Boolean>

    suspend fun setCarDisplayMode(mode: CarDisplayMode)
    suspend fun setCarUiScale(scale: Float)
    suspend fun setCarFontScale(scale: Float)
    suspend fun setCarCoverScale(scale: Float)
    suspend fun setCarContentMargin(margin: Float)
    suspend fun setCarImmersiveMode(enabled: Boolean)
}

interface NavidromeAudioQualityPreferencesStore {
    val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality>
    val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality>

    suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality)
    suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality)
}

interface LibraryScanPreferencesStore {
    val scanIncremental: StateFlow<Boolean>
    val autoScanOnStartup: StateFlow<Boolean>

    suspend fun setScanIncremental(enabled: Boolean)
    suspend fun setAutoScanOnStartup(enabled: Boolean)
}

interface PlaybackDecoderPreferencesStore {
    val useAndroidExtensionDecoder: StateFlow<Boolean>

    suspend fun setUseAndroidExtensionDecoder(enabled: Boolean)
}

interface PlayerArtworkStylePreferencesStore {
    val playerArtworkStyle: StateFlow<PlayerArtworkStyle>

    suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle)
}

interface LyricsShareFontPreferencesStore {
    val selectedLyricsShareFontKey: StateFlow<String?>

    suspend fun setSelectedLyricsShareFontKey(value: String?)
}

object UnsupportedCompactPlayerLyricsPreferencesStore : CompactPlayerLyricsPreferencesStore {
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(false)

    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        mutableShowCompactPlayerLyrics.value = enabled
    }
}

object UnsupportedDesktopLyricsPreferencesStore : DesktopLyricsPreferencesStore {
    private val mutableShowDesktopLyrics = MutableStateFlow(false)

    override val showDesktopLyrics: StateFlow<Boolean> = mutableShowDesktopLyrics

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        mutableShowDesktopLyrics.value = enabled
    }
}

object UnsupportedMenuBarLyricsControlsPreferencesStore : MenuBarLyricsControlsPreferencesStore {
    private val mutableShowMenuBarLyricsControls = MutableStateFlow(false)

    override val showMenuBarLyricsControls: StateFlow<Boolean> = mutableShowMenuBarLyricsControls

    override suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        mutableShowMenuBarLyricsControls.value = enabled
    }
}

object UnsupportedAutoPlayOnStartupPreferencesStore : AutoPlayOnStartupPreferencesStore {
    private val mutableAutoPlayOnStartup = MutableStateFlow(false)

    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableAutoPlayOnStartup.value = enabled
    }
}

object UnsupportedAutoOpenPlayerOnStartupPreferencesStore : AutoOpenPlayerOnStartupPreferencesStore {
    private val mutableAutoOpenPlayerOnStartup = MutableStateFlow(false)

    override val autoOpenPlayerOnStartup: StateFlow<Boolean> = mutableAutoOpenPlayerOnStartup

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        mutableAutoOpenPlayerOnStartup.value = enabled
    }
}

object UnsupportedWindowClosePreferencesStore : WindowClosePreferencesStore {
    private val mutableMinimizeWindowOnClose = MutableStateFlow(DEFAULT_MINIMIZE_WINDOW_ON_CLOSE)

    override val minimizeWindowOnClose: StateFlow<Boolean> = mutableMinimizeWindowOnClose

    override suspend fun setMinimizeWindowOnClose(enabled: Boolean) {
        mutableMinimizeWindowOnClose.value = enabled
    }
}

object UnsupportedAppDisplayPreferencesStore : AppDisplayPreferencesStore {
    private val mutableAppDisplayScalePreset = MutableStateFlow(AppDisplayScalePreset.Default)

    override val appDisplayScalePreset: StateFlow<AppDisplayScalePreset> = mutableAppDisplayScalePreset

    override suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset) {
        mutableAppDisplayScalePreset.value = preset
    }
}

object UnsupportedNavidromeAudioQualityPreferencesStore : NavidromeAudioQualityPreferencesStore {
    private val mutableWifiAudioQuality = MutableStateFlow(DEFAULT_NAVIDROME_WIFI_AUDIO_QUALITY)
    private val mutableMobileAudioQuality = MutableStateFlow(DEFAULT_NAVIDROME_MOBILE_AUDIO_QUALITY)

    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> = mutableWifiAudioQuality
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> = mutableMobileAudioQuality

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        mutableWifiAudioQuality.value = quality
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        mutableMobileAudioQuality.value = quality
    }
}

object UnsupportedLibraryScanPreferencesStore : LibraryScanPreferencesStore {
    private val mutableScanIncremental = MutableStateFlow(DEFAULT_LIBRARY_SCAN_INCREMENTAL)
    private val mutableAutoScanOnStartup = MutableStateFlow(DEFAULT_LIBRARY_SCAN_AUTO_ON_STARTUP)

    override val scanIncremental: StateFlow<Boolean> = mutableScanIncremental
    override val autoScanOnStartup: StateFlow<Boolean> = mutableAutoScanOnStartup

    override suspend fun setScanIncremental(enabled: Boolean) {
        mutableScanIncremental.value = enabled
    }

    override suspend fun setAutoScanOnStartup(enabled: Boolean) {
        mutableAutoScanOnStartup.value = enabled
    }
}

object UnsupportedPlaybackDecoderPreferencesStore : PlaybackDecoderPreferencesStore {
    private val mutableUseAndroidExtensionDecoder =
        MutableStateFlow(DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED)

    override val useAndroidExtensionDecoder: StateFlow<Boolean> = mutableUseAndroidExtensionDecoder

    override suspend fun setUseAndroidExtensionDecoder(enabled: Boolean) {
        mutableUseAndroidExtensionDecoder.value = enabled
    }
}

object UnsupportedPlayerArtworkStylePreferencesStore : PlayerArtworkStylePreferencesStore {
    private val mutablePlayerArtworkStyle = MutableStateFlow(DEFAULT_PLAYER_ARTWORK_STYLE)

    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = mutablePlayerArtworkStyle

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        mutablePlayerArtworkStyle.value = style
    }
}

object UnsupportedLyricsShareFontPreferencesStore : LyricsShareFontPreferencesStore {
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow<String?>(null)

    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey

    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        mutableSelectedLyricsShareFontKey.value = value?.trim()?.takeIf { it.isNotBlank() }
    }
}

interface DesktopVlcPreferencesStore {
    val desktopVlcManualPath: StateFlow<String?>
    val desktopVlcAutoDetectedPath: StateFlow<String?>
    val desktopVlcEffectivePath: StateFlow<String?>

    suspend fun setDesktopVlcManualPath(path: String?)
    suspend fun setDesktopVlcAutoDetectedPath(path: String?)
}

object UnsupportedDesktopVlcPreferencesStore : DesktopVlcPreferencesStore {
    private val mutableManualPath = MutableStateFlow<String?>(null)
    private val mutableAutoDetectedPath = MutableStateFlow<String?>(null)
    private val mutableEffectivePath = MutableStateFlow<String?>(null)

    override val desktopVlcManualPath: StateFlow<String?> = mutableManualPath
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = mutableAutoDetectedPath
    override val desktopVlcEffectivePath: StateFlow<String?> = mutableEffectivePath

    override suspend fun setDesktopVlcManualPath(path: String?) {
        mutableManualPath.value = path?.takeIf { it.isNotBlank() }
        mutableEffectivePath.value = mutableManualPath.value ?: mutableAutoDetectedPath.value
    }

    override suspend fun setDesktopVlcAutoDetectedPath(path: String?) {
        mutableAutoDetectedPath.value = path?.takeIf { it.isNotBlank() }
        mutableEffectivePath.value = mutableManualPath.value ?: mutableAutoDetectedPath.value
    }
}

object UnsupportedCarDisplayPreferencesStore : CarDisplayPreferencesStore {
    private val mutableCarDisplayMode = MutableStateFlow(DEFAULT_CAR_DISPLAY_MODE)
    private val mutableCarUiScale = MutableStateFlow(DEFAULT_CAR_UI_SCALE)
    private val mutableCarFontScale = MutableStateFlow(DEFAULT_CAR_FONT_SCALE)
    private val mutableCarCoverScale = MutableStateFlow(DEFAULT_CAR_COVER_SCALE)
    private val mutableCarContentMargin = MutableStateFlow(DEFAULT_CAR_CONTENT_MARGIN)
    private val mutableCarImmersiveMode = MutableStateFlow(DEFAULT_CAR_IMMERSIVE_MODE)

    override val carDisplayMode: StateFlow<CarDisplayMode> = mutableCarDisplayMode
    override val carUiScale: StateFlow<Float> = mutableCarUiScale
    override val carFontScale: StateFlow<Float> = mutableCarFontScale
    override val carCoverScale: StateFlow<Float> = mutableCarCoverScale
    override val carContentMargin: StateFlow<Float> = mutableCarContentMargin
    override val carImmersiveMode: StateFlow<Boolean> = mutableCarImmersiveMode

    override suspend fun setCarDisplayMode(mode: CarDisplayMode) {
        mutableCarDisplayMode.value = mode
    }

    override suspend fun setCarUiScale(scale: Float) {
        mutableCarUiScale.value = normalizedCarDisplayValue(scale, CAR_UI_SCALE_MIN, CAR_UI_SCALE_MAX, DEFAULT_CAR_UI_SCALE)
    }

    override suspend fun setCarFontScale(scale: Float) {
        mutableCarFontScale.value = normalizedCarDisplayValue(scale, CAR_FONT_SCALE_MIN, CAR_FONT_SCALE_MAX, DEFAULT_CAR_FONT_SCALE)
    }

    override suspend fun setCarCoverScale(scale: Float) {
        mutableCarCoverScale.value = normalizedCarDisplayValue(scale, CAR_COVER_SCALE_MIN, CAR_COVER_SCALE_MAX, DEFAULT_CAR_COVER_SCALE)
    }

    override suspend fun setCarContentMargin(margin: Float) {
        mutableCarContentMargin.value = normalizedCarDisplayValue(margin, CAR_CONTENT_MARGIN_MIN, CAR_CONTENT_MARGIN_MAX, DEFAULT_CAR_CONTENT_MARGIN)
    }

    override suspend fun setCarImmersiveMode(enabled: Boolean) {
        mutableCarImmersiveMode.value = enabled
    }
}
