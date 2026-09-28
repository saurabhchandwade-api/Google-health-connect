package com.example.fitnessdashboard.util

import com.example.fitnessdashboard.domain.model.FitnessDateRange
import com.example.fitnessdashboard.domain.model.RangeType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object DateRangeUtils {

    fun calculateDateRange(
        type: RangeType,
        customStart: Instant? = null,
        customEnd: Instant? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FitnessDateRange {
        val now = ZonedDateTime.now(zoneId)
        return when (type) {
            RangeType.TODAY -> getTodayRange(now.toLocalDate(), zoneId)
            RangeType.WEEK -> getWeekRange(now.toLocalDate(), zoneId)
            RangeType.MONTH -> getMonthRange(now.toLocalDate(), zoneId)
            RangeType.CUSTOM -> {
                val start = customStart ?: now.with(LocalTime.MIN).toInstant()
                val end = customEnd ?: now.with(LocalTime.MAX).toInstant()
                FitnessDateRange(start, end, RangeType.CUSTOM, zoneId)
            }
        }
    }

    fun getTodayRange(
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FitnessDateRange {
        val startZdt = today.atStartOfDay(zoneId)
        val endZdt = today.atTime(LocalTime.MAX).atZone(zoneId)
        return FitnessDateRange(
            start = startZdt.toInstant(),
            end = endZdt.toInstant(),
            type = RangeType.TODAY,
            zoneId = zoneId
        )
    }

    fun getWeekRange(
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FitnessDateRange {
        val monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)

        val startZdt = monday.atStartOfDay(zoneId)
        val endZdt = sunday.atTime(LocalTime.MAX).atZone(zoneId)

        return FitnessDateRange(
            start = startZdt.toInstant(),
            end = endZdt.toInstant(),
            type = RangeType.WEEK,
            zoneId = zoneId
        )
    }

    fun getMonthRange(
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FitnessDateRange {
        val firstDay = referenceDate.with(TemporalAdjusters.firstDayOfMonth())
        val lastDay = referenceDate.with(TemporalAdjusters.lastDayOfMonth())

        val startZdt = firstDay.atStartOfDay(zoneId)
        val endZdt = lastDay.atTime(LocalTime.MAX).atZone(zoneId)

        return FitnessDateRange(
            start = startZdt.toInstant(),
            end = endZdt.toInstant(),
            type = RangeType.MONTH,
            zoneId = zoneId
        )
    }

    fun getDailyBoundsForRange(range: FitnessDateRange): List<Pair<ZonedDateTime, ZonedDateTime>> {
        val startZdt = range.start.atZone(range.zoneId)
        val endZdt = range.end.atZone(range.zoneId)

        val bounds = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        var currentDayStart = startZdt.toLocalDate().atStartOfDay(range.zoneId)

        val lastDay = endZdt.toLocalDate()
        while (!currentDayStart.toLocalDate().isAfter(lastDay)) {
            val currentDayEnd = currentDayStart.toLocalDate().atTime(LocalTime.MAX).atZone(range.zoneId)
            bounds.add(Pair(currentDayStart, currentDayEnd))
            currentDayStart = currentDayStart.plusDays(1)
        }

        return bounds
    }

    fun formatIsoDateTime(zdt: ZonedDateTime): String {
        return zdt.toOffsetDateTime().toString()
    }
}
