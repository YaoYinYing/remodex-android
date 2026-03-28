package com.remodex.mobile.service.transport

import com.remodex.mobile.model.CODEX_SECURE_PROTOCOL_VERSION
import com.remodex.mobile.model.RpcMessage
import com.remodex.mobile.model.SecureTranscriptInput
import com.remodex.mobile.service.PairingPayload
import com.remodex.mobile.service.secure.CodexSecureTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
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
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<RpcMessage>>()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var isOpen = false

    @Volatile
    private var secureSession: ActiveSecureSession? = null

    private var controlMessages: Channel<JsonObject> = Channel(Channel.UNLIMITED)
    private val phoneIdentity: PhoneIdentity = createPhoneIdentity()

    override suspend fun open() {
        stateMutex.withLock {
            if (isOpen) {
                return
            }
            controlMessages.close()
            controlMessages = Channel(Channel.UNLIMITED)
            pendingResponses.clear()
            connectSocket()
            try {
                performSecureHandshake()
                isOpen = true
            } catch (error: Throwable) {
                closeSocket()
                throw error
            }
        }
    }

    override suspend fun close() {
        stateMutex.withLock {
            failPendingResponses(IllegalStateException("Transport closed."))
            closeSocket()
        }
    }

    override suspend fun request(method: String, params: JsonObject): RpcMessage {
        if (!isOpen) {
            open()
        }

        val requestId = requestIdCounter.getAndIncrement().toString()
        val responseDeferred = CompletableDeferred<RpcMessage>()
        pendingResponses[requestId] = responseDeferred

        val rpc = RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive(requestId),
            method = method,
            params = params
        )
        val payloadText = json.encodeToString(rpc)
        sendEncryptedApplicationPayload(payloadText)

        return try {
            withTimeout(REQUEST_TIMEOUT_MS) { responseDeferred.await() }
        } finally {
            pendingResponses.remove(requestId)
        }
    }

    private suspend fun connectSocket() {
        val opened = CompletableDeferred<Unit>()
        val request = Request.Builder()
            .url(resolveRelaySessionWebSocketUrl())
            .header("x-role", "android")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                processIncomingText(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleSocketClosed(IllegalStateException("Relay socket closed: $code $reason"))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) {
                    opened.completeExceptionally(t)
                } else {
                    handleSocketClosed(t)
                }
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)
        withTimeout(CONNECT_TIMEOUT_MS) {
            opened.await()
        }
    }

    private suspend fun performSecureHandshake() {
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
        sendControl(clientHello)

        val serverHello = waitForMatchingServerHello(expectedClientNonce = clientHello.clientNonce)
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

        val clientAuthTranscript = buildClientAuthTranscript(transcript)
        val phoneSignature = signPayload(phoneIdentity.privateSeed, clientAuthTranscript)
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

        sendControl(
            SecureResumeState(
                sessionId = pairing.sessionId,
                keyEpoch = serverHello.keyEpoch,
                lastAppliedBridgeOutboundSeq = 0
            )
        )
    }

    private fun processIncomingText(rawText: String) {
        val parsed = try {
            json.parseToJsonElement(rawText) as? JsonObject
        } catch (_: Throwable) {
            null
        } ?: return

        val kind = (parsed["kind"] as? JsonPrimitive)?.contentOrNull
        when (kind) {
            "serverHello", "secureReady", "secureError" -> {
                controlMessages.trySend(parsed)
            }

            "encryptedEnvelope" -> {
                handleEncryptedEnvelope(parsed)
            }

            else -> {
                handleRpcPayloadText(rawText)
            }
        }
    }

    private fun handleEncryptedEnvelope(parsed: JsonObject) {
        val envelope = try {
            json.decodeFromJsonElement<SecureEnvelope>(parsed)
        } catch (_: Throwable) {
            return
        }

        val session = secureSession ?: return
        if (envelope.sessionId != session.sessionId || envelope.keyEpoch != session.keyEpoch) {
            return
        }
        if (envelope.sender != "mac" || envelope.counter <= session.lastInboundCounter) {
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
        } catch (_: Throwable) {
            return
        }

        val payload = try {
            json.decodeFromString<SecureApplicationPayload>(plaintext.decodeToString())
        } catch (_: Throwable) {
            return
        }

        session.lastInboundCounter = envelope.counter
        if (payload.bridgeOutboundSeq != null && payload.bridgeOutboundSeq > session.lastAppliedBridgeOutboundSeq) {
            session.lastAppliedBridgeOutboundSeq = payload.bridgeOutboundSeq
        }
        secureSession = session

        handleRpcPayloadText(payload.payloadText)
    }

    private fun handleRpcPayloadText(payloadText: String) {
        val message = try {
            json.decodeFromString<RpcMessage>(payloadText)
        } catch (_: Throwable) {
            return
        }

        val idKey = rpcIdKey(message.id) ?: return
        if (message.result != null || message.error != null) {
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
                continue
            }
            return hello
        }
    }

    private suspend fun waitForControl(expectedKind: String): JsonObject {
        return withTimeout(HANDSHAKE_TIMEOUT_MS) {
            var matched: JsonObject? = null
            while (matched == null) {
                val message = controlMessages.receive()
                val kind = (message["kind"] as? JsonPrimitive)?.contentOrNull ?: continue
                if (kind == "secureError") {
                    val secureError = json.decodeFromJsonElement<SecureErrorMessage>(message)
                    throw IllegalStateException(secureError.message)
                }
                if (kind == expectedKind) {
                    matched = message
                }
            }
            matched
        }
    }

    private fun sendControl(value: Any) {
        val text = when (value) {
            is SecureClientHello -> json.encodeToString(value)
            is SecureClientAuth -> json.encodeToString(value)
            is SecureResumeState -> json.encodeToString(value)
            is SecureEnvelope -> json.encodeToString(value)
            else -> throw IllegalArgumentException("Unsupported wire payload.")
        }
        val socket = webSocket ?: throw IllegalStateException("Relay socket is unavailable.")
        val accepted = socket.send(text)
        if (!accepted) {
            throw IllegalStateException("Failed to send wire payload to relay socket.")
        }
    }

    private fun resolveRelaySessionWebSocketUrl(): String {
        val relayBase = pairing.relayUrl.trim().trimEnd('/')
        return if (relayBase.endsWith("/relay")) {
            "$relayBase/${pairing.sessionId}"
        } else {
            "$relayBase/relay/${pairing.sessionId}"
        }
    }

    private fun handleSocketClosed(cause: Throwable) {
        isOpen = false
        secureSession = null
        failPendingResponses(cause)
    }

    private fun failPendingResponses(cause: Throwable) {
        val pending = pendingResponses.values.toList()
        pendingResponses.clear()
        pending.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.completeExceptionally(cause)
            }
        }
    }

    private fun closeSocket() {
        isOpen = false
        secureSession = null
        runCatching { webSocket?.close(1000, "client_close") }
        webSocket = null
    }

    private fun createPhoneIdentity(): PhoneIdentity {
        val privateSeed = ByteArray(32).also { secureRandom.nextBytes(it) }
        val privateKey = Ed25519PrivateKeyParameters(privateSeed, 0)
        val publicKey = privateKey.generatePublicKey().encoded
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
