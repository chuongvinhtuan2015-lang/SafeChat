package com.example.safechat.ui.friends;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.safechat.data.models.Friend;
import com.example.safechat.data.models.User;
import com.example.safechat.data.repository.FriendRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class FriendsViewModel extends ViewModel {

    private final FriendRepository friendRepository;
    private final MutableLiveData<List<Friend>> friendsList = new MutableLiveData<>();
    private final MutableLiveData<User> searchedUser = new MutableLiveData<>();
    private final MutableLiveData<User> pendingSender = new MutableLiveData<>();
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();

    // Khai báo quản lý ListenerRegistration để lắng nghe Realtime
    private ListenerRegistration requestListenerRegistration;

    public FriendsViewModel(FriendRepository repository) {
        this.friendRepository = repository;
    }

    public LiveData<List<Friend>> getFriendsList() { return friendsList; }
    public LiveData<User> getSearchedUser() { return searchedUser; }
    public LiveData<User> getPendingSender() { return pendingSender; }
    public LiveData<String> getStatusMessage() { return statusMessage; }

    // Xử lý tìm kiếm người dùng qua Email
    public void searchUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            statusMessage.setValue("Vui lòng nhập email cần tìm");
            return;
        }

        friendRepository.searchUserByEmail(email.trim())
                .addOnSuccessListener(user -> searchedUser.setValue(user))
                .addOnFailureListener(e -> {
                    searchedUser.setValue(null);
                    statusMessage.setValue(e.getMessage());
                });
    }

    // Xử lý gửi lời mời kết bạn theo UID
    public void sendFriendRequest(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            statusMessage.setValue("Không thể gửi lời mời kết bạn cho chính mình!");
            return;
        }

        friendRepository.sendFriendRequest(currentUserId, targetUserId)
                .addOnSuccessListener(aVoid -> statusMessage.setValue("Đã gửi lời mời kết bạn!"))
                .addOnFailureListener(e -> statusMessage.setValue("Lỗi khi gửi lời mời: " + e.getMessage()));
    }

    // Xử lý gửi lời mời kết bạn qua Email
    public void sendFriendRequestByEmail(String currentUserId, String email) {
        if (email == null || email.trim().isEmpty()) {
            statusMessage.setValue("Vui lòng nhập email người cần kết bạn!");
            return;
        }

        friendRepository.searchAndSendFriendRequest(currentUserId, email.trim())
                .addOnSuccessListener(aVoid -> statusMessage.setValue("Đã gửi lời mời kết bạn thành công!"))
                .addOnFailureListener(e -> statusMessage.setValue(e.getMessage()));
    }

    // Lắng nghe Realtime lời mời kết bạn mới gửi đến
    public void listenForPendingRequests(String currentUserId) {
        if (requestListenerRegistration != null) {
            requestListenerRegistration.remove();
        }

        requestListenerRegistration = friendRepository.listenPendingRequests(currentUserId, (value, error) -> {
            if (error != null || value == null || value.isEmpty()) {
                pendingSender.setValue(null);
                return;
            }

            // Lấy lời mời đầu tiên đang PENDING
            DocumentSnapshot doc = value.getDocuments().get(0);
            String senderId = doc.getString("senderId");

            if (senderId != null) {
                friendRepository.getUserProfile(senderId).addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        User sender = userDoc.toObject(User.class);
                        if (sender != null) {
                            sender.setUid(senderId);
                            pendingSender.setValue(sender);
                        }
                    }
                });
            }
        });
    }

    // Chấp nhận kết bạn & thực hiện trao đổi khóa ECDH
    public void acceptRequestAndExchangeKey(String currentUserId, String peerUserId) {
        friendRepository.performKeyExchange(currentUserId, peerUserId)
                .addOnSuccessListener(aBoolean -> statusMessage.setValue("Kết bạn và trao đổi khóa thành công!"))
                .addOnFailureListener(e -> statusMessage.setValue("Lỗi trao đổi khóa: " + e.getMessage()));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Hủy lắng nghe Firestore khi ViewModel bị hủy để tránh rò rỉ bộ nhớ
        if (requestListenerRegistration != null) {
            requestListenerRegistration.remove();
        }
    }
}