package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.EmailVerification
import com.adamratzman.forms.common.models.StatusWithRedirect
import com.adamratzman.forms.common.models.User
import com.adamratzman.forms.common.models.UserNotificationSettings
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import com.adamratzman.forms.frontend.utils.getRandomId
import com.adamratzman.forms.frontend.utils.getVerificationRequest
import com.adamratzman.forms.frontend.utils.getVerificationRequestById
import org.apache.commons.validator.routines.EmailValidator
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.jsoup.Jsoup
import spark.ModelAndView
import spark.Spark.*
import kotlin.math.ceil

fun FormFrontend.registerSettingsEndpoints() {
    path("/settings") {
        get("") { request, response ->
            val map = getMap(request, "Change Settings")
            if (map["user"] == null) response.redirect(getLoginRedirect(request, "/settings"))

            handlebars.render(ModelAndView(map, "settings.hbs"))
        }

        get("/email-verification") { request, response ->
            val map = getMap(request, "Email Verification Request")
            val user = map["user"] as? User
            if (user == null) response.redirect(getLoginRedirect(request, "/settings/email-verification"))
            else {
                val verificationRequest = getVerificationRequest(user.username)
                if (verificationRequest == null) response.redirect("/settings")
                println(verificationRequest)
                map["description"] = "We sent an email verification request to ${verificationRequest.email}. Please check your email. " +
                        "This request will expire in ${ceil((verificationRequest.expiry - System.currentTimeMillis()).toDouble().div(1000))} seconds" +
                        "<br><br>" +
                        "<a href='/settings'>Return to settings </a><a class='uk-icon' uk-icon='icon: arrow-right' href='/settings'></a>"

                handlebars.render(ModelAndView(map, "quick-info.hbs"))
            }
        }

        get("/confirm-email/:id") { request, response ->
            getVerificationRequestById(request.params(":id"))?.let { verificationRequest ->
                Jsoup.connect("$databaseBase/mail/verification/confirm/${verificationRequest.username}").requestBody(verificationRequest.email).post()
                Jsoup.connect("$databaseBase/mail/verification/remove/${verificationRequest.username}").post()
                response.redirect("/settings")
            } ?: response.redirect("/404")

        }

        post("") { request, _ ->
            var issue: String? = null
            var redirect: String? = null

            val map = getMap(request, "Change Settings")
            if (map["user"] as? User == null) issue = "You're not logged in"
            else {
                val user = map["user"] as User

                val json = JSONParser().parse(request.body()) as? JSONObject
                if (json == null || json["type"] as? String == null) issue = "Invalid request"
                else {
                    when (json["type"] as String) {
                        "remove-email" -> {
                            if (user.email == null) issue = "There's no set email"
                            else {
                                Jsoup.connect("$databaseBase/mail/remove/${user.username}").post()
                            }
                        }
                        "add-email" -> {
                            if (user.email != null) issue = "There's already a set email"
                            else if (!EmailValidator.getInstance().isValid(json["value"] as? String)) issue = "That's not a valid email"
                            else {
                                if (getFromBackend("/mail/verification/exists/${user.username}") == "true") {
                                    Jsoup.connect("$databaseBase/mail/verification/remove/${user.username}").post()
                                }

                                Jsoup.connect("$databaseBase/mail/verification/put")
                                        .requestBody(globalGson.toJson(
                                                EmailVerification(user.username, json["value"] as String,
                                                        System.currentTimeMillis() + 1000 * 60 * 5, getRandomId()))).post()

                                redirect = "/settings/email-verification"
                            }
                        }
                        "notification-update" -> {
                            val newSubmission = json["newSubmission"] as? Boolean
                            val deleteSubmission = json["deleteSubmission"] as? Boolean
                            if (newSubmission == null || deleteSubmission == null) issue = "Invalid request"
                            else {
                                Jsoup.connect("$databaseBase/notifications/${user.username}/update")
                                        .requestBody(globalGson.toJson(UserNotificationSettings(newSubmission, deleteSubmission)))
                                        .post()
                            }
                        }
                    }
                }
            }
            if (redirect == null) redirect = "/settings"

            val status = if (issue != null) StatusWithRedirect(400, redirect, issue) else StatusWithRedirect(200, redirect, null)
            globalGson.toJson(status)
        }
    }
}