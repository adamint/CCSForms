package com.adamratzman.forms.frontend.security

import org.jsoup.Jsoup
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService

class FormsUserDetailsService : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        Jsoup.connect("http://database/")
        User()
    }
}