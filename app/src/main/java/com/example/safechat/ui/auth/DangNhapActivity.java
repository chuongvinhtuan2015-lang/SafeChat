package com.example.safechat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.safechat.R;
import com.example.safechat.ui.home.ChungActivity;

public class DangNhapActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etRecoveryCode;
    private Button btnLogin;
    private TextView tvForgotPassword, tvRegister;
    private ProgressBar progressBar;
    private DangNhapViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dangnhap);

        initViews();
        viewModel = new ViewModelProvider(this).get(DangNhapViewModel.class);
        observeViewModel();

        btnLogin.setOnClickListener(v -> viewModel.login(
                etEmail.getText().toString(),
                etPassword.getText().toString(),
                etRecoveryCode.getText().toString()
        ));

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(DangNhapActivity.this, DangKyActivity.class);
            startActivity(intent);
        });

        tvForgotPassword.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng khôi phục mật khẩu đang được phát triển", Toast.LENGTH_SHORT).show()
        );
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etRecoveryCode = findViewById(R.id.etRecoveryCode);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnLogin.setEnabled(!loading);
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(DangNhapActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getLoginSuccess().observe(this, success -> {
            if (success) {
                Intent intent = new Intent(DangNhapActivity.this, ChungActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}