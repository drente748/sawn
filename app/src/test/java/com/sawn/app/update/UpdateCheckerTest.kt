package com.sawn.app.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun blankManifestDisablesNetworkChecks() = runBlocking {
        assertEquals(UpdateStatus.Disabled, RemoteManifestUpdateChecker("").check(25))
    }

    @Test
    fun insecureManifestIsRejectedBeforeOpeningAConnection() = runBlocking {
        val result = RemoteManifestUpdateChecker("http://updates.example.test/manifest.json").check(25)
        assertEquals(UpdateStatus.Error("Manifest must use HTTPS"), result)
    }
}
