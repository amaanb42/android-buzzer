package com.amaanb.androidbuzzer.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.amaanb.androidbuzzer.ui.theme.BuzzerTheme
import org.junit.Rule
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
