package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.Form
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import spark.ModelAndView
import spark.Spark.get
import spark.Spark.path

fun FormFrontend.registerMiscEndpoints() {
    path("/forms/xt") {
        get("/success/:id") { request, _ ->
            val formId = request.params(":id")
            val form = globalGson.fromJson(getFromBackend("/forms/get/$formId"), Form::class.java)
            val map = getMap(request, "Take | ${form?.name}")
            if (form == null) {
                map["pageTitle"] = "404"
                map["description"] = "No form with that id was found"
                map["notFound"] = true
                handlebars.render(ModelAndView(map, "error.hbs"))
            } else {
                map["form"] = form
                handlebars.render(ModelAndView(map, "form-creation-success.hbs"))
            }
        }
    }
}