package com.example.safechat.crypto;

import android.util.Base64;

import com.example.safechat.crypto.models.EncryptedMessagePayload;
import com.example.safechat.data.local.db.entities.RatchetSessionEntity;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class RatchetManager {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // 16 bytes (128 bits)
    private static final int IV_LENGTH = 12;       // 12 bytes chuẩn AES-GCM

    // Hằng số phân tách miền cho hàm dẫn xuất KDF (Domain Separation)
    private static final byte[] CONSTANT_MESSAGE_KEY = new byte[]{0x01};
    private static final byte[] CONSTANT_CHAIN_KEY = new byte[]{0x02};

    // =========================================================================
    // 1. DẪN XUẤT KHÓA KDF CHAIN (HMAC-SHA256)
    // =========================================================================

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(data);
    }

    /**
     * Thực hiện 1 bước nhảy KDF Ratchet:
     * Trả về mảng 2 chiều gồm: [0] = NextChainKey (32 bytes), [1] = MessageKey (32 bytes)
     */
    public static byte[][] stepKdfChain(byte[] currentChainKey) throws Exception {
        byte[] messageKey = hmac(currentChainKey, CONSTANT_MESSAGE_KEY);
        byte[] nextChainKey = hmac(currentChainKey, CONSTANT_CHAIN_KEY);
        return new byte[][]{nextChainKey, messageKey};
    }

    // =========================================================================
    // 2. MÃ HÓA TIN NHẮN (GỬI ĐI)
    // =========================================================================

    public static synchronized EncryptedMessagePayload encryptMessage(
            String plaintext,
            RatchetSessionEntity session,
            String senderId,
            String receiverId,
            byte[] myEd25519PrivateKeyBytes
    ) throws Exception {
        byte[] currentChainKey = Base64.decode(session.getSendChainKey(), Base64.NO_WRAP);

        // 1. Bước nhảy KDF: sinh MessageKey và NextChainKey
        byte[][] derived = stepKdfChain(currentChainKey);
        byte[] nextChainKey = derived[0];
        byte[] messageKey = derived[1];

        // 2. Sinh IV ngẫu nhiên 12 bytes
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        // 3. Mã hóa bằng AES-256-GCM
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(messageKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // 4. Ký chữ ký số Ed25519 lên dữ liệu (Ciphertext + IV + KeyIndex)
        int keyIndex = session.getCurrentKeyIndex();
        byte[] dataToSign = buildDataToAuthenticate(ciphertext, iv, keyIndex);
        byte[] signature = signEd25519(dataToSign, myEd25519PrivateKeyBytes);

        // 5. Cập nhật trạng thái chuỗi khóa cho phiên
        session.setSendChainKey(Base64.encodeToString(nextChainKey, Base64.NO_WRAP));
        session.setCurrentKeyIndex(keyIndex + 1);

        // 6. Xóa sạch MessageKey khỏi RAM ngay lập tức (Forward Secrecy)
        Arrays.fill(messageKey, (byte) 0);

        return new EncryptedMessagePayload(
                Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP),
                Base64.encodeToString(signature, Base64.NO_WRAP),
                keyIndex,
                senderId,
                receiverId,
                System.currentTimeMillis()
        );
    }

    // =========================================================================
    // 3. GIẢI MÃ TIN NHẮN ĐỐI PHƯƠNG (NHẬN VỀ TỪ PEER)
    // =========================================================================

    public static synchronized String decryptMessage(
            EncryptedMessagePayload payload,
            RatchetSessionEntity session,
            byte[] peerEd25519PublicKeyBytes
    ) throws Exception {
        byte[] ciphertext = Base64.decode(payload.getCiphertext(), Base64.NO_WRAP);
        byte[] iv = Base64.decode(payload.getIv(), Base64.NO_WRAP);
        byte[] signature = Base64.decode(payload.getSignature(), Base64.NO_WRAP);

        // 1. Xác thực chữ ký số Ed25519 bằng Public Key của BẠN BÈ
        byte[] dataToVerify = buildDataToAuthenticate(ciphertext, iv, payload.getKeyIndex());
        boolean isValid = verifyEd25519(dataToVerify, signature, peerEd25519PublicKeyBytes);
        if (!isValid) {
            throw new SecurityException("Chữ ký số Ed25519 không hợp lệ! Tin nhắn đối phương có dấu hiệu bị giả mạo.");
        }

        // 2. Bước nhảy KDF chuỗi nhận (RecvChainKey)
        byte[] currentRecvChainKey = Base64.decode(session.getRecvChainKey(), Base64.NO_WRAP);
        byte[][] derived = stepKdfChain(currentRecvChainKey);
        byte[] nextRecvChainKey = derived[0];
        byte[] messageKey = derived[1];

        // 3. Giải mã AES-256-GCM
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(messageKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] decryptedBytes = cipher.doFinal(ciphertext);

        // 4. Lưu lại RecvChainKey mới vào phiên
        session.setRecvChainKey(Base64.encodeToString(nextRecvChainKey, Base64.NO_WRAP));

        // 5. Xóa sạch MessageKey khỏi RAM
        Arrays.fill(messageKey, (byte) 0);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    // =========================================================================
    // 4. GIẢI MÃ TIN NHẮN CỦA CHÍNH MÌNH (ĐỒNG BỘ NẤC XOAY TRÊN MÁY MỚI)
    // =========================================================================

    /**
     * Dùng khi đăng nhập máy mới để tải lại tin nhắn do chính mình gửi từ máy cũ.
     * Sử dụng SendChainKey để giải mã và đồng bộ nấc xoay SendChain lên vị trí mới nhất.
     */
    public static synchronized String decryptSelfMessage(
            EncryptedMessagePayload payload,
            RatchetSessionEntity session,
            byte[] myEd25519PublicKeyBytes
    ) throws Exception {
        byte[] ciphertext = Base64.decode(payload.getCiphertext(), Base64.NO_WRAP);
        byte[] iv = Base64.decode(payload.getIv(), Base64.NO_WRAP);
        byte[] signature = Base64.decode(payload.getSignature(), Base64.NO_WRAP);

        // 1. Xác thực chữ ký số Ed25519 bằng Public Key của CHÍNH MÌNH
        byte[] dataToVerify = buildDataToAuthenticate(ciphertext, iv, payload.getKeyIndex());
        boolean isValid = verifyEd25519(dataToVerify, signature, myEd25519PublicKeyBytes);
        if (!isValid) {
            throw new SecurityException("Chữ ký số Ed25519 của chính mình không hợp lệ!");
        }

        // 2. Bước nhảy KDF chuỗi gửi (SendChainKey)
        byte[] currentSendChainKey = Base64.decode(session.getSendChainKey(), Base64.NO_WRAP);
        byte[][] derived = stepKdfChain(currentSendChainKey);
        byte[] nextSendChainKey = derived[0];
        byte[] messageKey = derived[1];

        // 3. Giải mã AES-256-GCM
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(messageKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] decryptedBytes = cipher.doFinal(ciphertext);

        // 4. Cập nhật nấc xoay SendChainKey và Index mới nhất vào session
        session.setSendChainKey(Base64.encodeToString(nextSendChainKey, Base64.NO_WRAP));
        session.setCurrentKeyIndex(payload.getKeyIndex() + 1);

        // 5. Xóa sạch MessageKey khỏi RAM
        Arrays.fill(messageKey, (byte) 0);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    // =========================================================================
    // 5. BỘ CHUYỂN ĐỔI VÀ TIỆN ÍCH CHỮ KÝ ED25519 (BOUNCY CASTLE)
    // =========================================================================

    // =========================================================================
    // TIỆN ÍCH CHUYỂN ĐỔI KHÓA ED25519 (TỐI ƯU CHO CHUẨN 44 BYTES / 48 BYTES)
    // =========================================================================

    public static Ed25519PrivateKeyParameters parseEd25519PrivateKey(byte[] privateKeyBytes) {
        if (privateKeyBytes == null) {
            throw new IllegalArgumentException("Private key không được null");
        }
        // 48 bytes (PKCS#8 Java) -> nhảy qua 16 bytes header để lấy 32 bytes seed
        // 32 bytes (Raw seed)    -> đọc từ vị trí 0
        int offset = (privateKeyBytes.length == 48 || privateKeyBytes.length == 83) ? 16 : 0;
        if (privateKeyBytes.length == 48 || privateKeyBytes.length == 83 || privateKeyBytes.length == 32) {
            return new Ed25519PrivateKeyParameters(privateKeyBytes, offset);
        }
        throw new IllegalArgumentException("Độ dài Private Key không hợp lệ: " + privateKeyBytes.length);
    }

    public static Ed25519PublicKeyParameters parseEd25519PublicKey(byte[] publicKeyBytes) {
        if (publicKeyBytes == null) {
            throw new IllegalArgumentException("Public key không được null");
        }
        // 44 bytes (X.509 Java) -> nhảy qua 12 bytes header để lấy 32 bytes raw key
        // 32 bytes (Raw key)    -> đọc từ vị trí 0
        int offset = (publicKeyBytes.length == 44) ? 12 : 0;
        if (publicKeyBytes.length == 44 || publicKeyBytes.length == 32) {
            return new Ed25519PublicKeyParameters(publicKeyBytes, offset);
        }
        throw new IllegalArgumentException("Độ dài Public Key không hợp lệ: " + publicKeyBytes.length);
    }

    private static byte[] signEd25519(byte[] data, byte[] privateKeyBytes) {
        Ed25519PrivateKeyParameters privKeyParams = parseEd25519PrivateKey(privateKeyBytes);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privKeyParams);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    private static boolean verifyEd25519(byte[] data, byte[] signature, byte[] publicKeyBytes) {
        Ed25519PublicKeyParameters pubKeyParams = parseEd25519PublicKey(publicKeyBytes);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, pubKeyParams);
        verifier.update(data, 0, data.length);
        return verifier.verifySignature(signature);
    }

    private static byte[] buildDataToAuthenticate(byte[] ciphertext, byte[] iv, int keyIndex) {
        byte[] indexBytes = String.valueOf(keyIndex).getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[ciphertext.length + iv.length + indexBytes.length];
        System.arraycopy(ciphertext, 0, result, 0, ciphertext.length);
        System.arraycopy(iv, 0, result, ciphertext.length, iv.length);
        System.arraycopy(indexBytes, 0, result, ciphertext.length + iv.length, indexBytes.length);
        return result;
    }
}