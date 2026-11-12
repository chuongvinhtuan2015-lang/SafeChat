package com.example.safechat.data.local.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.safechat.data.local.db.entities.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    LiveData<List<MessageEntity>> getMessagesByConversation(String conversationId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(MessageEntity message);

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    void updateStatus(String messageId, String status);

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    void clearMessagesByConversation(String conversationId);

    @Query("SELECT * FROM messages WHERE messageId = :id LIMIT 1")
    MessageEntity getMessageById(String id);
}