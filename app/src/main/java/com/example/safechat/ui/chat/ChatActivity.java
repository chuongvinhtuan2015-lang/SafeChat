package com.example.safechat.ui.chat;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safechat.R;
import com.example.safechat.data.local.db.AppDatabase;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.remote.FcmNotificationService;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.repository.ChatRepository;
import com.example.safechat.utils.AvatarHelper;
import com.google.android.material.card.MaterialCardView;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_PARTNER_ID = "extra_partner_id";
    public static final String EXTRA_PARTNER_NAME = "extra_partner_name";
    public static final String EXTRA_PARTNER_AVATAR = "extra_partner_avatar";
    public static final String EXTRA_PEER_ED25519_KEY = "extra_peer_ed25519_key";

    // Views
    private View chatRootLayout;
    private View layoutBottomBar;
    private ImageView btnBack;
    private ImageView ivAvatar;
    private TextView tvAvatarText;
    private TextView tvUserName;
    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private MaterialCardView btnSend;

    private ChatViewModel chatViewModel;
    private MessageAdapter messageAdapter;

    private String partnerId;
    private String partnerName;
    private String partnerAvatar;
    private String peerEd25519Key;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        partnerId = getIntent().getStringExtra(EXTRA_PARTNER_ID);
        partnerName = getIntent().getStringExtra(EXTRA_PARTNER_NAME);
        partnerAvatar = getIntent().getStringExtra(EXTRA_PARTNER_AVATAR);
        peerEd25519Key = getIntent().getStringExtra(EXTRA_PEER_ED25519_KEY);

        LocalDataManager localDataManager = LocalDataManager.getInstance(this);
        currentUserId = localDataManager.getUid();

        if (partnerId == null || currentUserId == null) {
            Toast.makeText(this, "Không thể tải thông tin cuộc trò chuyện!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupKeyboardInsets();
        setupViewModel(localDataManager);
        setupListeners();
    }

    private void initViews() {
        chatRootLayout = findViewById(R.id.chatRootLayout);
        layoutBottomBar = findViewById(R.id.layoutBottomBar);
        btnBack = findViewById(R.id.btnBack);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvAvatarText = findViewById(R.id.tvAvatarText);
        tvUserName = findViewById(R.id.tvUserName);
        rvMessages = findViewById(R.id.rvMessages);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSend = findViewById(R.id.btnSend);

        tvUserName.setText((partnerName != null && !partnerName.trim().isEmpty()) ? partnerName : "Người dùng");
        AvatarHelper.loadAvatar(this, partnerAvatar, partnerName, ivAvatar, tvAvatarText);

        messageAdapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);
    }

    /**
     * Xử lý trồi layout bottom bar khi bàn phím ảo xuất hiện
     */
    private void setupKeyboardInsets() {
        View targetView = chatRootLayout != null ? chatRootLayout : findViewById(android.R.id.content);

        ViewCompat.setOnApplyWindowInsetsListener(targetView, (v, windowInsets) -> {
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            int bottomPadding = Math.max(imeInsets.bottom, systemBarsInsets.bottom);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomPadding);

            if (imeInsets.bottom > 0 && messageAdapter != null && messageAdapter.getItemCount() > 0) {
                rvMessages.postDelayed(() -> rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1), 100);
            }

            return windowInsets;
        });
    }

    private void setupViewModel(LocalDataManager localDataManager) {
        AppDatabase db = AppDatabase.getInstance(this);
        FirestoreDataSource firestore = new FirestoreDataSource();
        ChatRepository repository = new ChatRepository(
                db.messageDao(),
                db.conversationDao(),
                db.ratchetSessionDao(),
                firestore,
                localDataManager
        );

        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new ChatViewModel(repository);
            }
        };
        chatViewModel = new ViewModelProvider(this, factory).get(ChatViewModel.class);

        chatViewModel.initChat(currentUserId, partnerId, partnerName, partnerAvatar, peerEd25519Key);

        if (chatViewModel.getMessagesLiveData() != null) {
            chatViewModel.getMessagesLiveData().observe(this, messages -> {
                if (messages != null) {
                    messageAdapter.submitList(messages);
                    if (!messages.isEmpty()) {
                        rvMessages.scrollToPosition(messages.size() - 1);
                    }
                }
            });
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                chatViewModel.sendMessage(partnerId, partnerName, partnerAvatar, text);
                etMessageInput.setText("");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FcmNotificationService.currentActivePartnerId = partnerId;
    }

    @Override
    protected void onPause() {
        super.onPause();
        FcmNotificationService.currentActivePartnerId = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatViewModel != null) {
            chatViewModel.stopListening();
        }
    }
}