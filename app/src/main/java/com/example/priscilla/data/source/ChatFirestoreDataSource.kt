package com.example.priscilla.data.source

import android.util.Log
import com.example.priscilla.data.ChatTurnEntity
import com.example.priscilla.data.Conversation
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// This interface defines the contract for any chat data source, cloud or local.
// For now, it will be focused on the cloud operations we need.
interface ChatCloudDataSource {
    fun getConversationsFlow(userId: String): Flow<List<Conversation>>
    //  conversationId is now a String ---
    suspend fun getTurnsForConversation(userId: String, conversationId: String): List<ChatTurnEntity>
    // the Map key is now a String ---
    suspend fun uploadConversations(userId: String, conversations: List<Conversation>, turns: Map<String, List<ChatTurnEntity>>)
    suspend fun hasCloudData(userId: String): Boolean
}
// The concrete implementation that uses Firebase Firestore.
class ChatFirestoreDataSourceImpl(
    private val firestore: FirebaseFirestore = Firebase.firestore
) : ChatCloudDataSource {

    // Firestore collection names
    private companion object {
        const val USERS_COLLECTION = "users"
        const val CONVERSATIONS_COLLECTION = "conversations"
        const val TURNS_COLLECTION = "turns"
    }

    override fun getConversationsFlow(userId: String): Flow<List<Conversation>> = callbackFlow {
        // Create a reference to the user's conversations collection.
        val collectionRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CONVERSATIONS_COLLECTION)

        // addSnapshotListener is the key to real-time updates.
        // It will trigger every time the data changes in this collection.
        val listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatFirestore", "Listen failed.", error)
                close(error) // Close the flow on error
                return@addSnapshotListener
            }

            if (snapshot != null) {
                // We have a new snapshot. Convert the documents to our Conversation data class.
                val conversations = snapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val startTime = (doc.get("startTime") as? Number)?.toLong() ?: 0L
                    val firstMessagePreview = doc.getString("firstMessagePreview") ?: ""
                    val lastModified = (doc.get("lastModified") as? Number)?.toLong() ?: startTime

                    Conversation(
                        id = id,
                        startTime = startTime,
                        firstMessagePreview = firstMessagePreview,
                        kvCachePaths = emptyMap(),
                        lastModified = lastModified
                    )
                }
                // Emit the new list to the Flow.
                trySend(conversations)
            }
        }

        // This is called when the Flow is cancelled (e.g., the user logs out).
        // It's crucial for preventing memory leaks.
        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun getTurnsForConversation(userId: String, conversationId: String): List<ChatTurnEntity> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .collection(TURNS_COLLECTION)
                .orderBy("id")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val convId = doc.getString("conversationId") ?: ""
                val user = doc.getString("user") ?: ""
                val assistant = doc.getString("assistant") ?: ""
                val imagePath = doc.getString("imagePath")

                ChatTurnEntity(
                    id = id,
                    conversationId = convId,
                    user = user,
                    assistant = assistant,
                    imagePath = imagePath
                )
            }
        } catch (e: Exception) {
            Log.e("ChatFirestore", "Error getting turns for conversation $conversationId", e)
            emptyList()
        }
    }


    override suspend fun uploadConversations(userId: String, conversations: List<Conversation>, turns: Map<String, List<ChatTurnEntity>>) {
        try {
            // Use a batched write to perform all operations as a single atomic unit.
            val batch = firestore.batch()

            conversations.forEach { conversation ->
                // Create a reference to the document for this conversation.
                // We use the conversation's ID as the document ID for easy lookup.
                val conversationDocRef = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CONVERSATIONS_COLLECTION)
                    .document(conversation.id)

                // Create a map of the data, excluding the KV cache.
                val conversationData = mapOf(
                    "id" to conversation.id,
                    "startTime" to conversation.startTime,
                    "firstMessagePreview" to conversation.firstMessagePreview,
                    "lastModified" to conversation.lastModified
                )
                // Add the conversation data to the batch.
                batch.set(conversationDocRef, conversationData)

                // Get the turns for this specific conversation.
                val conversationTurns = turns[conversation.id] ?: emptyList()
                conversationTurns.forEach { turn ->
                    // Create a reference to the turn document within its conversation.
                    val turnDocRef = conversationDocRef.collection(TURNS_COLLECTION).document(turn.id)

                    // Create the turn data map.
                    val turnData = mapOf(
                        "id" to turn.id,
                        "conversationId" to turn.conversationId,
                        "user" to turn.user,
                        "assistant" to turn.assistant,
                        // Include the imagePath. If it's null, Firestore will omit the field.
                        "imagePath" to turn.imagePath
                    )
                    // Add the turn data to the batch.
                    batch.set(turnDocRef, turnData)
                }
            }

            // Commit the batch to Firestore. This sends all the data in one go.
            batch.commit().await()
            Log.i("ChatFirestore", "Successfully uploaded ${conversations.size} conversations.")
        } catch (e: Exception) {
            Log.e("ChatFirestore", "Error uploading conversations", e)
            throw e
        }
    }

    override suspend fun hasCloudData(userId: String): Boolean {
        return try {
            // We perform a very lightweight query. We only ask for ONE document.
            // If the result is not empty, we know they have data.
            // This is much cheaper than fetching all conversations.
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CONVERSATIONS_COLLECTION)
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            Log.e("ChatFirestore", "Error checking for cloud data", e)
            false
        }
    }
}