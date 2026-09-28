package com.example.fitnessdashboard.util

import com.example.fitnessdashboard.domain.model.RangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DateRangeUtilsTest {

    private val testZone = ZoneId.of("Asia/Kolkata")

    @Test
    fun testTodayRangeCalculation() {
        val today = LocalDate.of(2025, 1, 15)
        val range = DateRangeUtils.getTodayRange(today, testZone)

        val startZdt = range.start.atZone(testZone)
        val endZdt = range.end.atZone(testZone)

        assertEquals(2025, startZdt.year)
        assertEquals(1, startZdt.monthValue)
        assertEquals(15, startZdt.dayOfMonth)
        assertEquals(0, startZdt.hour)
        assertEquals(0, startZdt.minute)

        assertEquals(15, endZdt.dayOfMonth)
        assertEquals(23, endZdt.hour)
        assertEquals(59, endZdt.minute)
    }

    @Test
    fun testWeekRangeStartsOnMondayEndsOnSunday() {
        // Wednesday Jan 15, 2025
        val Wednesday = LocalDate.of(2025, 1, 15)
        val range = DateRangeUtils.getWeekRange(Wednesday, testZone)

        val startZdt = range.start.atZone(testZone)
        val endZdt = range.end.atZone(testZone)

        // Monday should be Jan 13, 2025
        assertEquals(13, startZdt.dayOfMonth)
        assertEquals(0, startZdt.hour)

        // Sunday should be Jan 19, 2025
        assertEquals(19, endZdt.dayOfMonth)
        assertEquals(23, endZdt.hour)
    }

    @Test
    fun testMonthRangeForLeapYearFebruary() {
        // Feb 10, 2024 (2024 is a leap year -> 29 days)
        val feb2024 = LocalDate.of(2024, 2, 10)
        val range = DateRangeUtils.getMonthRange(feb2024, testZone)

        val startZdt = range.start.atZone(testZone)
        val endZdt = range.end.atZone(testZone)

        assertEquals(1, startZdt.dayOfMonth)
        assertEquals(29, endZdt.dayOfMonth)
    }

    @Test
    fun testMonthRangeForNonLeapYearFebruary() {
        // Feb 10, 2025 (2025 is not a leap year -> 28 days)
        val feb2025 = LocalDate.of(2025, 2, 10)
        val range = DateRangeUtils.getMonthRange(feb2025, testZone)

        val endZdt = range.end.atZone(testZone)

        assertEquals(28, endZdt.dayOfMonth)
    }

    @Test
    fun testDailyBoundsForWeekRange() {
        val refDate = LocalDate.of(2025, 1, 15)
        val range = DateRangeUtils.getWeekRange(refDate, testZone)
        val dailyBounds = DateRangeUtils.getDailyBoundsForRange(range)

        assertEquals(7, dailyBounds.size)
        assertEquals(13, dailyBounds.first().first.dayOfMonth)
        assertEquals(19, dailyBounds.last().first.dayOfMonth)
    }
}
