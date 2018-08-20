package com.adamratzman.forms.frontend

import com.adamratzman.forms.common.models.*
import com.google.gson.Gson
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
                val map = getMap(request, "Home")
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
            get("/create") { request, response ->
                val map = getMap(request, "Form Creation")
                if (map["user"] == null) response.redirect(getLoginRedirect("/forms/create"))
                else {
                    val availableCategories = mutableListOf(FormCategory.PERSONAL)
                    val role = map["role"] as Role
                    if (role == Role.ATHLETICS || role == Role.ADMIN) availableCategories.add(FormCategory.ATHLETICS)
                    if (role == Role.COUNSELING || role == Role.ADMIN) availableCategories.add(FormCategory.COUNSELING)

                    map["availableCategories"] = availableCategories
                    map["notStudent"] = role != Role.STUDENT
                    handlebars.render(ModelAndView(map, "create-form.hbs"))
                }
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

    private fun getLoginRedirect(url: String) = "/login?redirect=${URLEncoder.encode(url, "UTF-8")}"
}