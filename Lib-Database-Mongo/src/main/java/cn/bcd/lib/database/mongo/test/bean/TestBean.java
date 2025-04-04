package cn.bcd.lib.database.mongo.test.bean;

import cn.bcd.lib.database.mongo.bean.SuperBaseBean;
import cn.bcd.lib.database.mongo.code.CodeGenerator;
import cn.bcd.lib.database.mongo.code.CollectionConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Getter
@Setter
@Document(collection = "test")
//测试类
public class TestBean extends SuperBaseBean {
    @Schema(description = "vin")
    public String vin;
    @Schema(description = "时间")
    public Date time;
    public static void main(String[] args) {
        CollectionConfig config = new CollectionConfig("Test", "测试", TestBean.class);
        config.needCreateControllerFile = true;
        config.needCreateServiceFile = true;
        config.needValidateSaveParam = true;
        CodeGenerator.generate(config);
    }
}
