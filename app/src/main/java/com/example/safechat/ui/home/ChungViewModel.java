package com.example.safechat.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.safechat.data.local.db.AppDatabase;
import com.example.safechat.data.local.db.entities.ConversationEntity;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.models.Friend;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.remote.FriendRemoteDataSource;
import com.example.safechat.data.repository.ChatRepository;
import com.example.safechat.data.repository.FriendRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChungViewModel extends AndroidViewModel {

    private final MutableLiveData<String> userFullName = new MutableLiveData<>();
    private final MutableLiveData<String> userAvatarUrl = new MutableLiveData<>();
    private final ChatRepository chatRepository;
    private final FriendRepository friendRepository;
    private final AppDatabase appDb;
    private final LocalDataManager localDataManager;
    private final LiveData<List<ConversationEntity>> conversations;

    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private ListenerRegistration friendsListener;

    public ChungViewModel(@NonNull Application application) {
        super(application);
        this.localDataManager = LocalDataManager.getInstance(application);
        this.appDb = AppDatabase.getInstance(application);

        userFullName.setValue(localDataManager.getFullName());
        userAvatarUrl.setValue(localDataManager.getAvatarUrl());

        this.chatRepository = new ChatRepository(
                appDb.messageDao(),
                appDb.conversationDao(),
                appDb.ratchetSessionDao(),
                new FirestoreDataSource(),
                localDataManager
        );

        this.friendRepository = new FriendRepository(
                new FriendRemoteDataSource(),
                new FirestoreDataSource(),
                appDb.conversationDao(),
                localDataManager
        );

        this.conversations = chatRepository.getConversations();

        // Tự động kích hoạt đồng bộ khi ViewModel khởi tạo
        syncAndSeedConversations();
    }

    public LiveData<String> getUserFullName() {
        return userFullName;
    }

    public LiveData<String> getUserAvatarUrl() {
        return userAvatarUrl;
    }

    public LiveData<List<ConversationEntity>> getConversations() {
        return conversations;
    }

    public void refreshUserProfile() {
        userFullName.setValue(localDataManager.getFullName());
        userAvatarUrl.setValue(localDataManager.getAvatarUrl());
    }

    private String buildConversationId(String uid1, String uid2) {
        if (uid1 == null || uid2 == null) return "";
        return (uid1.compareTo(uid2) < 0) ? (uid1 + "_" + uid2) : (uid2 + "_" + uid1);
    }

    public void syncAndSeedConversations() {
        String currentUserId = localDataManager.getUid();
        if (currentUserId == null || currentUserId.trim().isEmpty()) return;

        // 1. Đồng bộ thông tin bạn bè từ remote
        friendRepository.syncFriendsFromRemote(currentUserId, null);

        // 2. Lắng nghe bạn bè realtime & đồng bộ Room 2 chiều
        if (friendsListener != null) {
            friendsListener.remove();
        }

        friendsListener = friendRepository.listenFriendsList(currentUserId, (snapshots, error) -> {
            if (error != null || snapshots == null) return;

            diskExecutor.execute(() -> {
                Set<String> activeFriendIds = new HashSet<>();

                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    if ("BLOCKED".equals(doc.getString("status"))) {
                        continue;
                    }

                    Friend friend = doc.toObject(Friend.class);
                    if (friend == null) continue;

                    String friendUid = friend.getUid() != null ? friend.getUid() : doc.getId();
                    activeFriendIds.add(friendUid);

                    ConversationEntity existing = appDb.conversationDao().getConversationByPartnerId(friendUid);
                    if (existing == null) {
                        String name = (friend.getFullName() != null) ? friend.getFullName() : "Người dùng";
                        String conversationId = buildConversationId(currentUserId, friendUid);

                        ConversationEntity newConversation = new ConversationEntity(
                                conversationId,
                                friendUid,
                                name,
                                friend.getAvatarUrl(),
                                "Bắt đầu cuộc trò chuyện...",
                                System.currentTimeMillis(),
                                friend.getPublicKeyEd25519()
                        );

                        appDb.conversationDao().insertOrUpdate(newConversation);
                    }
                }

                // Xóa các cuộc hội thoại không còn là bạn bè
                List<ConversationEntity> allLocalConvs = appDb.conversationDao().getAllConversationsSync();
                if (allLocalConvs != null) {
                    for (ConversationEntity conv : allLocalConvs) {
                        if (!activeFriendIds.contains(conv.getPartnerId())) {
                            appDb.conversationDao().deleteConversationByPartnerId(conv.getPartnerId());
                        }
                    }
                }
            });
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (friendsListener != null) {
            friendsListener.remove();
        }
        diskExecutor.shutdown();
    }
}