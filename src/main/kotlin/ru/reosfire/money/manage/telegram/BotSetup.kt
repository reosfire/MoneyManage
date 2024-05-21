package ru.reosfire.money.manage.telegram

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.litote.kmongo.combine
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import ru.reosfire.money.manage.data.DB
import ru.reosfire.money.manage.data.TelegramAuthData
import java.util.*

sealed class AttachmentEvent {
    data class Confirmed(
        val telegramToken: String,
        val username: String?,
    ) : AttachmentEvent()

    data class Cancelled(val telegramToken: String) : AttachmentEvent()
}

class TGBot(private val db: DB) {

    val attachmentEventsFlow = MutableSharedFlow<AttachmentEvent>()

    private val bot = bot {
        token = System.getenv("TG_TOKEN")

        dispatch {
            command("start") {
                val userId = message.from?.id
                if (userId == null) {
                    bot.sendMessage(chatId, "Err. Command used not by user?!")
                    return@command
                }

                if (args.size != 1) {
                    bot.sendMessage(chatId, "Err. Incorrect number of arguments.")
                    return@command
                }
                val token = args[0]

                val telegramRequestsCollection = db.getTelegramRequestsCollection()
                if (telegramRequestsCollection.findOne(TelegramAuthData::token eq token) == null) {
                    bot.sendMessage(chatId, "Err. Unknown token")
                    return@command
                }

                telegramRequestsCollection.updateOne(
                    TelegramAuthData::token eq token,
                    combine(
                        setValue(TelegramAuthData::confirmed, true),
                        setValue(TelegramAuthData::userTgId, userId),
                        setValue(TelegramAuthData::userChatId, message.chat.id),
                    ),
                )

                val cancelButton = InlineKeyboardButton.CallbackData("Cancel", token)
                bot.sendMessage(
                    chatId,
                    "Successfully confirmed. Please continue your registration on site or cancel",
                    replyMarkup = InlineKeyboardMarkup.createSingleButton(cancelButton),
                )
                attachmentEventsFlow.emit(AttachmentEvent.Confirmed(token, message.from?.username))
            }

            callbackQuery {
                val token = callbackQuery.data
                val telegramRequestsCollection = db.getTelegramRequestsCollection()
                val user = telegramRequestsCollection.findOne(TelegramAuthData::token eq token)
                    ?: return@callbackQuery

                user.userTgId ?: return@callbackQuery
                user.userChatId ?: return@callbackQuery
                if (!user.confirmed) return@callbackQuery

                telegramRequestsCollection.updateOne(
                    TelegramAuthData::token eq token,
                    combine(
                        setValue(TelegramAuthData::confirmed, false),
                    ),
                )

                bot.sendMessage(ChatId.fromId(user.userChatId), "Successfully canceled")
                attachmentEventsFlow.emit(AttachmentEvent.Cancelled(token))
            }
        }
    }

    init {
        bot.startPolling()
    }

    fun sendLoginMessage(chatId: Long) {
        bot.sendMessage(ChatId.fromId(chatId), text = "Some user logged into your account")
    }

    fun Application.setupRoutes() {
        routing {
            post("/tokenForTg") {
                val token = UUID.randomUUID().toString()
                val telegramRequestsCollection = db.getTelegramRequestsCollection()

                telegramRequestsCollection.insertOne(TelegramAuthData(token))
                call.respond(token)
            }
        }
    }
}