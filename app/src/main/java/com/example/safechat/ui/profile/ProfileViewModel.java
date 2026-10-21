
package com.example.safechat.ui.profile;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.safechat.data.repository.ProfileRepository;

public class ProfileViewModel extends ViewModel {

    private final ProfileRepository repository;

    private final MutableLiveData<String> fullName = new MutableLiveData<>();
    private final MutableLiveData<String> email = new MutableLiveData<>();
    private final MutableLiveData<String> avatarUrl = new MutableLiveData<>();

    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public ProfileViewModel(Context context) {
        repository = new ProfileRepository(context.getApplicationContext());
        loadProfile();
    }

    public LiveData<String> getFullName() {
        return fullName;
    }

    public LiveData<String> getEmail() {
        return email;
    }

    public LiveData<String> getAvatarUrl() {
        return avatarUrl;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
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
