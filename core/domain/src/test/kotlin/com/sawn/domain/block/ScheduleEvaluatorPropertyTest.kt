package com.sawn.domain.block

import com.sawn.domain.model.Schedule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.zip
import io.kotest.property.checkAll
import java.util.Calendar

/**
 * The schedules, as properties rather than examples.
 *
 * A schedule is a promise about *when* the blocking applies, so the two things that
 * must never break are: a minute that is inside the window is active, and a window
 * that crosses midnight belongs to two days — it is still running in the small hours
 * of the next day, and that day must see it.
 */
class ScheduleEvaluatorPropertyTest : StringSpec({

    /** A schedule that runs on one day only, between two minutes of the day. */
    fun scheduleOf(day: Int, startMinute: Int, endMinute: Int): Schedule =
        Schedule(
            id = 1,
            name = "property",
            enabled = true,
            daysMask = 1 shl day,
            startMinute = startMinute,
            endMinute = endMinute,
        )

    /**
     * A calendar at a given minute of a given day.
     *
     * The evaluator numbers the days from Monday (0) to Sunday (6); the calendar
     * numbers them the other way round, with Sunday as 1.
     */
    fun calendar(day: Int, minuteOfDay: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, if (day == 6) 1 else day + 2)
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /**
     * INVARIANT 1 — every minute inside the window is active.
     *
     * If this broke, the blocking would silently stop in the middle of the window the
     * user set, and the schedule would be worse than useless: it would promise
     * protection and quietly withdraw it.
     */
    "every minute inside a window that does not cross midnight is active" {
        checkAll(100, ScheduleWindows.dayWindow, Arb.int(0, 6)) { (start, end), day ->
            val schedule = scheduleOf(day, start, end)
            for (minute in start until end) {
                ScheduleEvaluator.isActive(schedule, calendar(day, minute)) shouldBe true
            }
        }
    }

    /** The minute the window ends is the first one outside it. */
    "the minute the window ends is not active" {
        checkAll(ScheduleWindows.dayWindow, Arb.int(0, 6)) { (start, end), day ->
            val schedule = scheduleOf(day, start, end)
            if (end < 1439) {
                ScheduleEvaluator.isActive(schedule, calendar(day, end)) shouldBe false
            }
        }
    }

    /**
     * INVARIANT 2 — a window that crosses midnight is still running in the early hours
     * of the next day.
     *
     * The window belongs to the day it starts on, but it does not end at midnight: the
     * small hours belong to it, and the next day must see that. The day after the
     * window's own day is the one that has to see it.
     */
    "the early hours after a window that crosses midnight are active" {
        checkAll(100, ScheduleWindows.nightWindow, Arb.int(0, 6)) { (start, end), day ->
            val schedule = scheduleOf(day, start, end)
            val nextDay = (day + 1) % 7
            for (minute in 0 until end) {
                ScheduleEvaluator.isActive(schedule, calendar(nextDay, minute)) shouldBe true
            }
            // The window is over once its end is reached.
            ScheduleEvaluator.isActive(schedule, calendar(nextDay, end)) shouldBe false
        }
    }

    /** Before it starts, the window has not begun — not even on the next day. */
    "the hours before a window that crosses midnight are not active" {
        checkAll(100, ScheduleWindows.nightWindow, Arb.int(0, 6)) { (start, end), day ->
            val schedule = scheduleOf(day, start, end)
            for (minute in 0 until start) {
                ScheduleEvaluator.isActive(schedule, calendar(day, minute)) shouldBe false
            }
        }
    }

    /** A schedule whose day is not today never fires today. */
    "a schedule fires only on the days it names" {
        checkAll(ScheduleWindows.dayWindow, Arb.int(0, 6), Arb.int(0, 6)) { (start, end), day, other ->
            val schedule = scheduleOf(day, start, end)
            if (other != day) {
                ScheduleEvaluator.isActive(schedule, calendar(other, start)) shouldBe false
            }
        }
    }

    /**
     * The generators' own invariants.
     *
     * A generator that can produce a window with `start == end` makes every property
     * above meaningless for that case — the window is a different animal there (it
     * wraps), so the properties would be testing the wrong thing and would fail in a
     * way that looks like a bug in the evaluator. Asserting the generators' contract
     * turns that into a failure that names the real cause.
     */
    "the generated windows always satisfy their contract" {
        checkAll(ScheduleWindows.dayWindow) { (start, end) ->
            (start < end) shouldBe true
            (end <= 1439) shouldBe true
        }
        checkAll(ScheduleWindows.nightWindow) { (start, end) ->
            (start > end) shouldBe true
            (end <= 1439) shouldBe true
        }
    }
})

/**
 * The windows the properties are drawn from.
 *
 * Both generators sort their two ends and then either keep or swap them, so the
 * contract holds for every sample: a day window always starts before it ends, a night
 * window always starts after it ends.
 */
private object ScheduleWindows {

    /** A window that does not cross midnight: the start is strictly before the end. */
    val dayWindow: Arb<Pair<Int, Int>> = Arb.zip(Arb.int(0, 1438), Arb.int(0, 1438)) { a, b ->
        val earlier = minOf(a, b)
        val later = maxOf(a, b)
        if (earlier == later) earlier to (earlier + 1) else earlier to later
    }

    /** A window that crosses midnight: the start is strictly after the end. */
    val nightWindow: Arb<Pair<Int, Int>> = Arb.zip(Arb.int(0, 1438), Arb.int(0, 1438)) { a, b ->
        val earlier = minOf(a, b)
        val later = maxOf(a, b)
        if (earlier == later) (earlier + 1) to earlier else later to earlier
    }
}
