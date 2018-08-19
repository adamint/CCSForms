package com.adamratzman.forms.backend.main

import com.adamratzman.forms.common.models.Role
import com.adamratzman.forms.common.models.SpringUserResponse
import com.adamratzman.forms.common.models.User
import com.adamratzman.forms.common.models.UserLogin
import com.adamratzman.forms.common.utils.asPojo
import com.google.gson.Gson
import com.rethinkdb.RethinkDB.r
import org.apache.commons.lang3.RandomStringUtils
import spark.Spark.get
import spark.Spark.port
import java.util.concurrent.Executors

val executor = Executors.newScheduledThreadPool(1)

fun main(args: Array<String>) {
    FormBackend()
}

class FormBackend(val gson: Gson = Gson()) {
    val conn = r.connection().hostname("database").port(28015).db("chs").connect()
    val loginUtils = LoginUtils(conn, gson)

    init {
        databaseSetup()
        port(80)

        get("/login/:username/:password") { request, _ ->
            gson.toJson(loginUtils.checkLogin(request.params("username"), request.params("password")))
        }

        get("/user/:username") { request, _ ->
            val username = request.params("username")
            gson.toJson(SpringUserResponse(asPojo(gson, r.table("users").get(username).run(conn), User::class.java),
                    asPojo(gson, r.table("logins").get(username).run(conn), UserLogin::class.java)))
        }
    }

    fun databaseSetup() {
        if (!r.dbList().run<List<String>>(conn).contains("chs")) {
            println("Creating database `chs`")
            r.dbCreate("chs").run<Any>(conn)
        }

        val tables = listOf("users", "login_attempts", "forms", "responses", "logins")

        tables.forEach { table ->
            if (!r.tableList().run<List<String>>(conn).contains(table)) {
                println("Creating table `$table`")
                when (table) {
                    "users" -> r.tableCreate(table).optArg("primary_key", "username").run<Any>(conn)
                    "login_attempts" -> {
                        r.tableCreate(table).run<Any>(conn)
                        r.table(table).indexCreate("username").runNoReply(conn)
                    }
                    "logins" -> r.tableCreate(table).optArg("primary_key", "username").run<Any>(conn)
                }
            }
        }

        if (r.table("users").count().run<Long>(conn) == 0L) {
            val password = RandomStringUtils.randomAlphanumeric(12)
            println("Initial setup | Admin user - username: admin - password: $password")
            loginUtils.insertUser("admin", password, Role.ADMIN)

            // Insert some test students as well
            loginUtils.insertUser("student", "chsrocks", Role.STUDENT)
            loginUtils.insertUser("student1", "password", Role.STUDENT)
        }
    }
}