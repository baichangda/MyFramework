package cn.bcd.lib.spring.database.mongo.service;

import cn.bcd.lib.spring.database.mongo.anno.DocumentExt;
import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import cn.bcd.lib.base.exception.BaseException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public final class BeanInfo<T> {
    /** 初始化时缓存可更新字段，排除主键和非持久化、只读字段。 */
    public final List<Field> updateFieldList;
    /**
     * service的实体类
     */
    public final Class<T> clazz;

    /**
     * bean所属collection
     */
    public final String collection;

    /**
     * 是否在新增时候自动设置创建信息
     */
    public final boolean autoSetCreateInfo;
    /**
     * 是否在更新时候自动设置更新信息
     */
    public final boolean autoSetUpdateInfo;


    public BeanInfo(Class<T> clazz) {
        this.clazz = clazz;
        List<Field> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Field field : FieldUtils.getAllFieldsList(clazz)) {
            int modifiers = field.getModifiers();
            String name = field.getName();
            if (!names.add(name) || field.isSynthetic() || Modifier.isStatic(modifiers)
                    || Modifier.isTransient(modifiers) || Modifier.isFinal(modifiers)
                    || name.equals("id") || hasAnnotation(field, Id.class)
                    || hasAnnotation(field, Transient.class) || hasAnnotation(field, ReadOnlyProperty.class)) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        updateFieldList = List.copyOf(fields);

        collection = clazz.getAnnotation(Document.class).collection();

        if (BaseBean.class.isAssignableFrom(clazz)) {
            DocumentExt documentExt = clazz.getAnnotation(DocumentExt.class);
            if (documentExt == null) {
                autoSetCreateInfo = true;
                autoSetUpdateInfo = true;
            } else {
                autoSetCreateInfo = documentExt.autoSetCreateInfo();
                autoSetUpdateInfo = documentExt.autoSetUpdateInfo();
            }
        } else {
            autoSetCreateInfo = false;
            autoSetUpdateInfo = false;
        }
    }

    private boolean hasAnnotation(Field field, Class<? extends Annotation> annotation) {
        if (AnnotatedElementUtils.hasAnnotation(field, annotation)) {
            return true;
        }
        var property = BeanUtils.getPropertyDescriptor(clazz, field.getName());
        return property != null && property.getReadMethod() != null
                && AnnotatedElementUtils.hasAnnotation(property.getReadMethod(), annotation);
    }

    public Update toUpdate(T bean) {
        Update update = new Update();
        try {
            for (Field field : updateFieldList) {
                update.set(field.getName(), field.get(bean));
            }
        } catch (IllegalAccessException ex) {
            throw BaseException.get(ex);
        }
        return update;
    }
}
