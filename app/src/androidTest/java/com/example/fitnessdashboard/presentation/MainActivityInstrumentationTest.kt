package com.example.fitnessdashboard.presentation

import android.webkit.WebView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.fitnessdashboard.R
import com.example.fitnessdashboard.web.FitnessJavascriptBridge
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityInstrumentationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testMainActivityLaunchesAndWebViewLoads() {
        activityRule.scenario.onActivity { activity ->
            assertNotNull(activity)
            val webView = activity.findViewById<WebView>(R.id.webView)
            assertNotNull(webView)
        }
    }

    @Test
    fun testJavascriptBridgeConstants() {
        assertEquals("AndroidFitnessBridge", FitnessJavascriptBridge.BRIDGE_NAME)
    }

    private fun assertEquals(expected: String, actual: String) {
        Assert.assertEquals(expected, actual)
    }
}
