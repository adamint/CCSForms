package com.adamratzman.forms.frontend

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.globalGson
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.jsoup.Jsoup
import spark.ModelAndView
import spark.Request
import spark.Spark.*
import spark.template.handlebars.HandlebarsTemplateEngine
import java.net.URLEncoder

fun main(args: Array<String>) {
    FormFrontend()
}

class FormFrontend {
    val databaseBase = "http://backend"
    val handlebars = HandlebarsTemplateEngine()

    init {
        port(80)
        staticFileLocation("/public")

        registerHandlebarsHelpers()

        notFound { request, response ->
            val map = getMap(request, "Not Found")
            map["notFound"] = true
            map["description"] = "The page you were looking for couldn't be found :("
            handlebars.render(ModelAndView(map, "error.hbs"))
        }

        internalServerError { request, response ->
            val map = getMap(request, "Something went wrong!")
            map["title"] = "Server Error"
            map["description"] = "Please try again soon"
            handlebars.render(ModelAndView(map, "error.hbs"))
        }

        exception(Exception::class.java) { exception, _, _ -> exception.printStackTrace() }


        get("/") { request, _ ->
            val map = getMap(request, "Home")
            handlebars.render(ModelAndView(map, "index.hbs"))
        }

        get("/login") { request, response ->
            val map = getMap(request, "Home")
            when {
                map["user"] != null -> response.redirect("/")
                request.queryParams("redirect") == null -> response.redirect(getLoginRedirect(request, "/?login=true"))
                else -> handlebars.render(ModelAndView(map, "login.hbs"))
            }
        }

        get("/logout") { request, response ->
            request.session().removeAttribute("user")
            response.redirect("/?logout=true")
        }

        post("/login") { request, response ->
            try {
                val map = getMap(request, "Login")
                if (map["user"] != null) response.redirect("/")
                else {
                    val username = request.queryParams("username")
                    val password = request.queryParams("password")
                    if (username == null || password == null) globalGson.toJson(LoginFailure(400,
                            "An invalid username or password was specified", 0))
                    val responseText = getFromBackend("/login/$username/$password")
                    val successfulResponse: LoginSuccess? = globalGson.fromJson(responseText, LoginSuccess::class.java)
                    if (successfulResponse != null) {
                        request.session().attribute("user", successfulResponse.user)
                    }
                    responseText
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        path("/forms") {
            path("/take") {
                get("/:id") { request, response ->
                    val formId = request.params(":id")
                    val form = globalGson.fromJson(getFromBackend("/forms/get/$formId"), Form::class.java)
                    val map = getMap(request, "Take | ${form?.name}")
                    if (form == null) {
                        map["pageTitle"] = "404"
                        map["description"] = "No form with that id was found"
                        map["notFound"] = true
                        handlebars.render(ModelAndView(map, "error.hbs"))
                    } else {
                        val user = map["user"] as? User
                        if (user == null && !form.submitRoles.contains(null)) response.redirect(getLoginRedirect(request, "/forms/take/$formId", "This form requires you to log in"))
                        else if (user != null && !form.submitRoles.contains(user.role) && user.username != form.creator) {
                            map["pageTitle"] = "Unauthorized"
                            map["description"] = "You don't have permission to take this form. If you think this is in error, please contact ${form.creator}"
                            handlebars.render(ModelAndView(map, "error.hbs"))
                        } else if (!form.allowMultipleSubmissions && user != null && user.username != form.creator
                                && getFromBackend("/forms/info/${form.id}/taken/${user.username}") == "true") {
                            map["pageTitle"] = "You've already taken this form!"
                            map["description"] = "You don't have permission to take it again. If you think this is in error, please contact ${form.creator}"
                            handlebars.render(ModelAndView(map, "error.hbs"))
                        } else {
                            // render the form
                            map["form"] = form
                            map["questions"] = form.formQuestions.map {
                                globalGson.toJson(when (it) {
                                    is MultipleChoiceQuestion -> 1
                                    is CheckboxQuestion -> 2
                                    is DropboxQuestion -> 3
                                    is TextQuestion -> 4
                                    is NumberQuestion -> 5
                                    else -> throw IllegalArgumentException("${it.type} isn't in 1-5")
                                } to it)
                            }
                            handlebars.render(ModelAndView(map, "take-form.hbs"))
                        }
                    }
                }
            }
            path("/manage") {
                get("") { request, response ->
                    val map = getMap(request, "TEMP")
                    val user = map["user"] as? User
                    if (user == null) response.redirect(getLoginRedirect(request, "/forms/manage"))
                    else {
                        map["pageTitle"] = "Manage Forms"
                        println(getFromBackend("/forms/all/${user.username}"))
                        val forms = globalGson.fromJson(getFromBackend("/forms/all/${user.username}"), Array<Form>::class.java)
                        map["hasForms"] = forms.isNotEmpty()
                        map["forms"] = forms
                        map["isAdmin"] = user.role == Role.ADMIN
                        handlebars.render(ModelAndView(map, "manage-home.hbs"))
                    }
                }
                path("/:id") {
                    get("") { request, response ->
                        val map = getMap(request, "TEMP")
                        val user = map["user"] as? User
                        val formId = request.params(":id")
                        val form = globalGson.fromJson(getFromBackend("/forms/get/$formId"), Form::class.java)
                        if (user == null) response.redirect(getLoginRedirect(request, "/forms/manage/$formId"))
                        else if (form?.id == null || user.username != form.creator) response.redirect("/")
                        else {
                            map["pageTitle"] = "Manage Form | \"${form.name}\""
                            map["form"] = form
                            val responses = globalGson.fromJson(getFromBackend("/forms/responses/$formId"), Array<FormResponse>::class.java)
                            map["numResponses"] = responses.size
                            map["accessibleGroups"] = if (form.submitRoles.contains(null)) "anyone" else form.submitRoles.joinToString { it!!.readable.toLowerCase() + "s" }
                            handlebars.render(ModelAndView(map, "manage-form.hbs"))
                        }
                    }
                }
            }

            get("/create") { request, response ->
                val map = getMap(request, "Form Creation")
                if (map["user"] == null) response.redirect(getLoginRedirect(request, "/forms/create"))
                else {
                    if (request.queryParams("existing") != null) {
                        val existingId = request.queryParams("existing")
                        val userCreatedIds = globalGson.fromJson(getFromBackend("/forms/available/created-ids/${(map["user"] as User).username}"),
                                Array<String>::class.java)
                        if (userCreatedIds.contains(existingId)) {
                            val form = globalGson.fromJson(getFromBackend("/forms/get/$existingId"), Form::class.java)
                            map.putAll(
                                    mapOf("formId" to form.id,
                                            "fn" to form.name,
                                            "fd" to form.description,
                                            "ams" to form.allowMultipleSubmissions,
                                            "sa" to form.submitRoles.contains(null),
                                            "ss" to form.submitRoles.contains(Role.STUDENT),
                                            "st" to form.submitRoles.contains(Role.TEACHER),
                                            "ed" to form.expireDate,
                                            "va" to form.viewResultRoles.contains(null),
                                            "vs" to form.viewResultRoles.contains(Role.STUDENT),
                                            "vt" to form.viewResultRoles.contains(Role.TEACHER),
                                            "vc" to form.viewResultRoles.contains(Role.COUNSELING),
                                            "questions" to form.formQuestions.map {
                                                globalGson.toJson(when (it) {
                                                    is MultipleChoiceQuestion -> 1
                                                    is CheckboxQuestion -> 2
                                                    is DropboxQuestion -> 3
                                                    is TextQuestion -> 4
                                                    is NumberQuestion -> 5
                                                    else -> throw IllegalArgumentException("${it.type} isn't in 1-5")
                                                } to it)
                                            })
                            )
                        }
                    }

                    map["datePicker"] = true

                    val availableCategories = mutableListOf(FormCategory.PERSONAL)
                    val role = map["role"] as Role
                    if (role == Role.ATHLETICS || role == Role.ADMIN) availableCategories.add(FormCategory.ATHLETICS)
                    if (role == Role.COUNSELING || role == Role.ADMIN) availableCategories.add(FormCategory.COUNSELING)

                    map["availableCategories"] = availableCategories
                    map["notStudent"] = role != Role.STUDENT
                    handlebars.render(ModelAndView(map, "create-form.hbs"))
                }
            }

            post("/delete/:id") { request, _ ->
                val id = request.params(":id")
                val form = globalGson.fromJson(getFromBackend("/forms/get/$id"), Form::class.java)
                val map = getMap(request, "TEMP")
                val status = if (form != null && form.creator == (map["user"] as? User)?.username) {
                    Jsoup.connect("$databaseBase/forms/delete/$id").post()
                    StatusWithRedirect(200, null)
                } else StatusWithRedirect(401, getLoginRedirect(request, "/forms/manage/$id"), "You need to login as the creator of this form")
                globalGson.toJson(status)
            }

            post("/create") { request, _ ->
                println(request.body())
                val status: StatusWithRedirect
                val map = getMap(request, "Form Creation")
                if (map["user"] == null) status = StatusWithRedirect(401, getLoginRedirect(request, "/forms/create"))
                else {
                    val jsonObject = JSONParser().parse(request.body()) as? JSONObject
                    if (jsonObject != null) {
                        var invalidMessage: String? = null

                        var formId = (jsonObject["formId"] as? String)?.let { if (it.isBlank()) null else it }
                        val formName = jsonObject["formName"] as? String
                        val description = jsonObject["description"] as? String

                        val allowMultipleSubmissions = (jsonObject["allowMultipleSubmissions"] as? String) == "on"
                        val category = (jsonObject["category"] as? String)?.let { strCat -> FormCategory.values().find { strCat == it.readable } }
                                ?: FormCategory.PERSONAL

                        val anyoneSubmit = jsonObject["anyoneSubmit"] as? Boolean ?: false
                        val studentSubmit = jsonObject["studentSubmit"] as? Boolean ?: false
                        val teacherSubmit = jsonObject["teacherSubmit"] as? Boolean ?: false

                        val submitRoles = listOf(null to anyoneSubmit, Role.STUDENT to studentSubmit,
                                Role.TEACHER to teacherSubmit).filter { it.second }.map { it.first }

                        val viewAnyone: Boolean
                        val viewStudents: Boolean
                        val viewTeachers: Boolean
                        val viewCounseling: Boolean

                        if ((map["user"] as User).role != Role.STUDENT) {
                            viewAnyone = jsonObject["viewAnyone"] as? Boolean ?: false
                            viewStudents = jsonObject["viewStudents"] as? Boolean ?: false
                            viewTeachers = jsonObject["viewTeachers"] as? Boolean ?: false
                            viewCounseling = jsonObject["viewCounseling"] as? Boolean ?: false
                        } else {
                            viewAnyone = false
                            viewStudents = false
                            viewTeachers = false
                            viewCounseling = false
                        }

                        val viewRoles = listOf(null to viewAnyone, Role.STUDENT to viewStudents,
                                Role.TEACHER to viewTeachers, Role.COUNSELING to viewCounseling).filter { it.second }
                                .map { it.first }

                        val endDate = (jsonObject["endDate"] as? Long)?.let {
                            if (it <= System.currentTimeMillis()) null else it
                        }

                        if (!anyoneSubmit && !studentSubmit && !teacherSubmit) invalidMessage = "A group must be able to submit this form"
                        else if (formName == null) invalidMessage = "Form name was submitted as null"
                        else if (description == null) invalidMessage = "Description cannot be null"
                        else {
                            val questions = mutableListOf<FormQuestion>()
                            (jsonObject["questions"] as? JSONArray)?.forEach { questionJson ->
                                questionJson as JSONObject
                                val questionType = (questionJson["type"] as? Long)?.toInt()
                                val questionName = questionJson["question"] as? String
                                val required = questionJson["required"] as? Boolean ?: true
                                if (questionType !in 1..5 || questionName == null || questionName.length < 5) {
                                    invalidMessage = "Invalid question composition contained"
                                    return@forEach
                                } else if (questionType == 4) {
                                    val wordLimit = (questionJson["wordLimit"] as? Long)?.toInt()?.let { if (it <= 0) null else it }
                                    questions.add(TextQuestion(questionName, required, wordLimit))
                                } else if (questionType == 5) {
                                    val minimumNumber = questionJson["minimumNumber"]?.let { it as? Long }?.toDouble()
                                    val maximumNumber = questionJson["maximumNumber"]?.let { it as?Long }?.toDouble()
                                    if (maximumNumber != null && minimumNumber != null && maximumNumber < minimumNumber) {
                                        invalidMessage = "Minimum number greater than maximum number"
                                    } else questions.add(NumberQuestion(questionName, required, minimumNumber, maximumNumber))
                                } else {
                                    val includeOtherOption = questionJson["includeOtherOption"] as? Boolean ?: false
                                    val options = (questionJson["options"] as? JSONArray)?.mapNotNull { it as? String }?.toMutableList()
                                    if (options == null || options.size == 1) invalidMessage = "An option-based question has 1 or less set options. This is not allowed"
                                    else questions.add(when (questionType) {
                                        1 -> MultipleChoiceQuestion(questionName, required, includeOtherOption, options)
                                        2 -> CheckboxQuestion(questionName, required, includeOtherOption, options)
                                        3 -> DropboxQuestion(questionName, required, includeOtherOption, options)
                                        else -> throw IllegalArgumentException("= 1,2,3 must yield 1, 2, or 3. You broke math")
                                    })
                                }
                            }

                            if (formId != null) {
                                val allowed = globalGson.fromJson<List<String>>(
                                        getFromBackend("/forms/available/created-ids/${(map["user"] as User).username}"),
                                        List::class.java)
                                if (allowed.contains(formId)) {
                                    val form = Form(formId, (map["user"] as User).username, formName, description, category, submitRoles, viewRoles, mutableListOf(),
                                            mutableListOf(), allowMultipleSubmissions, System.currentTimeMillis(), endDate, true, questions)
                                    val updateStatus = globalGson.fromJson(Jsoup.connect("$databaseBase/forms/create").requestBody(globalGson.toJson(form)).post().body().text()
                                            , StatusWithRedirect::class.java)
                                    if (updateStatus.status == 200) formId = updateStatus.message
                                } else invalidMessage = "You're not able to edit this form!"
                            } else {
                                // form *creation*, no existing form id. one must be generated
                                val form = Form(null, (map["user"] as User).username, formName, description, category, submitRoles, viewRoles, mutableListOf(),
                                        mutableListOf(), allowMultipleSubmissions, System.currentTimeMillis(), endDate, true, questions)
                                val creationStatus = globalGson.fromJson(Jsoup.connect("$databaseBase/forms/create").requestBody(globalGson.toJson(form)).post().body().text()
                                        , StatusWithRedirect::class.java)
                                if (creationStatus.status == 200) formId = creationStatus.message
                            }

                            println("$formId\n$formName\n$allowMultipleSubmissions\n$category\n" +
                                    "$anyoneSubmit\n$studentSubmit\n$teacherSubmit\n$viewAnyone\n" +
                                    "$viewStudents\n$viewTeachers\n$viewCounseling\n$endDate\n${globalGson.toJson(questions)}")
                        }

                        status = if (invalidMessage != null) StatusWithRedirect(400, null, invalidMessage)
                        else StatusWithRedirect(200, "/forms/manage/$formId")
                    } else status = StatusWithRedirect(400, null, "An unknown error occured")
                }
                globalGson.toJson(status)
            }
        }
    }

    private fun getMap(request: Request, pageTitle: String): HashMap<String, Any?> {
        val map = hashMapOf<String, Any?>()
        val session = request.session()
        val user: User? = session.attribute<User>("user")
        map["user"] = user
        map["role"] = user?.role ?: Role.NOT_LOGGED_IN
        map["pageTitle"] = pageTitle

        return map
    }

    private fun getLoginRedirect(request: Request, url: String, message: String? = null) = "/login?redirect=" +
            encode(url + request.queryMap().toMap().toList()
                    .mapIndexed { i, pair -> (if (i == 0) "?" else "") + "${pair.first}=${pair.second.getOrNull(0)}" }
                    .joinToString("&")) + (if (message != null) "&message=${encode(message)}" else "")

    private fun encode(thing: String) = URLEncoder.encode(thing, "UTF-8")

    private fun registerHandlebarsHelpers() {
        val field = handlebars::class.java.getDeclaredField("handlebars")
        field.isAccessible = true
        val handle = field.get(handlebars) as Handlebars
        handle.registerHelper("streq") { first: Any?, options: Options ->
            if (options.params[0].toString().equals(first?.toString(), true)) {
                options.fn()
            } else options.inverse()
        }
    }

    private fun getFromBackend(path: String): String {
        return Jsoup.connect("$databaseBase$path").get().body().text()
    }
}