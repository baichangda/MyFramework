package cn.bcd.lib.data.notify.onlyNotify.transferAccess;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class TransferAccessData {
    public String vin;
    public List<String> platformCode;
}