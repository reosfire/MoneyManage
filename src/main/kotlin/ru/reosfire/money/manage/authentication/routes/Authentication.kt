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
import io.ktor.util.date.*
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.eq
import ru.reosfire.money.manage.authentication.JWTConfiguration
import ru.reosfire.money.manage.authentication.LoginPassword
import ru.reosfire.money.manage.authentication.User
import java.security.SecureRandom
import java.util.*

private const val AUTH_COOKIE = "AUTH_TOKEN"
private const val MIN_LOGIN_LENGTH = 3
private val PASSWORD_REGEX = Regex("[a-zA-Z0-9_-]*")

fun Application.setupAuthenticationRoutes(
    jwtConfiguration: JWTConfiguration,
    users: CoroutineCollection<User>,
) {
    routing {
        post("/register") {
            val loginPassword = call.receive<LoginPassword>()

            if (!loginPassword.login.matches(PASSWORD_REGEX)) {
                call.respond(HttpStatusCode.BadRequest, "Login should match [a-zA-Z0-9_-]")
                return@post
            }
            if (loginPassword.login.length < MIN_LOGIN_LENGTH) {
                call.respond(HttpStatusCode.BadRequest, "Login should be at least 3 characters long")
                return@post
            }

            // TODO check weak password?

            val foundUser = users.findOne(loginPassword::login eq loginPassword.login)
            if (foundUser != null) {
                call.respond(HttpStatusCode.Conflict, "User with that login already exist")
                return@post
            }

            users.insertOne(loginPassword.toHashed())
            call.respond(HttpStatusCode.OK, "Successfully registered")
        }

        post("/login") {
            val sent = call.receive<LoginPassword>()

            if (!sent.login.matches(PASSWORD_REGEX)) {
                call.respond(HttpStatusCode.BadRequest, "Login should match [a-zA-Z0-9_-]")
                return@post
            }

            // TODO: validation

            val storedUser = users.findOne(User::login eq sent.login)
            if (storedUser == null) {
                call.respond(HttpStatusCode.BadRequest, "user not found") //TODO redirect to register?
                return@post
            }

            val sentUser = sent.toHashed(salt = storedUser.salt)

            if (sentUser.hash != storedUser.hash) {
                call.respond(HttpStatusCode.Unauthorized, "Wrong credentials")
                return@post
            }


            val token = JWT.create()
                .withAudience(jwtConfiguration.audience)
                .withIssuer(jwtConfiguration.issuer)
                .withClaim("username", storedUser.login)
                .withExpiresAt(Date(System.currentTimeMillis() + 600000))
                .sign(Algorithm.HMAC256(jwtConfiguration.secret))

            call.response.cookies.append(AUTH_COOKIE, token)
            call.respond(HttpStatusCode.OK)
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

private fun LoginPassword.toHashed(
    salt: String = randomSalt()
) = User(
    login = login,
    hash = DigestUtils.sha256Hex("$salt$password"),
    salt = salt,
)
