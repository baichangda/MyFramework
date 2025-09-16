package cn.bcd.lib.base.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public interface Initializable {

    Logger logger = LoggerFactory.getLogger(Initializable.class);

    void init();

    /**
     * 初始化顺序、越小优先级越高
     * 默认优先级100
     * <p>
     * 此方法应该在程序启动后调用、用于按顺序初始化
     *
     * @return
     */
    default int order() {
        return 100;
    }

    static void initByOrder(List<Initializable> list) {
        logger.info("""
                --------------initAll start--------------
                {}
                --------------initAll finish--------------
                """, list.stream().map(e -> e.order() + " " + e.getClass().getName()).collect(Collectors.joining("\n")));
        list.sort(Comparator.comparingInt(Initializable::order));
        for (Initializable e : list) {
            e.init();
        }
    }
}
