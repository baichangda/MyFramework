package cn.bcd.lib.base.util;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {
    static final String STR_POOL_ALPHABETIC_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String STR_POOL_ALPHABETIC_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    static final String STR_POOL_ALPHABETIC = STR_POOL_ALPHABETIC_UPPERCASE + STR_POOL_ALPHABETIC_LOWERCASE;
    static final String STR_POOL_NUMERIC = "0123456789";
    static final String STR_POOL_ALPHANUMERIC = STR_POOL_ALPHABETIC + STR_POOL_NUMERIC;
    static final ThreadLocalRandom random = ThreadLocalRandom.current();

    /**
     * 随机大写字母字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphabeticUppercase(int n) {
        return random(STR_POOL_ALPHABETIC_UPPERCASE, n);
    }


    /**
     * 随机小写字母字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphabeticLowercase(int n) {
        return random(STR_POOL_ALPHABETIC_LOWERCASE, n);
    }

    /**
     * 随机大小写字母字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphabetic(int n) {
        return random(STR_POOL_ALPHABETIC, n);
    }

    /**
     * 随机数字字符串
     *
     * @param n
     * @return
     */
    public static String randomString_numeric(int n) {
        return random(STR_POOL_NUMERIC, n);
    }


    /**
     * 随机大小写字母数字字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphanumeric(int n) {
        return random(STR_POOL_ALPHANUMERIC, n);
    }

    /**
     * 从给定的字符串池中随机取出n个生成随机字符串
     *
     * @param strPool 字符串池
     * @param n
     * @return
     */
    public static String random(String strPool, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(strPool.charAt(random.nextInt(strPool.length())));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println(randomString_alphabeticUppercase(32));
        System.out.println(randomString_alphabeticLowercase(32));
        System.out.println(randomString_alphabetic(32));
        System.out.println(randomString_numeric(32));
        System.out.println(randomString_alphanumeric(32));
    }
}
