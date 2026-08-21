package com.amaanb.androidbuzzer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaanb.androidbuzzer.R
import com.amaanb.androidbuzzer.data.HttpBuzzerApi
import com.amaanb.androidbuzzer.ui.theme.BuzzerTheme
import com.amaanb.androidbuzzer.ui.theme.IdleBackgroundDark
import com.amaanb.androidbuzzer.ui.theme.IdleBackgroundLight
import com.amaanb.androidbuzzer.ui.theme.IdleButtonDark
import com.amaanb.androidbuzzer.ui.theme.IdleButtonLight
import com.amaanb.androidbuzzer.ui.theme.RingingBackgroundDark
import com.amaanb.androidbuzzer.ui.theme.RingingBackgroundLight
import com.amaanb.androidbuzzer.ui.theme.RingingButtonDark
import com.amaanb.androidbuzzer.ui.theme.RingingButtonLight

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BuzzerScreen(
    state: BuzzerUiState,
    hasLocalNetworkPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onToggleRinging: () -> Unit,
    onRequestPermission: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val targetBackground = when {
        state.ringing && darkTheme -> RingingBackgroundDark
        state.ringing -> RingingBackgroundLight
        darkTheme -> IdleBackgroundDark
        else -> IdleBackgroundLight
    }
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "ring state background",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        DecorativeCircles(darkTheme = darkTheme)

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            if (hasLocalNetworkPermission) {
                BuzzerControls(
                    state = state,
                    darkTheme = darkTheme,
                    onToggleRinging = onToggleRinging,
                    contentPadding = contentPadding,
                )
            } else {
                PermissionRequired(
                    permanentlyDenied = permissionPermanentlyDenied,
                    onRequestPermission = onRequestPermission,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BuzzerControls(
    state: BuzzerUiState,
    darkTheme: Boolean,
    onToggleRinging: () -> Unit,
    contentPadding: PaddingValues,
) {
    val isReady = state.connection == ConnectionState.Connected
    val enabledButtonContainer = when {
        state.ringing && darkTheme -> RingingButtonDark
        state.ringing -> RingingButtonLight
        darkTheme -> IdleButtonDark
        else -> IdleButtonLight
    }
    val enabledButtonContent = if (darkTheme) {
        if (state.ringing) Color(0xFF062E6F) else Color(0xFF690005)
    } else {
        Color.White
    }
    val targetButtonContainer = if (isReady) {
        enabledButtonContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    }
    val targetButtonContent = if (isReady) {
        enabledButtonContent
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
    }
    val buttonContainer by animateColorAsState(
        targetValue = targetButtonContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "buzzer button container",
    )
    val buttonContent by animateColorAsState(
        targetValue = targetButtonContent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "buzzer button content",
    )
    val stateLabel = if (state.ringing) "Ringing" else "Ready"

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val heroSize = (maxWidth * 0.58f).coerceIn(176.dp, 224.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "BuzZaki",
                    color = contentColor(darkTheme),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                ConnectionPill(state.connection)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = isReady,
                    enter = fadeIn(
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    ) + expandVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        expandFrom = Alignment.CenterVertically,
                    ),
                    exit = fadeOut(
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ) + shrinkVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        shrinkTowards = Alignment.CenterVertically,
                    ),
                    label = "buzzer state label",
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stateLabel,
                            color = contentColor(darkTheme),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
                Button(
                    onClick = onToggleRinging,
                    enabled = isReady,
                    modifier = Modifier
                        .size(heroSize)
                        .semantics {
                            stateDescription = if (state.ringing) {
                                "The bedroom buzzer is ringing"
                            } else {
                                "The bedroom buzzer is stopped"
                            }
                        },
                    shapes = ButtonDefaults.shapes(
                        shape = RoundedCornerShape(64.dp),
                        pressedShape = CircleShape,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainer,
                        contentColor = buttonContent,
                        disabledContainerColor = buttonContainer,
                        disabledContentColor = buttonContent,
                    ),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = state.ringing,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "ring button content",
                        ) { ringing ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (ringing) {
                                        Icons.Rounded.StopCircle
                                    } else {
                                        Icons.Rounded.NotificationsActive
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(68.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = if (ringing) "Stop" else "Ring",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                        if (state.commandInFlight) {
                            Spacer(Modifier.height(8.dp))
                            LoadingIndicator(
                                modifier = Modifier.size(28.dp),
                                color = buttonContent,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Updating…", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = state.acknowledgementVisible,
                    enter = fadeIn(
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    ) + expandVertically(
                        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                        expandFrom = Alignment.Top,
                    ),
                    exit = fadeOut(
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ) + shrinkVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        shrinkTowards = Alignment.Top,
                    ),
                    label = "acknowledgement",
                ) {
                    Column(
                        modifier = Modifier.padding(top = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.zaki_acknowledged),
                            contentDescription = "Zaki",
                            modifier = Modifier
                                .size(132.dp)
                                .clip(RoundedCornerShape(32.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.buzzer_acknowledged),
                            color = contentColor(darkTheme),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            Text(
                text = HttpBuzzerApi.DEFAULT_BASE_URL.removePrefix("http://"),
                modifier = Modifier
                    .padding(top = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = contentColor(darkTheme).copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ConnectionPill(connection: ConnectionState) {
    val (label, icon) = when (connection) {
        ConnectionState.Checking -> "Connecting" to Icons.Rounded.WifiFind
        ConnectionState.Connected -> "Connected" to Icons.Rounded.Wifi
        ConnectionState.Offline -> "Offline" to Icons.Rounded.WifiOff
    }
    val container = when (connection) {
        ConnectionState.Checking -> MaterialTheme.colorScheme.surfaceVariant
        ConnectionState.Connected -> MaterialTheme.colorScheme.tertiaryContainer
        ConnectionState.Offline -> MaterialTheme.colorScheme.errorContainer
    }
    val foreground = when (connection) {
        ConnectionState.Checking -> MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Connected -> MaterialTheme.colorScheme.onTertiaryContainer
        ConnectionState.Offline -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(color = container, contentColor = foreground, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PermissionRequired(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (permanentlyDenied) Icons.Rounded.ErrorOutline else Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Local access needed",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (permanentlyDenied) {
                        "Allow local network access in Android settings to reach the buzzer."
                    } else {
                        "Buzzer only connects to 192.168.50.50 on your Wi-Fi network."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRequestPermission) {
                    Text(if (permanentlyDenied) "Open settings" else "Allow access")
                }
            }
        }
    }
}

@Composable
private fun DecorativeCircles(darkTheme: Boolean) {
    val tint = contentColor(darkTheme)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp)
                .size(150.dp)
                .alpha(0.06f)
                .background(tint, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 90.dp)
                .size(108.dp)
                .alpha(0.05f)
                .background(tint, RoundedCornerShape(32.dp)),
        )
    }
}

private fun contentColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFFF9E5E2) else Color(0xFF251918)

@Preview(showBackground = true)
@Composable
private fun IdlePreview() {
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

@Preview(showBackground = true)
@Composable
private fun RingingPreview() {
    BuzzerTheme(darkTheme = false) {
        BuzzerScreen(
            state = BuzzerUiState(ringing = true, connection = ConnectionState.Connected),
            hasLocalNetworkPermission = true,
            permissionPermanentlyDenied = false,
            onToggleRinging = {},
            onRequestPermission = {},
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DarkPermissionPreview() {
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
