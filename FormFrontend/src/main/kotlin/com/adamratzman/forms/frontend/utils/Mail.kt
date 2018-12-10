package com.adamratzman.forms.frontend.utils

import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.jsoup.Jsoup

fun getAccountId(zohoAuthToken: String) = JSONParser().parse(
        Jsoup.connect("http://mail.zoho.com/api/accounts").header("Authorization", zohoAuthToken).get().text()).let {
    it as JSONObject
    it["accountId"]
}
