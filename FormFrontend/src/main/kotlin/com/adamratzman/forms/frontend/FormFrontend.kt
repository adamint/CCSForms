package com.adamratzman.forms.frontend

import spark.Spark.port

fun main(args: Array<String>) {
    FormFrontend()
}

class FormFrontend {
    val databaseBase = "http://database"

    init {
        port(80)

    }
}