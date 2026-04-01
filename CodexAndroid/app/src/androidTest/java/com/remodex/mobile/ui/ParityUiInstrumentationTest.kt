package com.remodex.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.remodex.mobile.model.RpcMessage
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.PairingPayload
import com.remodex.mobile.service.transport.ScriptedRpcTransport
import com.remodex.mobile.ui.parity.OnboardingScreen
import com.remodex.mobile.ui.parity.PairingScreen
import com.remodex.mobile.ui.parity.TodoRow
import com.remodex.mobile.ui.parity.WebsiteFeatureTodos
import com.remodex.mobile.ui.parity.WorkspaceScreen
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode
import com.remodex.mobile.ui.theme.RemodexTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        composeRule.onNodeWithText("Control Codex from your Android.").assertIsDisplayed()
        composeRule.onNodeWithText("Get Started").assertIsDisplayed()
        composeRule.onNodeWithText("End-to-end encrypted").assertIsDisplayed()
        composeRule.onNodeWithText("Open source on GitHub").assertIsDisplayed()
    }

    @Test
    fun pairingScreenShowsConnectionActions() {
        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_LIGHT) {
                PairingScreen(
                    connectionState = ConnectionState.Paired,
                    status = "Pairing saved.",
                    relayUrl = "ws://127.0.0.1:9000/relay",
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
                    onConnectLive = {},
                    onScannedPairing = {},
                    scannerEnabled = false,
                    onHeaderTap = {}
                )
            }
        }
        composeRule.onNodeWithText("Scan QR").assertIsDisplayed()
        composeRule.onNodeWithText("Enter Details Manually").assertIsDisplayed()
    }

    @Test
    fun workspaceDrawerAndRefreshControlsAreInteractive() {
        val service = CodexService()
        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_LIGHT) {
                WorkspaceScreen(
                    service = service,
                    connectionState = ConnectionState.Connected,
                    status = "Connected",
                    currentProjectPath = "/Users/yyy/Documents/protein_design/remodex",
                    availableModels = listOf("gpt-5.4"),
                    selectedModel = "gpt-5.4",
                    availableReasoningEfforts = listOf("medium"),
                    selectedReasoningEffort = "medium",
                    pendingPermissions = emptyList(),
                    rateLimitInfo = "Rate limit: 177/200",
                    ciStatus = "CI status: running",
                    gitStatusSummary = "Branch main clean",
                    gitBranches = listOf("main"),
                    checkoutBranch = "main",
                    onCheckoutBranchChange = {},
                    threads = emptyList(),
                    selectedThreadId = null,
                    timeline = emptyList(),
                    composerInput = "",
                    onComposerInputChange = {},
                    onSwitchModel = {},
                    onSwitchReasoningEffort = {},
                    onOpenSettings = {},
                    onOpenPairing = {},
                    onHeaderTap = {}
                )
            }
        }

        composeRule.onNodeWithText("↻").assertIsDisplayed()
        composeRule.onNodeWithText("☰").performClick()
        composeRule.onNodeWithText("Chats").assertIsDisplayed()
    }

    @Test
    fun workspaceSettingsRouteOpensFromDrawer() {
        val service = CodexService()
        var openSettingsInvoked = false

        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_LIGHT) {
                WorkspaceScreen(
                    service = service,
                    connectionState = ConnectionState.Connected,
                    status = "Connected",
                    currentProjectPath = "/Users/yyy/Documents/protein_design/remodex",
                    availableModels = listOf("gpt-5.4"),
                    selectedModel = "gpt-5.4",
                    availableReasoningEfforts = listOf("medium"),
                    selectedReasoningEffort = "medium",
                    pendingPermissions = emptyList(),
                    rateLimitInfo = "Rate limit: 177/200",
                    ciStatus = "CI status: running",
                    gitStatusSummary = "Branch main clean",
                    gitBranches = listOf("main"),
                    checkoutBranch = "main",
                    onCheckoutBranchChange = {},
                    threads = emptyList(),
                    selectedThreadId = null,
                    timeline = emptyList(),
                    composerInput = "",
                    onComposerInputChange = {},
                    onSwitchModel = {},
                    onSwitchReasoningEffort = {},
                    onOpenSettings = { openSettingsInvoked = true },
                    onOpenPairing = {},
                    onHeaderTap = {}
                )
            }
        }

        composeRule.onNodeWithText("☰").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.runOnIdle {
            assertTrue(openSettingsInvoked)
        }
    }

    @Test
    fun scriptedMockWorkflowSendsComposerTurnAndRendersAssistantReply() {
        runBlocking {
            val service = scriptedConnectedService()

            composeRule.setContent {
                RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_DARK) {
                    ScriptedWorkspaceHarness(service = service)
                }
            }

            composeRule.onNodeWithText("Up").performClick()
            composeRule.waitUntil(timeoutMillis = 6_000L) {
                service.timeline.value.any { it.text.contains("Scripted emulator assistant reply") }
            }
            composeRule.onNodeWithText("Scripted emulator assistant reply.").assertIsDisplayed()
        }
    }

    @Test
    fun parityTodoRowShowsTheFirstGateEntry() {
        val todo = WebsiteFeatureTodos.first()

        composeRule.setContent {
            RemodexTheme(fontStyle = AppFontStyle.GEIST, toneMode = AppToneMode.FORCE_LIGHT) {
                TodoRow(todo = todo, state = todo.defaultState)
            }
        }

        composeRule.onNodeWithText(todo.title).assertIsDisplayed()
        composeRule.onNodeWithText(todo.defaultState.name.replace('_', ' ')).assertIsDisplayed()
        composeRule.onNodeWithText(todo.detail).assertIsDisplayed()
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

    @Composable
    private fun ScriptedWorkspaceHarness(service: CodexService) {
        val threads by service.threads.collectAsState()
        val selectedThreadId by service.selectedThreadId.collectAsState()
        val timeline by service.timeline.collectAsState()
        val status by service.status.collectAsState()
        val gitStatusSummary by service.gitStatusSummary.collectAsState()
        val gitBranches by service.gitBranches.collectAsState()
        val currentProjectPath by service.currentProjectPath.collectAsState()
        val availableModels by service.availableModels.collectAsState()
        val selectedModel by service.selectedModel.collectAsState()
        val availableReasoningEfforts by service.availableReasoningEfforts.collectAsState()
        val selectedReasoningEffort by service.selectedReasoningEffort.collectAsState()
        val pendingPermissions by service.pendingPermissions.collectAsState()
        val rateLimitInfo by service.rateLimitInfo.collectAsState()
        val ciStatus by service.ciStatus.collectAsState()
        var checkoutBranch by remember { mutableStateOf("android") }
        var composerInput by remember { mutableStateOf("Inject mocked Codex response from emulator test.") }

        WorkspaceScreen(
            service = service,
            connectionState = ConnectionState.Connected,
            status = status,
            currentProjectPath = currentProjectPath,
            availableModels = availableModels,
            selectedModel = selectedModel,
            availableReasoningEfforts = availableReasoningEfforts,
            selectedReasoningEffort = selectedReasoningEffort,
            pendingPermissions = pendingPermissions,
            rateLimitInfo = rateLimitInfo,
            ciStatus = ciStatus,
            gitStatusSummary = gitStatusSummary,
            gitBranches = gitBranches,
            checkoutBranch = checkoutBranch,
            onCheckoutBranchChange = { checkoutBranch = it },
            threads = threads,
            selectedThreadId = selectedThreadId,
            timeline = timeline,
            composerInput = composerInput,
            onComposerInputChange = { composerInput = it },
            onSwitchModel = {},
            onSwitchReasoningEffort = {},
            onOpenSettings = {},
            onOpenPairing = {},
            onHeaderTap = {}
        )
    }

    private fun scriptedConnectedService(): CodexService = runBlocking {
        val scripted = ScriptedRpcTransport()
        scripted.enqueue(
            method = "initialize",
            response = RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("init"),
                result = JsonObject(emptyMap())
            )
        )
        scripted.enqueue(
            method = "thread/list",
            response = RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("thread-list-live"),
                result = JsonObject(
                    mapOf(
                        "data" to JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive("thread-scripted-ui"),
                                        "title" to JsonPrimitive("Scripted UI thread"),
                                        "preview" to JsonPrimitive("Mocked timeline"),
                                        "cwd" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex"),
                                        "updated_at" to JsonPrimitive(1_710_000_555_000L)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        scripted.enqueue(
            method = "thread/list",
            response = RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("thread-list-archived"),
                result = JsonObject(mapOf("data" to JsonArray(emptyList())))
            )
        )
        scripted.enqueue(
            method = "git/status",
            response = RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("git-status"),
                result = JsonObject(
                    mapOf(
                        "status" to JsonObject(
                            mapOf(
                                "branch" to JsonPrimitive("android"),
                                "repoRoot" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex"),
                                "ahead" to JsonPrimitive(0),
                                "behind" to JsonPrimitive(0),
                                "stagedCount" to JsonPrimitive(0),
                                "unstagedCount" to JsonPrimitive(0),
                                "untrackedCount" to JsonPrimitive(0)
                            )
                        )
                    )
                )
            )
        )
        scripted.enqueue(
            method = "git/branches",
            response = RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("git-branches"),
                result = JsonObject(
                    mapOf(
                        "branches" to JsonArray(
                            listOf(
                                JsonPrimitive("android"),
                                JsonPrimitive("main")
                            )
                        )
                    )
                )
            )
        )
        scripted.enqueue(
            method = "turn/start",
            response = RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("turn-start"),
                result = JsonObject(
                    mapOf(
                        "turnId" to JsonPrimitive("turn-scripted-ui-1")
                    )
                )
            ),
            serverMessages = listOf(
                RpcMessage(
                    jsonrpc = "2.0",
                    method = "turn/started",
                    params = JsonObject(
                        mapOf(
                            "threadId" to JsonPrimitive("thread-scripted-ui"),
                            "turnId" to JsonPrimitive("turn-scripted-ui-1")
                        )
                    )
                )
            )
        )
        scripted.enqueue(
            method = "thread/read",
            response = RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("thread-read"),
                result = JsonObject(
                    mapOf(
                        "thread" to JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("thread-scripted-ui"),
                                "cwd" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex"),
                                "turns" to JsonArray(
                                    listOf(
                                        JsonObject(
                                            mapOf(
                                                "id" to JsonPrimitive("turn-scripted-ui-1"),
                                                "status" to JsonPrimitive("completed"),
                                                "items" to JsonArray(
                                                    listOf(
                                                        JsonObject(
                                                            mapOf(
                                                                "id" to JsonPrimitive("item-user"),
                                                                "type" to JsonPrimitive("user_message"),
                                                                "content" to JsonArray(
                                                                    listOf(
                                                                        JsonObject(
                                                                            mapOf(
                                                                                "type" to JsonPrimitive("text"),
                                                                                "text" to JsonPrimitive("Inject mocked Codex response from emulator test.")
                                                                            )
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        ),
                                                        JsonObject(
                                                            mapOf(
                                                                "id" to JsonPrimitive("item-assistant"),
                                                                "type" to JsonPrimitive("agent_message"),
                                                                "content" to JsonArray(
                                                                    listOf(
                                                                        JsonObject(
                                                                            mapOf(
                                                                                "type" to JsonPrimitive("text"),
                                                                                "text" to JsonPrimitive("Scripted emulator assistant reply.")
                                                                            )
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val service = CodexService(fixtureTransportFactory = { scripted })
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-scripted-ui",
                relayUrl = "ws://127.0.0.1:9000/relay",
                macDeviceId = "mac-scripted-ui",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXk=",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()
        return@runBlocking service
    }
}
