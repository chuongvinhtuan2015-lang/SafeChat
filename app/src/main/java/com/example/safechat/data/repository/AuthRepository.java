package com.example.safechat.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.safechat.crypto.CryptoManager;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.models.User;
import com.example.safechat.data.remote.FirebaseAuthSource;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.google.firebase.auth.FirebaseUser;

import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private final FirebaseAuthSource authSource;
    private final FirestoreDataSource firestoreSource;
    private final LocalDataManager localDataManager;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public AuthRepository(Context context) {
        this.authSource = new FirebaseAuthSource();
        this.firestoreSource = new FirestoreDataSource();
        this.localDataManager = LocalDataManager.getInstance(context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Kiểm tra trạng thái: Người dùng đã đăng nhập Firebase và Private Keys đã được nạp hợp lệ vào máy
     */
    public boolean isUserLoggedInAndUnlocked() {
        return authSource.getCurrentUser() != null &&
                localDataManager.isUserLoggedIn() &&
                localDataManager.hasValidKeyLoaded();
    }

    /**
     * Đăng ký tài khoản Zero-Knowledge:
     * 1. Tạo tài khoản trên Firebase Auth.
     * 2. Chuyển sang luồng phụ (Executor) để sinh cặp khóa X25519 & Ed25519.
     * 3. Dùng mã khôi phục để đóng gói 2 khóa bí mật bằng AES-256-GCM.
     * 4. Đẩy Public Keys và Gói khóa bí mật lên Firestore users/{uid}.
     * 5. Lưu phiên làm việc và Private Keys vào EncryptedSharedPreferences.
     */
    public void register(String fullName, String email, String password, String recoveryCode, Callback<Void> callback) {
        authSource.createUser(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        notifyError(callback, new Exception("Không thể khởi tạo thông tin người dùng trên Firebase Auth."));
                        return;
                    }

                    String uid = firebaseUser.getUid();

                    executorService.execute(() -> {
                        try {
                            // 1. Sinh các cặp khóa mật mã
                            KeyPair x25519Pair = CryptoManager.generateX25519KeyPair();
                            KeyPair ed25519Pair = CryptoManager.generateEd25519KeyPair();

                            byte[] x25519Priv = x25519Pair.getPrivate().getEncoded();
                            byte[] ed25519Priv = ed25519Pair.getPrivate().getEncoded();

                            // 2. Đóng gói Private Keys với Recovery Code
                            String encryptedBundle = CryptoManager.encryptKeyBundle(x25519Priv, ed25519Priv, recoveryCode);

                            // 3. Chuẩn bị User Model để lưu lên Firestore
                            User user = new User(
                                    uid,
                                    fullName,
                                    email,
                                    CryptoManager.toBase64(x25519Pair.getPublic().getEncoded()),
                                    CryptoManager.toBase64(ed25519Pair.getPublic().getEncoded()),
                                    encryptedBundle,
                                    System.currentTimeMillis()
                            );

                            firestoreSource.saveUser(user)
                                    .addOnSuccessListener(aVoid -> {
                                        // 4. Lưu session & khóa riêng tư xuống bộ nhớ cục bộ
                                        localDataManager.saveSession(uid, email, fullName, x25519Priv, ed25519Priv);
                                        notifySuccess(callback, null);
                                    })
                                    .addOnFailureListener(e -> notifyError(callback, e));

                        } catch (Exception e) {
                            notifyError(callback, e);
                        }
                    });
                })
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    /**
     * Đăng nhập Zero-Knowledge:
     * 1. Xác thực tài khoản với Firebase Auth.
     * 2. Lấy document người dùng từ Firestore users/{uid}.
     * 3. Giải mã EncryptedKeyBundle bằng mã khôi phục trên luồng phụ.
     * 4. Bóc tách Private Keys và lưu vào EncryptedSharedPreferences.
     */
    public void login(String email, String password, String recoveryCode, Callback<Void> callback) {
        authSource.signIn(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        notifyError(callback, new Exception("Xác thực người dùng thất bại."));
                        return;
                    }

                    String uid = firebaseUser.getUid();

                    firestoreSource.getUser(uid)
                            .addOnSuccessListener(documentSnapshot -> {
                                if (!documentSnapshot.exists()) {
                                    notifyError(callback, new Exception("Hồ sơ bảo mật của tài khoản không tồn tại trên hệ thống!"));
                                    return;
                                }

                                User user = documentSnapshot.toObject(User.class);
                                if (user == null || user.getEncryptedKeyBundle() == null) {
                                    notifyError(callback, new Exception("Gói khóa bảo mật của tài khoản bị hỏng hoặc không hợp lệ!"));
                                    return;
                                }

                                executorService.execute(() -> {
                                    try {
                                        // Thử giải mã Private Keys bằng Recovery Code
                                        CryptoManager.DecryptedPrivateKeys decryptedKeys =
                                                CryptoManager.decryptKeyBundle(user.getEncryptedKeyBundle(), recoveryCode);

                                        // Lưu session khi giải mã thành công
                                        localDataManager.saveSession(
                                                uid,
                                                user.getEmail(),
                                                user.getFullName(),
                                                decryptedKeys.x25519PrivateKey,
                                                decryptedKeys.ed25519PrivateKey
                                        );

                                        notifySuccess(callback, null);
                                    } catch (Exception e) {
                                        notifyError(callback, new Exception("Mã khôi phục không chính xác! Không thể mở khóa dữ liệu bảo mật."));
                                    }
                                });
                            })
                            .addOnFailureListener(e -> notifyError(callback, e));
                })
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    /**
     * Đăng xuất an toàn: Xóa hoàn toàn Private Keys khỏi bộ nhớ và đăng xuất Firebase
     */
    public void logout() {
        localDataManager.clearSession();
        authSource.signOut();
    }

    private <T> void notifySuccess(Callback<T> callback, T result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(result));
        }
    }

    private <T> void notifyError(Callback<T> callback, Exception e) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(e));
        }
    }
}