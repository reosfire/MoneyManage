package ru.reosfire.money.manage.data

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
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

    val database: CoroutineDatabase
        get() = client.getDatabase("MoneyManage")

    fun getUsersCollection(): CoroutineCollection<User> =
        database.getCollection<User>("users")

    fun getTelegramRequestsCollection(): CoroutineCollection<TelegramAuthData> =
        database.getCollection("telegram_requests")

    fun getRoomsCollection(): CoroutineCollection<Room> =
        database.getCollection("rooms")

    fun getRoomInvitesCollection(): CoroutineCollection<RoomInvite> =
        database.getCollection("room_invites")

    fun getEmojiCacheCollection(): CoroutineCollection<EmojiCache> =
        database.getCollection("emoji_cache")
}