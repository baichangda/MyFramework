package cn.bcd.lib.data.init.nacos;

import lombok.Data;

/**
 * https://nacos.io/docs/latest/manual/user/open-api/?spm=5238cd80.2ef5001f.0.0.3f613b7cq1VLqP#35-%E6%9F%A5%E8%AF%A2%E6%8C%87%E5%AE%9A%E6%9C%8D%E5%8A%A1%E7%9A%84%E5%AE%9E%E4%BE%8B%E5%88%97%E8%A1%A8
 */
@Data
public class ListInstanceResponse {
    public int code;
    public String message;
    public ListInstanceData data;
}
