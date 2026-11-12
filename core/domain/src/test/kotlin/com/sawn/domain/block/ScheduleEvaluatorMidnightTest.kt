package com.sawn.domain.block

import com.sawn.domain.model.Schedule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Calendar

/**
 * The schedules that wrap past midnight.
 *
 * A window that ends after it starts belongs to two calendar days: it is still running
 * in the small hours of the next day, and that day must be able to see it.
 */
class ScheduleEvaluatorMidnightTest : StringSpec({

    /** 22:00 → 06:00 on Monday. */
    val overnight = Schedule(
        id = 1,
        name = "sleep",
        enabled = true,
        daysMask = 0b0000001, // Monday only
        startMinute = 22 * 60,
        endMinute = 6 * 60,
    )

    fun cal(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    "the window is active before midnight" {
        ScheduleEvaluator.isActive(overnight, cal(Calendar.MONDAY, 23, 0)) shouldBe true
    }

    "the window is still active just after midnight" {
        ScheduleEvaluator.isActive(overnight, cal(Calendar.TUESDAY, 0, 0)) shouldBe true
    }

    "the window is still active in the early hours" {
        ScheduleEvaluator.isActive(overnight, cal(Calendar.TUESDAY, 5, 59)) shouldBe true
    }

    "the window is over once the end is reached" {
        ScheduleEvaluator.isActive(overnight, cal(Calendar.TUESDAY, 6, 0)) shouldBe false
    }

    "the window did not start yet on the next day" {
        ScheduleEvaluator.isActive(overnight, cal(Calendar.TUESDAY, 23, 0)) shouldBe false
    }

    "a window that ends when it starts covers the whole day" {
        // start == end is the wrap case, so the window runs from that minute to the end
        // of the day and on into the early hours of the next one.
        val zero = Schedule(
            id = 2,
            name = "zero",
            enabled = true,
            daysMask = 0b0011111,
            startMinute = 600,
            endMinute = 600,
        )
        ScheduleEvaluator.isActive(zero, cal(Calendar.WEDNESDAY, 10, 0)) shouldBe true
        ScheduleEvaluator.isActive(zero, cal(Calendar.WEDNESDAY, 23, 59)) shouldBe true
        // Thursday 05:00 is owned by Wednesday's window.
        ScheduleEvaluator.isActive(zero, cal(Calendar.THURSDAY, 5, 0)) shouldBe true
    }

    "a disabled schedule never counts" {
        // `isActive` judges the time window alone; the enabled flag is applied by
        // `anyActive`, which is what the engine calls.
        val disabled = overnight.copy(enabled = false)
        ScheduleEvaluator.anyActive(listOf(disabled), cal(Calendar.MONDAY, 23, 0)) shouldBe false
        ScheduleEvaluator.anyActive(listOf(overnight), cal(Calendar.MONDAY, 23, 0)) shouldBe true
    }
})
