package com.example.safechat.ui.auth;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.safechat.data.repository.AuthRepository;

public class DangNhapViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loginSuccess = new MutableLiveData<>(false);

    public DangNhapViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getLoginSuccess() { return loginSuccess; }

    public void login(String email, String password, String recoveryCode) {
        if (email.trim().isEmpty() || password.isEmpty() || recoveryCode.trim().isEmpty()) {
            errorMessage.setValue("Vui lòng điền Email, Mật khẩu và Mã khôi phục!");
            return;
        }

        isLoading.setValue(true);
        authRepository.login(email.trim(), password, recoveryCode.trim(), new AuthRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                isLoading.postValue(false);
                loginSuccess.postValue(true);
            }

            @Override
            public void onError(Exception e) {
                isLoading.postValue(false);
                errorMessage.postValue(e.getMessage());
            }
        });
    }
}