package com.example.fitnessdashboard.data

import com.example.fitnessdashboard.domain.model.FitnessMetricStatus
import com.example.fitnessdashboard.domain.model.RangeType
import com.example.fitnessdashboard.util.DateRangeUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FakeFitnessDataProviderTest {

    private lateinit var provider: FakeFitnessDataProvider
    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        provider = FakeFitnessDataProvider()
    }

    @Test
    fun testFakeProviderReturnsValidDataForToday() = runBlocking {
        val range = DateRangeUtils.getTodayRange(LocalDate.of(2025, 1, 15), zoneId)
        val result = provider.readFitnessData(range)

        assertNotNull(result)
        assertEquals(1, result.days.size)
        assertEquals("DemoProvider", result.providerName)

        val todayData = result.days.first()
        assertEquals(FitnessMetricStatus.AVAILABLE, todayData.steps.status)
        assertTrue((todayData.steps.value ?: 0) > 0)
        assertTrue((todayData.caloriesKcal.value ?: 0.0) > 0.0)
        assertTrue((todayData.distanceMeters.value ?: 0.0) > 0.0)
        assertTrue((todayData.sleepDurationMinutes.value ?: 0) > 0)
    }

    @Test
    fun testFakeProviderSummaryCalculationsForWeek() = runBlocking {
        val refDate = LocalDate.of(2025, 1, 15)
        val range = DateRangeUtils.getWeekRange(refDate, zoneId)
        val result = provider.readFitnessData(range)

        assertEquals(7, result.days.size)
        val totalSteps = result.days.sumOf { it.steps.value ?: 0 }
        assertEquals(totalSteps, result.summary.steps)
    }

    @Test
    fun testRecordingSubscriptionToggle() = runBlocking {
        val subResult = provider.subscribeToRecording()
        assertTrue(subResult.isSubscribed)
        assertTrue(provider.isRecordingSubscribed)

        val unsubResult = provider.unsubscribeFromRecording()
        assertTrue(!unsubResult.isSubscribed)
        assertTrue(!provider.isRecordingSubscribed)
    }
}
