package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A removable-storage volume appeared or disappeared; [volumeKey] is the platform's stable volume identity. */
sealed interface UsbVolumeEvent {
    data class Mounted(val volumeKey: String) : UsbVolumeEvent
    data class Unmounted(val volumeKey: String) : UsbVolumeEvent
}

/**
 * Platform bridge that reports USB/removable volume mount changes. Desktop and iOS stay silent
 * ([NoOp]); Android feeds it from mount broadcasts plus periodic mounted-volume polling.
 */
interface UsbVolumeEvents {
    val events: Flow<UsbVolumeEvent>

    companion object {
        val NoOp: UsbVolumeEvents = object : UsbVolumeEvents {
            override val events: Flow<UsbVolumeEvent> = emptyFlow()
        }
    }
}
