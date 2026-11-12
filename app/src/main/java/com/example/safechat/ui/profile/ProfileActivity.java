package com.example.safechat.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.safechat.R;
import com.example.safechat.ui.auth.DangNhapActivity;
import com.example.safechat.utils.AvatarHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

public class ProfileActivity extends AppCompatActivity {

    private ProfileViewModel viewModel;

    // Các thành phần hiển thị thông tin
    private TextView tvAvatarChar;
    private TextView tvUserSubInfo;
    private EditText etFullName;
    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private ImageView ivProfileAvatar;

    // Các nút bấm tương tác
    private MaterialButton btnSaveProfile;
    private MaterialButton btnChangePassword;
    private MaterialCardView btnLogout;
    private FrameLayout btnChangeAvatar;

    // Nút đồng bộ trên Header và hiệu ứng xoay
    private FrameLayout btnSyncData;
    private ImageView ivSyncIcon;
    private RotateAnimation rotateAnim;

    // 1. Launcher chọn ảnh từ thiết bị
    private ActivityResultLauncher<String> imagePickerLauncher;
    // 2. Launcher nhận kết quả cắt ảnh tròn từ UCrop
    private ActivityResultLauncher<Intent> cropLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);

        initViews();
        initViewModel();
        initPickers();
        observeViewModel();
        initListeners();

        viewModel.loadProfile();
    }

    private void initViews() {
        tvAvatarChar = findViewById(R.id.tvAvatarChar);
        tvUserSubInfo = findViewById(R.id.tvUserSubInfo);
        etFullName = findViewById(R.id.etFullName);
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        ivProfileAvatar = findViewById(R.id.ivProfileAvatar);

        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnLogout = findViewById(R.id.btnLogout);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);

        // Ánh xạ nút đồng bộ ở Header
        btnSyncData = findViewById(R.id.btnSyncData);
        ivSyncIcon = findViewById(R.id.ivSyncIcon);

        // Khởi tạo animation xoay tròn 360 độ vô tận
        rotateAnim = new RotateAnimation(
                0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        rotateAnim.setDuration(700);
        rotateAnim.setRepeatCount(Animation.INFINITE);
    }

    private void initViewModel() {
        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @Override
            public <T extends ViewModel> T create(Class<T> modelClass) {
                if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
                    return modelClass.cast(new ProfileViewModel(getApplicationContext()));
                }
                throw new IllegalArgumentException("Không thể tạo ViewModel: " + modelClass.getName());
            }
        };

        viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
    }

    private void initPickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        startCrop(uri);
                    }
                }
        );

        cropLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri croppedUri = UCrop.getOutput(result.getData());
                        if (croppedUri != null) {
                            handleCroppedImage(croppedUri);
                        }
                    } else if (result.getResultCode() == UCrop.RESULT_ERROR && result.getData() != null) {
                        Throwable cropError = UCrop.getError(result.getData());
                        Toast.makeText(this, "Lỗi cắt ảnh: " + (cropError != null ? cropError.getMessage() : ""), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void startCrop(Uri sourceUri) {
        try {
            File destinationFile = new File(getCacheDir(), "cropped_avatar_" + System.currentTimeMillis() + ".jpg");
            Uri destinationUri = Uri.fromFile(destinationFile);

            UCrop.Options options = new UCrop.Options();
            options.setCircleDimmedLayer(true);
            options.setShowCropFrame(false);
            options.setShowCropGrid(false);
            options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
            options.setCompressionQuality(75);
            options.setHideBottomControls(false);

            UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                    .withAspectRatio(1, 1)
                    .withMaxResultSize(300, 300)
                    .withOptions(options);

            cropLauncher.launch(uCrop.getIntent(this));
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi mở cắt ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleCroppedImage(Uri croppedUri) {
        try (InputStream inputStream = getContentResolver().openInputStream(croppedUri)) {
            if (inputStream == null) {
                Toast.makeText(this, "Không thể đọc ảnh đã cắt.", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                Toast.makeText(this, "Ảnh không hợp lệ.", Toast.LENGTH_SHORT).show();
                return;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            ivProfileAvatar.setImageBitmap(bitmap);
            ivProfileAvatar.setVisibility(View.VISIBLE);
            tvAvatarChar.setVisibility(View.GONE);

            viewModel.uploadAvatar(imageBytes);

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void observeViewModel() {
        viewModel.getFullName().observe(this, name -> {
            String safeName = name == null ? "" : name;
            if (!etFullName.getText().toString().equals(safeName)) {
                etFullName.setText(safeName);
            }

            String currentUrl = viewModel.getAvatarUrl().getValue();
            AvatarHelper.loadAvatar(this, currentUrl, safeName, ivProfileAvatar, tvAvatarChar);
        });

        viewModel.getEmail().observe(this, email -> {
            tvUserSubInfo.setText(email == null ? "" : email);
        });

        viewModel.getAvatarUrl().observe(this, url -> {
            String currentName = etFullName.getText().toString().trim();
            AvatarHelper.loadAvatar(this, url, currentName, ivProfileAvatar, tvAvatarChar);
        });

        viewModel.getMessage().observe(this, message -> {
            if (message != null && !message.trim().isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getLoading().observe(this, loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            btnSaveProfile.setEnabled(!isLoading);
            btnChangePassword.setEnabled(!isLoading);
            btnChangeAvatar.setEnabled(!isLoading);
            ivProfileAvatar.setEnabled(!isLoading);
        });

        // 1. Điều khiển hiệu ứng xoay tròn và khóa nút khi đang đồng bộ
        viewModel.getSyncLoading().observe(this, isSyncing -> {
            boolean syncing = Boolean.TRUE.equals(isSyncing);
            if (btnSyncData != null) {
                btnSyncData.setEnabled(!syncing);
            }
            if (ivSyncIcon != null) {
                if (syncing) {
                    ivSyncIcon.startAnimation(rotateAnim);
                } else {
                    ivSyncIcon.clearAnimation();
                }
            }
        });

        // 2. Hiển thị thông báo phản hồi kết quả đồng bộ
        viewModel.getSyncMessage().observe(this, msg -> {
            if (msg != null && !msg.trim().isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Bắt sự kiện nhấn nút Đồng bộ
        if (btnSyncData != null) {
            btnSyncData.setOnClickListener(v -> viewModel.syncFriendsData());
        }

        btnSaveProfile.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            viewModel.updateFullName(fullName);
        });

        btnChangePassword.setOnClickListener(v -> changePassword());

        btnChangeAvatar.setOnClickListener(v -> openImageChooser());
        ivProfileAvatar.setOnClickListener(v -> openImageChooser());
        tvAvatarChar.setOnClickListener(v -> openImageChooser());

        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void openImageChooser() {
        try {
            imagePickerLauncher.launch("image/*");
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở bộ chọn ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void changePassword() {
        String currentPassword = etCurrentPassword.getText().toString();
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmNewPassword.getText().toString();

        if (currentPassword.trim().isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ các trường mật khẩu.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmNewPassword.setError("Mật khẩu xác nhận không khớp.");
            return;
        }

        viewModel.changePassword(currentPassword, newPassword);

        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmNewPassword.setText("");
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi SafeChat?")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Đăng xuất", (dialog, which) -> performLogout())
                .show();
    }

    private void performLogout() {
        try {
            viewModel.logout();

            Intent intent = new Intent(ProfileActivity.this, DangNhapActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Không thể đăng xuất: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}