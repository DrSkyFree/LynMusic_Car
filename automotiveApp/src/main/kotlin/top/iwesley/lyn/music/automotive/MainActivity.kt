package top.iwesley.lyn.music.automotive

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info
import kotlin.math.min
import top.iwesley.lyn.music.automotive.ui.AutomotiveApp
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.StartupAutoOpenGate
import top.iwesley.lyn.music.automotive.ui.StartupDatabaseErrorScreen
import top.iwesley.lyn.music.buildPlayerAppComponent
import top.iwesley.lyn.music.core.model.CarDisplayMode
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.platform.AndroidExternalAudioOpenSupport
import top.iwesley.lyn.music.platform.LynPlaybackMediaLibraryService
import top.iwesley.lyn.music.platform.applyCarImmersiveMode
import top.iwesley.lyn.music.platform.createAndroidRuntimeGraph
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val startupAutoOpenViewModel by viewModels<StartupAutoOpenViewModel>()
    private val externalAudioOpenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appComponent: LynMusicAppComponent? = null
    private var pendingExternalAudioOpenIntent: Intent? = null
    private var externalAudioOpenJob: Job? = null
    private var externalAudioOpenRequestId = 0L
    private var currentImmersiveMode = false
    private val carDisplayObservationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val completeExitScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var completeExitTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        //enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appComponentResult = runCatching {
            val runtimeGraph = createAndroidRuntimeGraph(this, platformName = ANDROID_AUTOMOTIVE_PLATFORM_NAME)
            try {
                buildPlayerAppComponent(
                    sharedGraph = runtimeGraph.sharedGraph,
                    playerRuntimeServices = runtimeGraph.playerRuntimeServices,
                )
            } catch (error: Throwable) {
                runtimeGraph.disposeAfterComponentBuildFailure()
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
                throw error
            }
        }
        appComponent = appComponentResult.getOrNull()
        applyCarDisplayOrientation(appComponentResult.getOrNull()?.carDisplayMode?.value)
        applyCarImmersiveMode(
            window,
            appComponentResult.getOrNull()?.carImmersiveMode?.value == true,
        )
        observeCarDisplayModeRestart(appComponentResult.getOrNull())
        observeCarImmersiveMode(appComponentResult.getOrNull())
        handleExternalAudioOpenIntent(intent)

        setContent {
            ProvideUiLanguage {
                val appComponent = appComponentResult.getOrNull()
                if (appComponent != null) {
                    val carUiScale by appComponent.carUiScale.collectAsState()
                    val carFontScale by appComponent.carFontScale.collectAsState()
                    ProvideCarDisplayDensity(carUiScale = carUiScale, carFontScale = carFontScale) {
                        AutomotiveApp(
                            component = appComponent,
                            startupAutoOpenGate = startupAutoOpenViewModel.gate,
                            onCompleteExitRequest = { performCompleteExit(appComponent) },
                        )
                    }
                } else {
                    StartupDatabaseErrorScreen(
                        error = appComponentResult.exceptionOrNull(),
                        showDetails = false,
                    )
                }


            }
}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalAudioOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        appComponent?.settingsStore?.dispatch(SettingsIntent.RecheckDesktopLyricsPermission)
        // 个别车机会在恢复前台时重置窗口 flag,重新隐藏系统栏
        applyCarImmersiveMode(window, currentImmersiveMode)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyCarImmersiveMode(window, currentImmersiveMode)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        externalAudioOpenScope.cancel()
        carDisplayObservationScope.cancel()
        completeExitScope.cancel()
    }

    /**
     * 完全退出序列(顺序严格):
     * a. 暂停播放并显式落盘播放光标(prepareCompleteExit),下次启动 restorePlayback 从此续播;
     * b. 断开 MediaController 客户端连接并停掉前台播放服务,清除媒体通知;
     * c. finishAffinity() 结束任务栈 + Runtime.exit(0) 终止进程,不依赖系统回收。
     */
    private fun performCompleteExit(component: LynMusicAppComponent) {
        if (completeExitTriggered) return
        completeExitTriggered = true
        val logger = component.logger
        logger.info(COMPLETE_EXIT_LOG_TAG) { "complete-exit-requested" }
        completeExitScope.launch {
            // a. 暂停播放并显式保存光标;播放服务未就绪时超时兜底,不得卡死退出。
            runCatching {
                withTimeoutOrNull(COMPLETE_EXIT_PLAYBACK_PERSIST_TIMEOUT_MS) {
                    component.playbackRepository.prepareCompleteExit()
                }
            }.onFailure { error ->
                logger.error(COMPLETE_EXIT_LOG_TAG, error) { "complete-exit-persist-cursor-failed" }
            }
            // b. 断开 MediaController、停掉前台播放服务并清除媒体通知。
            runCatching { component.playbackRepository.close() }
                .onFailure { error ->
                    logger.error(COMPLETE_EXIT_LOG_TAG, error) { "complete-exit-release-controller-failed" }
                }
            runCatching {
                stopService(Intent(this@MainActivity, LynPlaybackMediaLibraryService::class.java))
            }.onFailure { error ->
                logger.error(COMPLETE_EXIT_LOG_TAG, error) { "complete-exit-stop-service-failed" }
            }
            // c. 结束任务栈并终止进程。
            finishAffinity()
            logger.info(COMPLETE_EXIT_LOG_TAG) { "complete-exit-now" }
            Runtime.getRuntime().exit(0)
        }
    }

    private fun applyCarDisplayOrientation(mode: CarDisplayMode?) {
        requestedOrientation = when (mode) {
            CarDisplayMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            CarDisplayMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    // 「使用设备」切换后自动重启套用:生效模式是进程启动时的快照,运行期收到不同值即代表用户改了设置。
    private fun observeCarDisplayModeRestart(component: LynMusicAppComponent?) {
        if (component == null) return
        carDisplayObservationScope.launch {
            var effectiveModeSeen = false
            component.carDisplayMode.collect { mode ->
                if (!effectiveModeSeen) {
                    effectiveModeSeen = true
                    return@collect
                }
                triggerCarDisplayRestart()
            }
        }
    }

    // 全屏模式实时生效 + 重新 attach 时重套(个别车机在窗口重排后会复位 flag)
    private fun observeCarImmersiveMode(component: LynMusicAppComponent?) {
        if (component == null) return
        currentImmersiveMode = component.carImmersiveMode.value
        carDisplayObservationScope.launch {
            component.carImmersiveMode.collect { enabled ->
                currentImmersiveMode = enabled
                applyCarImmersiveMode(window, enabled)
            }
        }
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                applyCarImmersiveMode(window, currentImmersiveMode)
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    private fun triggerCarDisplayRestart() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(
            Intent(this, CarRestartActivity::class.java).apply {
                putExtra(CarRestartActivity.EXTRA_OLD_PID, Process.myPid())
                putExtra(CarRestartActivity.EXTRA_MAIN_INTENT, mainIntent)
            },
        )
    }

    private fun handleExternalAudioOpenIntent(intent: Intent?) {
        if (!AndroidExternalAudioOpenSupport.isExternalAudioOpenIntent(intent)) return
        val component = appComponent
        if (component == null) {
            pendingExternalAudioOpenIntent = intent?.let(::Intent)
            return
        }
        val intentSnapshot = intent?.let(::Intent) ?: pendingExternalAudioOpenIntent ?: return
        pendingExternalAudioOpenIntent = null
        val requestId = ++externalAudioOpenRequestId
        externalAudioOpenJob?.cancel()
        externalAudioOpenJob = externalAudioOpenScope.launch {
            val tracks = AndroidExternalAudioOpenSupport.tracksFromIntent(
                context = this@MainActivity,
                intent = intentSnapshot,
                logger = component.logger,
            )
            if (requestId != externalAudioOpenRequestId) return@launch
            if (tracks.isEmpty()) {
                Toast.makeText(this@MainActivity, top.iwesley.lyn.music.platform.resolveNativeUiString(Res.string.playback_audio_open_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            component.playerStore.dispatch(PlayerIntent.PlayTransientTracks(tracks, 0))
        }
    }
}

internal class StartupAutoOpenViewModel : ViewModel() {
    val gate = StartupAutoOpenGate()
}

@Composable
private fun ProvideCarDisplayDensity(
    carUiScale: Float,
    carFontScale: Float,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density, currentDensity.fontScale, carUiScale, carFontScale) {
        Density(
            density = currentDensity.density * carUiScale,
            fontScale = currentDensity.fontScale * carFontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}


private fun ComponentActivity.isTabletIgnoringDisplaySize(): Boolean {
    val (widthPx, heightPx) = currentDisplayPx()
    val stableDensity = androidStableDensityScale(resources.displayMetrics.density)
    if (widthPx == null || heightPx == null || stableDensity <= 0f) return false
    val smallestWidthDp = min(widthPx, heightPx) / stableDensity
    return smallestWidthDp >= 600f
}

private fun ComponentActivity.currentDisplayPx(): Pair<Int?, Int?> {
    val displayMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.mode
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.mode
    }
    val width = displayMode?.physicalWidth?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.takeIf { it > 0 }
    val height = displayMode?.physicalHeight?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.takeIf { it > 0 }
    return width to height
}

private fun androidStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}

private const val COMPLETE_EXIT_LOG_TAG = "CompleteExit"
private const val COMPLETE_EXIT_PLAYBACK_PERSIST_TIMEOUT_MS = 2_000L
