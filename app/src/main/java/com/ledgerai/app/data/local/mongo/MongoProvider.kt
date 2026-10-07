package com.ledgerai.app.data.local.mongo

import com.ledgerai.app.BuildConfig
import com.mongodb.kotlin.client.sync.MongoClient
import com.mongodb.kotlin.client.sync.MongoDatabase
import org.bson.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MongoProvider @Inject constructor() {

    private val uri: String
        get() = BuildConfig.MONGODB_URI.ifBlank {
            throw IllegalStateException("MONGODB_URI is empty. Add it to secrets.properties")
        }

    private val client: MongoClient by lazy { MongoClient.create(uri) }

    /** Database name used for LedgerAI collections */
    val database: MongoDatabase by lazy {
        client.getDatabase(DATABASE_NAME)
    }

    fun transactionsCollection() = database.getCollection<Document>(COLLECTION_TRANSACTIONS)
    fun budgetsCollection() = database.getCollection<Document>(COLLECTION_BUDGETS)
    fun debtsCollection() = database.getCollection<Document>(COLLECTION_DEBTS)
    fun goalsCollection() = database.getCollection<Document>(COLLECTION_GOALS)
    fun billsCollection() = database.getCollection<Document>(COLLECTION_BILLS)

    companion object {
        const val DATABASE_NAME = "ledgerai"
        const val COLLECTION_TRANSACTIONS = "transactions"
        const val COLLECTION_BUDGETS = "budgets"
        const val COLLECTION_DEBTS = "debts"
        const val COLLECTION_GOALS = "goals"
        const val COLLECTION_BILLS = "bills"
    }
}
