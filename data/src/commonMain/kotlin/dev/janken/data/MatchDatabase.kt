package dev.janken.data

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Upsert

internal const val MATCH_DATABASE_NAME = "janken-matches.db"

@Entity(tableName = "matches")
internal data class MatchEntity(
    @PrimaryKey val id: String,
    val rounds: String,
    val playerScore: Int,
    val botScore: Int,
    val finished: Boolean,
)

@Dao
internal interface MatchDao {
    @Query("SELECT * FROM matches WHERE finished = 1 OR rowid = (SELECT MAX(rowid) FROM matches) ORDER BY rowid DESC")
    suspend fun currentAndCompleted(): List<MatchEntity>

    @Upsert
    suspend fun save(match: MatchEntity)

    @Query("DELETE FROM matches WHERE id != :currentId")
    suspend fun deleteOtherMatches(currentId: String)

    @Transaction
    suspend fun clearCompleted(current: MatchEntity) {
        save(current)
        deleteOtherMatches(current.id)
    }
}

@Database(entities = [MatchEntity::class], version = 1)
@ConstructedBy(MatchDatabaseConstructor::class)
internal abstract class MatchDatabase : RoomDatabase() {
    abstract fun matches(): MatchDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object MatchDatabaseConstructor : RoomDatabaseConstructor<MatchDatabase> {
    override fun initialize(): MatchDatabase
}
