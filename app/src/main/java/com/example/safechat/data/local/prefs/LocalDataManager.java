package com.example.safechat.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.safechat.crypto.CryptoManager;
import com.example.safechat.utils.Constants;

public class LocalDataManager {

    private static volatile LocalDataManager instance;
    private final SharedPreferences securePrefs;

    private LocalDataManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            securePrefs = EncryptedSharedPreferences.create(
                    context.getApplicationContext(),
                    Constants.PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            throw new RuntimeException("Không thể khởi tạo EncryptedSharedPreferences", e);
        }
    }

    public static LocalDataManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LocalDataManager.class) {
                if (instance == null) {
                    instance = new LocalDataManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Lưu trữ phiên làm việc và Private Keys tạm thời trên thiết bị
     */
    public void saveSession(String uid, String email, String fullName, byte[] x25519Priv, byte[] ed25519Priv) {
        securePrefs.edit()
                .putBoolean(Constants.KEY_IS_LOGGED_IN, true)
                .putString(Constants.KEY_UID, uid)
                .putString(Constants.KEY_EMAIL, email)
                .putString(Constants.KEY_FULL_NAME, fullName)
                .putString(Constants.KEY_X25519_PRIVATE, CryptoManager.toBase64(x25519Priv))
                .putString(Constants.KEY_ED25519_PRIVATE, CryptoManager.toBase64(ed25519Priv))
                .apply();
    }

    public boolean isUserLoggedIn() {
        return securePrefs.getBoolean(Constants.KEY_IS_LOGGED_IN, false);
    }

    public boolean hasValidKeyLoaded() {
        return securePrefs.contains(Constants.KEY_X25519_PRIVATE) &&
                securePrefs.contains(Constants.KEY_ED25519_PRIVATE);
    }

    public String getUid() {
        return securePrefs.getString(Constants.KEY_UID, null);
    }

    public String getEmail() {
        return securePrefs.getString(Constants.KEY_EMAIL, null);
    }

    public String getFullName() {
        return securePrefs.getString(Constants.KEY_FULL_NAME, "");
    }

    public void setFullName(String fullName) {
        securePrefs.edit()
                .putString(Constants.KEY_FULL_NAME, fullName)
                .apply();
    }

    public String getAvatarUrl() {
        return securePrefs.getString(Constants.KEY_AVATAR_URL, null);
    }

    public void setAvatarUrl(String avatarUrl) {
        securePrefs.edit()
                .putString(Constants.KEY_AVATAR_URL, avatarUrl)
                .apply();
    }

    public byte[] getX25519PrivateKey() {
        String base64 = securePrefs.getString(Constants.KEY_X25519_PRIVATE, null);
        return base64 != null ? CryptoManager.fromBase64(base64) : null;
    }

    public byte[] getEd25519PrivateKey() {
        String base64 = securePrefs.getString(Constants.KEY_ED25519_PRIVATE, null);
        return base64 != null ? CryptoManager.fromBase64(base64) : null;
    }
    // LƯU VÀ ĐỌC KHÓA BÍ MẬT DÙNG DỰ ÁN SAFECHAT
    public void saveEncryptedSecret(String key, String secretValue) {
        securePrefs.edit().putString(key, secretValue).apply();
    }

    public String getEncryptedSecret(String key) {
        return securePrefs.getString(key, null);
    }

    /**
     * Xóa sạch Private Keys và thông tin phiên khi đăng xuất
     */
    public void clearSession() {
        securePrefs.edit().clear().apply();
    }
}