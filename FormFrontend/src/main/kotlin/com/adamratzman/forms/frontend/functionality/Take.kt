package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.jsoup.Jsoup
import spark.ModelAndView
import spark.Spark
import spark.Spark.*

fun FormFrontend.registerTakeEndpoints() {
    path("/forms") {
        path("/take") {
            get("/:id") { request, response ->
                val formId = request.params(":id")
                val form = globalGson.fromJson(getFromBackend("/forms/get/$formId"), Form::class.java)
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
            val status: StatusWithRedirect
            val map = getMap(request, "Form Creation")
            val jsonObject = JSONParser().parse(request.body()) as? JSONObject
            if (jsonObject != null) {
                var invalidMessage: String? = null
                val form = (jsonObject["formId"] as? String)?.let { if (it.isBlank()) null else it }?.let {
                    globalGson.fromJson(getFromBackend("/forms/get/$it"), Form::class.java)
                }
                val responses = (jsonObject["responses"] as? JSONArray)?.map { obj ->
                    obj as JSONObject
                    val questionName = obj["questionName"] as? String
                    if (questionName == null) null
                    else {
                        when (form?.formQuestions?.find { it.question == questionName }) {
                            is MultipleChoiceQuestion -> MultipleChoiceAnswer(questionName, obj["selected"] as String)
                            is DropboxQuestion -> DropboxAnswer(questionName, obj["selected"] as String)
                            is NumberQuestion -> NumberAnswer(questionName, (obj["number"] as Number).toFloat())
                            is TextQuestion -> TextAnswer(questionName, obj["text"] as String)
                            is CheckboxQuestion -> CheckboxAnswer(questionName, (obj["selected"] as JSONArray).map { it as String })
                            else -> null
                        }
                    }
                }?.filterNotNull()
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
                                        (question as MultipleChoiceQuestion).options.asSequence().map { it.trim() }.contains(answer.selected.trim())
                                    }
                                    is CheckboxAnswer -> {
                                        (question as CheckboxQuestion).options.map { it.trim() }.containsAll(answer.selected.map { it.trim() })
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
                                }
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
                                    .requestBody(globalGson.toJson(FormResponseDatabaseWrapper(user?.username, FormResponse(form.id!!, responses.toList()), form.id!!)))
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