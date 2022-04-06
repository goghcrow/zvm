package zvm.test.thirdparty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// 测试用例修改自 github.com/lihaoyi/Metascala
// 测试用例修改自 https://github.com/zxh0/jvm.go/tree/master/test/testclasses/src/main/java/jvm/ex
public class Test_Exceptions {

    public static Object AThrow() {
        try {
            foo();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }
    static void foo() {
        try {
            throw new RuntimeException("foo!");
        } catch (RuntimeException e) {
            throw new RuntimeException("bar!");
        }
    }

    // ----------------------------------------------------------------

    public static Object test() {
        List<? super Object> lst = new ArrayList<>();

        lst.add(throwCatch(-1));
        lst.add(throwCatch(0));
        lst.add(throwCatch(1));

        lst.add(multiCatch(-1));
        lst.add(multiCatch(0));
        lst.add(multiCatch(1));
        lst.add(multiCatch(2));
        lst.add(multiCatch(3));
        lst.add(multiCatch(4));

        lst.add(nullPointer(new Object()));
        lst.add(nullPointer(null));

        lst.add(arrayIndexOutOfBounds(-1));
        lst.add(arrayIndexOutOfBounds(0));
        lst.add(arrayIndexOutOfBounds(1));
        lst.add(arrayIndexOutOfBounds(2));
        lst.add(arrayIndexOutOfBounds(3));
        lst.add(arrayIndexOutOfBounds(4));
        return lst;
    }
    
    
    static int throwCatch(int a){

        int b = 1;
        if (a >= 1) b += 1;
        else        b += 2;

        try{
            int j = a + 1;
            if(a > 0) throw new Exception();
            b += j;
        }catch(Exception e){
            return b - 1;
        }
        return b;
    }

    static int multiCatch(int in){
        try{
            try{
                try{
                    try{
                        switch (in){
                            case 0: throw new IOException("IO");
                            case 1: throw new ArrayIndexOutOfBoundsException("Array!");
                            case 2: throw new NullPointerException("NPE");
                            case 3: throw new IllegalArgumentException("IaE");
                            default: throw new Exception("Excc");
                        }
                    }catch(IOException e){
                        return 0;
                    }
                }catch(ArrayIndexOutOfBoundsException e){
                    return 1;
                }
            }catch(NullPointerException e){
                return 2;
            }
        }catch(Exception e){
            return 3;
        }
    }

    static String nullPointer(Object o){
        try{
            return o.toString().substring(0, o.toString().indexOf('@'));
        }catch(NullPointerException npe){
            return "null!";
        }
    }

    static String arrayIndexOutOfBounds(int i){
        try{
            int[] a = {1, 2, 4, 8};
            return "result! " + a[i] ;
        }catch(ArrayIndexOutOfBoundsException e){
            return "array index out of bounds: " + i;
        }
    }

    // ----------------------------------------------------------------

    public static Object catch_test() {
        return new Object[] {
                f0(), f1(), f2(), f3()
        };
    }

    public static int FinallyTest() {
        int x = 1;
        try {
            bad();
            x = 100;
        } catch (Exception e) {
            x += 2;
        } finally {
            x *= 3;
        }
        return x;
    }

    static int f0() {
        try {
            bad();
            return -1;
        } catch (Throwable t) {
            return 0;
        }
    }
    static int f1() {
        try {
            bad();
            return -1;
        } catch (Exception e) {
            return 1;
        }
    }
    static int f2() {
        try {
            bad();
            return -1;
        } catch (RuntimeException e) {
            return 2;
        }
    }
    static int f3() {
        try {
            bad2();
            return -1;
        } catch (RuntimeException e) {
            return 3;
        }
    }
    static void bad() {
        throw new RuntimeException("BAD!");
    }
    static void bad2() {
        try {
            bad();
        } catch (RuntimeException e) {
            throw e;
        }
    }

    // ----------------------------------------------------------------

    // todo 隐藏栈帧

//    public static Object StackTraceTest() {
//        try {
//            foo();
//            return null;
//        } catch (Exception e) {
//            return e.getStackTrace();
//        }
//    }
//    static void foo() {
//        bar();
//    }
//    static void bar() {
//        bad();
//    }

    // ----------------------------------------------------------------

    public static Object npe_test() {
        InstructionNpeTest test = new InstructionNpeTest();

        Object[] r = new Object[5];
        try {
            test.arraylength();
            r[1] = 0;
        } catch (NullPointerException e) {
            r[1] = 1;
        }
        try {
            test.athrow();
            r[1] = 0;
        } catch (Exception e) {
            r[1] = 1;
        }
        try {
            test.getfield();
            r[1] = 0;
        } catch (NullPointerException e) {
            r[1] = 1;
        }
        try {
            test.monitorenter();
            r[1] = 0;
        } catch (NullPointerException e) {
            r[1] = 1;
        }
        try {
            test.invokevirtual();
            r[1] = 0;
        } catch (NullPointerException e) {
            r[1] = 1;
        }
        return r;
    }

    static class InstructionNpeTest {
        private int i;
        public void arraylength() {
            int[] x = (int[]) nullObj();
            int y = x.length;
        }
        public void athrow() throws Exception {
            Exception x = (Exception) nullObj();
            throw x;
        }
        public void getfield() {
            InstructionNpeTest x = (InstructionNpeTest) nullObj();
            int y = x.i;
        }
        public void monitorenter() {
            Object x = nullObj();
            synchronized(x) {
                bad();
            }
        }
        public void invokevirtual() {
            Object x = nullObj();
            x.toString();
        }
        static Object nullObj() { return null; }
    }

    // ----------------------------------------------------------------

    public static Object ex_test() {
        InstructionExTest test = new InstructionExTest();

        Object[] r = new Object[5];
        try {
            test.checkcast();
            r[1] = 0;
        } catch (ClassCastException e) {
            r[1] = 1;
        }
        try {
            test.newarray();
            r[1] = 0;
        } catch (NegativeArraySizeException e) {
            r[1] = 1;
        }
        try {
            test.anewarray();
            r[1] = 0;
        } catch (NegativeArraySizeException e) {
            r[1] = 1;
        }
        try {
            test.aload();
            r[1] = 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            r[1] = e.getMessage();
        }
        try {
            test.astore();
            r[1] = 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            r[1] = e.getMessage();
        }
        try {
            test.idiv();
            r[1] = 0;
        } catch (ArithmeticException e) {
            r[1] = 1;
        }
        try {
            test.irem();
            r[1] = 0;
        } catch (ArithmeticException e) {
            r[1] = 1;
        }
        return r;
    }

    public static class InstructionExTest {
        public void checkcast() {
            Object x = "String";
            Integer y = (Integer) x;
        }
        public void newarray() {
            int[] a = new int[-3];
        }
        public void anewarray() {
            Object[] a = new Object[-1];
        }
        public void aload() {
            int[] a = {1};
            int x = a[2];
        }
        public void astore() {
            int[] a = {};
            a[1] = 2;
            int x = a[1];
        }
        public void idiv() {
            int x = 0;
            int y = 1 / x;
        }
        public void irem() {
            int x = 0;
            int y = 1 % x;
        }
    }
    // ----------------------------------------------------------------
}
