package com.example.fitnessdashboard.presentation

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.example.fitnessdashboard.databinding.ActivityMainBinding
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.domain.model.ProviderAvailability
import com.example.fitnessdashboard.util.Logger
import com.example.fitnessdashboard.web.FitnessJavascriptBridge
import com.example.fitnessdashboard.web.WebViewCallbackDispatcher
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var callbackDispatcher: WebViewCallbackDispatcher
    private lateinit var javascriptBridge: FitnessJavascriptBridge

    private val healthConnectPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        Logger.i("Health Connect permissions callback: granted count = ${grantedPermissions.size}")
        javascriptBridge.checkFitnessPermissions()
        viewModel.checkAvailabilityAndPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSystemBars()

        callbackDispatcher = WebViewCallbackDispatcher { binding.webView }
        javascriptBridge = FitnessJavascriptBridge(
            activity = this,
            repository = viewModel.repository,
            dispatcher = callbackDispatcher,
            coroutineScope = lifecycleScope,
            onRequestPermissions = ::requestPermissionsIfNeeded
        )

        setupWebView()

        // Ask for Health Connect access up front so the dashboard never needs a permission button
        if (savedInstanceState == null) {
            requestPermissionsIfNeeded()
        }
    }

    // Blend the status and navigation bars into the page background
    private fun setupSystemBars() {
        val background = Color.parseColor("#F4F7F9")
        @Suppress("DEPRECATION")
        window.statusBarColor = background
        @Suppress("DEPRECATION")
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    Logger.i("WebView loaded page: $url")
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // Restrict navigation to local assets only; open external links in browser
                    if (url.startsWith("file:///android_asset/")) {
                        return false
                    } else {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(browserIntent)
                        return true
                    }
                }
            }

            addJavascriptInterface(javascriptBridge, FitnessJavascriptBridge.BRIDGE_NAME)
            loadUrl("file:///android_asset/index.html")
        }
    }

    private fun requestPermissionsIfNeeded() {
        lifecycleScope.launch {
            val repository = viewModel.repository
            if (repository.checkAvailability() != ProviderAvailability.AVAILABLE) {
                Logger.w("Health Connect is not available; skipping permission request.")
                return@launch
            }
            if (repository.checkPermissions() != PermissionStatus.GRANTED) {
                Logger.i("Launching Health Connect permission request dialog.")
                healthConnectPermissionLauncher.launch(repository.healthConnectProvider.requiredPermissions)
            } else {
                javascriptBridge.checkFitnessPermissions()
            }
        }
    }
}
