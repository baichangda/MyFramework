package cn.bcd.lib.parser.base.anno.data;

/**
 * @param type 0代表值无问题
 *             其他情况代表值有各种异常情况、此时无需查看val
 * @param val  当type为0时有效
 */
public record NumVal_byte(int type, byte val) {
    public static NumVal_byte get(DefaultNumValChecker checker, NumType numType, int val) {
        int t = checker.getType(numType, val);
        if (t == 0) {
            return new NumVal_byte(0, (byte) val);
        } else {
            return new NumVal_byte(t, (byte) 0);
        }
    }

    public static NumVal_byte get(DefaultNumValChecker checker, NumType numType, int rawVal) {
        int t = checker.getType(numType, val);
        if (t == 0) {
            return new NumVal_byte(0, (byte) val);
        } else {
            return new NumVal_byte(t, (byte) 0);
        }
    }
}
