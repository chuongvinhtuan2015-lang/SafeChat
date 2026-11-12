package com.example.safechat.data.local.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "messages",
        indices = {@Index(value = {"conversationId"}), @Index(value = {"timestamp"})}
)
public class MessageEntity {

    @PrimaryKey
    @NonNull
    private String messageId;
    private String conversationId;
    private String senderId;
    private String receiverId;
    private String messageText;
    private long timestamp;
    private boolean isSentByMe;
    private String status;

    public MessageEntity(@NonNull String messageId, String conversationId, String senderId,
                         String receiverId, String messageText, long timestamp,
                         boolean isSentByMe, String status) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.isSentByMe = isSentByMe;
        this.status = status;
    }

    @NonNull
    public String getMessageId() { return messageId; }
    public void setMessageId(@NonNull String messageId) { this.messageId = messageId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isSentByMe() { return isSentByMe; }
    public void setSentByMe(boolean sentByMe) { isSentByMe = sentByMe; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}