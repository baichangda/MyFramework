package cn.bcd.app.bp.backend.user;

import cn.bcd.app.bp.backend.base.support_spring_paramconverter.StringToDateConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserHttpModelTest {
    private final UserService service = mock(UserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var conversion = new DefaultFormattingConversionService();
        conversion.addConverter(new StringToDateConverter());
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service)).setConversionService(conversion).build();
    }

    @Test
    void pageBindsQuerySeparatelyFromPaginationIncludingDateConverter() throws Exception {
        when(service.pageUser(any(), anyInt(), anyInt())).thenReturn(Page.empty(PageRequest.of(1, 10)));
        mvc.perform(get("/api/user/page").param("username", "alice")
                        .param("birthdayBegin", "1234").param("pageNum", "2").param("pageSize", "10"))
                .andExpect(status().isOk());
        verify(service).pageUser(argThat(query -> "alice".equals(query.username)
                && new Date(1234).equals(query.birthdayBegin)), eq(2), eq(10));
    }

    @Test
    void listUsesSameQueryAndQueryResponseModel() throws Exception {
        UserQueryResponse queryResponse = new UserQueryResponse();
        queryResponse.username = "alice";
        when(service.listUser(any())).thenReturn(List.of(queryResponse));
        mvc.perform(get("/api/user/list").param("username", "alice"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("alice")));
        verify(service).listUser(argThat(query -> "alice".equals(query.username)));
    }

    @Test
    void invalidSaveDoesNotReachService() throws Exception {
        mvc.perform(post("/api/user/save").contentType("application/json")
                .content("{\"username\":\"\",\"status\":1}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void controllerPassesPaginationToServiceWithoutRangeValidation() throws Exception {
        when(service.pageUser(any(), anyInt(), anyInt())).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(get("/api/user/page").param("pageNum", "0").param("pageSize", "-5"))
                .andExpect(status().isOk());
        verify(service).pageUser(any(), eq(0), eq(-5));
    }

    @Test
    void pageSizeHasNoGeneratedUpperLimit() throws Exception {
        when(service.pageUser(any(), anyInt(), anyInt())).thenReturn(Page.empty(PageRequest.of(0, 201)));
        mvc.perform(get("/api/user/page").param("pageSize", "201")).andExpect(status().isOk());
        verify(service).pageUser(any(), eq(1), eq(201));
    }

    @Test
    void saveBindsIndependentRequestAndRejectsInvalidStatus() throws Exception {
        mvc.perform(post("/api/user/save").contentType("application/json")
                        .content("{\"id\":7,\"username\":\"alice\",\"status\":1,\"email\":null}"))
                .andExpect(status().isOk());
        verify(service).saveUser(argThat(request -> request.id == 7L && request.email == null && request.status == 1));
        mvc.perform(post("/api/user/save").contentType("application/json")
                .content("{\"username\":\"alice\",\"status\":2}")).andExpect(status().isBadRequest());
    }
}
