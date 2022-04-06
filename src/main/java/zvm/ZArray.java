package zvm;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author chuxiaofeng
 *
 * 为啥需要包装 ZArray
 * 💥💥💥 😈😈😈 array 如果用宿主的话, 没办法获取 runtime array-value 的声明类型...
 * 1. 数组协变需要运行时类型检查
 *  Object[] arr = new String[]
 *  arr[0] = 1 // ArrayStoreException
 * 2. instanceof 也需要
 *
 *
 * ❌ 编译不通过 new Object[0] instanceof FooClass
 * ❌ 编译不通过 new int[0] instanceof BarInterface
 * ✅ 编译通过 new Object[0] instanceof Cloneable
 * ✅ 编译通过 new int[0] instanceof Serializable
 * ✅ 编译通过 new byte[0] instanceof Object
 * 本来 instanceof 需要运行时获取对象检查, 现在不合法的根本编译不通过, 直接返回 true
 * 这种玩意还是要获取运行时类型的...
 * Object o1 = new Object[0];
 * Object o2 = new int[0];
 * assertFalse(o1 instanceof C1);
 * assertFalse(o2 instanceof I1);
 */
public final class ZArray extends ZObject {
    private final @NotNull Object array;

    ZArray(VM vm, @NotNull ZClass z_class, @NotNull Object array) {
        super(vm, z_class);
        assert z_class().is_array();
        assert array.getClass().isArray();
        // 检查 array 类型 ???
        this.array = array;
    }

    int length() {
        return Array.getLength(array);
    }

    <T> T index(int idx) {
        bound_check(idx);
        //noinspection unchecked
        return (T) Array.get(array, idx);
    }

    void index(int idx, Object val) {
        bound_check(idx);
        store_check(val);
        Array.set(array, idx, val);
    }

    private void bound_check(int idx) {
        if (idx < 0 || idx >= Array.getLength(array)) {
//            ZObject ex = z_class_.vm.load_class("java/lang/ArrayIndexOutOfBoundsException", true)
//                    .new_instance("(I)V", new Object[]{ idx });
            ZObject ex = z_class.vm.load_class("java/lang/ArrayIndexOutOfBoundsException", true)
                    .new_instance("()V", new Object[0]);
            throw new ZThrowable(ex);
        }
    }

    private void store_check(Object value) {
        boolean success;
        ZClass component_class = z_class.component_class();
        if (component_class.is_primitive()) {
            success = ZClass.type_of_primitive(value) == component_class.native_class();
        } else {
            success = value == null || component_class.is_instance(z_class.vm, false, value);
        }
        if (!success) {
            throw new ZThrowable(vm.load_class("java/lang/IllegalArgumentException", true)
                    .new_instance("(Ljava/lang/String;)V", new Object[] {
                            Natives.new_string(vm, "argument type mismatch")
                    }));
        }
    }

    boolean is_bool_array() { return array instanceof boolean[]; }
    boolean is_byte_array() { return array instanceof byte[]; }
    byte[] byte_array() { return (byte[]) array; }
    char[] char_array() { return (char[]) array; }
    // ZArray[] array_array() { return (ZArray[]) array; }
    ZObject[] z_object_array() { return (ZObject[]) array; }

    ZArray clone0() {
        Object copy;
        Class<?> component_type = array.getClass().getComponentType();
        if (component_type.isPrimitive()) {
            if (component_type == byte.class) {
                copy = Arrays.copyOf(((byte[]) array), ((byte[]) array).length);
            } else if (component_type == short.class) {
                copy = Arrays.copyOf(((short[]) array), ((short[]) array).length);
            } else if (component_type == int.class) {
                copy = Arrays.copyOf(((int[]) array), ((int[]) array).length);
            } else if (component_type == long.class) {
                copy = Arrays.copyOf(((long[]) array), ((long[]) array).length);
            } else if (component_type == char.class) {
                copy = Arrays.copyOf(((char[]) array), ((char[]) array).length);
            } else if (component_type == float.class) {
                copy = Arrays.copyOf(((float[]) array), ((float[]) array).length);
            } else if (component_type == double.class) {
                copy = Arrays.copyOf(((double[]) array), ((double[]) array).length);
            } else if (component_type == boolean.class) {
                copy = Arrays.copyOf(((boolean[]) array), ((boolean[]) array).length);
            } else {
                throw new AssertionError();
            }
        } else {
            // 这里不能 cast ZObject[], 因为有基础类型，会被装箱
            copy = Arrays.copyOf(((Object[]) array), ((Object[]) array).length);
        }

        return new ZArray(vm, z_class, copy);
    }

    static void copy(ZArray src, int src_pos, ZArray dest, int dest_pos, int length) {
        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(src.array, src_pos, dest.array, dest_pos, length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(array);
    }

    @Override
    public String toString() {
        String array_string;
        if (array instanceof boolean[]) {
            array_string = Arrays.toString(((boolean[]) array));
        } else if (array instanceof byte[]) {
            array_string = Arrays.toString(((byte[]) array));
        } else if (array instanceof short[]) {
            array_string = Arrays.toString(((short[]) array));
        } else if (array instanceof char[]) {
            array_string = Arrays.toString(((char[]) array));
        } else if (array instanceof int[]) {
            array_string = Arrays.toString(((int[]) array));
        } else if (array instanceof long[]) {
            array_string = Arrays.toString(((long[]) array));
        } else if (array instanceof float[]) {
            array_string = Arrays.toString(((float[]) array));
        } else if (array instanceof double[]) {
            array_string = Arrays.toString(((double[]) array));
        } else if (array instanceof Object[]) {
            array_string = Arrays.toString(((Object[]) array));
        } else {
            throw new AssertionError();
        }
        return z_class.toString() + " " + array_string;
    }
}
