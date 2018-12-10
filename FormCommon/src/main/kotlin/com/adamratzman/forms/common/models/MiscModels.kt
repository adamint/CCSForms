package com.adamratzman.forms.common.models

data class StatusWithRedirect(val status: Int, val redirect: String?, val message: String? = null)

data class Credential(val id: String, val value: String)

data class QueuedMail(val to: String, val subject: String, val body: String)
data class EmailVerification(val username: String, val id: String, val expiry: Long)