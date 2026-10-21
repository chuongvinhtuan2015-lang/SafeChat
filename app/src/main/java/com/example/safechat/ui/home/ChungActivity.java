
package com.example.safechat.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.example.safechat.ui.friends.FriendsActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;

import com.example.safechat.R;
import com.example.safechat.ui.profile.ProfileActivity;
import com.google.android.material.card.MaterialCardView;

public class ChungActivity extends AppCompatActivity {

    private TextView tvAdminName;
    private MaterialCardView btnProfile;
    private ChungViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chung);

        tvAdminName = findViewById(R.id.tvAdminName);
        btnProfile = findViewById(R.id.btnProfile);

        viewModel = new ViewModelProvider(this)
                .get(ChungViewModel.class);

        View tabFriends = findViewById(R.id.tabFriends); // Kiểm tra đúng ID của tab Bạn bè trong chung.xml
        if (tabFriends != null) {
            tabFriends.setOnClickListener(v -> {
                Intent intent = new Intent(ChungActivity.this, FriendsActivity.class);
                startActivity(intent);
            });
        }

        viewModel.getUserFullName().observe(this, name -> {
            if (name != null && !name.isEmpty()) {
                tvAdminName.setText(name);
            } else {
                tvAdminName.setText("Người dùng");
            }
        });
        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(
                    ChungActivity.this,
                    ProfileActivity.class
            );
            startActivity(intent);
        });
    }
}
