// FILE: secure-transport-vectors.test.js
// Purpose: Verifies stable secure-transport transcript and nonce vectors shared with Android.
// Layer: Unit test
// Exports: node:test suite
// Depends on: node:test, node:assert/strict, node:crypto, node:fs, node:path, ../src/secure-transport

const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const { __test, nonceForDirection } = require("../src/secure-transport");

function readVectors() {
  const filePath = path.resolve(__dirname, "../../protocol/secure-transport-vectors.json");
  const raw = fs.readFileSync(filePath, "utf8");
  return JSON.parse(raw);
}

test("secure transcript bytes match the shared golden vector", () => {
  const vectors = readVectors();
  const transcript = vectors.transcript;
  const produced = __test.buildTranscriptBytes({
    sessionId: transcript.sessionId,
    protocolVersion: transcript.protocolVersion,
    handshakeMode: transcript.handshakeMode,
    keyEpoch: transcript.keyEpoch,
    macDeviceId: transcript.macDeviceId,
    phoneDeviceId: transcript.phoneDeviceId,
    macIdentityPublicKey: transcript.macIdentityPublicKeyBase64,
    phoneIdentityPublicKey: transcript.phoneIdentityPublicKeyBase64,
    macEphemeralPublicKey: transcript.macEphemeralPublicKeyBase64,
    phoneEphemeralPublicKey: transcript.phoneEphemeralPublicKeyBase64,
    clientNonce: Buffer.from(transcript.clientNonceBase64, "base64"),
    serverNonce: Buffer.from(transcript.serverNonceBase64, "base64"),
    expiresAtForTranscript: transcript.expiresAtForTranscript,
  });

  assert.equal(produced.toString("hex"), transcript.expectedTranscriptHex);
  assert.equal(
    crypto.createHash("sha256").update(produced).digest("hex"),
    transcript.expectedTranscriptSha256
  );
});

test("secure nonce vectors stay stable across mobile senders", () => {
  const vectors = readVectors();
  const expected = vectors.nonce.expectedHexBySender;
  const counter = vectors.nonce.counter;

  assert.equal(nonceForDirection("iphone", counter).toString("hex"), expected.iphone);
  assert.equal(nonceForDirection("android", counter).toString("hex"), expected.android);
  assert.equal(nonceForDirection("mac", counter).toString("hex"), expected.mac);
});
