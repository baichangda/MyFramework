package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AESUtilTest {
    @Test
    void gcmRoundTripsEmptyPlaintextWithAndWithoutAad() {
        byte[] key = AESUtil.generateKey(128);
        for (byte[] aad : new byte[][]{null, new byte[0], new byte[]{1, 2, 3}}) {
            byte[] encrypted = AESUtil.encryptGcm(new byte[0], key, aad);
            assertEquals(28, encrypted.length);
            assertArrayEquals(new byte[0], AESUtil.decryptGcm(encrypted, key, aad));
        }
        assertEquals("", AESUtil.decryptGcmFromBase64(AESUtil.encryptGcmToBase64("", key, null), key, null));
    }

    @Test
    void gcmRejectsTruncatedAndTamperedEmptyCiphertext() {
        byte[] key = AESUtil.generateKey(128);
        byte[] encrypted = AESUtil.encryptGcm(new byte[0], key, null);
        assertThrows(IllegalStateException.class,
                () -> AESUtil.decryptGcm(Arrays.copyOf(encrypted, 27), key, null));
        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> AESUtil.decryptGcm(encrypted, key, null));
    }
}
