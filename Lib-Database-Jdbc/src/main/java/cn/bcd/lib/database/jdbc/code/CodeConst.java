package cn.bcd.lib.database.jdbc.code;

import freemarker.template.Configuration;
import freemarker.template.Version;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Administrator on 2017/8/14.
 */
public class CodeConst {
    public final static Version FREEMARKER_VERSION = Configuration.VERSION_2_3_32;

    public final static String MODULE_DIR_PATH = System.getProperty("user.dir") + "/Lib-Database-Jdbc";

    public final static String TEMPLATE_DIR_PATH = MODULE_DIR_PATH + "/src/main/resources/template";

    public final static Set<String> CREATE_INFO_FIELD_NAME = new HashSet<>();

    static {
        CREATE_INFO_FIELD_NAME.add("createTime");
        CREATE_INFO_FIELD_NAME.add("updateTime");
        CREATE_INFO_FIELD_NAME.add("createUserId");
        CREATE_INFO_FIELD_NAME.add("createUserName");
        CREATE_INFO_FIELD_NAME.add("updateUserId");
        CREATE_INFO_FIELD_NAME.add("updateUserName");
    }

    public enum PkType {
        Integer,
        Long,
        String
    }


}
