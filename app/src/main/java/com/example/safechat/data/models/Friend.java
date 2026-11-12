package com.example.safechat.data.models;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Friend {
    private String uid;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String publicKeyX25519;
    private String publicKeyEd25519;
    private boolean isKeyExchanged = false;
    private String sharedKeyAlias;
    private String status = "ACCEPTED"; // "ACCEPTED" hoặc "BLOCKED"
    private String blockedBy;

    public Friend() {}

    public Friend(String uid, String fullName, String email, String avatarUrl,
                  String publicKeyX25519, String publicKeyEd25519,
                  boolean isKeyExchanged, String sharedKeyAlias) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.publicKeyX25519 = publicKeyX25519;
        this.publicKeyEd25519 = publicKeyEd25519;
        this.isKeyExchanged = isKeyExchanged;
        this.sharedKeyAlias = sharedKeyAlias;
        this.status = "ACCEPTED";
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getPublicKeyX25519() { return publicKeyX25519; }
    public void setPublicKeyX25519(String publicKeyX25519) { this.publicKeyX25519 = publicKeyX25519; }

    public String getPublicKeyEd25519() { return publicKeyEd25519; }
    public void setPublicKeyEd25519(String publicKeyEd25519) { this.publicKeyEd25519 = publicKeyEd25519; }

    public boolean isKeyExchanged() { return isKeyExchanged; }
    public void setKeyExchanged(boolean keyExchanged) { isKeyExchanged = keyExchanged; }

    public String getSharedKeyAlias() { return sharedKeyAlias; }
    public void setSharedKeyAlias(String sharedKeyAlias) { this.sharedKeyAlias = sharedKeyAlias; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBlockedBy() { return blockedBy; }
    public void setBlockedBy(String blockedBy) { this.blockedBy = blockedBy; }

    @Exclude
    public String getName() {
        return fullName != null ? fullName : "";
    }

    public void setName(String name) {
        if (this.fullName == null || this.fullName.isEmpty()) {
            this.fullName = name;
        }
    }

    @Exclude
    public String getUserId() {
        return uid;
    }

    public void setUserId(String userId) {
        if (this.uid == null || this.uid.isEmpty()) {
            this.uid = userId;
        }
    }
}