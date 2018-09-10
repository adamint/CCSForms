package com.adamratzman.forms.common.utils

import com.adamratzman.forms.common.models.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.RuntimeTypeAdapterFactory
import com.rethinkdb.net.Cursor
import org.json.simple.JSONObject

val formAdapter = RuntimeTypeAdapterFactory
        .of(FormQuestion::class.java)
        .registerSubtype(MultipleChoiceQuestion::class.java)
        .registerSubtype(CheckboxQuestion::class.java)
        .registerSubtype(DropboxQuestion::class.java)
        .registerSubtype(TextQuestion::class.java)
        .registerSubtype(NumberQuestion::class.java)

val globalGson = GsonBuilder().setPrettyPrinting().registerTypeAdapterFactory(formAdapter).create()

fun <T> asPojo(gson: Gson, map: HashMap<*, *>?, tClass: Class<T>): T? {
    return gson.fromJson(JSONObject.toJSONString(map), tClass)
}

fun <T> Any.queryAsArrayList(gson: Gson, t: Class<T>): MutableList<T?> {
    val tS = mutableListOf<T?>()
    val cursor = this as Cursor<HashMap<*, *>>
    cursor.forEach { hashMap -> tS.add(asPojo(gson, hashMap, t)) }
    cursor.close()
    return tS
}
