package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

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
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (visited.add(temp)) {
            Throwable target;
            if (temp instanceof BaseException ex) {
                target = ex.getTargetException();
            } else if (temp instanceof InvocationTargetException ex) {
                target = ex.getTargetException();
            } else if (temp instanceof UndeclaredThrowableException ex) {
                target = ex.getUndeclaredThrowable();
            } else if (temp instanceof CompletionException || temp instanceof ExecutionException) {
                target = temp.getCause();
            } else {
                return temp;
            }
            if (target == null) {
                return temp;
            }
            temp = target;
        }
        // A repeated object marks a cycle; return the first repeated exception.
        return temp;
    }
}
