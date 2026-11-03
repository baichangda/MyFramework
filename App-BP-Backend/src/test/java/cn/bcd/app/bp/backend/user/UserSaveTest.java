package cn.bcd.app.bp.backend.user;

import cn.bcd.app.bp.backend.permission.PermissionService;
import cn.bcd.app.bp.backend.role.RoleService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserSaveTest {
    private final UserService service = spy(new UserService(mock(PermissionService.class), mock(RoleService.class)));

    @Test
    void creationKeepsServerPasswordAndStatusDefaults() {
        doNothing().when(service).insert(any(UserBean.class));
        UserSaveRequest request = new UserSaveRequest();
        request.username = "alice";
        request.status = 0;
        service.saveUser(request);
        verify(service).insert(argThat((UserBean bean) -> "alice".equals(bean.username)
                && bean.password != null && bean.status == 1 && bean.id == null));
    }

    @Test
    void updateOnlyWritesEditableFieldsAndPreservesExplicitNull() {
        UserBean stored = new UserBean();
        stored.id = 7L;
        stored.password = "stored hash";
        doReturn(stored).when(service).get(7L);
        doNothing().when(service).update(eq(7L), anyMap());
        UserSaveRequest request = new UserSaveRequest();
        request.id = 7L;
        request.username = "alice";
        request.status = 1;
        service.saveUser(request);
        verify(service).update(eq(7L), argThat((Map<String, Object> changes) ->
                changes.containsKey("email") && changes.get("email") == null
                        && !changes.containsKey("password") && !changes.containsKey("id")
                        && !changes.containsKey("createTime") && !changes.containsKey("updateTime")));
        assertEquals("stored hash", stored.password);
    }

    @Test
    void missingUpdateTargetIsRejected() {
        doReturn(null).when(service).get(7L);
        UserSaveRequest request = new UserSaveRequest();
        request.id = 7L;
        assertThrows(IllegalArgumentException.class, () -> service.saveUser(request));
        verify(service, never()).update(anyLong(), anyMap());
    }
}
