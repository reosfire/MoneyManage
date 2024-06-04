package ru.reosfire.money.manage.data

import kotlinx.serialization.Serializable

@Serializable
data class User (
    val login: String,
    val hash: String,
    val salt: String,
    val telegramId: Long,
    val telegramChatId: Long,
)

@Serializable
data class TelegramAuthData(
    val token: String,
    val confirmed: Boolean = false,
    val userTgId: Long? = null,
    val userChatId: Long? = null,
)

@Serializable
data class Room(
    val id: String, // UUID string
    val owner: String, // owner login
    val name: String,
    val shopList: ShopList,
    val users: List<String>, // logins
)

@Serializable
data class ShopList(
    val items: List<ShopListItem>,
)

@Serializable
data class ShopListItem(
    val uuid: String?,
    val name: String?,
    val price: Double?,
    val checked: Boolean?,
    val emoji: String?,
    val tags: List<Tag>?,
)

@Serializable
data class Tag(
    val hexColor: String,
    val label: String,
)

@Serializable
data class RoomInvite(
    val code: String,
    val roomId: String,
)

@Serializable
data class EmojiCache(
    val query: String,
    val emoji: String,
)