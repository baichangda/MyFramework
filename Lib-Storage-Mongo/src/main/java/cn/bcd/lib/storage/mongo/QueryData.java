package cn.bcd.lib.storage.mongo;

import lombok.Data;

@Data
public class QueryData<T> {
    public String id;
    public T value;

    public QueryData(String id, T value) {
        this.id = id;
        this.value = value;
    }

    public QueryData() {
    }
}
