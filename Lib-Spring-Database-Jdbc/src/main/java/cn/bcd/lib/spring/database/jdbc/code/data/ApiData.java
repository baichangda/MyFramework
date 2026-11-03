package cn.bcd.lib.spring.database.jdbc.code.data;

import java.util.List;

/** Controller、Service 和接口模型共享的模板数据。 */
public class ApiData {
    public String moduleName;
    public String moduleNameCN;
    public String packagePre;
    public List<BeanField> queryFields;
    public List<BeanField> saveFields;
    public List<BeanField> responseFields;
}
