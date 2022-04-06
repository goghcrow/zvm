package zvm.test.thirdparty;

// 修改自 https://github.com/zxh0/jvm.go/blob/master/test/testclasses/src/main/java/jvm/field/FieldsTest.java
// 修改自 https://github.com/zxh0/jvm.go/blob/master/test/testclasses/src/main/java/jvm/field/FieldAccessTest.java
public class Test_Fields {
    static class Sup {
        static int x;
        int a;
    }
    static class Sub extends Sup {
        static int y;
        int b;
    }
    public static Object staticFields() {
        int z = Sub.x + Sub.y;
        z += 100;
        Sub.y = z;
        Sub.x = z;
        return new Object[] {
            Sub.x, Sub.y
        };
    }
    public static Object instanceFields() {
        Sub sub = new Sub();
        int c = sub.a + sub.b;
        c += 100;
        sub.a = c;
        sub.b = c;
        return new Object[] {
                sub.a, sub.b
        };
    }



    public static Object test() {
        return new Object[] {
                B.i, B.k, B.a, B.b
        };
    }
    public static Object test1() {
        return new Object[] {
                B.i, B.k, B.a, B.b
        };
    }
    public static Object test2() {
        B.a=42; B.b=100;
        return new Object[] {
                B.i, B.k, B.a, B.b
        };
    }
    public static Object test3() {
        B.a=42; B.b=100;
        return new Object[] {
                B.i, B.k, B.a, B.b
        };
    }
    interface I {
        int i = val(1);
    }
    interface J {
        int j = val(2);
    }
    interface K extends I, J {
        int k = val(3);
    }
    static class A implements K {
        static int a = val(4);
    }
    static class B extends A {
        static int b = val(5);
    }
    static int val(int x) {
        return x;
    }



    public static Object ConstantStaticFieldsTest() {
        return new Object[] {
                ConstantStaticFields.z,
                getFieldValue("z"),
                ConstantStaticFields.b,
                getFieldValue("b"),
                ConstantStaticFields.c,
                getFieldValue("c"),
                ConstantStaticFields.s,
                getFieldValue("s"),
                ConstantStaticFields.x,
                getFieldValue("x"),
                ConstantStaticFields.y,
                getFieldValue("y"),
                ConstantStaticFields.j,
                getFieldValue("j"),
                ConstantStaticFields.f, 0.1,
                getFieldValue("f"),
                ConstantStaticFields.d, 0.1,
                getFieldValue("d"),
                ConstantStaticFields.str1,
                getFieldValue("str1"),
                ConstantStaticFields.str2,
                getFieldValue("str2")
        };
    }

    interface II {
        int i = val(1);
    }
    interface JJ extends II {
        int j = val(2);
    }
    static class CC implements II,JJ {
        static int x = i + j;
    }

    public static int test_init() {
        return CC.x;
    }

    static Object getFieldValue(String name) {
        return null;
// todo
//        try {
//            return ConstantStaticFieldsTest.class.getField(name).get(null);
//        } catch (ReflectiveOperationException e) {
//            throw new RuntimeException(e);
//        }
    }

    // 修改自 https://github.com/zxh0/jvm.go/blob/master/test/testclasses/src/main/java/jvm/field/ConstantStaticFieldsTest.java
    // A constant variable is a final variable of primitive type or type String
    // that is initialized with a constant expression (§15.28).
    static class ConstantStaticFields {
        public static final boolean z = true;
        public static final byte b = 125;
        public static final char c = 'c';
        public static final short s = 300;
        public static final int x = 100;
        public static final int y = x + 18;
        public static final long j = 1L;
        public static final float f = 3.14f;
        public static final double d = 2.71828;
        public static final String str1 = "hello";
        public static final String str2 = str1 + " world!";
    }
}
