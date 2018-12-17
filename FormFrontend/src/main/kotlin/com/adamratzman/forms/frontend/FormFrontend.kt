package com.adamratzman.forms.frontend

import com.adamratzman.forms.common.models.Role
import com.adamratzman.forms.common.models.User
import com.adamratzman.forms.common.models.UserNotificationSettings
import com.adamratzman.forms.frontend.functionality.*
import com.adamratzman.forms.frontend.utils.getUser
import com.adamratzman.forms.frontend.utils.registerErrorEndpoints
import com.adamratzman.forms.frontend.utils.registerLoginEndpoints
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import spark.Request
import spark.Spark.port
import spark.Spark.staticFileLocation
import spark.template.handlebars.HandlebarsTemplateEngine
import java.net.URLEncoder
import java.text.DateFormat
import java.time.Instant
import java.util.*
import kotlin.collections.set

fun main(args: Array<String>) {
    FormFrontend()
}

class FormFrontend {
    val databaseBase = "http://backend"
    val handlebars = HandlebarsTemplateEngine()
    val key = RandomStringUtils.randomAlphanumeric(35)

    init {
        port(80)
        staticFileLocation("/public")

        registerHandlebarsHelpers()

        registerErrorEndpoints()
        registerHomeEndpoint()
        registerLoginEndpoints()

        registerAvailableEndpoint()
        registerCreationEndpoints()
        registerManageEndpoints()
        registerTakeEndpoints()

        registerSettingsEndpoints()

        registerMiscEndpoints()

        var start = false
        while (!start) {
            try {
                Jsoup.connect("$databaseBase/utils/key").requestBody(key).post()
                start = true
            } catch (e: Exception) {
            }
        }

        println("Started frontend")
    }

    internal fun getMap(request: Request, pageTitle: String): HashMap<String, Any?> {
        val map = hashMapOf<String, Any?>()
        val session = request.session()
        val user: User? = session.attribute<User>("user")
        user?.let {
            if (getUser(user.username) != user) session.removeAttribute("user") else {
                map["user"] = user
                map["role"] = user.role
            }

            Runnable {
                session.attribute("user", getUser(user.username))
            }.run()
        }
        if (user != null && user.userNotificationSettings == null) user.userNotificationSettings = UserNotificationSettings()

        if (!map.containsKey("role")) map["role"] = Role.NOT_LOGGED_IN
        map["pageTitle"] = pageTitle
        return map
    }

    internal fun getLoginRedirect(request: Request, url: String, message: String? = null) = "/login?redirect=" +
            encode(url + request.queryMap().toMap().toList().filter { it.second != null }
                    .mapIndexed { i, pair -> (if (i == 0) "?" else "") + "${pair.first}=${pair.second.getOrNull(0)}" }
                    .joinToString("&")) + (if (message != null) "&message=${encode(message)}" else "")

    private fun encode(thing: String) = URLEncoder.encode(thing, "UTF-8")

    private fun registerHandlebarsHelpers() {
        val field = handlebars::class.java.getDeclaredField("handlebars")
        field.isAccessible = true
        val handle = field.get(handlebars) as Handlebars
        handle.registerHelper("streq") { first: Any?, options: Options ->
            if (options.params[0].toString().equals(first?.toString(), true)) {
                options.fn()
            } else options.inverse()
        }
    }

    internal fun getFromBackend(path: String): String {
        return Jsoup.connect("$databaseBase$path").get().body().text()
    }
}

fun Long.toDate() = DateFormat.getDateInstance().format(Date.from(Instant.ofEpochMilli(this)))