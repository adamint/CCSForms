package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.frontend.FormFrontend
import com.adamratzman.forms.frontend.utils.getForm
import spark.ModelAndView
import spark.Spark.get
import spark.Spark.path

fun FormFrontend.registerMiscEndpoints() {
    path("/forms/xt") {
        get("/success/:id") { request, _ ->
            val formId = request.params(":id")
            val form = getForm(formId)
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