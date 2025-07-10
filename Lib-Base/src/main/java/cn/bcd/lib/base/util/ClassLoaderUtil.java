package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

public class ClassLoaderUtil extends ClassLoader {

    final static ClassLoaderUtil instance = new ClassLoaderUtil();

    static ConcurrentHashMap<String, Class<?>> cache = new ConcurrentHashMap<>();

    Class<?> defineClass(String name, String classPath) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(classPath));
            Class<?> aClass = defineClass(null, bytes, 0, bytes.length);
            cache.put(name, aClass);
            return aClass;
        } catch (IOException ex) {
            throw BaseException.get(ex);
        }
    }

    @Override
    protected Class<?> findClass(String name) {
        return cache.get(name);
    }


    public static Class<?> put(String name, String classPath) {
        return instance.defineClass(name, classPath);
    }

    public static Class<?> get(String name) {
        return instance.findClass(name);
    }
}
