package cn.bcd.lib.database.jdbc.service;

import cn.bcd.lib.database.common.condition.Condition;
import cn.bcd.lib.database.jdbc.bean.SuperBaseBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Iterator;
import java.util.List;

public class BatchIterable<T extends SuperBaseBean> implements Iterable<List<T>> {
    private final int batch;
    private final BaseService<T> baseService;
    private final Condition condition;
    private final Sort sort;

    public BatchIterable(int batch, BaseService<T> baseService, Condition condition, Sort sort) {
        this.batch = batch;
        this.baseService = baseService;
        this.condition = condition;
        this.sort = sort;
    }

    public BatchIterable(int batch, BaseService<T> baseService) {
        this(batch, baseService, null, null);
    }

    @Override
    public Iterator<List<T>> iterator() {
        return new BatchIterator<>(batch, baseService, condition, sort);
    }

    static class BatchIterator<T extends SuperBaseBean> implements Iterator<List<T>> {
        private boolean hasNext;
        private Pageable pageable;

        private final BaseService<T> baseService;
        private final Condition condition;

        public BatchIterator(int batch, BaseService<T> baseService, Condition condition, Sort sort) {
            hasNext = true;
            if (sort == null) {
                pageable = PageRequest.of(0, batch);
            } else {
                pageable = PageRequest.of(0, batch, sort);
            }
            this.baseService = baseService;
            this.condition = condition;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public List<T> next() {
            Page<T> page = baseService.page(condition, pageable);
            pageable = page.nextPageable();
            hasNext = page.hasNext();
            return page.getContent();
        }
    }
}



