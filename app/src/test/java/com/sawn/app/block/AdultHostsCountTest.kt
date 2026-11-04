package com.sawn.app.block

import org.junit.Assert.assertTrue
import org.junit.Test

class AdultHostsCountTest {
    @Test
    fun baseHostlist_is_populated() {
        assertTrue(AdultHosts.includedCount() > 100)
    }
}
