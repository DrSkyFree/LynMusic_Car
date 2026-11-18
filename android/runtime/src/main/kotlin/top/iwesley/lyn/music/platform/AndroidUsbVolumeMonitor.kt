package top.iwesley.lyn.music.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.UsbVolumeEvent
import top.iwesley.lyn.music.core.model.UsbVolumeEvents
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.warn

/**
 * Watches removable storage volumes: mount broadcasts plus a mounted-volume diff driven by activity
 * resumes and a periodic poll (the poll also covers API 30+ where these broadcasts are restricted).
 * Detected changes are emitted as [UsbVolumeEvent]s carrying the stable volume identity.
 */
internal class AndroidUsbVolumeMonitor(
    private val context: Context,
    activityResumedEvents: Flow<Unit>,
    private val scope: CoroutineScope,
    private val logger: DiagnosticLogger = GlobalDiagnosticLogger,
) : UsbVolumeEvents {
    private val mutableEvents = MutableSharedFlow<UsbVolumeEvent>(
        replay = 4,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val events: Flow<UsbVolumeEvent> = mutableEvents.asSharedFlow()

    private val refreshMutex = Mutex()

    /** `null` until the first snapshot is taken: the baseline never emits mount/unmount events. */
    @Volatile
    private var lastKnownVolumeKeys: Set<String>? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logger.info(USB_VOLUME_LOG_TAG) {
                "usb-broadcast action=${intent?.action.orEmpty()} data=${intent?.dataString.orEmpty()}"
            }
            refresh("broadcast")
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { throwable ->
            logger.warn(USB_VOLUME_LOG_TAG) {
                "usb-receiver-register-failed reason=${throwable.message.orEmpty()}"
            }
        }
        AndroidUsbVolumeMonitorRegistry.bind(this)
        val collectorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch {
                activityResumedEvents.collect { refresh("activity-resumed") }
            }
            launch {
                while (isActive) {
                    refresh("periodic")
                    delay(USB_VOLUME_POLL_INTERVAL_MS)
                }
            }
        }
        collectorJob.invokeOnCompletion {
            AndroidUsbVolumeMonitorRegistry.unbind(this)
            runCatching { context.unregisterReceiver(receiver) }
        }
        refresh("startup")
    }

    /** Diffs the currently mounted USB volumes against the previous snapshot; safe to call from anywhere. */
    fun refresh(reason: String) {
        scope.launch(Dispatchers.Default) { refreshVolumes(reason) }
    }

    private suspend fun refreshVolumes(reason: String) {
        refreshMutex.withLock {
            val volumes = runCatching { listAndroidUsbVolumes(context, logger) }
                .onFailure { throwable ->
                    logger.warn(USB_VOLUME_LOG_TAG) {
                        "usb-volume-list-failed reason=$reason error=${throwable.message.orEmpty()}"
                    }
                }
                .getOrDefault(emptyList())
            val currentKeys = volumes.map { it.volumeKey }.toSet()
            val previousKeys = lastKnownVolumeKeys
            lastKnownVolumeKeys = currentKeys
            if (previousKeys == null) {
                logger.info(USB_VOLUME_LOG_TAG) {
                    "usb-volume-baseline reason=$reason keys=$currentKeys"
                }
                return@withLock
            }
            val mounted = currentKeys - previousKeys
            val unmounted = previousKeys - currentKeys
            if (mounted.isEmpty() && unmounted.isEmpty()) return@withLock
            logger.info(USB_VOLUME_LOG_TAG) {
                "usb-volume-change reason=$reason mounted=$mounted unmounted=$unmounted keys=$currentKeys"
            }
            mounted.forEach { key -> mutableEvents.tryEmit(UsbVolumeEvent.Mounted(key)) }
            unmounted.forEach { key -> mutableEvents.tryEmit(UsbVolumeEvent.Unmounted(key)) }
        }
    }

    private companion object {
        const val USB_VOLUME_POLL_INTERVAL_MS = 60_000L
    }
}

/** Lets the manifest-registered receiver reach the monitor of the running Android runtime graph. */
internal object AndroidUsbVolumeMonitorRegistry {
    @Volatile
    private var current: AndroidUsbVolumeMonitor? = null

    internal fun bind(monitor: AndroidUsbVolumeMonitor) {
        current = monitor
    }

    internal fun unbind(monitor: AndroidUsbVolumeMonitor) {
        if (current === monitor) current = null
    }

    internal fun notifyUsbBroadcast() {
        current?.refresh("manifest-broadcast")
    }
}

/** Manifest entry point; the monitor instance registers itself in [AndroidUsbVolumeMonitorRegistry]. */
class AndroidUsbVolumeBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        AndroidUsbVolumeMonitorRegistry.notifyUsbBroadcast()
    }
}
