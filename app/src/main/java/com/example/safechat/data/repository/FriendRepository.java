package com.example.safechat.data.repository;

import com.example.safechat.crypto.CryptoManager;
import com.example.safechat.data.local.db.dao.ConversationDao;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.models.Friend;
import com.example.safechat.data.models.User;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.example.safechat.data.remote.FriendRemoteDataSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FriendRepository {
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();

    private final FriendRemoteDataSource friendRemoteDataSource;
    private final FirestoreDataSource userRemoteDataSource;
    private final ConversationDao conversationDao;
    private final LocalDataManager localDataManager;

    public interface SyncCallback {
        void onSuccess(int updatedCount);
        void onFailure(String errorMessage);
    }

    public FriendRepository(FriendRemoteDataSource friendRemoteDataSource,
                            FirestoreDataSource userRemoteDataSource,
                            ConversationDao conversationDao,
                            LocalDataManager localData) {
        this.friendRemoteDataSource = friendRemoteDataSource;
        this.userRemoteDataSource = userRemoteDataSource;
        this.conversationDao = conversationDao;
        this.localDataManager = localData;
    }

    public void syncFriendsFromRemote(String currentUserId, SyncCallback callback) {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            if (callback != null) callback.onFailure("Mã người dùng không hợp lệ");
            return;
        }

        friendRemoteDataSource.getFriendsSubcollection(currentUserId)
                .addOnSuccessListener(friendSnapshots -> {
                    if (friendSnapshots == null || friendSnapshots.isEmpty()) {
                        if (callback != null) callback.onSuccess(0);
                        return;
                    }

                    Map<String, DocumentSnapshot> existingFriendDocs = new HashMap<>();
                    List<String> friendIds = new ArrayList<>();
                    for (DocumentSnapshot doc : friendSnapshots.getDocuments()) {
                        friendIds.add(doc.getId());
                        existingFriendDocs.put(doc.getId(), doc);
                    }

                    List<String> queryBatch = friendIds.size() > 30 ? friendIds.subList(0, 30) : friendIds;

                    friendRemoteDataSource.getUsersProfilesByIds(queryBatch)
                            .addOnSuccessListener(userDocs -> {
                                if (userDocs == null || userDocs.isEmpty()) {
                                    if (callback != null) callback.onSuccess(0);
                                    return;
                                }

                                Map<String, Map<String, Object>> dirtyUpdates = new HashMap<>();
                                Map<String, String[]> profileMap = new HashMap<>();

                                for (DocumentSnapshot userDoc : userDocs.getDocuments()) {
                                    String partnerId = userDoc.getId();
                                    String newName = userDoc.getString("fullName");
                                    String newAvatar = userDoc.getString("avatarUrl");

                                    DocumentSnapshot currentFriendDoc = existingFriendDocs.get(partnerId);
                                    String oldName = currentFriendDoc != null ? currentFriendDoc.getString("fullName") : null;
                                    String oldAvatar = currentFriendDoc != null ? currentFriendDoc.getString("avatarUrl") : null;

                                    boolean nameChanged = !Objects.equals(oldName, newName) && newName != null;
                                    boolean avatarChanged = !Objects.equals(oldAvatar, newAvatar) && newAvatar != null;

                                    if (nameChanged || avatarChanged) {
                                        Map<String, Object> updateData = new HashMap<>();
                                        if (nameChanged) updateData.put("fullName", newName);
                                        if (avatarChanged) updateData.put("avatarUrl", newAvatar);
                                        dirtyUpdates.put(partnerId, updateData);
                                    }

                                    profileMap.put(partnerId, new String[]{newName, newAvatar});
                                }

                                Runnable updateRoomTask = () -> {
                                    conversationDao.updateProfilesMap(profileMap);
                                    if (callback != null) callback.onSuccess(profileMap.size());
                                };

                                if (!dirtyUpdates.isEmpty()) {
                                    friendRemoteDataSource.updateChangedFriendProfiles(currentUserId, dirtyUpdates)
                                            .addOnCompleteListener(task -> {
                                                if (!task.isSuccessful() && task.getException() != null) {
                                                    if (callback != null) {
                                                        callback.onFailure("Lỗi cập nhật Firestore: " + task.getException().getMessage());
                                                    }
                                                    return;
                                                }
                                                DB_EXECUTOR.execute(updateRoomTask);
                                            });
                                } else {
                                    DB_EXECUTOR.execute(updateRoomTask);
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onFailure("Lỗi lấy thông tin: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure("Lỗi đọc bạn bè: " + e.getMessage());
                });
    }

    public Task<Boolean> performKeyExchange(String currentUserId, String peerUserId) {
        Task<DocumentSnapshot> getPeerTask = userRemoteDataSource.getUser(peerUserId);
        Task<DocumentSnapshot> getMyTask = userRemoteDataSource.getUser(currentUserId);

        return Tasks.whenAllSuccess(getPeerTask, getMyTask).continueWithTask(tasks -> {
            List<Object> results = tasks.getResult();
            if (results == null || results.size() < 2) {
                return Tasks.forException(new IllegalStateException("Không lấy được dữ liệu"));
            }

            DocumentSnapshot peerDoc = (DocumentSnapshot) results.get(0);
            DocumentSnapshot myDoc = (DocumentSnapshot) results.get(1);

            User peerUser = peerDoc.toObject(User.class);
            User myUser = myDoc.toObject(User.class);

            if (peerUser == null || myUser == null) {
                return Tasks.forException(new Exception("Lỗi đọc dữ liệu người dùng"));
            }

            peerUser.setUid(peerDoc.getId());
            myUser.setUid(myDoc.getId());

            String peerPublicKeyBase64 = peerUser.getPublicKeyX25519();
            boolean isCalculated = ensureSharedKeyForPeer(peerUserId, peerPublicKeyBase64);
            if (!isCalculated) {
                return Tasks.forException(new Exception("Lỗi khi tính toán khóa bí mật"));
            }

            String myKeyAlias = "shared_key_" + peerUserId;

            Friend friendForMe = new Friend(
                    peerUser.getUid(), peerUser.getFullName(), peerUser.getEmail(),
                    peerDoc.getString("avatarUrl"), peerUser.getPublicKeyX25519(),
                    peerUser.getPublicKeyEd25519(), true, myKeyAlias
            );
            friendForMe.setStatus("ACCEPTED");

            Friend friendForPeer = new Friend(
                    myUser.getUid(), myUser.getFullName(), myUser.getEmail(),
                    myDoc.getString("avatarUrl"), myUser.getPublicKeyX25519(),
                    myUser.getPublicKeyEd25519(), true, null
            );
            friendForPeer.setStatus("ACCEPTED");

            return friendRemoteDataSource.saveFriendshipBothSides(currentUserId, friendForMe, peerUserId, friendForPeer)
                    .continueWith(t -> true);
        });
    }

    public boolean ensureSharedKeyForPeer(String peerUserId, String peerPublicKeyX25519) {
        if (peerUserId == null || peerPublicKeyX25519 == null || peerPublicKeyX25519.trim().isEmpty()) {
            return false;
        }

        String keyAlias = "shared_key_" + peerUserId;
        String existingSecret = localDataManager.getEncryptedSecret(keyAlias);
        if (existingSecret != null && !existingSecret.trim().isEmpty()) {
            return true;
        }

        byte[] myPrivateKey = localDataManager.getX25519PrivateKey();
        if (myPrivateKey == null || myPrivateKey.length == 0) {
            return false;
        }

        try {
            String sharedSecret = CryptoManager.generateSharedSecret(myPrivateKey, peerPublicKeyX25519);
            if (sharedSecret == null || sharedSecret.trim().isEmpty()) {
                return false;
            }
            localDataManager.saveEncryptedSecret(keyAlias, sharedSecret);
            return true;
        } finally {
            Arrays.fill(myPrivateKey, (byte) 0);
        }
    }

    public Task<Void> sendFriendRequest(String currentUserId, String targetUserId) {
        if (currentUserId == null || targetUserId == null || currentUserId.trim().isEmpty() || targetUserId.trim().isEmpty()) {
            return Tasks.forException(new Exception("Mã người dùng không hợp lệ"));
        }
        if (currentUserId.equals(targetUserId)) {
            return Tasks.forException(new Exception("Không thể gửi lời mời kết bạn cho chính mình!"));
        }

        Task<DocumentSnapshot> checkTargetTask = friendRemoteDataSource.getFriendDocument(targetUserId, currentUserId);
        Task<DocumentSnapshot> checkMyTask = friendRemoteDataSource.getFriendDocument(currentUserId, targetUserId);

        return Tasks.whenAllComplete(checkTargetTask, checkMyTask).continueWithTask(allTasks -> {
            if (checkTargetTask.isSuccessful() && checkTargetTask.getResult() != null) {
                DocumentSnapshot doc = checkTargetTask.getResult();
                if (doc.exists()) {
                    String status = doc.getString("status");
                    if ("BLOCKED".equalsIgnoreCase(status)) {
                        return Tasks.forException(new Exception("Không thể gửi lời mời kết bạn đến người dùng này."));
                    }
                    if ("ACCEPTED".equalsIgnoreCase(status)) {
                        return Tasks.forException(new Exception("Hai bạn đã là bạn bè rồi."));
                    }
                }
            }

            if (checkMyTask.isSuccessful() && checkMyTask.getResult() != null) {
                DocumentSnapshot doc = checkMyTask.getResult();
                if (doc.exists()) {
                    String status = doc.getString("status");
                    if ("BLOCKED".equalsIgnoreCase(status)) {
                        return Tasks.forException(new Exception("Bạn đang chặn người này. Hãy bỏ chặn trước khi kết bạn."));
                    }
                    if ("ACCEPTED".equalsIgnoreCase(status)) {
                        return Tasks.forException(new Exception("Hai bạn đã là bạn bè rồi."));
                    }
                }
            }

            return friendRemoteDataSource.sendFriendRequest(currentUserId, targetUserId);
        });
    }

    public Task<Void> searchAndSendFriendRequest(String currentUserId, String email) {
        return userRemoteDataSource.getUserByEmail(email).continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                return Tasks.forException(new Exception("Không tìm thấy người dùng với email này!"));
            }

            DocumentSnapshot document = task.getResult().getDocuments().get(0);
            String targetUserId = document.getId();
            return sendFriendRequest(currentUserId, targetUserId);
        });
    }

    public Task<Void> blockFriend(String currentUserId, String peerUserId) {
        if (currentUserId == null || peerUserId == null) {
            return Tasks.forException(new Exception("ID không hợp lệ"));
        }

        // Xóa local secret và conversation
        String keyAlias = "shared_key_" + peerUserId;
        localDataManager.removeEncryptedSecret(keyAlias);
        DB_EXECUTOR.execute(() -> conversationDao.deleteConversationByPartnerId(peerUserId));

        return friendRemoteDataSource.blockFriendBothSides(currentUserId, peerUserId);
    }

    public Task<Void> unblockFriend(String currentUserId, String peerUserId) {
        if (currentUserId == null || peerUserId == null) {
            return Tasks.forException(new Exception("ID không hợp lệ"));
        }
        return friendRemoteDataSource.unblockFriend(currentUserId, peerUserId);
    }

    public Task<Void> deleteFriend(String currentUserId, String peerUserId) {
        if (currentUserId == null || peerUserId == null) {
            return Tasks.forException(new Exception("ID không hợp lệ"));
        }

        String keyAlias = "shared_key_" + peerUserId;
        localDataManager.removeEncryptedSecret(keyAlias);
        DB_EXECUTOR.execute(() -> conversationDao.deleteConversationByPartnerId(peerUserId));

        return friendRemoteDataSource.deleteFriendshipBothSides(currentUserId, peerUserId);
    }

    public Task<Void> declineFriendRequest(String currentUserId, String senderId) {
        if (currentUserId == null || senderId == null) {
            return Tasks.forException(new Exception("ID không hợp lệ"));
        }
        return friendRemoteDataSource.deleteFriendRequest(senderId, currentUserId);
    }

    public Task<User> searchUserByEmail(String email) {
        return userRemoteDataSource.getUserByEmail(email).continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                return Tasks.forException(new Exception("Không tìm thấy người dùng"));
            }
            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
            User user = doc.toObject(User.class);
            if (user != null) user.setUid(doc.getId());
            return Tasks.forResult(user);
        });
    }

    public ListenerRegistration listenPendingRequests(String currentUserId, EventListener<QuerySnapshot> listener) {
        return friendRemoteDataSource.listenPendingRequests(currentUserId, listener);
    }

    public Task<DocumentSnapshot> getUserProfile(String uid) {
        return userRemoteDataSource.getUser(uid);
    }

    public ListenerRegistration listenFriendsList(String currentUserId, EventListener<QuerySnapshot> listener) {
        return friendRemoteDataSource.listenFriendsList(currentUserId, listener);
    }
}