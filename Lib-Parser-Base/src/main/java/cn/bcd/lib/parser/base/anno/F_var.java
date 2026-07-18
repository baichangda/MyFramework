package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.processor.ProcessContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将字段值保存到一次完整解析或反解析共享的{@link ProcessContext}中。
 *
 * <p>此注解是辅助注解，必须和一个字段解析注解同时使用，不能单独使用。</p>
 *
 * <p>index必须从0开始按照使用顺序连续分配，不要使用稀疏索引。整个顶层对象及其所有
 * 子对象共享相同的索引空间；相同index被多次写入时，后写入的值会覆盖之前的值。
 * Bean列表重复使用相同index时，解析结束后保留最后一次写入的值。</p>
 *
 * <p>建议将index定义成公共静态常量，让字段注解和自定义处理器共同引用：</p>
 * <pre>{@code
 * public static final int VAR_PACKET_TYPE = 0;
 *
 * @F_num(...)
 * @F_var(index = VAR_PACKET_TYPE)
 * public int packetType;
 *
 * int packetType = (Integer) processContext.getVar(VAR_PACKET_TYPE);
 * }</pre>
 *
 * <p>解析时在字段赋值完成后保存；反解析时在字段写出前保存。字段值允许为null，读取
 * 尚未写入的index会抛出{@link IllegalStateException}。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface F_var {
    /**
     * ProcessContext通用变量索引，从0开始按照使用顺序连续分配。
     */
    int index();
}
