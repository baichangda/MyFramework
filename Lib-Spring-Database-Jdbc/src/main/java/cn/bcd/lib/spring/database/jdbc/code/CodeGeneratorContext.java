package cn.bcd.lib.spring.database.jdbc.code;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.database.jdbc.code.data.ApiData;
import cn.bcd.lib.spring.database.jdbc.code.data.BeanField;

import java.io.File;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CodeGeneratorContext {
    public final TableConfig tableConfig;
    public final DBSupport dbSupport;
    public final Connection connection;


    //以下是cache字段
    public List<BeanField> allBeanFields;
    public List<BeanField> declaredBeanFields;
    public String packagePre;
    public Boolean containCreateAndUpdateField;


    public CodeGeneratorContext(TableConfig tableConfig, DBSupport dbSupport, Connection connection) {
        this.tableConfig = tableConfig;
        this.dbSupport = dbSupport;
        this.connection = connection;
    }

    /**
     * 获取实体类定义字段信息(排除公用信息 id/create/update信息)
     */
    public List<BeanField> getDeclaredBeanFields() {
        if (declaredBeanFields == null) {
            if (getContainCreateAndUpdateField()) {
                declaredBeanFields = getAllBeanFields().stream().filter(e -> {
                    if ("id".equals(e.name)) {
                        return false;
                    } else {
                        return !CodeConst.CREATE_INFO_FIELD_NAME.contains(e.name);
                    }
                }).collect(Collectors.toList());
            } else {
                declaredBeanFields = getAllBeanFields().stream().filter(e -> {
                    if ("id".equals(e.name)) {
                        return false;
                    } else {
                        return true;
                    }
                }).collect(Collectors.toList());
            }
        }
        return declaredBeanFields;
    }

    /**
     * 获取所有字段信息
     *
     * @return
     */
    public List<BeanField> getAllBeanFields() {
        if (allBeanFields == null) {
            allBeanFields = dbSupport.getTableBeanFieldList(tableConfig, connection);
        }
        return allBeanFields;
    }


    /**
     * 初始化包名
     * 初始化当前表生成代码目录父包名
     */
    public String getPackagePre() {
        if (packagePre == null) {
            String springSrcPath = "src" +
                    File.separatorChar +
                    "main" +
                    File.separatorChar +
                    "java" +
                    File.separatorChar;
            String targetDirPath = tableConfig.config.targetDirPath;
            if (targetDirPath.contains(springSrcPath)) {
                String splitStr = springSrcPath.replace("\\", "\\\\");
                packagePre = targetDirPath.split(splitStr)[1].replace(File.separator, ".");
            } else {
                throw BaseException.get("targetDirPath[" + targetDirPath + "] must contains [" + springSrcPath + "]");
            }
        }
        return packagePre;
    }

    /**
     * 获取当前功能的包名
     */
    public String getModulePackage() {
        String moduleName = tableConfig.moduleName;
        return getPackagePre() + "." + moduleName.substring(0, 1).toLowerCase() + moduleName.substring(1);
    }

    /**
     * 初始化request mapping
     *
     * @return
     */
    public String getRequestMappingPre() {
        return "/" + getPackagePre().substring(getPackagePre().lastIndexOf('.') + 1);
    }

    /** 请求包含全部非审计字段，响应包含全部字段；生成后按业务需要手工调整。 */
    public <D extends ApiData> D initApiData(D data) {
        List<BeanField> all = getAllBeanFields();
        if (all.stream().noneMatch(field -> field.name.equals("id"))) {
            throw new IllegalArgumentException("CRUD generation requires an id property");
        }
        data.moduleName = tableConfig.moduleName;
        data.moduleNameCN = tableConfig.moduleNameCN;
        data.packagePre = getModulePackage();
        data.queryResponseFields = List.copyOf(all);
        data.queryFields = all.stream()
                .filter(field -> !CodeConst.CREATE_INFO_FIELD_NAME.contains(field.name))
                .toList();
        data.saveFields = data.queryFields.stream().filter(field -> !field.name.equals("id")).toList();
        Set<String> queryNames = new HashSet<>();
        for (BeanField field : data.queryFields) {
            if (field.name.equals("pageNum") || field.name.equals("pageSize")) {
                throw new IllegalArgumentException("Query field conflicts with pagination: " + field.name);
            }
            List<String> properties = field.type.equals("Date")
                    ? List.of(field.name + "Begin", field.name + "End") : List.of(field.name);
            for (String property : properties) {
                if (!queryNames.add(property)) throw new IllegalArgumentException("Duplicate query property: " + property);
            }
        }
        return data;
    }

    public boolean getContainCreateAndUpdateField() {
        if (containCreateAndUpdateField == null) {
            containCreateAndUpdateField = true;
            Set<String> fieldNameSet = getAllBeanFields().stream().map(e -> e.name).collect(Collectors.toSet());
            for (String s : CodeConst.CREATE_INFO_FIELD_NAME) {
                if (!fieldNameSet.contains(s)) {
                    containCreateAndUpdateField = false;
                    break;
                }
            }
        }
        return containCreateAndUpdateField;
    }

}
