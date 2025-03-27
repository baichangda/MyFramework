package cn.bcd.lib.database.jdbc.code;


import cn.bcd.lib.database.jdbc.code.data.BeanField;
import cn.bcd.lib.database.jdbc.dbinfo.data.DBInfo;

import java.sql.Connection;
import java.util.List;

public interface DBSupport {

    DBInfo getSpringDBConfig();


    /**
     * 获取指定表的列名
     *
     * @param config
     * @param connection
     * @return
     */
    List<BeanField> getTableBeanFieldList(TableConfig config, Connection connection);


    record PkInfo(String name, CodeConst.PkType pkType) {
    }
    /**
     * 获取指定表的列名
     * <p>
     * 支持如下几种主键类型
     * Byte、Short、Integer、Long、String
     *
     * @param config
     * @param connection
     * @return
     */
    BeanField getTablePk(TableConfig config, Connection connection);

}


