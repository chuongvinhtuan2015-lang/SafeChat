package com.example.safechat.crypto.models;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class KeyBundle {
    private final byte[] salt;       // 16 bytes dùng cho PBKDF2
    private final byte[] iv;         // 12 bytes IV dùng cho AES-GCM
    private final byte[] ciphertext; // Dữ liệu khóa bí mật đã mã hóa
    private final byte[] authTag;    // 16 bytes Authentication Tag (GCM)

    public KeyBundle(byte[] salt, byte[] iv, byte[] ciphertext, byte[] authTag) {
        this.salt = salt;
        this.iv = iv;
        this.ciphertext = ciphertext;
        this.authTag = authTag;
    }

    public byte[] getSalt() {
        return salt;
    }

    public byte[] getIv() {
        return iv;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public byte[] getAuthTag() {
        return authTag;
    }

    /**
     * Đóng gói toàn bộ cấu trúc: [Salt (16B) + IV (12B) + AuthTag (16B) + Ciphertext] thành Base64
     */
    public String toBase64String() {
        ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + authTag.length + ciphertext.length);
        buffer.put(salt);
        buffer.put(iv);
        buffer.put(authTag);
        buffer.put(ciphertext);
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    /**
     * Bóc tách chuỗi Base64 thành đối tượng KeyBundle
     */
    public static KeyBundle fromBase64String(String base64Str) throws IllegalArgumentException {
        byte[] rawBytes = Base64.decode(base64Str, Base64.NO_WRAP);

        int saltLen = 16;
        int ivLen = 12;
        int tagLen = 16;
        int minLen = saltLen + ivLen + tagLen;

        if (rawBytes.length < minLen) {
            throw new IllegalArgumentException("Dữ liệu KeyBundle không hợp lệ (độ dài quá ngắn).");
        }

        ByteBuffer buffer = ByteBuffer.wrap(rawBytes);

        byte[] salt = new byte[saltLen];
        buffer.get(salt);

        byte[] iv = new byte[ivLen];
        buffer.get(iv);

        byte[] authTag = new byte[tagLen];
        buffer.get(authTag);

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        return new KeyBundle(salt, iv, ciphertext, authTag);
    }
}