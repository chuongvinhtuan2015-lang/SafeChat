package com.example.safechat.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safechat.R;
import com.example.safechat.data.local.db.entities.ConversationEntity;
import com.example.safechat.ui.chat.ChatActivity;
import com.example.safechat.ui.friends.FriendsActivity;
import com.example.safechat.ui.profile.ProfileActivity;
import com.example.safechat.utils.AvatarHelper;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ChungActivity extends AppCompatActivity {

    private TextView tvAdminName;
    private TextView tvProfileChar;
    private ImageView ivProfileAvatar;
    private MaterialCardView btnProfile;
    private RecyclerView rvChatList;
    private EditText etSearch;

    private ChungViewModel viewModel;
    private ConversationAdapter adapter;
    private List<ConversationEntity> originalList = new ArrayList<>();

    // Cờ tránh đọc lại EncryptedSharedPreferences 2 lần lúc mới mở app
    private boolean isFirstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chung);

        initViews();
        setupRecyclerView();
        setupListeners();
        observeViewModel();

        // Trì hoãn xin quyền sau khi frame đầu tiên đã vẽ xong để không khựng animation mở app
        getWindow().getDecorView().post(this::checkAndRequestNotificationPermission);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFirstResume) {
            isFirstResume = false;
            return; // Bỏ qua lần đầu vì ViewModel vừa mới load xong trong onCreate
        }
        if (viewModel != null) {
            viewModel.refreshUserProfile();
        }
    }

    private void checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }
    }

    private void initViews() {
        tvAdminName = findViewById(R.id.tvAdminName);
        tvProfileChar = findViewById(R.id.tvProfileChar);
        ivProfileAvatar = findViewById(R.id.ivProfileAvatar);
        btnProfile = findViewById(R.id.btnProfile);
        rvChatList = findViewById(R.id.rvChatList);
        etSearch = findViewById(R.id.etSearch);

        viewModel = new ViewModelProvider(this).get(ChungViewModel.class);
    }

    private void setupRecyclerView() {
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setHasFixedSize(true); // Giảm chi phí đo đạc lại layout khi cập nhật tin nhắn

        adapter = new ConversationAdapter(conversation -> {
            String peerEdKey = conversation.getPeerEd25519PublicKey();
            if (peerEdKey == null || peerEdKey.trim().isEmpty()) {
                Toast.makeText(ChungActivity.this, "Không tìm thấy khóa bảo mật của người này", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(ChungActivity.this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_PARTNER_ID, conversation.getPartnerId());
            intent.putExtra(ChatActivity.EXTRA_PARTNER_NAME, conversation.getPartnerName());
            intent.putExtra(ChatActivity.EXTRA_PARTNER_AVATAR, conversation.getPartnerAvatar());
            intent.putExtra(ChatActivity.EXTRA_PEER_ED25519_KEY, peerEdKey);
            startActivity(intent);
        });

        rvChatList.setAdapter(adapter);
    }

    private void setupListeners() {
        View tabFriends = findViewById(R.id.tabFriends);
        if (tabFriends != null) {
            tabFriends.setOnClickListener(v -> {
                Intent intent = new Intent(ChungActivity.this, FriendsActivity.class);
                startActivity(intent);
            });
        }

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                Intent intent = new Intent(ChungActivity.this, ProfileActivity.class);
                startActivity(intent);
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterConversations(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void observeViewModel() {
        // 1. Chỉ đổi text khi tên thay đổi
        viewModel.getUserFullName().observe(this, name -> {
            String displayName = (name != null && !name.trim().isEmpty()) ? name : "Người dùng";
            if (tvAdminName != null) {
                tvAdminName.setText(displayName);
            }
        });

        // 2. Chỉ load lại avatar khi link ảnh hoặc dữ liệu thay đổi
        viewModel.getUserAvatarUrl().observe(this, avatar -> {
            String name = viewModel.getUserFullName().getValue();
            String displayName = (name != null && !name.trim().isEmpty()) ? name : "Người dùng";
            AvatarHelper.loadAvatar(this, avatar, displayName, ivProfileAvatar, tvProfileChar);
        });

        // Quan sát danh sách cuộc trò chuyện
        viewModel.getConversations().observe(this, list -> {
            if (list != null) {
                originalList = list;
                if (etSearch != null && !etSearch.getText().toString().trim().isEmpty()) {
                    filterConversations(etSearch.getText().toString());
                } else {
                    adapter.setList(list);
                }
            }
        });
    }

    //Search theo tên và theo tin nhắn cuối
    private void filterConversations(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setList(originalList);
            return;
        }

        List<ConversationEntity> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();
        for (ConversationEntity item : originalList) {
            boolean matchName = item.getPartnerName() != null && item.getPartnerName().toLowerCase().contains(lowerQuery);
            boolean matchMsg = item.getLastMessage() != null && item.getLastMessage().toLowerCase().contains(lowerQuery);
            if (matchName || matchMsg) {
                filtered.add(item);
            }
        }
        adapter.setList(filtered);
    }
}