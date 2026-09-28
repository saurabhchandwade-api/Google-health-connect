package com.example.fitnessdashboard.util

import com.example.fitnessdashboard.domain.model.FitnessDashboardData
import com.example.fitnessdashboard.domain.model.FitnessDayData
import com.example.fitnessdashboard.domain.model.FitnessError
import com.example.fitnessdashboard.domain.model.FitnessSummary
import com.example.fitnessdashboard.domain.model.MetricValue
import com.example.fitnessdashboard.domain.model.PermissionResult
import com.example.fitnessdashboard.domain.model.ProviderAvailability
import com.example.fitnessdashboard.domain.model.RangeType
import com.example.fitnessdashboard.domain.model.SubscriptionResult
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSerializer

object JsonUtils {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(MetricValue::class.java, JsonSerializer<MetricValue<*>> { src, _, context ->
            val jsonObject = JsonObject()
            if (src.value != null) {
                when (val v = src.value) {
                    is Number -> jsonObject.addProperty("value", v)
                    is Boolean -> jsonObject.addProperty("value", v)
                    else -> jsonObject.addProperty("value", v.toString())
                }
            } else {
                jsonObject.add("value", null)
            }
            jsonObject.addProperty("status", src.status.name)
            if (src.errorMessage != null) {
                jsonObject.addProperty("errorMessage", src.errorMessage)
            }
            jsonObject
        })
        .registerTypeAdapter(FitnessDayData::class.java, JsonSerializer<FitnessDayData> { src, _, context ->
            val jsonObject = JsonObject()
            jsonObject.addProperty("date", src.date.toString())
            jsonObject.addProperty("startTime", src.startTime)
            jsonObject.addProperty("endTime", src.endTime)

            jsonObject.add("steps", context.serialize(src.steps))
            jsonObject.add("calories", context.serialize(src.caloriesKcal))
            jsonObject.add("distanceMeters", context.serialize(src.distanceMeters))
            jsonObject.add("heartPoints", context.serialize(src.heartPoints))
            jsonObject.add("moveMinutes", context.serialize(src.moveMinutes))
            jsonObject.add("sleepDurationMinutes", context.serialize(src.sleepDurationMinutes))

            val availability = JsonObject()
            availability.addProperty("steps", src.steps.status.name)
            availability.addProperty("calories", src.caloriesKcal.status.name)
            availability.addProperty("distanceMeters", src.distanceMeters.status.name)
            availability.addProperty("heartPoints", src.heartPoints.status.name)
            availability.addProperty("moveMinutes", src.moveMinutes.status.name)
            availability.addProperty("sleepDurationMinutes", src.sleepDurationMinutes.status.name)
            jsonObject.add("availability", availability)

            jsonObject
        })
        .create()

    fun parseRangeType(requestJson: String): RangeType {
        return try {
            val element = gson.fromJson(requestJson, JsonObject::class.java)
            val rangeStr = element.get("range")?.asString?.uppercase() ?: "TODAY"
            RangeType.valueOf(rangeStr)
        } catch (e: Exception) {
            RangeType.TODAY
        }
    }

    fun parseProviderName(requestJson: String): String? {
        return try {
            val element = gson.fromJson(requestJson, JsonObject::class.java)
            element.get("provider")?.asString
        } catch (e: Exception) {
            null
        }
    }

    fun toJson(data: FitnessDashboardData): String {
        val root = JsonObject()
        root.addProperty("success", true)
        root.addProperty("range", data.range.type.name)
        root.addProperty("provider", data.providerName)
        root.addProperty("generatedAt", data.generatedAt.toString())

        val dataObj = JsonObject()
        dataObj.add("summary", gson.toJsonTree(data.summary))
        dataObj.add("days", gson.toJsonTree(data.days))
        root.add("data", dataObj)

        return gson.toJson(root)
    }

    fun toJson(error: FitnessError): String {
        val root = JsonObject()
        root.addProperty("success", false)
        root.addProperty("errorCode", error.code.name)
        root.addProperty("message", error.message)
        root.addProperty("recoverable", error.recoverable)
        if (error.suggestedAction != null) {
            root.addProperty("suggestedAction", error.suggestedAction)
        }
        return gson.toJson(root)
    }

    fun toJson(permissionResult: PermissionResult): String {
        val root = JsonObject()
        root.addProperty("success", permissionResult.isGranted)
        root.addProperty("isGranted", permissionResult.isGranted)
        if (permissionResult.deniedPermissions.isNotEmpty()) {
            root.add("deniedPermissions", gson.toJsonTree(permissionResult.deniedPermissions))
        }
        if (permissionResult.message != null) {
            root.addProperty("message", permissionResult.message)
        }
        return gson.toJson(root)
    }

    fun toJson(availability: ProviderAvailability, providerName: String): String {
        val root = JsonObject()
        root.addProperty("success", availability == ProviderAvailability.AVAILABLE)
        root.addProperty("availability", availability.name)
        root.addProperty("provider", providerName)
        return gson.toJson(root)
    }

    fun toJson(subscriptionResult: SubscriptionResult): String {
        val root = JsonObject()
        root.addProperty("success", subscriptionResult.isSubscribed)
        root.addProperty("isSubscribed", subscriptionResult.isSubscribed)
        root.addProperty("metric", subscriptionResult.metric)
        root.addProperty("message", subscriptionResult.message)
        return gson.toJson(root)
    }
}
