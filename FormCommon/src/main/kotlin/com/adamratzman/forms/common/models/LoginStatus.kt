package com.adamratzman.forms.common.models

data class LoginSuccess(val status: Int, val user: User) : LoginResult()

data class LoginFailure(val status: Int, val message: String, val retryAfter: Long) : LoginResult()

abstract class LoginResult

data class LoginAttempt(val timestamp: Long, val username: String, val response: LoginResult)