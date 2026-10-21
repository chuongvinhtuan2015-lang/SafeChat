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

public class DangKyActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword, etConfirmPassword, etRecoveryCode, etConfirmRecoveryCode;
    private Button btnRegister;
    private View btnBack;
    private TextView tvLogin;
    private ProgressBar progressBar;
    private DangKyViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dangky);

        initViews();
        viewModel = new ViewModelProvider(this).get(DangKyViewModel.class);
        observeViewModel();

        btnRegister.setOnClickListener(v -> viewModel.register(
                etFullName.getText().toString(),
                etEmail.getText().toString(),
                etPassword.getText().toString(),
                etConfirmPassword.getText().toString(),
                etRecoveryCode.getText().toString(),
                etConfirmRecoveryCode.getText().toString()
        ));

        tvLogin.setOnClickListener(v -> finish());
        btnBack.setOnClickListener(v -> finish());
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etRecoveryCode = findViewById(R.id.etRecoveryCode);
        etConfirmRecoveryCode = findViewById(R.id.etConfirmRecoveryCode);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        progressBar = findViewById(R.id.progressBar);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnRegister.setEnabled(!loading);
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(DangKyActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getRegisterSuccess().observe(this, success -> {
            if (success) {
                Intent intent = new Intent(DangKyActivity.this, ChungActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}