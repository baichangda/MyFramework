import com.alibaba.excel.EasyExcel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JVTest {
    @Test
    public void cprofileOutputToXlsx() throws IOException {
        List<ArrayList<Object>> res = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get("d:/a.txt"));
        for (int x = 0; x < lines.size(); x++) {
            String line = lines.get(x);
            ArrayList<Object> list = new ArrayList<>();
            char[] charArray = line.toCharArray();
            int index = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < charArray.length; i++) {
                char c = charArray[i];
                if (c == ' ') {
                    if (!sb.isEmpty()) {
                        if (x == 0 || list.isEmpty()) {
                            list.add(sb.toString());
                        } else {
                            list.add(Double.parseDouble(sb.toString()));
                        }
                        sb.delete(0, sb.length());
                        if (list.size() == 5) {
                            index = i;
                            break;
                        }
                    }
                } else {
                    sb.append(c);
                }
            }
            list.add(line.substring(index).trim());
            if (list.size() != 6) {
                System.out.println(Arrays.toString(list.toArray()));
            }
            res.add(list);
        }
        EasyExcel.write(Paths.get("d:/b.xlsx").toFile()).sheet(0).doWrite(res);
    }
}
