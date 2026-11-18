package top.iwesley.lyn.music.platform

import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackException
import top.iwesley.lyn.music.core.model.diagnosticMessage
import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull
import top.iwesley.lyn.music.core.model.uiText
import java.io.FileNotFoundException

/** Keeps diagnostics separate from descriptions when Media3 wraps a data source failure. */
internal fun PlaybackGatewayState.withAndroidPlaybackFailure(
    error: Throwable,
    context: Context? = null,
    mediaUri: Uri? = null,
): PlaybackGatewayState {
    val visited = mutableListOf<Throwable>()
    val details = mutableListOf<String>()
    var current: Throwable? = error
    while (current != null && visited.none { it === current }) {
        val name = current::class.simpleName ?: current::class.qualifiedName ?: "Throwable"
        val message = current.message?.takeIf { it.isNotBlank() }
        details += if (message == null) name else "$name: $message"
        visited += current
        current = current.cause
    }
    val diagnostic = details.distinct().joinToString(" -> ")
    val fallback = uiText(Res.string.playback_media_failed)
    val usbUnavailable = mediaUri?.let { uri -> androidUsbUnavailableFailureText(context, uri, error) }
    return copy(
        canSeek = false,
        errorMessage = diagnostic.ifBlank { (fallback).diagnosticMessage() },
        errorText = usbUnavailable
            ?: error.uiFailureTextOrNull()
            ?: diagnostic.takeIf { it.isNotBlank() }?.let { uiText(Res.string.ui_error_with_details, UiText.Raw(it)) }
            ?: fallback,
        errorRevision = errorRevision + 1L,
    )
}

/**
 * U 盘拔出后直连文件读取失败时，把通用的“访问失败”换成 USB 专属文案：
 * 只有当媒体确实指向可移动存储、失败属于“找不到文件/无法访问”，且对应卷当前已不可读时才触发。
 */
internal fun androidUsbUnavailableFailureText(
    context: Context?,
    mediaUri: Uri,
    error: Throwable,
): UiText? {
    if (context == null || !isAndroidFileUnavailableFailure(error)) return null
    when (mediaUri.scheme?.lowercase()) {
        "file" -> {
            val path = mediaUri.path ?: return null
            if (!isPlausibleAndroidUsbMediaPath(path)) return null
            // The volume root decides: a deleted single file on a healthy volume keeps the generic message.
            val volumeRoot = findAndroidUsbVolumeRootPath(path) ?: return null
            return if (!volumeRoot.isDirectory || !volumeRoot.canRead()) {
                uiText(Res.string.playback_usb_unavailable)
            } else {
                null
            }
        }

        "content" -> {
            // MediaStore-backed locators come from the removable-volume fallback; when no USB volume is
            // readable anymore the content rows point at files that are gone with the stick.
            val authority = mediaUri.authority?.lowercase().orEmpty()
            if (authority.startsWith("media") && !hasReadableAndroidUsbStorageRoot(context)) {
                return uiText(Res.string.playback_usb_unavailable)
            }
            return null
        }

        else -> return null
    }
}

private fun isAndroidFileUnavailableFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 16) {
        if (current is FileNotFoundException) return true
        if (current is PlaybackException && current.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
            return true
        }
        current = current.cause
        depth += 1
    }
    return false
}
