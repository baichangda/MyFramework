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

import java.security.SecureRandom;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

public class SM2Utils {
    // 注册BouncyCastleProvider
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public record KeyPair(byte[] publicKey, byte[] privateKey) {
    }

    /**
     * 生成SM2密钥对（字节数组形式）
     *
     * @return 包含公钥（publicKey）和私钥（privateKey）的Map（均为byte[]）
     */
    public static KeyPair generateKeyPair() {
        // 获取SM2曲线参数
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName("sm2p256v1");
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

        byte[] privateKeyBytes = privateKey.getD().toByteArray();
        byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
        return new KeyPair(publicKeyBytes, privateKeyBytes);
    }

    /**
     * SM2加密（字节数组输入输出）
     *
     * @param publicKey 公钥（byte[]）
     * @param plainText 明文（byte[]）
     * @return 密文（byte[]）
     */
    public static byte[] encrypt(byte[] publicKey, byte[] plainText) throws InvalidCipherTextException {
        // 解析公钥
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN()
        );
        ECPoint publicKeyPoint = sm2ECParameters.getCurve().decodePoint(publicKey);
        ECPublicKeyParameters publicKeyParameters = new ECPublicKeyParameters(publicKeyPoint, domainParameters);

        // 初始化加密引擎
        SM2Engine engine = new SM2Engine();
        engine.init(true, new ParametersWithRandom(publicKeyParameters, new SecureRandom()));

        // 加密并返回密文字节数组
        return engine.processBlock(plainText, 0, plainText.length);
    }

    /**
     * SM2解密（字节数组输入输出）
     *
     * @param privateKey 私钥（byte[]）
     * @param cipherText 密文（byte[]）
     * @return 明文（byte[]）
     */
    public static byte[] decrypt(byte[] privateKey, byte[] cipherText) throws InvalidCipherTextException {
        // 解析私钥
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN()
        );
        ECPrivateKeyParameters privateKeyParameters = new ECPrivateKeyParameters(
                new java.math.BigInteger(1, privateKey), // 私钥字节数组转BigInteger
                domainParameters
        );

        // 初始化解密引擎
        SM2Engine engine = new SM2Engine();
        engine.init(false, privateKeyParameters);

        // 解密并返回明文字节数组
        return engine.processBlock(cipherText, 0, cipherText.length);
    }

    /**
     * SM2签名（字节数组输入输出）
     *
     * @param privateKey 私钥（byte[]）
     * @param content    待签名内容（byte[]）
     * @return 签名结果（byte[]）
     */
    public static byte[] sign(byte[] privateKey, byte[] content) {
        // 解析私钥
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN()
        );
        ECPrivateKeyParameters privateKeyParameters = new ECPrivateKeyParameters(
                new java.math.BigInteger(1, privateKey),
                domainParameters
        );

        // 初始化签名器
        SM2Signer signer = new SM2Signer();
        signer.init(true, new ParametersWithRandom(privateKeyParameters, new SecureRandom()));

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
     * @param publicKey 公钥（byte[]）
     * @param content   待验签内容（byte[]）
     * @param signature 签名结果（byte[]）
     * @return 验签是否通过
     */
    public static boolean verify(byte[] publicKey, byte[] content, byte[] signature) {
        // 解析公钥
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domainParameters = new ECDomainParameters(
                sm2ECParameters.getCurve(),
                sm2ECParameters.getG(),
                sm2ECParameters.getN()
        );
        ECPoint publicKeyPoint = sm2ECParameters.getCurve().decodePoint(publicKey);
        ECPublicKeyParameters publicKeyParameters = new ECPublicKeyParameters(publicKeyPoint, domainParameters);

        // 初始化验签器
        SM2Signer signer = new SM2Signer();
        signer.init(false, publicKeyParameters);

        // 验签
        signer.update(content, 0, content.length);
        return signer.verifySignature(signature);
    }

    // 测试方法
    public static void main(String[] args) throws Exception {
        // 生成密钥对（字节数组形式）
        KeyPair keyPair = generateKeyPair();
        byte[] privateKey = keyPair.privateKey;
        byte[] publicKey = keyPair.publicKey;
        System.out.println("私钥长度：" + privateKey.length + " bytes");
        System.out.println("公钥长度：" + publicKey.length + " bytes");

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