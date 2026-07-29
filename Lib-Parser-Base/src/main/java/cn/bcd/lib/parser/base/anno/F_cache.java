package cn.bcd.lib.parser.base.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将字段解析或反解析过程中产生的值缓存到共享的解析上下文中。
 *
 * <p>用于和字段解析注解配合使用，不能单独作为字段解析注解。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface F_cache {
    /**
     * {@link cn.bcd.lib.parser.base.processor.ProcessContext} 中的缓存索引。
     *
     * <p>索引缓存基于数组实现，性能高于{@link #key()}。性能敏感场景应优先使用，
     * 并在同一次解析流程中从0开始连续递增，避免浪费数组空间和不必要的扩容。</p>
     */
    int index() default -1;

    /**
     * {@link cn.bcd.lib.parser.base.processor.ProcessContext} 中的缓存键。
     */
    String key() default "";
}
