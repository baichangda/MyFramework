package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSAUtil {

    /**
     * 算法/模式/补码方式
     */
    public final static String PADDING_TYPE = "RSA/ECB/PKCS1Padding";


    public record RsaKeyPair(PublicKey publicKey, PrivateKey privateKey) {
        public byte[] getPublicKeyBytes() {
            return RSAUtil.publicKeyToBytes(publicKey);
        }

        public byte[] getPrivateKeyBytes() {
            return RSAUtil.privateKeyToBytes(privateKey);
        }
    }

    /**
     * 生成指定模数位数的RSA密钥对。
     * 每次调用使用独立的生成器，避免并发调用时相互覆盖密钥长度等配置。
     *
     * @param size RSA模数位数，取值须符合当前安全提供者的限制
     * @return 包含公钥和私钥的密钥对
     * @throws InvalidParameterException 密钥位数不符合安全提供者要求时抛出
     * @throws BaseException 当前环境不支持RSA密钥生成算法时抛出
     */
    public static RsaKeyPair generateKey(int size) {
        try {
            // 生成器带有可变配置，只在当前调用中使用。
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(size);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RsaKeyPair(publicKey, privateKey);
        } catch (NoSuchAlgorithmException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 还原公钥，X509EncodedKeySpec 用于构建公钥的规范
     *
     * @param keyBytes
     * @return
     */
    public static PublicKey publicKeyFromBytes(byte[] keyBytes) {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyBytes);
        try {
            return Singleton.INSTANCE.keyFactory.generatePublic(x509EncodedKeySpec);
        } catch (InvalidKeySpecException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 还原私钥，PKCS8EncodedKeySpec 用于构建私钥的规范
     *
     * @param keyBytes
     * @return
     */
    public static PrivateKey privateKeyFromBytes(byte[] keyBytes) {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(
                keyBytes);
        try {
            return Singleton.INSTANCE.keyFactory
                    .generatePrivate(pkcs8EncodedKeySpec);
        } catch (InvalidKeySpecException e) {
            throw BaseException.get(e);
        }
    }

    public static byte[] publicKeyToBytes(PublicKey publicKey) {
        return publicKey.getEncoded();
    }

    public static byte[] privateKeyToBytes(PrivateKey privateKey) {
        return privateKey.getEncoded();
    }

    /**
     * 加密，三步走。
     *
     * @param key
     * @param plainText
     * @return
     */
    public static byte[] encode(PublicKey key, byte[] plainText) {
        try {
            Cipher cipher = Cipher.getInstance(PADDING_TYPE);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(plainText);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                 | InvalidKeyException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw BaseException.get(e);
        }

    }

    /**
     * 使用私钥解密单块RSA密文，原样返回明文字节，不进行字符集转换。
     * 文本调用方应使用与加密前一致的字符集将返回值转换为字符串。
     *
     * @param key RSA私钥
     * @param encodedText 待解密的密文字节
     * @return 解密后的原始明文字节
     * @throws BaseException 密钥无效、密文无效或解密失败时抛出
     */
    public static byte[] decode(PrivateKey key, byte[] encodedText) {
        try {
            Cipher cipher = Cipher.getInstance(PADDING_TYPE);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(encodedText);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                 | InvalidKeyException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw BaseException.get(e);
        }
    }

    public static void main(String[] args) {
        RsaKeyPair rsaKeyPair1 = generateKey(1024);
        PublicKey publicKey1 = rsaKeyPair1.publicKey;
        PrivateKey privateKey1 = rsaKeyPair1.privateKey;
        System.out.println(Base64.getEncoder().encodeToString(publicKey1.getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(privateKey1.getEncoded()));
        RsaKeyPair rsaKeyPair2 = generateKey(1024);
        PublicKey publicKey2 = rsaKeyPair2.publicKey;
        PrivateKey privateKey2 = rsaKeyPair2.privateKey;
        System.out.println(Base64.getEncoder().encodeToString(publicKey2.getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(privateKey2.getEncoded()));

        byte[] b1 = encode(publicKey1, "abc".getBytes(StandardCharsets.UTF_8));
        byte[] b2 = encode(publicKey2, "def".getBytes(StandardCharsets.UTF_8));
        System.out.println(new String(decode(privateKey1, b1), StandardCharsets.UTF_8));
        System.out.println(new String(decode(privateKey2, b2), StandardCharsets.UTF_8));
    }

    enum Singleton {
        INSTANCE;
        final KeyFactory keyFactory;

        Singleton() {
            try {
                keyFactory = KeyFactory.getInstance("RSA");
            } catch (NoSuchAlgorithmException e) {
                throw BaseException.get(e);
            }
        }
    }
}