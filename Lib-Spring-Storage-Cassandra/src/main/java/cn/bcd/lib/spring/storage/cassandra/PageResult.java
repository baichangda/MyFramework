package cn.bcd.lib.spring.storage.cassandra;

import com.datastax.oss.driver.api.core.cql.PagingState;

import java.util.List;

public class PageResult<T> {
    public final List<T> list;
    public final String pagingState;

    public PageResult(List<T> list, PagingState pagingState) {
        this.list = list;
        this.pagingState = pagingState == null ? null : pagingState.toString();
    }
}
