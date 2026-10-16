package de.riemer87.relic.storage

import de.riemer87.relic.storage.FolderLabel.Location
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderLabelTest {

    @Test
    fun `internal storage folder`() {
        assertEquals(
            FolderLabel.Parts(Location.INTERNAL, listOf("Documents", "Relic")),
            FolderLabel.parse(FolderLabel.EXTERNAL_STORAGE_AUTHORITY, "primary:Documents/Relic"),
        )
    }

    @Test
    fun `root of internal storage has no segments`() {
        assertEquals(FolderLabel.Parts(Location.INTERNAL, emptyList()), FolderLabel.parse(FolderLabel.EXTERNAL_STORAGE_AUTHORITY, "primary:"))
    }

    @Test
    fun `sd card folder`() {
        assertEquals(
            FolderLabel.Parts(Location.SD_CARD, listOf("Tagebuch")),
            FolderLabel.parse(FolderLabel.EXTERNAL_STORAGE_AUTHORITY, "1A2B-3C4D:Tagebuch"),
        )
    }

    @Test
    fun `other provider shows only the last part`() {
        assertEquals(
            FolderLabel.Parts(Location.OTHER, listOf("Relic")),
            FolderLabel.parse("com.example.cloud", "root/Sync/Relic"),
        )
        assertEquals(FolderLabel.Parts(Location.OTHER, emptyList()), FolderLabel.parse("com.example.cloud", ""))
    }
}
