package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.Form
import com.adamratzman.forms.common.models.Role
import com.adamratzman.forms.common.models.User
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import spark.ModelAndView
import spark.Spark.get

fun FormFrontend.registerAvailableEndpoint() {
    get("/forms/available") { request, _ ->
        val map = getMap(request, "Available Forms")
        val user = map["user"] as? User
        val role = user?.role ?: Role.NOT_LOGGED_IN
        val forms = getFromBackend("/forms/available/open/${role.position}/${user?.username ?: "null"}").let {
            globalGson.fromJson(it, Array<Form>::class.java)
        }
        map["forms"] = forms
        map["total"] = forms.size
        map["userString"] = role.toString()
        handlebars.render(ModelAndView(map, "available.hbs"))
    }
}