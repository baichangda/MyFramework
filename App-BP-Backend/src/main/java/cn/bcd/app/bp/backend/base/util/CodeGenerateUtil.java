package cn.bcd.app.bp.backend.base.util;

import cn.bcd.lib.spring.database.jdbc.code.CodeGenerator;
import cn.bcd.lib.spring.database.jdbc.code.Config;
import cn.bcd.lib.spring.database.jdbc.code.TableConfig;

public class CodeGenerateUtil {
    public static void main(String[] args) {
        String path = "D:\\workspace\\MyFramework\\App-BP-Backend\\src\\main\\java\\cn\\bcd\\app\\bp\\backend";
        final TableConfig.Helper helper = TableConfig.newHelper();
        helper.needCreateBeanFile = true;
        helper.needCreateServiceFile = true;
        helper.needCreateControllerFile = true;
        helper
                .addModule("PermissionTest", "权限", "t_sys_permission");
        // 默认生成 Bean、QueryRequest、SaveRequest、QueryResponse、Service、Controller。
        // Request 默认包含全部非审计字段；id 只用于查询和区分新增/修改。
        Config config = Config.newConfig(path).addTableConfig(helper.toTableConfigs());
        // 生成时覆盖目标目录中的同名文件。
        // 生成后直接编辑 Request、QueryResponse 和 Service，删除不需要的字段或调整业务规则。
        CodeGenerator.MYSQL.generate(config);
//        CodeGenerator.PGSQL.generate(config);
    }
}
