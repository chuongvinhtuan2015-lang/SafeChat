package com.example.safechat.data.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.remote.FirebaseAuthSource;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.repository.AuthRepository;
public class ProfileRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private final LocalDataManager localDataManager;
    private final FirestoreDataSource firestoreSource;
    private final FirebaseAuthSource authSource;
    private final Handler mainHandler;
    private final AuthRepository authRepository;
    public ProfileRepository(Context context) {
        this.localDataManager = LocalDataManager.getInstance(context);
        this.firestoreSource = new FirestoreDataSource();
        this.authSource = new FirebaseAuthSource();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.authRepository = new AuthRepository(context);
    }

    public String getFullName() {
        return localDataManager.getFullName();
    }

    public String getEmail() {
        return localDataManager.getEmail();
    }

    public String getAvatarUrl() {
        return localDataManager.getAvatarUrl();
    }

    public void updateFullName(String fullName, Callback<Void> callback) {
        String uid = localDataManager.getUid();

        if (uid == null || uid.trim().isEmpty()) {
            notifyError(callback, new Exception("Không tìm thấy thông tin tài khoản."));
            return;
        }

        firestoreSource.updateFullName(uid, fullName)
                .addOnSuccessListener(unused -> {
                    localDataManager.setFullName(fullName);
                    notifySuccess(callback, null);
                })
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    public void uploadAvatar(byte[] imageBytes, Callback<String> callback) {
        String uid = localDataManager.getUid();

        if (uid == null || uid.trim().isEmpty()) {
            notifyError(callback, new Exception("Không tìm thấy thông tin tài khoản."));
            return;
        }

        if (imageBytes == null || imageBytes.length == 0) {
            notifyError(callback, new Exception("Ảnh đại diện không hợp lệ."));
            return;
        }

        // Chuyển mảng byte ảnh thành chuỗi Data URI Base64
        String base64String = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
        String avatarDataUri = "data:image/jpeg;base64," + base64String;

        // Lưu trực tiếp chuỗi này lên Firestore (field avatarUrl trong document users/{uid})
        firestoreSource.updateAvatarUrl(uid, avatarDataUri)
                .addOnSuccessListener(unused -> {
                    // Lưu vào LocalDataManager để hiển thị offline
                    localDataManager.setAvatarUrl(avatarDataUri);
                    notifySuccess(callback, avatarDataUri);
                })
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    private void saveAvatarUrl(String uid, Uri uri, Callback<String> callback) {
        if (uri == null) {
            notifyError(callback, new Exception("Không thể lấy đường dẫn ảnh tải lên."));
            return;
        }

        String avatarUrl = uri.toString();

        firestoreSource.updateAvatarUrl(uid, avatarUrl)
                .addOnSuccessListener(unused -> {
                    localDataManager.setAvatarUrl(avatarUrl);
                    notifySuccess(callback, avatarUrl);
                })
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    public void changePassword(String currentPassword, String newPassword, Callback<Void> callback) {
        if (currentPassword == null || currentPassword.isEmpty()
                || newPassword == null || newPassword.isEmpty()) {
            notifyError(callback, new Exception("Vui lòng nhập đầy đủ mật khẩu."));
            return;
        }

        authSource.reauthenticate(currentPassword)
                .addOnSuccessListener(unused ->
                        authSource.updatePassword(newPassword)
                                .addOnSuccessListener(result -> notifySuccess(callback, null))
                                .addOnFailureListener(e -> notifyError(callback, e)))
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    public void logout() {
        authRepository.logout();
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