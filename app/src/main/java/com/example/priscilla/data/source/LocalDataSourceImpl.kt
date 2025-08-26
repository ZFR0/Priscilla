package com.example.priscilla.data.source

import com.example.priscilla.LocalDataSource
import com.example.priscilla.data.ChatDao
import com.example.priscilla.data.ChatTurnEntity
import com.example.priscilla.data.Conversation
import com.example.priscilla.data.ConversationWithTurns
import kotlinx.coroutines.flow.Flow

class LocalDataSourceImpl(
    private val chatDao: ChatDao
) : LocalDataSource {

    override fun getAllConversations(): Flow<List<Conversation>> {
        return chatDao.getAllConversations()
    }

    override fun getConversationWithTurns(conversationId: String): Flow<ConversationWithTurns?> {
        return chatDao.getConversationWithTurns(conversationId)
    }

    override suspend fun getTurnsForConversation(conversationId: String): List<ChatTurnEntity> {
        return chatDao.getTurnsForConversation(conversationId)
    }

    override suspend fun setFavoriteStatus(conversationId: String, isFavorite: Boolean, timestamp: Long) {
        chatDao.setFavoriteStatus(conversationId, isFavorite, timestamp)
    }

    override suspend fun softDeleteConversation(conversationId: String, timestamp: Long) {
        chatDao.softDeleteConversation(conversationId, timestamp)
    }

    override suspend fun getConversationById(conversationId: String): Conversation? {
        return chatDao.getConversationById(conversationId)
    }

    override suspend fun getConversationByIdIncludingDeleted(conversationId: String): Conversation? {
        return chatDao.getConversationByIdIncludingDeleted(conversationId)
    }

    override suspend fun insertConversation(conversation: Conversation) {
        chatDao.insertConversation(conversation)
    }

    override suspend fun insertConversations(conversations: List<Conversation>) {
        chatDao.insertConversations(conversations)
    }

    override suspend fun insertTurns(turns: List<ChatTurnEntity>) {
        chatDao.insertTurns(turns)
    }

    override suspend fun updateKvCachePaths(conversationId: String, pathsAsJson: String) {
        chatDao.updateKvCachePaths(conversationId, pathsAsJson)
    }

    override suspend fun updateConversationTimestamp(conversationId: String, timestamp: Long) {
        chatDao.updateConversationTimestamp(conversationId, timestamp)
    }

    override suspend fun clearAllChatData() {
        chatDao.clearConversations()
        chatDao.clearTurns()
    }

    override suspend fun deleteTurnsForConversation(conversationId: String) {
        chatDao.deleteTurnsForConversation(conversationId)
    }
}