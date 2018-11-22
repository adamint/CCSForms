package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import org.jsoup.Jsoup
import spark.ModelAndView
import spark.Spark.*

fun FormFrontend.registerManageEndpoints() {
    path("/forms") {
        path("/manage") {
            get("") { request, response ->
                val map = getMap(request, "TEMP")
                val user = map["user"] as? User
                if (user == null) response.redirect(getLoginRedirect(request, "/forms/manage"))
                else {
                    map["pageTitle"] = "Manage Forms"
                    val forms = globalGson.fromJson(getFromBackend("/forms/all/${user.username}"), Array<Form>::class.java)
                    map["hasForms"] = forms.isNotEmpty()
                    map["forms"] = forms
                    map["isAdmin"] = user.role == Role.ADMIN
                    handlebars.render(ModelAndView(map, "manage-home.hbs"))
                }
            }
            path("/:id") {
                get("") { request, response ->
                    val map = getMap(request, "TEMP")
                    val user = map["user"] as? User
                    val formId = request.params(":id")
                    val form = globalGson.fromJson(getFromBackend("/forms/get/$formId"), Form::class.java)
                    if (user == null) response.redirect(getLoginRedirect(request, "/forms/manage/$formId"))
                    else if (form?.id == null || user.username != form.creator) response.redirect("/")
                    else {
                        map["pageTitle"] = "Manage Form | \"${form.name}\""
                        map["form"] = form
                        val responses = globalGson.fromJson(getFromBackend("/forms/responses/$formId"), Array<FormResponse>::class.java)
                        map["numResponses"] = responses.size
                        map["accessibleGroups"] = if (form.submitRoles.contains(null)) "anyone" else form.submitRoles.joinToString { it!!.readable.toLowerCase() + "s" }
                        handlebars.render(ModelAndView(map, "manage-form.hbs"))
                    }
                }
            }
        }
        post("/delete/:id") { request, _ ->
            val id = request.params(":id")
            val form = globalGson.fromJson(getFromBackend("/forms/get/$id"), Form::class.java)
            val map = getMap(request, "TEMP")
            val status = if (form != null && form.creator == (map["user"] as? User)?.username) {
                Jsoup.connect("$databaseBase/forms/delete/$id").post()
                StatusWithRedirect(200, null)
            } else StatusWithRedirect(401, getLoginRedirect(request, "/forms/manage/$id"), "You need to login as the creator of this form")
            globalGson.toJson(status)
        }
    }
}