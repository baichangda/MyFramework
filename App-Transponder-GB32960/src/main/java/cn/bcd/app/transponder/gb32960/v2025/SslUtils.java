package cn.bcd.app.transponder.gb32960.v2025;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public class SslUtils {
    // 加载密钥管理器（用于提供自身证书和私钥）
    public static KeyManagerFactory loadKeyManagerFactory(String keyStorePath, String keyStorePassword, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray()); // 私钥密码（通常与密钥库密码一致）
        return kmf;
    }

    // 加载信任管理器（用于验证对方证书）
    public static TrustManagerFactory loadTrustManagerFactory(String trustStorePath, String trustStorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(trustStorePath)) {
            trustStore.load(fis, trustStorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }
}