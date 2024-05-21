package ru.reosfire.money.manage.shoplist

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.addToSet
import org.litote.kmongo.div
import org.litote.kmongo.eq
import ru.reosfire.money.manage.data.*
import java.util.*

@Serializable
private class ShopListItemNet(
    val name: String,
    val price: Double,
    val checked: Boolean,
    val emoji: String,
    val tags: List<Tag>,
)

fun Application.setupShopListRoutes(db: DB) {
    routing {
        authenticate {
            get("/shop-list/list") {
                val roomId = call.parameters["roomId"]
                if (roomId == null) {
                    //TODO err
                }
                val query = call.parameters["filters"]

                val roomsCollection = db.getRoomsCollection()

                val room = roomsCollection.findOne(
                    Room::id eq roomId,
                )

                if (room == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                call.respond(room.shopList)
            }

            get("/shop-list/edit") {
                val roomId = call.parameters["roomId"]
                if (roomId == null) {
                    //TODO err
                }

                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val name = call.parameters["name"]
                val price = call.parameters["price"]?.toDouble()
                val checked = call.parameters["checked"]?.let { it == "true" }
                val emoji = call.parameters["emoji"]
                val tags = call.parameters["tags"]
            }

            post("/shop-list/add") {
                val roomId = call.parameters["roomId"]
                if (roomId == null) {
                    //TODO err
                }

                val addItem = call.receive<ShopListItemNet>()

                val uuid = UUID.randomUUID().toString()

                val roomsCollection = db.getRoomsCollection()
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

            get("/shop-list/remove") {
                val roomId = call.parameters["roomId"]
                if (roomId == null) {
                    //TODO err
                }

                val id = call.parameters["id"]?.let { UUID.fromString(it) }

            }
        }
    }
}