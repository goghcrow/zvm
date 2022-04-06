package zvm.test.thirdparty;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;

public class Test_CheckCast {

    public static Object CheckCast() {
        sClass();
        sArray();
        sArray_tArray();
        return null;
    }

    static class Sup implements Closeable {
        @Override
        public void close() throws IOException {
            System.out.println("close!");
        }
    }
    static class Sub extends Sup implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static void sClass() {
        Object s = new Sub();
        Sub t1 = (Sub) s;
        Sup t2 = (Sup) s;
        Serializable t3 = (Serializable) s;
        Closeable t4 = (Closeable) s;
        AutoCloseable t5 = (AutoCloseable) s;
    }

    private static void sArray() {
        Object s = new String[0];
        Cloneable t1 = (Cloneable) s;
        Serializable t2 = (Serializable) s;
    }

    private static void sArray_tArray() {
        Object s = new Sub[0];
        Sup[] t1 = (Sup[]) s;
        Object[] t2 = (Object[]) s;
        Serializable[] t3 = (Serializable[]) s;
        Closeable[] t4 = (Closeable[]) s;
        AutoCloseable[] t5 = (AutoCloseable[]) s;

        Object s1 = new int[0];
        int[] t6 = (int[]) s1;
    }
}
