package com.example.safechat.data.remote;

import com.example.safechat.data.models.User;
import com.example.safechat.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirestoreDataSource {
    private final FirebaseFirestore db;

    public FirestoreDataSource() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Lưu document người dùng kèm Public Keys và Gói khóa bí mật lên Firestore[cite: 1]
     */
    public Task<Void> saveUser(User user) {
        return db.collection(Constants.COLLECTION_USERS)
                .document(user.getUid())
                .set(user);
    }

    /**
     * Lấy document người dùng từ collection users dựa trên UID[cite: 1]
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
}