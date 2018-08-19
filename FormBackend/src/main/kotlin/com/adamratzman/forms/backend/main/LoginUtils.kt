package com.adamratzman.forms.backend.main

import com.adamratzman.forms.backend.common.*
import com.adamratzman.forms.backend.utils.asPojo
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
        println("Insert details: password: $password salt: ${salt.map { it.toInt() }} hash: ${hashedPassword.map { it.toInt() }}")
        r.table("logins").insert(r.json(gson.toJson(UserLogin(username, salt, hashedPassword)))).run<Any>(conn)
        r.table("users").insert(r.json(gson.toJson(User(username, role, mutableListOf())))).run<Any>(conn)
    }


    fun checkLogin(username: String, password: String): LoginResult {
        return when {
            !usernameExists(username) -> LoginFailure(404, "Username not found", 0)
            !canLogin(username) -> LoginFailure(402, "Cannot login right now",
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
                ?: return LoginFailure(404, "An invalid username or password was specified", 0)
        println(login.hash.map { it.toInt() })
        println(getHashedPassword(password, login.salt).map { it.toInt() })

        val user = if (login.hash.contentEquals(getHashedPassword(password, login.salt))) {
            asPojo(gson, r.table("users").get(username).run(conn), User::class.java)
        } else null

        val retryAfter = if (getLoginAttemptsPastMinute(username) >= 3) {
            if (!retryWaiting.containsKey(username)) {
                retryWaiting[username] = System.currentTimeMillis() + (1000 * 60)
                60
            } else {
                val value = (retryWaiting[username]!! - System.currentTimeMillis()) / 1000
                if (value <= 0) {
                    retryWaiting.remove(username)
                    0
                } else value
            }
        } else 0

        val loginResult = if (user == null) LoginFailure(401, "An invalid username or password was specified", retryAfter)
        else LoginSuccess(200, user)

        r.table("login_attempts").insert(r.json(gson.toJson(LoginAttempt(System.currentTimeMillis(), username, loginResult)))).run<Any>(conn)
        return loginResult
    }

    /**
     * Users can login if they have <= 3 failed attempts in the past minute.
     * Precondition: username exists
     */
    fun canLogin(username: String): Boolean {
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