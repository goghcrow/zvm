package zvm.test.thirdparty;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import sun.reflect.Reflection;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

// https://github.com/zxh0/jvm.go/tree/master/test/testclasses/src/main/java/stdlib/basic/reflection
public class Test_stdlib {

    @SuppressWarnings("StringEquality")
    public static Object test_str() {
        Object[] r = new Object[5];
        String s1 = "abc1";
        String s2 = "abc1";
        r[0] = s1 == s2;

        int x = 1;
        String s3 = "abc" + x;
        r[1] = s1 == s3;

        s3 = s3.intern();
        r[2] = s1 == s3;

        String s = "\uD800";
        r[3] = s.length();
        r[4] = (int)s.charAt(0);

        return r;
    }

    public static Object test_reflect() {
        testArrayClass(boolean[].class, "[Z");
        testArrayClass(byte[].class,    "[B");
        testArrayClass(char[].class,    "[C");
        testArrayClass(short[].class,   "[S");
        testArrayClass(int[].class,     "[I");
        testArrayClass(long[].class,    "[J");
        testArrayClass(float[].class,   "[F");
        testArrayClass(double[].class,  "[D");
        testArrayClass(int[][].class,   "[[I");
        testArrayClass(Object[].class,  "[Ljava.lang.Object;");
        testArrayClass(Object[][].class,"[[Ljava.lang.Object;");
        return null;
    }

    private static void testArrayClass(Class<?> c, String name) {
        assertEquals(name, c.getName());
        assertEquals(Object.class, c.getSuperclass());
        assertArrayEquals(new Class<?>[]{Cloneable.class, Serializable.class}, c.getInterfaces());
        assertEquals(0, c.getFields().length);
        assertEquals(0, c.getDeclaredFields().length);
        assertEquals(9, c.getMethods().length);
        assertEquals(0, c.getDeclaredMethods().length);
    }

    // === === === === === === === === === === === === === === === === === === === === ===

    public static void getNullArray() {
        try {
            Object x = null;
            Array.get(x, 3);
            fail();
        } catch (NullPointerException e) {
            assertNull(e.getMessage());
        }
    }

    public static void getNonArray() {
        try {
            String str = "abc";
            Array.get(str, 1);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Argument is not an array", e.getMessage());
        }
    }

    public static void getArrayBadIndex() {
        try {
            int[] arr = {1, 2, 3};
            Array.get(arr, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertNull(e.getMessage());
        }
    }

    public static void getObjectArray() {
        String[] arr = {"a", "b", "c"};
        assertEquals("c", Array.get(arr, 2));
    }

    public static void getPrimitiveArray() {
        assertEquals(true,     Array.get(new boolean[]{true}, 0));
        assertEquals((byte)2,  Array.get(new byte[]{2},       0));
        assertEquals('a',      Array.get(new char[]{'a'},     0));
        assertEquals((short)2, Array.get(new short[]{2},      0));
        assertEquals(2,        Array.get(new int[]{2},        0));
        assertEquals(2L,       Array.get(new long[]{2},       0));
        assertEquals(3.14f,    Array.get(new float[]{3.14f},  0));
        assertEquals(2.71,     Array.get(new double[]{2.71},  0));
    }

    // === === === === === === === === === === === === === === === === === === === === ===

    public static void setNullArray() {
        try {
            Object x = null;
            Array.set(x, 3, "a");
            fail();
        } catch (NullPointerException e) {
            assertNull(e.getMessage());
        }
    }

    public static void setNonArray() {
        try {
            String str = "abc";
            Array.set(str, 1, "a");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Argument is not an array", e.getMessage());
        }
    }

    public static void setArrayTypeMismatch() {
        try {
            int[] arr = {1, 2, 3};
            Array.set(arr, 0, "beyond");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("argument type mismatch", e.getMessage());
        }
    }

    public static void setArrayBadIndex() {
        try {
            int[] arr = {1, 2, 3};
            Array.set(arr, -1, 4);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertNull(e.getMessage());
        }
    }

    public static void setObjectArray() {
        String[] arr = {"beyond"};
        Array.set(arr, 0, "5457");
        assertEquals("5457", Array.get(arr, 0));
    }

    public static void setPrimitiveArray() {
        Array.set(new boolean[]{true}, 0, false);
        Array.set(new byte[]{2}, 0, (byte) 3);
        Array.set(new char[]{'a'}, 0, 'b');
        Array.set(new short[]{2}, 0, (short) 3);
        Array.set(new int[]{2}, 0, 3);
        Array.set(new long[]{2}, 0, 3L);
        Array.set(new float[]{3.14f}, 0, 2.71f);
        Array.set(new double[]{2.71}, 0, 3.14);

        {
            boolean[] arr = {true, true, true, true};
            Array.set(arr, 0, false);
            assertEquals(false, Array.get(arr, 0));
        }
        {
            byte[] arr = {5, 4, 5, 7};
            Array.set(arr, 0, ((byte) 0));
            assertEquals(((byte) 0), Array.get(arr, 0));
        }
        {
            char[] arr = {5, 4, 5, 7};
            Array.set(arr, 0, ((char) 0));
            assertEquals(((char) 0), Array.get(arr, 0));
        }
        {
            short[] arr = {5, 4, 5, 7};
            Array.set(arr, 0, ((short) 0));
            assertEquals(((short) 0), Array.get(arr, 0));
        }
        {
            int[] arr = {5, 4, 5, 7};
            Array.set(arr, 0, 0);
            assertEquals(0, Array.get(arr, 0));
        }
        {
            long[] arr = {5, 4, 5, 7};
            Array.set(arr, 0, 0L);
            assertEquals(0L, Array.get(arr, 0));
        }
        {
            float[] arr = {5, 4, 5, 7};
            Array.set(arr, 0, 0.0f);
            assertEquals(0.0f, Array.get(arr, 0));
        }
        {
            double[] arr = {5, 4, 5, 7};
            Array.set(arr, 0, 0.0d);
            assertEquals(0.0d, Array.get(arr, 0));
        }
    }

    // === === === === === === === === === === === === === === === === === === === === ===

//    static class Foo {
//        static void test() {
//            Bar.test();
//        }
//    }

    // todo 实现不对❌❌❌❌
//    static class Bar {
//        static void test() {
//            System.out.println(Reflection.getCallerClass(0).getName());
//            System.out.println(Reflection.getCallerClass(1).getName());
//            System.out.println(Reflection.getCallerClass(2).getName());
//            System.out.println(Reflection.getCallerClass(3).getName());
//        }
//    }
//
//    public static void test_caller_class() {
//        Bar.test();
//    }

//    public static void main(String[] args) {
//        Bar.test();
//    }

    // === === === === === === === === === === === === === === === === === === === === ===

    static class A {
        public static int a = 100;
    }

    public static void ClassInitTest_getStatic() throws Exception {
        Field field_a = A.class.getField("a");
//        System.out.println(field_a);
        assertEquals(100, field_a.get(null));
    }

    // === === === === === === === === === === === === === === === === === === === === ===

    public static class ClassTest implements Runnable {
        private int a;
        public double b;
        static boolean z;

        public static void _main(String[] args) throws Exception{ }

        @Override
        public void run() throws RuntimeException {
//            System.out.println("run!");
        }
    }

//    todo
//    public static void _package() {
//        assertEquals("zvm.test.thirdparty", ClassTest.class.getPackage().getName());
//    }

    public static void _class() {
        Class<?> c = ClassTest.class;
        assertEquals("zvm.test.thirdparty.Test_stdlib$ClassTest", c.getName());
        assertEquals(Object.class, c.getSuperclass());
        assertArrayEquals(new Class<?>[]{Runnable.class}, c.getInterfaces());
        assertEquals(1, c.getFields().length);
        assertEquals(3, c.getDeclaredFields().length);
        assertEquals(11, c.getMethods().length);
        assertEquals(2, c.getDeclaredMethods().length);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Anno { }

    public int a() { return 42; }
    public static int b() { return 42; }

    @Anno
    public static void method() throws Exception {
        Method main = ClassTest.class.getMethod("_main", String[].class);
        assertArrayEquals(new Class<?>[]{Exception.class}, main.getExceptionTypes());
        assertArrayEquals(new Class<?>[]{String[].class}, main.getParameterTypes());

        // todo
        Test_stdlib.class.getDeclaredMethod("a").invoke(new Test_stdlib());
        Test_stdlib.class.getDeclaredMethod("b").invoke(null);
//        System.out.println();
//        System.out.println();

//        Method getAnnotationBytes = Method.class.getDeclaredMethod("getAnnotationBytes");
//        getAnnotationBytes.setAccessible(true);
//        Object invoke = getAnnotationBytes.invoke(main);
//        System.out.println(invoke);
//        assertEquals(0, main.getDeclaredAnnotations().length); // todo

//        Method run = ClassTest.class.getMethod("run");
//        assertEquals(1, run.getDeclaredAnnotations().length);
    }

    // === === === === === === === === === === === === === === === === === === === === ===









    // === === === === === === === === === === === === === === === === === === === === ===

    public static void PrimitiveClassTest() {
        testPrimitiveClass(void.class,      "void");
        testPrimitiveClass(boolean.class,   "boolean");
        testPrimitiveClass(byte.class,      "byte");
        testPrimitiveClass(char.class,      "char");
        testPrimitiveClass(short.class,     "short");
        testPrimitiveClass(int.class,       "int");
        testPrimitiveClass(long.class,      "long");
        testPrimitiveClass(float.class,     "float");
        testPrimitiveClass(double.class,    "double");
    }
    private static void testPrimitiveClass(Class<?> c, String name) {
        assertEquals(name, c.getName());
        assertEquals(null, c.getSuperclass());
        assertEquals(0, c.getFields().length);
        assertEquals(0, c.getDeclaredFields().length);
        assertEquals(0, c.getMethods().length);
        assertEquals(0, c.getDeclaredMethods().length);
    }


    // === === === === === === === === === === === === === === === === === === === === ===

    private static class MethodTest implements Callable<Integer> {
        @Override
        public Integer call() {
            return 7;
        }
        public long returnLong() {
            return 3;
        }
    }
    public static void boxReturn() throws Exception {
        Method m = MethodTest.class.getMethod("returnLong");
        Object x = m.invoke(new MethodTest());
        assertEquals(3L, x);
    }
    public static void invokeInterfaceMethod() throws Exception {
        Method m = Callable.class.getMethod("call");
        Object x = m.invoke(new MethodTest());
        assertEquals(7, x);
    }

    // === === === === === === === === === === === === === === === === === === === === ===

    private static class SuperA {
        static int a = 3;
    }
    private static class SubB extends SuperA implements IB { }
    interface IA {
        // int a = 1;
        int b = 1;
        int c = 1;
    }
    interface IB extends IA {
        int b = 2;
    }

    public static void test_static_field() {
        // 如果 IA IB 有 int a; 则无法 直接通过 SubB.a; 访问, 因为有歧义
        assertEquals(3, SubB.a);
        assertEquals(3, SuperA.a);
        assertEquals(2, IB.b);
    }

    // === === === === === === === === === === === === === === === === === === === === ===

    public static class InnerClassTest {
        private static class Inner {}
    }
    public static void InnerClassTest_test() {
        assertEquals(9, InnerClassTest.class.getModifiers());
        assertEquals(10, InnerClassTest.Inner.class.getModifiers());
    }

    // === === === === === === === === === === === === === === === === === === === === ===


    // === === === === === === === === === === === === === === === === === === === === ===

    // === === === === === === === === === === === === === === === === === === === === ===

    // === === === === === === === === === === === === === === === === === === === === ===

    // === === === === === === === === === === === === === === === === === === === === ===
}
