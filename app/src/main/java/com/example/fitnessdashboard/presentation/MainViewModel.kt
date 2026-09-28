package com.example.fitnessdashboard.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitnessdashboard.data.FitnessRepository
import com.example.fitnessdashboard.domain.model.FitnessDashboardData
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.domain.model.ProviderAvailability
import com.example.fitnessdashboard.domain.model.RangeType
import com.example.fitnessdashboard.domain.model.SubscriptionResult
import com.example.fitnessdashboard.util.DateRangeUtils
import com.example.fitnessdashboard.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = false,
    val providerAvailability: ProviderAvailability = ProviderAvailability.AVAILABLE,
    val activeProviderName: String = "GoogleFit",
    val permissionStatus: PermissionStatus = PermissionStatus.NOT_GRANTED,
    val currentRangeType: RangeType = RangeType.TODAY,
    val dashboardData: FitnessDashboardData? = null,
    val subscriptionResult: SubscriptionResult? = null,
    val errorMessage: String? = null
)

class MainViewModel @JvmOverloads constructor(
    application: Application,
    val repository: FitnessRepository = FitnessRepository(application.applicationContext)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkAvailabilityAndPermissions()
    }

    fun checkAvailabilityAndPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val availability = repository.checkAvailability()
                val permissions = repository.checkPermissions()
                val providerName = repository.getActiveProvider().name

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        providerAvailability = availability,
                        permissionStatus = permissions,
                        activeProviderName = providerName
                    )
                }

                if (permissions == PermissionStatus.GRANTED) {
                    loadFitnessData(_uiState.value.currentRangeType)
                }
            } catch (e: Exception) {
                Logger.e("Error checking state", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error initializing fitness provider: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun loadFitnessData(rangeType: RangeType = _uiState.value.currentRangeType) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, currentRangeType = rangeType, errorMessage = null) }
            try {
                val range = DateRangeUtils.calculateDateRange(rangeType)
                val data = repository.readFitnessData(range)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dashboardData = data,
                        activeProviderName = data.providerName
                    )
                }
            } catch (e: Exception) {
                Logger.e("Error loading fitness data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load fitness data: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun selectProvider(providerName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.selectProvider(providerName)
            checkAvailabilityAndPermissions()
        }
    }

    fun subscribeToRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.subscribeToRecording()
            _uiState.update { it.copy(subscriptionResult = result) }
        }
    }

    fun unsubscribeFromRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.unsubscribeFromRecording()
            _uiState.update { it.copy(subscriptionResult = result) }
        }
    }
}
