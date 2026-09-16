package cn.bcd.lib.spring.database.mongo.service;

import cn.bcd.lib.spring.database.mongo.anno.DocumentExt;
import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import org.springframework.data.mongodb.core.mapping.Document;

public final class BeanInfo<T> {
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
}
