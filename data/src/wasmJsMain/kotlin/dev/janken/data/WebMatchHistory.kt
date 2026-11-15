package dev.janken.data

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import dev.janken.prototype.game.MatchHistoryRepository
import org.w3c.dom.Worker

fun webMatchHistory(worker: Worker): MatchHistoryRepository {
    val database = Room.databaseBuilder<MatchDatabase>(MATCH_DATABASE_NAME)
        .setDriver(WebWorkerSQLiteDriver(worker))
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .build()
    return RoomMatchHistory(database.matches())
}
