package cn.bcd.app.bp.backend.base.util;

import cn.bcd.lib.spring.database.jdbc.code.CodeGenerator;
import cn.bcd.lib.spring.database.jdbc.code.Config;
import cn.bcd.lib.spring.database.jdbc.code.TableConfig;

public class CodeGenerateUtil {
    public static void main(String[] args) {
        String path = "D:\\work\\bcd\\MyFramework\\App-BP-Backend\\src\\main\\java\\cn\\bcd\\app\\bp\\backend\\test";
        final TableConfig.Helper helper = TableConfig.newHelper();
        helper.needCreateBeanFile = true;
        helper.needCreateServiceFile = true;
        helper.needCreateControllerFile = true;
        helper
                .addModule("Permission", "权限", "t_sys_permission");
        Config config = Config.newConfig(path).addTableConfig(helper.toTableConfigs());
        CodeGenerator.MYSQL.generate(config);
//        CodeGenerator.PGSQL.generate(config);
    }
}
