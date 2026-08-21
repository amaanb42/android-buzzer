package com.amaanb.androidbuzzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amaanb.androidbuzzer.data.BuzzerCommand
import com.amaanb.androidbuzzer.data.DefaultBuzzerRepository
import com.amaanb.androidbuzzer.data.HttpBuzzerApi
import com.amaanb.androidbuzzer.ui.BuzzerScreen
import com.amaanb.androidbuzzer.ui.BuzzerUiEffect
import com.amaanb.androidbuzzer.ui.BuzzerViewModel
import com.amaanb.androidbuzzer.ui.theme.BuzzerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BuzzerViewModel by viewModels {
        BuzzerViewModel.Factory(DefaultBuzzerRepository(HttpBuzzerApi()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val darkTheme = isSystemInDarkTheme()
            BuzzerTheme(darkTheme = darkTheme) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val view = LocalView.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val snackbarHostState = remember { SnackbarHostState() }
                var permissionGranted by remember { mutableStateOf(hasLocalNetworkPermission()) }
                var permissionDenied by rememberSaveable { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    permissionGranted = granted
                    permissionDenied = !granted
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionGranted = hasLocalNetworkPermission()
                            if (permissionGranted) permissionDenied = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LifecycleStartEffect(permissionGranted) {
                    if (permissionGranted) viewModel.startPolling()
                    onStopOrDispose { viewModel.stopPolling() }
                }

                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is BuzzerUiEffect.CommandSucceeded -> {
                                view.performHapticFeedback(successHaptic(effect.command))
                            }

                            is BuzzerUiEffect.CommandFailed -> {
                                view.performHapticFeedback(failureHaptic())
                                snackbarHostState.showSnackbar(effect.message)
                            }
                        }
                    }
                }

                BuzzerScreen(
                    state = state,
                    hasLocalNetworkPermission = permissionGranted,
                    permissionPermanentlyDenied = permissionDenied,
                    onToggleRinging = viewModel::toggleRinging,
                    onRequestPermission = {
                        if (permissionDenied) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null),
                                ),
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        }
                    },
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    private fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED

    private fun successHaptic(command: BuzzerCommand): Int = when {
        Build.VERSION.SDK_INT >= 34 && command == BuzzerCommand.Ring ->
            HapticFeedbackConstants.TOGGLE_ON
        Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.TOGGLE_OFF
        Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
        else -> HapticFeedbackConstants.VIRTUAL_KEY
    }

    private fun failureHaptic(): Int = if (Build.VERSION.SDK_INT >= 30) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
}
