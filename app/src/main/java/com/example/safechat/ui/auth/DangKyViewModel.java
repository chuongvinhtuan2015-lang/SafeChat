package com.example.safechat.ui.auth;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.safechat.data.repository.AuthRepository;

public class DangKyViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registerSuccess = new MutableLiveData<>(false);

    public DangKyViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getRegisterSuccess() { return registerSuccess; }

    public void register(String fullName, String email, String password, String confirmPassword,
                         String recoveryCode, String confirmRecoveryCode) {
        if (fullName.trim().isEmpty() || email.trim().isEmpty() || password.isEmpty() || recoveryCode.isEmpty()) {
            errorMessage.setValue("Vui lòng điền đầy đủ tất cả các trường!");
            return;
        }
        if (password.length() < 8) {
            errorMessage.setValue("Mật khẩu phải có độ dài từ 8 ký tự trở lên!");
            return;
        }
        if (!password.equals(confirmPassword)) {
            errorMessage.setValue("Mật khẩu xác nhận không khớp!");
            return;
        }
        if (!recoveryCode.equals(confirmRecoveryCode)) {
            errorMessage.setValue("Mã khôi phục xác nhận không khớp!");
            return;
        }

        isLoading.setValue(true);
        authRepository.register(fullName.trim(), email.trim(), password, recoveryCode.trim(), new AuthRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                isLoading.postValue(false);
                registerSuccess.postValue(true);
            }

            @Override
            public void onError(Exception e) {
                isLoading.postValue(false);
                errorMessage.postValue(e.getMessage());
            }
        });
    }
}