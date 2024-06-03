package ru.reosfire.money.manage.rooms

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.addToSet
import org.litote.kmongo.contains
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.projection
import org.litote.kmongo.eq
import ru.reosfire.money.manage.data.DB
import ru.reosfire.money.manage.data.Room
import ru.reosfire.money.manage.data.ShopList
import ru.reosfire.money.manage.data.User
import java.util.*

private class CommonContext(
    val roomsCollection: CoroutineCollection<Room>,
    val roomId: String,
    val username: String,
)

private suspend inline fun PipelineContext<Unit, ApplicationCall>.commonChecks(
    db: DB,
    block: CommonContext.() -> Unit,
) {
    val s = arrayOf(1, 2, 3).shuffle()

    val principal = call.principal<JWTPrincipal>()
    val username = principal?.getClaim("username", String::class)

    if (username == null) {
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    val roomId = call.parameters["roomId"]
    if (roomId == null) {
        call.respond(HttpStatusCode.BadRequest)
        return
    }

    val roomsCollection = db.getRoomsCollection()

    val users = roomsCollection.projection(Room::users, Room::id eq roomId).first()

    if (users == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }

    if (!users.contains(username)) {
        call.respond(HttpStatusCode.Forbidden)
        return
    }
    block.invoke(CommonContext(roomsCollection, roomId, username))
}
@Serializable
data class RoomInfo(
    val id: String,
    val name: String,
)

fun Application.setupRoomsRoutes(db: DB) {
    routing {
        authenticate {
            get("/rooms/list") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.getClaim("username", String::class)
                if (username == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val roomsCollection = db.getRoomsCollection()

                val rooms = roomsCollection
                    .withDocumentClass<RoomInfo>()
                    .find(Room::users contains username)
                    .projection(RoomInfo::id, RoomInfo::name).toList()

                call.respond(rooms)
            }
            post("/rooms/create") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.getClaim("username", String::class)

                if (username == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val roomName = call.parameters["name"]

                if (roomName == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val usersCollection = db.getUsersCollection()
                val user = usersCollection.findOne(User::login eq username)

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                if (user.rooms.contains(roomName)) {
                    call.respond(HttpStatusCode.Conflict, "Room with that name already exists")
                    return@post
                }

                val roomId = UUID.randomUUID().toString()
                val initialRoom = Room(
                    id = roomId,
                    owner = username,
                    name = roomName,
                    shopList = ShopList(listOf()),
                    users = listOf(username)
                )

                val roomsCollection = db.getRoomsCollection()
                roomsCollection.insertOne(initialRoom)

                usersCollection.updateOne(
                    filter = User::login eq username,
                    update = addToSet(
                        User::rooms,
                        roomName,
                    ),
                )

                call.respond(HttpStatusCode.OK)
            }

            post("/rooms/update") {

            }

        }
    }
}
