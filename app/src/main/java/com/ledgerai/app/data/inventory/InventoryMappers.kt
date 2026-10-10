package com.ledgerai.app.data.inventory

import com.ledgerai.app.domain.inventory.ItemConfidence
import com.ledgerai.app.domain.inventory.ItemKind
import com.ledgerai.app.domain.inventory.ShoppingLine
import com.ledgerai.app.domain.inventory.ShoppingList
import com.ledgerai.app.domain.inventory.StockItem

fun ItemEntity.toDomain(): StockItem = StockItem(
    id = id,
    ownerUserId = ownerUserId,
    householdId = householdId,
    name = name,
    category = category,
    quantity = quantity,
    unit = unit,
    location = location,
    expiresOn = expiresOn,
    kind = ItemKind.fromStored(kind),
    sourceType = sourceType,
    sourceId = sourceId,
    confidence = ItemConfidence.fromStored(confidence),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun ShoppingListEntity.toDomain(): ShoppingList = ShoppingList(
    id = id,
    householdId = householdId,
    userId = userId,
    name = name,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun ShoppingItemEntity.toDomain(): ShoppingLine = ShoppingLine(
    id = id,
    listId = listId,
    itemName = itemName,
    qty = qty,
    addedBy = addedBy,
    boughtBy = boughtBy,
    boughtAt = boughtAt,
    status = status,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
