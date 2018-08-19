package com.adamratzman.forms.frontend.common

data class UserLogin(val username: String, val salt: ByteArray, val hash: ByteArray)

data class User(val username: String, val role: Role, val logins: MutableList<Long>)

enum class Role{
    STUDENT, TEACHER, COUNSELOR, ADMIN
}