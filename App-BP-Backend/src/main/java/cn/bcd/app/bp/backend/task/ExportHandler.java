package cn.bcd.app.bp.backend.task;

import cn.bcd.app.bp.backend.file.FileService;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 通用导出流程。子类注册为 Spring Bean，提供任务类型、查询参数类型和数据 Service。
 * payload 仅包含业务查询参数；Condition、排序和查询结果都在领取任务后由执行线程生成。
 */
public abstract class ExportHandler<P> implements TaskHandler<P> {
    private static final Logger logger = LoggerFactory.getLogger(ExportHandler.class);
    private final FileService fileService;

    protected ExportHandler(FileService fileService) {
        this.fileService = fileService;
    }

    /** 返回子类注入的数据 Service，不通过 Bean 名称或反射寻找服务。 */
    protected abstract BaseService<?> service();

    /** 根据持久化的业务参数构造条件，返回 null 表示不限制。 */
    protected abstract Condition condition(P payload);

    /** 子类需要稳定排序时覆盖此方法。 */
    protected Sort sort(P payload) {
        return Sort.unsorted();
    }

    @Override
    public final String execute(TaskContext context, P payload) throws Exception {
        context.checkStop();
        BaseService<?> service = service();
        Condition condition = condition(payload);
        Sort sort = sort(payload);
        Path dir = Path.of("temp/export");
        Files.createDirectories(dir);
        Path path = Files.createTempFile(dir, "task-" + context.task().id + "-", ".xlsx");
        try {
            try (ExcelWriter writer = FesodSheet.write().file(path.toFile()).build()) {
                WriteSheet sheet = FesodSheet.writerSheet(0).build();
                boolean empty = true;
                for (List<?> batch : service.batchIterable(1000, condition, sort)) {
                    context.checkStop();
                    writer.write(batch, sheet);
                    empty = false;
                    context.checkStop();
                }
                if (empty) {
                    writer.write(List.of(), sheet);
                }
            }
            context.checkStop();
            // 文件名包含任务 ID，不同类型的导出也不会互相覆盖。
            String fileName = context.task().name.replaceAll("[^\\p{L}\\p{N}._-]", "_")
                    + "_" + context.task().id + ".xlsx";
            String serverPath = "export/" + fileName;
            fileService.upload(serverPath, path);
            return serverPath;
        } finally {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                // 清理失败不能覆盖已经上传成功或主动停止的执行结果。
                logger.error("Cannot delete task export file {}", path, ex);
            }
        }
    }
}
