package com.sawn.domain.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The backup body must survive encode → decode byte-for-byte, or a "restore" lies. */
class BackupCodecTest {

    private fun doc() = BackupDocument(
        profile = BackupProfile("سالم", 14),
        settings = BackupSettings(
            masterEnabled = true, alwaysOn = false, blockDoh = true, blockAds = false,
            blockNotifications = true, pornGuard = true, safeSearchForce = false, reduceMotion = true,
            locale = "ar", deepFocusEnabled = true, deepFocusWhitelist = listOf("com.android.dialer"),
            nightGuardEnabled = true, nightGuardStartMin = 1260, nightGuardEndMin = 400,
            accountabilityName = "أبي", accountabilityContact = "+000", nudgesEnabled = true,
        ),
        blockedItems = listOf(
            BackupItem("APP", "com.instagram.android", "Instagram", true, "PAUSE", 20, "توقف", 3, 45, 10),
            BackupItem("WEBSITE", "example.com", "example.com", false, "BLOCK", 15, null, 0, 0, 0),
        ),
        schedules = listOf(BackupSchedule("العمل", daysMask = 0b00011111, startMinute = 540, endMinute = 1020, enabled = true)),
        protectedDays = listOf("2026-11-01", "2026-11-02"),
        enabledFeatures = listOf("porn", "safesearch"),
        onboardingAnswers = "source=phone|goal=focus",
    )

    @Test
    fun `encode then decode returns the same document`() {
        val original = doc()
        val restored = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun `hostile free text cannot break the format`() {
        val hostile = doc().copy(
            profile = BackupProfile("a\tb|c\nd", 9),
            blockedItems = listOf(doc().blockedItems[0].copy(label = "x|y\tz", customPrompt = "line\tone")),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(hostile))
        assertEquals(hostile.profile, restored?.profile)
        assertEquals(hostile.blockedItems, restored?.blockedItems)
    }

    @Test
    fun `the old placeholder is not accepted as a backup`() {
        assertNull(BackupCodec.decode("sawn-backup-export"))
        assertNull(BackupCodec.decode(""))
        assertNull(BackupCodec.decode("SAWNBAK1:too\tfew"))
    }
}
