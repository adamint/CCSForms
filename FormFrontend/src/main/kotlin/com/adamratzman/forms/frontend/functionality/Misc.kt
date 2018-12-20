package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import com.adamratzman.forms.frontend.utils.getForm
import org.jsoup.Jsoup
import spark.ModelAndView
import spark.Spark.*

fun FormFrontend.registerMiscEndpoints() {
    path("/forms/xt") {
        get("/success/:id") { request, _ ->
            val formId = request.params(":id")
            val form = getForm(formId)
            val map = getMap(request, "Success > ${form?.name}")
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
        post("/render") { request, _ ->
            val map = globalGson.fromJson(request.body(), MutableMap::class.java)
            if (map == null || map["key"] != key || map["path"] as? String == null) "unauthorized"
            else handlebars.render(ModelAndView(map, map["path"] as String))
        }

        post("/regenerate-key") { _, _ ->
            Jsoup.connect("$databaseBase/utils/key").requestBody(key).post()
        }
    }
}