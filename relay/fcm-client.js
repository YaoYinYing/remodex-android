// FILE: fcm-client.js
// Purpose: Sends Firebase Cloud Messaging alerts for relay-hosted Remodex notifications.
// Layer: Hosted service helper
// Exports: createFCMClient
// Depends on: crypto, fs

const crypto = require("crypto");
const fs = require("fs");

const FCM_TOKEN_TTL_MS = 50 * 60 * 1000;
const FCM_OAUTH_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

function createFCMClient({
  projectId = "",
  clientEmail = "",
  privateKey = "",
  fetchImpl = globalThis.fetch,
  now = () => Date.now(),
} = {}) {
  let cachedAccessToken = null;

  function isConfigured() {
    return Boolean(projectId && clientEmail && privateKey && typeof fetchImpl === "function");
  }

  async function sendNotification({
    deviceToken,
    title,
    body,
    payload = {},
  } = {}) {
    if (!isConfigured()) {
      throw fcmError("fcm_not_configured", "FCM credentials are not configured.", 503);
    }

    const normalizedDeviceToken = normalizeString(deviceToken);
    if (!normalizedDeviceToken) {
      throw fcmError("invalid_device_token", "A valid FCM device token is required.", 400);
    }

    const accessToken = await authorizationToken();
    const response = await fetchImpl(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          authorization: `Bearer ${accessToken}`,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: normalizedDeviceToken,
            notification: {
              title: normalizeString(title) || "Remodex",
              body: normalizeString(body) || "Response ready",
            },
            data: normalizePayload(payload),
            android: {
              priority: "HIGH",
            },
          },
        }),
      }
    );

    const responseText = await response.text();
    const parsed = safeParseJSON(responseText);
    if (!response.ok) {
      const reason = parsed?.error?.message || parsed?.error || responseText || `FCM request failed with HTTP ${response.status}.`;
      throw fcmError("fcm_request_failed", reason, response.status);
    }

    return { ok: true };
  }

  async function authorizationToken() {
    const currentTimeMs = now();
    if (cachedAccessToken && cachedAccessToken.expiresAtMs > currentTimeMs + 30_000) {
      return cachedAccessToken.value;
    }

    const issuedAtSeconds = Math.floor(currentTimeMs / 1000);
    const assertion = signedJWT({
      iss: clientEmail,
      scope: FCM_OAUTH_SCOPE,
      aud: "https://oauth2.googleapis.com/token",
      iat: issuedAtSeconds,
      exp: issuedAtSeconds + 3600,
    }, privateKey);

    const tokenResponse = await fetchImpl("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: {
        "content-type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    });

    const responseText = await tokenResponse.text();
    const parsed = safeParseJSON(responseText);
    if (!tokenResponse.ok || !normalizeString(parsed?.access_token)) {
      const reason = parsed?.error_description || parsed?.error || responseText || `OAuth token request failed with HTTP ${tokenResponse.status}.`;
      throw fcmError("fcm_oauth_failed", reason, tokenResponse.status || 500);
    }

    const expiresInSeconds = Number(parsed.expires_in) || 3600;
    cachedAccessToken = {
      value: parsed.access_token,
      expiresAtMs: currentTimeMs + Math.min(expiresInSeconds * 1000, FCM_TOKEN_TTL_MS),
    };
    return cachedAccessToken.value;
  }

  return {
    isConfigured,
    sendNotification,
  };
}

function fcmConfigFromEnv(env) {
  return {
    projectId: readFirstDefinedEnv(["REMODEX_FCM_PROJECT_ID", "PHODEX_FCM_PROJECT_ID"], env),
    clientEmail: readFirstDefinedEnv(["REMODEX_FCM_CLIENT_EMAIL", "PHODEX_FCM_CLIENT_EMAIL"], env),
    privateKey: readFCMPrivateKey(env),
  };
}

function readFCMPrivateKey(env) {
  const rawValue = readFirstDefinedEnv(["REMODEX_FCM_PRIVATE_KEY", "PHODEX_FCM_PRIVATE_KEY"], env);
  if (rawValue) {
    return rawValue.replace(/\\n/g, "\n");
  }

  const filePath = readFirstDefinedEnv(
    ["REMODEX_FCM_PRIVATE_KEY_FILE", "PHODEX_FCM_PRIVATE_KEY_FILE"],
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
    const value = normalizeString(env?.[key]);
    if (value) {
      return value;
    }
  }
  return "";
}

function signedJWT(claims, privateKey) {
  const header = base64UrlJSON({
    alg: "RS256",
    typ: "JWT",
  });
  const payload = base64UrlJSON(claims);
  const signingInput = `${header}.${payload}`;
  const signature = crypto.sign("RSA-SHA256", Buffer.from(signingInput), privateKey);
  return `${signingInput}.${base64Url(signature)}`;
}

function normalizePayload(payload) {
  if (!payload || typeof payload !== "object") {
    return {};
  }

  const entries = Object.entries(payload);
  const normalized = {};
  for (const [key, value] of entries) {
    if (!normalizeString(key)) {
      continue;
    }
    normalized[key] = stringifyValue(value);
  }
  return normalized;
}

function stringifyValue(value) {
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (value == null) {
    return "";
  }
  return JSON.stringify(value);
}

function safeParseJSON(value) {
  if (!value) {
    return null;
  }

  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function base64UrlJSON(value) {
  return base64Url(Buffer.from(JSON.stringify(value)));
}

function base64Url(value) {
  return Buffer.from(value)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function normalizeString(value) {
  return typeof value === "string" && value.trim() ? value.trim() : "";
}

function fcmError(code, message, status) {
  const error = new Error(message);
  error.code = code;
  error.status = status;
  return error;
}

module.exports = {
  createFCMClient,
  fcmConfigFromEnv,
};
