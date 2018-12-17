package com.adamratzman.forms.frontend.functionality

import com.adamratzman.forms.common.models.Form
import com.adamratzman.forms.common.models.FormCategory
import com.adamratzman.forms.common.models.Role
import com.adamratzman.forms.common.models.User
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend
import com.adamratzman.forms.frontend.utils.getUser
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
        println(forms.toList())
        val counselingForms = forms.filter { it.category == FormCategory.COUNSELING }
        val athleticsForms = forms.filter { getUser(it.creator)?.role == Role.ATHLETICS || it.category == FormCategory.ATHLETICS }
        val genericSchoolForms = forms.filter { getUser(it.creator)?.role == Role.ADMIN && it !in counselingForms && it !in athleticsForms }

        val formMapping = mutableListOf<Pair<String, List<Form>>>()

        if (genericSchoolForms.isNotEmpty()) formMapping.add("School Forms" to genericSchoolForms)
        if (counselingForms.isNotEmpty()) formMapping.add("Counseling Forms" to counselingForms)
        if (athleticsForms.isNotEmpty()) formMapping.add("Athletics Forms" to athleticsForms)

        map["forms"] = formMapping
        
        map["total"] = forms.size
        map["userString"] = role.toString()
        handlebars.render(ModelAndView(map, "available.hbs"))
    }
}