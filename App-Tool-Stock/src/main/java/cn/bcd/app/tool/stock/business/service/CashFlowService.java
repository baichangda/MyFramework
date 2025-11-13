package cn.bcd.app.tool.stock.business.service;

import cn.bcd.app.tool.stock.base.support_eastmoney.CashFlowData;
import cn.bcd.app.tool.stock.base.support_eastmoney.EastMoneyUtil;
import cn.bcd.app.tool.stock.business.bean.CashFlowBean;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class CashFlowService extends BaseService<CashFlowBean> {
    @Scheduled(cron = "0 5 15 * * ?")
    public void collectCashFlowToday() {
        String[] codes = {"002050"};
        collectCashFlowToday(codes);
    }

    public void collectCashFlowToday(String... codes) {
        String day = DateZoneUtil.dateToStr_yyyy_MM_dd(new Date());
        for (String code : codes) {
            List<CashFlowData> list = EastMoneyUtil.fetchCashFlowToday(code);
            List<CashFlowBean> beanList = list.stream().map(e -> CashFlowBean.from(code, e)).toList();
            delete(Condition.and(
                    StringCondition.EQUAL("code", code),
                    StringCondition.EQUAL("day", day)
            ));
            insertBatch(beanList);
        }
    }
}
