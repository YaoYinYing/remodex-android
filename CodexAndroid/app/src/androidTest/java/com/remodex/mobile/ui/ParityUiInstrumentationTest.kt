package com.remodex.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.ui.parity.OnboardingScreen
import com.remodex.mobile.ui.parity.PairingScreen
import com.remodex.mobile.ui.parity.TodoState
import com.remodex.mobile.ui.parity.WebsiteFeatureTodos
import com.remodex.mobile.ui.parity.WorkspaceScreen
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode
import com.remodex.mobile.ui.theme.RemodexTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParityUiInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onboardingScreenShowsInstallCommands() {
        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_DARK) {
                OnboardingScreen(onContinue = {})
            }
        }
        composeRule.onNodeWithText("npm install -g @openai/codex@latest").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to Pairing").assertIsDisplayed()
    }

    @Test
    fun pairingScreenShowsConnectionActions() {
        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_LIGHT) {
                PairingScreen(
                    connectionState = ConnectionState.Paired,
                    status = "Pairing saved.",
                    relayUrl = "ws://127.0.0.1:8765/relay",
                    onRelayUrlChange = {},
                    sessionId = "session-id",
                    onSessionIdChange = {},
                    macDeviceId = "mac-id",
                    onMacDeviceIdChange = {},
                    macIdentityPublicKey = "key",
                    onMacIdentityPublicKeyChange = {},
                    notificationsEnabled = true,
                    onRequestNotificationPermission = {},
                    onRememberPairing = {},
                    onConnectDemo = {},
                    onConnectLive = {}
                )
            }
        }
        composeRule.onNodeWithText("Remember Pair").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Live").assertIsDisplayed()
    }

    @Test
    fun workspaceDrawerAndRefreshStripAreInteractive() {
        val service = CodexService()
        val todoStates = WebsiteFeatureTodos.associate { it.id to TodoState.TODO }.toMutableMap()

        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_LIGHT) {
                WorkspaceScreen(
                    service = service,
                    connectionState = ConnectionState.Connected,
                    status = "Connected",
                    currentProjectPath = "/Users/yyy/Documents/protein_design/remodex",
                    availableModels = listOf("gpt-5.4"),
                    selectedModel = "gpt-5.4",
                    pendingPermissions = emptyList(),
                    rateLimitInfo = "Rate limit: 177/200",
                    ciStatus = "CI status: running",
                    gitStatusSummary = "Branch main clean",
                    gitBranches = listOf("main"),
                    checkoutBranch = "",
                    onCheckoutBranchChange = {},
                    commitMessage = "",
                    onCommitMessageChange = {},
                    pushToken = "",
                    onPushTokenChange = {},
                    manualPermissionId = "",
                    onManualPermissionIdChange = {},
                    threads = emptyList(),
                    selectedThreadId = null,
                    timeline = emptyList(),
                    composerInput = "",
                    onComposerInputChange = {},
                    notificationsEnabled = true,
                    onRequestNotificationPermission = {},
                    eventLog = emptyList(),
                    todos = WebsiteFeatureTodos,
                    todoStates = todoStates,
                    onAdvanceTodo = {},
                    fontStyle = AppFontStyle.GEIST,
                    toneMode = AppToneMode.FORCE_LIGHT,
                    onFontStyleChanged = {},
                    onToneModeChanged = {}
                )
            }
        }

        composeRule.onNodeWithTag("workspace_refresh_strip").assertIsDisplayed()
        composeRule.onNodeWithText("Menu").performClick()
        composeRule.onNodeWithText("Sidebar").assertIsDisplayed()
    }

    @Test
    fun rootSnapshotCaptureProducesStableNonEmptyFrame() {
        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_DARK) {
                OnboardingScreen(onContinue = {})
            }
        }
        val image = composeRule.onRoot().captureToImage()
        val pixels = image.toPixelMap()
        var luminanceTotal = 0.0
        for (x in 0 until pixels.width step maxOf(1, pixels.width / 16)) {
            for (y in 0 until pixels.height step maxOf(1, pixels.height / 16)) {
                val p = pixels[x, y]
                luminanceTotal += (p.red * 0.2126 + p.green * 0.7152 + p.blue * 0.0722).toDouble()
            }
        }
        assertTrue(image.width > 0 && image.height > 0)
        assertTrue("snapshot luminance should be non-zero", luminanceTotal > 0.01)
    }
}
