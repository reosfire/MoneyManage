package ru.reosfire.money.manage.shoplist

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.projection
import ru.reosfire.money.manage.data.*
import java.util.*

@Serializable
private data class ShopListItemAdd(
    val name: String,
    val price: Double,
    val checked: Boolean,
    val emoji: String,
    val tags: List<Tag>,
)

@Serializable
private data class ShopListItemEdit(
    val name: String?,
    val price: Double?,
    val checked: Boolean?,
    val emoji: String?,
    val tags: List<Tag>,
)

private class CommonContext(
    val roomsCollection: CoroutineCollection<Room>,
    val roomId: String,
    val username: String,
)

private suspend inline fun PipelineContext<Unit, ApplicationCall>.commonChecks(
    db: DB,
    block: CommonContext.() -> Unit,
) {
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

fun Application.setupShopListRoutes(db: DB) {
    routing {
        authenticate {
            get("/shop-list/list") {
                commonChecks(db) {
                    val filters = call.parameters["filters"]
                    val sort = call.parameters["sort"]

                    val room = roomsCollection.findOne(
                        Room::id eq roomId,
                    )

                    if (room == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }

                    call.respond(room.shopList)
                }
            }

            post("/shop-list/edit") {
                val id = call.parameters["id"]

                if (id == null) {
                    call.respond(HttpStatusCode.NotModified)
                    return@post
                }

                commonChecks(db) {
                    val editItem = call.receive<ShopListItemEdit>()

                    val updates = mutableListOf<Bson>()

                    val root = Room::shopList % ShopList::items.posOp
                    editItem.name?.let { updates.add(setValue(root / ShopListItem::name, it))}
                    editItem.price?.let { updates.add(setValue(root / ShopListItem::price, it))}
                    editItem.checked?.let { updates.add(setValue(root / ShopListItem::checked, it))}
                    editItem.emoji?.let { updates.add(setValue(root / ShopListItem::emoji, it))}

                    roomsCollection.updateOne(
                        filter = combine(
                            Room::id eq roomId,
                            Room::shopList / ShopList::items / ShopListItem::uuid eq id,
                        ),
                        update = combine(updates)
                    )

                    call.respond(HttpStatusCode.OK)
                }
            }

            post("/shop-list/add") {
                commonChecks(db) {
                    val addItem = call.receive<ShopListItemAdd>()

                    val uuid = UUID.randomUUID().toString()

                    roomsCollection.updateOne(
                        filter = Room::id eq roomId,
                        update = addToSet(
                            Room::shopList / ShopList::items,
                            ShopListItem(
                                uuid = uuid,
                                name = addItem.name,
                                price = addItem.price,
                                checked = addItem.checked,
                                emoji = addItem.emoji,
                                tags = addItem.tags,
                            )
                        ),
                    )

                    call.respond(uuid)
                }
            }

            delete("/shop-list/remove") {
                commonChecks(db) {
                    val id = call.parameters["id"]

                    if (id == null) {
                        call.respond(HttpStatusCode.NotModified)
                        return@delete
                    }

                    roomsCollection.updateOne(
                        filter = Room::id eq roomId,
                        update = pullByFilter(
                            Room::shopList / ShopList::items,
                            ShopListItem::uuid eq id
                        ),
                    )

                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}