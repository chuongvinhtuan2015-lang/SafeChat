package com.example.safechat.data.remote;

import com.example.safechat.data.models.Friend;
import com.example.safechat.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendRemoteDataSource {

    private final FirebaseFirestore db;

    public FriendRemoteDataSource() {
        this.db = FirebaseFirestore.getInstance();
    }

    // Lấy document quan hệ bạn bè của 1 user cụ thể
    public Task<DocumentSnapshot> getFriendDocument(String userId, String friendId) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .collection("friends")
                .document(friendId)
                .get();
    }

    // Lắng nghe danh sách bạn bè realtime
    public ListenerRegistration listenFriendsList(String currentUserId, EventListener<QuerySnapshot> listener) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .collection("friends")
                .addSnapshotListener(listener);
    }

    // Lấy toàn bộ subcollection friends
    public Task<QuerySnapshot> getFriendsSubcollection(String currentUserId) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .collection("friends")
                .get();
    }

    // Lấy profile danh sách user theo danh sách ID (giới hạn <= 30)
    public Task<QuerySnapshot> getUsersProfilesByIds(List<String> userIds) {
        return db.collection(Constants.COLLECTION_USERS)
                .whereIn(FieldPath.documentId(), userIds)
                .get();
    }

    // Gửi lời mời kết bạn lên root collection "friend_requests"
    public Task<Void> sendFriendRequest(String senderId, String receiverId) {
        String requestId = senderId + "_" + receiverId;
        Map<String, Object> request = new HashMap<>();
        request.put("senderId", senderId);
        request.put("receiverId", receiverId);
        request.put("status", "PENDING");
        request.put("timestamp", FieldValue.serverTimestamp());

        return db.collection("friend_requests").document(requestId).set(request);
    }

    // Lắng nghe lời mời kết bạn gửi đến
    public ListenerRegistration listenPendingRequests(String currentUserId, EventListener<QuerySnapshot> listener) {
        return db.collection("friend_requests")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener(listener);
    }

    // Xóa lời mời kết bạn (dùng khi từ chối)
    public Task<Void> deleteFriendRequest(String senderId, String receiverId) {
        String requestId = senderId + "_" + receiverId;
        return db.collection("friend_requests").document(requestId).delete();
    }

    // Lưu bạn bè 2 chiều và xóa lời mời kết bạn cùng lúc
    public Task<Void> saveFriendshipBothSides(String currentUserId, Friend friendForMe,
                                              String peerUserId, Friend friendForPeer) {
        WriteBatch batch = db.batch();

        DocumentReference myFriendRef = db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .collection("friends")
                .document(peerUserId);
        batch.set(myFriendRef, friendForMe);

        DocumentReference peerFriendRef = db.collection(Constants.COLLECTION_USERS)
                .document(peerUserId)
                .collection("friends")
                .document(currentUserId);
        batch.set(peerFriendRef, friendForPeer);

        // Đánh dấu hoặc xóa lời mời kết bạn
        String requestId = peerUserId + "_" + currentUserId;
        DocumentReference requestRef = db.collection("friend_requests").document(requestId);
        batch.delete(requestRef);

        return batch.commit();
    }

    // Thực hiện Chặn: Đổi status = BLOCKED bên mình, xóa bên đối phương, xóa cả 2 lời mời kết bạn
    public Task<Void> blockFriendBothSides(String currentUserId, String peerUserId) {
        WriteBatch batch = db.batch();

        DocumentReference myFriendRef = db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .collection("friends")
                .document(peerUserId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "BLOCKED");
        updates.put("blockedBy", currentUserId);
        batch.set(myFriendRef, updates, SetOptions.merge());

        DocumentReference peerFriendRef = db.collection(Constants.COLLECTION_USERS)
                .document(peerUserId)
                .collection("friends")
                .document(currentUserId);
        batch.delete(peerFriendRef);

        DocumentReference req1 = db.collection("friend_requests").document(currentUserId + "_" + peerUserId);
        DocumentReference req2 = db.collection("friend_requests").document(peerUserId + "_" + currentUserId);
        batch.delete(req1);
        batch.delete(req2);

        return batch.commit();
    }

    // Xóa bạn bè cả 2 chiều
    public Task<Void> deleteFriendshipBothSides(String currentUserId, String peerUserId) {
        WriteBatch batch = db.batch();

        DocumentReference myFriendRef = db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .collection("friends")
                .document(peerUserId);
        batch.delete(myFriendRef);

        DocumentReference peerFriendRef = db.collection(Constants.COLLECTION_USERS)
                .document(peerUserId)
                .collection("friends")
                .document(currentUserId);
        batch.delete(peerFriendRef);

        return batch.commit();
    }

    // Bỏ chặn: Xóa bản ghi chặn bên phía mình
    public Task<Void> unblockFriend(String currentUserId, String peerUserId) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .collection("friends")
                .document(peerUserId)
                .delete();
    }

    // Cập nhật thông tin profile bạn bè thay đổi theo batch
    public Task<Void> updateChangedFriendProfiles(String currentUserId, Map<String, Map<String, Object>> dirtyUpdates) {
        WriteBatch batch = db.batch();
        for (Map.Entry<String, Map<String, Object>> entry : dirtyUpdates.entrySet()) {
            DocumentReference ref = db.collection(Constants.COLLECTION_USERS)
                    .document(currentUserId)
                    .collection("friends")
                    .document(entry.getKey());
            batch.set(ref, entry.getValue(), SetOptions.merge());
        }
        return batch.commit();
    }
}