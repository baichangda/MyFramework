package cn.bcd.app.businessProcess.backend.sys.service;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import cn.bcd.app.businessProcess.backend.base.support_task.TaskBuilder;
import cn.bcd.app.businessProcess.backend.base.support_task.TaskDao;
import cn.bcd.app.businessProcess.backend.sys.bean.TaskBean;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.builder.ExcelWriterSheetBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 */
@Service
public class TaskService extends BaseService<TaskBean> implements TaskDao<TaskBean, Long> {

    static Logger logger = LoggerFactory.getLogger(TaskService.class);

    @Autowired
    FileService fileService;

    public final TaskBuilder<TaskBean, Long> taskBuilder;

    public TaskService() {
        taskBuilder = TaskBuilder.newInstance(this, 4);
        taskBuilder.init();
    }

    @Override
    public TaskBean doCreate(TaskBean task) {
        insert(task);
        return task;
    }

    @Override
    public TaskBean doRead(Long id) {
        return get(id);
    }

    @Override
    public void doUpdate(TaskBean task) {
        update(task);
    }

    static Path exportDirPath = Paths.get("temp/export");
    static String serverDirPath = "export";

    static {
        try {
            Files.createDirectories(exportDirPath);
        } catch (IOException e) {
            logger.error("error", e);
        }
    }

    /**
     * 开始导出任内务
     * @param taskName
     * @param service
     * @param condition
     * @param sort
     * @return
     */
    public long startTask_export(String taskName, BaseService<?> service, Condition condition, Sort sort) {
        TaskBean taskBean = new TaskBean(taskName, 1);
        return taskBuilder.register(taskBean, runnable -> {
            String fileName = taskName + "_" + DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date()) + ".xlsx";
            Path exportPath = Paths.get(exportDirPath + "/" + fileName);
            try {
                //导出文件
                ExcelWriterBuilder excelWriterBuilder = EasyExcel.write();
                ExcelWriterSheetBuilder excelWriterSheetBuilder = EasyExcel.writerSheet(0);
                try (ExcelWriter excelWriter = excelWriterBuilder.build()) {
                    boolean empty = true;
                    WriteSheet writeSheet = excelWriterSheetBuilder.build();
                    for (List<?> list : service.batchIterable(1000, condition, sort)) {
                        //检测停止
                        if (runnable.stop) {
                            return false;
                        }
                        excelWriter.write(list, writeSheet);
                        empty = false;
                        //检测停止
                        if (runnable.stop) {
                            return false;
                        }
                    }
                    if (empty) {
                        excelWriter.write(new ArrayList<>(), writeSheet);
                    }
                }
                //检测停止
                if (runnable.stop) {
                    return false;
                }
                //上传到文件服务器
                String serverPath = serverDirPath + "/" + fileName;
                fileService.upload(serverPath, exportPath);
                runnable.task.result = serverPath;

                return true;
            } finally {
                try {
                    Files.deleteIfExists(exportPath);
                } catch (IOException ex) {
                    logger.error("error", ex);
                }
            }
        });
    }

    public void deleteTaskAndDeleteFile(long... ids) {
        List<TaskBean> list = list(ids);
        String[] arr = list.stream().filter(e -> e.type == 1 && e.result != null).map(e -> e.result).toArray(String[]::new);
        delete(ids);
        if (arr.length != 0) {
            fileService.delete(arr);
        }
    }
}
