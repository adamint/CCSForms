package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import com.adamratzman.forms.frontend.toDate
import com.adamratzman.forms.frontend.utils.getForm
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.jsoup.Jsoup
import spark.ModelAndView
import spark.Spark.*

fun FormFrontend.registerTakeEndpoints() {
    path("/forms") {
        path("/take") {
            get("/:id") { request, response ->
                val formId = request.params(":id")
                val form = getForm(formId)
                val map = getMap(request, "Take | ${form?.name}")
                if (form == null) {
                    map["pageTitle"] = "404"
                    map["description"] = "No form with that id was found"
                    map["notFound"] = true
                    handlebars.render(ModelAndView(map, "error.hbs"))
                } else {
                    val user = map["user"] as? User
                    if (user == null && !form.submitRoles.contains(null)) response.redirect(getLoginRedirect(request, "/forms/take/$formId", "This form requires you to log in"))
                    else if (user != null && !form.submitRoles.contains(user.role) && user.username != form.creator) {
                        map["pageTitle"] = "Unauthorized"
                        map["description"] = "You don't have permission to take this form. If you think this is in error, please contact ${form.creator}"
                        handlebars.render(ModelAndView(map, "error.hbs"))
                    } else if (!form.allowMultipleSubmissions && user != null && user.username != form.creator
                            && getFromBackend("/forms/info/${form.id}/taken/${user.username}") == "true") {
                        map["pageTitle"] = "You've already taken this form!"
                        map["description"] = "You don't have permission to take it again. If you think this is in error, please contact ${form.creator}"
                        handlebars.render(ModelAndView(map, "error.hbs"))
                    } else if (form.expireDate != null && form.expireDate!! < System.currentTimeMillis()) {
                        map["pageTitle"] = "Form not available anymore"
                        map["description"] = "This form isn't available to take anymore. It closed on ${form.expireDate!!.toDate()}"
                        handlebars.render(ModelAndView(map, "error.hbs"))
                    } else {
                        // render the form
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
        }

        post("/submit") { request, _ ->
            println(request.body())
            val status: StatusWithRedirect
            val map = getMap(request, "Form Creation")
            val jsonObject = JSONParser().parse(request.body()) as? JSONObject
            if (jsonObject != null) {
                var invalidMessage: String? = null
                val form = (jsonObject["formId"] as? String)?.let { if (it.isBlank()) null else it }?.let {
                    getForm(it)
                }
                val responses = (jsonObject["responses"] as? JSONArray)?.mapNotNull { obj ->
                    obj as JSONObject
                    val questionName = obj["questionName"] as? String
                    if (questionName == null) null
                    else {
                        when (form?.formQuestions?.find { it.question == questionName }) {
                            is MultipleChoiceQuestion -> MultipleChoiceAnswer(questionName, (obj["selected"] as String).trim())
                            is DropboxQuestion -> DropboxAnswer(questionName, (obj["selected"] as String).trim())
                            is NumberQuestion -> NumberAnswer(questionName, (obj["number"] as Number).toFloat())
                            is TextQuestion -> TextAnswer(questionName, (obj["text"] as String).trim())
                            is CheckboxQuestion -> CheckboxAnswer(questionName, (obj["selected"] as JSONArray).map { it as String }.map { it.trim() })
                            else -> null
                        }
                    }
                }
                if (responses == null || form == null) invalidMessage = "Invalid parameters sent."
                else {
                    val mappedQuestionResponses = form.formQuestions.map { question ->
                        responses.find { it.questionName == question.question }?.let { pairedResponse ->
                            when (pairedResponse) {
                                is MultipleChoiceAnswer -> question is MultipleChoiceQuestion
                                is CheckboxAnswer -> question is CheckboxQuestion
                                is DropboxAnswer -> question is DropboxQuestion
                                is NumberAnswer -> question is NumberQuestion
                                is TextAnswer -> question is TextQuestion
                                else -> false
                            }.let { if (it) question to pairedResponse else question to null }
                        } ?: question to null
                    }
                    var submit = true
                    mappedQuestionResponses.forEach { (question, answer) ->
                        if (question.required && answer == null) submit = false
                        else {
                            answer?.let { _ ->
                                when (answer) {
                                    is MultipleChoiceAnswer -> {
                                        val selected = answer.selected.trim()
                                        question as MultipleChoiceQuestion
                                        question.includeOtherOption || question.options.asSequence().map { it.trim() }.contains(selected)
                                    }
                                    is CheckboxAnswer -> {
                                        val selected = answer.selected.map { it.trim() }
                                        question as CheckboxQuestion
                                        selected.filter { select ->
                                            question.options.asSequence().map { it.trim().toLowerCase() }.contains(select.toLowerCase())
                                                    || question.includeOtherOption
                                        }.size == selected.size
                                    }
                                    is DropboxAnswer -> {
                                        (question as DropboxQuestion).options.asSequence().map { it.trim() }.contains(answer.selected.trim())
                                    }
                                    is NumberAnswer -> {
                                        (question as NumberQuestion).let {
                                            var valid = true
                                            if (question.onlyWholeNumbers) valid = answer.chosen.toInt().toFloat() == answer.chosen
                                            if (question.maximumNumber != null && valid) valid = question.maximumNumber!! >= answer.chosen
                                            if (question.minimumNumber != null && valid) valid = question.minimumNumber!! <= answer.chosen
                                            valid
                                        }
                                    }
                                    is TextAnswer -> {
                                        (question as TextQuestion).wordLimit?.let { wordLimit ->
                                            wordLimit >= answer.text.split(" ").filter { it.trim() == it }.size
                                        } ?: true
                                    }
                                    else -> throw IllegalArgumentException()
                                }.let { if (!it) submit = false }
                            }
                        }
                    }
                    if (!submit) invalidMessage = "A field was incorrectly submitted"
                    else {
                        val user = map["user"] as? User
                        if (!form.submitRoles.contains(user?.role) && form.creator != user?.username) invalidMessage = "You're not allowed to submit this form!"
                        else if (!form.allowMultipleSubmissions && getFromBackend("/forms/info/${form.id}/taken/${user?.username
                                        ?: "null"}").toBoolean()) {
                            invalidMessage = "You've already submitted this form!"
                        } else {
                            // CAN submit
                            Jsoup.connect("$databaseBase/forms/submit")
                                    .requestBody(globalGson.toJson(
                                            FormResponseDatabaseWrapper(
                                                    user?.username, FormResponse(form.id!!, responses.toList()), form.id!!,
                                                    id = getFromBackend("/utils/generate-response-id")
                                            )))
                                    .post()
                        }
                    }
                }
                status = if (invalidMessage == null) StatusWithRedirect(200, "/forms/xt/success/${form!!.id}")
                else StatusWithRedirect(400, "/", invalidMessage)
            } else status = StatusWithRedirect(400, redirect = "/", message = "Invalid request")

            globalGson.toJson(status)
        }
    }
}