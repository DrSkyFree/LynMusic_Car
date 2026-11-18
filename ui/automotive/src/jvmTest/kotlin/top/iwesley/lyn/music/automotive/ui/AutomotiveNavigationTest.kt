package top.iwesley.lyn.music.automotive.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppTab

class AutomotiveNavigationTest {
    @Test
    fun `rotation changes shell and player together including narrow landscape screens`() {
        for ((width, height) in listOf(480 to 800, 720 to 1280)) {
            val portrait = AutomotiveLayoutProfile(width.dp, height.dp)
            val landscape = AutomotiveLayoutProfile(height.dp, width.dp)
            assertTrue(portrait.usesPortraitLibraryNavigation)
            assertTrue(portrait.isCompactLayout)
            assertFalse(portrait.isLandscape)
            assertFalse(landscape.usesPortraitLibraryNavigation)
            assertTrue(landscape.isExpandedLayout)
            assertTrue(landscape.isLandscape)
        }
        assertTrue(AutomotiveLayoutProfile(600.dp, 600.dp).isCompactLayout)
    }

    @Test
    fun `car navigation converges on my library and settings`() {
        val expected = listOf(AppTab.My, AppTab.Library, AppTab.Settings)
        assertEquals(expected, automotivePrimaryNavigationTabs(true))
        assertEquals(expected, automotivePrimaryNavigationTabs(false))
        assertEquals(expected, automotiveLandscapeNavigationTabs)
    }

    @Test
    fun `collections and sources fold into their parent destinations`() {
        assertEquals(listOf(AppTab.Favorites, AppTab.Playlists), automotiveCollectionTabs)
        assertEquals(AppTab.My, automotiveNavigationGroup(AppTab.Playlists))
        assertEquals(AppTab.My, automotiveNavigationGroup(AppTab.Favorites))
        assertEquals(AppTab.Settings, automotiveNavigationGroup(AppTab.Sources))
        assertEquals(AppTab.Library, automotiveNavigationGroup(AppTab.Tags))
        for (tab in listOf(AppTab.My, AppTab.Library, AppTab.Settings)) {
            assertEquals(tab, automotiveNavigationGroup(tab))
        }
        assertTrue(isMobileLibraryHubTab(AppTab.Favorites))
        assertTrue(isMobileLibraryHubTab(AppTab.Playlists))
        assertFalse(isMobileLibraryHubTab(AppTab.Sources))
    }

    @Test
    fun `restored unsupported tags and sources destinations land on a reachable tab`() {
        assertEquals(AppTab.Library, resolveAutomotiveTab(AppTab.Tags))
        assertEquals(AppTab.Settings, resolveAutomotiveTab(AppTab.Sources))
        for (tab in automotiveLandscapeNavigationTabs) assertEquals(tab, resolveAutomotiveTab(tab))
    }
}
