package zvm.test;

import zvm.test.thirdparty.Test_Invoke;

import java.util.Arrays;

public class T {
    static class T1 {

        static class A {
            // 这里必须是 public , 不能是 package protected
            public void test() {
                System.out.println("A");
            }
        }
        static class B extends A implements I {
            // test A
        }
        interface I {
            default void test() {
                System.out.println("interface");
            }
        }

    }
    static class T2 {
        static class A {
            void test() {
                System.out.println("A");
            }
        }
        static class B extends A implements I {
            @Override
            public void test() {
                // 这里为啥是 super ???
                I.super.test(); // interface
//            ((I) this).test(); // stack-over-flow
            }
        }
        interface I {
            default void test() {
                System.out.println("interface");
            }
        }

    }
    public static void main(String[] args) {
        new T1.B().test(); // A
        ((T1.I) new T1.B()).test(); // A
        new T2.B().test(); // interface

        System.out.println(Arrays.toString(((Object[]) T3.InterfaceDefaultMethodTest2())));
    }



    static class T3 {
        static class TestInterfaceDefaultTest extends FirstTestClass implements FirstTest, SecondTest { }
        static class TestInterfaceFirstTestClass implements FirstTest, SecondTest, ThirdTest, DefaultTest { }
        static class FirstTestClass implements ThirdTest, FirstTest { }
        interface FirstTest extends DefaultTest { }
        interface DefaultTest {
            default String test() { return "DefaultTest"; }
        }
        interface SecondTest extends FirstTest {
            default String test() { return "SecondTest"; }
        }
        interface ThirdTest extends DefaultTest, SecondTest {
            default String test() { return "ThirdTest"; }
        }

        public static Object InterfaceDefaultMethodTest2() {
            TestInterfaceFirstTestClass testInterfaceFirstTestClass = new TestInterfaceFirstTestClass();
            DefaultTest defaultTest = testInterfaceFirstTestClass;
            FirstTest firstTest = testInterfaceFirstTestClass;
            SecondTest secondTest = testInterfaceFirstTestClass;
            ThirdTest thirdTest = testInterfaceFirstTestClass;

            return new Object[] {
                    defaultTest.test(),
                    firstTest.test(),
                    secondTest.test(),
                    thirdTest.test()
            };
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

    }

}
