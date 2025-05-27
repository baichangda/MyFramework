package cn.bcd.lib.storage.cassandra;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "lib.storage.cassandra")
public class CassandraProp {
    public String[] dbs;
    public String username;
    public String password;
}
