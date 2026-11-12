package com.example.safechat.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.safechat.data.local.db.entities.MessageEntity;
import com.example.safechat.data.repository.ChatRepository;

import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository chatRepository;
    private LiveData<List<MessageEntity>> messagesLiveData;
    private String peerEd25519PublicKey;

    public ChatViewModel(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    /**
     * Bắt đầu phiên trò chuyện: quan sát Local DB và kích hoạt lắng nghe Firebase Realtime
     */
    public void initChat(String currentUserId, String partnerId, String partnerName,
                         String partnerAvatar, String peerEd25519PublicKey) {
        this.peerEd25519PublicKey = peerEd25519PublicKey;

        String conversationId = ChatRepository.getChatId(currentUserId, partnerId);
        messagesLiveData = chatRepository.getMessagesForConversation(conversationId);

        // Kích hoạt lắng nghe tin nhắn mới từ Firestore
        chatRepository.startListeningMessages(partnerId, partnerName, partnerAvatar, peerEd25519PublicKey);
    }

    public LiveData<List<MessageEntity>> getMessagesLiveData() {
        return messagesLiveData;
    }

    /**
     * Gửi tin nhắn mới (truyền đủ 5 tham số sang ChatRepository)
     */
    public void sendMessage(String partnerId, String partnerName, String partnerAvatar, String text) {
        if (text == null || text.trim().isEmpty()) return;
        chatRepository.sendMessage(partnerId, partnerName, partnerAvatar, this.peerEd25519PublicKey, text.trim());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Hủy listener Firebase để tiết kiệm tài nguyên và pin
        chatRepository.stopListeningMessages();
    }
    public void stopListening() {
        chatRepository.stopListeningMessages();
    }
}