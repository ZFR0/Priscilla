package com.example.priscilla.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class Converters {
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, String>>() {}.type

    @TypeConverter
    fun fromString(value: String?): Map<String, String> {
        return if (value == null) {
            emptyMap()
        } else {
            gson.fromJson(value, type)
        }
    }

    @TypeConverter
    fun fromMap(map: Map<String, String>): String {
        return gson.toJson(map)
    }
}

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val firstMessagePreview: String,
    val kvCachePaths: Map<String, String> = emptyMap(),
    val lastModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(tableName = "chat_turns",
    foreignKeys = [ForeignKey(
        entity = Conversation::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    // Add an index for faster lookups by conversationId
    indices = [Index(value = ["conversationId"])]
)
data class ChatTurnEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val user: String,
    val assistant: String,
    val imagePath: String? = null
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val task: String,
    val triggerAtMillis: Long,
    val lastModified: Long = System.currentTimeMillis()
)

// --- Data Transfer Object (to group a conversation with its turns) ---

data class ConversationWithTurns(
    @Embedded val conversation: Conversation,
    @Relation(
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val turns: List<ChatTurnEntity>
)

// --- DAO (Data Access Object) - The interface for database operations ---

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<Conversation>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurns(turns: List<ChatTurnEntity>)

    @Query("SELECT * FROM conversations WHERE isDeleted = 0 ORDER BY isFavorite DESC, startTime DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Transaction
    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun getConversationWithTurns(conversationId: String): Flow<ConversationWithTurns?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: String): ReminderEntity?

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    @Query("SELECT * FROM chat_turns WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getTurnsForConversation(conversationId: String): List<ChatTurnEntity>

    @Query("UPDATE conversations SET kvCachePaths = :pathsAsJson WHERE id = :conversationId")
    suspend fun updateKvCachePaths(conversationId: String, pathsAsJson: String)

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): Conversation?

    @Transaction
    @Query("SELECT * FROM conversations WHERE isDeleted = 0 ORDER BY isFavorite DESC, startTime DESC")
    fun getAllConversationsWithTurns(): Flow<List<ConversationWithTurns>>

    @Query("UPDATE conversations SET lastModified = :timestamp WHERE id = :conversationId")
    suspend fun updateConversationTimestamp(conversationId: String, timestamp: Long)

    @Query("UPDATE conversations SET isFavorite = :isFavorite, lastModified = :timestamp WHERE id = :conversationId")
    suspend fun setFavoriteStatus(conversationId: String, isFavorite: Boolean, timestamp: Long)

    @Query("UPDATE conversations SET isDeleted = 1, lastModified = :timestamp WHERE id = :conversationId")
    suspend fun softDeleteConversation(conversationId: String, timestamp: Long)

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query("DELETE FROM chat_turns")
    suspend fun clearTurns()

    @Query("DELETE FROM chat_turns WHERE conversationId = :conversationId")
    suspend fun deleteTurnsForConversation(conversationId: String)

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationByIdIncludingDeleted(conversationId: String): Conversation?
}

// --- The Database Class ---

@Database(entities = [Conversation::class, ChatTurnEntity::class, ReminderEntity::class], version = 10)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}