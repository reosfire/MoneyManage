package ru.reosfire.money.manage.authentication

import kotlinx.serialization.Serializable

@Serializable
data class LoginPassword (
    val login: String,
    val password: String,
)

@Serializable
data class User (
    val login: String,
    val hash: String,
    val salt: String,
)
