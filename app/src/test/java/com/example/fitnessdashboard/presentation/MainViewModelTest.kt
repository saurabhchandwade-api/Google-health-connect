package com.example.fitnessdashboard.presentation

import com.example.fitnessdashboard.data.FakeFitnessDataProvider
import com.example.fitnessdashboard.domain.model.RangeType
import com.example.fitnessdashboard.util.DateRangeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @Test
    fun testFakeProviderDirectIntegration() = runBlocking {
        val fakeProvider = FakeFitnessDataProvider()
        val range = DateRangeUtils.calculateDateRange(
            type = RangeType.TODAY,
            zoneId = ZoneId.of("UTC")
        )
        val data = fakeProvider.readFitnessData(range)

        assertNotNull(data)
        assertEquals("DemoProvider", data.providerName)
        assertEquals(1, data.days.size)
        assertTrue((data.summary.steps ?: 0) > 0)
    }

    @Test
    fun testFakeProviderWeekDataIntegration() = runBlocking {
        val fakeProvider = FakeFitnessDataProvider()
        val range = DateRangeUtils.getWeekRange(
            referenceDate = LocalDate.of(2025, 1, 15),
            zoneId = ZoneId.of("UTC")
        )
        val data = fakeProvider.readFitnessData(range)

        assertNotNull(data)
        assertEquals(7, data.days.size)
        assertNotNull(data.summary.steps)
        assertNotNull(data.summary.caloriesKcal)
        assertNotNull(data.summary.distanceMeters)
    }
}
