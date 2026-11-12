package com.example.safechat.data.local.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.safechat.data.local.db.entities.RatchetSessionEntity;

@Dao
public interface RatchetSessionDao {

    @Query("SELECT * FROM ratchet_sessions WHERE conversationId = :conversationId LIMIT 1")
    RatchetSessionEntity getSession(String conversationId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveSession(RatchetSessionEntity session);

    @Query("DELETE FROM ratchet_sessions WHERE conversationId = :conversationId")
    void deleteSession(String conversationId);
}