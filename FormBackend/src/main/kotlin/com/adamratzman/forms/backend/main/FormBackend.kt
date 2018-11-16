package com.adamratzman.forms.backend.main

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.asPojo
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.common.utils.queryAsArrayList
import com.rethinkdb.RethinkDB.r
import com.rethinkdb.gen.exc.ReqlOpFailedError
import com.rethinkdb.net.Connection
import org.apache.commons.lang3.RandomStringUtils
import spark.Spark.*
import java.util.concurrent.Executors

fun main(args: Array<String>) {
    FormBackend()
}

class FormBackend {
    lateinit var conn: Connection
    lateinit var loginUtils: LoginUtils

    init {
        databaseSetup()
        port(80)

        exception(Exception::class.java) { exception, _, _ ->
            exception.printStackTrace()
        }

        registerAuthenticationEndpoints()
        registerFormRetrievalEndpoints()
        registerFormCreationEndpoint()
        registerFormSubmissionEndpoint()
        registerUtilsEndpoints()
        registerFormDeletionEndpoint()
        registerFormSpecificEndpoints()
    }

    fun databaseSetup() {
        while (true) {
            try {
                conn = r.connection().hostname("database").port(28015).db("chs").connect()
                loginUtils = LoginUtils(conn, globalGson)
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
                        "forms" -> {
                            r.tableCreate(table).run<Any>(conn)
                            r.table(table).indexCreate("creator").runNoReply(conn)
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
            globalGson.toJson(loginUtils.checkLogin(request.params("username"), request.params("password")))
        }

        get("/user/:username") { request, _ ->
            globalGson.toJson(getUser(request.params("username")))
        }
    }

    fun registerFormSpecificEndpoints() {
        path("/forms/info/:id") {
            get("/taken/:username") { request, _ ->
                val username = request.params(":username")
                (username != "null" && getResponses(request.params(":id")).find { it.submitter == username } != null).toString()
            }
            get("/taken") { request, _ ->
                globalGson.toJson(getResponses(request.params(":id")))
            }
        }
    }

    fun getResponses(formId: String): List<FormResponseDatabaseWrapper> {
        return r.table("responses").getAll(formId).optArg("index", "formId").run<Any>(conn)
                .queryAsArrayList(globalGson, FormResponseDatabaseWrapper::class.java).filterNotNull()
    }

    fun registerFormDeletionEndpoint() {
        post("/forms/delete/:id") { request, _ ->
            r.table("forms").get(request.params(":id")).delete().run(conn)
        }
    }

    fun registerFormCreationEndpoint() {
        post("/forms/create") { request, _ ->
            val form = globalGson.fromJson(request.body(), Form::class.java)
            if (form.id != null) {
                r.table("responses").getAll(form.id).optArg("index", "formId").delete().runNoReply(conn)
                r.table("forms").get(form.id).update(r.json(globalGson.toJson(form))).run<Any>(conn)
                globalGson.toJson(StatusWithRedirect(200, null, form.id))
            } else {
                form.id = getRandomFormId()
                r.table("forms").insert(r.json(globalGson.toJson(form))).run<Any>(conn)
                globalGson.toJson(StatusWithRedirect(200, null, form.id))
            }
        }
    }

    fun registerFormSubmissionEndpoint() {
        post("/forms/submit") { request, _ ->
            val submission = globalGson.fromJson(request.body(), FormResponseDatabaseWrapper::class.java)
            if (submission?.response?.formId != null) {
                r.table("responses").insert(r.json(globalGson.toJson(submission))).run<Any>(conn)
            }
        }
    }

    fun registerFormRetrievalEndpoints() {
        path("/forms") {
            path("/responses") {
                get("/:id") { request, _ ->
                    r.table("responses").getAll(request.params(":id")).optArg("index", "formId").run<Any>(conn)
                            .queryAsArrayList(globalGson, FormResponseDatabaseWrapper::class.java).filterNotNull().let { globalGson.toJson(it) }
                }
            }

            path("/available") {
                get("/submit/:username") { request, _ ->
                    val username = request.params("username")
                    val role = getUser(username).user!!.role
                    val availableForms = getForms().filter { form ->
                        (form.creator == username || form.allowedContributors.contains(username) ||
                                form.submitRoles.contains(role)) &&
                                (form.allowMultipleSubmissions || !getResponsesFor(form).map { it.submitter }.contains(username))
                    }
                    globalGson.toJson(availableForms)
                }

                get("/created-ids/:username") { request, _ ->
                    globalGson.toJson(getForms().asSequence().filter { it.creator == request.params(":username") }.map { it.id }.toList())
                }
            }

            get("/all/:id") { request, _ ->
                // in the future, this needs to include all forms a user has *access* to manage, not just ones created by them
                r.table("forms").getAll(request.params(":id")).optArg("index", "creator").run<Any>(conn)
                        .queryAsArrayList(globalGson, Form::class.java).let {
                            globalGson.toJson(it)
                        }
            }

            get("/get/:id") { request, _ ->
                globalGson.toJson(asPojo(globalGson, r.table("forms").get(request.params(":id")
                        ?: "-1").run(conn), Form::class.java))
            }
        }
    }

    fun registerUtilsEndpoints() {
        path("/utils") {
            get("/random-form-id") { _, _ -> getRandomFormId() }
        }
    }

    fun getUser(username: String): SparkUserResponse {
        return SparkUserResponse(asPojo(globalGson, r.table("users").get(username).run(conn), User::class.java),
                asPojo(globalGson, r.table("logins").get(username).run(conn), UserLogin::class.java))
    }

    fun getForms(): List<Form> = r.table("forms").run<Any>(conn).queryAsArrayList(globalGson, Form::class.java).filterNotNull()

    fun getResponsesFor(form: Form): List<FormResponseDatabaseWrapper> {
        return r.table("responses").getAll(form.id).optArg("index", "formId").run<Any>(conn)
                .queryAsArrayList(globalGson, FormResponseDatabaseWrapper::class.java).filterNotNull()
    }

    fun getRandomFormId(): String {
        val randomString = RandomStringUtils.randomAlphanumeric(6)
        return if (getForms().find { it.id == randomString } == null) randomString else getRandomFormId()
    }
}