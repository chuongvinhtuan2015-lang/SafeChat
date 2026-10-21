package com.example.safechat.data.repository;

import com.example.safechat.crypto.CryptoManager;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.example.safechat.data.models.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.safechat.data.models.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.ListenerRegistration;
public class FriendRepository {
    private final FirestoreDataSource firestoreDataSource;
    private final LocalDataManager localDataManager;

    public FriendRepository(FirestoreDataSource firestore, LocalDataManager localData) {
        this.firestoreDataSource = firestore;
        this.localDataManager = localData;
    }

    public Task<Boolean> performKeyExchange(String currentUserId, String peerUserId) {
        return firestoreDataSource.getPeerPublicKey(peerUserId).continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                return Tasks.forException(new Exception("Không tìm thấy Public Key của đối phương"));
            }

            String peerPublicKeyBase64 = task.getResult().getString("publicKeyX25519");
            if (peerPublicKeyBase64 == null) {
                return Tasks.forException(new Exception("Public Key bị trống"));
            }

            // 1. Lấy Private Key X25519 của bạn từ EncryptedSharedPreferences
            byte[] myPrivateKey = localDataManager.getX25519PrivateKey();
            if (myPrivateKey == null) {
                return Tasks.forException(new Exception("Không tìm thấy Private Key của bạn trên thiết bị"));
            }

            // 2. Gọi tĩnh phương thức generateSharedSecret với đủ 2 tham số
            String sharedSecret = CryptoManager.generateSharedSecret(myPrivateKey, peerPublicKeyBase64);
            if (sharedSecret == null) {
                return Tasks.forException(new Exception("Lỗi khi tính toán Shared Secret"));
            }

            // 3. Lưu Khóa chung (Shared Secret) vào bộ nhớ cục bộ
            String keyAlias = "shared_key_" + peerUserId;
            localDataManager.saveEncryptedSecret(keyAlias, sharedSecret);

            // 4. Đánh dấu trạng thái trao đổi khóa thành công trên Firestore
            return firestoreDataSource.acceptFriendRequest(peerUserId, currentUserId)
                    .continueWith(t -> true);
        });
    }
    //Tìm người dùng theo email
    public Task<User> searchUserByEmail(String email) {
        return firestoreDataSource.getUserByEmail(email).continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                return Tasks.forException(new Exception("Không tìm thấy người dùng với email này"));
            }

            DocumentSnapshot document = task.getResult().getDocuments().get(0);
            User user = document.toObject(User.class);
            if (user != null) {
                user.setUid(document.getId());
            }
            return Tasks.forResult(user);
        });
    }
    //gửi lời mời kết bạn
    public Task<Void> sendFriendRequest(String currentUserId, String targetUserId) {
        return firestoreDataSource.sendFriendRequest(currentUserId, targetUserId);
    }
    //Tìm theo Email rồi tự động gửi Lời mời kết bạn ngay
    public Task<Void> searchAndSendFriendRequest(String currentUserId, String email) {
        return firestoreDataSource.getUserByEmail(email).continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                return Tasks.forException(new Exception("Không tìm thấy người dùng với email này!"));
            }

            DocumentSnapshot document = task.getResult().getDocuments().get(0);
            String targetUserId = document.getId();

            if (targetUserId.equals(currentUserId)) {
                return Tasks.forException(new Exception("Không thể gửi lời mời kết bạn cho chính mình!"));
            }

            return firestoreDataSource.sendFriendRequest(currentUserId, targetUserId);
        });
    }

    //Đăng ký lắng nghe lời mời kết bạn theo thời gian thực
    public ListenerRegistration listenPendingRequests(String currentUserId, EventListener<QuerySnapshot> listener) {
        return firestoreDataSource.listenPendingRequests(currentUserId, listener);
    }

    //Lấy thông tin Profile người dùng theo UID
    public Task<DocumentSnapshot> getUserProfile(String uid) {
        return firestoreDataSource.getUser(uid);
    }
}