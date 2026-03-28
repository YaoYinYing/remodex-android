// FILE: push-notification-service-client.test.js
// Purpose: Verifies timeout behavior for push-service HTTP calls from the local bridge.
// Layer: Unit test
// Exports: node:test suite
// Depends on: node:test, node:assert/strict, ../src/push-notification-service-client

const test = require("node:test");
const assert = require("node:assert/strict");

const { createPushNotificationServiceClient } = require("../src/push-notification-service-client");

test("push service client aborts stalled requests with a timeout error", async () => {
  const client = createPushNotificationServiceClient({
    baseUrl: "https://push.example.test",
    sessionId: "session-timeout",
    notificationSecret: "secret-timeout",
    requestTimeoutMs: 20,
    fetchImpl: async (_url, options) => new Promise((_, reject) => {
      options.signal.addEventListener("abort", () => {
        reject(options.signal.reason);
      }, { once: true });
    }),
  });

  await assert.rejects(
    client.registerDevice({
      deviceToken: "aabbcc",
      alertsEnabled: true,
      apnsEnvironment: "development",
    }),
    /timed out after 20ms/
  );
});

test("push service client includes platform/provider fields for android registrations", async () => {
  const requests = [];
  const client = createPushNotificationServiceClient({
    baseUrl: "https://push.example.test",
    sessionId: "session-android",
    notificationSecret: "secret-android",
    fetchImpl: async (_url, options) => {
      requests.push(JSON.parse(options.body));
      return {
        ok: true,
        async text() {
          return JSON.stringify({ ok: true });
        },
      };
    },
  });

  const response = await client.registerDevice({
    deviceToken: "fcm-token-1",
    alertsEnabled: true,
    platform: "android",
    pushProvider: "fcm",
    pushEnvironment: "production",
  });

  assert.equal(response.ok, true);
  assert.equal(requests.length, 1);
  assert.equal(requests[0].platform, "android");
  assert.equal(requests[0].pushProvider, "fcm");
  assert.equal(requests[0].pushEnvironment, "production");
});
