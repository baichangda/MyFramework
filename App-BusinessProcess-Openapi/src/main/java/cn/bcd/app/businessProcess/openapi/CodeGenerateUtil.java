package cn.bcd.app.businessProcess.openapi;

import cn.bcd.lib.database.jdbc.code.CodeGenerator;
import cn.bcd.lib.database.jdbc.code.Config;
import cn.bcd.lib.database.jdbc.code.TableConfig;

public class CodeGenerateUtil {
    public static void main(String[] args) {
        String path = "D:\\work\\bcd\\MyFramework\\Server-Business-Process-Openapi\\src\\main\\java\\cn\\bcd\\server\\business\\process\\openapi";
        final TableConfig.Helper helper = TableConfig.newHelper();
        helper.needCreateBeanFile = true;
        helper.needCreateServiceFile = true;
        helper.needCreateControllerFile = true;
        helper.needValidateBeanField = true;
        helper.needValidateSaveParam = true;
        helper
                .addModule("OpenapiUser", "openapi用户", "t_openapi_user");
        Config config = Config.newConfig(path).addTableConfig(helper.toTableConfigs());
        CodeGenerator.MYSQL.generate(config);
//        CodeGenerator.PGSQL.generate(config);
    }
}
