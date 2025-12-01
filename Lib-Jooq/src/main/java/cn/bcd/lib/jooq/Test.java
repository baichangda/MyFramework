package cn.bcd.lib.jooq;

import cn.bcd.lib.jooq.generate.tables.TSysUser;
import cn.bcd.lib.jooq.generate.tables.records.TSysUserRecord;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;

import java.util.List;


public class Test {
    public static void main(String[] args) throws Exception {
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
