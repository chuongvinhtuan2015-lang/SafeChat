package com.example.safechat.data.repository;

import android.util.Base64;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.safechat.crypto.CryptoManager;
import com.example.safechat.crypto.RatchetManager;
import com.example.safechat.crypto.models.EncryptedMessagePayload;
import com.example.safechat.data.local.db.dao.ConversationDao;
import com.example.safechat.data.local.db.dao.MessageDao;
import com.example.safechat.data.local.db.dao.RatchetSessionDao;
import com.example.safechat.data.local.db.entities.ConversationEntity;
import com.example.safechat.data.local.db.entities.MessageEntity;
import com.example.safechat.data.local.db.entities.RatchetSessionEntity;
import com.example.safechat.data.local.prefs.LocalDataManager;
import com.example.safechat.data.remote.FirestoreDataSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ChatRepository {

    private static final String TAG = "SAFECHAT_CHAT_REPO";

    private final MessageDao messageDao;
    private final ConversationDao conversationDao;
    private final RatchetSessionDao ratchetSessionDao;
    private final FirestoreDataSource firestoreDataSource;
    private final LocalDataManager localDataManager;

    // Executor xử lý các tác vụ đọc/ghi Room Database trên luồng nền
    private final ExecutorService diskExecutor;

    // Quản lý Listener lắng nghe tin nhắn Realtime
    private ListenerRegistration messageListenerRegistration;

    public ChatRepository(MessageDao messageDao,
                          ConversationDao conversationDao,
                          RatchetSessionDao ratchetSessionDao,
                          FirestoreDataSource firestoreDataSource,
                          LocalDataManager localDataManager) {
        this.messageDao = messageDao;
        this.conversationDao = conversationDao;
        this.ratchetSessionDao = ratchetSessionDao;
        this.firestoreDataSource = firestoreDataSource;
        this.localDataManager = localDataManager;
        this.diskExecutor = Executors.newSingleThreadExecutor();
    }

    // =========================================================================
    // 1. TẦNG QUAN SÁT (OBSERVE) CHO GIAO DIỆN
    // =========================================================================

    public LiveData<List<MessageEntity>> getMessagesForConversation(String conversationId) {
        return messageDao.getMessagesByConversation(conversationId);
    }

    public LiveData<List<ConversationEntity>> getConversations() {
        return conversationDao.getAllConversations();
    }

    // =========================================================================
    // 2. LUỒNG GỬI TIN NHẮN (SEND FLOW)
    // =========================================================================

    public void sendMessage(String partnerId, String partnerName, String partnerAvatar,
                            String peerEd25519PublicKey, String plaintext) {
        String currentUserId = localDataManager.getUid();
        if (currentUserId == null || plaintext == null || plaintext.trim().isEmpty()) {
            return;
        }

        String conversationId = getChatId(currentUserId, partnerId);
        String messageId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        MessageEntity localMessage = new MessageEntity(
                messageId,
                conversationId,
                currentUserId,
                partnerId,
                plaintext,
                now,
                true,
                "PENDING"
        );

        diskExecutor.execute(() -> {
            messageDao.insertMessage(localMessage);

            // 1. KHI MÌNH GỬI: Gắn tiền tố "Bạn: " vào lastMessage
            String displayLastMessage = "Bạn: " + plaintext;
            updateLocalConversation(conversationId, partnerId, partnerName, partnerAvatar, displayLastMessage, now, peerEd25519PublicKey);

            try {
                RatchetSessionEntity session = getOrInitRatchetSession(conversationId, currentUserId, partnerId);
                if (session == null) {
                    throw new IllegalStateException("Không tìm thấy khóa bí mật chung ECDH để mã hóa");
                }

                byte[] rawEdPrivateKey = localDataManager.getEd25519PrivateKey();
                if (rawEdPrivateKey == null) {
                    throw new IllegalStateException("Không tìm thấy Private Key Ed25519 trên thiết bị");
                }

                EncryptedMessagePayload payload = RatchetManager.encryptMessage(
                        plaintext,
                        session,
                        currentUserId,
                        partnerId,
                        rawEdPrivateKey
                );

                ratchetSessionDao.saveSession(session);

                firestoreDataSource.sendEncryptedMessage(conversationId, messageId, payload)
                        .addOnSuccessListener(aVoid -> diskExecutor.execute(() -> {
                            messageDao.updateStatus(messageId, "SENT");
                        }))
                        .addOnFailureListener(e -> Log.e(TAG, "Lỗi đẩy Firestore: " + e.getMessage()));

            } catch (Exception e) {
                Log.e(TAG, "Lỗi mã hóa tin nhắn: " + e.getMessage(), e);
            }
        });
    }

    // =========================================================================
    // 3. LUỒNG NHẬN TIN NHẮN (RECEIVE & DECRYPT FLOW)
    // =========================================================================

    public void startListeningMessages(String partnerId, String partnerName, String partnerAvatar, String peerEd25519PublicKeyBase64) {
        stopListeningMessages();

        String currentUserId = localDataManager.getUid();
        if (currentUserId == null || partnerId == null) return;

        String conversationId = getChatId(currentUserId, partnerId);

        messageListenerRegistration = firestoreDataSource.listenNewMessages(conversationId, 0, (value, error) -> {
            if (error != null) {
                Log.e(TAG, "Lỗi lắng nghe tin nhắn: " + error.getMessage());
                return;
            }

            if (value == null || value.isEmpty()) {
                return;
            }

            diskExecutor.execute(() -> {
                for (DocumentSnapshot doc : value.getDocuments()) {
                    EncryptedMessagePayload payload = doc.toObject(EncryptedMessagePayload.class);
                    if (payload == null) continue;

                    String messageId = doc.getId();

                    if (messageDao.getMessageById(messageId) != null) {
                        continue;
                    }

                    try {
                        RatchetSessionEntity session = getOrInitRatchetSession(conversationId, currentUserId, partnerId);
                        if (session == null) continue;

                        boolean isSelf = currentUserId.equals(payload.getSenderId());
                        String decryptedText;

                        if (isSelf) {
                            byte[] myEdPubKeyBytes = localDataManager.getEd25519PublicKeyBytes();
                            if (myEdPubKeyBytes == null && localDataManager.getEd25519PublicKey() != null) {
                                myEdPubKeyBytes = Base64.decode(localDataManager.getEd25519PublicKey(), Base64.NO_WRAP);
                            }

                            decryptedText = RatchetManager.decryptSelfMessage(payload, session, myEdPubKeyBytes);

                        } else {
                            byte[] peerEdPubKeyBytes = Base64.decode(peerEd25519PublicKeyBase64, Base64.NO_WRAP);

                            decryptedText = RatchetManager.decryptMessage(payload, session, peerEdPubKeyBytes);
                        }

                        ratchetSessionDao.saveSession(session);

                        MessageEntity incomingMessage = new MessageEntity(
                                messageId,
                                conversationId,
                                payload.getSenderId(),
                                payload.getReceiverId(),
                                decryptedText,
                                payload.getTimestamp(),
                                isSelf,
                                isSelf ? "SENT" : "DELIVERED"
                        );
                        messageDao.insertMessage(incomingMessage);

                        // 2. KHI NHẬN ĐƯỢC TIN NHẮN MỚI:
                        // Nếu là tin của mình gửi từ máy khác -> "Bạn: "
                        // Nếu là tin của đối phương -> "<Tên đối phương>: "
                        String senderPrefix = isSelf
                                ? "Bạn: "
                                : ((partnerName != null && !partnerName.trim().isEmpty() ? partnerName : "Người dùng") + ": ");

                        String displayLastMessage = senderPrefix + decryptedText;

                        updateLocalConversation(conversationId, partnerId, partnerName, partnerAvatar,
                                displayLastMessage, payload.getTimestamp(), peerEd25519PublicKeyBase64);

                    } catch (Exception e) {
                        Log.e(TAG, "Không thể giải mã tin nhắn " + messageId + ": " + e.getMessage());
                    }
                }
            });
        });
    }

    public void stopListeningMessages() {
        if (messageListenerRegistration != null) {
            messageListenerRegistration.remove();
            messageListenerRegistration = null;
        }
    }

    // =========================================================================
    // 4. KHỞI TẠO VÀ PHỤC HỒI PHIÊN XOAY KHÓA (RATCHET SESSION)
    // =========================================================================

    private RatchetSessionEntity getOrInitRatchetSession(String conversationId, String currentUserId, String partnerId) throws Exception {
        Log.d(TAG, "=== [ECDH Init] Bắt đầu khởi tạo phiên cho conversationId: " + conversationId + " ===");

        RatchetSessionEntity session = ratchetSessionDao.getSession(conversationId);
        if (session != null) {
            Log.d(TAG, "=> Tìm thấy session đã tồn tại trong Room Database cho conversationId: " + conversationId);
            return session;
        }

        String sharedKeyAlias = "shared_key_" + partnerId;
        String sharedSecretBase64 = localDataManager.getEncryptedSecret(sharedKeyAlias);

        if (sharedSecretBase64 == null) {
            Log.w(TAG, "Chưa có sharedSecret ở local. Bắt đầu tính toán lại ECDH qua mạng...");

            // 1. Kiểm tra Private Key của chính mình
            byte[] myPrivateKeyBytes = localDataManager.getX25519PrivateKey();
            if (myPrivateKeyBytes == null) {
                Log.e(TAG, "[ECDH Error 1] Không tìm thấy Private Key X25519 trên thiết bị (localDataManager.getX25519PrivateKey() trả về NULL)!");
                return null;
            }
            Log.d(TAG, "-> Độ dài myPrivateKeyBytes: " + myPrivateKeyBytes.length + " bytes (Chuẩn X25519 là 32 bytes)");

            // 2. Tải Public Key X25519 của đối phương từ Firestore
            String peerPublicKeyX25519 = null;
            try {
                Log.d(TAG, "-> Đang tải document Firestore users/" + partnerId + "...");
                DocumentSnapshot userDoc = Tasks.await(
                        FirebaseFirestore.getInstance().collection("users").document(partnerId).get()
                );
                if (userDoc.exists()) {
                    peerPublicKeyX25519 = userDoc.getString("publicKeyX25519");
                    Log.d(TAG, "-> Tìm thấy userDoc trên Firestore. publicKeyX25519 = " + peerPublicKeyX25519);
                } else {
                    Log.e(TAG, "[ECDH Error 2] Document 'users/" + partnerId + "' KHÔNG TỒN TẠI trên Firestore!");
                }
            } catch (Exception e) {
                Log.e(TAG, "[ECDH Error 3] Lỗi khi kết nối Firestore lấy thông tin partner (" + partnerId + "): " + e.getMessage(), e);
            }

            if (peerPublicKeyX25519 == null || peerPublicKeyX25519.trim().isEmpty()) {
                Log.e(TAG, "[ECDH Error 4] Chuỗi publicKeyX25519 của partner bị NULL hoặc rỗng!");
                return null;
            }

            // 3. Kiểm tra tính hợp lệ và độ dài byte của peerPublicKeyX25519
            try {
                byte[] peerPubBytes = Base64.decode(peerPublicKeyX25519.trim(), Base64.NO_WRAP);
                Log.d(TAG, "-> Decode Base64 peerPublicKeyX25519 thành công: " + peerPubBytes.length + " bytes (Chuẩn X25519 là 32 bytes)");
                if (peerPubBytes.length != 32) {
                    Log.e(TAG, "[ECDH Warning] Khóa của đối phương KHÔNG PHẢI 32 bytes (Đang là " + peerPubBytes.length + " bytes). Có thể bị lưu nhầm định dạng PKCS#8/X.509 hoặc nhầm với Ed25519!");
                }
            } catch (IllegalArgumentException ex) {
                Log.e(TAG, "[ECDH Error 5] peerPublicKeyX25519 không phải là chuỗi Base64 hợp lệ: " + ex.getMessage());
                return null;
            }

            // 4. Thực thi tính toán Shared Secret
            try {
                sharedSecretBase64 = CryptoManager.generateSharedSecret(
                        myPrivateKeyBytes,
                        peerPublicKeyX25519.trim()
                );
            } catch (Exception e) {
                Log.e(TAG, "[ECDH Error 6] CryptoManager.generateSharedSecret ném ngoại lệ: " + e.getMessage(), e);
                return null;
            }

            if (sharedSecretBase64 == null) {
                Log.e(TAG, "[ECDH Error 7] Tính toán generateSharedSecret thất bại (CryptoManager trả về NULL). Hãy kiểm tra stack trace chi tiết bên trong CryptoManager!");
                return null;
            }

            localDataManager.saveEncryptedSecret(sharedKeyAlias, sharedSecretBase64);
            Log.i(TAG, ">>> Đã tự động thiết lập và lưu thành công khóa chung ECDH cho partner: " + partnerId);
        } else {
            Log.d(TAG, "-> Đã có sharedSecret trong localDataManager: " + sharedKeyAlias);
        }

        // 5. Giải mã shared secret và tạo các khóa nhánh (Branch Keys)
        byte[] sharedSecret;
        try {
            sharedSecret = Base64.decode(sharedSecretBase64, Base64.NO_WRAP);
            Log.d(TAG, "-> Giải mã sharedSecretBase64 thành công: " + sharedSecret.length + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "[ECDH Error 8] Không thể decode Base64 sharedSecret: " + e.getMessage(), e);
            return null;
        }

        byte[] sendChainKey;
        byte[] recvChainKey;

        try {
            if (currentUserId.compareTo(partnerId) < 0) {
                sendChainKey = deriveBranchKey(sharedSecret, "Alice_Send_Key");
                recvChainKey = deriveBranchKey(sharedSecret, "Bob_Send_Key");
            } else {
                sendChainKey = deriveBranchKey(sharedSecret, "Bob_Send_Key");
                recvChainKey = deriveBranchKey(sharedSecret, "Alice_Send_Key");
            }
        } catch (Exception e) {
            Log.e(TAG, "[ECDH Error 9] Lỗi khi tạo chuỗi khóa phân nhánh (deriveBranchKey): " + e.getMessage(), e);
            throw e;
        }

        session = new RatchetSessionEntity(
                conversationId,
                sharedSecretBase64,
                Base64.encodeToString(sendChainKey, Base64.NO_WRAP),
                Base64.encodeToString(recvChainKey, Base64.NO_WRAP),
                0
        );

        try {
            ratchetSessionDao.saveSession(session);
            Log.i(TAG, "=== [ECDH Success] Đã lưu thành công RatchetSessionEntity vào Room cho: " + conversationId + " ===");
        } catch (Exception e) {
            Log.e(TAG, "[ECDH Error 10] Lỗi khi lưu session vào Room Database (ratchetSessionDao): " + e.getMessage(), e);
            throw e;
        }

        return session;
    }
    private byte[] deriveBranchKey(byte[] masterKey, String info) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
        return mac.doFinal(info.getBytes(StandardCharsets.UTF_8));
    }

    private void updateLocalConversation(String conversationId, String partnerId, String partnerName,
                                         String partnerAvatar, String lastMessage, long timestamp,
                                         String peerEd25519PublicKey) {
        ConversationEntity conv = new ConversationEntity(
                conversationId,
                partnerId,
                partnerName != null ? partnerName : "Người dùng",
                partnerAvatar,
                lastMessage,
                timestamp,
                peerEd25519PublicKey
        );
        conversationDao.insertOrUpdate(conv);
    }

    public static String getChatId(String uidA, String uidB) {
        return (uidA.compareTo(uidB) < 0) ? (uidA + "_" + uidB) : (uidB + "_" + uidA);
    }
}