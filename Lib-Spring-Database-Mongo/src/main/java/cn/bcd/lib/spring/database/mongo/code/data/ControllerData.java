package cn.bcd.lib.spring.database.mongo.code.data;


import java.util.List;

public class ControllerData {
    /**
     * 模块名
     */
    public String moduleName;

    /**
     * 模块中文名
     */
    public String moduleNameCN;

    /**
     * 包路径
     */
    public String packagePre;

    /**
     * Bean所在包路径
     */
    public String beanPackage;

    /**
     * 字段集合
     */
    public List<BeanField> fieldList;

}
