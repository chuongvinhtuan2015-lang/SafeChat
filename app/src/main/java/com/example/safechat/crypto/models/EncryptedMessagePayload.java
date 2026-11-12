package com.example.safechat.crypto.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class EncryptedMessagePayload {
    private String ciphertext; // Bản mã AES-256-GCM (Base64)
    private String iv;         // Initialization Vector 12 bytes ngẫu nhiên (Base64)
    private String signature;  // Chữ ký số Ed25519 của người gửi (Base64)
    private int keyIndex;      // Thứ tự tin nhắn trong chuỗi xoay khóa
    private String senderId;
    private String receiverId;
    private long timestamp;

    public EncryptedMessagePayload() {
    }

    public EncryptedMessagePayload(String ciphertext, String iv, String signature,
                                   int keyIndex, String senderId, String receiverId, long timestamp) {
        this.ciphertext = ciphertext;
        this.iv = iv;
        this.signature = signature;
        this.keyIndex = keyIndex;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.timestamp = timestamp;
    }

    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public int getKeyIndex() { return keyIndex; }
    public void setKeyIndex(int keyIndex) { this.keyIndex = keyIndex; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}