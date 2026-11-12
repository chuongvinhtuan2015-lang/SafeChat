package com.example.safechat.data.remote;

import com.example.safechat.crypto.models.EncryptedMessagePayload;
import com.example.safechat.data.models.Friend;
import com.example.safechat.data.models.User;
import com.example.safechat.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.gms.tasks.Tasks;

import java.util.Arrays;
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

    /**
     * Tìm kiếm tài khoản người dùng theo địa chỉ Email
     */
    public Task<QuerySnapshot> getUserByEmail(String email) {
        return db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("email", email)
                .get();
    }

    public Task<Void> sendEncryptedMessage(String chatId, String messageId, EncryptedMessagePayload payload) {
        WriteBatch batch = db.batch();

        // 1. Ghi tin nhắn mã hóa vào sub-collection "messages"
        DocumentReference messageRef = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId);
        batch.set(messageRef, payload);

        // 2. Cập nhật thông tin hội thoại gần nhất ở document chat cha
        DocumentReference chatRef = db.collection("chats").document(chatId);
        Map<String, Object> chatMetadata = new HashMap<>();
        chatMetadata.put("participants", Arrays.asList(payload.getSenderId(), payload.getReceiverId()));
        chatMetadata.put("lastSenderId", payload.getSenderId());
        chatMetadata.put("lastUpdated", FieldValue.serverTimestamp());
        batch.set(chatRef, chatMetadata, SetOptions.merge());

        return batch.commit();
    }

    /**
     * Lắng nghe các tin nhắn mới gửi đến theo thời gian thực (Realtime)
     * Lọc sắp xếp theo mốc thời gian tăng dần để bắt đúng thứ tự tin nhắn
     */
    public ListenerRegistration listenNewMessages(String chatId, long sinceTimestamp, EventListener<QuerySnapshot> listener) {
        return db.collection("chats")
                .document(chatId)
                .collection("messages")
                .whereGreaterThan("timestamp", sinceTimestamp)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    /**
     * Tải các tin nhắn cũ hơn phục vụ tải lịch sử (phân trang)
     */
    public Task<QuerySnapshot> fetchRecentMessages(String chatId, int limit) {
        return db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get();
    }
}