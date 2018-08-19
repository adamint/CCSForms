package com.adamratzman.forms.common.utils

import com.google.gson.Gson
import com.rethinkdb.net.Cursor
import org.json.simple.JSONObject

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
