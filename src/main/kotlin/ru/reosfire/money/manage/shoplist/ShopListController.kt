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
    val name: String? = null,
    val price: Double? = null,
    val checked: Boolean? = null,
    val emoji: String? = null,
    val tags: List<Tag>? = null,
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

private const val PRICE_LABEL = "price:"
private const val CHECKED_LABEL = "checked:"
private const val NAME_LABEL = "name:"

fun parseQuery(query: String): Bson {
    val priceRangeIndex = query.indexOf(PRICE_LABEL)
    val checkedIndex = query.indexOf(CHECKED_LABEL)
    val nameIndex = query.indexOf(NAME_LABEL)

    val filters = mutableListOf<Bson>()

    if (priceRangeIndex != -1) {
        val end = query.indexOf(";", priceRangeIndex)
        if (end != -1) {
            val value = query.substring(priceRangeIndex + PRICE_LABEL.length ..<end)
            val tokens = value.split("..")
            if (tokens.size == 2) {
                runCatching {
                    val lowest = tokens[0].toDouble()
                    val highest = tokens[1].toDouble()
                    filters.add(
                        and(
                            Room::shopList / ShopList::items / ShopListItem::price gte lowest,
                            Room::shopList / ShopList::items / ShopListItem::price lte highest,
                        )
                    )
                }
            }
        }
    }
    if(checkedIndex != -1) {
        val end = query.indexOf(";", checkedIndex)
        if (end != -1) {
            val value = query.substring(checkedIndex + CHECKED_LABEL.length ..<end)
            when (value) {
                "true" -> filters.add(Room::shopList / ShopList::items / ShopListItem::checked eq true)
                "false" -> filters.add(Room::shopList / ShopList::items / ShopListItem::checked eq false)
            }
        }
    }
    if(nameIndex != -1) {
        val end = query.indexOf(";", nameIndex)
        if (end != -1) {
            val value = query.substring(nameIndex + NAME_LABEL.length ..<end)
            if (value.isNotBlank()) {
                filters.add(Room::shopList / ShopList::items / ShopListItem::name regex value)
            }
        }
    }

    if (filters.isEmpty()) {
        filters.add(Room::shopList / ShopList::items / ShopListItem::name regex query)
    }

    return and(filters)
}

fun Application.setupShopListRoutes(db: DB) {
    routing {
        authenticate {
            get("/shop-list/list") {
                commonChecks(db) {
                    val query = call.parameters["query"]

                    val aggregationPipeline = mutableListOf(
                        match(Room::id eq roomId),
                        (Room::shopList / ShopList::items).unwind(),
                    )

                    if (!query.isNullOrBlank()) {
                        aggregationPipeline.add(match(parseQuery(query)))
                    }

                    aggregationPipeline.add(
                        sort(
                            combine(
                                ascending(Room::shopList / ShopList::items / ShopListItem::checked),
                                ascending(Room::shopList / ShopList::items / ShopListItem::name),
                                descending(Room::shopList / ShopList::items / ShopListItem::price),
                            )
                        )
                    )
                    aggregationPipeline.add(
                        group("\$uuid", ShopList::items.push("\$shopList.items")),
                    )

                    val shopList = roomsCollection.aggregate<ShopList>(
                        aggregationPipeline,
                    ).first()

                    call.respond(shopList ?: ShopList(emptyList()))
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