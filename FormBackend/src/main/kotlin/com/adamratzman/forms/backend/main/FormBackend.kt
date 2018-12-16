package com.adamratzman.forms.backend.main

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.asPojo
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.common.utils.queryAsArrayList
import com.rethinkdb.RethinkDB.r
import com.rethinkdb.gen.exc.ReqlOpFailedError
import com.rethinkdb.net.Connection
import net.sargue.mailgun.Configuration
import net.sargue.mailgun.Mail
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import spark.Spark.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    FormBackend(args[0])
}

class FormBackend(val frontendIp: String) {
    lateinit var conn: Connection
    lateinit var loginUtils: LoginUtils
    lateinit var mailgunConfig: Configuration
    lateinit var key: String

    val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    val frontend = "http://frontend"

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
        registerFormResponseDeletionEndpoint()
        registerFormSpecificEndpoints()
        registerEmailEndpoints()
        registerNotificationsEndpoints()

        initiateMailgunConfig()

        var start = false
        while (!start) {
            try {
                Jsoup.connect("$frontend/forms/xt/regenerate-key").post()
                start = true
            } catch (e: Exception) {
            }
        }
        println("Started backend")
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

            val tables = listOf("users", "login_attempts", "forms", "responses", "logins", "credentials",
                    "email_verification")

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
                        "email_verification" -> r.tableCreate(table).optArg("primary_key", "username").run<Any>(conn)
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

                // ...and some other roles too
                loginUtils.insertUser("teacher", "teacher", Role.TEACHER)
                loginUtils.insertUser("counselor", "counselor", Role.COUNSELING)
            }

            scheduledExecutor.scheduleAtFixedRate({ expireOldVerificationRequests() }, 0, 1, TimeUnit.MINUTES)
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

    fun registerFormResponseDeletionEndpoint() {
        post("/forms/response/delete/:id") { request, _ ->
            r.table("responses").get(request.params(":id")).delete().run(conn)
        }
    }

    fun registerFormCreationEndpoint() {
        post("/forms/create") { request, _ ->
            val form = globalGson.fromJson(request.body(), Form::class.java)
            if (form.id != null) {
                val existing = getForms().find { it.id == form.id }
                if (existing != null && existing.creator == form.creator) {
                    form.creationDate = existing.creationDate
                    if (!(form isIdenticalTo existing)) {
                        r.table("responses").getAll(form.id).optArg("index", "formId").delete().runNoReply(conn)
                    }
                    r.table("forms").get(form.id).update(r.json(globalGson.toJson(form))).run<Any>(conn)
                    globalGson.toJson(StatusWithRedirect(200, null, form.id))
                } else globalGson.toJson(StatusWithRedirect(400, null, "bad id"))
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

                val form = getForms().first { it.id == submission.formId }

                val formCreator = getUser(form.creator).user!!
                if (formCreator.email != null && formCreator.userNotificationSettings?.globalNotifyNewSubmissions == true) {
                    val subject = "New CHSForms Submission - ${form.name}"
                    val map = getEmailMap()
                    map["user"] = formCreator.username
                    map["formName"] = form.name
                    map["responseLink"] = "$frontend/forms/manage/response/${submission.id}"
                    map["allResponsesLink"] = "$frontend/forms/manage/responses/${form.id}"
                    map["submitted"] = submission.submitter ?: "Someone"

                    val body = renderTemplate(map, "email-response-notification.hbs")

                    sendEmail(QueuedMail(formCreator.email!!, subject, body))
                }
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
                get("") { _, _ ->
                    r.table("responses").run<Any>(conn).queryAsArrayList(globalGson, FormResponseDatabaseWrapper::class.java).let { globalGson.toJson(it) }
                }
            }

            path("/available") {
                get("/submit/:username") { request, _ ->
                    val username = request.params("username")
                    val role = getUser(username).user!!.role
                    val availableForms = getForms().filter { form ->
                        (form.allowMultipleSubmissions || !getResponsesFor(form).asSequence().map { it.submitter }.contains(username) &&
                                (form.creator == username || form.allowedContributors.contains(username) || form.submitRoles.contains(role)))
                    }
                    globalGson.toJson(availableForms)
                }

                get("/created-ids/:username") { request, _ ->
                    globalGson.toJson(getForms().asSequence().filter { it.creator == request.params(":username") }.map { it.id }.toList())
                }

                get("/open/:role/:user") { request, _ ->
                    val role = Role.values().first { it.position == request.params(":role").toInt() }
                    val username = request.params(":user")
                    getForms().asSequence().filterNot { getUser(it.creator).user!!.role == Role.ADMIN }.filter { form ->
                        form.creator == username || (form.submitRoles.contains(null) || form.submitRoles.contains(role) || role == Role.ADMIN)
                    }.toList().let { globalGson.toJson(it) }
                }
            }

            get("/all/:id") { request, _ ->
                val user = getUser(request.params(":id")).user!!
                r.table("forms").run<Any>(conn).queryAsArrayList(globalGson, Form::class.java).filter { form ->
                    form != null && (form.creator == user.username || user.role == Role.ADMIN || form.viewResultRoles.contains(user.role))
                }.let { globalGson.toJson(it) }
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
            get("/generate-response-id") { _, _ -> getRandomResponseId() }
            post("/key") { request, _ -> key = request.body(); Unit }
            get("/random") { _, _ -> getRandomId() }
        }
    }

    fun registerEmailEndpoints() {
        path("/mail") {
            post("/send") { request, _ ->
                val queuedMail = globalGson.fromJson(request.body(), QueuedMail::class.java)
                sendEmail(queuedMail)
            }

            post("/remove/:id") { request, _ ->
                val user = getUser(request.params(":id")).user!!
                user.email = null
                r.table("users").get(request.params(":id")).update(r.json(globalGson.toJson(user))).run<Any>(conn)
            }

            path("/verification") {
                post("/put") { request, _ ->
                    globalGson.fromJson(request.body(), EmailVerification::class.java)?.let { verification ->
                        r.table("email_verification").insert(r.json(globalGson.toJson(verification))).run<Any>(conn)
                        val map = getEmailMap()
                        map["user"] = verification.username
                        map["link"] = "http://$frontendIp/settings/confirm-email/${verification.id}"

                        val body = renderTemplate(map, "email-verification-body.hbs")
                        sendEmail(QueuedMail(verification.email, "CHSForms Email Verification", body))

                    }
                }

                get("/get/:id") { request, _ ->
                    globalGson.toJson(getVerificationRequest(request.params(":id")))
                }

                get("/get-id/:id") { request, _ ->
                    globalGson.toJson(getVerificationRequests().find { it.id == request.params(":id") })
                }

                post("/remove/:id") { request, _ ->
                    r.table("email_verification").get(request.params(":id")).delete().run(conn)
                }

                post("/confirm/:id") { request, _ ->
                    val email = request.body()
                    val username = request.params(":id")
                    val user = getUser(username).user!!
                    user.email = email
                    r.table("users").get(username).update(r.json(globalGson.toJson(user))).run(conn)
                }

                get("/exists/:id") { request, _ ->
                    (getVerificationRequest(request.params(":id")) != null).toString()
                }

            }
        }
    }

    fun registerNotificationsEndpoints() {
        path("/notifications/:username") {
            post("/update") { request, _ ->
                val user = getUser(request.params(":username")).user!!
                user.userNotificationSettings = globalGson.fromJson(request.body(), UserNotificationSettings::class.java)
                r.table("users").get(request.params(":username")).update(r.json(globalGson.toJson(user))).run(conn)
            }
        }
    }

    fun expireOldVerificationRequests() {
        r.table("email_verification").filter { it.g("expiry").lt(System.currentTimeMillis()) }.delete().runNoReply(conn)
    }

    fun getVerificationRequest(username: String) = asPojo(globalGson, r.table("email_verification").get(username).run(conn), EmailVerification::class.java)
    fun getVerificationRequests() = r.table("email_verification").run<Any>(conn).queryAsArrayList(globalGson, EmailVerification::class.java).filterNotNull()

    fun getUser(username: String): SparkUserResponse {
        return SparkUserResponse(asPojo(globalGson, r.table("users").get(username).run(conn), User::class.java),
                asPojo(globalGson, r.table("logins").get(username).run(conn), UserLogin::class.java))
    }

    fun getForms(): List<Form> = r.table("forms").run<Any>(conn).queryAsArrayList(globalGson, Form::class.java).filterNotNull()

    fun getResponsesFor(form: Form): List<FormResponseDatabaseWrapper> {
        return r.table("responses").getAll(form.id).optArg("index", "formId").run<Any>(conn)
                .queryAsArrayList(globalGson, FormResponseDatabaseWrapper::class.java).filterNotNull()
    }

    fun getResponses(): List<FormResponseDatabaseWrapper> {
        return r.table("responses").run<Any>(conn).queryAsArrayList(globalGson, FormResponseDatabaseWrapper::class.java).filterNotNull()
    }

    fun getRandomFormId(): String {
        val randomString = RandomStringUtils.randomAlphanumeric(6)
        return if (getForms().find { it.id == randomString } == null) randomString else getRandomFormId()
    }

    fun getRandomResponseId(): String {
        val randomId = r.uuid().run<String>(conn)
        return if (getResponses().find { it.id == randomId } == null) randomId else getRandomResponseId()
    }

    fun getRandomId(): String = r.uuid().run(conn)

    fun getCredential(id: String) = asPojo(globalGson, r.table("credentials").get(id).run(conn), Credential::class.java)!!.value

    fun sendEmail(queuedMail: QueuedMail) = Mail.using(mailgunConfig)
            .to(queuedMail.to)
            .subject(queuedMail.subject)
            .html(queuedMail.body.replace("\n", "").replace("[nl]", "<br>"))
            .build()
            .sendAsync()

    fun initiateMailgunConfig() {
        mailgunConfig = Configuration()
                .domain("chsforms.adamratzman.com")
                .apiKey(getCredential("mailgun_key"))
                .from("CHSForms (Carmel High School)", "accounts@chsforms.adamratzman.com")
    }

    fun renderTemplate(map: MutableMap<Any, Any>, templatePath: String): String {
        map["key"] = key
        map["path"] = templatePath

        return Jsoup.connect("$frontend/forms/xt/render").requestBody(globalGson.toJson(map)).post().body().text()
    }

    fun getEmailMap() = mutableMapOf<Any, Any>("settingsLink" to "$frontend/settings")
}