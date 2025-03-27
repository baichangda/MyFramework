package cn.bcd.lib.database.jdbc.condition;

import java.util.List;

public class ConvertRes {
    public String sql;
    public final List<Object> paramList;
    public ConvertRes(String sql, List<Object> paramList) {
        this.sql = sql;
        this.paramList = paramList;
    }
}
