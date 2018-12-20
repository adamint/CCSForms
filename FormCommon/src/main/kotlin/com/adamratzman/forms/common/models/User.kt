package com.adamratzman.forms.common.models

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

    /**
     * Required to implement due to the primitive array type being used as a parameter
     */
    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + Arrays.hashCode(salt)
        result = 31 * result + Arrays.hashCode(hash)
        return result
    }
}

data class User(val username: String, val role: Role, var email: String? = null, var userNotificationSettings: UserNotificationSettings? = null)

data class UserNotificationSettings(var globalNotifyNewSubmissions: Boolean = true, var globalNotifySubmissionDeletion: Boolean = false)

enum class Role(val readable: String, val position: Int) {
    NOT_LOGGED_IN("Not Logged in", 0), STUDENT("Student", 1),
    TEACHER("Teacher", 2), COUNSELING("Counseling and Student Services", 3),
    ATHLETICS("Athletics", 4), ADMIN("Admin", 5);

    override fun toString() =
            when (this) {
                NOT_LOGGED_IN -> "those not logged in"
                TEACHER -> "teachers"
                STUDENT -> "students"
                ATHLETICS -> "teachers"
                ADMIN -> "administrators"
                COUNSELING -> "counselors"
            }
}

/**
 * Represents a request for user data. Since the [user] and [login] may not be found, they are nullable
 *
 * @return nullable [User] and [UserLogin] data for the specified username
 */
data class SparkUserResponse(val user: User?, val login: UserLogin?)