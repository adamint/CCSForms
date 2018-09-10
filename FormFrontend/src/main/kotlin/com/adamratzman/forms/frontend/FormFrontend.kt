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

        exception(Exception::class.java) { exception, _, _ -> exception.printStackTrace() }

        get("/") { request, _ ->
            val map = getMap(request, "Home")
            handlebars.render(ModelAndView(map, "index.hbs"))
        }

        get("/login") { request, response ->
            val map = getMap(request, "Home")
            when {
                map["user"] != null -> response.redirect("/")
                request.queryParams("redirect") == null -> response.redirect(getLoginRedirect("/?login=true"))
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
                    val responseText = Jsoup.connect("$databaseBase/login/$username/$password").get().body().text()
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
            post("/initial-form-validation") { request, _ ->
                val redirect = "/forms/create-questions?" +
                        request.listOfCreationParams().joinToString("&") {
                            "${it.first}=${encode(it.second?.toString() ?: "no")}"
                        }
                globalGson.toJson(StatusWithRedirect(200, redirect))
            }

            get("/create") { request, response ->
                val map = getMap(request, "Form Creation")
                if (map["user"] == null) response.redirect(getLoginRedirect("/forms/create"))
                else {
                    request.listOfCreationParams().forEach { map[it.first] = it.second }

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

            post("/create") { request, _ ->
                println(request.body())
                val status: StatusWithRedirect
                val map = getMap(request, "Form Creation")
                if (map["user"] == null) status = StatusWithRedirect(401, getLoginRedirect("/forms/create"))
                else {
                    val jsonObject = JSONParser().parse(request.body()) as? JSONObject
                    if (jsonObject != null) {
                        var invalidMessage: String? = null

                        var formId = (jsonObject["formId"] as? String)?.let { if (it.isBlank()) null else it }
                        val formName = jsonObject["formName"] as? String

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
                                    val characterLimit = (questionJson["characterLimit"] as? Long)?.toInt()?.let { if (it <= 0) null else it }
                                    questions.add(TextQuestion(questionName, required, characterLimit))
                                } else if (questionType == 5) {
                                    val minimumNumber = questionJson["minimumNumber"]?.let { it as? Long }?.toDouble()
                                    val maximumNumber = questionJson["maximumNumber"]?.let { it as?Long }?.toDouble()
                                    if (maximumNumber != null && minimumNumber != null && maximumNumber < minimumNumber) {
                                        invalidMessage = "Minimum number greater than maximum number"
                                    } else questions.add(NumberQuestion(questionName, required, minimumNumber, maximumNumber))
                                } else {
                                    val options = (questionJson["options"] as? JSONArray)?.mapNotNull { it as? String }?.toMutableList()
                                    if (options == null || options.size == 1) invalidMessage = "An option-based question has 1 or less set options. This is not allowed"
                                    else questions.add(when (questionType) {
                                        1 -> MultipleChoiceQuestion(questionName, required, options)
                                        2 -> CheckboxQuestion(questionName, required, options)
                                        3 -> DropboxQuestion(questionName, required, options)
                                        else -> throw IllegalArgumentException("= 1,2,3 must yield 1, 2, or 3. You broke math")
                                    })
                                }
                            }

                            if (formId != null) {
                                // this is for form updating and will completed later
                            } else {
                                // form *creation*, no existing form id. one must be generated
                                val form = Form(null, (map["user"] as User).username, formName, category, submitRoles, viewRoles, mutableListOf(),
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

    private fun Request.listOfCreationParams(): List<Pair<String, Any?>> {
        return listOf("fn" to queryParams("formName"),
                "ams" to queryParams("allowMultipleSubmissions")?.equals("yes"),
                "c" to queryParams("category"),
                "sa" to queryParams("submitAnyone")?.equals("on"),
                "ss" to queryParams("submitStudents")?.equals("on"),
                "st" to queryParams("submitTeachers")?.equals("on"),
                "va" to queryParams("viewAnyone")?.equals("on"),
                "vs" to queryParams("viewStudents"),
                "vt" to queryParams("viewTeachers"),
                "vc" to queryParams("viewCounseling"),
                "ed" to queryParams("endDate"))
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

    private fun getLoginRedirect(url: String) = "/login?redirect=${encode(url)}"
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
}