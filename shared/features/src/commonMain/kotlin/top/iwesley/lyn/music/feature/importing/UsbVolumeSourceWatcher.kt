package top.iwesley.lyn.music.feature.importing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.UsbVolumeEvent
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.parseUsbLocalFolderReference
import top.iwesley.lyn.music.core.model.resolveUiTextForStorage
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.resources.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Bridges platform USB volume events onto the import sources: a remounted stick whose volume identity is
 * stored in an import source's root reference triggers that source's rescan; a removal marks the source's
 * stored tracks as temporarily unavailable (they stay in the library, matching the unreadable-folder
 * semantics of a rescan). Events of volumes without matching sources are ignored.
 */
class UsbVolumeSourceWatcher(
    private val repository: ImportSourceRepository,
    private val importStore: ImportStore,
    events: Flow<UsbVolumeEvent>,
    scope: CoroutineScope,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event ->
                runCatching { handleEvent(event) }
                    .onFailure { throwable ->
                        logger.warn(USB_VOLUME_LOG_TAG) {
                            "usb-event-handling-failed event=$event reason=${throwable.message.orEmpty()}"
                        }
                    }
            }
        }
    }

    private suspend fun handleEvent(event: UsbVolumeEvent) {
        when (event) {
            is UsbVolumeEvent.Mounted -> rescanSourcesOnVolume(event.volumeKey)
            is UsbVolumeEvent.Unmounted -> markSourcesUnavailable(event.volumeKey)
        }
    }

    private suspend fun localFolderSourcesForVolume(volumeKey: String): List<top.iwesley.lyn.music.core.model.ImportSource> {
        return repository.observeSources().first()
            .map { it.source }
            .filter { source ->
                source.enabled &&
                    source.type == ImportSourceType.LOCAL_FOLDER &&
                    parseUsbLocalFolderReference(source.rootReference)?.volumeKey == volumeKey
            }
    }

    private suspend fun rescanSourcesOnVolume(volumeKey: String) {
        // MediaStore may still be indexing the freshly mounted volume; give it a moment.
        delay(RESCAN_SETTLE_DELAY)
        val sources = localFolderSourcesForVolume(volumeKey)
        if (sources.isEmpty()) {
            logger.info(USB_VOLUME_LOG_TAG) { "usb-mount-no-matching-source volumeKey=$volumeKey" }
            return
        }
        logger.info(USB_VOLUME_LOG_TAG) {
            "usb-mount-rescan-start volumeKey=$volumeKey sources=${sources.joinToString(separator = ",") { it.id }}"
        }
        sources.forEach { source ->
            if (importStore.state.value.isWorking) {
                logger.info(USB_VOLUME_LOG_TAG) {
                    "usb-mount-rescan-skipped-busy source=${source.id} volumeKey=$volumeKey"
                }
                return
            }
            importStore.dispatch(ImportIntent.RescanSource(source.id))
        }
    }

    private suspend fun markSourcesUnavailable(volumeKey: String) {
        val sources = localFolderSourcesForVolume(volumeKey)
        if (sources.isEmpty()) {
            logger.info(USB_VOLUME_LOG_TAG) { "usb-unmount-no-matching-source volumeKey=$volumeKey" }
            return
        }
        val reason = resolveUiTextForStorage(
            uiText(Res.string.import_usb_volume_unavailable),
            fallbackMessage = "USB storage unavailable.",
        )
        sources.forEach { source ->
            repository.markLocalFolderUnavailable(source.id, reason)
                .onFailure { throwable ->
                    logger.warn(USB_VOLUME_LOG_TAG) {
                        "usb-unmount-mark-failed source=${source.id} reason=${throwable.message.orEmpty()}"
                    }
                }
            logger.info(USB_VOLUME_LOG_TAG) { "usb-unmount-marked source=${source.id} volumeKey=$volumeKey" }
        }
    }

    private companion object {
        val RESCAN_SETTLE_DELAY = 2500.milliseconds
        val USB_VOLUME_LOG_TAG = "UsbVolume"
    }
}
