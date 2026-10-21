package com.example.safechat.ui.splash;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import com.example.safechat.R;
import com.example.safechat.data.repository.AuthRepository;
import com.example.safechat.ui.auth.DangNhapActivity;
import com.example.safechat.ui.home.ChungActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChoActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cho);

        progressBar = findViewById(R.id.progressBar);
        progressBar.setProgress(0);

        loadInitialData();
    }

    private void loadInitialData() {
        executor.execute(() -> {
            // Bước 1: Bắt đầu nạp cấu hình hệ thống (30%)
            updateProgressSmooth(30);

            AuthRepository repository = new AuthRepository(ChoActivity.this);

            // Bước 2: Kiểm tra phiên đăng nhập và khóa bảo mật (70%)
            updateProgressSmooth(70);
            boolean isLoggedIn = repository.isUserLoggedInAndUnlocked();

            // Bước 3: Hoàn tất khởi tạo (100%)
            updateProgressSmooth(100);

            // Chờ thanh animation chạy chạm mốc 100% rồi chuyển màn hình
            mainHandler.postDelayed(() -> {
                Intent intent;
                if (isLoggedIn) {
                    intent = new Intent(ChoActivity.this, ChungActivity.class);
                } else {
                    intent = new Intent(ChoActivity.this, DangNhapActivity.class);
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }, 300);
        });
    }

    /**
     * Tạo hiệu ứng lướt mượt mà giữa các mốc tiến trình
     */
    private void updateProgressSmooth(int targetProgress) {
        mainHandler.post(() -> {
            ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), targetProgress);
            animation.setDuration(250);
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();
        });

        try {
            // Khoảng nghỉ nhỏ để UI kịp vẽ hiệu ứng chuyển động
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}