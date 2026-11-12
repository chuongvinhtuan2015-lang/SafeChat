package com.example.safechat.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safechat.R;
import com.example.safechat.data.local.db.AppDatabase;
import com.example.safechat.data.local.db.dao.ConversationDao;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.models.Friend;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.remote.FriendRemoteDataSource;
import com.example.safechat.data.repository.FriendRepository;
import com.example.safechat.ui.chat.ChatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

public class FriendsActivity extends AppCompatActivity {

    private MaterialCardView btnAddFriend;
    private EditText etSearchFriend;
    private TextView tvRequestBadge;
    private MaterialCardView cardFriendRequest;
    private TextView tvReqName;
    private MaterialButton btnAccept;
    private MaterialButton btnDecline;
    private TextView tvFriendsCount;
    private RecyclerView rvFriendsList;
    private TextView tvReqAvatarChar;

    private FriendsViewModel viewModel;
    private LocalDataManager localDataManager;
    private FriendAdapter friendAdapter;

    private String currentPendingSenderId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.friends);

        localDataManager = LocalDataManager.getInstance(this);

        initViews();
        setupViewModel();
        setupListeners();
    }

    private void initViews() {
        btnAddFriend = findViewById(R.id.btnAddFriend);
        etSearchFriend = findViewById(R.id.etSearchFriend);
        tvRequestBadge = findViewById(R.id.tvRequestBadge);

        cardFriendRequest = findViewById(R.id.cardFriendRequest);
        tvReqName = cardFriendRequest.findViewById(R.id.tvReqName);
        tvReqAvatarChar = cardFriendRequest.findViewById(R.id.tvReqAvatarChar);
        btnAccept = cardFriendRequest.findViewById(R.id.btnAccept);
        btnDecline = cardFriendRequest.findViewById(R.id.btnDecline);

        tvFriendsCount = findViewById(R.id.tvFriendsCount);
        rvFriendsList = findViewById(R.id.rvFriendsList);

        friendAdapter = new FriendAdapter(new FriendAdapter.OnFriendActionListener() {
            @Override
            public void onFriendClick(Friend friend) {
                if (friend == null) return;

                String partnerId = friend.getUid() != null ? friend.getUid() : friend.getUserId();
                String partnerName = friend.getFullName() != null ? friend.getFullName() : friend.getName();

                Intent intent = new Intent(FriendsActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_PARTNER_ID, partnerId);
                intent.putExtra(ChatActivity.EXTRA_PARTNER_NAME, partnerName);
                intent.putExtra(ChatActivity.EXTRA_PARTNER_AVATAR, friend.getAvatarUrl());
                intent.putExtra(ChatActivity.EXTRA_PEER_ED25519_KEY, friend.getPublicKeyEd25519());
                startActivity(intent);
            }

            @Override
            public void onBlockFriendClick(Friend friend) {
                confirmBlockFriend(friend);
            }

            @Override
            public void onDeleteFriendClick(Friend friend) {
                confirmDeleteFriend(friend);
            }

            @Override
            public void onUnblockFriendClick(Friend friend) {
                confirmUnblockFriend(friend);
            }
        });

        rvFriendsList.setLayoutManager(new LinearLayoutManager(this));
        rvFriendsList.setAdapter(friendAdapter);
    }

    // 1. CHẶN: Đổi status = BLOCKED để item đổi sang nút "Bỏ chặn"
    private void confirmBlockFriend(Friend friend) {
        if (friend == null) return;
        String displayName = friend.getFullName() != null ? friend.getFullName() : friend.getName();
        String friendId = friend.getUid() != null ? friend.getUid() : friend.getUserId();

        new AlertDialog.Builder(this)
                .setTitle("Chặn người dùng")
                .setMessage("Bạn có chắc chắn muốn chặn " + displayName + "? Cuộc trò chuyện sẽ bị ẩn và bạn có thể bỏ chặn bất kỳ lúc nào tại đây.")
                .setPositiveButton("Chặn", (dialog, which) -> {
                    String currentUid = localDataManager.getUid();
                    if (currentUid != null && friendId != null) {
                        viewModel.blockFriend(currentUid, friendId);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // 2. BỎ CHẶN: Xóa document trong subcollection friends -> Mất khỏi danh sách -> Thành người lạ, có thể kết bạn lại
    private void confirmUnblockFriend(Friend friend) {
        if (friend == null) return;
        String displayName = friend.getFullName() != null ? friend.getFullName() : friend.getName();
        String friendId = friend.getUid() != null ? friend.getUid() : friend.getUserId();

        new AlertDialog.Builder(this)
                .setTitle("Bỏ chặn người dùng")
                .setMessage("Bạn có chắc muốn bỏ chặn " + displayName + "? Người này sẽ bị xóa khỏi danh sách chặn và hai bạn có thể tìm thấy để kết bạn lại.")
                .setPositiveButton("Bỏ chặn", (dialog, which) -> {
                    String currentUid = localDataManager.getUid();
                    if (currentUid != null && friendId != null) {
                        viewModel.unblockFriend(currentUid, friendId);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // 3. XÓA BẠN: Xóa quan hệ bạn bè
    private void confirmDeleteFriend(Friend friend) {
        if (friend == null) return;
        String displayName = friend.getFullName() != null ? friend.getFullName() : friend.getName();
        String friendId = friend.getUid() != null ? friend.getUid() : friend.getUserId();

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa bạn")
                .setMessage("Bạn có chắc chắn muốn xóa " + displayName + " khỏi danh sách bạn bè?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    String currentUid = localDataManager.getUid();
                    if (currentUid != null && friendId != null) {
                        viewModel.deleteFriend(currentUid, friendId);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void setupViewModel() {
        FriendRemoteDataSource friendRemote = new FriendRemoteDataSource();
        FirestoreDataSource userRemote = new FirestoreDataSource();
        AppDatabase db = AppDatabase.getInstance(this);
        ConversationDao conversationDao = db.conversationDao();

        FriendRepository repository = new FriendRepository(friendRemote, userRemote, conversationDao, localDataManager);
        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new FriendsViewModel(repository);
            }
        };
        viewModel = new ViewModelProvider(this, factory).get(FriendsViewModel.class);
        String currentUid = localDataManager.getUid();

        if (currentUid != null) {
            viewModel.listenForPendingRequests(currentUid);
            viewModel.listenForFriendsList(currentUid);
        }

        viewModel.getStatusMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                if (message.contains("thành công")) {
                    etSearchFriend.setText("");
                }
            }
        });

        viewModel.getPendingSender().observe(this, sender -> {
            if (sender != null) {
                displayFriendRequest(sender.getUid(), sender.getFullName());
            } else {
                cardFriendRequest.setVisibility(View.GONE);
                tvRequestBadge.setText("0");
            }
        });

        viewModel.getFriendsList().observe(this, friends -> {
            if (friends != null) {
                tvFriendsCount.setText(friends.size() + " người");
                friendAdapter.submitList(friends);
            } else {
                tvFriendsCount.setText("0 người");
                friendAdapter.submitList(new ArrayList<>());
            }
        });
    }

    private void setupListeners() {
        btnAddFriend.setOnClickListener(v -> {
            String email = etSearchFriend.getText().toString().trim();
            String currentUid = localDataManager.getUid();
            viewModel.sendFriendRequestByEmail(currentUid, email);
        });

        btnAccept.setOnClickListener(v -> {
            if (!currentPendingSenderId.isEmpty()) {
                String currentUid = localDataManager.getUid();
                viewModel.acceptRequestAndExchangeKey(currentUid, currentPendingSenderId);
            }
        });

        btnDecline.setOnClickListener(v -> {
            if (!currentPendingSenderId.isEmpty()) {
                String currentUid = localDataManager.getUid();
                viewModel.declineFriendRequest(currentUid, currentPendingSenderId);
            }
            cardFriendRequest.setVisibility(View.GONE);
            tvRequestBadge.setText("0");
        });

        etSearchFriend.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        View tabMessages = findViewById(R.id.tabMessages);
        if (tabMessages != null) {
            tabMessages.setOnClickListener(v -> finish());
        }
    }

    public void displayFriendRequest(String senderId, String senderName) {
        this.currentPendingSenderId = senderId;
        tvReqName.setText(senderName);
        tvRequestBadge.setText("1");

        if (tvReqAvatarChar != null && senderName != null && !senderName.trim().isEmpty()) {
            String[] parts = senderName.trim().split("\\s+");
            if (parts.length >= 2) {
                String initials = "" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0);
                tvReqAvatarChar.setText(initials.toUpperCase());
            } else {
                tvReqAvatarChar.setText(senderName.substring(0, Math.min(2, senderName.length())).toUpperCase());
            }
        }

        cardFriendRequest.setVisibility(View.VISIBLE);
    }
}