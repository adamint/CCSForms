package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import com.adamratzman.forms.frontend.utils.getForm
import com.adamratzman.forms.frontend.utils.getFormWithManagementPermission
import com.adamratzman.forms.frontend.utils.getResponses
import com.adamratzman.forms.frontend.utils.getResponsesFor
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
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
                    val forms = getFormWithManagementPermission(user.username)
                    map["hasForms"] = forms.isNotEmpty()
                    map["forms"] = forms
                    map["isAdmin"] = user.role == Role.ADMIN
                    handlebars.render(ModelAndView(map, "manage-home.hbs"))
                }
            }

            get("/responses/:id") { request, response ->
                val formId = request.params(":id")
                val map = getMap(request, "TEMP")
                val user = map["user"] as? User
                if (user == null) response.redirect(getLoginRedirect(request, "/forms/manage/responses/$formId"))
                else {
                    val form = getFormWithManagementPermission(user.username).find { formId == it.id }
                    if (form == null) {
                        map["pageTitle"] = "You can't access the responses to this form"
                        map["description"] = "You don't have access to manage this form. Please make sure your username (${user.username}) and role (${user.role}) are able to manage the form."
                    handlebars.render(ModelAndView(map, "error.hbs"))
                    } else {
                        map["pageTitle"] = "Responses | ${form.name}"
                        map["form"] = form
                        map["formJson"] = globalGson.toJson(form)
                        val responses = globalGson.fromJson(getFromBackend("/forms/responses/${form.id}"), Array<FormResponseDatabaseWrapper>::class.java)
                                .sortedBy { it.time }
                        map["responses"] = globalGson.toJson(responses)
                        handlebars.render(ModelAndView(map, "manage-responses.hbs"))
                    }
                }
            }

            path("/:id") {
                get("") { request, response ->
                    val map = getMap(request, "TEMP")
                    val user = map["user"] as? User
                    val formId = request.params(":id")
                    val form = getForm(formId)
                    when {
                        user == null -> response.redirect(getLoginRedirect(request, "/forms/manage/$formId"))
                        form?.id == null -> response.redirect("/")
                        getFormWithManagementPermission(user.username).find { it.id == formId } == null -> {
                            map["pageTitle"] = "No Permission"
                            map["description"] = "You don't have permission to access this form's management page."
                            handlebars.render(ModelAndView(map, "error.hbs"))
                        }
                        else -> {
                            map["pageTitle"] = "Manage Form | \"${form.name}\""
                            map["form"] = form
                            val responses = getResponsesFor(formId)
                            map["numResponses"] = responses.size
                            map["accessibleGroups"] = if (form.submitRoles.contains(null)) "anyone" else form.submitRoles.joinToString { it!!.readable.toLowerCase() + "s" }
                            handlebars.render(ModelAndView(map, "manage-form.hbs"))
                        }
                    }
                }
            }

            path("/response") {
                get("/:id") { request, response ->
                    val map = getMap(request, "TEMP")
                    val user = map["user"] as? User
                    val responseId = request.params(":id")
                    val formResponse = getResponses().find { it.id == responseId}
                    val form = formResponse?.let { getForm(it.formId) }

                    when {
                        user == null -> response.redirect(getLoginRedirect(request, "/forms/manage/response/$responseId"))
                        formResponse == null || form == null -> response.redirect("/forms/manage")
                        getFormWithManagementPermission(user.username).find { it.id == form.id } == null -> response.redirect("/forms/manage")
                        else -> {
                            map["pageTitle"] = "${formResponse.submitter}'s response | ${form.name}"
                            map["dbResponse"] = globalGson.toJson(formResponse)
                            map["form"] = form
                            map["questions"] = form.formQuestions.map {
                                globalGson.toJson(when (it) {
                                    is MultipleChoiceQuestion -> 1
                                    is CheckboxQuestion -> 2
                                    is DropboxQuestion -> 3
                                    is TextQuestion -> 4
                                    is NumberQuestion -> 5
                                    else -> throw IllegalArgumentException("${it.type} isn't in 1-5")
                                } to it)
                            }
                            handlebars.render(ModelAndView(map, "take-form.hbs"))
                        }
                    }
                }
                post("/delete") { request, _ ->
                    val map = getMap(request, "POST")
                    val user = map["user"]
                    val json = JSONParser().parse(request.body()) as? JSONObject
                    val formId = json?.get("formId") as? String
                    val responseId = json?.get("responseId") as? String
                    (if (user !is User || json == null || formId == null || responseId == null) StatusWithRedirect(400, null, message = "Invalid parameters sent to server")
                    else {
                        val form = globalGson.fromJson(getFromBackend("/forms/get/$formId"), Form::class.java)
                        val response = getResponsesFor(formId)
                        if (form == null || response == null) StatusWithRedirect(404, null, message = "No form/response found with those ids")
                        else if (getFormWithManagementPermission(user.username).find { it.id == form.id } == null) StatusWithRedirect(401, null, "You don't have permission to do that!")
                        else {
                            Jsoup.connect("$databaseBase/forms/response/delete/$responseId").post()
                            StatusWithRedirect(200, null, "Success")
                        }
                    }).let { globalGson.toJson(it) }
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