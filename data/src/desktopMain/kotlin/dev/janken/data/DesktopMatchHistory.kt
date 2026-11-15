package dev.janken.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.janken.prototype.game.MatchHistoryRepository
import java.nio.file.Files
import java.nio.file.Path

fun desktopMatchHistory(directory: Path): MatchHistoryRepository =
    RoomMatchHistory(openMatchDatabase(directory).matches())

internal fun openMatchDatabase(directory: Path): MatchDatabase {
    Files.createDirectories(directory)
    return Room.databaseBuilder<MatchDatabase>(directory.resolve(MATCH_DATABASE_NAME).toString())
        .setDriver(BundledSQLiteDriver())
        .build()
}
