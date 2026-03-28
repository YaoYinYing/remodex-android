package com.remodex.mobile.service.push

data class PushRegistrationPayload(
    val deviceToken: String,
    val alertsEnabled: Boolean,
    val platform: String = "android",
    val pushProvider: String = "fcm",
    val pushEnvironment: String = "production"
)
