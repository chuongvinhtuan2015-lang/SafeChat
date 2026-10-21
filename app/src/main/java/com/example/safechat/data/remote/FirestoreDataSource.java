package com.example.safechat.data.remote;

import com.example.safechat.data.models.User;
import com.example.safechat.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.gms.tasks.Tasks;

import java.util.HashMap;
import java.util.Map;

public class FirestoreDataSource {

    private final FirebaseFirestore db;

    public FirestoreDataSource() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Lưu document người dùng kèm Public Keys và Gói khóa bí mật lên Firestore
     */
    public Task<Void> saveUser(User user) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(user.getUid())
                .set(user);
    }

    /**
     * Lấy document người dùng từ collection users dựa trên UID
     */
    public Task<DocumentSnapshot> getUser(String uid) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get();
    }

    public Task<Void> updateFullName(String uid, String fullName) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .update("fullName", fullName);
    }

    public Task<Void> updateAvatarUrl(String uid, String avatarUrl) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .update("avatarUrl", avatarUrl);
    }
    //Gửi lời mời kết bạn
    public Task<Void> sendFriendRequest(String currentUserId, String targetUserId) {
        String requestId = currentUserId + "_" + targetUserId;
        Map<String, Object> request = new HashMap<>();
        request.put("senderId", currentUserId);
        request.put("receiverId", targetUserId);
        request.put("status", "PENDING");
        request.put("timestamp", FieldValue.serverTimestamp());

        return db.collection("friend_requests").document(requestId).set(request);
    }

    //Chấp nhận kết bạn
    public Task<Void> acceptFriendRequest(String senderId, String receiverId) {
        return db.collection("friend_requests")
                .whereEqualTo("senderId", senderId)
                .whereEqualTo("receiverId", receiverId)
                .whereEqualTo("status", "PENDING")
                .get()
                .continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        String docId = task.getResult().getDocuments().get(0).getId();
                        return db.collection("friend_requests").document(docId).update("status", "ACCEPTED");
                    }
                    return Tasks.forException(new Exception("Không tìm thấy lời mời kết bạn"));
                });
    }

    //Lấy Public Key của bạn bè
    public Task<DocumentSnapshot> getPeerPublicKey(String peerUid) {
        return db.collection("users").document(peerUid).get();
    }

    //Lưu Khóa phiên đã mã hóa lên Firestore
    public Task<Void> sendEncryptedSessionKey(String currentUserId, String peerUserId, String encryptedKeyBase64) {
        Map<String, Object> keyData = new HashMap<>();
        keyData.put("from", currentUserId);
        keyData.put("encryptedKey", encryptedKeyBase64);
        keyData.put("timestamp", FieldValue.serverTimestamp());

        return db.collection("key_exchanges")
                .document(currentUserId + "_" + peerUserId)
                .set(keyData);
    }

    //Lấy danh sách bạn bè của người dùng hiện tại
    public Task<QuerySnapshot> getFriendsList(String currentUserId) {
        return db.collection(Constants.COLLECTION_USERS).document(currentUserId)
                .collection("friends")
                .get();
    }
    /**
     * Tìm kiếm tài khoản người dùng theo địa chỉ Email
     */
    public Task<QuerySnapshot> getUserByEmail(String email) {
        return db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("email", email)
                .get();
    }

    public ListenerRegistration listenPendingRequests(String currentUserId, EventListener<QuerySnapshot> listener) {
        return db.collection("friend_requests")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener(listener);
    }
}