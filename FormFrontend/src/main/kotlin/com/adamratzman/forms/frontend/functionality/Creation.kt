package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.*
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import com.adamratzman.forms.frontend.utils.getForm
import com.adamratzman.forms.frontend.utils.getFormWithManagementPermission
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.jsoup.Jsoup
import spark.ModelAndView
import spark.Spark.*

fun FormFrontend.registerCreationEndpoints() {
    path("/forms") {
        get("/create") { request, response ->
            val map = getMap(request, "Form Creation")
            if (map["user"] == null) response.redirect(getLoginRedirect(request, "/forms/create"))
            else {
                if (request.queryParams("existing") != null) {
                    val existingId = request.queryParams("existing")
                    if (getFormWithManagementPermission((map["user"] as User).username).find { it.id == existingId } != null) {
                        val form = getForm(existingId)
                        map.putAll(
                                mapOf("formId" to form.id,
                                        "fn" to form.name,
                                        "fd" to form.description,
                                        "c" to form.category,
                                        "ams" to form.allowMultipleSubmissions,
                                        "sa" to form.submitRoles.contains(null),
                                        "ss" to form.submitRoles.contains(Role.STUDENT),
                                        "st" to form.submitRoles.contains(Role.TEACHER),
                                        "ed" to form.expireDate,
                                        "va" to form.viewResultRoles.contains(null),
                                        "vs" to form.viewResultRoles.contains(Role.STUDENT),
                                        "vt" to form.viewResultRoles.contains(Role.TEACHER),
                                        "vc" to form.viewResultRoles.contains(Role.COUNSELING),
                                        "questions" to form.formQuestions.map {
                                            globalGson.toJson(when (it) {
                                                is MultipleChoiceQuestion -> 1
                                                is CheckboxQuestion -> 2
                                                is DropboxQuestion -> 3
                                                is TextQuestion -> 4
                                                is NumberQuestion -> 5
                                                else -> throw IllegalArgumentException("${it.type} isn't in 1-5")
                                            } to it)
                                        })
                        )
                    } else response.redirect("/create")
                }

                map["datePicker"] = true

                val availableCategories = mutableListOf(FormCategory.PERSONAL)
                val role = map["role"] as Role
                if (role == Role.ATHLETICS || role == Role.ADMIN) availableCategories.add(FormCategory.ATHLETICS)
                if (role == Role.COUNSELING || role == Role.ADMIN) availableCategories.add(FormCategory.COUNSELING)

                map["availableCategories"] = availableCategories
                map["notStudent"] = role != Role.STUDENT
                handlebars.render(ModelAndView(map, "create-form.hbs"))
            }
        }

        post("/create") { request, _ ->
            val status: StatusWithRedirect
            val map = getMap(request, "Form Creation")
            if (map["user"] == null) status = StatusWithRedirect(401, getLoginRedirect(request, "/forms/create"))
            else {
                val jsonObject = JSONParser().parse(request.body()) as? JSONObject
                if (jsonObject != null) {
                    var invalidMessage: String? = null

                    var formId = (jsonObject["formId"] as? String)?.let { if (it.isBlank()) null else it }
                    val formName = jsonObject["formName"] as? String
                    val description = jsonObject["description"] as? String

                    val allowMultipleSubmissions = (jsonObject["allowMultipleSubmissions"] as? String) == "yes"
                    val category = (jsonObject["category"] as? String)?.let { strCat ->
                        FormCategory.values().find { strCat.equals(it.readable, true) }
                    } ?: FormCategory.PERSONAL

                    val anyoneSubmit = jsonObject["anyoneSubmit"] as? Boolean ?: false
                    val studentSubmit = jsonObject["studentSubmit"] as? Boolean ?: false
                    val teacherSubmit = jsonObject["teacherSubmit"] as? Boolean ?: false

                    val submitRoles = listOf(null to anyoneSubmit, Role.STUDENT to studentSubmit,
                            Role.TEACHER to teacherSubmit).filter { it.second }.map { it.first }

                    val viewAnyone: Boolean
                    val viewStudents: Boolean
                    val viewTeachers: Boolean
                    val viewCounseling: Boolean

                    if ((map["user"] as User).role != Role.STUDENT) {
                        viewAnyone = jsonObject["viewAnyone"] as? Boolean ?: false
                        viewStudents = jsonObject["viewStudents"] as? Boolean ?: false
                        viewTeachers = jsonObject["viewTeachers"] as? Boolean ?: false
                        viewCounseling = jsonObject["viewCounseling"] as? Boolean ?: false
                    } else {
                        viewAnyone = false
                        viewStudents = false
                        viewTeachers = false
                        viewCounseling = false
                    }

                    val viewRoles = listOf(null to viewAnyone, Role.STUDENT to viewStudents,
                            Role.TEACHER to viewTeachers, Role.COUNSELING to viewCounseling).filter { it.second }
                            .map { it.first }

                    val endDate = (jsonObject["endDate"] as? Long)?.let {
                        if (it <= System.currentTimeMillis()) null else it
                    }

                    if (!anyoneSubmit && !studentSubmit && !teacherSubmit) invalidMessage = "A group must be able to submit this form"
                    else if (formName == null) invalidMessage = "Form name was submitted as null"
                    else if (description == null) invalidMessage = "Description cannot be null"
                    else {
                        val questions = mutableListOf<FormQuestion>()
                        (jsonObject["questions"] as? JSONArray)?.forEach { questionJson ->
                            questionJson as JSONObject
                            val questionType = (questionJson["type"] as? Long)?.toInt()
                            val questionName = questionJson["question"] as? String
                            val required = questionJson["required"] as? Boolean ?: true
                            if (questionType !in 1..5 || questionName == null || questionName.length < 5) {
                                invalidMessage = "Invalid question composition contained"
                                return@forEach
                            } else if (questionType == 4) {
                                val wordLimit = (questionJson["wordLimit"] as? Long)?.toInt()?.let { if (it <= 0) null else it }
                                questions.add(TextQuestion(questionName, required, wordLimit))
                            } else if (questionType == 5) {
                                val minimumNumber = questionJson["minimumNumber"]?.let { it as? Long }?.toDouble()
                                val maximumNumber = questionJson["maximumNumber"]?.let { it as?Long }?.toDouble()
                                val onlyWholeNumbers = questionJson["onlyWholeNumbers"]?.let { it as? Boolean }
                                if (maximumNumber != null && minimumNumber != null && maximumNumber < minimumNumber) {
                                    invalidMessage = "Minimum number greater than maximum number"
                                } else if (onlyWholeNumbers == null) {
                                    invalidMessage = "No 'whole number' option checked"
                                } else questions.add(NumberQuestion(questionName, required, minimumNumber, maximumNumber, onlyWholeNumbers))
                            } else {
                                val includeOtherOption = questionJson["includeOtherOption"] as? Boolean ?: false
                                val options = (questionJson["options"] as? JSONArray)?.mapNotNull { it as? String }?.toMutableList()
                                if (options == null || options.size == 1) invalidMessage = "An option-based question has 1 or less set options. This is not allowed"
                                else questions.add(when (questionType) {
                                    1 -> MultipleChoiceQuestion(questionName, required, includeOtherOption, options)
                                    2 -> CheckboxQuestion(questionName, required, includeOtherOption, options)
                                    3 -> DropboxQuestion(questionName, required, includeOtherOption, options)
                                    else -> throw IllegalArgumentException("= 1,2,3 must yield 1, 2, or 3. You broke math")
                                })
                            }
                        }

                        if (formId != null) {
                            val allowed = globalGson.fromJson<List<String>>(
                                    getFromBackend("/forms/available/created-ids/${(map["user"] as User).username}"),
                                    List::class.java)
                            if (allowed.contains(formId)) {
                                val form = Form(formId, (map["user"] as User).username, formName, description, category, submitRoles, viewRoles, mutableListOf(),
                                        mutableListOf(), allowMultipleSubmissions, System.currentTimeMillis(), endDate, true, questions)
                                val updateStatus = globalGson.fromJson(Jsoup.connect("$databaseBase/forms/create").requestBody(globalGson.toJson(form)).post().body().text()
                                        , StatusWithRedirect::class.java)
                                if (updateStatus.status == 200) formId = updateStatus.message
                            } else invalidMessage = "You're not able to edit this form!"
                        } else {
                            // form *creation*, no existing form id. one must be generated
                            val form = Form(null, (map["user"] as User).username, formName, description, category, submitRoles, viewRoles, mutableListOf(),
                                    mutableListOf(), allowMultipleSubmissions, System.currentTimeMillis(), endDate, true, questions)
                            val creationStatus = globalGson.fromJson(Jsoup.connect("$databaseBase/forms/create").requestBody(globalGson.toJson(form)).post().body().text()
                                    , StatusWithRedirect::class.java)
                            if (creationStatus.status == 200) formId = creationStatus.message
                        }
                    }
                    status = if (invalidMessage != null) StatusWithRedirect(400, null, invalidMessage)
                    else StatusWithRedirect(200, "/forms/manage/$formId")
                } else status = StatusWithRedirect(400, null, "An unknown error occured")
            }
            globalGson.toJson(status)
        }
    }
}