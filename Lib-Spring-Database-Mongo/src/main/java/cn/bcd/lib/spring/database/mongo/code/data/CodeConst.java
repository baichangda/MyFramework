package cn.bcd.lib.spring.database.mongo.code.data;

import freemarker.template.Configuration;
import freemarker.template.Version;

/**
 * Created by Administrator on 2017/8/14.
 */
public class CodeConst {
    public final static Version FREEMARKER_VERSION = Configuration.VERSION_2_3_34;

    public final static String CLASS_OUT_DIR_PATH = "out/production/classes";
    public final static String CLASS_BUILD_DIR_PATH = "build/classes/java/main";
    public final static String SOURCE_DIR_PATH = "src/main/java";

    public final static String MODULE_DIR_PATH = System.getProperty("user.dir") + "/Lib-Spring-Database-Mongo";

    public final static String TEMPLATE_DIR_PATH = MODULE_DIR_PATH + "/src/main/resources/template";
}
