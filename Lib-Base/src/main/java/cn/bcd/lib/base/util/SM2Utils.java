package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.Security;

public class SM2Utils {


    static final String SM2_CURVE_NAME = "sm2p256v1";

    // 注册BouncyCastleProvider
    static {
        Security.addProvider(new BouncyCastleProvider());
    }


    public static ECPublicKeyParameters publicKeyFromBytes(byte[] bytes) {
        // 解析公钥
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName(SM2_CURVE_NAME);
        ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN()
        );
        ECPoint publicKeyPoint = sm2ECParameters.getCurve().decodePoint(bytes);
        return new ECPublicKeyParameters(publicKeyPoint, domainParameters);
    }

    public static ECPrivateKeyParameters privateKeyFromBytes(byte[] bytes) {
        // 解析私钥
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN()
        );
        return new ECPrivateKeyParameters(new BigInteger(1, bytes), domainParameters);
    }

    public static byte[] publicKeyToBytes(ECPublicKeyParameters publicKey) {
        return publicKey.getQ().getEncoded(false);
    }

    public static byte[] privateKeyToBytes(ECPrivateKeyParameters privateKey) {
        return privateKey.getD().toByteArray();
    }

    public record Sm2KeyPair(ECPublicKeyParameters publicKey, ECPrivateKeyParameters privateKey) {
        public byte[] getPublicKeyBytes() {
            return publicKeyToBytes(publicKey);
        }
        public byte[] getPrivateKeyBytes() {
            return privateKeyToBytes(privateKey);
        }

    }

    /**
     * 生成SM2密钥对（字节数组形式）
     *
     * @return 包含公钥（publicKey）和私钥（privateKey）的Map（均为byte[]）
     */
    public static Sm2KeyPair generateKeyPair() {
        // 获取SM2曲线参数
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName(SM2_CURVE_NAME);
        ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN()
        );

        // 生成密钥对
        ECKeyPairGenerator keyPairGenerator = new ECKeyPairGenerator();
        keyPairGenerator.init(new ECKeyGenerationParameters(domainParameters, new SecureRandom()));
        AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();

        // 提取公钥和私钥（字节数组形式）
        ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) keyPair.getPrivate();
        ECPublicKeyParameters publicKey = (ECPublicKeyParameters) keyPair.getPublic();
        return new Sm2KeyPair(publicKey, privateKey);
    }

    /**
     * SM2加密（字节数组输入输出）
     *
     * @param publicKey 公钥
     * @param plainText 明文（byte[]）
     * @return 密文（byte[]）
     */
    public static byte[] encrypt(ECPublicKeyParameters publicKey, byte[] plainText) throws InvalidCipherTextException {
        // 初始化加密引擎
        SM2Engine engine = new SM2Engine();
        engine.init(true, new ParametersWithRandom(publicKey, new SecureRandom()));

        // 加密并返回密文字节数组
        return engine.processBlock(plainText, 0, plainText.length);
    }

    /**
     * SM2解密（字节数组输入输出）
     *
     * @param privateKey 私钥
     * @param cipherText 密文（byte[]）
     * @return 明文（byte[]）
     */
    public static byte[] decrypt(ECPrivateKeyParameters privateKey, byte[] cipherText) throws InvalidCipherTextException {
        // 初始化解密引擎
        SM2Engine engine = new SM2Engine();
        engine.init(false, privateKey);
        // 解密并返回明文字节数组
        return engine.processBlock(cipherText, 0, cipherText.length);
    }

    /**
     * SM2签名（字节数组输入输出）
     *
     * @param privateKey 私钥
     * @param content    待签名内容（byte[]）
     * @return 签名结果（byte[]）
     */
    public static byte[] sign(ECPrivateKeyParameters privateKey, byte[] content) {
        // 初始化签名器
        SM2Signer signer = new SM2Signer();
        signer.init(true, new ParametersWithRandom(privateKey, new SecureRandom()));

        // 生成签名并返回字节数组
        signer.update(content, 0, content.length);
        try {
            return signer.generateSignature();
        } catch (CryptoException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * SM2验签（字节数组输入）
     *
     * @param publicKey 公钥
     * @param content   待验签内容（byte[]）
     * @param signature 签名结果（byte[]）
     * @return 验签是否通过
     */
    public static boolean verify(ECPublicKeyParameters publicKey, byte[] content, byte[] signature) {
        // 初始化验签器
        SM2Signer signer = new SM2Signer();
        signer.init(false, publicKey);

        // 验签
        signer.update(content, 0, content.length);
        return signer.verifySignature(signature);
    }

    // 测试方法
    public static void main(String[] args) throws Exception {
        // 生成密钥对（字节数组形式）
        Sm2KeyPair keyPair = generateKeyPair();
        ECPrivateKeyParameters privateKey = keyPair.privateKey;
        ECPublicKeyParameters publicKey = keyPair.publicKey;

        // 测试加密解密
        byte[] plainText = "Hello SM2! 测试字节数组加解密".getBytes();
        System.out.println("明文：" + new String(plainText));

        byte[] cipherText = encrypt(publicKey, plainText);
        System.out.println("密文长度：" + cipherText.length + " bytes");

        byte[] decryptedText = decrypt(privateKey, cipherText);
        System.out.println("解密后：" + new String(decryptedText)); // 应与明文一致

        // 测试签名验签
        byte[] content = "Test SM2 Signature 测试字节数组签名".getBytes();
        byte[] signature = sign(privateKey, content);
        System.out.println("签名长度：" + signature.length + " bytes");

        boolean verifyResult = verify(publicKey, content, signature);
        System.out.println("验签结果：" + verifyResult); // 应输出true

        // 测试篡改内容后的验签（应失败）
        byte[] fakeContent = "Test SM2 Signature 测试字节数组签名（篡改）".getBytes();
        boolean fakeVerify = verify(publicKey, fakeContent, signature);
        System.out.println("篡改内容验签结果：" + fakeVerify); // 应输出false
    }
}