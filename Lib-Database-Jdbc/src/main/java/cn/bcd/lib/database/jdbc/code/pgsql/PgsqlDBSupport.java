package cn.bcd.lib.database.jdbc.code.pgsql;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.database.jdbc.code.DBSupport;
import cn.bcd.lib.database.jdbc.code.TableConfig;
import cn.bcd.lib.database.jdbc.code.data.BeanField;
import cn.bcd.lib.database.jdbc.dbinfo.data.DBInfo;
import cn.bcd.lib.database.jdbc.dbinfo.pgsql.bean.ColumnsBean;
import cn.bcd.lib.database.jdbc.dbinfo.pgsql.util.DBInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PgsqlDBSupport implements DBSupport {

    Logger logger = LoggerFactory.getLogger(PgsqlDBSupport.class);

    @Override
    public DBInfo getSpringDBConfig() {
        return DBInfoUtil.getDBInfo();
    }

    @Override
    public List<BeanField> getTableBeanFieldList(TableConfig config, Connection connection) {
        String tableName = config.tableName;
        List<ColumnsBean> res = DBInfoUtil.findColumns(connection, config.config.dbInfo.db, tableName);
        return res.stream().map(e -> {
            PgsqlDBColumn pgsqlDBColumn = new PgsqlDBColumn();
            pgsqlDBColumn.name = e.column_name;
            pgsqlDBColumn.type = e.udt_name;
            pgsqlDBColumn.comment = e.description;
            pgsqlDBColumn.isNull = e.is_nullable;
            pgsqlDBColumn.strLen = e.character_maximum_length;
            BeanField beanField = pgsqlDBColumn.toBeanField();
            if (beanField == null) {
                logger.warn("不支持[table:{}] [name:{}] [type:{}]类型数据库字段,忽略此字段!", config.tableName, pgsqlDBColumn.name, pgsqlDBColumn.type);
            }
            return beanField;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public BeanField getTablePk(TableConfig config, Connection connection) {
        ColumnsBean pk = DBInfoUtil.findPKColumn(connection, config.config.dbInfo.db, config.tableName);
        switch (pk.udt_name) {
            case "int2","int4","int8","varchar"->{
                PgsqlDBColumn pgsqlDBColumn = new PgsqlDBColumn();
                pgsqlDBColumn.name = pk.column_name;
                pgsqlDBColumn.type = pk.udt_name;
                pgsqlDBColumn.comment = pk.description;
                pgsqlDBColumn.isNull = pk.is_nullable;
                pgsqlDBColumn.strLen = pk.character_maximum_length;
                BeanField beanField = pgsqlDBColumn.toBeanField();
                if (beanField == null) {
                    logger.warn("不支持[table:{}] [name:{}] [type:{}]类型数据库字段,忽略此字段!", config.tableName, pgsqlDBColumn.name, pgsqlDBColumn.type);
                }
                return beanField;
            }
            default-> {
                throw BaseException.get("pk[{},{},{}] not support", pk.table_name, pk.column_name, pk.udt_name);
            }
        }
    }
}
