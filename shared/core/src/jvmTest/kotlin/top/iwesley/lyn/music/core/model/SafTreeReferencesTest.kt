package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafTreeReferencesTest {
    private val usbTreeChinese =
        "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A%E9%9F%B3%E4%B9%90%2F%E5%8D%8E%E8%AF%AD"

    @Test
    fun `parses usb tree with chinese folder segments`() {
        val reference = parseSafTreeReference(usbTreeChinese)

        assertEquals("1A2B-3C4D", reference?.volumeId)
        assertEquals("音乐/华语", reference?.relativePath)
        assertEquals("华语", reference?.folderName)
        assertEquals("1A2B-3C4D/音乐/华语", reference?.displayPath)
        assertTrue(reference?.isRemovableVolume == true)
    }

    @Test
    fun `parses primary tree and keeps it out of the removable bucket`() {
        val reference = parseSafTreeReference(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic%2F%E6%97%A5%E6%9C%AC",
        )

        assertEquals("primary", reference?.volumeId)
        assertEquals("Music/日本", reference?.relativePath)
        assertFalse(reference?.isRemovableVolume == true)
    }

    @Test
    fun `volume root tree has no trailing slash in display path`() {
        val reference = parseSafTreeReference(
            "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A",
        )

        assertEquals("", reference?.relativePath)
        assertEquals("1A2B-3C4D", reference?.folderName)
        assertEquals("1A2B-3C4D", reference?.displayPath)
    }

    @Test
    fun `non tree references are rejected`() {
        assertNull(parseSafTreeReference("/storage/1A2B-3C4D/音乐"))
        assertNull(parseSafTreeReference("lynmusic-usb-folder://v1/abc/def"))
        assertNull(parseSafTreeReference("content://com.android.externalstorage.documents/root/primary"))
        assertNull(parseSafTreeReference(""))
        assertFalse(isSafTreeReference("file:///storage/1A2B-3C4D/song.flac"))
    }

    @Test
    fun `malformed percent escapes survive verbatim`() {
        val reference = parseSafTreeReference(
            "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A100%25%E9%9F%B3%E4%B9%90",
        )

        assertEquals("100%音乐", reference?.relativePath)
        // `%ZZ` is not a valid escape; keeping the literal text is better than dropping the name entirely.
        assertEquals(
            "100%ZZ音乐",
            parseSafTreeReference(
                "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A100%25ZZ%E9%9F%B3%E4%B9%90",
            )?.relativePath,
        )
    }

    @Test
    fun `repeated and mixed separators normalize to single slash`() {
        val reference = parseSafTreeReference(
            "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A%2F%E9%9F%B3%E4%B9%90%2F%2F%E5%8D%8E%E8%AF%AD%2F",
        )

        assertEquals("音乐/华语", reference?.relativePath)
    }

    @Test
    fun `display label of a usb tree shows drive and folder path`() = runTest {
        val simplified = resolveUiText(displayLocalFolderReference(usbTreeChinese), AppLanguage.SimplifiedChinese)

        assertTrue(simplified.contains("U 盘"), simplified)
        assertTrue(simplified.contains("音乐/华语"), simplified)
        assertFalse(simplified.contains("content://"), simplified)
    }

    @Test
    fun `display label of a primary tree never leaks the raw uri`() = runTest {
        val label = resolveUiText(
            displayLocalFolderReference(
                "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            ),
            AppLanguage.SimplifiedChinese,
        )

        assertEquals("primary/Music", label)
    }
}
