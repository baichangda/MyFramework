package cn.bcd.lib.spring.database.mongo.code;

import cn.bcd.lib.spring.database.mongo.code.data.CodeConst;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class CollectionConfig {
    public String templateDirPath;
    public String targetDirPath;
    public String moduleName;
    public String moduleNameCN;
    public Class<?> clazz;
    public boolean needCreateServiceFile = true;
    public boolean needCreateControllerFile = true;
    public boolean needCreateQueryRequestFile = true;
    public boolean needCreateSaveRequestFile = true;
    public boolean needCreateQueryResponseFile = true;

    public CollectionConfig(String moduleName, String moduleNameCN, Class<?> clazz) {
        this(moduleName, moduleNameCN, clazz, null);
        parseTargetDirPath();
    }

    /** 指定输出目录时不依赖 Bean 的编译输出位置，适用于预览和外部模型。 */
    public CollectionConfig(String moduleName, String moduleNameCN, Class<?> clazz, String targetDirPath) {
        this.moduleName = moduleName;
        this.moduleNameCN = moduleNameCN;
        this.clazz = clazz;
        this.targetDirPath = targetDirPath;
    }

    private void parseTargetDirPath() {
        try {
            var resource = clazz.getResource("");
            if (resource == null || !resource.getProtocol().equals("file")) {
                throw new IllegalArgumentException("Specify targetDirPath for bean " + clazz.getName());
            }
            String path = Path.of(resource.toURI()).toString().replace('\\', '/');
            if (path.contains(CodeConst.CLASS_BUILD_DIR_PATH)) {
                path = path.replace(CodeConst.CLASS_BUILD_DIR_PATH, CodeConst.SOURCE_DIR_PATH);
            } else if (path.contains(CodeConst.CLASS_OUT_DIR_PATH)) {
                path = path.replace(CodeConst.CLASS_OUT_DIR_PATH, CodeConst.SOURCE_DIR_PATH);
            } else {
                throw new IllegalArgumentException("Specify targetDirPath for bean " + clazz.getName());
            }
            targetDirPath = Path.of(path).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid bean resource", ex);
        }
    }

    public CollectionConfig setClazz(Class<?> clazz) {
        this.clazz = clazz;
        parseTargetDirPath();
        return this;
    }
}
