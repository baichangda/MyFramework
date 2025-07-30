package cn.bcd.lib.storage.cassandra.raw;

import cn.bcd.lib.storage.cassandra.CassandraConfig;
import cn.bcd.lib.storage.cassandra.PageResult;
import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.*;
import com.google.common.base.Function;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CassandraUtil_gb32960 {

    static final Function<Row, RawData> rowFunction = row -> {
        RawData rawData = new RawData();
        rawData.vin = row.getString("vin");
        rawData.collectTime = row.getInstant("collect_time");
        rawData.type = row.getInt("type");
        rawData.gwReceiveTime = row.getInstant("gw_receive_time");
        rawData.gwSendTime = row.getInstant("gw_send_time");
        rawData.parseReceiveTime = row.getInstant("parse_receive_time");
        rawData.hex = row.getString("hex");
        return rawData;
    };


    /**
     * 范围查询
     *
     * @param vin             vin
     * @param beginTime       开始时间 包含 必填
     * @param endTime         结束时间 不包含 必填
     * @param pagingState     分页状态器
     * @param pageSize        页大小
     * @param collectTimeDesc 是否时间采集时间降序
     * @return
     */
    public static CompletableFuture<PageResult<RawData>> page_rawData(String vin, Instant beginTime, Instant endTime, String pagingState, int pageSize, boolean collectTimeDesc) {
        List<RawData> list = new ArrayList<>();
        SimpleStatementBuilder statementBuilder = new SimpleStatementBuilder("select * from " + CassandraConfig.keySpace + ".raw_data where vin=? and collect_time>=? and collect_time<? order by collect_time " + (collectTimeDesc ? "desc" : "asc"))
                .addPositionalValues(vin, beginTime, endTime);
        statementBuilder.setPageSize(pageSize);
        if (pagingState != null) {
            statementBuilder.setPagingState(PagingState.fromString(pagingState).getRawPagingState());
        }
        SimpleStatement statement = statementBuilder.build();
        return CassandraConfig.session.executeAsync(statement).thenApply(resultSet -> {
            for (Row row : resultSet.currentPage()) {
                list.add(rowFunction.apply(row));
            }
            return new PageResult<>(list, resultSet.getExecutionInfo().getSafePagingState());
        }).toCompletableFuture();
    }

    public static CompletableFuture<RawData> get_rawData(String vin, Instant collectTime, int type) {
        SimpleStatement statement = new SimpleStatementBuilder("select * from " + CassandraConfig.keySpace + ".raw_data where vin=? and collect_time=? and type=?")
                .addPositionalValues(vin, collectTime, type)
                .build();
        return CassandraConfig.session.executeAsync(statement).thenApply(resultSet -> {
            Row one = resultSet.one();
            if (one == null) {
                return null;
            } else {
                return rowFunction.apply(one);
            }
        }).toCompletableFuture();
    }

    /**
     * 批量保存
     *
     * @param list
     */
    public static CompletableFuture<AsyncResultSet> save_rawData(List<RawData> list) {
        if (list.isEmpty()) {
            return null;
        }
        PreparedStatement preparedStatement = CassandraConfig.session.prepare("insert into " + CassandraConfig.keySpace + ".raw_data(vin,collect_time,type,gw_receive_time,gw_send_time,parse_receive_time,hex) values(?,?,?,?,?,?,?)");
        BatchStatementBuilder batchStatementBuilder = new BatchStatementBuilder(BatchType.UNLOGGED)
                .setConsistencyLevel(ConsistencyLevel.ONE);
        for (RawData rawData : list) {
            BoundStatement boundStatement = preparedStatement.bind(rawData.vin, rawData.collectTime, rawData.type, rawData.gwReceiveTime, rawData.gwSendTime, rawData.parseReceiveTime, rawData.hex);
            batchStatementBuilder.addStatement(boundStatement);
        }
        return CassandraConfig.session.executeAsync(batchStatementBuilder.build()).toCompletableFuture();
    }

}
