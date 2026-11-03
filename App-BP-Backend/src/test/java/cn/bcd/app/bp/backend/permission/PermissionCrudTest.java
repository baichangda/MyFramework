package cn.bcd.app.bp.backend.permission;

import cn.bcd.lib.spring.database.jdbc.sqlite.SqliteJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PermissionCrudTest {
    @Test
    void listPageAndDeleteHaveNoGeneratedQuantityLimits() {
        try (SqliteJdbc sqlite = SqliteJdbc.inMemory()) {
            var jdbc = sqlite.jdbcTemplate();
            jdbc.execute("create table t_sys_permission(id integer primary key, name text, resource text)");
            jdbc.execute("""
                    with recursive ids(id) as (select 1 union all select id+1 from ids where id<1001)
                    insert into t_sys_permission select id, 'read', 'user:read' from ids
                    """);
            PermissionService service = new PermissionService(jdbc);
            service.init(jdbc, sqlite.transactionTemplate());
            assertEquals(1001, service.listPermission(new PermissionQueryRequest()).size());
            assertEquals(1001, service.pagePermission(new PermissionQueryRequest(), 1, 1001).getContent().size());
            service.deletePermission(java.util.stream.LongStream.rangeClosed(1, 1001).toArray());
            assertEquals(0, jdbc.queryForObject("select count(*) from t_sys_permission", Integer.class));
        }
    }

    @Test
    void independentModelsSupportSaveListPageAndDelete() throws Exception {
        try (SqliteJdbc sqlite = SqliteJdbc.inMemory()) {
            var jdbc = sqlite.jdbcTemplate();
            jdbc.execute("""
                    create table t_sys_permission(
                        id integer primary key autoincrement, name text not null, resource text not null,
                        create_time timestamp, create_user_id integer, create_user_name text,
                        update_time timestamp, update_user_id integer, update_user_name text)
                    """);
            PermissionService service = new PermissionService(jdbc);
            service.init(jdbc, sqlite.transactionTemplate());
            var mvc = MockMvcBuilders.standaloneSetup(new PermissionController(service)).build();
            mvc.perform(post("/api/permission/save").contentType("application/json")
                    .content("{\"name\":\"read\",\"resource\":\"user:read\"}"))
                    .andExpect(status().isOk());

            Long id = jdbc.queryForObject("select id from t_sys_permission", Long.class);
            Object created = jdbc.queryForMap("select create_time from t_sys_permission").get("create_time");
            assertNotNull(created);
            mvc.perform(get("/api/permission/list").param("name", "read"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].resource").value("user:read"));
            mvc.perform(get("/api/permission/page").param("name", "read").param("pageNum", "1").param("pageSize", "5"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));

            mvc.perform(post("/api/permission/save").contentType("application/json")
                    .content("{\"id\":" + id + ",\"name\":\"write\",\"resource\":\"user:write\"}"))
                    .andExpect(status().isOk());
            assertEquals("write", jdbc.queryForObject("select name from t_sys_permission", String.class));
            assertEquals(created, jdbc.queryForMap("select create_time from t_sys_permission").get("create_time"));
            assertNotNull(jdbc.queryForMap("select update_time from t_sys_permission").get("update_time"));

            mvc.perform(delete("/api/permission/delete").param("ids", id + "," + id))
                    .andExpect(status().isOk());
            assertEquals(0, jdbc.queryForObject("select count(*) from t_sys_permission", Integer.class));
        }
    }
}
