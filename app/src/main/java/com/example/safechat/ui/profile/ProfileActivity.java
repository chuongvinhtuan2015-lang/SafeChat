
package com.example.safechat.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.storage.FirebaseStorage;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ProfileActivity extends AppCompatActivity {

    private ProfileViewModel viewModel;

    private TextView tvAvatarChar;
    private TextView tvUserSubInfo;
    private EditText etFullName;
    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private ImageView ivProfileAvatar;

    private com.google.android.material.button.MaterialButton btnSaveProfile;
    private com.google.android.material.button.MaterialButton btnChangePassword;

    private com.google.android.material.card.MaterialCardView btnLogout;
    private android.widget.FrameLayout btnChangeAvatar;

    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);

        initViews();
        initViewModel();
        initImagePicker();
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
    }

    private void initViewModel() {
        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @Override
            public <T extends ViewModel> T create(
                    Class<T> modelClass
            ) {
                if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
                    return modelClass.cast(
                            new ProfileViewModel(getApplicationContext())
                    );
                }

                throw new IllegalArgumentException(
                        "Không thể tạo ViewModel: " + modelClass.getName()
                );
            }
        };

        viewModel = new ViewModelProvider(this, factory)
                .get(ProfileViewModel.class);
    }

    private void initImagePicker() {
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleSelectedImage
        );
    }

    private void observeViewModel() {
        viewModel.getFullName().observe(this, name -> {
            String safeName = name == null ? "" : name;

            if (!etFullName.getText().toString().equals(safeName)) {
                etFullName.setText(safeName);
            }

            updateAvatarInitial(safeName);
        });

        viewModel.getEmail().observe(this, email -> {
            tvUserSubInfo.setText(email == null ? "" : email);
        });

        viewModel.getAvatarUrl().observe(this, this::loadAvatar);

        viewModel.getMessage().observe(this, message -> {
            if (message != null && !message.trim().isEmpty()) {
                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        viewModel.getLoading().observe(this, loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);

            btnSaveProfile.setEnabled(!isLoading);
            btnChangePassword.setEnabled(!isLoading);
            btnChangeAvatar.setEnabled(!isLoading);
        });
    }

    private void initListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnSaveProfile.setOnClickListener(v -> {
            String fullName = etFullName.getText()
                    .toString()
                    .trim();

            viewModel.updateFullName(fullName);
        });

        btnChangePassword.setOnClickListener(v -> changePassword());

        btnChangeAvatar.setOnClickListener(v ->
                imagePicker.launch("image/*")
        );

        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void changePassword() {
        String currentPassword = etCurrentPassword.getText()
                .toString();

        String newPassword = etNewPassword.getText()
                .toString();

        String confirmPassword = etConfirmNewPassword.getText()
                .toString();

        if (currentPassword.trim().isEmpty()
                || newPassword.isEmpty()
                || confirmPassword.isEmpty()) {
            Toast.makeText(
                    this,
                    "Vui lòng nhập đầy đủ các trường mật khẩu.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmNewPassword.setError(
                    "Mật khẩu xác nhận không khớp."
            );
            return;
        }

        viewModel.changePassword(currentPassword, newPassword);

        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmNewPassword.setText("");
    }

    private void handleSelectedImage(Uri uri) {
        if (uri == null) {
            return;
        }

        try (InputStream inputStream =
                     getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            if (inputStream == null) {
                Toast.makeText(
                        this,
                        "Không thể đọc ảnh đã chọn.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            byte[] buffer = new byte[8192];
            int count;
            int totalBytes = 0;
            int maxBytes = 5 * 1024 * 1024;

            while ((count = inputStream.read(buffer)) != -1) {
                totalBytes += count;

                if (totalBytes > maxBytes) {
                    Toast.makeText(
                            this,
                            "Ảnh quá lớn. Vui lòng chọn ảnh dưới 5 MB.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                outputStream.write(buffer, 0, count);
            }

            byte[] imageBytes = outputStream.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.length
            );

            if (bitmap == null) {
                Toast.makeText(
                        this,
                        "Tệp đã chọn không phải ảnh hợp lệ.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            ivProfileAvatar.setImageBitmap(bitmap);
            viewModel.uploadAvatar(imageBytes);

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Không thể đọc ảnh: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void loadAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            ivProfileAvatar.setImageDrawable(null);
            ivProfileAvatar.setVisibility(android.view.View.GONE);
            tvAvatarChar.setVisibility(android.view.View.VISIBLE);
            return;
        }

        FirebaseStorage.getInstance()
                .getReferenceFromUrl(avatarUrl)
                .getBytes(5L * 1024 * 1024)
                .addOnSuccessListener(bytes -> {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.length
                    );

                    if (bitmap != null) {
                        ivProfileAvatar.setImageBitmap(bitmap);
                        ivProfileAvatar.setVisibility(
                                android.view.View.VISIBLE
                        );
                        tvAvatarChar.setVisibility(
                                android.view.View.GONE
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    ivProfileAvatar.setVisibility(
                            android.view.View.GONE
                    );
                    tvAvatarChar.setVisibility(
                            android.view.View.VISIBLE
                    );
                });
    }

    private void updateAvatarInitial(String name) {
        if (name == null || name.trim().isEmpty()) {
            tvAvatarChar.setText("?");
            return;
        }

        String trimmedName = name.trim();
        tvAvatarChar.setText(
                trimmedName.substring(0, 1).toUpperCase()
        );
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi SafeChat?")
                .setNegativeButton("Hủy", (dialog, which) ->
                        dialog.dismiss())
                .setPositiveButton("Đăng xuất", (dialog, which) ->
                        performLogout())
                .show();
    }

    private void performLogout() {
        try {
            viewModel.logout();

            Intent intent = new Intent(
                    ProfileActivity.this,
                    DangNhapActivity.class
            );

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Không thể đăng xuất: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}
