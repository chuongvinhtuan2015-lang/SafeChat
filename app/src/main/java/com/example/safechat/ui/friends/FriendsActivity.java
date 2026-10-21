package com.example.safechat.ui.friends;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safechat.R;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.repository.FriendRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

    private FriendsViewModel viewModel;
    private LocalDataManager localDataManager;


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
        tvReqName = findViewById(R.id.tvReqName);
        btnAccept = findViewById(R.id.btnAccept);
        btnDecline = findViewById(R.id.btnDecline);
        tvFriendsCount = findViewById(R.id.tvFriendsCount);
        rvFriendsList = findViewById(R.id.rvFriendsList);

        rvFriendsList.setLayoutManager(new LinearLayoutManager(this));

        // Khởi tạo và gán Adapter

    }

    private void setupViewModel() {
        FirestoreDataSource firestore = new FirestoreDataSource();
        FriendRepository repository = new FriendRepository(firestore, localDataManager);

        // Khởi tạo ViewModel đúng chuẩn qua Factory
        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new FriendsViewModel(repository);
            }
        };
        viewModel = new ViewModelProvider(this, factory).get(FriendsViewModel.class);
        String currentUid = localDataManager.getUid();

        // Kích hoạt lắng nghe Realtime lời mời kết bạn đến
        if (currentUid != null) {
            viewModel.listenForPendingRequests(currentUid);
        }

        // Lắng nghe thông báo trạng thái
        viewModel.getStatusMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                if (message.contains("thành công")) {
                    etSearchFriend.setText(""); // Xóa chữ ở ô nhập sau khi gửi thành công
                }
            }
        });

        // Lắng nghe lời mời kết bạn mới gửi đến
        viewModel.getPendingSender().observe(this, sender -> {
            if (sender != null) {
                displayFriendRequest(sender.getUid(), sender.getFullName());
            } else {
                cardFriendRequest.setVisibility(View.GONE);
                tvRequestBadge.setText("0");
            }
        });

        // Lắng nghe danh sách bạn bè và đẩy vào Adapter
        viewModel.getFriendsList().observe(this, friends -> {
            if (friends != null) {
                tvFriendsCount.setText(friends.size() + " người");

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
            cardFriendRequest.setVisibility(View.GONE);
            tvRequestBadge.setText("0");
            Toast.makeText(this, "Đã từ chối lời mời kết bạn", Toast.LENGTH_SHORT).show();
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
        cardFriendRequest.setVisibility(View.VISIBLE);
    }
}