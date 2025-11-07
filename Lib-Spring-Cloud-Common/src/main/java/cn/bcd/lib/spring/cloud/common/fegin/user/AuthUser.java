package cn.bcd.lib.spring.cloud.common.fegin.user;

import lombok.Data;

@Data
public class AuthUser {
    public long id;
    public String username;
    public int status;

    public AuthUser() {
    }

    public AuthUser(long id, String username, int status) {
        this.id = id;
        this.username = username;
        this.status = status;
    }
}
