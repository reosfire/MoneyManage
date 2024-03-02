package ru.reosfire.money.manage

import com.mongodb.ConnectionString
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.reactive.awaitFirst
import org.bson.Document
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

fun connectionStringFromEnv(): ConnectionString {
    val user = System.getenv("MONGO_USER")
    val password = System.getenv("MONGO_PASSWORD")
    val host = System.getenv("MONGO_HOST") // localhost:27017

    return ConnectionString("mongodb://$user:$password@$host/")
}

data class User(
    val login: String
)

suspend fun db() {
    val database = "MoneyManage"

    val db = KMongo.createClient(
        connectionStringFromEnv()
    ).coroutine.getDatabase(database)

    val usersCollection = db.getCollection<User>()
    usersCollection.insertOne(User("reosfire"))
}

suspend fun main() {
    db()

    val server = embeddedServer(
        factory = Netty,
        port = 80,
        host = "0.0.0.0"
    ) {
        setupSecurity()
        setupContentNegotiation()
    }

    server.start(wait = true)
}

fun Application.setupContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.setupSecurity() {

}
