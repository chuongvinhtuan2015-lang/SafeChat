package com.example.safechat.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.safechat.crypto.CryptoManager;
import com.example.safechat.crypto.RatchetManager;
import com.example.safechat.data.local.db.AppDatabase;
import com.example.safechat.utils.Constants;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalDataManager {

    private static volatile LocalDataManager instance;
    private final SharedPreferences securePrefs;
    private final Context appContext;
    private final ExecutorService diskExecutor;

    private LocalDataManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.diskExecutor = Executors.newSingleThreadExecutor();

        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            securePrefs = EncryptedSharedPreferences.create(
                    appContext,
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
     * Lưu trữ phiên làm việc và Private Keys trên thiết bị
     */
    public void saveSession(String uid, String email, String fullName, String avatarUrl, byte[] x25519Priv, byte[] ed25519Priv) {
        SharedPreferences.Editor editor = securePrefs.edit()
                .putBoolean(Constants.KEY_IS_LOGGED_IN, true)
                .putString(Constants.KEY_UID, uid)
                .putString(Constants.KEY_EMAIL, email)
                .putString(Constants.KEY_FULL_NAME, fullName)
                .putString(Constants.KEY_X25519_PRIVATE, CryptoManager.toBase64(x25519Priv))
                .putString(Constants.KEY_ED25519_PRIVATE, CryptoManager.toBase64(ed25519Priv));

        // Tránh lỗi NullPointerException khi avatarUrl là null (user mới đăng ký)
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            editor.putString(Constants.KEY_AVATAR_URL, avatarUrl.trim());
        } else {
            editor.remove(Constants.KEY_AVATAR_URL);
        }

        editor.apply();
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
        SharedPreferences.Editor editor = securePrefs.edit();
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            editor.putString(Constants.KEY_AVATAR_URL, avatarUrl.trim());
        } else {
            editor.remove(Constants.KEY_AVATAR_URL);
        }
        editor.apply();
    }

    // =========================================================================
    // KHÓA RIÊNG TƯ (PRIVATE KEYS)
    // =========================================================================

    public byte[] getX25519PrivateKey() {
        String base64 = securePrefs.getString(Constants.KEY_X25519_PRIVATE, null);
        return base64 != null ? CryptoManager.fromBase64(base64) : null;
    }

    public byte[] getEd25519PrivateKey() {
        String base64 = securePrefs.getString(Constants.KEY_ED25519_PRIVATE, null);
        return base64 != null ? CryptoManager.fromBase64(base64) : null;
    }

    // =========================================================================
    // KHÓA CÔNG KHAI (PUBLIC KEYS - TỰ ĐỘNG SUY DẪN TỪ PRIVATE KEY CỦA CHÍNH MÌNH)
    // =========================================================================

    public byte[] getEd25519PublicKeyBytes() {
        byte[] privBytes = getEd25519PrivateKey();
        if (privBytes == null) return null;
        try {
            Ed25519PrivateKeyParameters privParams = RatchetManager.parseEd25519PrivateKey(privBytes);
            return privParams.generatePublicKey().getEncoded();
        } catch (Exception e) {
            return null;
        }
    }

    public String getEd25519PublicKey() {
        byte[] pubBytes = getEd25519PublicKeyBytes();
        return pubBytes != null ? CryptoManager.toBase64(pubBytes) : null;
    }

    public byte[] getX25519PublicKeyBytes() {
        byte[] privBytes = getX25519PrivateKey();
        if (privBytes == null) return null;
        try {
            byte[] seed = privBytes;
            if (privBytes.length == 48 || privBytes.length == 83) {
                seed = Arrays.copyOfRange(privBytes, 16, 48);
            }
            if (seed.length == 32) {
                X25519PrivateKeyParameters x25519Params = new X25519PrivateKeyParameters(seed, 0);
                return x25519Params.generatePublicKey().getEncoded();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getX25519PublicKey() {
        byte[] pubBytes = getX25519PublicKeyBytes();
        return pubBytes != null ? CryptoManager.toBase64(pubBytes) : null;
    }

    // =========================================================================
    // LƯU VÀ ĐỌC KHÓA BÍ MẬT DÙNG DỰ ÁN SAFECHAT
    // =========================================================================

    public void saveEncryptedSecret(String key, String secretValue) {
        securePrefs.edit().putString(key, secretValue).apply();
    }

    public String getEncryptedSecret(String key) {
        return securePrefs.getString(key, null);
    }

    public void removeEncryptedSecret(String keyAlias) {
        if (keyAlias != null && !keyAlias.trim().isEmpty()) {
            securePrefs.edit().remove(keyAlias).apply();
        }
    }

    public boolean hasEncryptedSecret(String keyAlias) {
        return keyAlias != null && securePrefs.contains(keyAlias);
    }

    // =========================================================================
    // XÓA TOÀN BỘ DỮ LIỆU LOCAL (ĐĂNG XUẤT)
    // =========================================================================

    public void clearSession() {
        clearSession(null);
    }

    public void clearSession(Runnable onComplete) {
        // Đẩy toàn bộ quá trình xóa prefs và Room DB sang background thread
        diskExecutor.execute(() -> {
            try {
                // 1. Xóa sạch EncryptedSharedPreferences không block main thread
                securePrefs.edit().clear().commit();

                // 2. Xóa sạch dữ liệu Room DB
                AppDatabase.getInstance(appContext).clearAllTables();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Đồng bộ an toàn khi reset instance
                synchronized (LocalDataManager.class) {
                    instance = null;
                }

                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        });
    }
}