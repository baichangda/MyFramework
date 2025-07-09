package cn.bcd.server.business.process.backend.asm;

public class Test {
    public int add(int a, int b, int c) {
        return a - b + c;
    }

    public int testFor(int n) {
        int sum = 0;
        Double d = 1d;
        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) {
                sum += i;
            }
        }
        return (int) (sum + d);
    }

    public static void main(String[] args) {

    }
}
