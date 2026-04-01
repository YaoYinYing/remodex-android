package com.remodex.mobile.service

import com.remodex.mobile.service.push.PushRegistrationPayload
import com.remodex.mobile.model.normalizeFilesystemProjectPath
import com.remodex.mobile.ui.parity.ParityAcceptanceMatrix
import com.remodex.mobile.ui.parity.TodoState
import com.remodex.mobile.ui.parity.WebsiteFeatureTodos
import com.remodex.mobile.ui.parity.advanceNextTodo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexServiceTest {
    @Test
    fun connectWithFixtureLoadsThreadsAndTimeline() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )

        service.connectWithFixture()
        service.refreshActiveThreadTimeline(silentStatus = true)

        assertEquals(ConnectionState.Connected, service.connectionState.value)
        assertTrue(service.threads.value.isNotEmpty())
        assertTrue(service.timeline.value.isNotEmpty())
    }

    @Test
    fun sendTurnStartAppendsTimelineItemsInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        val beforeSize = service.timeline.value.size
        service.sendTurnStart("Validate composer parity path.")
        val afterTimeline = service.timeline.value

        assertTrue(afterTimeline.size > beforeSize)
        assertTrue(afterTimeline.any { it.text.contains("Validate composer parity path.") })
        assertTrue(afterTimeline.any { it.text.contains("Fixture reply") })
    }

    @Test
    fun sendTurnStartAllowsAttachmentOnlyPayload() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        service.sendTurnStart(
            inputText = "",
            attachments = listOf(TurnImageAttachment("data:image/jpeg;base64,ZmFrZQ==", "fake.jpg"))
        )

        assertTrue(service.status.value.contains("Turn started"))
        assertTrue(service.timeline.value.isNotEmpty())
    }

    @Test
    fun registerPushTokenUpdatesStatusInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        service.registerPushToken(
            PushRegistrationPayload(
                deviceToken = "fcm-token-xyz",
                alertsEnabled = true
            )
        )

        assertTrue(service.status.value.contains("Push registered via fcm (production)"))
    }

    @Test
    fun refreshGitStatusAndBranchesInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        service.refreshGitStatus()
        service.refreshGitBranches()

        assertTrue(service.gitStatusSummary.value.contains("Branch remodex/android-parity"))
        assertTrue(service.gitBranches.value.contains("main"))
        assertTrue(service.gitBranches.value.contains("remodex/android-parity"))
    }

    @Test
    fun checkoutGitBranchUpdatesFixtureBranchState() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        service.checkoutGitBranch("feature/fixture-checkout")

        assertTrue(service.gitStatusSummary.value.contains("feature/fixture-checkout"))
        assertTrue(service.gitBranches.value.contains("feature/fixture-checkout"))
    }

    @Test
    fun loadsPersistedPairingOnStartupWhenStoreHasValidPayload() {
        val persistedPayload = PairingPayload(
            sessionId = "session-persisted",
            relayUrl = "ws://127.0.0.1:8765/relay",
            macDeviceId = "mac-persisted",
            macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
            expiresAt = System.currentTimeMillis() + 300_000L
        )
        val store = InMemoryPairingStore(persistedPayload)

        val service = CodexService(pairingStore = store)

        assertEquals(ConnectionState.Paired, service.connectionState.value)
        assertTrue(service.status.value.contains("Loaded saved pairing for mac-persisted"))
    }

    @Test
    fun startThreadCreatesAndSelectsThreadInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()
        val beforeCount = service.threads.value.size

        service.startThread()

        assertTrue(service.threads.value.size > beforeCount)
        assertTrue(service.selectedThreadId.value?.startsWith("thread-") == true)
    }

    @Test
    fun interruptActiveTurnSendsTurnInterruptInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()
        service.openThread("thread-alpha", silentStatus = true)
        service.sendTurnStart("Run a turn and interrupt it.")

        service.interruptActiveTurn()

        assertTrue(service.timeline.value.isNotEmpty())
        assertTrue(service.status.value.contains("Interrupted turn"))
    }

    @Test
    fun gitPullPushAndCommitUpdateStatusInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        service.gitCommit("Fixture commit from Android")
        assertTrue(service.gitStatusSummary.value.contains("Branch"))

        service.gitPull()
        assertTrue(service.gitStatusSummary.value.contains("Branch"))

        service.gitPush()
        assertTrue(service.gitStatusSummary.value.contains("Branch"))
    }

    @Test
    fun gitDoesNotFallbackToGlobalPathWhenThreadIsUnbound() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        // Fixture thread-gamma intentionally has no cwd, so git must remain unavailable for it.
        service.openThread("thread-gamma", silentStatus = true)
        service.refreshGitStatus(silentStatus = true)

        assertTrue(
            service.gitStatusSummary.value.contains("Git status unavailable: select a thread bound to a local repo.")
        )
    }

    @Test
    fun refreshWorkflowSignalsInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        service.refreshRateLimitInfo()
        service.refreshModels()
        service.refreshPendingPermissions()
        service.refreshCiStatus()

        assertTrue(service.rateLimitInfo.value.contains("Rate limit"))
        assertTrue(service.availableModels.value.isNotEmpty())
        assertTrue(service.pendingPermissions.value.isNotEmpty())
        assertTrue(service.ciStatus.value.contains("CI status"))
    }

    @Test
    fun switchModelAndGrantPermissionInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()
        service.refreshPendingPermissions()
        val beforeCount = service.pendingPermissions.value.size

        service.switchModel("gpt-5.4-mini")
        val permissionId = service.pendingPermissions.value.first().id
        service.grantPermission(permissionId, allow = true)

        assertEquals("gpt-5.4-mini", service.selectedModel.value)
        assertTrue(service.pendingPermissions.value.size < beforeCount)
    }

    @Test
    fun startThreadBindsPreferredProjectPathInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        val preferredPath = "/Users/yyy/Documents/protein_design/remodex/CodexAndroid"
        service.startThread(preferredProjectPath = preferredPath)

        val selectedId = service.selectedThreadId.value ?: error("Expected selected thread.")
        val selectedThread = service.threads.value.firstOrNull { it.id == selectedId }
            ?: error("Expected created thread in list.")
        assertEquals(preferredPath, selectedThread.cwd)
    }

    @Test
    fun normalizeProjectPathSkipsPseudoProjectBucketPath() {
        assertNull(normalizeFilesystemProjectPath("server"))
        assertNull(normalizeFilesystemProjectPath("_default"))
        assertEquals("~/", normalizeFilesystemProjectPath("~/"))
        assertEquals("C:/", normalizeFilesystemProjectPath("C:/"))
    }

    @Test
    fun fixtureBridgeManagedStateLoadsAccountAndBridgeVersions() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()
        service.refreshBridgeManagedState()

        assertEquals(BridgeManagedAccountStatus.AUTHENTICATED, service.gptAccountStatus.value)
        assertEquals("fixture@example.com", service.gptAccountEmail.value)
        assertEquals("1.3.7", service.bridgeInstalledVersion.value)
        assertEquals("1.3.8", service.latestBridgePackageVersion.value)
    }

    @Test
    fun fuzzyFileAndSkillsSourcesReturnFixtureData() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        val files = service.fuzzyFileSearch(query = "workspace", roots = listOf("/Users/yyy/Documents/protein_design/remodex"))
        val skills = service.listSkills(cwds = listOf("/Users/yyy/Documents/protein_design/remodex"))

        assertTrue(files.isNotEmpty())
        assertTrue(files.any { it.fileName.contains("Workspace", ignoreCase = true) })
        assertTrue(skills.isNotEmpty())
        assertTrue(skills.any { it.name == "openai-docs" })
    }

    @Test
    fun renameArchiveAndDeleteThreadLifecycleWorksInFixtureMode() = runBlocking {
        val service = CodexService()
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-test",
                relayUrl = "ws://127.0.0.1:8765/relay",
                macDeviceId = "mac-test",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
        service.connectWithFixture()

        service.renameThread("thread-alpha", "Renamed Alpha")
        assertTrue(
            service.threads.value.any { it.id == "thread-alpha" && it.displayTitle == "Renamed Alpha" }
        )

        service.archiveThread("thread-alpha")
        assertTrue(
            service.threads.value.any { it.id == "thread-alpha" && it.isArchived }
        )

        service.unarchiveThread("thread-alpha")
        assertTrue(
            service.threads.value.any { it.id == "thread-alpha" && !it.isArchived }
        )

        service.deleteThreadLocally("thread-alpha")
        assertTrue(
            service.threads.value.none { it.id == "thread-alpha" }
        )
    }

    @Test
    fun parityTodoGateContainsCanonicalEntriesWithMatchingAcceptanceRows() {
        assertEquals(37, WebsiteFeatureTodos.size)
        assertEquals(37, ParityAcceptanceMatrix.size)
        assertEquals(
            (1..37).map { "TODO-%02d".format(it) }.toSet(),
            WebsiteFeatureTodos.map { it.id }.toSet()
        )
        assertEquals(
            WebsiteFeatureTodos.map { it.id }.toSet(),
            ParityAcceptanceMatrix.map { it.todoId }.toSet()
        )
        assertTrue(WebsiteFeatureTodos.all { it.id.startsWith("TODO-") })
        assertTrue(WebsiteFeatureTodos.all { it.iosReference.isNotBlank() })
        assertTrue(WebsiteFeatureTodos.all { it.websiteClaim.isNotBlank() })
        assertTrue(WebsiteFeatureTodos.all { it.defaultState == TodoState.DONE })
        assertTrue(ParityAcceptanceMatrix.all { it.verification.isNotBlank() })
        assertTrue(ParityAcceptanceMatrix.all { it.evidence.isNotBlank() })
        assertTrue(ParityAcceptanceMatrix.all { it.defaultState == TodoState.DONE })
    }

    @Test
    fun advanceNextTodoCompletesInProgressBeforeAdvancingNextTodo() {
        val firstInProgress = WebsiteFeatureTodos.first()
        val firstTodo = WebsiteFeatureTodos.drop(1).first()
        val stateMap = mutableMapOf(
            firstInProgress.id to TodoState.IN_PROGRESS,
            firstTodo.id to TodoState.TODO
        )
        advanceNextTodo(stateMap)
        assertEquals(TodoState.DONE, stateMap[firstInProgress.id])
        assertEquals(TodoState.TODO, stateMap[firstTodo.id])

        stateMap[firstInProgress.id] = TodoState.DONE
        advanceNextTodo(stateMap)
        assertEquals(TodoState.IN_PROGRESS, stateMap[firstTodo.id])
        assertNotNull(stateMap[firstTodo.id])
    }

    private class InMemoryPairingStore(
        private var payload: PairingPayload? = null
    ) : PairingStateStore {
        override fun load(): PairingPayload? = payload

        override fun save(payload: PairingPayload) {
            this.payload = payload
        }

        override fun clear() {
            payload = null
        }
    }
}
