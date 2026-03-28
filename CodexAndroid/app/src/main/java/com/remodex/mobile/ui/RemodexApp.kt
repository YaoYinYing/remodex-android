package com.remodex.mobile.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.PairingPayload
import com.remodex.mobile.service.ServiceEvent
import com.remodex.mobile.ui.parity.OnboardingScreen
import com.remodex.mobile.ui.parity.PairingScreen
import com.remodex.mobile.ui.parity.PaywallScreen
import com.remodex.mobile.ui.parity.TodoState
import com.remodex.mobile.ui.parity.WebsiteFeatureTodos
import com.remodex.mobile.ui.parity.WorkspaceScreen
import com.remodex.mobile.ui.parity.advanceNextTodo
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode
import com.remodex.mobile.ui.theme.RemodexTheme
import kotlinx.coroutines.launch

private const val UI_PREFS = "remodex.ui"
private const val PREF_HAS_SEEN_ONBOARDING = "hasSeenOnboarding"
private const val PREF_HAS_PRO_ACCESS = "hasProAccess"
private const val PREF_FONT_STYLE = "fontStyle"
private const val PREF_TONE_MODE = "toneMode"

private enum class AppGate {
    ONBOARDING,
    PAYWALL,
    PAIRING,
    WORKSPACE
}

@Composable
fun RemodexApp(
    service: CodexService = remember { CodexService() },
    notificationsEnabled: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }

    val connectionState by service.connectionState.collectAsState()
    val threads by service.threads.collectAsState()
    val selectedThreadId by service.selectedThreadId.collectAsState()
    val timeline by service.timeline.collectAsState()
    val gitStatusSummary by service.gitStatusSummary.collectAsState()
    val gitBranches by service.gitBranches.collectAsState()
    val currentProjectPath by service.currentProjectPath.collectAsState()
    val availableModels by service.availableModels.collectAsState()
    val selectedModel by service.selectedModel.collectAsState()
    val pendingPermissions by service.pendingPermissions.collectAsState()
    val rateLimitInfo by service.rateLimitInfo.collectAsState()
    val ciStatus by service.ciStatus.collectAsState()
    val status by service.status.collectAsState()
    val scope = rememberCoroutineScope()

    var hasSeenOnboarding by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PREF_HAS_SEEN_ONBOARDING, false))
    }
    var hasProAccess by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PREF_HAS_PRO_ACCESS, false))
    }
    var fontStyleRaw by rememberSaveable {
        mutableStateOf(prefs.getString(PREF_FONT_STYLE, AppFontStyle.GEIST.storageValue) ?: AppFontStyle.GEIST.storageValue)
    }
    var toneModeRaw by rememberSaveable {
        mutableStateOf(prefs.getString(PREF_TONE_MODE, AppToneMode.SYSTEM.name) ?: AppToneMode.SYSTEM.name)
    }

    val fontStyle = AppFontStyle.fromStorage(fontStyleRaw)
    val toneMode = runCatching { AppToneMode.valueOf(toneModeRaw) }.getOrDefault(AppToneMode.SYSTEM)

    var relayUrl by rememberSaveable { mutableStateOf("ws://127.0.0.1:8765/relay") }
    var sessionId by rememberSaveable { mutableStateOf("session-demo-android") }
    var macDeviceId by rememberSaveable { mutableStateOf("mac-demo") }
    var macIdentityPublicKey by rememberSaveable { mutableStateOf("bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==") }
    var expiresAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() + 300_000L) }

    var pushToken by rememberSaveable { mutableStateOf("") }
    var checkoutBranch by rememberSaveable { mutableStateOf("") }
    var commitMessage by rememberSaveable { mutableStateOf("") }
    var composerInput by rememberSaveable { mutableStateOf("") }
    var manualPermissionId by rememberSaveable { mutableStateOf("") }

    val eventLog = remember { mutableStateListOf<ServiceEvent>() }
    val todoStates = remember { mutableStateMapOf<String, TodoState>() }

    LaunchedEffect(Unit) {
        if (todoStates.isEmpty()) {
            WebsiteFeatureTodos.forEach { todo ->
                todoStates[todo.id] = when (todo.id) {
                    "pairing", "workspace", "git", "refresh-notify" -> TodoState.DONE
                    "task-steering" -> TodoState.IN_PROGRESS
                    else -> TodoState.TODO
                }
            }
        }
    }

    LaunchedEffect(service) {
        service.events.collect { event ->
            eventLog.add(0, event)
            while (eventLog.size > 40) {
                eventLog.removeAt(eventLog.lastIndex)
            }
        }
    }

    LaunchedEffect(hasSeenOnboarding, hasProAccess, fontStyleRaw, toneModeRaw) {
        prefs.edit()
            .putBoolean(PREF_HAS_SEEN_ONBOARDING, hasSeenOnboarding)
            .putBoolean(PREF_HAS_PRO_ACCESS, hasProAccess)
            .putString(PREF_FONT_STYLE, fontStyleRaw)
            .putString(PREF_TONE_MODE, toneModeRaw)
            .apply()
    }

    val gate = when {
        !hasSeenOnboarding -> AppGate.ONBOARDING
        !hasProAccess -> AppGate.PAYWALL
        connectionState == ConnectionState.Connected -> AppGate.WORKSPACE
        else -> AppGate.PAIRING
    }

    val effectiveToneMode = if (gate == AppGate.ONBOARDING) AppToneMode.FORCE_DARK else toneMode
    RemodexTheme(
        fontStyle = fontStyle,
        toneMode = effectiveToneMode
    ) {
        when (gate) {
            AppGate.ONBOARDING -> {
                OnboardingScreen(
                    onContinue = { hasSeenOnboarding = true }
                )
            }
            AppGate.PAYWALL -> {
                PaywallScreen(
                    onUnlock = { hasProAccess = true },
                    onRestore = { hasProAccess = true }
                )
            }
            AppGate.PAIRING -> {
                PairingScreen(
                    connectionState = connectionState,
                    status = status,
                    relayUrl = relayUrl,
                    onRelayUrlChange = { relayUrl = it },
                    sessionId = sessionId,
                    onSessionIdChange = { sessionId = it },
                    macDeviceId = macDeviceId,
                    onMacDeviceIdChange = { macDeviceId = it },
                    macIdentityPublicKey = macIdentityPublicKey,
                    onMacIdentityPublicKeyChange = { macIdentityPublicKey = it },
                    notificationsEnabled = notificationsEnabled,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRememberPairing = {
                        service.rememberPairing(
                            PairingPayload(
                                sessionId = sessionId.trim(),
                                relayUrl = relayUrl.trim(),
                                macDeviceId = macDeviceId.trim(),
                                macIdentityPublicKey = macIdentityPublicKey.trim(),
                                expiresAt = expiresAt
                            )
                        )
                    },
                    onConnectDemo = {
                        scope.launch {
                            runCatching { service.connectWithFixture() }
                        }
                    },
                    onConnectLive = {
                        scope.launch {
                            runCatching { service.connectLive() }
                        }
                    }
                )
            }
            AppGate.WORKSPACE -> {
                WorkspaceScreen(
                    service = service,
                    connectionState = connectionState,
                    status = status,
                    currentProjectPath = currentProjectPath,
                    availableModels = availableModels,
                    selectedModel = selectedModel,
                    pendingPermissions = pendingPermissions,
                    rateLimitInfo = rateLimitInfo,
                    ciStatus = ciStatus,
                    gitStatusSummary = gitStatusSummary,
                    gitBranches = gitBranches,
                    checkoutBranch = checkoutBranch,
                    onCheckoutBranchChange = { checkoutBranch = it },
                    commitMessage = commitMessage,
                    onCommitMessageChange = { commitMessage = it },
                    pushToken = pushToken,
                    onPushTokenChange = { pushToken = it },
                    manualPermissionId = manualPermissionId,
                    onManualPermissionIdChange = { manualPermissionId = it },
                    threads = threads,
                    selectedThreadId = selectedThreadId,
                    timeline = timeline,
                    composerInput = composerInput,
                    onComposerInputChange = { composerInput = it },
                    notificationsEnabled = notificationsEnabled,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    eventLog = eventLog,
                    todos = WebsiteFeatureTodos,
                    todoStates = todoStates,
                    onAdvanceTodo = { advanceNextTodo(todoStates) },
                    fontStyle = fontStyle,
                    toneMode = toneMode,
                    onFontStyleChanged = { style -> fontStyleRaw = style.storageValue },
                    onToneModeChanged = { mode -> toneModeRaw = mode.name }
                )
            }
        }
    }
}
