// FILE: push-provider-router.js
// Purpose: Routes completion notifications to APNs or FCM using a shared provider interface.
// Layer: Hosted service helper
// Exports: createPushProviderRouter
// Depends on: ./apns-client, ./fcm-client

const fs = require("fs");
const { createAPNsClient } = require("./apns-client");
const { createFCMClient, fcmConfigFromEnv } = require("./fcm-client");

function createPushProviderRouter({
  apnsClient = createAPNsClient(apnsConfigFromEnv(process.env)),
  fcmClient = createFCMClient(fcmConfigFromEnv(process.env)),
} = {}) {
  async function sendNotification({
    pushProvider,
    deviceToken,
    pushEnvironment,
    title,
    body,
    payload,
  } = {}) {
    if (pushProvider === "fcm") {
      return fcmClient.sendNotification({
        deviceToken,
        title,
        body,
        payload,
      });
    }

    return apnsClient.sendNotification({
      deviceToken,
      apnsEnvironment: pushEnvironment,
      title,
      body,
      payload,
    });
  }

  function getStats() {
    return {
      apnsConfigured: Boolean(apnsClient?.isConfigured?.()),
      fcmConfigured: Boolean(fcmClient?.isConfigured?.()),
    };
  }

  return {
    sendNotification,
    getStats,
  };
}

function apnsConfigFromEnv(env) {
  return {
    teamId: readFirstDefinedEnv(["REMODEX_APNS_TEAM_ID", "PHODEX_APNS_TEAM_ID"], env),
    keyId: readFirstDefinedEnv(["REMODEX_APNS_KEY_ID", "PHODEX_APNS_KEY_ID"], env),
    bundleId: readFirstDefinedEnv(["REMODEX_APNS_BUNDLE_ID", "PHODEX_APNS_BUNDLE_ID"], env),
    privateKey: readAPNsPrivateKey(env),
  };
}

function readAPNsPrivateKey(env) {
  const rawValue = readFirstDefinedEnv(["REMODEX_APNS_PRIVATE_KEY", "PHODEX_APNS_PRIVATE_KEY"], env);
  if (rawValue) {
    return rawValue;
  }

  const filePath = readFirstDefinedEnv(
    ["REMODEX_APNS_PRIVATE_KEY_FILE", "PHODEX_APNS_PRIVATE_KEY_FILE"],
    env
  );
  if (!filePath) {
    return "";
  }

  try {
    return fs.readFileSync(filePath, "utf8");
  } catch {
    return "";
  }
}

function readFirstDefinedEnv(keys, env) {
  for (const key of keys) {
    const value = readString(env?.[key]);
    if (value) {
      return value;
    }
  }
  return "";
}

function readString(value) {
  return typeof value === "string" && value.trim() ? value.trim() : "";
}

module.exports = {
  createPushProviderRouter,
};
