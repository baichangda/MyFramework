package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by Administrator on 2017/7/27.
 */
public class ExceptionUtil {

    private final static Logger logger = LoggerFactory.getLogger(ExceptionUtil.class);

    /**
     * 获取堆栈信息
     *
     * @param throwable
     * @return
     */
    public static String getStackTraceMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            return sw.toString();
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }


    /**
     * 获取真实的异常
     *
     * @param throwable
     * @return
     */
    public static Throwable getRealException(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable temp = throwable;
        while (true) {
            if (temp instanceof BaseException ex) {
                Throwable t = ex.getTargetException();
                if (t == null) {
                    return temp;
                } else {
                    temp = t;
                }
            } else if (temp instanceof InvocationTargetException ex) {
                Throwable t = ex.getTargetException();
                if (t == null) {
                    return temp;
                } else {
                    temp = t;
                }
            } else {
                return temp;
            }
        }
    }
}
