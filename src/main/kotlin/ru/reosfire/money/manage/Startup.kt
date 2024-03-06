package ru.reosfire.money.manage

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mongodb.ConnectionString
import io.ktor.http.auth.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import ru.reosfire.money.manage.authentication.JWTConfiguration
import ru.reosfire.money.manage.authentication.User
import ru.reosfire.money.manage.authentication.routes.setupAuthenticationRoutes
import ru.reosfire.money.manage.authentication.routes.setupJwt

fun connectionStringByEnv(): ConnectionString {
    val user = System.getenv("MONGO_USER")
    val password = System.getenv("MONGO_PASSWORD")
    val host = System.getenv("MONGO_HOST") // localhost:27017

    return ConnectionString("mongodb://$user:$password@$host/")
}

fun createDatabase(): CoroutineDatabase {
    return KMongo.createClient(
        connectionStringByEnv()
    ).coroutine.getDatabase("MoneyManage")
}

fun main() {
    val database = createDatabase()
    val usersCollection = database.getCollection<User>("users")
    val jwtConfiguration = JWTConfiguration.byEnvironment()

    val server = embeddedServer(
        factory = Netty,
        port = 25530,
        host = "0.0.0.0"
    ) {
        setupJwt(jwtConfiguration)

        setupContentNegotiation()
        setupAuthenticationRoutes(jwtConfiguration, usersCollection)

        setupSecuredRoutes(usersCollection)
    }

    server.start(wait = true)
}

fun Application.setupContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.setupSecuredRoutes(users: CoroutineCollection<User>) {
    routing {
        authenticate {
            get("/") {
                val principal = call.principal<JWTPrincipal>()
                val claim = principal?.getClaim("username", String::class)

                users.findOne(User::login eq claim)?.let { call.respond(it) }
            }
        }
    }
}
