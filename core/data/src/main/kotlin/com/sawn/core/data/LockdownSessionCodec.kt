package com.sawn.core.data

import com.sawn.domain.lockdown.LockdownReason
import com.sawn.domain.lockdown.LockdownSession
import org.json.JSONObject

/**
 * The panic-lock session as JSON in DataStore.
 *
 * One session, one key — no Room table, no migration: a lockdown is a short-lived
 * thing, and the only thing that must survive a reboot is the session itself.
 *
 * Decoding is deliberately forgiving: a malformed or older payload yields null
 * (no lock) rather than a crash.
 */
internal object LockdownSessionCodec {

    fun encode(session: LockdownSession): String = JSONObject().apply {
        put("id", session.id)
        put("startedAt", session.startedAt)
        put("endsAt", session.endsAt)
        put("durationMinutes", session.durationMinutes)
        put("reason", session.reason.name)
        put("bootCount", session.bootCount)
        put("wallEndsAt", session.wallEndsAt)
    }.toString()

    fun decode(raw: String?): LockdownSession? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            LockdownSession(
                id = json.optString("id"),
                startedAt = json.optLong("startedAt"),
                endsAt = json.optLong("endsAt"),
                durationMinutes = json.optInt("durationMinutes"),
                reason = runCatching { LockdownReason.valueOf(json.optString("reason")) }
                    .getOrDefault(LockdownReason.PANIC),
                bootCount = json.optInt("bootCount"),
                wallEndsAt = json.optLong("wallEndsAt"),
            )
        }.getOrNull()
    }
}
