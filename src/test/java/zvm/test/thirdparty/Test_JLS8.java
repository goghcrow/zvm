package zvm.test.thirdparty;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

// 修改自 https://github.com/zxh0/jvm.go/tree/master/test/testclasses/src/main/java/jls8
public class Test_JLS8 {

    static class StringOut extends PrintWriter {
        public StringOut () {
            super(new StringWriter());
        }
        @Override
        public String toString() {
            String str = out.toString();
            return str.replaceAll("\r\n", "\n");
        }
    }

    public static Object test() {
        List<Object> lst = new ArrayList<>();
        lst.add(new Eg12_4_1_1().test());
        lst.add(new Eg12_4_1_2().test());
        lst.add(new Eg12_4_1_3().test());
        lst.add(new Eg12_5_2().test());
        return lst;
    }

    /**
     * Example 12.4.1-1.
     * Superclasses Are Initialized Before Subclasses
     */
    public static class Eg12_4_1_1 {
        private static final StringOut out = new StringOut();
        private static class Super {
            static { out.print("Super "); }
        }
        private static class One {
            static { out.print("One "); }
        }
        private static class Two extends Super {
            static { out.print("Two "); }
        }
        public Object test() {
            One o = null;
            Two t = new Two();
            Eg12_4_1_1.out.println((Object)o == (Object)t);
            String actual = Eg12_4_1_1.out.toString();
            return new Object[] {
                    "Super Two false\n",
                    actual,
                    "Super Two false\n".equals(actual)
            };
        }
    }


    /**
     * Example 12.4.1-2.
     * Only The Class That Declares static Field Is Initialized
     */
    public static class Eg12_4_1_2 {
        private static class Super {
            static int taxi = 1729;
            static void super_method() { }
        }
        private static class Sub extends Super {
            static {
                System.out.print("Sub ");
                if (true) {
                    throw new RuntimeException("BAD");
                }
            }
            static void sub_method() { }
        }
        public Object test() {
            Sub.super_method();
            return new Object[] {
                    1729,
                    Sub.taxi,
                    1729 == Sub.taxi
            };
        }
    }


    /**
     * Example 12.4.1-3.
     * Interface Initialization Does Not Initialize Superinterfaces
     */
    public static class Eg12_4_1_3 {
        private static final StringOut sout = new StringOut();
        private interface I {
            int i = 1, ii = out("ii", 2);
            static void i_method() {}
        }
        private interface J extends I {
            int j = out("j", 3), jj = out("jj", 4);
            static void j_method() {}
        }
        private interface K extends J {
            int k = out("k", 5);
            static void k_method() {}
        }
        static int out(String s, int i) {
            sout.println(s + "=" + i);
            return i;
        }
        public Object test() {
            sout.println("" + J.i);
            sout.println("" + K.j);
//            System.out.println(sout);
            J.j_method();
            K.k_method();
            String actual = sout.toString();
            return new Object[] {
                    "1\nj=3\njj=4\n3\n",
                    actual,
                    "1\nj=3\njj=4\n3\n".equals(actual)
            };
        }
    }

    /**
     * Example 12.5-2.
     * Dynamic Dispatch During Instance Creation
     */
    public static class Eg12_5_2 {
        private static final StringOut out = new StringOut();
        private static class Super {
            Super() { printThree(); }
            void printThree() { out.println("three"); }
        }
        private static class Test extends Super {
            int three = (int)Math.PI;  // That is, 3
            void printThree() { out.println(three); }
        }
        public Object test() {
            Test t = new Test();
            t.printThree();
            String actual = out.toString();
            return new Object[] {
                    "0\n3\n",
                    actual,
                    "0\n3\n".equals(actual)
            };
        }
    }
}
