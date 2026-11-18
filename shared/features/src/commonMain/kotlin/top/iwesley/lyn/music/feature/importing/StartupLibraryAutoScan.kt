package top.iwesley.lyn.music.feature.importing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.isUsbLocalFolderReference
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Rescans the enabled USB (removable-volume) local-folder sources once, one minute after the app has
 * started, so a stick that was plugged in before launch gets fresh library data without any user
 * action. Mirrors the [UsbVolumeSourceWatcher] mount-event bridge: the same enabled LOCAL_FOLDER +
 * USB-volume-identity filter, the same [ImportIntent.RescanSource] dispatch, and the same "skip while
 * the import store is busy" guard, which also keeps the delayed pass from repeating a mount-triggered
 * rescan of the same source within the same startup round. Runs only when the "auto scan on startup"
 * preference is on (default on); no WorkManager is involved.
 */
class StartupLibraryAutoScan(
    private val repository: ImportSourceRepository,
    private val importStore: ImportStore,
    private val autoScanOnStartup: StateFlow<Boolean>,
    scope: CoroutineScope,
    private val startupDelay: Duration = AUTO_SCAN_STARTUP_DELAY,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(startupDelay)
            runCatching { rescanUsbLocalFolderSources() }
                .onFailure { throwable ->
                    logger.warn(AUTO_SCAN_LOG_TAG) {
                        "startup-auto-scan-failed reason=${throwable.message.orEmpty()}"
                    }
                }
        }
    }

    private suspend fun rescanUsbLocalFolderSources() {
        if (!autoScanOnStartup.value) {
            logger.info(AUTO_SCAN_LOG_TAG) { "startup-auto-scan-disabled" }
            return
        }
        val sources = repository.observeSources().first()
            .map { it.source }
            .filter { source ->
                source.enabled &&
                    source.type == ImportSourceType.LOCAL_FOLDER &&
                    isUsbLocalFolderReference(source.rootReference)
            }
        if (sources.isEmpty()) {
            logger.info(AUTO_SCAN_LOG_TAG) { "startup-auto-scan-no-usb-source" }
            return
        }
        logger.info(AUTO_SCAN_LOG_TAG) {
            "startup-auto-scan-start sources=${sources.joinToString(separator = ",") { it.id }}"
        }
        sources.forEach { source ->
            if (importStore.state.value.isWorking) {
                logger.info(AUTO_SCAN_LOG_TAG) {
                    "startup-auto-scan-skipped-busy source=${source.id}"
                }
                return
            }
            importStore.dispatch(ImportIntent.RescanSource(source.id))
        }
    }

    private companion object {
        val AUTO_SCAN_STARTUP_DELAY = 60.seconds
        val AUTO_SCAN_LOG_TAG = "StartupAutoScan"
    }
}
