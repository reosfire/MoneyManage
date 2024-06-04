package ru.reosfire.money.manage.rooms

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.projection
import ru.reosfire.money.manage.data.DB
import ru.reosfire.money.manage.data.Room
import ru.reosfire.money.manage.data.RoomInvite
import ru.reosfire.money.manage.data.ShopList
import java.util.*

@Serializable
data class RoomBriefInfo(
    val id: String,
    val name: String,
)

@Serializable
data class RoomInfo(
    val id: String,
    val name: String,
    val owner: String,
    val users: List<String>,
)

private val CODE_CHARACTERS = ('a'..'z').joinToString("") +
        ('A'..'Z').joinToString("") +
        ('0'..'9').joinToString("")
private fun  generateCode(length: Int = 8): String {
    return CharArray(length) { CODE_CHARACTERS.random() }.concatToString()
}

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
                    .projection(
                        RoomInfo::id,
                        RoomInfo::name,
                        RoomInfo::owner,
                        RoomInfo::users,
                    ).toList()

                call.respond(rooms)
            }
            get("/rooms/brief-list") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.getClaim("username", String::class)
                if (username == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val roomsCollection = db.getRoomsCollection()

                val rooms = roomsCollection
                    .withDocumentClass<RoomBriefInfo>()
                    .find(Room::users contains username)
                    .projection(RoomBriefInfo::id, RoomBriefInfo::name).toList()

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

                if (roomName.length < 4) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val roomsCollection = db.getRoomsCollection()

                val alreadyExistRoom = roomsCollection.findOne(
                    and(
                        Room::users contains username,
                        Room::name eq roomName,
                    )
                )

                if (alreadyExistRoom != null) {
                    call.respond(HttpStatusCode.Conflict, "Room with that name already exists")
                    return@post
                }

                val roomId = UUID.randomUUID().toString()
                val createdRoom = Room(
                    id = roomId,
                    owner = username,
                    name = roomName,
                    shopList = ShopList(listOf()),
                    users = listOf(username)
                )

                roomsCollection.insertOne(createdRoom)

                call.respond(HttpStatusCode.OK)
            }

            post("/rooms/leave") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.getClaim("username", String::class)

                if (username == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val roomId = call.parameters["roomId"]

                if (roomId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val roomsCollection = db.getRoomsCollection()

                val roomOwner = roomsCollection.projection(Room::owner, Room::id eq roomId).first()
                if (roomOwner == username) {
                    roomsCollection.deleteOne(Room::id eq roomId)
                } else {
                    roomsCollection.updateOne(
                        filter = Room::id eq roomId,
                        update = pullByFilter(
                            Room::users contains username,
                        ),
                    )
                }

                call.respond(HttpStatusCode.OK)
            }
            post("/rooms/use-invite-code") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.getClaim("username", String::class)

                if (username == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val code = call.parameters["code"]

                if (code == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val roomInvitesCollection = db.getRoomInvitesCollection()

                val roomInvite = roomInvitesCollection.findOne(
                    RoomInvite::code eq code,
                )

                if (roomInvite == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val roomsCollection = db.getRoomsCollection()
                roomsCollection.updateOne(
                    filter = Room::id eq roomInvite.roomId,
                    update = addToSet(
                        Room::users,
                        username,
                    ),
                )

                roomInvitesCollection.deleteOne(
                    RoomInvite::code eq code,
                )

                call.respond(HttpStatusCode.OK)
            }

            post("/rooms/invite") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.getClaim("username", String::class)

                if (username == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val roomId = call.parameters["roomId"]

                if (roomId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val roomsCollection = db.getRoomsCollection()

                val roomOwner = roomsCollection.projection(Room::owner, Room::id eq roomId).first()

                if (roomOwner != username) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val roomInvitesCollection = db.getRoomInvitesCollection()
                val code = generateCode()
                roomInvitesCollection.insertOne(RoomInvite(
                    code = code,
                    roomId = roomId,
                ))

                call.respond(code)
            }
            post("/rooms/remove-user") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.getClaim("username", String::class)

                if (username == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val roomId = call.parameters["roomId"]
                val userToRemove = call.parameters["user"]

                if (roomId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val roomsCollection = db.getRoomsCollection()

                val roomOwner = roomsCollection.projection(Room::owner, Room::id eq roomId).first()

                if (roomOwner != username) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                if (roomOwner == userToRemove) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                roomsCollection.updateOne(
                    filter = Room::id eq roomId,
                    update = pullByFilter(
                        Room::users contains userToRemove,
                    ),
                )

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
