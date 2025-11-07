package cn.bcd.lib.spring.database.jdbc.service;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.anno.Transient;
import cn.bcd.lib.spring.database.jdbc.anno.Unique;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public final class BeanInfo<T extends SuperBaseBean> {
    static final Set<String> createInfoFields = Set.of("createTime", "createUserId", "createUserName");

    /**
     * service的实体类
     */
    public final Class<T> clazz;

    /**
     * 实体类表名
     */
    public final String tableName;

    /**
     * 主键字段信息
     */
    final FieldInfo idFieldInfo;

    /**
     * 字段名或列名 -> 列名
     */
    private final Map<String, String> fieldNameOrColumnName_columnName;

    /**
     * 新增的sql、区分包含id和不包含id的新增情况
     * <p>
     * insert into tableName(?,...) values(?,...)
     */
    public final String insertSql_noId;
    public final List<FieldInfo> insertFieldList_noId;
    public final String insertSql;
    public final List<FieldInfo> insertFieldList;

    /**
     * 更新的sql
     * 不包含对创建信息的更新(如果继承于{@link BaseBean})
     * <p>
     * update tableName set xxx=? ... where id=?
     */
    public final String updateSql;
    public final List<FieldInfo> updateFieldList;

    /**
     * 是否在新增时候自动设置创建信息
     */
    public final boolean autoSetCreateInfo;
    /**
     * 是否在更新时候自动设置更新信息
     */
    public final boolean autoSetUpdateInfo;
    /**
     * 唯一字段集合
     */
    public final List<UniqueInfo> uniqueInfoList;

    public BeanInfo(Class<T> clazz) {
        this.clazz = clazz;

        Table table = clazz.getAnnotation(Table.class);
        if (table == null) {
            throw BaseException.get("class[{}] must has annotation @Table", clazz.getName());
        }
        tableName = table.value();

        boolean isBeanBean = BaseBean.class.isAssignableFrom(clazz);
        if (isBeanBean) {
            autoSetCreateInfo = table.autoSetCreateInfo();
            autoSetUpdateInfo = table.autoSetUpdateInfo();
        } else {
            autoSetCreateInfo = false;
            autoSetUpdateInfo = false;
        }

        final Field[] allFields = FieldUtils.getAllFields(clazz);
        insertFieldList_noId = new ArrayList<>();
        insertFieldList = new ArrayList<>();
        updateFieldList = new ArrayList<>();
        uniqueInfoList = new ArrayList<>();
        fieldNameOrColumnName_columnName = new HashMap<>();

        FieldInfo tempIdFieldInfo = null;
        for (Field f : allFields) {
            if (f.getAnnotation(Transient.class) == null && !Modifier.isStatic(f.getModifiers())) {
                final FieldInfo fieldInfo = new FieldInfo(f);
                String fieldName = f.getName();
                if (fieldName.equals("id")) {
                    tempIdFieldInfo = fieldInfo;
                } else {
                    insertFieldList_noId.add(fieldInfo);
                    //只有不是继承自BaseBean且不属于创建信息字段才加入更新字段集合
                    if (!(isBeanBean && createInfoFields.contains(fieldName))) {
                        updateFieldList.add(fieldInfo);
                    }
                }
                fieldNameOrColumnName_columnName.put(fieldInfo.fieldName, fieldInfo.columnName);
                fieldNameOrColumnName_columnName.put(fieldInfo.columnName, fieldInfo.columnName);
                Unique unique = f.getAnnotation(Unique.class);
                if (unique != null) {
                    uniqueInfoList.add(new UniqueInfo(fieldInfo, unique, tableName));
                }
            }
        }
        idFieldInfo = tempIdFieldInfo;

        //拼装insert sql
        StringJoiner sj1 = new StringJoiner(",");
        StringJoiner sj2 = new StringJoiner(",");
        for (FieldInfo fieldInfo : insertFieldList_noId) {
            final String columnName = fieldInfo.columnName;
            sj1.add(columnName);
            sj2.add("?");

        }
        insertSql_noId = "insert into " + tableName + "(" + sj1 + ") values(" + sj2 + ")";
        insertSql = "insert into " + tableName + "(id," + sj1 + ") values(?," + sj2 + ")";
        insertFieldList.add(idFieldInfo);
        insertFieldList.addAll(insertFieldList_noId);

        //拼装update sql
        StringJoiner sj3 = new StringJoiner(",");
        for (FieldInfo fieldInfo : updateFieldList) {
            final String columnName = fieldInfo.columnName;
            sj3.add(columnName + "=?");
        }
        updateSql = "update " + tableName + " set " + sj3 + " where id=?";
        updateFieldList.add(idFieldInfo);
    }

    public List<Object> getInsertValues_noId(T t) {
        try {
            List<Object> args = new ArrayList<>();
            for (FieldInfo fieldInfo : insertFieldList_noId) {
                final Object v = fieldInfo.field.get(t);
                args.add(v);
            }
            return args;
        } catch (IllegalAccessException e) {
            throw BaseException.get(e);
        }
    }

    public List<Object> getInsertValues(T t) {
        try {
            List<Object> args = new ArrayList<>();
            for (FieldInfo fieldInfo : insertFieldList) {
                if (idFieldInfo == fieldInfo) {
                    args.add(t.getId());
                } else {
                    final Object v = fieldInfo.field.get(t);
                    args.add(v);
                }
            }
            return args;
        } catch (IllegalAccessException e) {
            throw BaseException.get(e);
        }
    }

    public List<Object> getUpdateValues(T t) {
        try {
            List<Object> args = new ArrayList<>();
            for (FieldInfo fieldInfo : updateFieldList) {
                if (idFieldInfo == fieldInfo) {
                    args.add(t.getId());
                } else {
                    final Object v = fieldInfo.field.get(t);
                    args.add(v);
                }
            }
            return args;
        } catch (IllegalAccessException e) {
            throw BaseException.get(e);
        }
    }

    public String toColumnName(String fieldNameOrColumnName) {
        final String columnName = fieldNameOrColumnName_columnName.get(fieldNameOrColumnName);
        if (columnName == null) {
            throw BaseException.get("bean[{}] tableName[{}] toColumnName[{}] null", clazz.getName(), tableName, fieldNameOrColumnName);
        }
        return columnName;
    }
}
