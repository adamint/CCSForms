package com.adamratzman.forms.frontend.utils

import com.adamratzman.forms.frontend.FormFrontend
import spark.ModelAndView
import spark.Spark.*

fun FormFrontend.registerErrorEndpoints() {
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
}