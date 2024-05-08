package ru.reosfire.money.manage.model

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import ru.reosfire.money.manage.model.auth.User
import ru.reosfire.money.manage.model.telegram.TelegramAuthData
import java.util.concurrent.TimeUnit

private fun getDbClient(connectionString: ConnectionString): CoroutineClient {
    val settings = MongoClientSettings.builder()
        .applyConnectionString(connectionString)
        .applyToConnectionPoolSettings {
            it.maxConnectionLifeTime(1, TimeUnit.MINUTES)
            it.maxConnectionIdleTime(1, TimeUnit.MINUTES)
        }
        .build()
    return KMongo.createClient(settings).coroutine
}

class DB(connectionString: ConnectionString) {

    val client = getDbClient(connectionString)

    fun getDatabase(): CoroutineDatabase =
        client.getDatabase("MoneyManage")

    fun getUsersCollection(): CoroutineCollection<User> =
        getDatabase().getCollection<User>("users")

    fun getTelegramRequestsCollection(): CoroutineCollection<TelegramAuthData> =
        getDatabase().getCollection("telegram_requests")
}