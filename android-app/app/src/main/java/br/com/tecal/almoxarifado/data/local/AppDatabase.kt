package br.com.tecal.almoxarifado.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import br.com.tecal.almoxarifado.data.model.MovementItemDto
import br.com.tecal.almoxarifado.data.model.PendingMovementDto
import br.com.tecal.almoxarifado.security.SecurityGuard
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val vaultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Entity(tableName = "cached_pending_movements")
data class CachedPendingMovementEntity(
    @PrimaryKey val idMov: Int,
    val encryptedPayload: String
) {
    fun toDtoOrNull(): PendingMovementDto? = runCatching {
        val plain = SecurityGuard.decryptVaultData(encryptedPayload) ?: return null
        vaultJson.decodeFromString<PendingMovementDto>(plain)
    }.getOrNull()

    companion object {
        fun fromDto(dto: PendingMovementDto) = CachedPendingMovementEntity(
            idMov = dto.idMov,
            encryptedPayload = SecurityGuard.encryptVaultData(vaultJson.encodeToString(dto))
        )
    }
}

@Entity(
    tableName = "cached_movement_items",
    primaryKeys = ["idMov", "nSeqItmMov"]
)
data class CachedMovementItemEntity(
    val idMov: Int,
    val nSeqItmMov: Int,
    val encryptedPayload: String
) {
    fun toDtoOrNull(): MovementItemDto? = runCatching {
        val plain = SecurityGuard.decryptVaultData(encryptedPayload) ?: return null
        vaultJson.decodeFromString<MovementItemDto>(plain)
    }.getOrNull()

    companion object {
        fun fromDto(dto: MovementItemDto) = CachedMovementItemEntity(
            idMov = dto.idMov,
            nSeqItmMov = dto.nSeqItmMov,
            encryptedPayload = SecurityGuard.encryptVaultData(vaultJson.encodeToString(dto))
        )
    }
}

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM cached_pending_movements ORDER BY idMov DESC")
    suspend fun getAllPendingMovements(): List<CachedPendingMovementEntity>

    @Query("DELETE FROM cached_pending_movements")
    suspend fun clearAllPendingMovements()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingMovements(items: List<CachedPendingMovementEntity>)

    @Transaction
    suspend fun replaceAllPendingMovements(items: List<CachedPendingMovementEntity>) {
        clearAllPendingMovements()
        insertPendingMovements(items)
    }

    @Query("DELETE FROM cached_pending_movements WHERE idMov = :idMov")
    suspend fun deleteMovementById(idMov: Int)

    @Query("SELECT * FROM cached_movement_items WHERE idMov = :idMov ORDER BY nSeqItmMov ASC")
    suspend fun getItemsForMovement(idMov: Int): List<CachedMovementItemEntity>

    @Query("DELETE FROM cached_movement_items WHERE idMov = :idMov")
    suspend fun deleteItemsForMovement(idMov: Int)

    @Query("DELETE FROM cached_movement_items")
    suspend fun clearAllMovementItems()

    @Transaction
    suspend fun clearAllCache() {
        clearAllPendingMovements()
        clearAllMovementItems()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovementItems(items: List<CachedMovementItemEntity>)

    @Transaction
    suspend fun replaceMovementItems(idMov: Int, items: List<CachedMovementItemEntity>) {
        deleteItemsForMovement(idMov)
        insertMovementItems(items)
    }
}

@Database(
    entities = [CachedPendingMovementEntity::class, CachedMovementItemEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun withdrawalDao(): WithdrawalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tecal_almoxarifado.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
