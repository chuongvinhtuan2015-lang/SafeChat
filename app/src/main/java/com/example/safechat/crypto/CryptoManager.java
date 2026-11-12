package com.example.safechat.crypto;

import android.util.Base64;

import com.example.safechat.crypto.models.KeyBundle;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
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

    // ASN.1 DER Header cho X.509 SubjectPublicKeyInfo (12 bytes)
    private static final byte[] ED25519_X509_HEADER = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final byte[] X25519_X509_HEADER = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
    };

    // ASN.1 DER Header cho PKCS#8 PrivateKeyInfo (16 bytes)
    private static final byte[] ED25519_PKCS8_HEADER = new byte[]{
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    };
    private static final byte[] X25519_PKCS8_HEADER = new byte[]{
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22, 0x04, 0x20
    };

    private static final SecureRandom secureRandom = new SecureRandom();

    private CryptoManager() {
        // Utility class
    }

    /**
     * Tạo Shared Secret giữa 2 người dùng qua thuật toán ECDH (X25519)
     * Hỗ trợ tự động chuẩn hóa cả định dạng Raw (32 bytes) lẫn X.509 (44 bytes)
     */
    public static String generateSharedSecret(byte[] myPrivateKeyBytes, String peerPublicKeyBase64) {
        if (myPrivateKeyBytes == null || peerPublicKeyBase64 == null || peerPublicKeyBase64.trim().isEmpty()) {
            return null;
        }
        try {
            // 1. Chuẩn hóa Public Key: rút về raw 32 bytes rồi bọc đúng 44 bytes X.509
            byte[] peerPublicKeyBytes = fromBase64(peerPublicKeyBase64.trim());
            byte[] rawPeerPub = extractRaw32Bytes(peerPublicKeyBytes);
            byte[] x509Bytes = toX509Encoded(rawPeerPub, false);

            KeyFactory keyFactory = getKeyFactory("X25519");
            PublicKey peerPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(x509Bytes));

            // 2. Chuẩn hóa Private Key: rút về raw 32 bytes rồi bọc đúng header PKCS#8 48 bytes
            byte[] rawMyPriv = extractRaw32Bytes(myPrivateKeyBytes);
            byte[] pkcs8Bytes = toPkcs8Encoded(rawMyPriv, false);
            PrivateKey myPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));

            // 3. Thực hiện Key Agreement (ECDH / X25519)
            KeyAgreement keyAgreement = getKeyAgreement("X25519");
            keyAgreement.init(myPrivateKey);
            keyAgreement.doPhase(peerPublicKey, true);

            // 4. Mảng byte khóa chung thu được -> Base64
            byte[] sharedSecretBytes = keyAgreement.generateSecret();
            return toBase64(sharedSecretBytes);
        } catch (Exception e) {
            android.util.Log.e("CRYPTO_MANAGER", "Chi tiết lỗi ECDH: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Ký dữ liệu bằng Private Key Ed25519
     */

    /**
     * Xác thực chữ ký số Ed25519
     * Tự động xử lý cả khi Public Key là 44 bytes (X.509) hoặc 32 bytes (Raw)
     */
    public static boolean verifySignature(byte[] data, byte[] signatureBytes, byte[] publicKeyBytes) {
        if (data == null || signatureBytes == null || publicKeyBytes == null) {
            return false;
        }
        try {
            KeyFactory keyFactory = getKeyFactory("Ed25519");
            byte[] x509Bytes = toX509Encoded(publicKeyBytes, true);
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(x509Bytes));

            Signature signature = getSignatureInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean verifySignature(byte[] data, byte[] signatureBytes, String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.trim().isEmpty()) {
            return false;
        }
        return verifySignature(data, signatureBytes, fromBase64(publicKeyBase64));
    }



    /**
     * Sinh cặp khóa X25519 phục vụ trao đổi khóa Diffie-Hellman (ECDH)
     */
    public static KeyPair generateX25519KeyPair() throws GeneralSecurityException {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519", PROVIDER_BC);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        }
    }

    /**
     * Sinh cặp khóa Ed25519 phục vụ ký số và xác thực danh tính
     */
    public static KeyPair generateEd25519KeyPair() throws GeneralSecurityException {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519", PROVIDER_BC);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        }
    }

    /**
     * Dẫn xuất khóa đối xứng AES-256 từ Recovery Code bằng PBKDF2WithHmacSHA256
     */
    public static SecretKey deriveKeyFromRecoveryCode(String recoveryCode, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(recoveryCode.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Đóng gói Private Keys (X25519 & Ed25519) bằng AES-256-GCM dựa trên mã khôi phục
     */
    public static String encryptKeyBundle(byte[] x25519Priv, byte[] ed25519Priv, String recoveryCode) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        SecretKey aesKey = deriveKeyFromRecoveryCode(recoveryCode, salt);

        ByteBuffer buffer = ByteBuffer.allocate(4 + x25519Priv.length + 4 + ed25519Priv.length);
        buffer.putInt(x25519Priv.length);
        buffer.put(x25519Priv);
        buffer.putInt(ed25519Priv.length);
        buffer.put(ed25519Priv);
        byte[] plaintext = buffer.array();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, parameterSpec);
        byte[] cipherWithTag = cipher.doFinal(plaintext);

        int tagLenBytes = GCM_TAG_LENGTH_BITS / 8;
        int cipherTextLen = cipherWithTag.length - tagLenBytes;

        byte[] ciphertext = Arrays.copyOfRange(cipherWithTag, 0, cipherTextLen);
        byte[] authTag = Arrays.copyOfRange(cipherWithTag, cipherTextLen, cipherWithTag.length);

        KeyBundle bundle = new KeyBundle(salt, iv, ciphertext, authTag);
        return bundle.toBase64String();
    }

    public static class DecryptedPrivateKeys {
        public final byte[] x25519PrivateKey;
        public final byte[] ed25519PrivateKey;

        public DecryptedPrivateKeys(byte[] x25519PrivateKey, byte[] ed25519PrivateKey) {
            this.x25519PrivateKey = x25519PrivateKey;
            this.ed25519PrivateKey = ed25519PrivateKey;
        }
    }

    /**
     * Giải mã gói khóa bằng Recovery Code (Mô hình Zero-Knowledge)
     */
    public static DecryptedPrivateKeys decryptKeyBundle(String encryptedBundleBase64, String recoveryCode) throws GeneralSecurityException {
        KeyBundle bundle = KeyBundle.fromBase64String(encryptedBundleBase64);
        SecretKey aesKey = deriveKeyFromRecoveryCode(recoveryCode, bundle.getSalt());

        ByteBuffer cipherBuffer = ByteBuffer.allocate(bundle.getCiphertext().length + bundle.getAuthTag().length);
        cipherBuffer.put(bundle.getCiphertext());
        cipherBuffer.put(bundle.getAuthTag());
        byte[] cipherWithTag = cipherBuffer.array();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, bundle.getIv());
        cipher.init(Cipher.DECRYPT_MODE, aesKey, parameterSpec);
        byte[] decryptedPlaintext = cipher.doFinal(cipherWithTag);

        ByteBuffer buffer = ByteBuffer.wrap(decryptedPlaintext);

        int x25519Len = buffer.getInt();
        byte[] x25519Priv = new byte[x25519Len];
        buffer.get(x25519Priv);

        int ed25519Len = buffer.getInt();
        byte[] ed25519Priv = new byte[ed25519Len];
        buffer.get(ed25519Priv);

        return new DecryptedPrivateKeys(x25519Priv, ed25519Priv);
    }

    // --- CÁC HÀM TIỆN ÍCH CHUẨN HÓA KEY & FALLBACK PROVIDER ---

    public static byte[] toX509Encoded(byte[] keyBytes, boolean isEd25519) {
        if (keyBytes == null) return null;
        if (keyBytes.length == 44) {
            return keyBytes; // Đã có sẵn header X.509
        }
        if (keyBytes.length == 32) {
            byte[] header = isEd25519 ? ED25519_X509_HEADER : X25519_X509_HEADER;
            byte[] x509 = new byte[header.length + 32];
            System.arraycopy(header, 0, x509, 0, header.length);
            System.arraycopy(keyBytes, 0, x509, header.length, 32);
            return x509;
        }
        return keyBytes;
    }

    public static byte[] toPkcs8Encoded(byte[] keyBytes, boolean isEd25519) {
        if (keyBytes == null) return null;
        if (keyBytes.length == 48) {
            return keyBytes; // Đã có sẵn header PKCS#8
        }
        if (keyBytes.length == 32) {
            byte[] header = isEd25519 ? ED25519_PKCS8_HEADER : X25519_PKCS8_HEADER;
            byte[] pkcs8 = new byte[header.length + 32];
            System.arraycopy(header, 0, pkcs8, 0, header.length);
            System.arraycopy(keyBytes, 0, pkcs8, header.length, 32);
            return pkcs8;
        }
        return keyBytes;
    }

    public static byte[] extractRaw32Bytes(byte[] keyBytes) {
        if (keyBytes == null) return null;

        // Đã là raw 32 bytes chuẩn
        if (keyBytes.length == 32) return keyBytes;

        // Public Key X.509 DER (44 bytes): 12 bytes header + 32 bytes key
        if (keyBytes.length == 44) {
            return Arrays.copyOfRange(keyBytes, 12, 44);
        }

        // Private Key PKCS#8 v1 (48 bytes) hoặc PKCS#8 v2 (83 bytes):
        // Seed 32 bytes luôn nằm chính xác ở vị trí [16 đến 48]
        if (keyBytes.length == 48 || keyBytes.length == 83) {
            return Arrays.copyOfRange(keyBytes, 16, 48);
        }

        return keyBytes;
    }

    private static KeyFactory getKeyFactory(String algorithm) throws GeneralSecurityException {
        try {
            return KeyFactory.getInstance(algorithm, PROVIDER_BC);
        } catch (Exception e) {
            return KeyFactory.getInstance(algorithm);
        }
    }

    private static KeyAgreement getKeyAgreement(String algorithm) throws GeneralSecurityException {
        try {
            return KeyAgreement.getInstance(algorithm, PROVIDER_BC);
        } catch (Exception e) {
            return KeyAgreement.getInstance(algorithm);
        }
    }

    private static Signature getSignatureInstance(String algorithm) throws GeneralSecurityException {
        try {
            return Signature.getInstance(algorithm, PROVIDER_BC);
        } catch (Exception e) {
            return Signature.getInstance(algorithm);
        }
    }

    public static String toBase64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] fromBase64(String base64Str) {
        return Base64.decode(base64Str, Base64.NO_WRAP);
    }
}