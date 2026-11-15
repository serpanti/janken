package dev.janken.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.janken.prototype.game.MatchHistoryRepository
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun iosMatchHistory(): MatchHistoryRepository {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, null, true, null,
    )
    val path = checkNotNull(directory?.URLByAppendingPathComponent(MATCH_DATABASE_NAME)?.path)
    val database = Room.databaseBuilder<MatchDatabase>(path)
        .setDriver(BundledSQLiteDriver())
        .build()
    return RoomMatchHistory(database.matches())
}
