package cn.bcd.lib.base.util;

import java.util.Arrays;
import java.util.function.IntFunction;

public class ArrayUtil {

    public static byte[] concat(byte[]... arrs) {
        int len = 0;
        for (byte[] arr : arrs) {
            len += arr.length;
        }
        byte[] res = new byte[len];
        int offset = 0;
        for (byte[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    public static short[] concat(short[]... arrs) {
        int len = 0;
        for (short[] arr : arrs) {
            len += arr.length;
        }
        short[] res = new short[len];
        int offset = 0;
        for (short[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    public static int[] concat(int[]... arrs) {
        int len = 0;
        for (int[] arr : arrs) {
            len += arr.length;
        }
        int[] res = new int[len];
        int offset = 0;
        for (int[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    public static long[] concat(long[]... arrs) {
        int len = 0;
        for (long[] arr : arrs) {
            len += arr.length;
        }
        long[] res = new long[len];
        int offset = 0;
        for (long[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    public static float[] concat(float[]... arrs) {
        int len = 0;
        for (float[] arr : arrs) {
            len += arr.length;
        }
        float[] res = new float[len];
        int offset = 0;
        for (float[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    public static double[] concat(double[]... arrs) {
        int len = 0;
        for (double[] arr : arrs) {
            len += arr.length;
        }
        double[] res = new double[len];
        int offset = 0;
        for (double[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    public static char[] concat(char[]... arrs) {
        int len = 0;
        for (char[] arr : arrs) {
            len += arr.length;
        }
        char[] res = new char[len];
        int offset = 0;
        for (char[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    public static boolean[] concat(boolean[]... arrs) {
        int len = 0;
        for (boolean[] arr : arrs) {
            len += arr.length;
        }
        boolean[] res = new boolean[len];
        int offset = 0;
        for (boolean[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }

    /**
     * 拼接多个数组
     * @param arrConstructor 结果数组的构造器
     * @param arrs
     * @return
     * @param <T>
     */
    @SafeVarargs
    public static <T> T[] concat(IntFunction<T[]> arrConstructor, T[]... arrs) {
        int len = 0;
        for (T[] arr : arrs) {
            len += arr.length;
        }
        T[] res = arrConstructor.apply(len);
        int offset = 0;
        for (T[] arr : arrs) {
            System.arraycopy(arr, 0, res, offset, arr.length);
            offset += arr.length;
        }
        return res;
    }


    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3, 4};
        System.out.println(Arrays.toString(concat(a, b)));

        String[] s1 = {"a", "b", "c"};
        String[] s2 = {"a", "b", "c", "d"};
        System.out.println(Arrays.toString(concat(String[]::new, s1, s2)));
    }
}
