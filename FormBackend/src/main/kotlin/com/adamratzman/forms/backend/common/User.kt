package com.adamratzman.forms.backend.common

import java.util.*

data class UserLogin(val username: String, val salt: ByteArray, val hash: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserLogin

        if (username != other.username) return false
        if (!Arrays.equals(salt, other.salt)) return false
        if (!Arrays.equals(hash, other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + Arrays.hashCode(salt)
        result = 31 * result + Arrays.hashCode(hash)
        return result
    }
}

data class User(val username: String, val role: Role, val logins: MutableList<Long>)

enum class Role{
    STUDENT, TEACHER, COUNSELOR, ADMIN
}

data class SpringUserResponse(val user: User?, val login: UserLogin?)