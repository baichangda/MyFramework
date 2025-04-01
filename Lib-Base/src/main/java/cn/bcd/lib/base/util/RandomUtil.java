package cn.bcd.lib.base.util;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {
    static final String strPool_alphabeticUppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String strPool_alphabeticLowercase = "abcdefghijklmnopqrstuvwxyz";
    static final String strPool_alphabetic = strPool_alphabeticUppercase + strPool_alphabeticLowercase;
    static final String strPool_numeric = "0123456789";
    static final String strPool_alphanumeric = strPool_alphabetic + strPool_numeric;
    static final ThreadLocalRandom random = ThreadLocalRandom.current();

    /**
     * 随机大写字母字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphabeticUppercase(int n) {
        return random(strPool_alphabeticUppercase, n);
    }


    /**
     * 随机小写字母字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphabeticLowercase(int n) {
        return random(strPool_alphabeticLowercase, n);
    }

    /**
     * 随机大小写字母字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphabetic(int n) {
        return random(strPool_alphabetic, n);
    }

    /**
     * 随机数字字符串
     *
     * @param n
     * @return
     */
    public static String randomString_numeric(int n) {
        return random(strPool_numeric, n);
    }


    /**
     * 随机大小写字母数字字符串
     *
     * @param n
     * @return
     */
    public static String randomString_alphanumeric(int n) {
        return random(strPool_alphanumeric, n);
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
