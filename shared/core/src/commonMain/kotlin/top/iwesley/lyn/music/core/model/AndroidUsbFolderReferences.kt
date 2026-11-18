package top.iwesley.lyn.music.core.model

import kotlin.io.encoding.Base64

/**
 * Persistent root reference for local folders imported from removable (USB) storage.
 *
 * The mount point of a USB stick changes between sessions (`/storage/XXXX-XXXX`, `/mnt/usb/sda1`, ...),
 * so the stored reference carries the stable volume identity plus the path *within* the volume instead of
 * an absolute path. Scanning resolves the currently mounted root for [volumeKey] and re-anchors
 * [volumeRelativePath] onto it, so re-plugging the same stick never creates a duplicate source.
 *
 * Encoding mirrors [parseIosLocalFolderReference]: `<prefix><base64url(volumeKey)>/<base64url(relativePath)>`.
 */
private const val USB_LOCAL_FOLDER_REFERENCE_PREFIX = "lynmusic-usb-folder://v1/"

data class UsbLocalFolderReference(
    val volumeKey: String,
    /** Directory path relative to the volume root; blank means the volume root itself. */
    val volumeRelativePath: String,
)

fun buildUsbLocalFolderReference(volumeKey: String, volumeRelativePath: String): String {
    require(volumeKey.isNotBlank()) { "USB 卷标识不能为空。" }
    val normalizedPath = volumeRelativePath.replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/")
    return buildString {
        append(USB_LOCAL_FOLDER_REFERENCE_PREFIX)
        append(Base64.UrlSafe.encode(volumeKey.encodeToByteArray()))
        append('/')
        append(Base64.UrlSafe.encode(normalizedPath.encodeToByteArray()))
    }
}

fun parseUsbLocalFolderReference(reference: String): UsbLocalFolderReference? {
    if (!reference.startsWith(USB_LOCAL_FOLDER_REFERENCE_PREFIX)) return null
    val payload = reference.removePrefix(USB_LOCAL_FOLDER_REFERENCE_PREFIX)
    val dividerIndex = payload.indexOf('/')
    if (dividerIndex <= 0 || dividerIndex == payload.lastIndex) return null
    return runCatching {
        val volumeKey = Base64.UrlSafe.decode(payload.substring(0, dividerIndex)).decodeToString()
        val volumeRelativePath = Base64.UrlSafe.decode(payload.substring(dividerIndex + 1))
            .decodeToString()
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/")
        UsbLocalFolderReference(volumeKey = volumeKey, volumeRelativePath = volumeRelativePath)
    }.getOrNull()?.takeIf { it.volumeKey.isNotBlank() }
}

/** Whether [reference] is the USB-encoded form; plain absolute paths and tree URIs are not. */
fun isUsbLocalFolderReference(reference: String): Boolean =
    parseUsbLocalFolderReference(reference) != null
