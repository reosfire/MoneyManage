package ru.reosfire.money.manage.authentication.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.date.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.litote.kmongo.eq
import ru.reosfire.money.manage.authentication.IUserCredentials
import ru.reosfire.money.manage.authentication.JWTConfiguration
import ru.reosfire.money.manage.authentication.LoginData
import ru.reosfire.money.manage.authentication.RegisterData
import ru.reosfire.money.manage.data.*
import ru.reosfire.money.manage.telegram.AttachmentEvent
import ru.reosfire.money.manage.telegram.TGBot
import java.security.SecureRandom
import java.util.*

private const val AUTH_COOKIE = "AUTH_TOKEN"
private const val MIN_LOGIN_LENGTH = 3
private val PASSWORD_REGEX = Regex("[a-zA-Z0-9_-]*")

fun Application.setupAuthenticationRoutes(
    jwtConfiguration: JWTConfiguration,
    db: DB,
    bot: TGBot,
) {
    routing {
        webSocket("/tg-linkage") {
            val token = call.parameters["token"]

            try {
                bot.attachmentEventsFlow.collect { event ->
                    when (event) {
                        is AttachmentEvent.Cancelled -> {
                            if (event.telegramToken == token)
                                outgoing.send(Frame.Text("cancelled"))
                        }
                        is AttachmentEvent.Confirmed -> {
                            if (event.telegramToken == token)
                                outgoing.send(Frame.Text("confirmed;${event.username}"))
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${closeReason.await()}")
                e.printStackTrace()
            }
        }

        post("/register") {
            val loginPassword = call.receive<RegisterData>()

            if (!loginPassword.login.matches(PASSWORD_REGEX)) {
                call.respond(HttpStatusCode.BadRequest, "Login should match [a-zA-Z0-9_-]")
                return@post
            }
            if (loginPassword.login.length < MIN_LOGIN_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, "Login should be at least 3 characters long")
                return@post
            }

            // TODO check weak password?
            val usersCollection = db.getUsersCollection()

            val foundUser = usersCollection.findOne(loginPassword::login eq loginPassword.login)
            if (foundUser != null) {
                call.respond(HttpStatusCode.Conflict, "User with that login already exist")
                return@post
            }

            val telegramDataCollection = db.getTelegramRequestsCollection()
            val telegramData = telegramDataCollection.findOne(TelegramAuthData::token eq loginPassword.telegramToken)
            if (telegramData == null) {
                call.respond(HttpStatusCode.BadRequest, "Sent registration with invalid telegram token")
                return@post
            }
            if (!telegramData.confirmed) {
                call.respond(HttpStatusCode.ExpectationFailed, "Telegram attach request must be confirmed to finish registration")
                return@post
            }

            val roomId = UUID.randomUUID().toString()
            val initialRoom = Room(
                id = roomId,
                owner = loginPassword.login,
                name = "${loginPassword.login}'s room",
                shopList = ShopList(listOf()),
                users = listOf(loginPassword.login)
            )

            val roomsCollection = db.getRoomsCollection()
            roomsCollection.insertOne(initialRoom)

            val salt = randomSalt()

            val user = User(
                login = loginPassword.login,
                hash = loginPassword.getHash(salt),
                salt = salt,
                telegramId = telegramData.userTgId!!,
                telegramChatId = telegramData.userChatId!!,
            )

            usersCollection.insertOne(user)
            call.respond(HttpStatusCode.OK, "Successfully registered")

            telegramDataCollection.deleteOne(TelegramAuthData::token eq loginPassword.telegramToken)
        }

        post("/login") {
            val sent = call.receive<LoginData>()

            if (!sent.login.matches(PASSWORD_REGEX)) {
                call.respond(HttpStatusCode.BadRequest, "Login should match [a-zA-Z0-9_-]")
                return@post
            }

            // TODO: validation
            val users = db.getUsersCollection()

            val storedUser = users.findOne(User::login eq sent.login)
            if (storedUser == null) {
                call.respond(HttpStatusCode.BadRequest, "user not found") //TODO redirect to register?
                return@post
            }

            val sentHash = sent.getHash(salt = storedUser.salt)

            if (sentHash != storedUser.hash) {
                call.respond(HttpStatusCode.Unauthorized, "Wrong credentials")
                return@post
            }

            val token = JWT.create()
                .withAudience(jwtConfiguration.audience)
                .withIssuer(jwtConfiguration.issuer)
                .withClaim("username", storedUser.login)
                .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
                .sign(Algorithm.HMAC256(jwtConfiguration.secret))

            call.response.cookies.append(AUTH_COOKIE, token)
            call.respond(HttpStatusCode.OK)

            bot.sendLoginMessage(storedUser.telegramId)
        }

        get("/logout") {
            call.response.cookies.append(
                Cookie(
                    name = AUTH_COOKIE,
                    value = "",
                    expires = GMTDate.START
                )
            )
            call.respond(HttpStatusCode.OK)
        }

        authenticate {
            get("/isLoggedIn") {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun Application.setupJwt(jwtConfiguration: JWTConfiguration) {
    authentication {
        jwt {
            val algorithm = Algorithm.HMAC256(jwtConfiguration.secret)
            verifier(JWT
                .require(algorithm)
                .withAudience(jwtConfiguration.audience)
                .withIssuer(jwtConfiguration.issuer)
                .build())

            authHeader { call ->
                val cookieValue = call.request.cookies[AUTH_COOKIE] ?: return@authHeader null

                try {
                    parseAuthorizationHeader("Bearer $cookieValue")
                } catch (cause: IllegalArgumentException) {
                    null
                }
            }

            validate { credential ->
                credential.payload.takeUnless {
                    it.getClaim("username").asString().isNullOrBlank()
                }?.let { JWTPrincipal(it) }
            }
        }
    }
}

private fun randomSalt(length: Int = 32): String {
    val randomBytes = ByteArray(length)
    SecureRandom.getInstance("SHA1PRNG").nextBytes(randomBytes)
    return Hex.encodeHexString(randomBytes)
}

private fun IUserCredentials.getHash(salt: String) =
    DigestUtils.sha256Hex("$salt$password")
