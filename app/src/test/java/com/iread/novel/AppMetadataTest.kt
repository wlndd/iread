package com.iread.novel

import org.junit.Assert.assertEquals
import org.junit.Test

class AppMetadataTest {
    @Test
    fun exposesApprovedApplicationIdentity() {
        assertEquals("iRead", AppMetadata.name)
        assertEquals("com.iread.novel", AppMetadata.packageName)
    }
}
