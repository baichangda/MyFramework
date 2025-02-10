package cn.bcd.dataProcess.gateway.mqtt;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import cn.bcd.base.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

public class MqttSslSupport {

    static Logger logger= LoggerFactory.getLogger(MqttSslSupport.class);

    public static MqttClientSslConfig getMqttClientSslConfig(String sslCertFilePath, String sslCertPassword) {
        try {
            // 加载 PKCS#12 格式的密钥库
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = MqttSslSupport.class.getClassLoader().getResourceAsStream(sslCertFilePath)) {
                keyStore.load(is, sslCertPassword.toCharArray());
            }

            // 创建 KeyManagerFactory 并初始化
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, sslCertPassword.toCharArray());

            // 创建 TrustManagerFactory 并初始化
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            return MqttClientSslConfig
                    .builder()
                    .trustManagerFactory(tmf)
                    .keyManagerFactory(kmf)
                    .build();
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }
}
