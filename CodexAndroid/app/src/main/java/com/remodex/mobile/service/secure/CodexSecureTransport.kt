package com.remodex.mobile.service.secure

import com.remodex.mobile.model.SecureTranscriptInput
import com.remodex.mobile.service.PairingPayload

class CodexSecureTransport {
    private var pairingPayload: PairingPayload? = null

    fun rememberPairing(payload: PairingPayload) {
        pairingPayload = payload
    }

    fun currentPairing(): PairingPayload? = pairingPayload

    fun buildTranscriptBytes(input: SecureTranscriptInput): ByteArray {
        return CodexSecureTranscript.buildTranscriptBytes(input)
    }

    fun nonceForSender(sender: String, counter: Long): ByteArray {
        return CodexSecureTranscript.nonceForSender(sender, counter)
    }
}
