package com.adamratzman.forms.backend.main

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.asPojo
import com.google.common.hash.Hashing
import com.google.gson.Gson
import com.rethinkdb.RethinkDB.r
import com.rethinkdb.net.Connection
import java.security.SecureRandom

class LoginUtils(val conn: Connection, val gson: Gson) {
    val retryWaiting: HashMap<String, Long> = hashMapOf()
    val random = SecureRandom()

    fun getHashedPassword(password: String, salt: ByteArray): ByteArray {
        return Hashing.sha256().hashBytes(arrayOf(password.toByteArray(), salt).map { it.toList() }.flatten().toByteArray()).asBytes()
    }

    fun insertUser(username: String, password: String, role: Role) {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val hashedPassword = getHashedPassword(password, salt)
        r.table("logins").insert(r.json(gson.toJson(UserLogin(username, salt, hashedPassword)))).run<Any>(conn)
        r.table("users").insert(r.json(gson.toJson(User(username, role)))).run<Any>(conn)
    }

    fun checkLogin(username: String, password: String): LoginResult {
        return when {
            !usernameExists(username) -> LoginFailure(404, "Username not found", 0)
            !canLogin(username) -> LoginFailure(402, "Cannot login right now. Please retry in ${(retryWaiting[username]!! - System.currentTimeMillis()) / 1000} seconds",
                    (retryWaiting[username]!! - System.currentTimeMillis()) / 1000)
            else -> verifyLogin(username, password)
        }
    }

    /**
     * Verifies a username & password combination
     * Preconditions: username exists and the user can log in
     */
    fun verifyLogin(username: String, password: String): LoginResult {
        val login = asPojo(gson, r.table("logins").get(username).run(conn), UserLogin::class.java)
                ?: return LoginFailure(404, "An invalid username or password was specified. Please retry.", 0)

        val user = if (login.hash.contentEquals(getHashedPassword(password, login.salt))) {
            asPojo(gson, r.table("users").get(username).run(conn), User::class.java)
        } else null

        val retryAfter = if (getLoginAttemptsPastMinute(username) >= 3) {
            if (!retryWaiting.containsKey(username)) {
                retryWaiting[username] = System.currentTimeMillis() + (1000 * 60)
                60
            } else {
                (retryWaiting[username]!! - System.currentTimeMillis()) / 1000
            }
        } else 0

        val loginResult = if (user == null) LoginFailure(401, "An invalid username or password was specified"
                + if (retryAfter > 0) ". You can retry after $retryAfter seconds" else "", retryAfter)
        else LoginSuccess(200, user)

        r.table("login_attempts").insert(r.json(gson.toJson(LoginAttempt(System.currentTimeMillis(), username, loginResult)))).run<Any>(conn)
        return loginResult
    }

    /**
     * Users can login if they have <= 3 failed attempts in the past minute.
     * Precondition: username exists
     */
    fun canLogin(username: String): Boolean {
        retryWaiting[username]?.let { if ((it - System.currentTimeMillis()) / 1000 <= 0) retryWaiting.remove(username) }
        return retryWaiting[username] == null && getLoginAttemptsPastMinute(username) <= 3
    }

    fun getLoginAttemptsPastMinute(username: String): Int {
        return r.table("login_attempts").getAll(username).optArg("index", "username")
                .filter { row -> row.g("timestamp").gt(System.currentTimeMillis() - (1000 * 60)) }
                .count().run<Int>(conn)
    }

    fun usernameExists(username: String): Boolean {
        return r.table("users").get(username).run<Any?>(conn) != null
    }
}