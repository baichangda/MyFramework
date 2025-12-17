package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;
import com.google.common.primitives.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressUtil {

    static Logger logger = LoggerFactory.getLogger(CompressUtil.class);

    final static int unGzipBufferSize = 10 * 1024;

    /**
     * gzip压缩
     *
     * @param data
     * @return
     */
    public static byte[] gzip(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(os)) {
            gos.write(data);
            gos.finish();
            return os.toByteArray();
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * gzip压缩
     *
     * @param data
     * @return
     */
    public static byte[] gzip(byte[] data, int off, int len) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(os)) {
            gos.write(data, off, len);
            gos.finish();
            return os.toByteArray();
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * gzip压缩分割器
     * 根据{@link #maxGzipSize}限制、在压缩的过程中、压缩数据大小一旦大于{@link #maxGzipSize}时，生成一个压缩数据块，并返回
     * 确保每个压缩后的文件接近{@link #maxGzipSize}
     */
    public static class GzipSplitter implements AutoCloseable {
        public final int maxGzipSize;

        public ByteArrayOutputStream baos;
        public GZIPOutputStream gos;

        public GzipSplitter(int maxGzipSize) {
            this.maxGzipSize = maxGzipSize;
        }

        private void checkAndNewOs() throws IOException {
            if (baos == null) {
                baos = new ByteArrayOutputStream();
                gos = new GZIPOutputStream(baos);
            }
        }

        private void checkAndCloseOs() {
            if (gos != null) {
                try {
                    gos.finish();
                    gos.close();
                    gos = null;
                } catch (IOException e) {
                    logger.error("close error", e);
                }
            }
            if (baos != null) {
                try {
                    baos.close();
                    baos = null;
                } catch (IOException e) {
                    logger.error("close error", e);
                }
            }
        }

        private byte[] checkRes() throws IOException {
            if (baos.size() >= maxGzipSize) {
                gos.finish();
                byte[] res = baos.toByteArray();
                checkAndCloseOs();
                return res;
            } else {
                return null;
            }
        }

        /**
         * 写入数据
         *
         * @param bytes
         * @return 压缩数据块、可能为null、null时候代表仅仅写入数据、非null时候表示压缩数据块达到阈值、返回压缩数据块
         */
        public byte[] write(byte[] bytes) {
            try {
                checkAndNewOs();
                gos.write(bytes);
                return checkRes();
            } catch (IOException e) {
                checkAndCloseOs();
                throw BaseException.get(e);
            }
        }

        /**
         * 写入数据
         *
         * @param bytes
         * @param off
         * @param len
         * @return 压缩数据块、可能为null、null时候代表仅仅写入数据、非null时候表示压缩数据块达到阈值、返回压缩数据块
         */
        public byte[] write(byte[] bytes, int off, int len) {
            try {
                checkAndNewOs();
                gos.write(bytes, off, len);
                return checkRes();
            } catch (IOException e) {
                checkAndCloseOs();
                throw BaseException.get(e);
            }
        }

        /**
         * 在结束时候调用此方法、获取最后的压缩数据块
         *
         * @return 最后的压缩数据块、可能为null(当上一次write返回非null)
         */
        public byte[] finish() {
            if (baos == null) {
                return null;
            } else {
                try {
                    gos.finish();
                    return baos.toByteArray();
                } catch (IOException e) {
                    throw BaseException.get(e);
                } finally {
                    checkAndCloseOs();
                }
            }
        }

        @Override
        public void close() throws Exception {
            checkAndCloseOs();
        }
    }

    /**
     * gzip压缩
     *
     * @param data
     * @return
     */
    public static byte[] gzip(byte[] data, int off, int len, int maxSize) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(os)) {
            gos.write(data, off, len);
            gos.finish();
            if (os.size() >= maxSize) {

            }
            return os.toByteArray();
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 解压zip格式数据
     *
     * @param data
     * @return
     */
    public static byte[] unGzip(byte[] data) {
        byte[] res;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            unGzip(bis, os);
            res = os.toByteArray();
        } catch (IOException e) {
            throw BaseException.get(e);
        }
        return res;
    }

    /**
     * 解压指定输入流到输出流中
     *
     * @param is
     */
    public static void unGzip(InputStream is, OutputStream os) {
        try (GZIPInputStream gis = new GZIPInputStream(is)) {
            int count;
            byte[] bytes = new byte[unGzipBufferSize];
            while ((count = gis.read(bytes, 0, bytes.length)) != -1) {
                os.write(bytes, 0, count);
            }
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 解压输入流
     * 根据指定的分隔符、分割为byte[]供消费
     *
     * @param is
     * @param splitChar
     * @param function
     */
    public static void unGzip(InputStream is, char splitChar, Function<byte[], Boolean> function) {
        try (GZIPInputStream gis = new GZIPInputStream(is)) {
            int count;
            byte[] buffer = null;
            Bytes.concat();
            byte[] bytes = new byte[unGzipBufferSize];
            while ((count = gis.read(bytes, 0, bytes.length)) != -1) {
                //根据分割符分割数据块
                List<byte[]> splitDatas = new ArrayList<>();
                int prevSplitIndex = -1;
                for (int i = 0; i < count; i++) {
                    if (splitChar == bytes[i]) {
                        byte[] data;
                        if (prevSplitIndex == -1) {
                            int dataLen = i;
                            if (dataLen == 0) {
                                data = new byte[0];
                            } else {
                                data = new byte[dataLen];
                                System.arraycopy(bytes, 0, data, 0, data.length);
                            }
                        } else {
                            int dataLen = i - prevSplitIndex - 1;
                            if (dataLen == 0) {
                                data = new byte[0];
                            } else {
                                data = new byte[dataLen];
                                System.arraycopy(bytes, prevSplitIndex + 1, data, 0, data.length);
                            }
                        }
                        splitDatas.add(data);
                        prevSplitIndex = i;
                    }
                }
                //添加最后一个数据块
                if (prevSplitIndex != -1) {
                    int dataLen = count - prevSplitIndex - 1;
                    if (dataLen == 0) {
                        splitDatas.add(new byte[0]);
                    } else {
                        byte[] data = new byte[dataLen];
                        System.arraycopy(bytes, prevSplitIndex + 1, data, 0, data.length);
                        splitDatas.add(data);
                    }
                }


                if (splitDatas.isEmpty()) {
                    if (buffer == null) {
                        buffer = new byte[count];
                        System.arraycopy(bytes, 0, buffer, 0, count);
                    } else {
                        byte[] newBuffer = new byte[buffer.length + count];
                        System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                        System.arraycopy(bytes, 0, newBuffer, buffer.length, count);
                        buffer = newBuffer;
                    }
                } else {
                    //处理头
                    byte[] applyBytes;
                    byte[] first = splitDatas.getFirst();
                    if (buffer == null) {
                        applyBytes = first;
                    } else {
                        if (first.length == 0) {
                            applyBytes = buffer;
                        } else {
                            applyBytes = new byte[buffer.length + first.length];
                            System.arraycopy(buffer, 0, applyBytes, 0, buffer.length);
                            System.arraycopy(first, 0, applyBytes, buffer.length, first.length);
                        }
                    }
                    Boolean apply = function.apply(applyBytes);
                    if (!apply) {
                        return;
                    }

                    //处理中间
                    for (int i = 1; i < splitDatas.size() - 1; i++) {
                        apply = function.apply(splitDatas.get(i));
                        if (!apply) {
                            return;
                        }
                    }

                    //处理尾
                    buffer = splitDatas.getLast();
                }

            }
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 解压文件到指定文件
     *
     * @param source 压缩文件
     * @param target 解压后的文件
     */
    public static void unGzip(String source, String target) {
        try (InputStream is = Files.newInputStream(Paths.get(source));
             OutputStream os = Files.newOutputStream(Paths.get(target))) {
            unGzip(is, os);
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    public static void main(String[] args) throws IOException {
//        GzipSplitter gzipSplitter = new GzipSplitter(300 * 1024);
//        byte[] bytes = Files.readAllBytes(Paths.get("D:\\testBackup.txt"));
//        String s = new String(bytes);
//        String[] split = s.split("\n");
//        int index = 0;
//        for (String string : split) {
//            byte[] arr = new byte[string.length() + 1];
//            arr[arr.length - 1] = '\n';
//            System.arraycopy(string.getBytes(), 0, arr, 0, string.length());
//            byte[] bs = gzipSplitter.write(arr);
//            if (bs != null) {
//                Files.write(Paths.get("test-" + index + ".txt"), bs);
//                index++;
//            }
//        }
//        byte[] finish = gzipSplitter.finish();
//        if (finish != null) {
//            Files.write(Paths.get("test-" + index + ".txt"), finish);
//        }
//
//        byte[] bs = Files.readAllBytes(Paths.get("test-3.txt"));
//        byte[] unGzip = CompressUtil.unGzip(bs);
//        Files.write(Paths.get("test-3-json.txt"), unGzip);

        InputStream is = Files.newInputStream(Paths.get("d:/testBackup.txt.gz"));
        unGzip(is,'\n',e->{
            System.out.println(new String(e));
            return true;
        });
    }
}
