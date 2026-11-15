package dev.janken.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.janken.prototype.game.MatchHistoryRepository

fun androidMatchHistory(context: Context): MatchHistoryRepository {
    val database = Room.databaseBuilder(
        context.applicationContext, MATCH_DATABASE_NAME, MatchDatabaseConstructor::initialize,
    )
        .setDriver(BundledSQLiteDriver())
        .build()
    return RoomMatchHistory(database.matches())
}
