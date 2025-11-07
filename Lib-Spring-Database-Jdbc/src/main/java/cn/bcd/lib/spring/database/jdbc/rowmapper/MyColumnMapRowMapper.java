package cn.bcd.lib.spring.database.jdbc.rowmapper;

import cn.bcd.lib.base.util.StringUtil;
import org.springframework.jdbc.core.ColumnMapRowMapper;

public class MyColumnMapRowMapper extends ColumnMapRowMapper {
    public final static MyColumnMapRowMapper ROW_MAPPER = new MyColumnMapRowMapper();

    @Override
    protected String getColumnKey(String columnName) {
        return StringUtil.splitCharToCamelCase(columnName, '_');
    }
}
