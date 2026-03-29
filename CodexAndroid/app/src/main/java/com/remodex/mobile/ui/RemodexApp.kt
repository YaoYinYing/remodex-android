package com.remodex.mobile.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.PairingPayload
import com.remodex.mobile.service.logging.AppLogger
import com.remodex.mobile.service.logging.LoggerLevel
import com.remodex.mobile.ui.parity.LoggerScreen
import com.remodex.mobile.ui.parity.OnboardingScreen
import com.remodex.mobile.ui.parity.PairingScreen
import com.remodex.mobile.ui.parity.PaywallScreen
import com.remodex.mobile.ui.parity.WorkspaceScreen
import com.remodex.mobile.ui.parity.WorkspaceSettingsScreen
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode
import com.remodex.mobile.ui.theme.RemodexTheme
import kotlinx.coroutines.launch

private const val UI_PREFS = "remodex.ui"
private const val PREF_HAS_SEEN_ONBOARDING = "hasSeenOnboarding"
private const val PREF_HAS_PRO_ACCESS = "hasProAccess"
private const val PREF_FONT_STYLE = "fontStyle"
private const val PREF_TONE_MODE = "toneMode"
private const val PREF_LOGGER_LEVEL = "loggerLevel"
private const val PREF_LOGGER_MAX_LINES = "loggerMaxLines"
private const val LOGGER_UNLOCK_TAP_COUNT = 7
private const val LOGGER_UNLOCK_WINDOW_MS = 4_000L

private enum class AppGate {
    ONBOARDING,
    PAYWALL,
    PAIRING,
    SETTINGS,
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
    var loggerLevelRaw by rememberSaveable {
        mutableStateOf(prefs.getString(PREF_LOGGER_LEVEL, LoggerLevel.INFO.name) ?: LoggerLevel.INFO.name)
    }
    var loggerMaxLines by rememberSaveable {
        mutableStateOf(prefs.getInt(PREF_LOGGER_MAX_LINES, 3_000).coerceIn(200, 20_000))
    }
    var showLoggerView by rememberSaveable { mutableStateOf(false) }
    var forcePairingView by rememberSaveable { mutableStateOf(false) }
    var showSettingsRoute by rememberSaveable { mutableStateOf(false) }
    val headerTapTimes = remember { ArrayDeque<Long>() }

    val fontStyle = AppFontStyle.fromStorage(fontStyleRaw)
    val toneMode = runCatching { AppToneMode.valueOf(toneModeRaw) }.getOrDefault(AppToneMode.SYSTEM)
    val loggerLevel = LoggerLevel.fromStorage(loggerLevelRaw)
    val loggerEntries by AppLogger.entries.collectAsState()
    val loggerSettings by AppLogger.settings.collectAsState()
    val initialPairing = remember(service) { service.currentPairing() }

    var relayUrl by rememberSaveable {
        mutableStateOf(initialPairing?.relayUrl ?: "ws://127.0.0.1:8765/relay")
    }
    var sessionId by rememberSaveable {
        mutableStateOf(initialPairing?.sessionId ?: "")
    }
    var macDeviceId by rememberSaveable {
        mutableStateOf(initialPairing?.macDeviceId ?: "")
    }
    var macIdentityPublicKey by rememberSaveable {
        mutableStateOf(initialPairing?.macIdentityPublicKey ?: "")
    }
    var expiresAt by rememberSaveable {
        mutableLongStateOf(initialPairing?.expiresAt ?: (System.currentTimeMillis() + 300_000L))
    }

    var checkoutBranch by rememberSaveable { mutableStateOf("") }
    var composerInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(hasSeenOnboarding, hasProAccess, fontStyleRaw, toneModeRaw, loggerLevelRaw, loggerMaxLines) {
        prefs.edit()
            .putBoolean(PREF_HAS_SEEN_ONBOARDING, hasSeenOnboarding)
            .putBoolean(PREF_HAS_PRO_ACCESS, hasProAccess)
            .putString(PREF_FONT_STYLE, fontStyleRaw)
            .putString(PREF_TONE_MODE, toneModeRaw)
            .putString(PREF_LOGGER_LEVEL, loggerLevelRaw)
            .putInt(PREF_LOGGER_MAX_LINES, loggerMaxLines)
            .apply()
    }

    LaunchedEffect(loggerLevel, loggerMaxLines) {
        AppLogger.configure(level = loggerLevel, maxLines = loggerMaxLines)
    }

    LaunchedEffect(connectionState) {
        service.currentPairing()?.let { pairing ->
            relayUrl = pairing.relayUrl
            sessionId = pairing.sessionId
            macDeviceId = pairing.macDeviceId
            macIdentityPublicKey = pairing.macIdentityPublicKey
            expiresAt = pairing.expiresAt
        }
    }

    val onHeaderTap: () -> Unit = {
        val now = System.currentTimeMillis()
        headerTapTimes.addLast(now)
        while (headerTapTimes.isNotEmpty() && now - headerTapTimes.first() > LOGGER_UNLOCK_WINDOW_MS) {
            headerTapTimes.removeFirst()
        }
        if (headerTapTimes.size >= LOGGER_UNLOCK_TAP_COUNT) {
            showLoggerView = true
            headerTapTimes.clear()
            AppLogger.info("RemodexApp", "Logger unlocked from hidden header gesture.")
        }
    }

    val hasSavedPairing = service.currentPairing() != null

    val gate = when {
        !hasSeenOnboarding -> AppGate.ONBOARDING
        !hasProAccess -> AppGate.PAYWALL
        connectionState == ConnectionState.Connected -> AppGate.WORKSPACE
        showSettingsRoute && hasSavedPairing -> AppGate.SETTINGS
        forcePairingView -> AppGate.PAIRING
        hasSavedPairing -> AppGate.WORKSPACE
        else -> AppGate.PAIRING
    }

    val effectiveToneMode = if (gate == AppGate.ONBOARDING) AppToneMode.FORCE_DARK else toneMode
    RemodexTheme(
        fontStyle = fontStyle,
        toneMode = effectiveToneMode
    ) {
        if (showLoggerView) {
            LoggerScreen(
                entries = loggerEntries,
                settings = loggerSettings,
                onClose = { showLoggerView = false },
                onClear = { AppLogger.clear() }
            )
        } else {
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
                            forcePairingView = false
                        },
                        onConnectLive = {
                            forcePairingView = false
                            scope.launch {
                                runCatching { service.connectLive() }
                            }
                        },
                        onScannedPairing = { scannedPayload ->
                            relayUrl = scannedPayload.relayUrl
                            sessionId = scannedPayload.sessionId
                            macDeviceId = scannedPayload.macDeviceId
                            macIdentityPublicKey = scannedPayload.macIdentityPublicKey
                            expiresAt = scannedPayload.expiresAt
                            forcePairingView = false
                            service.rememberPairing(scannedPayload)
                            scope.launch {
                                runCatching { service.connectLive() }
                            }
                        },
                        onHeaderTap = onHeaderTap
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
                        threads = threads,
                        selectedThreadId = selectedThreadId,
                        timeline = timeline,
                        composerInput = composerInput,
                        onComposerInputChange = { composerInput = it },
                        onOpenSettings = { showSettingsRoute = true },
                        onOpenPairing = { forcePairingView = true },
                        onHeaderTap = onHeaderTap
                    )
                }
                AppGate.SETTINGS -> {
                    WorkspaceSettingsScreen(
                        connectionState = connectionState,
                        status = status,
                        selectedModel = selectedModel,
                        availableModels = availableModels,
                        currentProjectPath = currentProjectPath
                            .takeUnless { it == "Project path not resolved." }
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        archivedThreadCount = threads.count { it.isArchived },
                        hasProAccess = hasProAccess,
                        trustedPairLabel = service.currentPairing()?.let { pairing ->
                            "Trusted Mac: ${pairing.macDeviceId} · ${pairing.relayUrl}"
                        },
                        notificationsEnabled = notificationsEnabled,
                        rateLimitInfo = rateLimitInfo,
                        ciStatus = ciStatus,
                        fontStyle = fontStyle,
                        toneMode = toneMode,
                        loggerLevel = loggerLevel,
                        loggerMaxLines = loggerMaxLines,
                        onClose = { showSettingsRoute = false },
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onFontStyleChanged = { style -> fontStyleRaw = style.storageValue },
                        onToneModeChanged = { mode -> toneModeRaw = mode.name },
                        onLoggerLevelChanged = { level -> loggerLevelRaw = level.name },
                        onLoggerMaxLinesChanged = { maxLines -> loggerMaxLines = maxLines.coerceIn(200, 20_000) },
                        onSwitchModel = { model ->
                            scope.launch { runCatching { service.switchModel(model) } }
                        },
                        onDisconnect = {
                            scope.launch { runCatching { service.disconnect() } }
                            showSettingsRoute = false
                        },
                        onForgetPair = {
                            service.forgetPairing()
                            showSettingsRoute = false
                        }
                    )
                }
            }
        }
    }
}
