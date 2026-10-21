
package com.example.safechat.data.repository;

import android.content.Context;
import android.net.Uri;

import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.remote.FirebaseStorageSource;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.remote.FirebaseAuthSource;

public class ProfileRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private final LocalDataManager localDataManager;
    private final FirestoreDataSource firestoreSource;
    private final FirebaseStorageSource storageSource;
    private final FirebaseAuthSource authSource;

    public ProfileRepository(Context context) {
        localDataManager = LocalDataManager.getInstance(context);
        firestoreSource = new FirestoreDataSource();
        storageSource = new FirebaseStorageSource();
        authSource = new FirebaseAuthSource();
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
            notifyError(callback,
                    new Exception("Không tìm thấy thông tin tài khoản."));
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
            notifyError(callback,
                    new Exception("Không tìm thấy thông tin tài khoản."));
            return;
        }

        if (imageBytes == null || imageBytes.length == 0) {
            notifyError(callback,
                    new Exception("Ảnh đại diện không hợp lệ."));
            return;
        }

        storageSource.uploadAvatar(uid, imageBytes)
                .addOnSuccessListener(uri ->
                        saveAvatarUrl(uid, uri, callback))
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    private void saveAvatarUrl(
            String uid,
            Uri uri,
            Callback<String> callback
    ) {
        String avatarUrl = uri.toString();

        firestoreSource.updateAvatarUrl(uid, avatarUrl)
                .addOnSuccessListener(unused -> {
                    localDataManager.setAvatarUrl(avatarUrl);
                    notifySuccess(callback, avatarUrl);
                })
                .addOnFailureListener(e -> notifyError(callback, e));
    }

    private <T> void notifySuccess(Callback<T> callback, T result) {
        if (callback != null) {
            callback.onSuccess(result);
        }
    }

    private <T> void notifyError(Callback<T> callback, Exception e) {
        if (callback != null) {
            callback.onError(e);
        }
    }
    public void changePassword(
            String currentPassword,
            String newPassword,
            Callback<Void> callback
    ) {
        if (currentPassword == null || currentPassword.isEmpty()
                || newPassword == null || newPassword.isEmpty()) {
            notifyError(callback,
                    new Exception("Vui lòng nhập đầy đủ mật khẩu."));
            return;
        }

        authSource.reauthenticate(currentPassword)
                .addOnSuccessListener(unused ->
                        authSource.updatePassword(newPassword)
                                .addOnSuccessListener(result ->
                                        notifySuccess(callback, null))
                                .addOnFailureListener(e ->
                                        notifyError(callback, e)))
                .addOnFailureListener(e ->
                        notifyError(callback, e));
    }

    public void logout() {
        authSource.signOut();
        localDataManager.clearSession();
    }
}
