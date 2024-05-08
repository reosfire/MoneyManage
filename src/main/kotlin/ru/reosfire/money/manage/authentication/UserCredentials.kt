package ru.reosfire.money.manage.authentication

import kotlinx.serialization.Serializable

interface IUserCredentials {
    val login: String
    val password: String
}

@Serializable
/** DTO to be passed through network from client */
data class LoginData (
    override val login: String,
    override val password: String,
): IUserCredentials

@Serializable
/** DTO to be passed through network from client */
data class RegisterData (
    override val login: String,
    override val password: String,
    val telegramToken: String,
): IUserCredentials
