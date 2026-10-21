package com.example.safechat.data.models;

public class User {
    private String uid;
    private String fullName;
    private String email;
    private String publicKeyX25519;
    private String publicKeyEd25519;
    private String encryptedKeyBundle;
    private long createdAt;

    public User() {
        // Constructor rỗng bắt buộc cho Firestore Deserialization
    }

    public User(String uid, String fullName, String email, String publicKeyX25519,
                String publicKeyEd25519, String encryptedKeyBundle, long createdAt) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.publicKeyX25519 = publicKeyX25519;
        this.publicKeyEd25519 = publicKeyEd25519;
        this.encryptedKeyBundle = encryptedKeyBundle;
        this.createdAt = createdAt;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPublicKeyX25519() { return publicKeyX25519; }
    public void setPublicKeyX25519(String publicKeyX25519) { this.publicKeyX25519 = publicKeyX25519; }

    public String getPublicKeyEd25519() { return publicKeyEd25519; }
    public void setPublicKeyEd25519(String publicKeyEd25519) { this.publicKeyEd25519 = publicKeyEd25519; }

    public String getEncryptedKeyBundle() { return encryptedKeyBundle; }
    public void setEncryptedKeyBundle(String encryptedKeyBundle) { this.encryptedKeyBundle = encryptedKeyBundle; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}