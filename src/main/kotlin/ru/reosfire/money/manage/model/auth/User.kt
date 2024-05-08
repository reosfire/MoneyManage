package ru.reosfire.money.manage.model.auth

import kotlinx.serialization.Serializable

@Serializable
data class User (
    val login: String,
    val hash: String,
    val salt: String,
    val telegramId: Long,
    val telegramChatId: Long,
)