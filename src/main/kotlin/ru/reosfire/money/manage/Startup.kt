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
import org.litote.kmongo.eq
import ru.reosfire.money.manage.authentication.JWTConfiguration
import ru.reosfire.money.manage.authentication.routes.setupAuthenticationRoutes
import ru.reosfire.money.manage.authentication.routes.setupJwt
import ru.reosfire.money.manage.model.DB
import ru.reosfire.money.manage.model.auth.User
import ru.reosfire.money.manage.telegram.TGBot

fun main() {
    val db = DB(connectionStringByEnv())
    val jwtConfiguration = JWTConfiguration.byEnvironment()

    val tgBot = TGBot(db)

    val server = embeddedServer(
        factory = Netty,
        port = 25530,
        host = "0.0.0.0"
    ) {
        setupJwt(jwtConfiguration)

        setupContentNegotiation()
        setupAuthenticationRoutes(jwtConfiguration, db, tgBot)

        routing {
            authenticate {
                setupSecuredRoutes(db)
            }
        }
        with(tgBot) { setupRoutes() }
    }

    server.start(wait = true)
}

private fun Application.setupContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}

private fun Route.setupSecuredRoutes(db: DB) {
    get("/") {
        val principal = call.principal<JWTPrincipal>()
        val claim = principal?.getClaim("username", String::class)

        val users = db.getUsersCollection()

        users.findOne(User::login eq claim)?.let { call.respond(it) }
    }
}

private fun connectionStringByEnv(): ConnectionString {
    val user = System.getenv("MONGO_USER")
    val password = System.getenv("MONGO_PASSWORD")
    val host = System.getenv("MONGO_HOST") // localhost:27017

    return ConnectionString("mongodb://$user:$password@$host/")
}
