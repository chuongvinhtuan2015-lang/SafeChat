package com.example.safechat.data.local.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.safechat.data.local.db.entities.ConversationEntity;

import java.util.List;
import java.util.Map;

@Dao
public interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastTimestamp DESC")
    LiveData<List<ConversationEntity>> getAllConversations();

    @Query("SELECT * FROM conversations WHERE conversationId = :conversationId LIMIT 1")
    ConversationEntity getConversationById(String conversationId);

    // Bổ sung: Tìm hội thoại theo partnerId (dùng khi mồi danh sách từ bạn bè)
    @Query("SELECT * FROM conversations WHERE partnerId = :partnerId LIMIT 1")
    ConversationEntity getConversationByPartnerId(String partnerId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ConversationEntity conversation);

    @Query("UPDATE conversations SET lastMessage = :lastMsg, lastTimestamp = :timestamp WHERE conversationId = :convId")
    void updateLastMessage(String convId, String lastMsg, long timestamp);

    // Cập nhật nhanh URL avatar khi bạn chat đổi ảnh
    @Query("UPDATE conversations SET partnerAvatar = :avatarUrl WHERE conversationId = :convId")
    void updateAvatar(String convId, String avatarUrl);

    // Cập nhật đồng bộ cả tên và avatar nếu user đổi thông tin cá nhân
    @Query("UPDATE conversations SET partnerName = :name, partnerAvatar = :avatarUrl WHERE partnerId = :partnerId")
    void updatePartnerProfile(String partnerId, String name, String avatarUrl);

    @Query("DELETE FROM conversations WHERE conversationId = :conversationId")
    void deleteConversation(String conversationId);

    // Bổ sung: Xóa hội thoại theo partnerId (dùng khi hủy kết bạn hoặc chặn)
    @Query("DELETE FROM conversations WHERE partnerId = :partnerId")
    void deleteConversationByPartnerId(String partnerId);
    @Query("SELECT * FROM conversations")
    List<ConversationEntity> getAllConversationsSync();
    @Transaction
    default void updateProfilesMap(Map<String, String[]> profileMap) {
        for (Map.Entry<String, String[]> entry : profileMap.entrySet()) {
            String partnerId = entry.getKey();
            String name = entry.getValue()[0];
            String avatar = entry.getValue()[1];
            updatePartnerProfile(partnerId, name, avatar);
        }
    }
}