package zvm.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

/**
 * @author chuxiaofeng
 */
public class Test_isAssignableFrom {
    static class S {}
    interface I {}
    static class C extends S implements I {}

    class C1 implements I3 {}
    class C2 extends C1 { }
    class C3 extends C2 { }

    interface I1 {}
    interface I2 extends I1 {}
    interface I2_1 {}
    interface I3 extends I2, I2_1 {}


    @SuppressWarnings("ConstantConditions")
    public static Object bug() {
        List<Object> lst = new ArrayList<>();
        lst.add(new Object[0] instanceof Cloneable); // assertTrue
        lst.add(new int[0] instanceof Serializable); // assertTrue
        lst.add(new byte[0] instanceof Object); // assertTrue

        lst.add(C1.class.isAssignableFrom(Object[].class)); // assertFalse
        lst.add(C1.class.isAssignableFrom(int[].class)); // assertFalse

//        System.out.println(int[].class);
//        System.out.println(Object[].class);

        Object o1 = new Object[0];
        Object o2 = new int[0];
        lst.add(o1 instanceof C1); // assertFalse
        lst.add(o2 instanceof I1); // assertFalse

        return lst;
    }

    public static Object fast_is_subtype() {
        List<Object> lst = new ArrayList<>();
        lst.add(new Cloneable[0] instanceof Object);
        lst.add(new Cloneable[0] instanceof Object[]);

        lst.add(new Cloneable[0][] instanceof Object);
        lst.add(new Cloneable[0][] instanceof Object[]);
        lst.add(new Cloneable[0][] instanceof Object[][]);

//        lst.add();
        return lst;
    }

    public static Object supertype_isAssignableFrom() {
        List<Object> lst = new ArrayList<>();
        // 虽然可以转换但是 null type 并不是引用类型...
        String str = ((String) null);

        // 基础类型
        lst.add(void.class.isAssignableFrom(void.class)); // assertTrue
        lst.add(int.class.isAssignableFrom(int.class)); // assertTrue
        lst.add(int.class.isAssignableFrom(byte.class)); // assertFalse
        lst.add(double.class.isAssignableFrom(float.class)); // assertFalse
        lst.add(Object.class.isAssignableFrom(void.class)); // assertFalse
        lst.add(Object.class.isAssignableFrom(int.class)); // assertFalse

        // 自身
        lst.add(S.class.isAssignableFrom(S.class)); // assertTrue
        lst.add(I.class.isAssignableFrom(I.class)); // assertTrue

        // 直接超类
        lst.add(S.class.isAssignableFrom(C.class)); // assertTrue
        lst.add(I.class.isAssignableFrom(C.class)); // assertTrue
        lst.add(Object.class.isAssignableFrom(I.class)); // assertTrue

        // 传递性
        lst.add(C1.class.isAssignableFrom(C3.class)); // assertTrue
        lst.add(C2.class.isAssignableFrom(C3.class)); // assertTrue
        lst.add(C3.class.isAssignableFrom(C3.class)); // assertTrue
        lst.add(I1.class.isAssignableFrom(C3.class)); // assertTrue
        lst.add(I2.class.isAssignableFrom(C3.class)); // assertTrue
        lst.add(I2_1.class.isAssignableFrom(C3.class)); // assertTrue
        lst.add(I3.class.isAssignableFrom(C3.class)); // assertTrue

        // 数组-自身
        lst.add(S[].class.isAssignableFrom(S[].class)); // assertTrue
        lst.add(I[].class.isAssignableFrom(I[].class)); // assertTrue
        lst.add(S[].class.isAssignableFrom(I[].class)); // assertFalse

        // 数组-直接超类
        lst.add(S[].class.isAssignableFrom(C[].class)); // assertTrue
        lst.add(C[].class.isAssignableFrom(S[].class)); // assertFalse
        lst.add(I[].class.isAssignableFrom(C[].class)); // assertTrue
        lst.add(C[].class.isAssignableFrom(I[].class)); // assertFalse
        lst.add(Object[].class.isAssignableFrom(I[].class)); // assertTrue
        lst.add(I[].class.isAssignableFrom(Object[].class)); // assertFalse

        // 数组-传递性
        lst.add(C1[].class.isAssignableFrom(C3[].class)); // assertTrue
        lst.add(C3[].class.isAssignableFrom(C1[].class)); // assertFalse
        lst.add(C2[].class.isAssignableFrom(C3[].class)); // assertTrue
        lst.add(C3[].class.isAssignableFrom(C2[].class)); // assertFalse
        lst.add(C3[].class.isAssignableFrom(C3[].class)); // assertTrue
        lst.add(I1[].class.isAssignableFrom(C3[].class)); // assertTrue
        lst.add(C3[].class.isAssignableFrom(C1[].class)); // assertFalse
        lst.add(I2[].class.isAssignableFrom(C3[].class)); // assertTrue
        lst.add(C3[].class.isAssignableFrom(I2[].class)); // assertFalse
        lst.add(I2_1[].class.isAssignableFrom(C3[].class)); // assertTrue
        lst.add(C3[].class.isAssignableFrom(I2_1[].class)); // assertFalse
        lst.add(I3[].class.isAssignableFrom(C3[].class)); // assertTrue
        lst.add(C3[].class.isAssignableFrom(I3[].class)); // assertFalse

        // 数组-其他
        lst.add(Object.class.isAssignableFrom(Object[].class)); // assertTrue
        lst.add(Cloneable.class.isAssignableFrom(Object[].class)); // assertTrue
        lst.add(Serializable.class.isAssignableFrom(Object[].class)); // assertTrue
        lst.add(RandomAccess.class.isAssignableFrom(Object[].class)); // assertFalse

        // 数组-基础类型数组
        lst.add(Object.class.isAssignableFrom(int[].class)); // assertTrue
        lst.add(Cloneable.class.isAssignableFrom(int[].class)); // assertTrue
        lst.add(Serializable.class.isAssignableFrom(int[].class)); // assertTrue
        lst.add(int[].class.isAssignableFrom(int[].class)); // assertTrue
        lst.add(byte[].class.isAssignableFrom(byte[].class)); // assertTrue
        lst.add(int[].class.isAssignableFrom(byte[].class)); // assertFalse

        // 数组-
        lst.add(Object[].class.isAssignableFrom(Object[][].class)); // assertTrue
        lst.add(Cloneable[].class.isAssignableFrom(Object[][].class)); // assertTrue
        lst.add(Serializable[].class.isAssignableFrom(Object[][].class)); // assertTrue

        // 数组-基础类型数组
        lst.add(Object[].class.isAssignableFrom(int[][].class)); // assertTrue
        lst.add(Cloneable[].class.isAssignableFrom(int[][].class)); // assertTrue
        lst.add(Serializable[].class.isAssignableFrom(int[][].class)); // assertTrue

        return lst;
    }

    /*
      https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html#jls-4.10
      java 的子类型
        S is a proper supertype of T, written S > T, if S :> T and S ≠ T.
        T is a proper subtype of S, written T < S, if T <: S and S ≠ T.
        T is a direct subtype of S, written T <1 S, if S >1 T.
        Subtyping does not extend through parameterized types: T <: S does not imply that C<T> <: C<S>.

      https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html#jls-4.10.2
      "The supertypes of a type are obtained by reflexive and transitive closure over the direct supertype relation"
      !!! 自反传递 !!!
      Given a non-generic type declaration C, the direct supertypes of the type C are all of the following:
           The direct superclass of C (§8.1.4).
           The direct superinterfaces of C (§8.1.5).
           The type Object, if C is an interface type with no direct superinterfaces (§9.1.3).
           Given a generic type declaration C<F1,...,Fn> (n > 0), the direct supertypes of the raw type C (§4.8) are all of the following:

      https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html#jls-4.10.3
      数组类型的子类型
      The following rules define the direct supertype relation among array types:
       If S and T are both reference types, then S[] >1 T[] iff S >1 T.
       Object >1 Object[]
       Cloneable >1 Object[]
       java.io.Serializable >1 Object[]
       If P is a primitive type, then:
           Object >1 P[]
           Cloneable >1 P[]
           java.io.Serializable >1 P[]
     */

}
