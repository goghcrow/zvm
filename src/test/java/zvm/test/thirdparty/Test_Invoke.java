package zvm.test.thirdparty;

import java.util.ArrayList;
import java.util.List;

// 测试用例修改自 https://github.com/zxh0/jvm.go/blob/master/test/testclasses/src/main/java/jvm/MethodCall.java
// 测试用例修改自 https://github.com/zxh0/jvm.go/blob/master/test/testclasses/src/main/java/jvm/lambda/InterfaceDefaultMethodTest.java
// 测试用例修改自 github.com/lihaoyi/Metascala
public class Test_Invoke {

    public static Integer[] test_invoke_static() {
        List<Integer> lst = new ArrayList<>();
        int a = 4;
        int b = 1;
        lst.add(helloWorld(a));
        lst.add(timesTwo(a));
        lst.add(helloWorld2(a, b));
        lst.add(timesTwo2(a, b));
        lst.add(tailFactorial(a));
        lst.add(fibonacci(a));
        lst.add(call(a));
        lst.add(callAtPhiBoundary(a));
        return lst.toArray(new Integer[0]);
    }

    static int helloWorld(int n){
        return timesTwo(n);
    }

    static int timesTwo(int n){
        return n * 2;
    }

    static int helloWorld2(int a, int b){
        return timesTwo2(a, b);
    }

    static int timesTwo2(int a, int b){
        return (a - b) * 2;
    }

    static int tailFactorial(int n){
        if (n == 1){
            return 1;
        }else{
            return n * tailFactorial(n-1);
        }
    }

    static int fibonacci(int n){
        if (n == 1 || n == 0){
            return 1;
        }else{
            return fibonacci(n-1) + fibonacci(n-2);
        }
    }

    static int call(int x) {
        return x+1;
    }

    static int callAtPhiBoundary(int i){
        int size = (i < 0) ? 1  : call(i);
        return size;
    }


    // ------------------------------------------------------------------

    interface If1 {
        static int x() {
            return 1;
        }
        default int y() {
            return 2;
        }
    }
    static class Impl1 implements If1 { }
    static class Impl2 implements If1 {
        @Override public int y() { return 12; }
    }
    static class Impl3 implements If1 {
        @Override public int y() { return 100 + If1.super.y(); }
    }

    public static Object InterfaceMethodTest() {
        return new Object[] {
                If1.x(),
                new Impl1().y(),
                new Impl2().y(),
                new Impl3().y()
        };
    }

    // ------------------------------------------------------------------

    static class TestInterfaceDefaultTest extends FirstTestClass implements FirstTest, SecondTest { }
    static class TestInterfaceFirstTestClass implements FirstTest, SecondTest, ThirdTest, DefaultTest { }
    static class FirstTestClass implements ThirdTest, FirstTest { }
    interface FirstTest extends DefaultTest { }
    interface DefaultTest {
        default String test() { return "DefaultTest"; }
        static String static_test() { return "DefaultTest"; }
    }
    interface SecondTest extends FirstTest {
        default String test() { return "SecondTest"; }
    }
    interface ThirdTest extends DefaultTest, SecondTest {
        default String test() { return "ThirdTest"; }
    }

    public static Object InterfaceDefaultMethodTest1() {
        TestInterfaceDefaultTest testInterfaceDefaultTest = new TestInterfaceDefaultTest();
        DefaultTest defaultTest = testInterfaceDefaultTest;
        FirstTestClass firstTestClass = testInterfaceDefaultTest;
        SecondTest secondTest = testInterfaceDefaultTest;
        ThirdTest thirdTest = testInterfaceDefaultTest;

        return new Object[] {
                defaultTest.test(), // "ThirdTest"
                firstTestClass.test(),// "ThirdTest"
                secondTest.test(), // "ThirdTest"
                thirdTest.test() // "ThirdTest"
        };
    }

    // 5.4.3 方法 字段解析等重新读一遍..
    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.4.3.3
    public static Object InterfaceDefaultMethodTest2() {
        TestInterfaceFirstTestClass testInterfaceFirstTestClass = new TestInterfaceFirstTestClass();
        DefaultTest defaultTest = testInterfaceFirstTestClass;
        FirstTest firstTest = testInterfaceFirstTestClass;
        SecondTest secondTest = testInterfaceFirstTestClass;
        ThirdTest thirdTest = testInterfaceFirstTestClass;

        return new Object[] {
                defaultTest.test(), // "ThirdTest"
                firstTest.test(),// "ThirdTest"
                secondTest.test(), // "ThirdTest"
                thirdTest.test() // "ThirdTest"
        };
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static Object InvokeTest() {
        InvokeTest test = new InvokeTest();
        test.add("ArrayList");

        ArrayList<String> arrayList = test;
        List<String> list = test;

        return new Object[] {
                // invokevirtual
                arrayList.get(0),
                // invokespecial
                test.getFromSuper(0),
                // invokeinterface
                list.get(0)
        };
    }


    static class InvokeTest extends ArrayList<String> {
        public int x;
        @Override
        public String get(int index) {
            return "InvokeTest";
        }
        public String getFromSuper(int index) {
            return super.get(index);
        }
    }


    public static Object test() {
        String[] args = new String[0];
        return new Object[] {
                f(1, 2L, 3.14f, 2.71828, args),
                new Test_Invoke().g(1, 2L, 3.14f, 2.71828, args)
        };
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    static Object f(int a, long b, float c, double d, Object e) {
        int x = a;
        long y = b;
        float z = c;
        double u = d;
        Object v = e;
        return new Object[] { x, y, z, u, v};
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    Object g(int a, long b, float c, double d, Object e) {
        int x = a;
        long y = b;
        float z = c;
        double u = d;
        Object v = e;
        return new Object[] { x, y, z, u, v};
    }
}
