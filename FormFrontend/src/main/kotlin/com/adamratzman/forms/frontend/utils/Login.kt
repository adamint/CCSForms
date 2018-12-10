package com.adamratzman.forms.frontend.utils

import com.adamratzman.forms.common.models.LoginFailure
import com.adamratzman.forms.common.models.LoginSuccess
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import spark.ModelAndView
import spark.Spark.get
import spark.Spark.post

fun FormFrontend.registerLoginEndpoints() {
    get("/login") { request, response ->
        val map = getMap(request, "Login")
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
            val map = getMap(request, "POST")
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
}