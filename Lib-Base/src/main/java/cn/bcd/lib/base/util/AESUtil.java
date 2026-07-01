package cn.bcd.lib.base.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class AESUtil {

    private AESUtil() {
    }

    private static final String AES = "AES";

    private static final String AES_CBC = "AES/CBC/PKCS5Padding";
    private static final int CBC_IV_LENGTH = 16;

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BIT = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成 AES 密钥
     *
     * @param keySize 支持 128、192、256
     */
    public static byte[] generateKey(int keySize) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES);
            keyGenerator.init(keySize);
            SecretKey secretKey = keyGenerator.generateKey();
            return secretKey.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Generate AES key failed", e);
        }
    }

    /**
     * AES-CBC 加密
     * <p>
     * 返回格式：
     * IV + Ciphertext
     */
    public static byte[] encryptCbc(byte[] plaintext, byte[] key) {
        try {
            checkAesKey(key);

            byte[] iv = randomBytes(CBC_IV_LENGTH);

            Cipher cipher = Cipher.getInstance(AES_CBC);
            SecretKeySpec keySpec = new SecretKeySpec(key, AES);
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            return concat(iv, ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("AES-CBC encrypt failed", e);
        }
    }

    /**
     * AES-CBC 解密
     * <p>
     * 输入格式：
     * IV + Ciphertext
     */
    public static byte[] decryptCbc(byte[] encrypted, byte[] key) {
        try {
            checkAesKey(key);

            if (encrypted == null || encrypted.length <= CBC_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid AES-CBC encrypted data");
            }

            byte[] iv = Arrays.copyOfRange(encrypted, 0, CBC_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, CBC_IV_LENGTH, encrypted.length);

            Cipher cipher = Cipher.getInstance(AES_CBC);
            SecretKeySpec keySpec = new SecretKeySpec(key, AES);
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("AES-CBC decrypt failed", e);
        }
    }

    /**
     * AES-GCM 加密
     * <p>
     * 返回格式：
     * IV + Ciphertext + Tag
     * <p>
     * 注意：
     * Java 的 AES/GCM/NoPadding 中，doFinal 返回的是 Ciphertext + Tag。
     */
    public static byte[] encryptGcm(byte[] plaintext, byte[] key, byte[] aad) {
        try {
            checkAesKey(key);

            byte[] iv = randomBytes(GCM_IV_LENGTH);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(key, AES);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            byte[] ciphertextWithTag = cipher.doFinal(plaintext);

            return concat(iv, ciphertextWithTag);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    /**
     * AES-GCM 解密
     * <p>
     * 输入格式：
     * IV + Ciphertext + Tag
     */
    public static byte[] decryptGcm(byte[] encrypted, byte[] key, byte[] aad) {
        try {
            checkAesKey(key);

            if (encrypted == null || encrypted.length <= GCM_IV_LENGTH + 16) {
                throw new IllegalArgumentException("Invalid AES-GCM encrypted data");
            }

            byte[] iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_LENGTH);
            byte[] ciphertextWithTag = Arrays.copyOfRange(encrypted, GCM_IV_LENGTH, encrypted.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            SecretKeySpec keySpec = new SecretKeySpec(key, AES);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            return cipher.doFinal(ciphertextWithTag);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }

    /**
     * AES-CBC 加密字符串，返回 Base64
     */
    public static String encryptCbcToBase64(String plaintext, byte[] key) {
        byte[] encrypted = encryptCbc(plaintext.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES-CBC 解密 Base64 字符串
     */
    public static String decryptCbcFromBase64(String base64Encrypted, byte[] key) {
        byte[] encrypted = Base64.getDecoder().decode(base64Encrypted);
        byte[] plaintext = decryptCbc(encrypted, key);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * AES-GCM 加密字符串，返回 Base64
     */
    public static String encryptGcmToBase64(String plaintext, byte[] key, byte[] aad) {
        byte[] encrypted = encryptGcm(plaintext.getBytes(StandardCharsets.UTF_8), key, aad);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES-GCM 解密 Base64 字符串
     */
    public static String decryptGcmFromBase64(String base64Encrypted, byte[] key, byte[] aad) {
        byte[] encrypted = Base64.getDecoder().decode(base64Encrypted);
        byte[] plaintext = decryptGcm(encrypted, key, aad);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void checkAesKey(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("AES key must not be null");
        }

        int length = key.length;
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalArgumentException("AES key length must be 16, 24 or 32 bytes");
        }
    }

    public static void main(String[] args) {
        byte[] key = AESUtil.generateKey(256);

        String plaintext = "hello AES 加密";

        String cbcEncrypted = AESUtil.encryptCbcToBase64(plaintext, key);
        String cbcDecrypted = AESUtil.decryptCbcFromBase64(cbcEncrypted, key);

        System.out.println("CBC encrypted: " + cbcEncrypted);
        System.out.println("CBC decrypted: " + cbcDecrypted);

        byte[] aad = "vehicleId=123456".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String gcmEncrypted = AESUtil.encryptGcmToBase64(plaintext, key, aad);
        String gcmDecrypted = AESUtil.decryptGcmFromBase64(gcmEncrypted, key, aad);

        System.out.println("GCM encrypted: " + gcmEncrypted);
        System.out.println("GCM decrypted: " + gcmDecrypted);
    }
}