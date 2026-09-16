package cn.bcd.lib.spring.database.mongo.service;

import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import cn.bcd.lib.spring.database.mongo.bean.SuperBaseBean;
import cn.bcd.lib.spring.database.mongo.bean.UserInterface;
import cn.bcd.lib.spring.database.mongo.util.ConditionUtil;
import com.mongodb.bulk.BulkWriteResult;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.data.util.Pair;

import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Created by Administrator on 2017/8/25.
 */
@SuppressWarnings("unchecked")
public class BaseService<T extends SuperBaseBean> {

    /**
     * 注意所有的类变量必须使用get方法获取
     * 因为类如果被aop代理了、代理对象的这些变量值都是null
     * 而get方法会被委托给真实对象的方法
     */


    private MongoTemplate mongoTemplate;

    private final BeanInfo<T> beanInfo;


    public BeanInfo<T> getBeanInfo() {
        return beanInfo;
    }

    public MongoTemplate getMongoTemplate() {
        return mongoTemplate;
    }

    @Autowired
    public void init(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public BaseService() {
        final Class<T> beanClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        beanInfo = new BeanInfo<>(beanClass);
    }

    /**
     * 获取代理对象
     * 需要如下注解开启 @EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
     * 如下场景使用
     * 同一个service中a()调用b()、其中b()符合aop切面定义、此时不会走aop逻辑、因为此时执行a()中this对象已经不是代理对象、此时需要getProxy().b()
     * 注意:
     * 此方法不要乱用、避免造成性能损失
     */
    protected BaseService<T> getProxy() {
        return (BaseService<T>) AopContext.currentProxy();
    }

    public List<T> list() {
        return list(null, null);
    }

    public List<T> list(Condition condition) {
        return list(condition, null);
    }

    public List<T> list(Sort sort) {
        return list(null, sort);
    }

    public List<T> list(Condition condition, Sort sort) {
        Query query = ConditionUtil.toQuery(condition);
        if (sort != null) {
            query.with(sort);
        }
        return getMongoTemplate().find(query, getBeanInfo().clazz);
    }

    public Page<T> page(Pageable pageable) {
        return page(null, pageable);
    }

    public Page<T> page(Condition condition, Pageable pageable) {
        Query query = ConditionUtil.toQuery(condition);
        final long total = getMongoTemplate().count(query, getBeanInfo().clazz);
        final int offset = pageable.getPageNumber() * pageable.getPageSize();
        if (total > offset) {
            query.with(pageable);
            List<T> list = getMongoTemplate().find(query, getBeanInfo().clazz);
            return new PageImpl<>(list, pageable, total);
        } else {
            return new PageImpl<>(Collections.emptyList(), pageable, total);
        }
    }

    public long count() {
        return getMongoTemplate().count(new Query(), getBeanInfo().clazz);
    }

    public long count(Condition condition) {
        Query query = ConditionUtil.toQuery(condition);
        return getMongoTemplate().count(query, getBeanInfo().clazz);
    }

    public T get(String id) {
        return getMongoTemplate().findById(id, getBeanInfo().clazz);
    }

    public T get(Condition condition) {
        Query query = ConditionUtil.toQuery(condition);
        return getMongoTemplate().findOne(query, getBeanInfo().clazz);
    }

    /**
     * 会设置创建信息或更新信息
     *
     * @param t
     * @return
     */
    public T save(T t) {
        BeanInfo<T> info = getBeanInfo();
        if (info.autoSetCreateInfo || info.autoSetUpdateInfo) {
            boolean isNew = t.getId() == null || !getMongoTemplate().exists(
                    Query.query(Criteria.where("id").is(t.getId())), info.clazz);
            if (isNew && info.autoSetCreateInfo) {
                setCreateInfo(t);
            } else if (!isNew && info.autoSetUpdateInfo) {
                setUpdateInfo(t);
            }
        }
        return getMongoTemplate().save(t);
    }

    /**
     * 会设置创建信息
     *
     * @param list
     * @return
     */
    public List<T> insertAll(List<T> list) {
        if (getBeanInfo().autoSetCreateInfo) {
            for (T t : list) {
                setCreateInfo(t);
            }
        }
        getMongoTemplate().insertAll(list);
        return list;
    }

    /**
     * 删除所有数据
     */
    public void deleteAll() {
        getMongoTemplate().remove(new Query(), getBeanInfo().clazz);
    }

    /**
     * 根据id删除
     *
     * @param ids
     */
    public void delete(String... ids) {
        if (ids.length == 1) {
            getMongoTemplate().remove(new Query(Criteria.where("id").is(ids[0])), getBeanInfo().clazz);
        } else if (ids.length > 1) {
            Query query = new Query(Criteria.where("id").in((Object[]) ids));
            getMongoTemplate().remove(query, getBeanInfo().clazz);
        }
    }

    /**
     * 根据条件删除
     *
     * @param condition
     */
    public void delete(Condition condition) {
        Query query = ConditionUtil.toQueryForUpdate(condition);
        if (query == null) {
            return;
        }
        getMongoTemplate().remove(query, getBeanInfo().clazz);
    }


    /**
     * 批量修改
     * 不会修改更新时间
     *
     * @param condition
     * @param updates
     * @return
     */
    public BulkWriteResult updateMulti(Condition condition, Update... updates) {
        if (updates.length == 0) {
            return null;
        } else {
            Query query = ConditionUtil.toQueryForUpdate(condition);
            if (query == null) {
                return null;
            }
            List<Pair<Query, UpdateDefinition>> collect = Arrays.stream(updates).map(e -> Pair.of(query, (UpdateDefinition) e)).toList();
            return getMongoTemplate().bulkOps(BulkOperations.BulkMode.UNORDERED, getBeanInfo().clazz).updateMulti(collect).execute();
        }
    }


    private void setCreateInfo(T t) {
        BaseBean bean = (BaseBean) t;
        bean.createTime = new Date();
        UserInterface user = getLoginUser();
        if (user != null) {
            bean.createUserId = user.getId();
            bean.createUserName = user.getUsername();
        }
    }

    private void setUpdateInfo(T t) {
        BaseBean bean = (BaseBean) t;
        bean.updateTime = new Date();
        UserInterface user = getLoginUser();
        if (user != null) {
            bean.updateUserId = user.getId();
            bean.updateUserName = user.getUsername();
        }
    }

    /**
     * 此方法主要是给内部创建信息、更新信息获取当前登陆用户使用
     * 不允许调用
     * 实现通过{@link UserGetter#getUser()}
     * 通过注册一个spring的bean {@link java.util.function.Supplier<UserInterface>}
     */
    private static UserInterface getLoginUser() {
        return UserGetter.getUser();
    }
}
