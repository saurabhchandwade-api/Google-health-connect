package com.example.fitnessdashboard.presentation

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fitnessdashboard.data.HealthConnectDataProvider
import com.example.fitnessdashboard.databinding.ActivityMainBinding
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.permissions.FitnessPermissionManager
import com.example.fitnessdashboard.util.Logger
import com.example.fitnessdashboard.web.FitnessJavascriptBridge
import com.example.fitnessdashboard.web.WebViewCallbackDispatcher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var permissionManager: FitnessPermissionManager
    private lateinit var callbackDispatcher: WebViewCallbackDispatcher
    private lateinit var javascriptBridge: FitnessJavascriptBridge

    private val activityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Logger.i("Activity Recognition permission granted.")
            requestGoogleFitPermissionIfNeeded()
        } else {
            Logger.w("Activity Recognition permission denied.")
            showNativeFallback("Activity Recognition permission was denied. Please grant permission in Settings.")
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Logger.i("Google Sign-In authorization success.")
            viewModel.checkAvailabilityAndPermissions()
        } else {
            Logger.w("Google Sign-In authorization cancelled or failed. Code: ${result.resultCode}")
            showNativeFallback("Google Account sign-in/authorization was cancelled.")
        }
    }

    private val healthConnectPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        Logger.i("Health Connect permissions callback: granted count = ${grantedPermissions.size}")
        binding.nativeFallbackContainer.visibility = View.GONE
        viewModel.checkAvailabilityAndPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = FitnessPermissionManager(this)
        callbackDispatcher = WebViewCallbackDispatcher { binding.webView }
        javascriptBridge = FitnessJavascriptBridge(
            activity = this,
            repository = viewModel.repository,
            dispatcher = callbackDispatcher,
            coroutineScope = lifecycleScope
        )

        setupWebView()
        setupListeners()
        observeViewModel()
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

    private fun setupListeners() {
        binding.btnGrantPermissions.setOnClickListener {
            requestPermissions()
        }

        binding.btnOpenSettings.setOnClickListener {
            openAppSettings()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.permissionStatus == PermissionStatus.GRANTED) {
                        binding.nativeFallbackContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun requestPermissions() {
        val activeProvider = viewModel.repository.getActiveProvider()
        if (activeProvider is HealthConnectDataProvider) {
            Logger.i("Launching Health Connect permission request dialog.")
            healthConnectPermissionLauncher.launch(activeProvider.requiredPermissions)
            return
        }

        val arStatus = permissionManager.checkActivityRecognitionPermission()
        if (arStatus != PermissionStatus.GRANTED) {
            val reqPermissions = FitnessPermissionManager.getRequiredPermissions()
            if (reqPermissions.isNotEmpty()) {
                activityRecognitionPermissionLauncher.launch(reqPermissions[0])
                return
            }
        }
        requestGoogleFitPermissionIfNeeded()
    }

    private fun requestGoogleFitPermissionIfNeeded() {
        lifecycleScope.launch {
            val result = viewModel.repository.requestPermissions(this@MainActivity)
            if (!result.isGranted) {
                // Trigger Google Sign In flow if needed
                val options = FitnessOptions.builder()
                    .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.AGGREGATE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.AGGREGATE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.AGGREGATE_HEART_POINTS, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.AGGREGATE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
                    .build()

                val signInClient = GoogleSignIn.getClient(
                    this@MainActivity,
                    GoogleSignInOptions.Builder(
                        GoogleSignInOptions.DEFAULT_SIGN_IN
                    ).addExtension(options).build()
                )
                googleSignInLauncher.launch(signInClient.signInIntent)
            } else {
                binding.nativeFallbackContainer.visibility = View.GONE
                viewModel.checkAvailabilityAndPermissions()
            }
        }
    }

    private fun showNativeFallback(message: String) {
        binding.nativeFallbackContainer.visibility = View.VISIBLE
        binding.fallbackMessage.text = message
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}
