package cn.bcd.lib.jooq;

import cn.bcd.lib.jooq.generate.tables.TSysUser;
import cn.bcd.lib.jooq.generate.tables.records.TSysUserRecord;
import org.jooq.CloseableDSLContext;
import org.jooq.codegen.GenerationTool;
import org.jooq.impl.DSL;
import org.jooq.meta.jaxb.*;
import org.jooq.meta.mysql.MySQLDatabase;

import java.util.List;


public class Test {
    public static void main(String[] args) throws Exception {
        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("com.mysql.cj.jdbc.Driver")
                        .withUrl("jdbc:mysql://127.0.0.1:13306/bcd")
                        .withUser("root")
                        .withPassword("root")
                ).withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName(MySQLDatabase.class.getName())
                                .withInputSchema("bcd")
                                .withIncludes(".*")
                                .withExcludes("""
                                        xxl_.*
                                        """)
                        ).withGenerate(new Generate()
                                .withDefaultCatalog(false)
                                .withDefaultSchema(false)
                        )
                        .withTarget(new Target()
                                .withClean(true)
                                .withPackageName("cn.bcd.lib.jooq.generate")
                                .withDirectory("D:\\work\\bcd\\MyFramework\\Lib-Jooq\\src\\main\\java")
                        )
                );
        GenerationTool.generate(configuration);

        try (CloseableDSLContext dslContext = DSL.using("jdbc:mysql://127.0.0.1:13306/bcd", "root", "root")) {
            List<TSysUserRecord> users = dslContext
                    .select(TSysUser.T_SYS_USER.USERNAME, TSysUser.T_SYS_USER.PASSWORD)
                    .from(TSysUser.T_SYS_USER)
                    .where(TSysUser.T_SYS_USER.USERNAME.isNotNull())
                    .fetchInto(TSysUserRecord.class);
            for (TSysUserRecord user : users) {
                System.out.println(user.getUsername());
            }

            TSysUserRecord record = dslContext.newRecord(TSysUser.T_SYS_USER);
            record.setUsername("test");
            record.setPassword("test");
            record.insert();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
