package cn.bcd.server.business.process.backend.asm;

import cn.bcd.lib.base.util.ClassLoaderUtil;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class TestAsm {

    public final int testFor(int var1) {
        int var2 = 0;

        for (int var3 = 0; var3 < var1; ++var3) {
            if (var3 % 2 == 0) {
                var2 += var3;
            }
        }

        return var2;
    }

    @Test
    public void test1() throws Exception {
        String className = "Test1";
        String classPath = "D:\\work\\bcd\\MyFramework\\Server-Business-Process-Backend\\src\\test\\java\\cn\\bcd\\server\\business\\process\\backend\\asm\\" + className + ".class";
        ClassFile.of().buildTo(Paths.get(classPath),
                ClassDesc.of("cn.bcd", className), b -> {
                    b
                            .withVersion(ClassFile.JAVA_24_VERSION, 0)
                            .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, m -> {
                                m.withCode(c -> {
                                    c.aload(c.receiverSlot())
                                            .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                            .return_();
                                });
                            })
                            .withMethod("add",
                                    MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int, ConstantDescs.CD_int, ConstantDescs.CD_int), ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
                                    m -> {
                                        m.withCode(c -> {
                                            c.iconst_1()
                                                    .istore(4)
                                                    .iload(1)
                                                    .iload(2)
                                                    .isub()
                                                    .iload(3)
                                                    .iadd()
                                                    .iload(4)
                                                    .iadd()
                                                    .ireturn();
                                        });
                                    })
                    ;
                });
        Class<?> clazz = ClassLoaderUtil.put("cn.bcd." + className, classPath);
        Object o = clazz.getConstructor().newInstance();
        Object res = clazz.getMethod("add", int.class, int.class, int.class).invoke(o, 2, 3, 4);
        System.out.println(res);

    }

    @Test
    public void test2() throws Exception {
        String className = "Test2";
        String classPath = "D:\\work\\bcd\\MyFramework\\Server-Business-Process-Backend\\src\\test\\java\\cn\\bcd\\server\\business\\process\\backend\\asm\\" + className + ".class";
        ClassFile.of().buildTo(Paths.get(classPath),
                ClassDesc.of("cn.bcd", className), b -> {
                    b
                            .withVersion(ClassFile.JAVA_24_VERSION, 0)
                            .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, m -> {
                                m.withCode(c -> {
                                    c.aload(c.receiverSlot())
                                            .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                            .return_();
                                });
                            })
                            .withMethod("testFor",
                                    MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int), ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL,
                                    m -> {
                                        m.withCode(c -> {
                                            Label label1 = c.newLabel();
                                            Label label2 = c.newLabel();
                                            Label label3 = c.newLabel();
                                            c.iconst_0()
                                                    .istore(2)
                                                    .iconst_0()
                                                    .istore(3)
                                                    .labelBinding(label1)
                                                    .iload(3)
                                                    .iload(1)
                                                    .if_icmpge(label2)
                                                    .iload(3)
                                                    .iconst_2()
                                                    .irem()
                                                    .ifne(label3)
                                                    .iload(2)
                                                    .iload(3)
                                                    .iadd()
                                                    .istore(2)
                                                    .labelBinding(label3)
                                                    .iinc(3, 1)
                                                    .goto_(label1)
                                                    .labelBinding(label2)
                                                    .iload(2)
                                                    .ireturn()
                                            ;

                                        });
                                    });
                });
        Class<?> clazz = ClassLoaderUtil.put("cn.bcd." + className, classPath);
        Object o = clazz.getConstructor().newInstance();
        Object res = clazz.getMethod("testFor",int.class).invoke(o, 10);
        System.out.println(res);
    }

    public static void main(String[] args) {

    }
}
