package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ExceptionUtilTest {

    @Test
    void unwrapsMixedWrappersAndPreservesBusinessResult() {
        BaseException business = BaseException.get("failed").code(10001);
        Throwable wrapped = new CompletionException(new ExecutionException(
                new UndeclaredThrowableException(new InvocationTargetException(BaseException.get(business)))));

        assertSame(business, ExceptionUtil.getRealException(wrapped));
        Result<?> result = Result.from(wrapped);
        assertEquals(10001, result.code);
        assertEquals("failed", result.message);
    }

    @Test
    void preservesNullAndOrdinaryExceptions() {
        assertNull(ExceptionUtil.getRealException(null));
        RuntimeException ordinary = new RuntimeException(new IllegalArgumentException());
        assertSame(ordinary, ExceptionUtil.getRealException(ordinary));
    }

    @Test
    void preservesWrappersWithoutTargets() {
        Throwable[] wrappers = {
                BaseException.get("failed"),
                new InvocationTargetException(null),
                new UndeclaredThrowableException(null),
                new CompletionException(null),
                new ExecutionException(null)
        };
        for (Throwable wrapper : wrappers) {
            assertSame(wrapper, ExceptionUtil.getRealException(wrapper));
        }
    }

    @Test
    void terminatesOnSelfCycle() {
        MutableInvocationException wrapper = new MutableInvocationException();
        wrapper.target = wrapper;
        assertSame(wrapper, ExceptionUtil.getRealException(wrapper));
    }

    @Test
    void terminatesOnCycleAcrossWrappers() {
        MutableInvocationException wrapper = new MutableInvocationException();
        CompletionException completion = new CompletionException(wrapper);
        wrapper.target = completion;
        assertSame(wrapper, ExceptionUtil.getRealException(BaseException.get(wrapper)));
    }

    @Test
    void detectsCyclesByIdentityInsteadOfEquality() {
        MutableInvocationException first = new MutableInvocationException();
        MutableInvocationException second = new MutableInvocationException();
        Throwable target = new IllegalArgumentException("target");
        first.target = second;
        second.target = target;
        assertSame(target, ExceptionUtil.getRealException(first));
    }

    private static class MutableInvocationException extends InvocationTargetException {
        private Throwable target;

        @Override
        public Throwable getTargetException() {
            return target;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableInvocationException;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
