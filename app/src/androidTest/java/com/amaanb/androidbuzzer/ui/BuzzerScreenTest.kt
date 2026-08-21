package com.amaanb.androidbuzzer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amaanb.androidbuzzer.data.BuzzerCommand
import com.amaanb.androidbuzzer.ui.theme.BuzzerTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
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

        composeRule.onNodeWithText("Idle").assertIsDisplayed()
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
    fun checkingStateShowsLabelAndAcceptsButtonInput() {
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

        composeRule.onNodeWithText("Idle").assertIsDisplayed()
        composeRule.onNodeWithText("Ring").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(toggled) }
    }

    @Test
    fun offlineStateShowsRingingLabelAndKeepsButtonEnabled() {
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

        composeRule.onNodeWithText("Ringing").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
    }

    @Test
    fun pendingCommandShowsReconnectMessage() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(
                        ringing = true,
                        connection = ConnectionState.Offline,
                        pendingCommand = BuzzerCommand.Ring,
                    ),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText(
            "Command will be sent once connection is established.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Ringing").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsEnabled()
    }

    @Test
    fun timeoutStateShowsTimeoutMessage() {
        composeRule.setContent {
            BuzzerTheme(darkTheme = false) {
                BuzzerScreen(
                    state = BuzzerUiState(
                        connection = ConnectionState.Connected,
                        timeoutVisible = true,
                    ),
                    hasLocalNetworkPermission = true,
                    permissionPermanentlyDenied = false,
                    onToggleRinging = {},
                    onRequestPermission = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeRule.onNodeWithText(
            "Ringing was never acknowledged after 2.5 minutes and the buzzer has timed out. " +
                "Press the Ring button to re-activate the buzzer.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Idle").assertIsDisplayed()
        composeRule.onNodeWithText("Ring").assertIsEnabled()
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
