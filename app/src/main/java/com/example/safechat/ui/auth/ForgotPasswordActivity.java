package com.example.safechat.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safechat.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private MaterialCardView btnBack;
    private TextInputEditText etEmail;
    private MaterialButton btnSendReset;
    private LinearLayout btnBackToLogin;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forgot_password);

        auth = FirebaseAuth.getInstance();

        btnBack = findViewById(R.id.btnBack);
        etEmail = findViewById(R.id.etEmail);
        btnSendReset = findViewById(R.id.btnSendReset);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);


        btnBack.setOnClickListener(v -> finish());

        // Nút "Quay lại đăng nhập" phía dưới
        btnBackToLogin.setOnClickListener(v -> finish());

        // Nút gửi liên kết
        btnSendReset.setOnClickListener(v -> sendPasswordReset());

    }

    private void sendPasswordReset() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Vui lòng nhập địa chỉ email");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Địa chỉ email không đúng định dạng");
            etEmail.requestFocus();
            return;
        }

        btnSendReset.setEnabled(false);
        btnSendReset.setText("Đang gửi...");

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    btnSendReset.setEnabled(true);
                    btnSendReset.setText("Gửi liên kết khôi phục");

                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Đã gửi liên kết khôi phục vào hòm thư của bạn!", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        String errorMessage = "Không thể gửi email khôi phục";
                        if (task.getException() != null && task.getException().getMessage() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(this, "Lỗi: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}