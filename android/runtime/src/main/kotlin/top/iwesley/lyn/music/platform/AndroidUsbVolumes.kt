package top.iwesley.lyn.music.platform

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.buildUsbLocalFolderReference
import top.iwesley.lyn.music.core.model.info
import java.io.File
import java.security.MessageDigest

/** A currently mounted removable volume and its stable identity used inside USB root references. */
internal data class AndroidUsbVolume(
    val volumeKey: String,
    val root: File,
)

/**
 * Lists readable removable volumes with stable identities. Key priority: StorageVolume.uuid (API 30+),
 * the file-system UUID surfaced in the mount path, the backing /proc/mounts device node, and finally a
 * stable hash of the root path. The same stick therefore keeps one identity across remounts.
 */
internal fun listAndroidUsbVolumes(
    context: Context,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): List<AndroidUsbVolume> {
    val volumes = linkedMapOf<String, AndroidUsbVolume>()

    fun addRoot(root: File, keySource: String, key: String, source: String) {
        val normalized = root.canonicalFileOrSelf()
        if (!normalized.exists() || !normalized.isDirectory || !normalized.canRead()) {
            logger.info(USB_VOLUME_LOG_TAG) {
                "usb-volume-skip source=$source keySource=$keySource path=${normalized.absolutePath} " +
                    "exists=${normalized.exists()} directory=${normalized.isDirectory} readable=${normalized.canRead()}"
            }
            return
        }
        val existing = volumes[key]
        if (existing == null) {
            volumes[key] = AndroidUsbVolume(volumeKey = key, root = normalized)
            logger.info(USB_VOLUME_LOG_TAG) {
                "usb-volume-add source=$source keySource=$keySource key=$key path=${normalized.absolutePath}"
            }
        } else {
            logger.info(USB_VOLUME_LOG_TAG) {
                "usb-volume-duplicate source=$source keySource=$keySource key=$key " +
                    "existingPath=${existing.root.absolutePath} path=${normalized.absolutePath}"
            }
        }
    }

    // API 30+ is the only version where StorageVolume.directory exists; the mount table supplies the same
    // roots on Android 7-9, otherwise a plugged-in stick stays invisible to the built-in picker.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(StorageManager::class.java)?.storageVolumes?.forEach { volume ->
            if (!volume.isRemovable || !isReadableStorageVolumeState(volume.state)) return@forEach
            val directory = volume.directory ?: return@forEach
            val key = androidUsbVolumeKey(volumeUuid = volume.uuid, root = directory)
            addRoot(directory, keySource = "uuid", key = key, source = "storage-volume")
        }
    }

    listMountedRemovableVolumeRoots(logger).forEach { root ->
        // No platform uuid on API < 30, so the key falls back to the file-system UUID in the mount path and
        // then to the backing device node — both survive a remount, unlike the mount point itself.
        val key = androidUsbVolumeKey(volumeUuid = null, root = root)
        addRoot(root, keySource = key.keySource(), key = key, source = "mounted-volume")
    }

    val primaryRoot = Environment.getExternalStorageDirectory().canonicalFileOrSelf()
    context.getExternalFilesDirs(null)
        .filterNotNull()
        .mapNotNull(File::findStorageVolumeRoot)
        .map(File::canonicalFileOrSelf)
        .filterNot { root ->
            root == primaryRoot || primaryRoot.absolutePath.startsWith(root.absolutePath + File.separator)
        }
        .forEach { root ->
            val key = androidUsbVolumeKey(volumeUuid = null, root = root)
            addRoot(root, keySource = key.keySource(), key = key, source = "external-files-dir")
        }

    listMountedUsbFallbackRoots(logger).forEach { root ->
        val key = androidUsbVolumeKey(volumeUuid = null, root = root)
        addRoot(root, keySource = key.keySource(), key = key, source = "mnt-usb-fallback")
    }

    return volumes.values.toList().also { listed ->
        logger.info(USB_VOLUME_LOG_TAG) {
            "usb-volume-list-complete volumes=${listed.joinToString(separator = ";") { volume ->
                "${volume.volumeKey}|${volume.root.absolutePath}"
            }}"
        }
    }
}

/** Finds the mounted volume with exactly the given stable identity. */
internal fun resolveAndroidUsbVolume(
    context: Context,
    volumeKey: String,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): AndroidUsbVolume? {
    return listAndroidUsbVolumes(context, logger).firstOrNull { it.volumeKey == volumeKey }
}

/** Finds the mounted removable volume that contains [file], for tagging new picks with a volume identity. */
internal fun findAndroidUsbVolumeContaining(
    context: Context,
    file: File,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): AndroidUsbVolume? {
    val normalized = file.canonicalFileOrSelf()
    return listAndroidUsbVolumes(context, logger).firstOrNull { volume ->
        normalized.isWithinOrSame(volume.root)
    }
}

/** Tags a picker selection with the USB volume identity when it points at a removable volume. */
internal fun LocalFolderSelection.withAndroidUsbVolumeReference(
    context: Context,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): LocalFolderSelection {
    val file = resolveAndroidLocalTrackFile(persistentReference)?.takeIf { it.isDirectory } ?: return this
    val volume = findAndroidUsbVolumeContaining(context, file, logger) ?: return this
    val volumeRelativePath = file.volumeRelativePathIn(volume.root)
    val reference = buildUsbLocalFolderReference(volume.volumeKey, volumeRelativePath)
    logger.info(USB_VOLUME_LOG_TAG) {
        "usb-selection-encoded volumeKey=${volume.volumeKey} volumeRoot=${volume.root.absolutePath} " +
            "volumeRelativePath=$volumeRelativePath label=$label"
    }
    return copy(persistentReference = reference)
}

private fun File.volumeRelativePathIn(volumeRoot: File): String {
    val rootPath = volumeRoot.canonicalFileOrSelf().absolutePath.trimEnd('/')
    val path = canonicalFileOrSelf().absolutePath.trimEnd('/')
    if (path == rootPath) return ""
    return path.removePrefix("$rootPath/").trim('/')
}

/**
 * Stable identity of a removable volume root. [volumeUuid] wins when the platform provides one; the
 * file-system UUID embedded in vold mount paths and the /proc/mounts device node follow; the root path
 * hash is the last resort.
 */
internal fun androidUsbVolumeKey(volumeUuid: String?, root: File): String {
    val uuid = volumeUuid?.trim()?.takeIf { it.isNotBlank() }
    if (uuid != null) return "uuid:$uuid"
    val mountPathName = root.name.trim()
    if (FILESYSTEM_UUID_PATTERN.matches(mountPathName)) {
        return "uuid:${mountPathName.uppercase()}"
    }
    procMountsDeviceNodeFor(root)?.let { deviceNode ->
        if (isSpecificBlockDeviceNode(deviceNode)) {
            return "dev:${deviceNode.replace(':', '_').replace(',', '_')}"
        }
    }
    return "path:${stablePathHash(root.canonicalFileOrSelf().absolutePath)}"
}

private fun String.keySource(): String = substringBefore(':')

private fun isSpecificBlockDeviceNode(deviceNode: String): Boolean {
    // Generic per-clone nodes like /dev/fuse would collide across sticks; only concrete block devices identify one.
    return deviceNode.startsWith("/dev/block/") && deviceNode != "/dev/fuse"
}

private fun procMountsDeviceNodeFor(root: File): String? {
    val target = root.canonicalFileOrSelf().absolutePath.trimEnd('/')
    val backingPath = "/mnt/media_rw/${root.name.trimEnd('/')}"
    return runCatching {
        File(PROC_MOUNTS_PATH).useLines { lines ->
            lines.asSequence()
                .map { it.split(' ') }
                .filter { fields -> fields.size >= 2 }
                .filter { fields ->
                    val mountPoint = fields[1]
                    mountPoint == target || mountPoint == backingPath
                }
                .map { it[0] }
                .firstOrNull { it.startsWith("/dev/") }
        }
    }.getOrNull()
}

private fun stablePathHash(path: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(path.encodeToByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(16)
}

/** Volume roots of the path prefixes Android uses for removable media; used to detect unplug failures. */
internal fun findAndroidUsbVolumeRootPath(path: String): File? {
    var current: File? = File(path)
    while (current != null) {
        val parentPath = current.parentFile?.absolutePath
        if (parentPath == STORAGE_ROOT_PATH || parentPath == MNT_USB_ROOT_PATH ||
            parentPath == MNT_MEDIA_RW_ROOT_PATH
        ) {
            return current
        }
        current = current.parentFile
    }
    return null
}

/** Whether a media path may belong to removable storage (internal emulated storage never does). */
internal fun isPlausibleAndroidUsbMediaPath(path: String): Boolean {
    val normalized = path.trim().trimEnd('/')
    if (normalized.startsWith("$MNT_USB_ROOT_PATH/") || normalized.startsWith("$MNT_MEDIA_RW_ROOT_PATH/")) {
        return true
    }
    if (!normalized.startsWith("$STORAGE_ROOT_PATH/")) return false
    val firstSegment = normalized.removePrefix("$STORAGE_ROOT_PATH/").substringBefore('/')
    return firstSegment.isNotEmpty() &&
        firstSegment != "emulated" &&
        firstSegment != "self" &&
        firstSegment != "sdcard"
}

private val FILESYSTEM_UUID_PATTERN = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")
internal const val USB_VOLUME_LOG_TAG = "UsbVolume"
