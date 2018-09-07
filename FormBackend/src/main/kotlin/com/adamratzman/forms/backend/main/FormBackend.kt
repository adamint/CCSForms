package com.adamratzman.forms.backend.main

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.asPojo
import com.adamratzman.forms.common.utils.queryAsArrayList
import com.google.gson.Gson
import com.rethinkdb.RethinkDB.r
import com.rethinkdb.gen.exc.ReqlOpFailedError
import com.rethinkdb.net.Connection
import org.apache.commons.lang3.RandomStringUtils
import spark.Spark.*
import java.util.concurrent.Executors

val executor = Executors.newScheduledThreadPool(1)

fun main(args: Array<String>) {
    FormBackend()
}

class FormBackend(val gson: Gson = Gson()) {
    lateinit var conn: Connection
    lateinit var loginUtils: LoginUtils

    init {
        databaseSetup()
        port(80)

        exception(Exception::class.java) {exception, _, _ ->
            exception.printStackTrace()
        }

        registerAuthenticationEndpoints()
        registerFormRetrievalEndpoints()
    }

    fun databaseSetup() {
        while (true) {
            try {
                conn = r.connection().hostname("database").port(28015).db("chs").connect()
                loginUtils = LoginUtils(conn, gson)
                break
            } catch (e: Exception) {
            }
        }
        try {
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
                        "responses" -> {
                            r.tableCreate(table).run<Any>(conn)
                            r.table(table).indexCreate("formId").runNoReply(conn)
                        }
                        else -> r.tableCreate(table).run<Any>(conn)
                    }
                }
            }

            if (r.table("users").count().run<Long>(conn) == 0L) {
                //val password = RandomStringUtils.randomAlphanumeric(12)
                //println("Initial setup | Admin user - username: admin - password: $password")
                loginUtils.insertUser("admin", "admin", Role.ADMIN)

                // Insert some test students as well
                loginUtils.insertUser("student", "chsrocks", Role.STUDENT)
                loginUtils.insertUser("student1", "password", Role.STUDENT)
            }
        } catch (e: ReqlOpFailedError) {
            databaseSetup()
        }
    }

    private fun registerAuthenticationEndpoints() {
        get("/login/:username/:password") { request, _ ->
            gson.toJson(loginUtils.checkLogin(request.params("username"), request.params("password")))
        }

        get("/user/:username") { request, _ ->
            gson.toJson(getUser(request.params("username")))
        }
    }

    fun registerFormRetrievalEndpoints() {
        path("/forms/available") {
            get("/submit/:username") { request, _ ->
                val username = request.params("username")
                val role = getUser(username).user!!.role
                val availableForms = getForms().filter { form ->
                    (form.creator == username || form.allowedContributors.contains(username) ||
                            form.submitRoles.contains(role)) &&
                            (form.allowMultipleSubmissions || !getResponsesFor(form).map { it.submitter }.contains(username))
                }
                gson.toJson(availableForms)
            }
        }
    }

    fun getUser(username: String): SparkUserResponse {
        return SparkUserResponse(asPojo(gson, r.table("users").get(username).run(conn), User::class.java),
                asPojo(gson, r.table("logins").get(username).run(conn), UserLogin::class.java))
    }

    fun getForms(): List<Form> = r.table("forms").run<Any>(conn).queryAsArrayList(gson, Form::class.java).filterNotNull()

    fun getResponsesFor(form: Form): List<FormResponse> {
        return r.table("responses").getAll(form.id).optArg("index", "formId").run<Any>(conn)
                .queryAsArrayList(gson, FormResponse::class.java).filterNotNull()
    }
}