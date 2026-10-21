package com.example.safechat.utils;

public final class Constants {
    private Constants() {}

    // Firestore Collections
    public static final String COLLECTION_USERS = "users";

    // EncryptedSharedPreferences Keys
    public static final String PREF_NAME = "safechat_secure_prefs";
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";
    public static final String KEY_UID = "user_uid";
    public static final String KEY_EMAIL = "user_email";
    public static final String KEY_FULL_NAME = "user_full_name";
    public static final String KEY_X25519_PRIVATE = "x25519_private_key";
    public static final String KEY_ED25519_PRIVATE = "ed25519_private_key";
}