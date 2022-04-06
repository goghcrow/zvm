package zvm.test;

import java.util.ArrayList;
import java.util.List;

public class Test_getName0 {

    public static List<String> test() {
        List<String> lst = new ArrayList<>();

        lst.add(boolean.class.getName());
        lst.add(byte.class.getName());
        lst.add(short.class.getName());
        lst.add(char.class.getName());
        lst.add(int.class.getName());
        lst.add(long.class.getName());
        lst.add(float.class.getName());
        lst.add(double.class.getName());

        lst.add(boolean[].class.getName());
        lst.add(byte[].class.getName());
        lst.add(short[].class.getName());
        lst.add(char[].class.getName());
        lst.add(int[].class.getName());
        lst.add(long[].class.getName());
        lst.add(float[].class.getName());
        lst.add(double[].class.getName());

        lst.add(boolean[][].class.getName());
        lst.add(byte[][].class.getName());
        lst.add(short[][].class.getName());
        lst.add(char[][].class.getName());
        lst.add(int[][].class.getName());
        lst.add(long[][].class.getName());
        lst.add(float[][].class.getName());
        lst.add(double[][].class.getName());

        lst.add(Boolean.class.getName());
        lst.add(Byte.class.getName());
        lst.add(Short.class.getName());
        lst.add(Character.class.getName());
        lst.add(Integer.class.getName());
        lst.add(Long.class.getName());
        lst.add(Float.class.getName());
        lst.add(Double.class.getName());

        lst.add(Boolean[].class.getName());
        lst.add(Byte[].class.getName());
        lst.add(Short[].class.getName());
        lst.add(Character[].class.getName());
        lst.add(Integer[].class.getName());
        lst.add(Long[].class.getName());
        lst.add(Float[].class.getName());
        lst.add(Double[].class.getName());

        lst.add(Boolean[][].class.getName());
        lst.add(Byte[][].class.getName());
        lst.add(Short[][].class.getName());
        lst.add(Character[][].class.getName());
        lst.add(Integer[][].class.getName());
        lst.add(Long[][].class.getName());
        lst.add(Float[][].class.getName());
        lst.add(Double[][].class.getName());

        lst.add(InnerClass.class.getName());
        lst.add(InnerClass.InnerInnerClass.class.getName());

        lst.add(InnerClass[].class.getName());
        lst.add(InnerClass.InnerInnerClass[].class.getName());

        lst.add(InnerClass[].class.getName());
        lst.add(InnerClass.InnerInnerClass[][].class.getName());

        return lst;
    }

    static class InnerClass {
        static class InnerInnerClass { }
    }
}
