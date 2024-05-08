package ru.reosfire.money.manage.telegram

import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId

val CommandHandlerEnvironment.chatId: ChatId
    get() = ChatId.fromId(message.chat.id)