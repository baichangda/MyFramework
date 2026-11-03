package cn.bcd.lib.spring.database.mongo.code;

import cn.bcd.lib.spring.database.mongo.anno.DocumentExt;
import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class MongoGeneratorFixtures {
    @Document("empty")
    public static class EmptyBean extends cn.bcd.lib.spring.database.mongo.bean.SuperBaseBean {
    }

    public static class GenericBean<T> extends BaseBean {
        public T inherited;
        public T[] inheritedArray;
    }

    public static class Detail {
        public String value;
    }

    @Document("fixture")
    public static class FixtureBean extends GenericBean<String> {
        @Field("display_name")
        public String name;
        public boolean enabled;
        public int count;
        public float ratio;
        public double score;
        public char grade;
        public Date time;
        public List<Map<String, Detail>> details;
        public List<? extends Detail> polymorphic;
        public Map<String, Detail> lookup;
        public int[] samples;
        private BigDecimal amount;
        private final String readOnly = "fixed";
        @Transient
        public String ignored;
        public transient String javaIgnored;
        public static String shared;

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getReadOnly() {
            return readOnly;
        }
    }

    @Document("manual")
    @DocumentExt(autoSetCreateInfo = false, autoSetUpdateInfo = false)
    public static class ManualBean extends FixtureBean {
    }
}
