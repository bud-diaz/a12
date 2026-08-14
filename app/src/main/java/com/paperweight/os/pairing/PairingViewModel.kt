package com.paperweight.os.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.DeviceRedeemRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface PairingState {
    data object Scanning : PairingState
    data object Redeeming : PairingState
    data object Paired : PairingState
    data class Error(val message: String) : PairingState
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<PairingState>(PairingState.Scanning)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    // rawValue is pairUrl = "{baseUrl}/pair?pt={pairToken}" from
    // POST /api/dashboard/devices/pair (paperweightv1's src/api/dashboard.js).
    // We skip that browser-oriented /pair confirmation page entirely and
    // call the JSON redeem endpoint directly.
    fun onQrScanned(rawValue: String) {
        if (_state.value !is PairingState.Scanning) return

        val scannedUrl = rawValue.toHttpUrlOrNull()
        val pairToken = scannedUrl?.queryParameter("pt")
        if (scannedUrl == null || pairToken.isNullOrBlank()) {
            _state.value = PairingState.Error("That QR code isn't a Paperweight pairing code.")
            return
        }

        val baseUrl = scannedUrl.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()

        _state.value = PairingState.Redeeming
        viewModelScope.launch {
            apiClient.sessionStore.baseUrl = baseUrl
            try {
                val response = apiClient.auth.redeemDevice(DeviceRedeemRequest(pairToken))
                if (response.isSuccessful && response.body()?.ok == true) {
                    _state.value = PairingState.Paired
                } else {
                    apiClient.sessionStore.baseUrl = null
                    _state.value = PairingState.Error(
                        response.body()?.error ?: "Pairing code expired or already used."
                    )
                }
            } catch (e: Exception) {
                apiClient.sessionStore.baseUrl = null
                _state.value = PairingState.Error("Couldn't reach that station. Check the network and try again.")
            }
        }
    }

    fun retry() {
        _state.value = PairingState.Scanning
    }
}
