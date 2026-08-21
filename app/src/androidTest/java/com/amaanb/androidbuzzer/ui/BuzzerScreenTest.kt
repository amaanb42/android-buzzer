package com.amaanb.androidbuzzer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amaanb.androidbuzzer.ui.theme.BuzzerTheme
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Test

class BuzzerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleStateShowsRingControl() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(connection = ConnectionState.Connected),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Ready").assertIsDisplayed()
        composeRule.onNodeWithText("Ring").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Connected").assertTextEquals("Connected")
    }

    @Test
    fun ringingStateShowsStopControl() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(
                        ringing = true,
                        connection = ConnectionState.Connected,
                    ),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Ringing").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun pendingStateKeepsLatestControlEnabled() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(
                        ringing = true,
                        connection = ConnectionState.Connected,
                        commandInFlight = true,
                    ),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Stop").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithContentDescription("Updating buzzer").assertIsDisplayed()
        composeRule.onNodeWithText("Updating…").assertDoesNotExist()
    }

    @Test
    fun checkingStateHidesLabelAndRejectsButtonInput() {
        var toggled = false
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(connection = ConnectionState.Checking),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = { toggled = true },
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Ready").assertDoesNotExist()
        composeRule.onNodeWithText("Ringing").assertDoesNotExist()
        composeRule.onNodeWithText("Ring").assertIsDisplayed().assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertFalse(toggled) }
    }

    @Test
    fun offlineStateHidesStaleRingingLabelAndDisablesButton() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(
                        ringing = true,
                        connection = ConnectionState.Offline,
                    ),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Ready").assertDoesNotExist()
        composeRule.onNodeWithText("Ringing").assertDoesNotExist()
        composeRule.onNodeWithText("Stop").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
    }

    @Test
    fun readyLabelUsesAnimatedVisibilityWhenConnectionChanges() {
        composeRule.mainClock.autoAdvance = false
        var state by mutableStateOf(BuzzerUiState(connection = ConnectionState.Checking))
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = state,
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Ready").assertDoesNotExist()

        composeRule.runOnIdle {
            state = BuzzerUiState(connection = ConnectionState.Connected)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Ready").assertExists()
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.onNodeWithText("Ready").assertIsDisplayed()

        composeRule.runOnIdle {
            state = BuzzerUiState(connection = ConnectionState.Checking)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Ready").assertExists()
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.onNodeWithText("Ready").assertDoesNotExist()
    }

    @Test
    fun acknowledgedStateShowsZakiBelowTheButton() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(
                        connection = ConnectionState.Connected,
                        acknowledgementVisible = true,
                    ),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Zaki acknowledged buzzer.").assertIsDisplayed()
    }

    @Test
    fun missingPermissionShowsPermissionExplanation() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(),
                    hasLocalNetworkPermission = false,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Local access needed").assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").assertIsDisplayed()
    }

    @Test
    fun missingPermissionIsReadableInDarkTheme() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = true) {
                BuzzerScreen(
                    state = BuzzerUiState(),
                    hasLocalNetworkPermission = false,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText("Local access needed").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Buzzer only connects to 192.168.50.50 on your Wi-Fi network.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").assertIsDisplayed().assertIsEnabled()
    }
}
