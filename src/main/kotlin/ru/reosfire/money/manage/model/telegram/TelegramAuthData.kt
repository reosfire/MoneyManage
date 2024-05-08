package ru.reosfire.money.manage.model.telegram

import kotlinx.serialization.Serializable

@Serializable
data class TelegramAuthData(
    val token: String,
    val confirmed: Boolean = false,
    val userTgId: Long? = null,
    val userChatId: Long? = null,
)