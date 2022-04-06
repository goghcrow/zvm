package zvm;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.reflect.Modifier.*;

/**
 * @author chuxiaofeng
 */
public class Test {
    public static boolean diff1(VM vm, Class<?> cls) {
        ZClass z_class = vm.load_class(cls.getName(), true);

        int public_static_final = PUBLIC | STATIC | FINAL;

        System.out.println(cls.getName());

        Map<String, Method> test_methods = Arrays.stream(cls.getDeclaredMethods())
                .filter(it -> isPublic(it.getModifiers()) && isStatic(it.getModifiers()))
                .collect(Collectors.toMap(Method::getName, it -> it));

        for (Field field : cls.getDeclaredFields()) {
            if ((field.getModifiers() & public_static_final) == public_static_final) {
                if (field.getName().startsWith("test_")) {
                    String[] s = field.getName().split("__");
                    String s1 = s[0].substring("test".length()).replaceFirst("_", "");
                    String[] str_args = s1.equals("") ? new String[0] : s1.split("_");
                    String method_name = s[1];
                    Method test_method = test_methods.get(method_name);
                    if (test_method.getParameterCount() != str_args.length) {
                        throw new AssertionError();
                    }
                    Object[] args = new Object[str_args.length];
                    Class<?>[] param_types = test_method.getParameterTypes();
                    for (int i = 0; i < param_types.length; i++) {
                        Class<?> t = param_types[i];
                        String str_arg = str_args[i];
                        if (t == byte.class) {
                            if (str_arg.startsWith("0x")) {
                                args[i] = new BigInteger(str_arg.substring(2), 16).byteValue();
                            } else {
                                args[i] = Byte.parseByte(str_arg);
                            }
                        } else if (t == short.class) {
                            if (str_arg.startsWith("0x")) {
                                args[i] = new BigInteger(str_arg.substring(2), 16).shortValue();
                            } else {
                                args[i] = Short.parseShort(str_arg);
                            }
                        } else if (t == char.class) {
                            assert !str_arg.startsWith("0x");
                            args[i] = Character.forDigit(Integer.parseInt(str_arg), 10);
                        } else if (t == int.class) {
                            if (str_arg.startsWith("0x")) {
                                args[i] = new BigInteger(str_arg.substring(2), 16).intValue();
                            } else {
                                args[i] = Integer.parseInt(str_arg);
                            }
                        } else if (t == long.class) {
                            if (str_arg.startsWith("0x")) {
                                args[i] = new BigInteger(str_arg.substring(2), 16).longValue();
                            } else {
                                args[i] = Long.parseLong(str_arg);
                            }
                        } else if (t == float.class) {
                            args[i] = Float.parseFloat(str_arg.replace('d', '.'));
                        } else if (t == double.class) {
                            args[i] = Double.parseDouble(str_arg.replace('d', '.'));
                        } else if (t == boolean.class) {
                            args[i] = Boolean.parseBoolean(str_arg);
                        } else {
                            throw new AssertionError();
                        }
                    }




                    System.out.println("----------------------------");
                    System.out.println("diff > " + test_method);
                    Object host_result = null, zvm_result = null;
                    Exception e1 = null;
                    ZThrowable e2 = null;
                    try {
                        test_method.setAccessible(true);
                        host_result = test_method.invoke(null, args);
                    } catch (Exception e) {
                        e1 = e;
                    }
                    try {
                        // !!! 参数全是基本类型...
                        zvm_result = z_class.static_method(test_method.getName(), Descriptor.method(test_method))
                                .invoke(null, args);
                    } catch (ZThrowable e) {
                        e2 = e;
                    }
                    if (e1 == null && e2 == null) {
                        if (host_result instanceof Object[]) {
                            System.err.println("host > " + Arrays.toString(((Object[]) host_result)));
                        } else {
                            System.err.println("host > " + host_result);
                        }
                        System.out.println("zvm  > " + zvm_result);
                        if (!vs_eq(vm, host_result, zvm_result)) {
                            return false;
                        }
                    } else if (e1 != null && e2 != null) {
                        // test... stacktrace 估计有问题...
                        if (!vs_eq(vm, e1, e2.z_throwable)) {
                            return false;
                        }
                    } else {
                        System.err.println("host > " + host_result);
                        System.err.println("zvm > " + zvm_result);
                        System.err.println("host > " + e1);
                        System.err.println("zvm > " + e2);
                        return false;
                    }
                }
            }
        }

        return true;
    }


    public static boolean diff(VM vm, Class<?> cls) {
        ZClass z_class = vm.load_class(cls.getName(), true);
        for (Method method : cls.getMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
                System.out.println("----------------------------");
                System.out.println("diff > " + method);
                assert method.getParameterCount() == 0;
                Object host_result = null, zvm_result = null;
                Exception e1 = null;
                ZThrowable e2 = null;
                try {
                    method.setAccessible(true);
                    host_result = method.invoke(null);
                } catch (Exception e) {
                    e1 = e;
                }
                try {
                    zvm_result = z_class.static_method(method.getName(), Descriptor.method(method)).invoke(null, new Object[0]);
                } catch (ZThrowable e) {
                    e2 = e;
                }
                if (e1 == null && e2 == null) {
                    if (host_result instanceof Object[]) {
                        System.err.println("host > " + Arrays.toString(((Object[]) host_result)));
                    } else {
                        System.err.println("host > " + host_result);
                    }
                    System.out.println("zvm  > " + zvm_result);
                    if (!vs_eq(vm, host_result, zvm_result)) {
                        return false;
                    }
                } else if (e1 != null && e2 != null) {
                    // test... stacktrace 估计有问题...
                    if (!vs_eq(vm, e1, e2.z_throwable)) {
                        return false;
                    }
                } else {
                    System.err.println("host > " + host_result);
                    System.err.println("zvm > " + zvm_result);
                    System.err.println("host > " + e1);
                    System.err.println("zvm > " + e2);
                    return false;
                }
            }
        }
        return true;
    }

    private final static Set<Field> white_list = new HashSet<>();
    static {
        try {
            white_list.add(Boolean.class.getDeclaredField("TRUE"));
            white_list.add(Boolean.class.getDeclaredField("FALSE"));
            white_list.add(String.class.getDeclaredField("hash"));
            white_list.add(Class.class.getDeclaredField("name"));
            white_list.add(Class.class.getDeclaredField("allPermDomain"));
            white_list.add(sun.reflect.ReflectionFactory.class.getDeclaredField("initted"));
        } catch (NoSuchFieldException e) {
            Natives.sneakyThrows(e);
        }
    }
    static boolean vs_eq(VM vm, Object host_result, Object zvm_result) {
        if (host_result != null && zvm_result == null) {
            return false;
        }
        if (host_result == null && zvm_result != null) {
            return false;
        }
        //noinspection ConstantConditions
        if (host_result == null && zvm_result == null) {
            return true;
        }

        if (host_result instanceof Class && zvm_result instanceof ZClass) {
            // todo: 先不比较 class 对象了
            return true;
        }

        if (zvm_result instanceof ZArray) {
            if (!host_result.getClass().isArray()) {
                return false;
            }
            ZArray z_array = ((ZArray) zvm_result);
            if (z_array.length() != Array.getLength(host_result)) {
                return false;
            }
            for (int i = 0; i < z_array.length(); i++) {
                if (!vs_eq(vm, Array.get(host_result, i), z_array.index(i))) {
                    return false;
                }
            }
            return true;
        } else if (zvm_result instanceof ZObject) {
            ZObject z_object = ((ZObject) zvm_result);
            ZClass z_class = z_object.z_class();
            if (z_class.name().equals(host_result.getClass().getName())) {
                return false;
            }

            // 特殊处理下 bool
            if (host_result instanceof Boolean) {
                return vs_eq(vm, host_result, z_class.field("value").get_value(z_object));
            }

            Class<?> h_class = host_result.getClass();
            while (h_class != null) {
                for (Field f : h_class.getDeclaredFields()) {
                    if (f.getName().equals("$assertionsDisabled")) {
                        continue;
                    }
                    if (white_list.contains(f)) continue;
                    f.setAccessible(true);

                    try {
                        Object zvm_field_value;
                        if (Modifier.isStatic(f.getModifiers())) {
                            // 这里 static 必须用 special 到固定的 class
                            // 不能用 ((ZObject) zvm_result).z_class()
                            // zvm_field_value = ((ZObject) zvm_result).z_class().get_field(f.getName());
                            zvm_field_value = vm.load_class(f.getDeclaringClass().getName()).field(f.getName()).get_value(null);
                        } else {
                            zvm_field_value = z_class.field(f.getName()).get_value(z_object);
                        }
                        Object host_field_value = f.get(host_result);
                        if (!vs_eq(vm, host_field_value, zvm_field_value)) {
                            System.err.println("--------------------------------");
                            System.err.println("field: " + f);
                            if (host_field_value instanceof Object[]) {
                                System.err.println("host: " + Arrays.toString(((Object[]) host_field_value)));
                            } else {
                                System.err.println("host: " + host_field_value);
                            }
                            System.err.println("zvm: " + zvm_field_value);
                            return false;
                        }
                    } catch (IllegalAccessException e) {
                        Natives.sneakyThrows(e);
                    }
                }
                h_class = h_class.getSuperclass();
            }
            return true;
        } else {
            return to_number(vm, host_result).equals(to_number(vm, zvm_result));
        }
    }

    static Number to_number(VM vm, Object object) {
        if (object instanceof ZObject) {
            ZObject z_object = ((ZObject) object);
            ZClass z_class = z_object.z_class();
            if (vm.load_class("java/lang/Number", false).is_assignable_from(z_class)
                    || vm.java_lang_Boolean.is_assignable_from(z_class)
                    || vm.java_lang_Character.is_assignable_from(z_class)
            ) {
                return to_number(vm, z_class.field("value").get_value(z_object));
            }
        } else {
            // c == 'Z' || c == 'B' || c == 'S' || c == 'I'
            if (object instanceof Boolean) {
                return ((Boolean) object) ? 1 : 0;
            }
            if (object instanceof Byte) {
                return ((Byte) object).intValue();
            }
            if (object instanceof Short) {
                return ((Short) object).intValue();
            }
            if (object instanceof Character) {
                return ((int) (Character) object);
            }
            if (object instanceof Number) {
                return ((Number) object);
            }
        }
        throw new AssertionError();
    }
}
