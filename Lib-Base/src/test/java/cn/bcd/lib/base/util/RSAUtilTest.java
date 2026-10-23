package cn.bcd.lib.base.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RSAUtilTest {
    private static RSAUtil.RsaKeyPair keys;

    @BeforeAll
    static void generateKeys() {
        keys = RSAUtil.generateKey(2048);
    }

    /** 分批覆盖全部字节值，确保非文本数据解密后原样保留。 */
    @Test
    void binaryPlaintextRoundTripsWithoutCharsetConversion() {
        for (int offset = 0; offset < 256; offset += 64) {
            byte[] plainText = new byte[64];
            for (int i = 0; i < plainText.length; i++) {
                plainText[i] = (byte) (offset + i);
            }
            assertArrayEquals(plainText, RSAUtil.decode(keys.privateKey(),
                    RSAUtil.encode(keys.publicKey(), plainText)));
        }
    }

    /** 空明文能够加密并还原为空字节数组。 */
    @Test
    void emptyPlaintextRoundTrips() {
        assertArrayEquals(new byte[0], RSAUtil.decode(keys.privateKey(),
                RSAUtil.encode(keys.publicKey(), new byte[0])));
    }

    /** 单块最大长度的二进制明文也能完整还原。 */
    @Test
    void maximumSingleBlockPlaintextRoundTrips() {
        int keyBytes = (((RSAPublicKey) keys.publicKey()).getModulus().bitLength() + 7) / 8;
        byte[] plainText = new byte[keyBytes - 11];
        for (int i = 0; i < plainText.length; i++) {
            plainText[i] = (byte) i;
        }
        assertArrayEquals(plainText, RSAUtil.decode(keys.privateKey(),
                RSAUtil.encode(keys.publicKey(), plainText)));
    }

    /** 文本由调用方使用相同的字符集显式编码和解码。 */
    @Test
    void textRoundTripsWithExplicitUtf8() {
        String text = "RSA中文测试\u0000\ud83d\ude00";
        byte[] encrypted = RSAUtil.encode(keys.publicKey(), text.getBytes(StandardCharsets.UTF_8));
        assertEquals(text, new String(RSAUtil.decode(keys.privateKey(), encrypted), StandardCharsets.UTF_8));
    }

    /** 并发生成不同位数的密钥，验证配置互不干扰、公私钥匹配且能完成加解密。 */
    @Test
    void concurrentGenerationKeepsEachCallsKeySizeAndKeyPair() throws Exception {
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        // 使用守护线程和统一超时，确保并发问题回归时测试不会无限等待。
        var executor = Executors.newFixedThreadPool(workers, Thread.ofPlatform().daemon(true).factory());
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                int bits = i % 2 == 0 ? 1024 : 2048;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int iteration = 0; iteration < 4; iteration++) {
                        var pair = RSAUtil.generateKey(bits);
                        var publicKey = (RSAPublicKey) pair.publicKey();
                        var privateKey = (RSAPrivateKey) pair.privateKey();
                        assertEquals(bits, publicKey.getModulus().bitLength());
                        assertEquals(publicKey.getModulus(), privateKey.getModulus());
                        byte[] plainText = {0, (byte) 0xff, (byte) iteration};
                        assertArrayEquals(plainText, RSAUtil.decode(privateKey,
                                RSAUtil.encode(publicKey, plainText)));
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

}
