package com.adamratzman.forms.common.models

abstract class LoginResult

data class LoginSuccess(val status: Int, val user: User) : LoginResult()

data class LoginFailure(val status: Int, val message: String, val retryAfter: Long) : LoginResult()

/**
 * @param timestamp the time of attempt, in milliseconds
 * @param username the attempted username
 * @param response the result (success/failure) of this login attempt
 */
data class LoginAttempt(val timestamp: Long, val username: String, val response: LoginResult)