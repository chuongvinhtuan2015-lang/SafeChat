package com.example.safechat.data.models;

public class Friend {
    private String userId;
    private String name;
    private String email;
    private String avatarUrl;
    private boolean isKeyExchanged = false;
    private String sharedKeyAlias;//định danh khóa chung lưu cục bộ
    public Friend(){

    }
    public Friend(String userId, String name, String email, String avatarUrl, boolean isKeyExchanged, String sharedKeyAlias){
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.isKeyExchanged = isKeyExchanged;
        this.sharedKeyAlias = sharedKeyAlias;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isKeyExchanged() {
        return isKeyExchanged;
    }

    public void setKeyExchanged(boolean keyExchanged) {
        isKeyExchanged = keyExchanged;
    }

    public String getSharedKeyAlias() {
        return sharedKeyAlias;
    }

    public void setSharedKeyAlias(String sharedKeyAlias) {
        this.sharedKeyAlias = sharedKeyAlias;
    }
}
