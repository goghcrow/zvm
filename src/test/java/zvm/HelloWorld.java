package zvm;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.lang.annotation.ElementType.*;

/**
 * @author chuxiaofeng
 */
@Deprecated
public final strictfp class
HelloWorld<A, B extends A> extends Object
        implements Cloneable, Comparator<Integer> {
//    HelloWorld<@ANNO A, @ANNO B extends @ANNO A> extends @ANNO Object
//        implements @ANNO Cloneable, @ANNO Comparator<Integer> {
    boolean bool_val = true;
    byte byte_val = 42;
    short short_val = 42;
    int int_val = 42;
    long long_val = 42;
    float float_val = 3.14f;
    double double_val = 3.14f;
    char char_val = 'c';
    String string_val = "Hello World\n";
    int[] array_val = new int[] { 42 };
    Object object = new Object();
    @Deprecated
    int deprecated_field = 0;
    A generic_signature = null;
    public final int const_int = 42;
    public final static String const_string = "Hello World\n";
    Runnable lambda_method_ref = HelloWorld::static_method;
    @SuppressWarnings("Convert2MethodRef")
    Runnable lambda_val = () -> static_method();
    Runnable lambda_interface_method_ref = Interface::interface_method;
    @ANNO String[][] string_arr_arr_val1;
    String @ANNO [][] string_arr_arr_val2;
    String[] @ANNO [] string_arr_arr_val3;
    @ANNO Map<String,Object> m1;
    Map<@ANNO String,Object> m2;
    Map<String,@ANNO Object> m3;
    List<@ANNO ? extends String> l1;
    List<? extends @ANNO String> l2;
    static { static_method(); } // <clint>
    { instance_method(); }
    @ANNO HelloWorld(@ANNO int i) {} // ctor
    Void instance_method() { System.out.println(string_val); return null; }
    static Void static_method() { System.out.println(const_string); return null; }
    @Override public int compare(Integer o1, Integer o2) { return 0; } // test_synthetic method, 此处是 bridge method
    @Deprecated void deprecated_method() {}
    @SuppressWarnings({"TryWithIdenticalCatches", "CatchMayIgnoreException"})
    void exception_method(boolean a) throws IOException, ClassNotFoundException {
        try {  if (a) throw new IOException(); else throw new ClassNotFoundException(); }
        catch (IOException ex) { }
        catch (ClassNotFoundException ex) {}
    }
    @ANNO(bool_val = true, byte_val = 42, char_val = 42, short_val = 42, int_val = 42, long_val = 42, float_val = 3.14F,
    double_val = 3.14D, string_val = "Hello World\n", enum_val = Color.Red, class_val = { 0, 42, 100 })
    int anno_field = 42;
    @ANNO(42) void anno(@ANNO int param) { @ANNO int i = 42; }
    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE, PACKAGE, TYPE_PARAMETER, TYPE_USE})
    @ANNO(100)
    @interface ANNO {
        int value() default 42;
        boolean bool_val() default true;
        byte byte_val() default 42;
        char char_val() default  42;
        short short_val() default  42;
        int int_val() default  42;
        long long_val() default  42;
        float float_val() default  3.14F;
        double double_val() default  3.14D;
        String string_val() default "Hello World\n";
        Color enum_val() default Color.Red;
        int[] class_val() default { 0, 42, 100 };
    }
    @ANNO enum Color { Red, Green, Blue  }
    @ANNO interface Interface { static void interface_method() {}}
    @SuppressWarnings("InnerClassMayBeStatic")
    @ANNO abstract class AbstractInnerClass { int i = 42; void m() {} }
    @ANNO abstract static class AbstractInnerStaticClass { int i = 42; void m() {} }
    static class Outer {
        static class MiddleStatic1 { class Inner { } }
        static class MiddleStatic2 { static class InnerStatic { } }
        class Middle1 { class Inner { } }
        class Middle2<@ANNO T> {
            class Inner<@ANNO U> {}
        }
    }
}
