package com.easytv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class StorageNavigationTest {
    private val usb = StorageLocation("USB", File("/storage/ABCD-1234"), removable = true)

    @Test
    fun `up from a storage root returns to device list`() {
        assertNull(storageParent(usb.directory, listOf(usb)))
    }

    @Test
    fun `up inside a storage volume returns parent directory`() {
        assertEquals(usb.directory, storageParent(File(usb.directory, "TV"), listOf(usb)))
    }
}
