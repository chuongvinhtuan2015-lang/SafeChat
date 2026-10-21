package com.example.safechat.crypto;

import android.util.Base64;

import com.example.safechat.crypto.models.KeyBundle;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoManager {

    private static final String PROVIDER_BC = "BC";
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128; // 16 bytes

    private static final SecureRandom secureRandom = new SecureRandom();

    private CryptoManager() {
        // Utility class
    }

    /**
     * Tạo Shared Secret giữa 2 người dùng qua thuật toán ECDH (X25519)
     */
    public static String generateSharedSecret(byte[] myPrivateKeyBytes, String peerPublicKeyBase64) {
        if (myPrivateKeyBytes == null || peerPublicKeyBase64 == null || peerPublicKeyBase64.isEmpty()) {
            return null;
        }
        try {
            // 1. Giải mã Public Key của đối phương từ Base64
            byte[] peerPublicKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT);
            KeyFactory keyFactory = KeyFactory.getInstance("X25519", PROVIDER_BC);

            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(peerPublicKeyBytes);
            PublicKey peerPublicKey = keyFactory.generatePublic(publicKeySpec);

            // 2. Chuyển đổi Private Key của bản thân
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(myPrivateKeyBytes);
            PrivateKey myPrivateKey = keyFactory.generatePrivate(privateKeySpec);

            // 3. Thực hiện Key Agreement (ECDH / X25519)
            KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519", PROVIDER_BC);
            keyAgreement.init(myPrivateKey);
            keyAgreement.doPhase(peerPublicKey, true);

            // 4. Mảng byte khóa chung thu được -> chuyển sang chuỗi Base64
            byte[] sharedSecretBytes = keyAgreement.generateSecret();
            return Base64.encodeToString(sharedSecretBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sinh cặp khóa X25519 phục vụ trao đổi khóa Diffie-Hellman (ECDH)[cite: 1]
     */
    public static KeyPair generateX25519KeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519", PROVIDER_BC);
        return kpg.generateKeyPair();
    }

    /**
     * Sinh cặp khóa Ed25519 phục vụ ký số và xác thực danh tính[cite: 1]
     */
    public static KeyPair generateEd25519KeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519", PROVIDER_BC);
        return kpg.generateKeyPair();
    }

    /**
     * Dẫn xuất khóa đối xứng AES-256 từ Recovery Code bằng PBKDF2WithHmacSHA256[cite: 1]
     */
    public static SecretKey deriveKeyFromRecoveryCode(String recoveryCode, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(recoveryCode.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Đóng gói Private Keys (X25519 & Ed25519) bằng AES-256-GCM dựa trên mã khôi phục[cite: 1]
     */
    public static String encryptKeyBundle(byte[] x25519Priv, byte[] ed25519Priv, String recoveryCode) throws GeneralSecurityException {
        // 1. Sinh Salt và IV ngẫu nhiên[cite: 1]
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        // 2. Dẫn xuất khóa AES-256[cite: 1]
        SecretKey aesKey = deriveKeyFromRecoveryCode(recoveryCode, salt);

        // 3. Gom 2 khóa bí mật thành gói nhị phân có tiền tố độ dài[cite: 1]
        ByteBuffer buffer = ByteBuffer.allocate(4 + x25519Priv.length + 4 + ed25519Priv.length);
        buffer.putInt(x25519Priv.length);
        buffer.put(x25519Priv);
        buffer.putInt(ed25519Priv.length);
        buffer.put(ed25519Priv);
        byte[] plaintext = buffer.array();

        // 4. Mã hóa bằng AES-GCM[cite: 1]
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, parameterSpec);
        byte[] cipherWithTag = cipher.doFinal(plaintext);

        // 5. Tách Ciphertext và AuthTag (16 bytes cuối)
        int tagLenBytes = GCM_TAG_LENGTH_BITS / 8;
        int cipherTextLen = cipherWithTag.length - tagLenBytes;

        byte[] ciphertext = Arrays.copyOfRange(cipherWithTag, 0, cipherTextLen);
        byte[] authTag = Arrays.copyOfRange(cipherWithTag, cipherTextLen, cipherWithTag.length);

        // 6. Trả về định dạng Base64 qua KeyBundle[cite: 1]
        KeyBundle bundle = new KeyBundle(salt, iv, ciphertext, authTag);
        return bundle.toBase64String();
    }

    /**
     * Lớp POJO chứa 2 Private Keys sau khi bóc tách thành công
     */
    public static class DecryptedPrivateKeys {
        public final byte[] x25519PrivateKey;
        public final byte[] ed25519PrivateKey;

        public DecryptedPrivateKeys(byte[] x25519PrivateKey, byte[] ed25519PrivateKey) {
            this.x25519PrivateKey = x25519PrivateKey;
            this.ed25519PrivateKey = ed25519PrivateKey;
        }
    }

    /**
     * Giải mã chuỗi Base64 gói khóa bằng Recovery Code (Mô hình Zero-Knowledge)[cite: 1]
     */
    public static DecryptedPrivateKeys decryptKeyBundle(String encryptedBundleBase64, String recoveryCode) throws GeneralSecurityException {
        // 1. Phân rã chuỗi Base64[cite: 1]
        KeyBundle bundle = KeyBundle.fromBase64String(encryptedBundleBase64);

        // 2. Dẫn xuất lại khóa AES-256 từ mã khôi phục và Salt lưu kèm[cite: 1]
        SecretKey aesKey = deriveKeyFromRecoveryCode(recoveryCode, bundle.getSalt());

        // 3. Nối Ciphertext và AuthTag để đưa vào giải mã AES-GCM
        ByteBuffer cipherBuffer = ByteBuffer.allocate(bundle.getCiphertext().length + bundle.getAuthTag().length);
        cipherBuffer.put(bundle.getCiphertext());
        cipherBuffer.put(bundle.getAuthTag());
        byte[] cipherWithTag = cipherBuffer.array();

        // 4. Giải mã và kiểm tra tính toàn vẹn (Ném AEADBadTagException nếu sai mã khôi phục)[cite: 1]
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, bundle.getIv());
        cipher.init(Cipher.DECRYPT_MODE, aesKey, parameterSpec);
        byte[] decryptedPlaintext = cipher.doFinal(cipherWithTag);

        // 5. Tách 2 khóa bí mật ra từ mảng byte
        ByteBuffer buffer = ByteBuffer.wrap(decryptedPlaintext);

        int x25519Len = buffer.getInt();
        byte[] x25519Priv = new byte[x25519Len];
        buffer.get(x25519Priv);

        int ed25519Len = buffer.getInt();
        byte[] ed25519Priv = new byte[ed25519Len];
        buffer.get(ed25519Priv);

        return new DecryptedPrivateKeys(x25519Priv, ed25519Priv);
    }

    /**
     * Helper chuyển đổi mảng byte sang Base64 chuỗi chuẩn
     */
    public static String toBase64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    /**
     * Helper chuyển đổi Base64 chuỗi về mảng byte
     */
    public static byte[] fromBase64(String base64Str) {
        return Base64.decode(base64Str, Base64.NO_WRAP);
    }
}