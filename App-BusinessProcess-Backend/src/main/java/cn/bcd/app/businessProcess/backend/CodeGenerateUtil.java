package cn.bcd.app.businessProcess.backend;

import cn.bcd.lib.spring.database.jdbc.code.CodeGenerator;
import cn.bcd.lib.spring.database.jdbc.code.Config;
import cn.bcd.lib.spring.database.jdbc.code.TableConfig;

public class CodeGenerateUtil {
    public static void main(String[] args) {
        String path = "D:\\work\\bcd\\MyFramework\\Server-Business-Process-Backend\\src\\main\\java\\cn\\bcd\\server\\business\\process\\backend\\sys\\test";
        final TableConfig.Helper helper = TableConfig.newHelper();
        helper.needCreateBeanFile = true;
        helper.needCreateServiceFile = true;
        helper.needCreateControllerFile = true;
        helper.needValidateBeanField = true;
        helper.needValidateSaveParam = true;
        helper
                .addModule("Permission", "权限", "t_sys_permission");
        Config config = Config.newConfig(path).addTableConfig(helper.toTableConfigs());
        CodeGenerator.MYSQL.generate(config);
//        CodeGenerator.PGSQL.generate(config);
    }
}
