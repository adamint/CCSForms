package com.adamratzman.forms.frontend

import com.adamratzman.forms.common.models.*
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import com.google.gson.Gson
import com.google.gson.JsonParser
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
    val gson = Gson()

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
                    if (username == null || password == null) gson.toJson(LoginFailure(400,
                            "An invalid username or password was specified", 0))
                    val responseText = Jsoup.connect("$databaseBase/login/$username/$password").get().body().text()
                    val successfulResponse: LoginSuccess? = gson.fromJson(responseText, LoginSuccess::class.java)
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
                gson.toJson(StatusWithRedirect(200, redirect))
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

            post("/create") { request, response ->
                val map = getMap(request, "Form Creation")
                if (map["user"] == null) response.redirect(getLoginRedirect("/forms/create"))
                else {
                    val json = JsonParser().parse(request.body())
                    val status: StatusWithRedirect
                    if (json.isJsonObject){
                       val jsonObject = json.asJsonObject
                        val formId = jsonObject.get("formId").asString
                        // add and verify other variables. if everything looks good,
                        // redirect to /forms/manage/formId
                        // which does not exist lol
                    } else status = StatusWithRedirect(400,null, "An unknown error occured")
                    //gson.toJson(status)
                }
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