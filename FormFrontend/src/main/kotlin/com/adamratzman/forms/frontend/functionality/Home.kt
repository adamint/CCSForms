package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.frontend.FormFrontend
import spark.ModelAndView
import spark.Spark

fun FormFrontend.registerHomeEndpoint() {
    Spark.get("/") { request, _ ->
        val map = getMap(request, "Home")
        handlebars.render(ModelAndView(map, "index.hbs"))
    }
}