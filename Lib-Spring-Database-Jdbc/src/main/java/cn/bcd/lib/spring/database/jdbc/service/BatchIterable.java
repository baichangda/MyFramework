package cn.bcd.lib.spring.database.jdbc.service;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import cn.bcd.lib.spring.database.jdbc.condition.ConditionUtil;
import cn.bcd.lib.spring.database.jdbc.condition.ConvertRes;
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
        private int offset;

        private final BaseService<T> baseService;
        private final ConvertRes convertRes;
        private final Sort sort;
        private final int batch;

        public BatchIterator(int batch, BaseService<T> baseService, Condition condition, Sort sort) {
            if (batch <= 0) {
                throw BaseException.get("param batch[{}] error", batch);
            }
            hasNext = true;
            offset = 0;
            this.batch = batch;
            this.baseService = baseService;
            this.convertRes = ConditionUtil.convertCondition(condition, baseService.getBeanInfo());
            this.sort = sort;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public List<T> next() {
            List<T> content = baseService.list(convertRes, sort, offset, batch);
            offset += batch;
            hasNext = content.size() == batch;
            return content;
        }
    }
}



