package cn.bcd.lib.spring.database.jdbc.code;



import java.util.ArrayList;
import java.util.List;

public class TableConfig {
    public Config config;
    public boolean needCreateQueryRequestFile = true;
    public boolean needCreateSaveRequestFile = true;
    public boolean needCreateQueryResponseFile = true;
    //模块名(英文)
    public String moduleName;
    //模块名(中文)
    public String moduleNameCN;
    //表名
    public String tableName;
    //是否创建bean文件(默认是)
    public boolean needCreateBeanFile = true;
    //是否创建service文件(默认是)
    public boolean needCreateServiceFile = true;
    //是否创建controller文件(默认是)
    public boolean needCreateControllerFile = true;

    public static Helper newHelper() {
        return new Helper();
    }


    public static class Helper {
        public boolean needCreateQueryRequestFile = true;
        public boolean needCreateSaveRequestFile = true;
        public boolean needCreateQueryResponseFile = true;

        //是否创建bean文件(默认是)
        public boolean needCreateBeanFile = true;
        //是否创建service文件(默认是)
        public boolean needCreateServiceFile = true;
        //是否创建controller文件(默认是)
        public boolean needCreateControllerFile = true;

        //模块名(英文)
        public List<String> moduleName = new ArrayList<>();
        //模块名(中文)
        public List<String> moduleNameCN = new ArrayList<>();
        //表名
        public List<String> tableName = new ArrayList<>();

        private Helper() {

        }

        public Helper addModule(String moduleName, String moduleNameCN, String tableName) {
            this.moduleName.add(moduleName);
            this.moduleNameCN.add(moduleNameCN);
            this.tableName.add(tableName);
            return this;
        }

        public List<TableConfig> toTableConfigs() {
            List<TableConfig> res = new ArrayList<>(this.moduleName.size());
            for (int i = 0; i < this.moduleName.size(); i++) {
                TableConfig tableConfig = new TableConfig();
                tableConfig.moduleName = this.moduleName.get(i);
                tableConfig.moduleNameCN = this.moduleNameCN.get(i);
                tableConfig.tableName = this.tableName.get(i);
                tableConfig.needCreateBeanFile = this.needCreateBeanFile;
                tableConfig.needCreateServiceFile = this.needCreateServiceFile;
                tableConfig.needCreateControllerFile = this.needCreateControllerFile;
                tableConfig.needCreateQueryRequestFile = this.needCreateQueryRequestFile;
                tableConfig.needCreateSaveRequestFile = this.needCreateSaveRequestFile;
                tableConfig.needCreateQueryResponseFile = this.needCreateQueryResponseFile;
                res.add(tableConfig);
            }
            return res;
        }

    }

}