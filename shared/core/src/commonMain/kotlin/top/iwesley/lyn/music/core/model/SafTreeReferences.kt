package top.iwesley.lyn.music.core.model

/**
 * Parsed form of a Storage Access Framework tree URI
 * (`content://com.android.externalstorage.documents/tree/<documentId>`).
 *
 * The stored reference stays the raw URI string so the platform can hand it back to the system, but
 * every UI and diagnostic surface needs the readable parts: which volume, which folder inside it, and
 * what to show to the user. Parsing is pure string work on purpose — it never touches Android APIs and
 * therefore works identically on Android 7, 9 and 12+, where the provider's behaviour differs.
 */
data class SafTreeReference(
    /** Storage volume id, `primary` for internal storage or a UUID/serial for removable media. */
    val volumeId: String,
    /** Folder path inside [volumeId], using `/` separators; blank means the volume root. */
    val relativePath: String,
) {
    /** True when the tree lives on removable media, i.e. a USB stick or SD card. */
    val isRemovableVolume: Boolean
        get() = volumeId.isNotBlank() &&
            !volumeId.equals(PRIMARY_VOLUME_ID, ignoreCase = true) &&
            !volumeId.equals(SD_CARD_VOLUME_ID, ignoreCase = true)

    /** Last path segment, or the volume id when the tree is a mount root. */
    val folderName: String
        get() = relativePath.substringAfterLast('/', missingDelimiterValue = "").ifBlank { volumeId }

    /** Human-readable path such as `U 盘/音乐/华语`; the volume root renders as just the volume id. */
    val displayPath: String
        get() = if (relativePath.isBlank()) volumeId else "$volumeId/$relativePath"
}

private const val SAF_TREE_REFERENCE_PREFIX = "content://"
private const val SAF_TREE_PATH_MARKER = "/tree/"
private const val PRIMARY_VOLUME_ID = "primary"
private const val SD_CARD_VOLUME_ID = "sdcard"

/** Whether [reference] looks like a SAF tree URI rather than a filesystem path or a USB/SMB reference. */
fun isSafTreeReference(reference: String): Boolean =
    parseSafTreeReference(reference) != null

/**
 * Parses any SAF tree URI, regardless of which documents provider serves it.
 *
 * Providers other than `com.android.externalstorage.documents` (third-party file managers, cloud
 * providers) use opaque document ids. Those still parse: everything before the first `:` becomes
 * [SafTreeReference.volumeId], and a document id without `:` yields a blank relative path. UI surfaces
 * therefore always have a folder name to show instead of dumping the raw URI at the user.
 */
fun parseSafTreeReference(reference: String): SafTreeReference? {
    val trimmed = reference.trim()
    if (!trimmed.startsWith(SAF_TREE_REFERENCE_PREFIX)) return null
    val treeIndex = trimmed.indexOf(SAF_TREE_PATH_MARKER)
    if (treeIndex <= 0) return null
    val rawDocumentId = trimmed.substring(treeIndex + SAF_TREE_PATH_MARKER.length)
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
    if (rawDocumentId.isBlank()) return null
    val documentId = decodeSafPercentEncoding(rawDocumentId).trim()
    if (documentId.isBlank()) return null
    val volumeId = documentId.substringBefore(':').trim().trim('/')
    val relativePath = normalizeSafRelativePath(documentId.substringAfter(':', ""))
    // Opaque document ids (third-party providers) carry no volume separator; keep them displayable.
    if (volumeId.isBlank()) return null
    return SafTreeReference(volumeId = volumeId, relativePath = relativePath)
}

private fun normalizeSafRelativePath(path: String): String =
    path.replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/")

/**
 * Decodes `%XX` escapes into their UTF-8 characters without relying on platform APIs.
 *
 * Folder names on a Chinese car head unit are routinely non-ASCII, and the system percent-encodes every
 * byte of them. Decoding byte-wise and converting once at the end keeps multi-byte characters intact;
 * decoding per-`%XX` code unit would mangle them. Malformed escapes are kept verbatim rather than dropped
 * so a corrupt reference still produces a readable label.
 */
internal fun decodeSafPercentEncoding(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%' && index + 2 < value.length) {
            val high = value[index + 1].digitToIntOrNull(16)
            val low = value[index + 2].digitToIntOrNull(16)
            if (high != null && low != null) {
                bytes += ((high shl 4) or low).toByte()
                index += 3
                continue
            }
        }
        value[index].toString().encodeToByteArray().forEach { bytes += it }
        index += 1
    }
    return bytes.toByteArray().decodeToString()
}
