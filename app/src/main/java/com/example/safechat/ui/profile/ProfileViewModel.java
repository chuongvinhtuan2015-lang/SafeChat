package com.example.safechat.ui.profile;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.safechat.data.local.db.AppDatabase;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.remote.FriendRemoteDataSource;
import com.example.safechat.data.repository.FriendRepository;
import com.example.safechat.data.repository.ProfileRepository;

public class ProfileViewModel extends ViewModel {

    private final ProfileRepository repository;
    private final FriendRepository friendRepository; // Thêm FriendRepository
    private final LocalDataManager localDataManager;

    private final MutableLiveData<String> fullName = new MutableLiveData<>();
    private final MutableLiveData<String> email = new MutableLiveData<>();
    private final MutableLiveData<String> avatarUrl = new MutableLiveData<>();

    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    // LiveData riêng cho trạng thái nút Đồng bộ
    private final MutableLiveData<Boolean> syncLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> syncMessage = new MutableLiveData<>();

    public ProfileViewModel(Context context) {
        Context appContext = context.getApplicationContext();
        repository = new ProfileRepository(appContext);
        localDataManager = LocalDataManager.getInstance(appContext);

        // Khởi tạo FriendRepository kết nối với ConversationDao (Room)
        AppDatabase db = AppDatabase.getInstance(appContext);
        FirestoreDataSource firestoreDataSource = new FirestoreDataSource();
        friendRepository = new FriendRepository(
                new FriendRemoteDataSource(),
                firestoreDataSource,
                db.conversationDao(),
                localDataManager
        );

        loadProfile();
    }

    public LiveData<String> getFullName() { return fullName; }
    public LiveData<String> getEmail() { return email; }
    public LiveData<String> getAvatarUrl() { return avatarUrl; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getLoading() { return loading; }

    // Getter cho trạng thái đồng bộ
    public LiveData<Boolean> getSyncLoading() { return syncLoading; }
    public LiveData<String> getSyncMessage() { return syncMessage; }

    /**
     * Kích hoạt đồng bộ thông tin bạn bè và cập nhật bảng conversations
     */
    public void syncFriendsData() {
        if (Boolean.TRUE.equals(syncLoading.getValue())) return;

        String currentUid = localDataManager.getUid();
        if (currentUid == null || currentUid.trim().isEmpty()) {
            syncMessage.setValue("Không tìm thấy thông tin đăng nhập.");
            return;
        }

        syncLoading.setValue(true);

        friendRepository.syncFriendsFromRemote(currentUid, new FriendRepository.SyncCallback() {
            @Override
            public void onSuccess(int updatedCount) {
                syncLoading.postValue(false);
                syncMessage.postValue("Đồng bộ thành công " + updatedCount + " cuộc trò chuyện.");
            }

            @Override
            public void onFailure(String errorMessage) {
                syncLoading.postValue(false);
                syncMessage.postValue("Lỗi đồng bộ: " + errorMessage);
            }
        });
    }

    public void loadProfile() {
        fullName.setValue(repository.getFullName());
        email.setValue(repository.getEmail());
        avatarUrl.setValue(repository.getAvatarUrl());
    }

    public void updateFullName(String name) {
        String normalizedName = name == null ? "" : name.trim();

        if (normalizedName.isEmpty()) {
            message.setValue("Vui lòng nhập họ và tên.");
            return;
        }

        loading.setValue(true);

        repository.updateFullName(
                normalizedName,
                new ProfileRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        fullName.setValue(normalizedName);
                        message.setValue("Cập nhật họ tên thành công.");
                        loading.setValue(false);
                    }

                    @Override
                    public void onError(Exception e) {
                        message.setValue(
                                e.getMessage() != null
                                        ? e.getMessage()
                                        : "Không thể cập nhật họ tên."
                        );
                        loading.setValue(false);
                    }
                }
        );
    }

    public void uploadAvatar(byte[] imageBytes) {
        loading.setValue(true);

        repository.uploadAvatar(
                imageBytes,
                new ProfileRepository.Callback<String>() {
                    @Override
                    public void onSuccess(String url) {
                        avatarUrl.setValue(url);
                        message.setValue("Cập nhật ảnh đại diện thành công.");
                        loading.setValue(false);
                    }

                    @Override
                    public void onError(Exception e) {
                        message.setValue(
                                e.getMessage() != null
                                        ? e.getMessage()
                                        : "Không thể cập nhật ảnh đại diện."
                        );
                        loading.setValue(false);
                    }
                }
        );
    }

    public void changePassword(String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.trim().isEmpty()
                || newPassword == null || newPassword.isEmpty()) {
            message.setValue("Vui lòng nhập đầy đủ mật khẩu.");
            return;
        }

        if (newPassword.length() < 6) {
            message.setValue("Mật khẩu mới phải có ít nhất 6 ký tự.");
            return;
        }

        if (currentPassword.equals(newPassword)) {
            message.setValue("Mật khẩu mới phải khác mật khẩu hiện tại.");
            return;
        }

        loading.setValue(true);

        repository.changePassword(
                currentPassword,
                newPassword,
                new ProfileRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        message.setValue("Đổi mật khẩu thành công.");
                        loading.setValue(false);
                    }

                    @Override
                    public void onError(Exception e) {
                        message.setValue(
                                e.getMessage() != null
                                        ? e.getMessage()
                                        : "Không thể đổi mật khẩu."
                        );
                        loading.setValue(false);
                    }
                }
        );
    }

    public void logout() {
        repository.logout();
    }
}