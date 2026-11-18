package top.iwesley.lyn.music.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.audioImportFailure
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.core.model.warn
import java.io.File

/**
 * Third read option for local folders: when the direct `File` scan comes back empty (or the root is not
 * directly readable), audio tracks are resolved through the system media library and stored as
 * `content://` media locators, which the player already supports.
 */
internal fun canQueryAndroidMediaStoreAudio(context: Context): Boolean {
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED ||
        hasManageAllFilesAccess(context)
}

internal fun scanAndroidMediaStoreAudioUnderRoot(
    context: Context,
    root: File,
    sourceId: String,
    progressSink: ImportScanProgressSink,
    logger: DiagnosticLogger = GlobalDiagnosticLogger,
): ImportScanReport {
    if (!canQueryAndroidMediaStoreAudio(context)) {
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "mediastore-scan-skip source=$sourceId root=${root.absolutePath} reason=permission"
        }
        return ImportScanReport(tracks = emptyList())
    }
    val startedAt = System.currentTimeMillis()
    logger.info(LOCAL_IMPORT_LOG_TAG) {
        "mediastore-scan-start source=$sourceId root=${root.absolutePath} sdk=${Build.VERSION.SDK_INT}"
    }
    return runCatching {
        collectAndroidMediaStoreAudioTracks(context, root, sourceId, progressSink, logger)
    }.onSuccess { report ->
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "mediastore-scan-complete source=$sourceId root=${root.absolutePath} " +
                "trackCount=${report.tracks.size} discoveredAudioFileCount=${report.discoveredAudioFileCount} " +
                "failureCount=${report.failures.size} elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
    }.onFailure { throwable ->
        logger.warn(LOCAL_IMPORT_LOG_TAG) {
            "mediastore-scan-failed source=$sourceId root=${root.absolutePath} " +
                "reason=${throwable.message.orEmpty()} elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
    }.getOrDefault(ImportScanReport(tracks = emptyList()))
}

private fun collectAndroidMediaStoreAudioTracks(
    context: Context,
    root: File,
    sourceId: String,
    progressSink: ImportScanProgressSink,
    logger: DiagnosticLogger,
): ImportScanReport {
    val rootPath = root.canonicalFileOrSelf().absolutePath.trimEnd('/')
    val mediaRwPrefix = "/mnt/media_rw/${root.name.trimEnd('/')}/"
    val tracks = mutableListOf<ImportedTrackCandidate>()
    val failures = mutableListOf<ImportScanFailure>()
    var discoveredAudioFileCount = 0

    val projection = mutableListOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_MODIFIED,
    )
    val selection = "(DATA LIKE ? OR DATA LIKE ?)"
    val selectionArgs = mutableListOf("$rootPath/%", "$mediaRwPrefix%")
    val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    if (useRelativePath) {
        projection += MediaStore.MediaColumns.RELATIVE_PATH
    }
    val collection: Uri = if (useRelativePath) {
        // content://media/external/audio on API 29+, per the media-library fallback contract.
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    context.contentResolver.query(
        collection,
        projection.toTypedArray(),
        selection,
        selectionArgs.toTypedArray(),
        "${MediaStore.Audio.Media.DATA} ASC",
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val relativePathColumn = if (useRelativePath) {
            cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            -1
        }
        while (cursor.moveToNext()) {
            val displayName = cursor.getString(displayNameColumn)?.takeIf { it.isNotBlank() } ?: continue
            val relativePath = when {
                !cursor.isNull(dataColumn) ->
                    cursor.getString(dataColumn)?.let { data -> data.relativePathUnder(rootPath, mediaRwPrefix) }
                        ?.takeIf { it.isNotBlank() }
                else -> null
            } ?: run {
                if (useRelativePath && relativePathColumn >= 0 && !cursor.isNull(relativePathColumn)) {
                    val directory = cursor.getString(relativePathColumn).orEmpty().trim('/')
                    listOf(directory, displayName).filter { it.isNotBlank() }.joinToString("/")
                } else {
                    displayName
                }
            }
            when (classifyAndroidScannedAudioFile(displayName)) {
                NonNavidromeAudioScanResult.NOT_AUDIO -> Unit

                NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED -> {
                    discoveredAudioFileCount += 1
                    failures += unsupportedAudioImportFailure(relativePath)
                }

                NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                    discoveredAudioFileCount += 1
                    runCatching {
                        AndroidAudioTagReader.readCandidate(
                            context = context,
                            uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                cursor.getLong(idColumn),
                            ),
                            displayName = displayName,
                            relativePath = relativePath,
                            artworkDirectory = File(context.cacheDir, "artwork"),
                            logger = logger,
                            sizeBytes = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                                cursor.getLong(sizeColumn)
                            } else {
                                0L
                            },
                            modifiedAt = if (dateModifiedColumn >= 0 && !cursor.isNull(dateModifiedColumn)) {
                                cursor.getLong(dateModifiedColumn) * 1000L
                            } else {
                                0L
                            },
                        )
                    }.onSuccess { candidate ->
                        tracks += candidate
                        progressSink.onProgress(
                            ImportScanProgress(
                                sourceId = sourceId,
                                phase = ImportScanPhase.Scanning,
                                importedTrackCount = tracks.size,
                            ),
                        )
                    }.onFailure { throwable ->
                        failures += audioImportFailure(relativePath, throwable)
                    }
                }
            }
        }
    }
    return ImportScanReport(
        tracks = tracks,
        discoveredAudioFileCount = discoveredAudioFileCount,
        failures = failures,
    )
}

/** `a.mp3` under the scanned root or its `/mnt/media_rw` backing mount, or `null` when outside both. */
private fun String.relativePathUnder(rootPath: String, mediaRwPrefix: String): String? {
    val normalized = replace('\\', '/')
    return when {
        normalized.startsWith("$rootPath/") -> normalized.removePrefix("$rootPath/")
        normalized.startsWith(mediaRwPrefix) -> normalized.removePrefix(mediaRwPrefix)
        else -> null
    }
}

private const val LOCAL_IMPORT_LOG_TAG = "LocalImport"
