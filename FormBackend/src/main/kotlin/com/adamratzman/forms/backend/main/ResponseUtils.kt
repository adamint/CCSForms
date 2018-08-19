package com.adamratzman.forms.backend.main

import com.google.gson.Gson

private val gson = Gson()

fun Any.jsonify(): String = gson.toJson(this)