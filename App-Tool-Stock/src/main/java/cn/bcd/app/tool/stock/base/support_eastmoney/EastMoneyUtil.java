package cn.bcd.app.tool.stock.base.support_eastmoney;

import cn.bcd.app.tool.stock.base.support_vertx.Const;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;

import java.util.ArrayList;
import java.util.List;

public class EastMoneyUtil {

    public static List<CashFlowData> fetchCashFlowToday(String code) {
        HttpRequest<Buffer> httpRequest = Const.webClient.get("https://push2.eastmoney.com/api/qt/stock/fflow/kline/get?cb=jQuery112303828149367430771_1763011549967&lmt=0&klt=1&fields1=f1%2Cf2%2Cf3%2Cf7&fields2=f51%2Cf52%2Cf53%2Cf54%2Cf55%2Cf56%2Cf57%2Cf58%2Cf59%2Cf60%2Cf61%2Cf62%2Cf63%2Cf64%2Cf65&ut=b2884a393a59ad64002292a3e90d46a5&secid=0." + code + "&_=1763011550018");
        String str = httpRequest.send().result().bodyAsString();
        String json = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
        JsonObject jsonObject = new JsonObject(json);
        JsonArray jsonArray = jsonObject.getJsonObject("data").getJsonArray("klines");
        List<CashFlowData> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            //天,开盘价、收盘价、最高价、最低价、成交量、成交额、振幅、涨跌幅、涨跌额、换手率
            String text = jsonArray.getString(i);
            String[] split = text.split(",");
            CashFlowData data = new CashFlowData();
            data.minute = split[0];
            data.d1 = Double.parseDouble(split[1]);
            data.d2 = Double.parseDouble(split[2]);
            data.d3 = Double.parseDouble(split[3]);
            data.d4 = Double.parseDouble(split[4]);
            data.d5 = Double.parseDouble(split[5]);
            list.add(data);
        }
        return list;
    }

    public static List<DailyData> fetchDaily(String code) {
        HttpRequest<Buffer> httpRequest = Const.webClient.get("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=0." + code + "&ut=fa5fd1943c7b386f172d6893dbfba10b&fields1=f1%2Cf2%2Cf3%2Cf4%2Cf5%2Cf6&fields2=f51%2Cf52%2Cf53%2Cf54%2Cf55%2Cf56%2Cf57%2Cf58%2Cf59%2Cf60%2Cf61&klt=101&fqt=1&beg=0&end=20500101&smplmt=100000&lmt=1000000&_=1752720275749");
        JsonObject jsonObject = httpRequest.send().result().bodyAsJsonObject();
        JsonArray jsonArray = jsonObject.getJsonObject("data").getJsonArray("klines");
        List<DailyData> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            //天,开盘价、收盘价、最高价、最低价、成交量、成交额、振幅、涨跌幅、涨跌额、换手率
            String text = jsonArray.getString(i);
            String[] split = text.split(",");
            DailyData data = new DailyData();
            data.day = split[0];
            data.open = Float.parseFloat(split[1]);
            data.close = Float.parseFloat(split[2]);
            data.highest = Float.parseFloat(split[3]);
            data.lowest = Float.parseFloat(split[4]);
            data.volume = Long.parseLong(split[5]);
            data.amount = Double.parseDouble(split[6]);
            data.amplitude = Float.parseFloat(split[7]);
            data.raiseRate = Float.parseFloat(split[8]);
            data.raise = Float.parseFloat(split[9]);
            data.turnover = Float.parseFloat(split[10]);
            list.add(data);
        }
        return list;
    }

    public static void main(String[] args) {
        List<DailyData> dailyDataList = fetchDaily("002050");
        System.out.println(dailyDataList.size());
        List<CashFlowData> cashFlowDataList = fetchCashFlowToday("002050");
        System.out.println(cashFlowDataList.size());
    }
}
