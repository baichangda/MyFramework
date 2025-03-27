package cn.bcd.lib.database.jdbc.code.data;



import cn.bcd.lib.database.jdbc.bean.BaseBean;
import cn.bcd.lib.database.jdbc.bean.SuperBaseBean;

import java.util.List;

public class BeanData {

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
     * 父类
     * 1: #{@link BaseBean}
     * 2: #{@link SuperBaseBean}
     */
    public int superBeanType;

    /**
     * 映射数据库表名
     */
    public String tableName;

    /**
     * 字段集合
     */
    public List<BeanField> fieldList;

    public boolean containCreateAndUpdateField;
}

