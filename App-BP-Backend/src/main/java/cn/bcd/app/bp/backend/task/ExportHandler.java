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
 * 子类是共享单例，查询参数、计数等单次执行数据应保存在局部变量中。
 *
 * @param <P> 可持久化为 JSON 的业务查询参数 DTO
 */
public abstract class ExportHandler<P> implements TaskHandler<P> {
    private static final Logger logger = LoggerFactory.getLogger(ExportHandler.class);
    private final FileService fileService;

    protected ExportHandler(FileService fileService) {
        this.fileService = fileService;
    }

    /** 返回子类注入的共享数据 Service；单次查询参数通过 condition/sort 传递。 */
    protected abstract BaseService<?> service();

    /** 执行时根据参数构造查询条件，返回 null 表示不限制；此处只准备条件，不预取结果。 */
    protected abstract Condition condition(P payload);

    /** 默认不排序。分批分页导出通常应由子类指定稳定排序，例如主键升序。 */
    protected Sort sort(P payload) {
        return Sort.unsorted();
    }

    /**
     * 同线程完成分批查询、写 Excel、关闭文件、上传和本地清理。
     * 停止检查位于入口、批次写入前后及上传前；已经进入的查询或上传不会被强制中断。
     * 正常返回后由 Worker 保存成功状态，本方法不直接更新任务状态。
     *
     * @return export/ 下的文件服务路径，保存到任务 result
     * @throws Exception 生成或上传失败，或者在检查点响应停止请求
     */
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
            // 先关闭 ExcelWriter，确保文件完整写出后再交给上传逻辑。
            try (ExcelWriter writer = FesodSheet.write().file(path.toFile()).build()) {
                WriteSheet sheet = FesodSheet.writerSheet(0).build();
                boolean empty = true;
                for (List<?> batch : service.batchIterable(1000, condition, sort)) {
                    context.checkStop();
                    writer.write(batch, sheet);
                    empty = false;
                    context.checkStop();
                }
                // 空结果仍生成可下载的工作表。
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
