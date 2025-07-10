package cn.bcd.lib.parser.base.log;

public interface LogCollector_deParse {

    LogCollector_deParse defaultInstance = new DefaultLogCollector_deParse();

    /**
     * @param clazz
     * @param type  1、C_skip
     * @param args
     */
    void collect_class(Class<?> clazz, int type, Object... args);

    /**
     * @param clazz
     * @param fieldName
     * @param type      1：F_skip
     *                  2、F_bit_num
     *                  0、other
     * @param args
     */
    void collect_field(Class<?> clazz, String fieldName, int type, Object... args);

}