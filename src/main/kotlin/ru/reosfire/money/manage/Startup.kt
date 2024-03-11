package ru.reosfire.money.manage

import com.mongodb.ConnectionString
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.litote.kmongo.coroutine.CoroutineClient
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

fun getDbClient(): CoroutineClient {
    return KMongo.createClient(
        connectionStringByEnv()
    ).coroutine
}

fun CoroutineClient.getDatabase(): CoroutineDatabase =
    getDatabase("MoneyManage")

fun CoroutineDatabase.getUsersCollection(): CoroutineCollection<User> =
    getCollection<User>("users")
fun main() {
    val client = getDbClient()
    val jwtConfiguration = JWTConfiguration.byEnvironment()

    val server = embeddedServer(
        factory = Netty,
        port = 25530,
        host = "0.0.0.0"
    ) {
        setupJwt(jwtConfiguration)

        setupContentNegotiation()
        setupAuthenticationRoutes(jwtConfiguration, client)

        setupSecuredRoutes(client)
    }

    server.start(wait = true)
}

fun Application.setupContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.setupSecuredRoutes(client: CoroutineClient) {
    routing {
        authenticate {
            get("/") {
                val principal = call.principal<JWTPrincipal>()
                val claim = principal?.getClaim("username", String::class)

                val users = client.getDatabase().getUsersCollection()

                users.findOne(User::login eq claim)?.let { call.respond(it) }
            }
        }
    }
}
