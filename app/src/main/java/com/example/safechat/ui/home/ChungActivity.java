package com.example.safechat.ui.home;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.safechat.R;

public class ChungActivity extends AppCompatActivity {

    private TextView tvAdminName;
    private ChungViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chung);

        tvAdminName = findViewById(R.id.tvAdminName);
        viewModel = new ViewModelProvider(this).get(ChungViewModel.class);

        viewModel.getUserFullName().observe(this, name -> {
            if (name != null && !name.isEmpty()) {
                tvAdminName.setText(name);
            } else {
                tvAdminName.setText("Người dùng");
            }
        });
    }
}