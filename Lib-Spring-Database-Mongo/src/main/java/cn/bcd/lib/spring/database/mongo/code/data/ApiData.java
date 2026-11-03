package cn.bcd.lib.spring.database.mongo.code.data;

import java.util.List;

/** Mongo 接口模型和业务层共享的生成数据。 */
public class ApiData {
    public String moduleName;
    public String moduleNameCN;
    public String packagePre;
    public String beanPackage;
    public String beanClassName;
    public String beanSimpleName;
    public List<BeanField> fieldList;
    public List<BeanField> queryFields;
    public List<BeanField> saveFields;
    public List<BeanField> queryResponseFields;
}
