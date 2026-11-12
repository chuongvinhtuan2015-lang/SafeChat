package com.example.safechat.data.local.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversations")
public class ConversationEntity {

    @PrimaryKey
    @NonNull
    private String conversationId;
    private String partnerId;
    private String partnerName;
    private String partnerAvatar;
    private String lastMessage;
    private long lastTimestamp;
    private String peerEd25519PublicKey;

    public ConversationEntity(@NonNull String conversationId, String partnerId,
                              String partnerName, String partnerAvatar,
                              String lastMessage, long lastTimestamp,
                              String peerEd25519PublicKey) {
        this.conversationId = conversationId;
        this.partnerId = partnerId;
        this.partnerName = partnerName;
        this.partnerAvatar = partnerAvatar;
        this.lastMessage = lastMessage;
        this.lastTimestamp = lastTimestamp;
        this.peerEd25519PublicKey = peerEd25519PublicKey;
    }

    @NonNull
    public String getConversationId() { return conversationId; }
    public void setConversationId(@NonNull String conversationId) { this.conversationId = conversationId; }

    public String getPartnerId() { return partnerId; }
    public void setPartnerId(String partnerId) { this.partnerId = partnerId; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getPartnerAvatar() { return partnerAvatar; }
    public void setPartnerAvatar(String partnerAvatar) { this.partnerAvatar = partnerAvatar; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(long lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public String getPeerEd25519PublicKey() { return peerEd25519PublicKey; }
    public void setPeerEd25519PublicKey(String peerEd25519PublicKey) { this.peerEd25519PublicKey = peerEd25519PublicKey; }
}