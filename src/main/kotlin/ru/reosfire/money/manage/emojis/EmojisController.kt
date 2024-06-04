package ru.reosfire.money.manage.emojis

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litote.kmongo.eq
import ru.reosfire.money.manage.data.DB
import ru.reosfire.money.manage.data.EmojiCache

@Serializable
data class EmojiApiResponse(
    val character: String,
)

fun Application.setupEmojiRoutes(db: DB) {
    val apiKey = System.getenv("EMOJIS_API_KEY")
    println("API KEY: $apiKey")

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    routing {
        authenticate {
            get("/emojis/get") {
                val query = call.parameters["query"]
                if (query == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                val emojiCacheCollection = db.getEmojiCacheCollection()

                val cached = emojiCacheCollection.findOne(
                    EmojiCache::query eq query
                )

                if (cached != null) {
                    call.respond(cached.emoji)
                    return@get
                }

                val apiResponse = client.get("https://emoji-api.com/emojis") {
                    url {
                        parameters.append("search", query)
                        parameters.append("access_key", apiKey)
                    }
                }

                if (apiResponse.status.value !in 200..299) {
                    call.respond(apiResponse.status)
                    return@get
                }

                val emoji = try {
                    val response = apiResponse.body<List<EmojiApiResponse>>()
                    response.first().character
                } catch (e: Exception) {
                    "❓"
                }

                emojiCacheCollection.insertOne(EmojiCache(query, emoji))
                call.respond(emoji)
            }
        }
    }
}
