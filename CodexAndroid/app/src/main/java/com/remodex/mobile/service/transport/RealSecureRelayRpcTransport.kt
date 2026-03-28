package com.remodex.mobile.service.transport

import com.remodex.mobile.model.CODEX_SECURE_PROTOCOL_VERSION
import com.remodex.mobile.model.RpcMessage
import com.remodex.mobile.model.SecureTranscriptInput
import com.remodex.mobile.service.PairingPayload
import com.remodex.mobile.service.logging.AppLogger
import com.remodex.mobile.service.secure.CodexSecureTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val HANDSHAKE_MODE_QR_BOOTSTRAP = "qr_bootstrap"
private const val HANDSHAKE_LABEL_CLIENT_AUTH = "client-auth"
private const val REQUEST_TIMEOUT_MS = 20_000L
private const val HANDSHAKE_TIMEOUT_MS = 12_000L
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val OPEN_HANDSHAKE_ATTEMPTS = 5
private const val OPEN_RETRY_DELAY_BASE_MS = 750L
private const val LOG_TAG = "RelayTransport"

class RealSecureRelayRpcTransport(
    private val pairing: PairingPayload,
    private val secureTransport: CodexSecureTransport,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) : RpcTransport {
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val secureRandom = SecureRandom()
    private val requestIdCounter = AtomicLong(1)
    private val stateMutex = Mutex()
    private val requestMutex = Mutex()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<RpcMessage>>()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var isOpen = false

    @Volatile
    private var secureSession: ActiveSecureSession? = null

    @Volatile
    private var lastSocketCloseCause: Throwable? = null

    private var controlMessages: Channel<JsonObject> = Channel(Channel.UNLIMITED)
    private val phoneIdentity: PhoneIdentity = createPhoneIdentity()

    override suspend fun open() {
        stateMutex.withLock {
            if (isOpen) {
                AppLogger.debug(LOG_TAG, "open() skipped because transport is already open. ${connectionTag()}")
                return
            }
            AppLogger.info(
                LOG_TAG,
                "open() starting with maxAttempts=$OPEN_HANDSHAKE_ATTEMPTS. ${connectionTag()}"
            )

            var lastError: Throwable? = null
            for (attempt in 1..OPEN_HANDSHAKE_ATTEMPTS) {
                resetHandshakeState()
                try {
                    AppLogger.info(LOG_TAG, "open() attempt $attempt/$OPEN_HANDSHAKE_ATTEMPTS connecting socket.")
                    connectSocket()
                    performSecureHandshake()
                    isOpen = true
                    AppLogger.info(
                        LOG_TAG,
                        "open() completed on attempt $attempt; secure session established. ${connectionTag()}"
                    )
                    return
                } catch (error: Throwable) {
                    lastError = error
                    AppLogger.error(
                        LOG_TAG,
                        "open() attempt $attempt failed during connect/handshake. ${connectionTag()}",
                        error
                    )
                    closeSocket()
                    if (attempt >= OPEN_HANDSHAKE_ATTEMPTS || !isRetryableOpenFailure(error)) {
                        throw error
                    }
                    val retryDelayMs = OPEN_RETRY_DELAY_BASE_MS * attempt
                    AppLogger.warn(
                        LOG_TAG,
                        "open() will retry after retryable failure on attempt $attempt in ${retryDelayMs}ms."
                    )
                    delay(retryDelayMs)
                }
            }

            throw (lastError ?: IllegalStateException("open() failed without a concrete error."))
        }
    }

    override suspend fun close() {
        stateMutex.withLock {
            AppLogger.info(LOG_TAG, "close() requested. ${connectionTag()}")
            failPendingResponses(IllegalStateException("Transport closed."))
            closeSocket()
        }
    }

    override suspend fun request(method: String, params: JsonObject): RpcMessage {
        return requestMutex.withLock {
            requestInternal(method, params, allowRetryOnSendFailure = true)
        }
    }

    private suspend fun requestInternal(
        method: String,
        params: JsonObject,
        allowRetryOnSendFailure: Boolean
    ): RpcMessage {
        if (!isOpen) {
            AppLogger.info(LOG_TAG, "request($method) opening transport lazily. ${connectionTag()}")
            open()
        }
        val requestId = requestIdCounter.getAndIncrement().toString()
        val responseDeferred = CompletableDeferred<RpcMessage>()
        pendingResponses[requestId] = responseDeferred
        val startedAt = System.currentTimeMillis()
        AppLogger.info(
            LOG_TAG,
            "rpc request start id=$requestId method=$method pending=${pendingResponses.size}. ${connectionTag()}"
        )

        val rpc = RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive(requestId),
            method = method,
            params = params
        )
        val payloadText = json.encodeToString(rpc)
        try {
            sendEncryptedApplicationPayload(payloadText)
        } catch (error: Throwable) {
            pendingResponses.remove(requestId)
            AppLogger.warn(
                LOG_TAG,
                "rpc request send failed id=$requestId method=$method recoverable=${isRecoverableSocketSendFailure(error)}.",
                error
            )
            if (allowRetryOnSendFailure && isRecoverableSocketSendFailure(error)) {
                AppLogger.info(
                    LOG_TAG,
                    "rpc request id=$requestId method=$method attempting one session reopen retry after send failure."
                )
                reopenSessionAfterSendFailure()
                return requestInternal(method, params, allowRetryOnSendFailure = false)
            }
            throw error
        }

        return try {
            val response = withTimeout(REQUEST_TIMEOUT_MS) { responseDeferred.await() }
            val elapsed = System.currentTimeMillis() - startedAt
            AppLogger.info(
                LOG_TAG,
                "rpc request success id=$requestId method=$method elapsedMs=$elapsed."
            )
            response
        } catch (error: Throwable) {
            val elapsed = System.currentTimeMillis() - startedAt
            AppLogger.warn(
                LOG_TAG,
                "rpc request failed id=$requestId method=$method elapsedMs=$elapsed.",
                error
            )
            throw error
        } finally {
            pendingResponses.remove(requestId)
        }
    }

    private fun isRecoverableSocketSendFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return error is IllegalStateException &&
            (message.contains("Failed to send wire payload to relay socket", ignoreCase = true) ||
                message.contains("Relay socket is unavailable", ignoreCase = true) ||
                message.contains("socket closed", ignoreCase = true))
    }

    private suspend fun reopenSessionAfterSendFailure() {
        AppLogger.warn(LOG_TAG, "Reopening socket/session after recoverable send failure. ${connectionTag()}")
        stateMutex.withLock {
            closeSocket()
            controlMessages.close()
            controlMessages = Channel(Channel.UNLIMITED)
            pendingResponses.clear()
        }
        open()
    }

    private fun resetHandshakeState() {
        controlMessages.close()
        controlMessages = Channel(Channel.UNLIMITED)
        pendingResponses.clear()
        secureSession = null
        isOpen = false
        lastSocketCloseCause = null
    }

    private fun isRetryableOpenFailure(error: Throwable): Boolean {
        if (error is TimeoutCancellationException) {
            return true
        }
        val message = error.message.orEmpty()
        return message.contains("timed out", ignoreCase = true) ||
            message.contains("socket", ignoreCase = true) ||
            message.contains("relay", ignoreCase = true)
    }

    private suspend fun connectSocket() {
        val opened = CompletableDeferred<Unit>()
        val wsUrl = resolveRelaySessionWebSocketUrl()
        AppLogger.info(LOG_TAG, "Connecting websocket to ${redactRelayUrl(wsUrl)}.")
        val request = Request.Builder()
            .url(wsUrl)
            .header("x-role", "android")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentSocket(webSocket)) {
                    AppLogger.debug(LOG_TAG, "Ignoring onOpen callback from stale websocket.")
                    return
                }
                AppLogger.info(
                    LOG_TAG,
                    "WebSocket opened with HTTP ${response.code}. ${connectionTag()}"
                )
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentSocket(webSocket)) {
                    AppLogger.debug(LOG_TAG, "Ignoring onMessage callback from stale websocket.")
                    return
                }
                processIncomingText(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(webSocket)) {
                    AppLogger.debug(LOG_TAG, "Ignoring onClosed callback from stale websocket.")
                    return
                }
                AppLogger.warn(LOG_TAG, "WebSocket closed code=$code reason=$reason. ${connectionTag()}")
                handleSocketClosed(webSocket, IllegalStateException("Relay socket closed: $code $reason"))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(webSocket)) {
                    AppLogger.debug(LOG_TAG, "Ignoring onClosing callback from stale websocket.")
                    return
                }
                AppLogger.info(LOG_TAG, "WebSocket closing code=$code reason=$reason.")
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrentSocket(webSocket)) {
                    AppLogger.debug(LOG_TAG, "Ignoring onFailure callback from stale websocket.")
                    return
                }
                val code = response?.code?.toString() ?: "n/a"
                AppLogger.error(
                    LOG_TAG,
                    "WebSocket failure responseCode=$code opened=${opened.isCompleted}. ${connectionTag()}",
                    t
                )
                if (!opened.isCompleted) {
                    opened.completeExceptionally(t)
                } else {
                    handleSocketClosed(webSocket, t)
                }
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)
        withTimeout(CONNECT_TIMEOUT_MS) {
            opened.await()
        }
        AppLogger.info(LOG_TAG, "WebSocket connect confirmed within timeout.")
    }

    private suspend fun performSecureHandshake() {
        AppLogger.info(LOG_TAG, "Handshake started. ${connectionTag()}")
        val clientNonce = ByteArray(32).also { secureRandom.nextBytes(it) }
        val phoneEphemeralPrivate = X25519PrivateKeyParameters(secureRandom)
        val phoneEphemeralPublic = phoneEphemeralPrivate.generatePublicKey().encoded

        val clientHello = SecureClientHello(
            protocolVersion = CODEX_SECURE_PROTOCOL_VERSION,
            sessionId = pairing.sessionId,
            handshakeMode = HANDSHAKE_MODE_QR_BOOTSTRAP,
            phoneDeviceId = phoneIdentity.deviceId,
            phoneIdentityPublicKey = encodeBase64(phoneIdentity.publicKey),
            phoneEphemeralPublicKey = encodeBase64(phoneEphemeralPublic),
            clientNonce = encodeBase64(clientNonce)
        )
        AppLogger.info(
            LOG_TAG,
            "Sending clientHello protocol=${clientHello.protocolVersion} mode=${clientHello.handshakeMode}."
        )
        sendControl(clientHello)

        val serverHello = waitForMatchingServerHello(expectedClientNonce = clientHello.clientNonce)
        AppLogger.info(
            LOG_TAG,
            "Received serverHello keyEpoch=${serverHello.keyEpoch} protocol=${serverHello.protocolVersion}."
        )
        validateServerHello(serverHello)

        val transcript = secureTransport.buildTranscriptBytes(
            SecureTranscriptInput(
                sessionId = serverHello.sessionId,
                protocolVersion = serverHello.protocolVersion,
                handshakeMode = serverHello.handshakeMode,
                keyEpoch = serverHello.keyEpoch,
                macDeviceId = serverHello.macDeviceId,
                phoneDeviceId = phoneIdentity.deviceId,
                macIdentityPublicKeyBase64 = serverHello.macIdentityPublicKey,
                phoneIdentityPublicKeyBase64 = encodeBase64(phoneIdentity.publicKey),
                macEphemeralPublicKeyBase64 = serverHello.macEphemeralPublicKey,
                phoneEphemeralPublicKeyBase64 = encodeBase64(phoneEphemeralPublic),
                clientNonceBase64 = clientHello.clientNonce,
                serverNonceBase64 = serverHello.serverNonce,
                expiresAtForTranscript = serverHello.expiresAtForTranscript
            )
        )

        val macSignature = decodeBase64(serverHello.macSignature)
        val macPublicKey = decodeBase64(serverHello.macIdentityPublicKey)
        if (!verifySignature(publicKey = macPublicKey, payload = transcript, signature = macSignature)) {
            throw IllegalStateException("Server signature verification failed.")
        }
        AppLogger.info(LOG_TAG, "Server signature verification passed.")

        val clientAuthTranscript = buildClientAuthTranscript(transcript)
        val phoneSignature = signPayload(phoneIdentity.privateSeed, clientAuthTranscript)
        AppLogger.info(LOG_TAG, "Sending clientAuth for keyEpoch=${serverHello.keyEpoch}.")
        sendControl(
            SecureClientAuth(
                sessionId = pairing.sessionId,
                phoneDeviceId = phoneIdentity.deviceId,
                keyEpoch = serverHello.keyEpoch,
                phoneSignature = encodeBase64(phoneSignature)
            )
        )

        val readyObject = waitForControl("secureReady")
        val ready = json.decodeFromJsonElement<SecureReadyMessage>(readyObject)
        if (ready.sessionId != pairing.sessionId || ready.keyEpoch != serverHello.keyEpoch) {
            throw IllegalStateException("Secure ready mismatch.")
        }
        AppLogger.info(LOG_TAG, "Received secureReady keyEpoch=${ready.keyEpoch}.")

        val sharedSecret = deriveSharedSecret(
            privateKey = phoneEphemeralPrivate,
            remotePublicKey = X25519PublicKeyParameters(decodeBase64(serverHello.macEphemeralPublicKey), 0)
        )
        val salt = sha256(transcript)
        val infoPrefix = "remodex-e2ee-v1|${pairing.sessionId}|${pairing.macDeviceId}|${phoneIdentity.deviceId}|${serverHello.keyEpoch}"
        val phoneToMac = hkdfSha256(
            ikm = sharedSecret,
            salt = salt,
            info = "$infoPrefix|phoneToMac".toByteArray(Charsets.UTF_8),
            outputBytes = 32
        )
        val macToPhone = hkdfSha256(
            ikm = sharedSecret,
            salt = salt,
            info = "$infoPrefix|macToPhone".toByteArray(Charsets.UTF_8),
            outputBytes = 32
        )

        secureSession = ActiveSecureSession(
            sessionId = pairing.sessionId,
            keyEpoch = serverHello.keyEpoch,
            phoneToMacKey = phoneToMac,
            macToPhoneKey = macToPhone
        )
        AppLogger.info(
            LOG_TAG,
            "Secure session activated keyEpoch=${serverHello.keyEpoch} phoneId=${phoneIdentity.deviceId}."
        )

        sendControl(
            SecureResumeState(
                sessionId = pairing.sessionId,
                keyEpoch = serverHello.keyEpoch,
                lastAppliedBridgeOutboundSeq = 0
            )
        )
        AppLogger.info(LOG_TAG, "Handshake completed; resumeState sent.")
    }

    private fun processIncomingText(rawText: String) {
        val parsed = try {
            json.parseToJsonElement(rawText) as? JsonObject
        } catch (error: Throwable) {
            AppLogger.debug(LOG_TAG, "Ignoring non-JSON websocket message (${rawText.length} chars).", error)
            null
        } ?: return

        val kind = (parsed["kind"] as? JsonPrimitive)?.contentOrNull
        if (kind != null) {
            AppLogger.debug(LOG_TAG, "Incoming websocket message kind=$kind.")
        }
        when (kind) {
            "serverHello", "secureReady", "secureError" -> {
                controlMessages.trySend(parsed)
            }

            "encryptedEnvelope" -> {
                handleEncryptedEnvelope(parsed)
            }

            else -> {
                AppLogger.debug(LOG_TAG, "Incoming websocket payload treated as raw RPC text.")
                handleRpcPayloadText(rawText)
            }
        }
    }

    private fun handleEncryptedEnvelope(parsed: JsonObject) {
        val envelope = try {
            json.decodeFromJsonElement<SecureEnvelope>(parsed)
        } catch (error: Throwable) {
            AppLogger.warn(LOG_TAG, "Failed to decode encrypted envelope.", error)
            return
        }

        val session = secureSession ?: run {
            AppLogger.warn(LOG_TAG, "Dropping encrypted envelope because secure session is null.")
            return
        }
        if (envelope.sessionId != session.sessionId || envelope.keyEpoch != session.keyEpoch) {
            AppLogger.warn(
                LOG_TAG,
                "Dropping envelope due to session/key mismatch epoch=${envelope.keyEpoch} counter=${envelope.counter}."
            )
            return
        }
        if (envelope.sender != "mac" || envelope.counter <= session.lastInboundCounter) {
            AppLogger.debug(
                LOG_TAG,
                "Ignoring envelope sender=${envelope.sender} counter=${envelope.counter} lastInbound=${session.lastInboundCounter}."
            )
            return
        }

        val nonce = secureTransport.nonceForSender("mac", envelope.counter.toLong())
        val plaintext = try {
            decryptAesGcm(
                key = session.macToPhoneKey,
                nonce = nonce,
                ciphertext = decodeBase64(envelope.ciphertext),
                tag = decodeBase64(envelope.tag)
            )
        } catch (error: Throwable) {
            AppLogger.warn(
                LOG_TAG,
                "Failed decrypting envelope counter=${envelope.counter}.",
                error
            )
            return
        }

        val payload = try {
            json.decodeFromString<SecureApplicationPayload>(plaintext.decodeToString())
        } catch (error: Throwable) {
            AppLogger.warn(
                LOG_TAG,
                "Failed decoding secure application payload counter=${envelope.counter}.",
                error
            )
            return
        }

        session.lastInboundCounter = envelope.counter
        if (payload.bridgeOutboundSeq != null && payload.bridgeOutboundSeq > session.lastAppliedBridgeOutboundSeq) {
            session.lastAppliedBridgeOutboundSeq = payload.bridgeOutboundSeq
        }
        secureSession = session
        AppLogger.debug(
            LOG_TAG,
            "Processed encrypted envelope counter=${envelope.counter} bridgeSeq=${payload.bridgeOutboundSeq ?: -1}."
        )

        handleRpcPayloadText(payload.payloadText)
    }

    private fun handleRpcPayloadText(payloadText: String) {
        val message = try {
            json.decodeFromString<RpcMessage>(payloadText)
        } catch (error: Throwable) {
            AppLogger.warn(LOG_TAG, "Failed decoding RPC payload text (${payloadText.length} chars).", error)
            return
        }

        val idKey = rpcIdKey(message.id) ?: return
        if (message.result != null || message.error != null) {
            AppLogger.debug(LOG_TAG, "Completing RPC response for id=$idKey.")
            pendingResponses.remove(idKey)?.complete(message)
        }
    }

    private suspend fun sendEncryptedApplicationPayload(payloadText: String) {
        val session = secureSession ?: throw IllegalStateException("Secure session is not ready.")
        val counter = session.nextOutboundCounter
        val nonce = secureTransport.nonceForSender("android", counter.toLong())
        val payload = SecureApplicationPayload(
            bridgeOutboundSeq = null,
            payloadText = payloadText
        )
        val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val (ciphertext, tag) = encryptAesGcm(session.phoneToMacKey, nonce, plaintext)
        val envelope = SecureEnvelope(
            kind = "encryptedEnvelope",
            v = CODEX_SECURE_PROTOCOL_VERSION,
            sessionId = session.sessionId,
            keyEpoch = session.keyEpoch,
            sender = "android",
            counter = counter,
            ciphertext = encodeBase64(ciphertext),
            tag = encodeBase64(tag)
        )
        session.nextOutboundCounter += 1
        secureSession = session
        AppLogger.debug(LOG_TAG, "Sending encrypted envelope counter=$counter keyEpoch=${session.keyEpoch}.")
        sendControl(envelope)
    }

    private fun validateServerHello(serverHello: SecureServerHello) {
        if (serverHello.protocolVersion != CODEX_SECURE_PROTOCOL_VERSION) {
            throw IllegalStateException("Protocol mismatch.")
        }
        if (serverHello.sessionId != pairing.sessionId) {
            throw IllegalStateException("Session mismatch.")
        }
        if (serverHello.macDeviceId != pairing.macDeviceId) {
            throw IllegalStateException("Mac device mismatch.")
        }
        if (serverHello.macIdentityPublicKey != pairing.macIdentityPublicKey) {
            throw IllegalStateException("Mac identity key mismatch.")
        }
    }

    private suspend fun waitForMatchingServerHello(expectedClientNonce: String): SecureServerHello {
        while (true) {
            val raw = waitForControl("serverHello")
            val hello = json.decodeFromJsonElement<SecureServerHello>(raw)
            if (hello.clientNonce != null && hello.clientNonce != expectedClientNonce) {
                AppLogger.warn(LOG_TAG, "Ignoring serverHello with mismatched client nonce.")
                continue
            }
            return hello
        }
    }

    private suspend fun waitForControl(expectedKind: String): JsonObject {
        return withTimeout(HANDSHAKE_TIMEOUT_MS) {
            var matched: JsonObject? = null
            while (matched == null) {
                val next = controlMessages.receiveCatching()
                if (next.isClosed) {
                    throw lastSocketCloseCause ?: IllegalStateException("Relay socket closed before $expectedKind.")
                }
                val message = next.getOrNull() ?: continue
                val kind = (message["kind"] as? JsonPrimitive)?.contentOrNull ?: continue
                if (kind == "secureError") {
                    val secureError = json.decodeFromJsonElement<SecureErrorMessage>(message)
                    AppLogger.error(
                        LOG_TAG,
                        "Received secureError code=${secureError.code} message=${secureError.message}."
                    )
                    throw IllegalStateException(secureError.message)
                }
                if (kind == expectedKind) {
                    AppLogger.debug(LOG_TAG, "waitForControl matched kind=$expectedKind.")
                    matched = message
                }
            }
            matched
        }
    }

    private fun sendControl(value: Any) {
        val (payloadKind, text) = when (value) {
            is SecureClientHello -> "clientHello" to json.encodeToString(value)
            is SecureClientAuth -> "clientAuth" to json.encodeToString(value)
            is SecureResumeState -> "resumeState" to json.encodeToString(value)
            is SecureEnvelope -> "encryptedEnvelope" to json.encodeToString(value)
            else -> throw IllegalArgumentException("Unsupported wire payload.")
        }
        val socket = webSocket ?: throw IllegalStateException("Relay socket is unavailable.")
        val accepted = socket.send(text)
        if (!accepted) {
            AppLogger.error(
                LOG_TAG,
                "Socket send returned false for kind=$payloadKind while isOpen=$isOpen. ${connectionTag()}"
            )
            throw IllegalStateException("Failed to send wire payload to relay socket.")
        }
        AppLogger.debug(LOG_TAG, "Socket send accepted for kind=$payloadKind.")
    }

    private fun resolveRelaySessionWebSocketUrl(): String {
        val relayBase = pairing.relayUrl.trim().trimEnd('/')
        return if (relayBase.endsWith("/relay")) {
            "$relayBase/${pairing.sessionId}"
        } else {
            "$relayBase/relay/${pairing.sessionId}"
        }
    }

    private fun handleSocketClosed(socket: WebSocket, cause: Throwable) {
        if (!isCurrentSocket(socket)) {
            AppLogger.debug(LOG_TAG, "Skipping socket-close handling for stale websocket.")
            return
        }
        AppLogger.warn(LOG_TAG, "Socket/session closed. ${connectionTag()}", cause)
        lastSocketCloseCause = cause
        controlMessages.trySend(
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("secureError"),
                    "code" to JsonPrimitive("socket_closed"),
                    "message" to JsonPrimitive(cause.message ?: "Relay socket closed.")
                )
            )
        )
        isOpen = false
        secureSession = null
        failPendingResponses(cause)
    }

    private fun failPendingResponses(cause: Throwable) {
        val pending = pendingResponses.values.toList()
        pendingResponses.clear()
        if (pending.isNotEmpty()) {
            AppLogger.warn(LOG_TAG, "Failing ${pending.size} pending RPC response(s).", cause)
        }
        pending.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.completeExceptionally(cause)
            }
        }
    }

    private fun closeSocket() {
        AppLogger.info(LOG_TAG, "Closing websocket. ${connectionTag()}")
        isOpen = false
        secureSession = null
        lastSocketCloseCause = null
        runCatching { webSocket?.close(1000, "client_close") }
        webSocket = null
    }

    private fun createPhoneIdentity(): PhoneIdentity {
        val privateSeed = ByteArray(32).also { secureRandom.nextBytes(it) }
        val privateKey = Ed25519PrivateKeyParameters(privateSeed, 0)
        val publicKey = privateKey.generatePublicKey().encoded
        AppLogger.info(LOG_TAG, "Generated ephemeral phone identity for transport session.")
        return PhoneIdentity(
            deviceId = "android-${UUID.randomUUID()}",
            privateSeed = privateSeed,
            publicKey = publicKey
        )
    }

    private fun buildClientAuthTranscript(transcript: ByteArray): ByteArray {
        val labelBytes = HANDSHAKE_LABEL_CLIENT_AUTH.toByteArray(Charsets.UTF_8)
        val prefix = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(labelBytes.size)
            .array()
        return transcript + prefix + labelBytes
    }

    private fun signPayload(privateSeed: ByteArray, payload: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateSeed, 0))
        signer.update(payload, 0, payload.size)
        return signer.generateSignature()
    }

    private fun verifySignature(publicKey: ByteArray, payload: ByteArray, signature: ByteArray): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(payload, 0, payload.size)
        return verifier.verifySignature(signature)
    }

    private fun deriveSharedSecret(
        privateKey: X25519PrivateKeyParameters,
        remotePublicKey: X25519PublicKeyParameters
    ): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(remotePublicKey, shared, 0)
        return shared
    }

    private fun sha256(value: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(value)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputBytes: Int): ByteArray {
        val normalizedSalt = if (salt.isNotEmpty()) salt else ByteArray(32)
        val prk = hmacSha256(normalizedSalt, ikm)
        val okm = ByteArray(outputBytes)
        var previousBlock = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < outputBytes) {
            val blockInput = previousBlock + info + byteArrayOf(counter.toByte())
            previousBlock = hmacSha256(prk, blockInput)
            val copySize = minOf(previousBlock.size, outputBytes - offset)
            previousBlock.copyInto(okm, offset, 0, copySize)
            offset += copySize
            counter += 1
        }

        return okm
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun encryptAesGcm(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val combined = cipher.doFinal(plaintext)
        val tagStart = combined.size - 16
        val ciphertext = combined.copyOfRange(0, tagStart)
        val tag = combined.copyOfRange(tagStart, combined.size)
        return ciphertext to tag
    }

    private fun decryptAesGcm(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, tag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val combined = ciphertext + tag
        return cipher.doFinal(combined)
    }

    private fun connectionTag(): String {
        return "relay=${relayHostLabel()} session=${sessionHash(pairing.sessionId)} mac=${pairing.macDeviceId}"
    }

    private fun relayHostLabel(): String {
        return runCatching { URI(pairing.relayUrl.trim()).host }.getOrNull()
            ?: pairing.relayUrl.trim()
    }

    private fun sessionHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun redactRelayUrl(url: String): String {
        return if (pairing.sessionId.isBlank()) {
            url
        } else {
            url.replace(pairing.sessionId, "<session>")
        }
    }

    private fun isCurrentSocket(socket: WebSocket): Boolean {
        return webSocket === socket
    }

    private fun rpcIdKey(id: JsonElement?): String? {
        val primitive = id as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }

    private fun decodeBase64(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }

    private fun encodeBase64(value: ByteArray): String {
        return Base64.getEncoder().encodeToString(value)
    }
}

private data class PhoneIdentity(
    val deviceId: String,
    val privateSeed: ByteArray,
    val publicKey: ByteArray
)

private data class ActiveSecureSession(
    val sessionId: String,
    val keyEpoch: Int,
    val phoneToMacKey: ByteArray,
    val macToPhoneKey: ByteArray,
    var lastInboundCounter: Int = -1,
    var nextOutboundCounter: Int = 0,
    var lastAppliedBridgeOutboundSeq: Int = 0
)

@Serializable
private data class SecureClientHello(
    val kind: String = "clientHello",
    val protocolVersion: Int,
    val sessionId: String,
    val handshakeMode: String,
    val phoneDeviceId: String,
    val phoneIdentityPublicKey: String,
    val phoneEphemeralPublicKey: String,
    val clientNonce: String
)

@Serializable
private data class SecureServerHello(
    val kind: String,
    val protocolVersion: Int,
    val sessionId: String,
    val handshakeMode: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val macEphemeralPublicKey: String,
    val serverNonce: String,
    val keyEpoch: Int,
    val expiresAtForTranscript: Long,
    val macSignature: String,
    val clientNonce: String? = null
)

@Serializable
private data class SecureClientAuth(
    val kind: String = "clientAuth",
    val sessionId: String,
    val phoneDeviceId: String,
    val keyEpoch: Int,
    val phoneSignature: String
)

@Serializable
private data class SecureReadyMessage(
    val kind: String,
    val sessionId: String,
    val keyEpoch: Int,
    val macDeviceId: String
)

@Serializable
private data class SecureResumeState(
    val kind: String = "resumeState",
    val sessionId: String,
    val keyEpoch: Int,
    val lastAppliedBridgeOutboundSeq: Int
)

@Serializable
private data class SecureErrorMessage(
    val kind: String,
    val code: String,
    val message: String
)

@Serializable
private data class SecureEnvelope(
    val kind: String,
    val v: Int,
    val sessionId: String,
    val keyEpoch: Int,
    val sender: String,
    val counter: Int,
    val ciphertext: String,
    val tag: String
)

@Serializable
private data class SecureApplicationPayload(
    val bridgeOutboundSeq: Int? = null,
    val payloadText: String
)
