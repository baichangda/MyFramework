package cn.bcd.lib.base.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOUtil {
    private static final int BUFFER_SIZE = 1024 * 4;

    public static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while ((n = in.read(buf)) > -1) {
            out.write(buf, 0, n);
            count += n;
        }
        return count;
    }
}
