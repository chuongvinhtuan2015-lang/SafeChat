package com.example.safechat.ui.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safechat.R;
import com.example.safechat.data.repository.AuthRepository;
import com.example.safechat.ui.auth.DangNhapActivity;
import com.example.safechat.ui.home.ChungActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChoActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cho);

        checkAuthAndNavigate();
    }

    private void checkAuthAndNavigate() {
        executor.execute(() -> {
            AuthRepository repository = new AuthRepository(ChoActivity.this);
            boolean isLoggedIn = repository.isUserLoggedInAndUnlocked();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                Intent intent = new Intent(
                        ChoActivity.this,
                        isLoggedIn ? ChungActivity.class : DangNhapActivity.class
                );
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}