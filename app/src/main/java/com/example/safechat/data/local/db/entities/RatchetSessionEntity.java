package com.example.safechat.data.local.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ratchet_sessions")
public class RatchetSessionEntity {

    @PrimaryKey
    @NonNull
    private String conversationId;
    private String rootKey;        // Base64
    private String sendChainKey;   // Base64 (dùng để sinh khóa gửi tin tiếp theo)
    private String recvChainKey;   // Base64 (dùng để sinh khóa giải mã tin tiếp theo)
    private int currentKeyIndex;   // Số bước nhảy KDF hiện tại

    public RatchetSessionEntity(@NonNull String conversationId, String rootKey,
                                String sendChainKey, String recvChainKey, int currentKeyIndex) {
        this.conversationId = conversationId;
        this.rootKey = rootKey;
        this.sendChainKey = sendChainKey;
        this.recvChainKey = recvChainKey;
        this.currentKeyIndex = currentKeyIndex;
    }

    @NonNull
    public String getConversationId() { return conversationId; }
    public void setConversationId(@NonNull String conversationId) { this.conversationId = conversationId; }

    public String getRootKey() { return rootKey; }
    public void setRootKey(String rootKey) { this.rootKey = rootKey; }

    public String getSendChainKey() { return sendChainKey; }
    public void setSendChainKey(String sendChainKey) { this.sendChainKey = sendChainKey; }

    public String getRecvChainKey() { return recvChainKey; }
    public void setRecvChainKey(String recvChainKey) { this.recvChainKey = recvChainKey; }

    public int getCurrentKeyIndex() { return currentKeyIndex; }
    public void setCurrentKeyIndex(int currentKeyIndex) { this.currentKeyIndex = currentKeyIndex; }
}