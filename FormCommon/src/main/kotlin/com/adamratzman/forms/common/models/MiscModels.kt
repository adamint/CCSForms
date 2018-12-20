package com.adamratzman.forms.common.models

data class StatusWithRedirect(val status: Int, val redirect: String?, val message: String? = null)

/**
 * Represents a key ([id]) value ([value]) pair in the database
 */
data class Credential(val id: String, val value: String)

/**
 * A piece of mail to the [to] email, with the [body] as the html content of the email
 */
data class QueuedMail(val to: String, val subject: String, val body: String)

/**
 * Models an email verification request
 * @param expiry when this verification request expires
 * @param id unique identifier of this request
 */
data class EmailVerification(val username: String, val email: String, val expiry: Long, val id: String)