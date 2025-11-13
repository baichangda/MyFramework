package cn.bcd.app.tool.stock;

import cn.bcd.lib.spring.database.jdbc.code.CodeGenerator;
import cn.bcd.lib.spring.database.jdbc.code.Config;
import cn.bcd.lib.spring.database.jdbc.code.TableConfig;

public class CodeGenerateUtil {
    public static void main(String[] args) {
        String path = "D:\\work\\bcd\\MyFramework\\App-Tool-Stock\\src\\main\\java\\cn\\bcd\\app\\tool\\stock\\business";
        final TableConfig.Helper helper = TableConfig.newHelper();
        helper.needCreateBeanFile = true;
        helper.needCreateServiceFile = true;
        helper.needCreateControllerFile = false;
        helper
                .addModule("CashFlow", "资金流向", "t_cash_flow");
        Config config = Config.newConfig(path).addTableConfig(helper.toTableConfigs());
        CodeGenerator.MYSQL.generate(config);
//        CodeGenerator.PGSQL.generate(config);
    }
}
