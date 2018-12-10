package com.adamratzman.forms.frontend.utils

import com.adamratzman.forms.common.models.Form
import com.adamratzman.forms.common.models.FormResponseDatabaseWrapper
import com.adamratzman.forms.common.models.SparkUserResponse
import com.adamratzman.forms.common.utils.globalGson
import com.adamratzman.forms.frontend.FormFrontend

fun FormFrontend.getForm(id: String) = globalGson.fromJson(getFromBackend("/forms/get/$id"), Form::class.java)

fun FormFrontend.getFormWithManagementPermission(username: String) = globalGson.fromJson(getFromBackend("/forms/all/$username"), Array<Form>::class.java)

fun FormFrontend.getResponsesFor(formId: String) =
        globalGson.fromJson(getFromBackend("/forms/responses/$formId"), Array<FormResponseDatabaseWrapper>::class.java)

fun FormFrontend.getResponses() = globalGson.fromJson(getFromBackend("/forms/responses"), Array<FormResponseDatabaseWrapper>::class.java)

fun FormFrontend.getUser(username: String) = globalGson.fromJson(getFromBackend("/user/$username"), SparkUserResponse::class.java)?.user

fun FormFrontend.getCredential(id: String) = getFromBackend("/credentials/$id")