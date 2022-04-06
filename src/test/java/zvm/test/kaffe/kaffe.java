package zvm.test.kaffe;

import org.junit.Test;
import zvm.VM;

public class kaffe {
    static VM vm = new VM(new String[]{
            "/Users/chuxiaofeng/Library/Mobile Documents/com~apple~CloudDocs/project/enable-criteria/j/target/test-classes/"
    });

    @Test
    public void diff() {
        for (Class<?> clazz : kaffe.class.getDeclaredClasses()) {
            System.out.println(clazz.getName());
            if (!zvm.Test.diff1(vm, clazz)) {
                throw new AssertionError();
            }
        }
    }


    static class VirtualMethod {
        public static final int test_0__base_fact = 1;
        public static final int test_1__base_fact = 1;
        public static final int test_2__base_fact = 2;
        public static final int test_3__base_fact = 6;
        public static final int test_4__base_fact = 24;
        public static final int test_5__base_fact = 120;

        public int fact(int i)
        {
            if (i == 1 || i == 0)
                return 1;

            return fact(i-1) * i;
        }

        public static int base_fact(int i)
        {
            VirtualMethod m = new VirtualMethod();

            return m.fact(i);
        }
    }

    static class TypeConversion
    {
        public static final int test_1__float_to_int = 1;

        public static int float_to_int(float a)
        {
            return (int)a;
        }

        public static final int test_1__double_to_int = 1;

        public static int double_to_int(double a)
        {
            return (int)a;
        }

        public static final int test__float_nan_to_int = 0;

        /*
         * NB: This must _not_ be final, otherwise the java compiler will optimize
         * it away.
         */
        public static float mynanf = Float.NaN;

        public static int float_nan_to_int()
        {
            return (int)mynanf;
        }

        public static final int test__double_nan_to_int = 0;
        public static double mynand = Double.NaN;

        public static int double_nan_to_int()
        {
            return (int)mynand;
        }

        public static final float test_1__int_to_float = 1.0F;
        public static final float test_255__int_to_float = 255.0F;
        public static final float test_50225__int_to_float = 50225.0F;

        public static float int_to_float(int a)
        {
            return (float)a;
        }

        public static final double test_1__int_to_double = 1.0;
        public static final double test_255__int_to_double = 255.0;
        public static final double test_50225__int_to_double = 50225.0;

        public static double int_to_double(int a)
        {
            return (double)a;
        }

        private TypeConversion()
        {
        }
    }

    static class StaticMethodCall
    {
        public static int int_method_void()
        {
            return 0xDEADBEEF;
        }

        public static final int test__int_call_void = 0xDEADBEEF;

        public static int int_call_void()
        {
            return int_method_void();
        }

        public static int int_method_int(int a)
        {
            return a;
        }

        public static final int test_1__int_call_int = 1;

        public static int int_call_int(int a)
        {
            return int_method_int(a);
        }

        public static int int_method_int_int(int a, int b)
        {
            return a + b;
        }

        public static final int test_1_2__int_call_int_int = 3;

        public static int int_call_int_int(int a, int b)
        {
            return int_method_int_int(a, b);
        }

        public static int int_method_int_int2(int a, int b)
        {
            return b;
        }

        public static final int test__int_call_int_int2 = 2;

        public static int int_call_int_int2()
        {
            return int_method_int_int2(1, 2);
        }

        public static float float_method_int_float(int a, float b)
        {
            return b;
        }

        public static final float test_1_2__float_call_int_float = 2.0F;
        public static final float test_2_3__float_call_int_float = 3.0F;

        public static float float_call_int_float(int a, float b)
        {
            return float_method_int_float(a, b);
        }

        public static double double_method_int_float_double_int(int a,
                                                                float b,
                                                                double c,
                                                                int d)
        {
            return c + (double)d;
        }

        public static final double test_4__double_call_int_float_double_int =
                7.0;

        public static double double_call_int_float_double_int(int d)
        {
            return double_method_int_float_double_int(1, 2.0F, 3.0, d);
        }

        private StaticMethodCall()
        {
        }
    }

    static class StaticFields
    {
        /* Force a <clinit> method. */
        public static int int_field0 = 0;
        public static int int_field1 = 0;

        public static long long_field0;
        public static long long_field1;

        public static float float_field0;
        public static float float_field1;

        public static double double_field0;
        public static double double_field1;

        public static final int test_1__exchange_int0 = 0;
        public static final int test_2__exchange_int0 = 1;

        public static int exchange_int0(int a)
        {
            int retval;

            retval = int_field0;
            int_field0 = a;
            return retval;
        }

        public static final long test_1__exchange_long0 = 0L;
        public static final long test_2__exchange_long0 = 1L;

        public static long exchange_long0(long a)
        {
            long retval;

            retval = long_field0;
            long_field0 = a;
            return retval;
        }

        public static final float test_1__exchange_float0 = 0.0F;
        public static final float test_2__exchange_float0 = 1.0F;

        public static float exchange_float0(float a)
        {
            float retval;

            retval = float_field0;
            float_field0 = a;
            return retval;
        }

        public static final double test_1__exchange_double0 = 0.0;
        public static final double test_2__exchange_double0 = 1.0;

        public static double exchange_double0(double a)
        {
            double retval;

            retval = double_field0;
            double_field0 = a;
            return retval;
        }

        private StaticFields()
        {
        }
    }

    static class ObjectFields
    {
        public static final boolean test_true__boolean_get_ref = true;

        public static final boolean boolean_get_ref(boolean z)
        {
            ObjectFields of = new ObjectFields(z);

            return of.z;
        }

        public static final byte test_1__byte_get_ref = 1;

        public static byte byte_get_ref(byte b)
        {
            ObjectFields of = new ObjectFields(b);

            return of.b;
        }

        public static final char test_1__char_get_ref = '1';

        public static char char_get_ref(char c)
        {
            ObjectFields of = new ObjectFields(c);

            return of.c;
        }

        public static final short test_1__short_get_ref = 1;

        public static short short_get_ref(short s)
        {
            ObjectFields of = new ObjectFields(s);

            return of.s;
        }

        public static final int test_1__int_get_ref = 1;

        public static int int_get_ref(int i)
        {
            ObjectFields of = new ObjectFields(i);

            return of.i;
        }

        public static final float test_1__float_get_ref = 1.0F;

        public static float float_get_ref(float f)
        {
            ObjectFields of = new ObjectFields(f);

            return of.f;
        }

        public static final double test_1__double_get_ref = 1.0;

        public static double double_get_ref(double d)
        {
            ObjectFields of = new ObjectFields(d);

            return of.d;
        }

        public static final int test_1__object_get_ref = 1;

        public static int object_get_ref(int i)
        {
            ObjectFields of = new ObjectFields(new ObjectFields(i));

            return of.o.i;
        }

        public static final int test__six_ints = 6;

        public static int six_ints()
        {
            ObjectFields of = new ObjectFields(1, 2, 3, 4, 5, 6);

            return of.i6;
        }

        private boolean z;
        private byte b;
        private char c;
        private short s;
        private int i;
        private float f;
        private double d;
        private ObjectFields o;

        private int i1;
        private int i2;
        private int i3;
        private int i4;
        private int i5;
        private int i6;

        private ObjectFields(boolean z)
        {
            this.z = z;
        }

        private ObjectFields(byte b)
        {
            this.b = b;
        }

        private ObjectFields(char c)
        {
            this.c = c;
        }

        private ObjectFields(short s)
        {
            this.s = s;
        }

        private ObjectFields(int i)
        {
            this.i = i;
        }

        private ObjectFields(float f)
        {
            this.f = f;
        }

        private ObjectFields(double d)
        {
            this.d = d;
        }

        private ObjectFields(ObjectFields o)
        {
            this.o = o;
        }

        private ObjectFields(int i1, int i2, int i3, int i4, int i5, int i6)
        {
            this.i1 = i1;
            this.i2 = i2;
            this.i3 = i3;
            this.i4 = i4;
            this.i5 = i5;
            this.i6 = i6;
        }
    }

    static class PrimitiveArrays
    {
        public static final boolean boolean_array0[] = {
                false, false, false, false,
                false, false, false, true,
                false, false, true, false,
                false, false, true, true,
                false, true, false, false,
                false, true, false, true,
                false, true, true, false,
                false, true, true, true,
                true, false, false, false,
                true, false, false, true,
                true, false, true, false,
                true, false, true, true,
                true, true, false, false,
                true, true, false, true,
                true, true, true, false,
                true, true, true, true,
        };

        public static final byte byte_array0[] = {
                0x7d, 0x6e, 0x5a, 0x4d, 0x3b, 0x2e, 0x1e, 0x0f,
        };

        public static final char char_array0[] = {
                'B', 'u', 'f', 'f', 'y',
        };

        public static final short short_array0[] = {
                0x70f0, 0x70f0,
                0x0f0f, 0x0f0f,
                0x7ead, 0x7eef,
        };

        public static final int int_array0[] = {
                0xf0f0f0f0,
                0x0f0f0f0f,
                0xdeadbeef,
        };

        public static final long long_array0[] = {
                0xdeadbeefd0decadeL,
                0xcafebabef00ba000L,
        };

        public static final boolean test_0__ref_boolean0 = false;
        public static final boolean test_7__ref_boolean0 = true;
        public static final boolean test_8__ref_boolean0 = false;

        public static boolean ref_boolean0(int a)
        {
            return boolean_array0[a];
        }

        public static final byte test_0__ref_byte0 = 0x7d;
        public static final byte test_1__ref_byte0 = 0x6e;
        public static final byte test_2__ref_byte0 = 0x5a;

        public static byte ref_byte0(int a)
        {
            return byte_array0[a];
        }

        public static final char test_0__ref_char0 = 'B';
        public static final char test_1__ref_char0 = 'u';
        public static final char test_2__ref_char0 = 'f';

        public static char ref_char0(int a)
        {
            return char_array0[a];
        }

        public static final short test_0__ref_short0 = 0x70f0;
        public static final short test_1__ref_short0 = 0x70f0;
        public static final short test_4__ref_short0 = 0x7ead;

        public static short ref_short0(int a)
        {
            return short_array0[a];
        }

        public static final int test_0__ref_int0 = 0xf0f0f0f0;
        public static final int test_1__ref_int0 = 0x0f0f0f0f;
        public static final int test_2__ref_int0 = 0xdeadbeef;

        public static int ref_int0(int a)
        {
            return int_array0[a];
        }

        public static final long test_0__ref_long0 = 0xdeadbeefd0decadeL;
        public static final long test_1__ref_long0 = 0xcafebabef00ba000L;

        public static long ref_long0(int a)
        {
            return long_array0[a];
        }

        private PrimitiveArrays()
        {
        }
    }

    static class ParameterizedMethods
    {

        /* Basic in and out parameter passing. */

        public static final int test_0x00000000__int_method_int = 0;
        public static final int test_0x00000001__int_method_int = 1;
        public static final int test_0xdeadbeef__int_method_int = 0xdeadbeef;

        public static int int_method_int(int a)
        {
            return a;
        }

        public static final short test_0x0000__short_method_short = 0;
        public static final short test_0x0001__short_method_short = 1;
        public static final short test_0x7eef__short_method_short = 0x7eef;

        public static short short_method_short(short a)
        {
            return a;
        }

        public static final long test_0x0000000000000000__long_method_long = 0L;
        public static final long test_0x0000000000000001__long_method_long = 1L;
        public static final long test_0xdeadbeefd0decade__long_method_long =
                0xdeadbeefd0decadeL;

        public static long long_method_long(long a)
        {
            return a;
        }

        public static final float test_0__float_method_float = 0;
        public static final float test_1__float_method_float = 1;
        public static final float test_100000__float_method_float = 100000;
        public static final float test_123d456__float_method_float = 123.456F;

        public static float float_method_float(float a)
        {
            return a;
        }

        public static final double test_0__double_method_double = 0;
        public static final double test_1__double_method_double = 1;
        public static final double test_100000__double_method_double = 100000;
        public static final double test_123d456__double_method_double = 123.456D;

        public static double double_method_double(double a)
        {
            return a;
        }

        /*
         * Mixed arguments, mostly a challenge for register rich CPUs.  Errors here
         * may also be due to a broken sysdepCallMethod.
         */

        public static final int test_1_2_3__int_method_int_float_int = 3;

        public static int int_method_int_float_int(int a, float b, int c)
        {
            return c;
        }

        public static final int test_1_2_3__int_method_int_double_int = 3;

        public static int int_method_int_double_int(int a, double b, int c)
        {
            return c;
        }

        public static final int test_1_2_3_4__int_method_int_float_double_int = 4;

        public static int int_method_int_float_double_int(int a,
                                                          float b,
                                                          double c,
                                                          int d)
        {
            return d;
        }

        public static final float
                test_1_2_3_4__float_method_int_float_double_int = 2.0F;

        public static float float_method_int_float_double_int(int a,
                                                              float b,
                                                              double c,
                                                              int d)
        {
            return b;
        }

        public static final double
                test_1_2_3_4__double_method_int_float_double_int = 3.0;

        public static double double_method_int_float_double_int(int a,
                                                                float b,
                                                                double c,
                                                                int d)
        {
            return c;
        }


        /* Test long parameter lists */

        public static final int test_0_1_2_3__int_method3 = 3;

        public static int int_method3(int a, int b, int c, int d)
        {
            return d;
        }

        public static final int test_0_1_2_3_4__int_method4 = 4;

        public static int int_method4(int a, int b, int c, int d, int e)
        {
            return e;
        }

        public static final int test_0_1_2_3_4_5__int_method5 = 5;

        public static int int_method5(int a, int b, int c, int d, int e, int f)
        {
            return f;
        }

        public static final int test_0_1_2_3_4_5_6__int_method6 = 6;

        public static int int_method6(int a, int b, int c, int d, int e, int f,
                                      int g)
        {
            return g;
        }

        public static final int test_0_1_2_3_4_5_6_7__int_method7 = 7;

        public static int int_method7(int a, int b, int c, int d, int e, int f,
                                      int g, int h)
        {
            return h;
        }

    /*
    public static final int test_0_1_2_3_4_5_6_7_8__int_method8 = 8;

    public static int int_method8(int a, int b, int c, int d, int e, int f,
				  int g, int h, int i)
    {
	return i;
    }

    public static final int test_0_1_2_3_4_5_6_7_8_9__int_method9 = 9;

    public static int int_method9(int a, int b, int c, int d, int e, int f,
				  int g, int h, int i, int j)
    {
	return j;
    }

    public static final int test_0_1_2_3_4_5_6_7_8_9_10__int_method10 = 10;

    public static int int_method10(int a, int b, int c, int d, int e, int f,
				   int g, int h, int i, int j, int k)
    {
	return k;
    }

    public static final int test_0_1_2_3_4_5_6_7_8_9_10_11__int_method11 = 11;

    public static int int_method11(int a, int b, int c, int d, int e, int f,
				   int g, int h, int i, int j, int k, int l)
    {
	return l;
    }
    */

        private ParameterizedMethods()
        {
        }
    }

    static class ParameterizedMathMethods
    {
        public static final int test_2__int_postinc_int = 2;
        public static final int test_3__int_postinc_int = 3;

        public static int int_postinc_int(int a)
        {
            return a++;
        }

        public static final int test_2__int_postdec_int = 2;
        public static final int test_3__int_postdec_int = 3;

        public static int int_postdec_int(int a)
        {
            return a--;
        }

        public static final int test_2__int_preinc_int = 3;
        public static final int test_3__int_preinc_int = 4;

        public static int int_preinc_int(int a)
        {
            return ++a;
        }

        public static final int test_2__int_predec_int = 1;
        public static final int test_3__int_predec_int = 2;

        public static int int_predec_int(int a)
        {
            return --a;
        }

        public static final int test_2__int_neg_int = -2;
        public static final int test_0xFFFFFFFE__int_neg_int = 2;

        public static int int_neg_int(int a)
        {
            return -a;
        }

        public static final int test_2__int_add_int0 = 4;
        public static final int test_128__int_add_int0 = 130;

        public static final int test_2__int_add_int1 = 2 + 255;
        public static final int test_128__int_add_int1 = 128 + 255;

        public static final int test_128__int_add_int2 = 128 + 32768;

        public static int int_add_int0(int a)
        {
            return a + 2;
        }

        public static int int_add_int1(int a)
        {
            return a + 255;
        }

        public static int int_add_int2(int a)
        {
            return a + 32768;
        }

        public static final int test_2__int_sub_int0 = 0;
        public static final int test_128__int_sub_int0 = 126;

        public static final int test_2__int_sub_int1 = 2 - 255;
        public static final int test_128__int_sub_int1 = 128 - 255;

        public static final int test_128__int_sub_int2 = 128 - 32768;

        public static int int_sub_int0(int a)
        {
            return a - 2;
        }

        public static int int_sub_int1(int a)
        {
            return a - 255;
        }

        public static int int_sub_int2(int a)
        {
            return a - 32768;
        }

        public static final int test_2_2__int_add_int_int = 4;
        public static final int test_128_64__int_add_int_int = 128 + 64;

        public static int int_add_int_int(int a, int b)
        {
            return a + b;
        }

        public static final int test_2_2__int_sub_int_int = 0;
        public static final int test_128_64__int_sub_int_int = 128 - 64;

        public static int int_sub_int_int(int a, int b)
        {
            return a - b;
        }

        public static final int test_2_2__int_div_int_int = 1;
        public static final int test_128_64__int_div_int_int = 2;

        public static int int_div_int_int(int a, int b)
        {
            return a / b;
        }

        public static final int test_2_2__int_mul_int_int = 4;
        public static final int test_128_64__int_mul_int_int = 128 * 64;

        public static int int_mul_int_int(int a, int b)
        {
            return a * b;
        }

        public static final int test_2_2__int_mod_int_int = 0;
        public static final int test_128_63__int_mod_int_int = 128 % 63;

        public static int int_mod_int_int(int a, int b)
        {
            return a % b;
        }

        public static final float test_2__float_neg_float = -2;

        public static float float_neg_float(float a)
        {
            return -a;
        }

        public static final float test_2__float_add_float0 = 4;
        public static final float test_128__float_add_float0 = 130;

        public static final float test_2__float_add_float1 = 2 + 255;
        public static final float test_128__float_add_float1 = 128 + 255;

        public static final float test_128__float_add_float2 = 128 + 32768;

        public static float float_add_float0(float a)
        {
            return a + 2;
        }

        public static float float_add_float1(float a)
        {
            return a + 255;
        }

        public static float float_add_float2(float a)
        {
            return a + 32768;
        }

        public static final float test_2__float_sub_float0 = 0;
        public static final float test_128__float_sub_float0 = 126;

        public static final float test_2__float_sub_float1 = 2 - 255;
        public static final float test_128__float_sub_float1 = 128 - 255;

        public static final float test_128__float_sub_float2 = 128 - 32768;

        public static float float_sub_float0(float a)
        {
            return a - 2;
        }

        public static float float_sub_float1(float a)
        {
            return a - 255;
        }

        public static float float_sub_float2(float a)
        {
            return a - 32768;
        }

        public static final float test_2_2__float_sub_float_float = 0;
        public static final float test_6_2__float_sub_float_float = 4;
        public static final float test_128_40__float_sub_float_float = 88;

        public static float float_sub_float_float(float a, float b)
        {
            return a - b;
        }

        public static final float test_2_2__float_div_float_float = 1;
        public static final float test_128_64__float_div_float_float = 2;

        public static float float_div_float_float(float a, float b)
        {
            return a / b;
        }

        public static final float test_2_2__float_mul_float_float = 4;
        public static final float test_128_64__float_mul_float_float = 128 * 64;

        public static float float_mul_float_float(float a, float b)
        {
            return a * b;
        }

    /*
    public static final float test_2_2__float_mod_float_float = 0;
    public static final float test_128_63__float_mod_float_float = 128 % 63;

    public static float float_mod_float_float(float a, float b)
    {
	return a % b;
    }
    */

        public static final double test_2__double_neg_double = -2;

        public static double double_neg_double(double a)
        {
            return -a;
        }

        public static final double test_2__double_add_double0 = 4;
        public static final double test_128__double_add_double0 = 130;

        public static final double test_2__double_add_double1 = 2 + 255;
        public static final double test_128__double_add_double1 = 128 + 255;

        public static final double test_128__double_add_double2 = 128 + 32768;

        public static double double_add_double0(double a)
        {
            return a + 2;
        }

        public static double double_add_double1(double a)
        {
            return a + 255;
        }

        public static double double_add_double2(double a)
        {
            return a + 32768;
        }

        public static final double test_2__double_sub_double0 = 0;
        public static final double test_128__double_sub_double0 = 126;

        public static final double test_2__double_sub_double1 = 2 - 255;
        public static final double test_128__double_sub_double1 = 128 - 255;

        public static final double test_128__double_sub_double2 = 128 - 32768;

        public static double double_sub_double0(double a)
        {
            return a - 2;
        }

        public static double double_sub_double1(double a)
        {
            return a - 255;
        }

        public static double double_sub_double2(double a)
        {
            return a - 32768;
        }

        public static final double test_2_2__double_sub_double_double = 0;
        public static final double test_6_2__double_sub_double_double = 4;
        public static final double test_128_40__double_sub_double_double = 88;

        public static double double_sub_double_double(double a, double b)
        {
            return a - b;
        }

        public static final double test_2_2__double_div_double_double = 1;
        public static final double test_128_64__double_div_double_double = 2;

        public static double double_div_double_double(double a, double b)
        {
            return a / b;
        }

        public static final double test_2_2__double_mul_double_double = 4;
        public static final double test_128_64__double_mul_double_double = 128 * 64;

        public static double double_mul_double_double(double a, double b)
        {
            return a * b;
        }

    /*
    public static final double test_2_2__double_mod_double_double = 0;
    public static final double test_128_63__double_mod_double_double = 128 % 63;

    public static double double_mod_double_double(double a, double b)
    {
	return a % b;
    }
    */

        private ParameterizedMathMethods()
        {
        }
    }

    static class ParameterizedLogicalMethods
    {
        /*
         * These boolean operations come out as branches in bytecode, so they
         * should probably be moved to ControlFlow, but it would be nice to
         * optimize them away.
         */

        public static final boolean test_true__bool_not_bool = false;
        public static final boolean test_false__bool_not_bool = true;

        public static boolean bool_not_bool(boolean a)
        {
            return !a;
        }

        public static final boolean test_true_true__bool_and_bool_bool = true;
        public static final boolean test_true_false__bool_and_bool_bool = false;
        public static final boolean test_false_false__bool_and_bool_bool = false;
        public static final boolean test_false_true__bool_and_bool_bool = false;

        public static boolean bool_and_bool_bool(boolean a, boolean b)
        {
            return a && b;
        }

        public static final boolean test_true_true__bool_or_bool_bool = true;
        public static final boolean test_true_false__bool_or_bool_bool = true;
        public static final boolean test_false_false__bool_or_bool_bool = false;
        public static final boolean test_false_true__bool_or_bool_bool = true;

        public static boolean bool_or_bool_bool(boolean a, boolean b)
        {
            return a || b;
        }

        public static final boolean test_1__bool_lt_int = true;
        public static final boolean test_2__bool_lt_int = false;

        public static boolean bool_lt_int(int a)
        {
            return a < 2;
        }

        public static final boolean test_1_2__bool_lt_int_int = true;
        public static final boolean test_2_1__bool_lt_int_int = false;

        public static boolean bool_lt_int_int(int a, int b)
        {
            return a < b;
        }

        public static final boolean test_0__bool_le_int = true;
        public static final boolean test_2__bool_le_int = false;
        public static final boolean test_1__bool_le_int = true;

        public static boolean bool_le_int(int a)
        {
            return a <= 1;
        }

        public static final boolean test_1_2__bool_le_int_int = true;
        public static final boolean test_2_1__bool_le_int_int = false;
        public static final boolean test_1_1__bool_le_int_int = true;

        public static boolean bool_le_int_int(int a, int b)
        {
            return a <= b;
        }

        public static final boolean test_2__bool_gt_int = true;
        public static final boolean test_1__bool_gt_int = false;

        public static boolean bool_gt_int(int a)
        {
            return a > 1;
        }

        public static final boolean test_2_1__bool_gt_int_int = true;
        public static final boolean test_1_2__bool_gt_int_int = false;

        public static boolean bool_gt_int_int(int a, int b)
        {
            return a > b;
        }

        public static final boolean test_2__bool_ge_int = true;
        public static final boolean test_1__bool_ge_int = false;
        public static final boolean test_3__bool_ge_int = true;

        public static boolean bool_ge_int(int a)
        {
            return a >= 2;
        }

        public static final boolean test_2_1__bool_ge_int_int = true;
        public static final boolean test_1_2__bool_ge_int_int = false;
        public static final boolean test_1_1__bool_ge_int_int = true;

        public static boolean bool_ge_int_int(int a, int b)
        {
            return a >= b;
        }

        public static final boolean test_1__bool_eq_int = false;
        public static final boolean test_2__bool_eq_int = true;

        public static boolean bool_eq_int(int a)
        {
            return a == 2;
        }

        public static final boolean test_2_1__bool_eq_int_int = false;
        public static final boolean test_2_2__bool_eq_int_int = true;

        public static boolean bool_eq_int_int(int a, int b)
        {
            return a == b;
        }

        public static final boolean test_1__bool_neq_int = true;
        public static final boolean test_2__bool_neq_int = false;

        public static boolean bool_neq_int(int a)
        {
            return a != 2;
        }

        public static final boolean test_2_1__bool_neq_int_int = true;
        public static final boolean test_2_2__bool_neq_int_int = false;

        public static boolean bool_neq_int_int(int a, int b)
        {
            return a != b;
        }

        public static final int test_2__int_amp_int = 3;
        public static final int test_3__int_amp_int = 4;
        public static final int test_4__int_amp_int = 5;

        public static int int_amp_int(int a)
        {
            int retval;

            if( (a == 2) & (a++ == 4) )
            {
                retval = 1;
            }
            else
            {
                retval = a;
            }
            return retval;
        }

        public static final int test_2__int_bar_int = 3;
        public static final int test_3__int_bar_int = 4;
        public static final int test_4__int_bar_int = -1;

        public static int int_bar_int(int a)
        {
            int retval;

            if( (a == 2) | (a++ == 3) )
            {
                retval = a;
            }
            else
            {
                retval = -1;
            }
            return retval;
        }

        public static final int test_2__int_carat_int = -1;
        public static final int test_3__int_carat_int = -1;
        public static final int test_4__int_carat_int = 5;

        public static int int_carat_int(int a)
        {
            int retval;

            if( (a == 2) ^ ((a++ & 0x1) == 0) )
            {
                retval = a;
            }
            else
            {
                retval = -1;
            }
            return retval;
        }

        private ParameterizedLogicalMethods()
        {
        }
    }

     /**
     * Tests for the bitwise operators, ~, &, |, ^, <<, >>, and >>>.
     */
    static class ParameterizedBitwiseMethods
    {
        public static final int test_0xFFFF0000__int_not_int = 0x0000FFFF;
        public static final int test_0xF0F0F0F0__int_not_int = 0x0F0F0F0F;

        public static int int_not_int(int a)
        {
            return ~a;
        }

        public static final int test_0xDEADBEEF__int_and_int =
                0xDEAD0000;
        public static final int test_0xBEEFD0DE__int_and_int =
                0xBEEF0000;

        public static int int_and_int(int a)
        {
            return a & 0xFFFF0000;
        }

        public static final int test_0xDEADBEEF_0xFFFFFFFF__int_and_int_int =
                0xDEADBEEF;
        public static final int test_0xDEADBEEF_0x00000000__int_and_int_int =
                0x00000000;

        public static int int_and_int_int(int a, int b)
        {
            return a & b;
        }

        public static final int test_0xDEADBEEF__int_or_int = 0xFEFDFEFF;
        public static final int test_0x00000000__int_or_int = 0xF0F0F0F0;

        public static int int_or_int(int a)
        {
            return a | 0xF0F0F0F0;
        }

        public static final int test_0xDEADBEEF_0xFFFFFFFF__int_or_int_int =
                0xFFFFFFFF;
        public static final int test_0xDEADBEEF_0x00000000__int_or_int_int =
                0xDEADBEEF;

        public static int int_or_int_int(int a, int b)
        {
            return a | b;
        }

        public static final int test_0xDEADBEEF__int_xor_int =
                0xDEADBEEF ^ 0xFFFFFFFF;
        public static final int test_0x00000000__int_xor_int =
                0x00000000 ^ 0xFFFFFFFF;

        public static int int_xor_int(int a)
        {
            return a ^ 0xFFFFFFFF;
        }

        public static final int test_0xDEADBEEF_0xFFFFFFFF__int_xor_int_int =
                0xDEADBEEF ^ 0xFFFFFFFF;
        public static final int test_0xDEADBEEF_0x00000000__int_xor_int_int =
                0xDEADBEEF;

        public static int int_xor_int_int(int a, int b)
        {
            return a ^ b;
        }

        public static final int test_2__int_lshl_int = 2 << 2;
        public static final int test_128__int_lshl_int = 128 << 2;
        public static final int test_0x80000000__int_lshl_int =
                0x80000000 << 2;

        public static int int_lshl_int(int a)
        {
            return a << 2;
        }

        public static final int test_2_2__int_lshl_int_int = 2 << 2;
        public static final int test_128_28__int_lshl_int_int = 128 << 28;
        public static final int test_0x80000000_1__int_lshl_int_int =
                0x80000000 << 1;

        public static int int_lshl_int_int(int a, int b)
        {
            return a << b;
        }

        public static final int test_2__int_lshr_int = 2 >> 2;
        public static final int test_128__int_lshr_int = 128 >> 2;
        public static final int test_0x80000000__int_lshr_int =
                0x80000000 >> 2;

        public static int int_lshr_int(int a)
        {
            return a >> 2;
        }

        public static final int test_2_2__int_lshr_int_int = 2 >> 2;
        public static final int test_128_3__int_lshr_int_int = 128 >> 3;
        public static final int test_0x80000000_1__int_lshr_int_int =
                0x80000000 >> 1;
        public static final int test_0x40000000_1__int_lshr_int_int =
                0x40000000 >> 1;

        public static int int_lshr_int_int(int a, int b)
        {
            return a >> b;
        }

        public static final int test_2__int_ulshr_int = 2 >>> 2;
        public static final int test_128__int_ulshr_int = 128 >>> 2;
        public static final int test_0x80000000__int_ulshr_int =
                0x80000000 >>> 2;
        public static final int test_0x40000000__int_ulshr_int =
                0x40000000 >>> 2;

        public static int int_ulshr_int(int a)
        {
            return a >>> 2;
        }

        public static final int test_2_2__int_ulshr_int_int = 2 >>> 2;
        public static final int test_128_28__int_ulshr_int_int = 128 >>> 28;
        public static final int test_0x80000000_1__int_ulshr_int_int =
                0x80000000 >>> 1;
        public static final int test_0x40000000_1__int_ulshr_int_int =
                0x40000000 >>> 1;

        public static int int_ulshr_int_int(int a, int b)
        {
            return a >>> b;
        }

        private ParameterizedBitwiseMethods()
        {
        }
    }

    static class NativeMethodCall
    {
        public static int int_nmethod_void()
        {
            int a1[] = new int[] { 1, 2, 3, 4 };
            int a2[] = new int[a1.length];

            System.arraycopy(a1, 0, a2, 0, a1.length);

            return a2[2];
        }

        public static final int test__int_nmethod_void = 3;
    }

    static final class MethodOptimizations
    {
        private int value = 0;

        int getValue()
        {
            return this.value;
        }

        public static final boolean test__extraFakeCalls = true;

        public static boolean extraFakeCalls()
        {
            MethodOptimizations mo = new MethodOptimizations();

            mo.getValue();
            mo.getValue();
            return true;
        }
    }

    static class Exceptions
    {

        public static final boolean test__boolean_exception = true;

        public static boolean boolean_exception()
        {
            boolean retval = false;

            try
            {
                throw new Throwable();
            }
            catch(Throwable th)
            {
                retval = true;
            }
            return retval;
        }


        public static final int test_0__int_exception = 1;

        public static int int_exception(int a)
        {
            try
            {
                throw new Throwable();
            }
            catch(Throwable th)
            {
                a++;
            }
            return a;
        }


        public static final int test_0__int_exception0 = 1;

        public static int int_exception0(int a)
        {
            try
            {
                a++;
                throw new Throwable();
            }
            catch(Throwable th)
            {}

            return a;
        }


        public static final int test_0__int_exception1 = 2;

        public static int int_exception1(int a)
        {
            a++;

            try
            {
                a++;
                throw new Throwable ();
            }
            catch (Throwable th)
            {}

            return a;
        }


        public static final int test_0__int_exception2 = 2;

        public static int int_exception2(int a)
        {
            a++;
            try
            {
                throw new Throwable ();
            }
            catch (Throwable th)
            {
                a++;
            }

            return a;
        }


        public static final int test_0__int_exception3 = 2;

        public static int int_exception3(int a)
        {
            try
            {
                a++;
                throw new Throwable ();
            }
            catch (Throwable th)
            {
                a++;
            }
            return a;
        }

//        public static Throwable int_exception3(Throwable th, Integer th2[])
//        {
//            return th;
//        }

        private Exceptions()
        {
        }
    }

    static class ControlFlowMethods
    {

        public static final int test_1__int_if_int = 2;
        public static final int test_4__int_if_int = 144;
        public static final int test_5__int_if_int = 0;

        public static int int_if_int(int a)
        {
            int retval;

            if( a == 1 )
            {
                retval = 2;
            }
            else if( a == 4 )
            {
                retval = 144;
            }
            else
            {
                retval = 0;
            }
            return( retval );
        }

        public static final int test__int_for = 10;

        public static int int_for()
        {
            int lpc;

            for( lpc = 0; lpc < 10; lpc++ )
            {
            }
            return( lpc );
        }

        public static final int test__int_while = 10;

        public static int int_while()
        {
            int lpc = 0;

            while( lpc < 10 )
            {
                lpc += 1;
            }
            return( lpc );
        }

        public static final int test__int_do_while = 10;

        public static int int_do_while()
        {
            int lpc = 0;

            do {
                lpc += 1;
            } while( lpc < 10 );
            return( lpc );
        }

        /*
         * A switch that reduces to a TABLESWITCH bytecode.
         */

        public static final int test_0__int_tableswitch_int = -1;
        public static final int test_1__int_tableswitch_int = 2;
        public static final int test_2__int_tableswitch_int = 144;
        public static final int test_3__int_tableswitch_int = 169;
        public static final int test_4__int_tableswitch_int = 0xdeadbeef;
        public static final int test_5__int_tableswitch_int = -1;

        public static int int_tableswitch_int(int a)
        {
            int retval;

            switch( a )
            {
                case 1:
                    retval = 2;
                    break;
                case 2:
                    retval = 144;
                    break;
                case 3:
                    retval = 169;
                    break;
                case 4:
                    retval = 0xdeadbeef;
                    break;
                default:
                    retval = -1;
                    break;
            }
            return( retval );
        }

        /*
         * A switch that reduces to a LOOKUPSWITCH bytecode.
         */

        public static final int test_0__int_lookupswitch_int = -1;
        public static final int test_0xdeadbeef__int_lookupswitch_int = 1;
        public static final int test_0xd0decade__int_lookupswitch_int = 2;
        public static final int test_0xbeefdead__int_lookupswitch_int = 3;
        public static final int test_0xffffffff__int_lookupswitch_int = -1;

        public static int int_lookupswitch_int(int a)
        {
            int retval;

            switch( a )
            {
                case 0xdeadbeef:
                    retval = 1;
                    break;
                case 0xd0decade:
                    retval = 2;
                    break;
                case 0xbeefdead:
                    retval = 3;
                    break;
                default:
                    retval = -1;
                    break;
            }
            return retval;
        }

        private ControlFlowMethods()
        {
        }
    }

    static class ConstMethods
    {
        public static final int test__int_method_void0 = 0x00000004;
        public static final int test__int_method_void1 = 0x0000ffff;
        public static final int test__int_method_void2 = 0x00007fff;
        public static final int test__int_method_void3 = 0xfffffff0;
        public static final int test__int_method_void4 = 0xcadef0f0;
        public static final int test__int_method_void5 = 0xcadee0f0;

        /**
         * @return a non-constpool immediate.  Should resolve to an ICONST_4
         * bytecode.
         */
        public static int int_method_void0()
        {
            return 0x00000004;
        }

        /**
         * @return an unsigned, at least, 16 bit constpool immediate.  This value
         * should not be sign extended when loaded as an immediate.
         */
        public static int int_method_void1()
        {
            return 0x0000ffff;
        }

        /**
         * @return a positive, at least, 16 bit constpool immediate.
         */
        public static int int_method_void2()
        {
            return 0x00007fff;
        }

        /**
         * @return a negative, at least, 16 bit constpool immediate.
         */
        public static int int_method_void3()
        {
            return 0xfffffff0;
        }

        /**
         * @return a signed 32 bit immediate.  The lower 16 bits is a negative
         * value so the sign extension should not damage the higher bits.
         */
        public static int int_method_void4()
        {
            return 0xcadef0f0;
        }

        /**
         * @return a signed 32 bit immediate.  Unlike the previous function, the
         * lower 16 bits are positive, so the lack of sign extension should not
         * damage the higher bits.
         */
        public static int int_method_void5()
        {
            return 0xcadee0f0;
        }

        public static final float test__float_method_void0 = 0.0F;
        public static final float test__float_method_void1 = 1.0F;
        public static final float test__float_method_void2 = 12345.6789F;

        public static float float_method_void0()
        {
            return 0.0F;
        }

        public static float float_method_void1()
        {
            return 1.0F;
        }

        public static float float_method_void2()
        {
            return 12345.6789F;
        }

        public static final double test__double_method_void0 = 0.0;
        public static final double test__double_method_void1 = 1.0;
        public static final double test__double_method_void2 = 12345.6789;

        public static double double_method_void0()
        {
            return 0.0;
        }

        public static double double_method_void1()
        {
            return 1.0;
        }

        public static double double_method_void2()
        {
            return 12345.6789;
        }

        public static final boolean test__boolean_method_void0 = false;
        public static final boolean test__boolean_method_void1 = true;

        public static boolean boolean_method_void0()
        {
            return false;
        }

        public static boolean boolean_method_void1()
        {
            return true;
        }

        public static final byte test__byte_method_void0 = 0;
        public static final byte test__byte_method_void1 = 1;
        public static final byte test__byte_method_void2 = 32;
        public static final byte test__byte_method_void3 = 127;
        public static final byte test__byte_method_void4 = -128;

        public static byte byte_method_void0()
        {
            return 0;
        }

        public static byte byte_method_void1()
        {
            return 1;
        }

        public static byte byte_method_void2()
        {
            return 32;
        }

        public static byte byte_method_void3()
        {
            return 127;
        }

        public static byte byte_method_void4()
        {
            return -128;
        }

        public static final char test__char_method_void0 = 0;
        public static final char test__char_method_void1 = 'a';
        public static final char test__char_method_void2 = 'A';
        public static final char test__char_method_void3 = 'z';
        public static final char test__char_method_void4 = 'Z';

        public static char char_method_void0()
        {
            return 0;
        }

        public static char char_method_void1()
        {
            return 'a';
        }

        public static char char_method_void2()
        {
            return 'A';
        }

        public static char char_method_void3()
        {
            return 'z';
        }

        public static char char_method_void4()
        {
            return 'Z';
        }

        public static final long test__long_method_void0 = 4L;
        public static final long test__long_method_void1 = 0x00000000FFFFFFFFL;
        public static final long test__long_method_void2 = 0xFFFFFFFF00000000L;
        public static final long test__long_method_void3 = 0x0000000FF0000000L;
        public static final long test__long_method_void4 = 0x0000000FFFFFFFF0L;
        public static final long test__long_method_void5 = 0x00000000EFFFFFFFL;
        public static final long test__long_method_void6 = 0xdeadbeefd0decadeL;

        public static long long_method_void0()
        {
            return 4L;
        }

        public static long long_method_void1()
        {
            return 0x00000000FFFFFFFFL;
        }

        public static long long_method_void2()
        {
            return 0xFFFFFFFF00000000L;
        }

        public static long long_method_void3()
        {
            return 0x0000000FF0000000L;
        }

        public static long long_method_void4()
        {
            return 0x0000000FFFFFFFF0L;
        }

        public static long long_method_void5()
        {
            return 0x00000000EFFFFFFFL;
        }

        public static long long_method_void6()
        {
            return 0xdeadbeefd0decadeL;
        }

        private ConstMethods()
        {
        }
    }

    static class ConstMathMethods
    {
        public static final int test__int_add_void0 = 2;
        public static final int test__int_add_void1 = 0xDEADFEEB;

        public static int int_add_void0()
        {
            return 1 + 1;
        }

        public static int int_add_void1()
        {
            return 0xdead0000 + 0xfeeb;
        }

        public static final int test__int_sub_void0 = 0;
        public static final int test__int_sub_void1 = 0xDEAD0000;

        public static int int_sub_void0()
        {
            return 1 - 1;
        }

        public static int int_sub_void1()
        {
            return 0xdeadFFFF - 0x0000FFFF;
        }

        public static final int test__int_mul_void0 = 4;
        public static final int test__int_mul_void1 = 0;

        public static int int_mul_void0()
        {
            return 2 * 2;
        }

        public static int int_mul_void1()
        {
            return 2 * 0;
        }

        public static final int test__int_div_void0 = 2;
        public static final int test__int_div_void1 = 4;

        public static int int_div_void0()
        {
            return 4 / 2;
        }

        public static int int_div_void1()
        {
            return 4 / 1;
        }

        public static final boolean test__boolean_lt_void0 = false;
        public static final boolean test__boolean_lt_void1 = true;

        public static boolean boolean_lt_void0()
        {
            return 4 < 1;
        }

        public static boolean boolean_lt_void1()
        {
            return 1 < 4;
        }

        public static final boolean test__boolean_le_void0 = false;
        public static final boolean test__boolean_le_void1 = true;
        public static final boolean test__boolean_le_void2 = true;

        public static boolean boolean_le_void0()
        {
            return 4 <= 1;
        }

        public static boolean boolean_le_void1()
        {
            return 1 <= 4;
        }

        public static boolean boolean_le_void2()
        {
            return 1 <= 1;
        }

        public static final boolean test__boolean_gt_void0 = false;
        public static final boolean test__boolean_gt_void1 = true;

        public static boolean boolean_gt_void0()
        {
            return 1 > 4;
        }

        public static boolean boolean_gt_void1()
        {
            return 4 > 1;
        }

        public static final boolean test__boolean_ge_void0 = false;
        public static final boolean test__boolean_ge_void1 = true;
        public static final boolean test__boolean_ge_void2 = true;

        public static boolean boolean_ge_void0()
        {
            return 1 >= 4;
        }

        public static boolean boolean_ge_void1()
        {
            return 4 >= 1;
        }

        public static boolean boolean_ge_void2()
        {
            return 1 >= 1;
        }

        public static final boolean test__boolean_eq_void0 = false;
        public static final boolean test__boolean_eq_void1 = true;

        public static boolean boolean_eq_void0()
        {
            return 1 == 4;
        }

        public static boolean boolean_eq_void1()
        {
            return 1 == 1;
        }

        public static final boolean test__boolean_neq_void0 = false;
        public static final boolean test__boolean_neq_void1 = true;

        public static boolean boolean_neq_void0()
        {
            return 1 != 1;
        }

        public static boolean boolean_neq_void1()
        {
            return 1 != 4;
        }

        public static final boolean test__boolean_not_void0 = false;
        public static final boolean test__boolean_not_void1 = true;

        public static boolean boolean_not_void0()
        {
            return !true;
        }

        public static boolean boolean_not_void1()
        {
            return !false;
        }

        private ConstMathMethods()
        {
        }
    }
}
