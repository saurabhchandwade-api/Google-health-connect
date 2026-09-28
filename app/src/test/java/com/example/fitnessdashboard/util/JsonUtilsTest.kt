package com.example.fitnessdashboard.util

import com.example.fitnessdashboard.domain.model.FitnessError
import com.example.fitnessdashboard.domain.model.FitnessErrorCode
import com.example.fitnessdashboard.domain.model.PermissionResult
import com.example.fitnessdashboard.domain.model.ProviderAvailability
import com.example.fitnessdashboard.domain.model.RangeType
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsTest {

    @Test
    fun testParseRangeTypeValid() {
        val json = "{\"range\": \"WEEK\"}"
        val range = JsonUtils.parseRangeType(json)
        assertEquals(RangeType.WEEK, range)
    }

    @Test
    fun testParseRangeTypeInvalidDefaultsToToday() {
        val json = "{\"range\": \"UNKNOWN_RANGE\"}"
        val range = JsonUtils.parseRangeType(json)
        assertEquals(RangeType.TODAY, range)
    }

    @Test
    fun testErrorToJsonSerialization() {
        val error = FitnessError(
            code = FitnessErrorCode.PERMISSION_DENIED,
            message = "Fitness access denied by user.",
            recoverable = true,
            suggestedAction = "REQUEST_PERMISSIONS"
        )

        val jsonStr = JsonUtils.toJson(error)
        val jsonObj = JsonParser.parseString(jsonStr).asJsonObject

        assertFalse(jsonObj.get("success").asBoolean)
        assertEquals("PERMISSION_DENIED", jsonObj.get("errorCode").asString)
        assertEquals("Fitness access denied by user.", jsonObj.get("message").asString)
        assertTrue(jsonObj.get("recoverable").asBoolean)
        assertEquals("REQUEST_PERMISSIONS", jsonObj.get("suggestedAction").asString)
    }

    @Test
    fun testPermissionResultToJsonSerialization() {
        val permResult = PermissionResult(
            isGranted = false,
            deniedPermissions = listOf("android.permission.ACTIVITY_RECOGNITION"),
            message = "Activity recognition permission required."
        )

        val jsonStr = JsonUtils.toJson(permResult)
        val jsonObj = JsonParser.parseString(jsonStr).asJsonObject

        assertFalse(jsonObj.get("success").asBoolean)
        assertFalse(jsonObj.get("isGranted").asBoolean)
        assertEquals(1, jsonObj.getAsJsonArray("deniedPermissions").size())
    }

    @Test
    fun testProviderAvailabilityToJsonSerialization() {
        val jsonStr = JsonUtils.toJson(ProviderAvailability.AVAILABLE, "GoogleFit")
        val jsonObj = JsonParser.parseString(jsonStr).asJsonObject

        assertTrue(jsonObj.get("success").asBoolean)
        assertEquals("AVAILABLE", jsonObj.get("availability").asString)
        assertEquals("GoogleFit", jsonObj.get("provider").asString)
    }
}
