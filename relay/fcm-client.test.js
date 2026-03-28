// FILE: fcm-client.test.js
// Purpose: Verifies FCM OAuth and send flow for relay-hosted completion notifications.
// Layer: Unit test
// Exports: node:test suite
// Depends on: node:test, node:assert/strict, node:crypto, ./fcm-client

const test = require("node:test");
const assert = require("node:assert/strict");
const { generateKeyPairSync } = require("node:crypto");
const { createFCMClient } = require("./fcm-client");

test("FCM client requests OAuth token and sends notification", async () => {
  const requests = [];
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const privatePem = privateKey.export({ format: "pem", type: "pkcs8" });

  const client = createFCMClient({
    projectId: "project-1",
    clientEmail: "svc@example.test",
    privateKey: privatePem,
    fetchImpl: async (url, options) => {
      requests.push({ url, options });
      if (url === "https://oauth2.googleapis.com/token") {
        return {
          ok: true,
          async text() {
            return JSON.stringify({
              access_token: "token-1",
              expires_in: 3600,
            });
          },
        };
      }

      if (url === "https://fcm.googleapis.com/v1/projects/project-1/messages:send") {
        return {
          ok: true,
          async text() {
            return JSON.stringify({ name: "projects/project-1/messages/msg-1" });
          },
        };
      }

      throw new Error(`Unexpected URL: ${url}`);
    },
  });

  await client.sendNotification({
    deviceToken: "fcm-device-token-1",
    title: "Title",
    body: "Body",
    payload: {
      threadId: "thread-1",
      turnId: "turn-1",
      result: "completed",
    },
  });

  assert.equal(requests.length, 2);
  assert.equal(requests[0].url, "https://oauth2.googleapis.com/token");
  assert.equal(requests[1].url, "https://fcm.googleapis.com/v1/projects/project-1/messages:send");

  const sendPayload = JSON.parse(requests[1].options.body);
  assert.equal(sendPayload.message.token, "fcm-device-token-1");
  assert.equal(sendPayload.message.notification.title, "Title");
  assert.equal(sendPayload.message.data.threadId, "thread-1");
});

test("FCM client rejects empty device tokens", async () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const privatePem = privateKey.export({ format: "pem", type: "pkcs8" });
  const client = createFCMClient({
    projectId: "project-1",
    clientEmail: "svc@example.test",
    privateKey: privatePem,
    fetchImpl: async () => {
      throw new Error("should not run");
    },
  });

  await assert.rejects(
    client.sendNotification({
      deviceToken: "   ",
    }),
    /FCM device token/
  );
});
