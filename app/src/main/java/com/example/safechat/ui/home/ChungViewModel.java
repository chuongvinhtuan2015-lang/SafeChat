package com.example.safechat.ui.home;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.safechat.data.local.prefs.LocalDataManager;

public class ChungViewModel extends AndroidViewModel {
    private final MutableLiveData<String> userFullName = new MutableLiveData<>();

    public ChungViewModel(@NonNull Application application) {
        super(application);
        LocalDataManager localDataManager = LocalDataManager.getInstance(application);
        userFullName.setValue(localDataManager.getFullName());
    }

    public LiveData<String> getUserFullName() {
        return userFullName;
    }
}