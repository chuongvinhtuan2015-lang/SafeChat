package com.example.safechat.ui.friends;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.safechat.data.models.Friend;
import com.example.safechat.data.models.User;
import com.example.safechat.data.repository.FriendRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class FriendsViewModel extends ViewModel {

    private final FriendRepository friendRepository;
    private final MutableLiveData<List<Friend>> friendsList = new MutableLiveData<>();
    private final MutableLiveData<User> searchedUser = new MutableLiveData<>();
    private final MutableLiveData<User> pendingSender = new MutableLiveData<>();
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();

    private ListenerRegistration requestListenerRegistration;
    private ListenerRegistration friendsListenerRegistration;

    public FriendsViewModel(FriendRepository repository) {
        this.friendRepository = repository;
    }

    public LiveData<List<Friend>> getFriendsList() { return friendsList; }
    public LiveData<User> getSearchedUser() { return searchedUser; }
    public LiveData<User> getPendingSender() { return pendingSender; }
    public LiveData<String> getStatusMessage() { return statusMessage; }

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

    public void sendFriendRequest(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            statusMessage.setValue("Không thể gửi lời mời kết bạn cho chính mình!");
            return;
        }

        friendRepository.sendFriendRequest(currentUserId, targetUserId)
                .addOnSuccessListener(aVoid -> statusMessage.setValue("Đã gửi lời mời kết bạn!"))
                .addOnFailureListener(e -> statusMessage.setValue("Lỗi khi gửi lời mời: " + e.getMessage()));
    }

    public void sendFriendRequestByEmail(String currentUserId, String email) {
        if (email == null || email.trim().isEmpty()) {
            statusMessage.setValue("Vui lòng nhập email người cần kết bạn!");
            return;
        }

        friendRepository.searchAndSendFriendRequest(currentUserId, email.trim())
                .addOnSuccessListener(aVoid -> statusMessage.setValue("Đã gửi lời mời kết bạn thành công!"))
                .addOnFailureListener(e -> statusMessage.setValue(e.getMessage()));
    }

    public void listenForPendingRequests(String currentUserId) {
        if (requestListenerRegistration != null) {
            requestListenerRegistration.remove();
        }

        requestListenerRegistration = friendRepository.listenPendingRequests(currentUserId, (value, error) -> {
            if (error != null || value == null || value.isEmpty()) {
                pendingSender.setValue(null);
                return;
            }

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

    public void acceptRequestAndExchangeKey(String currentUserId, String peerUserId) {
        friendRepository.performKeyExchange(currentUserId, peerUserId)
                .addOnSuccessListener(aBoolean -> statusMessage.setValue("Kết bạn và trao đổi khóa thành công!"))
                .addOnFailureListener(e -> statusMessage.setValue("Lỗi trao đổi khóa: " + e.getMessage()));
    }

    public void declineFriendRequest(String currentUserId, String peerUserId) {
        friendRepository.declineFriendRequest(currentUserId, peerUserId)
                .addOnSuccessListener(aVoid -> statusMessage.setValue("Đã từ chối lời mời kết bạn"))
                .addOnFailureListener(e -> statusMessage.setValue("Lỗi từ chối: " + e.getMessage()));
    }

    public void listenForFriendsList(String currentUserId) {
        if (friendsListenerRegistration != null) {
            friendsListenerRegistration.remove();
        }

        friendsListenerRegistration = friendRepository.listenFriendsList(currentUserId, (value, error) -> {
            if (error != null) {
                statusMessage.postValue("Lỗi tải bạn bè: " + error.getMessage());
                return;
            }

            if (value == null) return;

            List<Friend> list = new ArrayList<>();
            for (DocumentSnapshot doc : value.getDocuments()) {
                Friend friend = doc.toObject(Friend.class);
                if (friend != null) {
                    if (friend.getUid() == null || friend.getUid().isEmpty()) {
                        friend.setUid(doc.getId());
                    }
                    list.add(friend);
                }
            }
            friendsList.postValue(list);
        });
    }

    public void blockFriend(String currentUserId, String peerUserId) {
        friendRepository.blockFriend(currentUserId, peerUserId)
                .addOnSuccessListener(aVoid -> statusMessage.postValue("Đã chặn người dùng"))
                .addOnFailureListener(e -> statusMessage.postValue("Lỗi khi chặn: " + e.getMessage()));
    }

    public void unblockFriend(String currentUserId, String peerUserId) {
        friendRepository.unblockFriend(currentUserId, peerUserId)
                .addOnSuccessListener(aVoid -> statusMessage.postValue("Đã bỏ chặn thành công"))
                .addOnFailureListener(e -> statusMessage.postValue("Lỗi khi bỏ chặn: " + e.getMessage()));
    }

    public void deleteFriend(String currentUserId, String peerUserId) {
        friendRepository.deleteFriend(currentUserId, peerUserId)
                .addOnSuccessListener(aVoid -> statusMessage.postValue("Đã xóa bạn bè khỏi danh bạ"))
                .addOnFailureListener(e -> statusMessage.postValue("Lỗi khi xóa bạn: " + e.getMessage()));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (requestListenerRegistration != null) {
            requestListenerRegistration.remove();
        }
        if (friendsListenerRegistration != null) {
            friendsListenerRegistration.remove();
        }
    }
}