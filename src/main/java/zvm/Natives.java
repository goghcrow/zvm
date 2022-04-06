package zvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Signal;
import zvm.ClassParser.ClassFile.Method;
import zvm.helper.Reflect;

import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static zvm.ClassParser.AccessFlags.*;
import static zvm.ClassParser.ClassFile;
import static zvm.ClassParser.ConstantPool.instance_init;

/**
 * 先写个简陋的JNI实现凑合能把代码跑起来,否则木有 native 啥都干不了
 * @author chuxiaofeng
 */
@SuppressWarnings("unused")
public class Natives {
    private static final sun.misc.Unsafe UNSAFE = Reflect.of(sun.misc.Unsafe.class).field("theUnsafe").get();

    private Natives() {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface NativeDescriptor {
        String name() default "";
        String descriptor() default "";
    }

    public static Invokable resolve(VM vm, ZMethod method) {
        String method_name = method.name();
        String descriptor = method.descriptor();
        String class_name = method.class_name();
        Map<String, Object/*Invokable*/> map = vm.natives.get(class_name);
        if (map == null) {
            register(vm, class_name);
            return resolve(vm, method);
        }
        Object/*Invokable*/ jni = map.get(method_name);
        if (jni == null) {
            throw new UnsupportedOperationException(class_name + "." + method_name);
        }
        if (jni instanceof Invokable) {
            return (Invokable) jni;
        } else {
            for (Invokable invokable : ((Invokable[]) jni)) {
                if (descriptor.equals(invokable.descriptor())) {
                    return invokable;
                }
            }
        }
        throw new AssertionError();
    }

    static void register(VM vm, String class_name) {
        Class<?> clazz;
        if (class_name.startsWith("zvm/Natives$")) {
            clazz = class_for_name0(class_name.replace('/', '.') + "_impl", true);
        } else {
            clazz = class_for_name0("zvm.Natives$" + class_name.replace('/', '_'), true);
        }
        HashMap<String, Object/*Invokable*/> map = new HashMap<>();
        assert !vm.natives.containsKey(class_name);
        vm.natives.put(class_name, map);

        for (java.lang.reflect.Method java_method : clazz.getDeclaredMethods()) {
            // 处理重载(开始没处理重载, 最后加的代码, 懒得之前的了, 先这样吧)
            String method_name = java_method.getName();
            String descriptor;
            NativeDescriptor annotation = java_method.getAnnotation(NativeDescriptor.class);
            if (annotation == null) {
                descriptor = null;
            } else {
                if (!annotation.name().equals("")) {
                    method_name = annotation.name();
                }
                descriptor = annotation.descriptor();
            }

            Object o = map.get(method_name);
            if (o == null) {
                map.put(method_name, createInvokable(null, java_method));
            } else {
                Invokable[] arr;
                if (o instanceof Invokable) {
                    arr = new Invokable[] { ((Invokable) o), createInvokable(descriptor, java_method) };
                } else {
                    Invokable[] arr0 = (Invokable[]) o;
                    arr = new Invokable[arr0.length + 1];
                    System.arraycopy(arr0, 0, arr, 0, arr0.length);
                    arr[arr0.length] = createInvokable(descriptor, java_method);
                }
                map.put(method_name, arr);
            }
        }
    }

    private static Invokable createInvokable(String descriptor, java.lang.reflect.Method java_method) {
        return new Invokable() {
            @Override
            public @Nullable String descriptor() {
                return descriptor;
            }
            @Override
            public Object invoke(VM vm, ZMethod method, @Nullable ZObject object, Object[] args) {
                return method_invoke0(java_method, null, vm, method, object, args);
            }
        };
    }

    //~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-
    //
    //~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-

    // 用来拆箱, 一些 native 方法的参数需要手动处理拆箱, 比如 java.lang.reflect.Array.set
    static Object unbox(VM vm, ZObject z_object) {
        vm.check_null(z_object);
        ZClass z_class = z_object.z_class();

        if (z_class == vm.java_lang_Boolean) {
            return Interpreter.bool_val(z_class.field("value").get_value(z_object));
        } else if (z_class == vm.java_lang_Byte) {
            return Interpreter.byte_val((z_class.field("value").get_value(z_object)));
        } else if (z_class == vm.java_lang_Character) {
            return Interpreter.char_val(z_class.field("value").get_value(z_object));
        } else if (z_class == vm.java_lang_Short) {
            return Interpreter.short_val((z_class.field("value").get_value(z_object)));
        } else if (z_class == vm.java_lang_Integer
                || z_class == vm.java_lang_Long
                || z_class == vm.java_lang_Float
                || z_class == vm.java_lang_Double) {
            return z_class.field("value").get_value(z_object);
        } else {
            return z_object;
        }
    }

    // 用来装箱, 一些 native 方法的返回值需要手动处理装箱, 比如 java.lang.reflect.Array.get
    static ZObject box(VM vm, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ZObject) {
            return (ZObject) value;
        }
        if (value instanceof Boolean) {
            return (ZObject) vm.java_lang_Boolean.method("valueOf(Z)Ljava/lang/Boolean;")
                    .invoke(null, new Object[] { value });
        } else if (value instanceof Byte) {
            return (ZObject) vm.java_lang_Byte.method("valueOf(B)Ljava/lang/Byte;")
                    .invoke(null, new Object[] { value });
        } else if (value instanceof Character) {
            return (ZObject) vm.java_lang_Character.method("valueOf(C)Ljava/lang/Character;")
                    .invoke(null, new Object[] { value });
        } else if (value instanceof Short) {
            return (ZObject) vm.java_lang_Short.method("valueOf(S)Ljava/lang/Short;")
                    .invoke(null, new Object[] { value });
        } else if (value instanceof Integer) {
            return (ZObject) vm.java_lang_Integer.method("valueOf(I)Ljava/lang/Integer;")
                    .invoke(null, new Object[] { value });
        } else if (value instanceof Long) {
            return (ZObject) vm.java_lang_Long.method("valueOf(J)Ljava/lang/Long;")
                    .invoke(null, new Object[] { value });
        } else if (value instanceof Float) {
            return (ZObject) vm.java_lang_Float.method("valueOf(F)Ljava/lang/Float;")
                    .invoke(null, new Object[] { value });
        } else if (value instanceof Double) {
            return (ZObject) vm.java_lang_Double.method("valueOf(D)Ljava/lang/Double;")
                    .invoke(null, new Object[] { value });
        } else {
            throw new AssertionError();
        }
    }

    static ZObject new_java_lang_Long(VM vm, long val) {
        return vm.java_lang_Long.new_instance("(J)V", new Object[] { val });
    }

    static ZObject string_intern(VM vm, ZObject str) {
        vm.check_null(str);
        ZClass java_lang_string = vm.load_class("java/lang/String", false);
        assert java_lang_string.is_assignable_from(str.z_class());
        if (java_lang_String_field_value_slot == -1) {
            java_lang_String_field_value_slot = java_lang_string.field("value").field_slot();
        }
        String value = new String(((ZArray) str.get_field(java_lang_String_field_value_slot)).char_array());
        // String value = new String(((ZArray) java_lang_string.field("value").get_value(str)).char_array());
        return new_intern_string(vm, value);
    }

    static ZObject new_string(VM vm, String value) {
        return new_string(vm, value, false);
    }

    static ZObject new_intern_string(VM vm, String value) {
        return new_string(vm, value, true);
    }

    private static ZObject new_string(VM vm, String value, boolean intern) {
        if (intern) {
            // 注意 intern 方法的返回值
            ZObject str = new_string0(vm, value.toCharArray()); // 这里不能塞到 compute 里头, jdk8bug 会死循环
            ZObject str0 = vm.intern_strings.putIfAbsent(value, str);
            if (str0 == null) {
                return str;
            } else {
                return str0;
            }
        } else {
            return new_string0(vm, value.toCharArray());
        }
    }

    // string 对象创建入口!!! 优先从 intern 常量池找
    private static ZObject new_string0(VM vm, char[] value) {
        ZObject str = vm.intern_strings.get(new String(value));
        if (str == null) {
            Object[] chars = { vm.class_char.new_array(value) };
            return vm.load_class("java/lang/String").new_instance("([C)V", chars);
        } else {
            return str;
        }
    }

    // 可以跨实例缓存
    static int java_lang_String_field_value_slot = -1;
    static String from_string(VM vm, ZObject z_object) {
        vm.check_null(z_object);
        ZClass java_lang_string = vm.load_class("java/lang/String", false);
        assert z_object.z_class() == java_lang_string;
        if (java_lang_String_field_value_slot == -1) {
            java_lang_String_field_value_slot = java_lang_string.field("value").field_slot();
        }
        ZArray value = (ZArray) z_object.get_field(java_lang_String_field_value_slot);
        // ZArray value = (ZArray) java_lang_string.field("value").get_value(z_object);
        return new String(value.char_array());
    }

    static ZThrowable new_throwable(VM vm, Exception e) {
        Object[] args = { new_string(vm, e.getMessage()) };
        String class_name = e.getClass().getName().replace('.', '/');
        ZObject ex_object = vm.load_class(class_name)
                // new_instance 会触发填充堆栈
                .new_instance("(Ljava/lang/String;)V", args);
        return new ZThrowable(ex_object);
    }

    static ZObject method_type(VM vm, String descriptor) {
        ZClass method_type_class = vm.load_class("java/lang/invoke/MethodType", true);
        ZMethod method_type_method_ = method_type_class.static_method("methodType",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/invoke/MethodType;");

        ZClass return_type = vm.load_class(Descriptor.return_type(descriptor), true);
        String[] param_type_names = Descriptor.parameter_types(descriptor);
        ZClass[] param_types = new ZClass[param_type_names.length];
        for (int i = 0; i < param_type_names.length; i++) {
            param_types[i] = vm.load_class(param_type_names[i], true);
        }

        return (ZObject) method_type_method_.invoke(null, new Object[] {
                return_type, param_types
        });
    }

    //~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-
    //
    //~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-

    static class array_class {
        // 在方法 resolve 阶段 link 到 ZArray::clone 方法
        @Override public native Object clone() throws CloneNotSupportedException;
        static ZObject getClass(VM vm, ZMethod method, ZObject object, Object[] args) {
            return object.z_class();
        }

        static int hashCode(VM vm, ZMethod method, ZObject object, Object[] args) {
            return System.identityHashCode(object);
        }
        static void notify(VM vm, ZMethod method, ZObject object, Object[] args) {
            object.notify();
        }

        static void notifyAll(VM vm, ZMethod method, ZObject object, Object[] args) {
            object.notifyAll();
        }

        static void wait(VM vm, ZMethod method, ZObject object, Object[] args) throws InterruptedException {
            long timeout = (long) args[0];
            object.wait(timeout);
        }
    }

    static class java_lang_Object {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) {
            vm.load_class("java/lang/ref/SoftReference", true)
                    .field("clock").put_value(null, 0L);
        }

        static ZObject getClass(VM vm, ZMethod method, ZObject object, Object[] args) {
            return object.z_class();
        }

        static int hashCode(VM vm, ZMethod method, ZObject object, Object[] args) {
            return System.identityHashCode(object);
        }

        static Object clone(VM vm, ZMethod method, ZObject object, Object[] args) {
            throw new ZThrowable(vm.load_class("java/lang/CloneNotSupportedException", true)
                            .new_instance());
            // return object;
        }

        static void notify(VM vm, ZMethod method, ZObject object, Object[] args) {
            object.notify();
        }

        static void notifyAll(VM vm, ZMethod method, ZObject object, Object[] args) {
            object.notifyAll();
        }

        static void wait(VM vm, ZMethod method, ZObject object, Object[] args) throws InterruptedException {
            long timeout = (long) args[0];
            object.wait(timeout);
        }
    }

    static class java_lang_Class {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }

        static ZObject forName0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject name = ((ZObject) args[0]); // String
            boolean initialize = Interpreter.bool_val(args[1]);
            ZObject loader = ((ZObject) args[2]); // ClassLoader
            ZObject caller = ((ZObject) args[3]); // Class<?>
            assert name != null;
            String for_name = from_string(vm, name);
            assert for_name.length() > 0;
            // 比 jdk 的 forName0 多了支持 primitive 类型
            return vm.load_class(for_name.replace('.', '/'), initialize);
        }

        static boolean isPrimitive(VM vm, ZMethod method, ZObject object, Object[] args) {
            return ((ZClass) object).is_primitive();
        }

        static boolean isArray(VM vm, ZMethod method, ZObject object, Object[] args) {
            return ((ZClass) object).is_array();
        }

        static boolean isInterface(VM vm, ZMethod method, ZObject object, Object[] args) {
            return ((ZClass) object).is_interface();
        }

        static ZObject getName0(VM vm, ZMethod method, ZObject object, Object[] args) {
            String class_name = ((ZClass) object).name().replace('/', '.');
            return new_string(vm, class_name);
        }

        static ZClass getComponentType(VM vm, ZMethod method, ZObject object, Object[] args) {
            assert ((ZClass) object).is_array();
            return ((ZClass) object).component_class();
        }

        static int getModifiers(VM vm, ZMethod method, ZObject object, Object[] args) {
            return ((ZClass) object).access_flags();
        }

        static ZClass getSuperclass(VM vm, ZMethod method, ZObject object, Object[] args) {
            return ((ZClass) object).super_class();
        }

        // private native Class<?>[] getInterfaces0();
        static ZArray getInterfaces0(VM vm, ZMethod method, ZObject object, Object[] args) {
            return vm.load_class("java/lang/Class", false)
                    .new_array(((ZClass) object).interfaces());
        }

        // public native boolean isInstance(Object obj);
        static boolean isInstance(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject obj = (ZObject) args[0];
            if (obj == null) {
                return false;
            } else {
                return ((ZClass) object).is_assignable_from(obj.z_class());
            }
        }

        static boolean isAssignableFrom(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass cls = ((ZClass) args[0]);
            vm.check_null(cls);
            return ((ZClass) object).is_assignable_from(cls);
        }

        static boolean desiredAssertionStatus0(VM vm, ZMethod method, ZObject object, Object[] args) {
            // 断言开启...
            return true;
        }

        static ZObject getPrimitiveClass(VM vm, ZMethod method, ZObject object, Object[] args) {
            String name = from_string(vm, (ZObject) args[0]);
            ZClass z_class = vm.load_class(name, true);
            assert z_class.is_primitive();
            return z_class;
        }

        // private native Class<?> getDeclaringClass0();
        static ZClass getDeclaringClass0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass z_class = ((ZClass) object);
            return z_class.declaring_class(); // todo 不知道对不对...
        }

        // private native Object[] getEnclosingMethod0();
        static ZArray getEnclosingMethod0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass z_class = ((ZClass) object);
            Object[] enclosing_method = z_class.enclosing_method();
            if (enclosing_method == null) {
                return null;
            } else {
                return vm.java_lang_Object.new_array(enclosing_method);
            }
        }

        // private native Constructor<T>[] getDeclaredConstructors0(boolean publicOnly);
        static ZArray getDeclaredConstructors0(VM vm, ZMethod method, ZObject object, Object[] args) {
            boolean public_only = Interpreter.bool_val(args[0]);
            ZClass z_class = ((ZClass) object);

            if (z_class.declared_constructors0_cache_ == null) {
                z_class.declared_constructors0_cache_ = getDeclaredConstructors0_(vm, z_class);
            }

            if (public_only) {
                ZClass class_constructor = vm.load_class("java/lang/reflect/Constructor", false);
                ZField field_modifiers = class_constructor.field("modifiers");
                ZObject[] constructors = z_class.declared_constructors0_cache_.z_object_array();
                List<ZObject> public_constructors = new ArrayList<>();
                for (ZObject constructor : constructors) {
                    int access_flags = ((int) field_modifiers.get_value(constructor));
                    if ((access_flags & ACC_PUBLIC) != 0) {
                        public_constructors.add(constructor);
                    }
                }
                return class_constructor.new_array(public_constructors.toArray(new ZObject[0]));
            } else {
                return z_class.declared_constructors0_cache_.clone0();
            }
        }
        private static ZArray getDeclaredConstructors0_(VM vm, ZClass z_class) {
//            boolean public_only = Interpreter.bool_val(args[0]);
//            ZClass z_class = ((ZClass) object);

            // method 与其在 method[]数组index, 用来配合
            // sun_reflect_NativeConstructorAccessorImpl.newInstance0
            Map<Method, Integer> constructor_slots = new LinkedHashMap<>();
            Method[] methods = z_class.methods();
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                if (m.name().equals(instance_init)) {
//                    if (public_only) {
//                        if ((m.access_flags & ACC_PUBLIC) != 0) {
//                            constructor_slots.put(m, i);
//                        }
//                    } else {
                        constructor_slots.put(m, i);
//                    }
                }
            }

            ZClass constructor_class = vm.load_class("java/lang/reflect/Constructor");
            String descriptor = "(Ljava/lang/Class;[Ljava/lang/Class;[Ljava/lang/Class;IILjava/lang/String;[B[B)V";
            ZObject[] constructors = new ZObject[constructor_slots.size()];

            int i = 0;
            for (Map.Entry<Method, Integer> it : constructor_slots.entrySet()) {
                Method constructor = it.getKey();
                // 方法在 class 文件中的 index
                int slot = it.getValue();

                String[] param_type_names = Descriptor.parameter_types(constructor.descriptor());
                ZClass[] param_classes = new ZClass[param_type_names.length];
                for (int j = 0; j < param_type_names.length; j++) {
                    param_classes[j] = vm.load_class(param_type_names[j], true);
                }

                String[] ex_names = constructor.checked_exceptions();
                ZClass[] ex_classes;
                if (ex_names == null) {
                    ex_classes = new ZClass[0];
                } else {
                    ex_classes = new ZClass[ex_names.length];
                    for (int j = 0; j < ex_names.length; j++) {
                        ex_classes[j] = vm.load_class(ex_names[j], true);
                    }
                }

                ZClass java_lang_class = vm.load_class("java/lang/Class", false);
                constructors[i] = constructor_class.new_instance(descriptor, new Object[] {
                        vm.load_class(constructor.class_file().this_name(), true),
                        java_lang_class.new_array(param_classes),
                        java_lang_class.new_array(ex_classes),
                        constructor.access_flags,
                        // 🚨🚨🚨🚨🚨 hack 用 method 顺序做 slot, 给 unsafe 用...
                        slot,
                        new_string(vm, constructor.generic_signature()),
                        vm.class_byte.new_array(constructor.annotations_raw),
                        vm.class_byte.new_array(constructor.parameter_annotations_raw)
                });

                i++;
            }

            return vm.load_class("java/lang/reflect/Constructor", false)
                    .new_array(constructors);
        }

        // private native java.lang.reflect.Method[]      getDeclaredMethods0(boolean publicOnly);
        static ZArray getDeclaredMethods0(VM vm, ZMethod method, ZObject object, Object[] args) {
            boolean public_only = Interpreter.bool_val(args[0]);
            ZClass z_class = ((ZClass) object);

            if (z_class.declared_methods0_cache_ == null) {
                z_class.declared_methods0_cache_ = getDeclaredMethods0_(vm, method, z_class);
            }

            if (public_only) {
                ZClass method_class = vm.load_class("java/lang/reflect/Method", false);
                ZField field_modifiers = method_class.field("modifiers");

                ZObject[] methods = z_class.declared_methods0_cache_.z_object_array();
                List<ZObject> public_methods = new ArrayList<>();
                for (ZObject method_ : methods) {
                    int access_flags = ((int) field_modifiers.get_value(method_));
                    if ((access_flags & ACC_PUBLIC) != 0) {
                        public_methods.add(method_);
                    }
                }
                return method_class.new_array(public_methods.toArray(new ZObject[0]));
            } else {
                return z_class.declared_methods0_cache_.clone0();
            }
        }

        private static ZArray getDeclaredMethods0_(VM vm, ZMethod method_, ZClass z_class) {
            // method_ 与其在 method_[]数组index, 用来配合
            // sun_reflect_NativeConstructorAccessorImpl.newInstance0
            Map<Method, Integer> methods_slots = new LinkedHashMap<>();
            Method[] methods_ = z_class.methods();
            for (int i = 0; i < methods_.length; i++) {
                Method m = methods_[i];
                if (!m.name().equals(instance_init)) {
                    methods_slots.put(m, i);
                }
            }

            ZClass method_class = vm.load_class("java/lang/reflect/Method");
            String descriptor = "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;IILjava/lang/String;[B[B[B)V";
            ZObject[] methods = new ZObject[methods_slots.size()];

            int i = 0;
            for (Map.Entry<Method, Integer> it : methods_slots.entrySet()) {
                Method method = it.getKey();
                // 方法在 class 文件中的 index
                int slot = it.getValue();

                String[] param_type_names = Descriptor.parameter_types(method.descriptor());
                String return_type_name = Descriptor.return_type(method.descriptor());
                ZClass[] param_classes = new ZClass[param_type_names.length];
                for (int j = 0; j < param_type_names.length; j++) {
                    param_classes[j] = vm.load_class(param_type_names[j], true);
                }
                ZClass return_type = vm.load_class(return_type_name, true);

                String[] ex_names = method.checked_exceptions();
                ZClass[] ex_classes;
                if (ex_names == null) {
                    ex_classes = new ZClass[0];
                } else {
                    ex_classes = new ZClass[ex_names.length];
                    for (int j = 0; j < ex_names.length; j++) {
                        ex_classes[j] = vm.load_class(ex_names[j], true);
                    }
                }

                ZClass java_lang_class = vm.load_class("java/lang/Class", false);
                methods[i] = method_class.new_instance(descriptor, new Object[] {
                        vm.load_class(method.class_file().this_name(), true),
                        new_intern_string(vm, method.name()),
                        java_lang_class.new_array(param_classes),
                        return_type,
                        java_lang_class.new_array(ex_classes),
                        method.access_flags,
                        // 🚨🚨🚨🚨🚨 hack 用 method 顺序做 slot, 给 unsafe 用...
                        slot,
                        new_string(vm, method.generic_signature()),
                        vm.class_byte.new_array(method.annotations_raw),
                        vm.class_byte.new_array(method.parameter_annotations_raw),
                        vm.class_byte.new_array(method.annotation_default_raw)
                });

                i++;
            }

            return vm.load_class("java/lang/reflect/Method", false)
                    .new_array(methods);
        }

        // private native Field[]       getDeclaredFields0(boolean publicOnly);
        static ZArray getDeclaredFields0(VM vm, ZMethod method, ZObject object, Object[] args) {
            boolean public_only = Interpreter.bool_val(args[0]);
            ZClass z_class = ((ZClass) object);

            if (z_class.declared_fields0_cache_ == null) {
                z_class.declared_fields0_cache_ = getDeclaredFields0_(vm, method, z_class);
            }

            if (public_only) {
                ZClass class_field = vm.load_class("java/lang/reflect/Field", false);
                ZField field_modifiers = class_field.field("modifiers");
                ZObject[] fields = z_class.declared_fields0_cache_.z_object_array();
                List<ZObject> public_fields = new ArrayList<>();
                for (ZObject field : fields) {
                    int access_flags = ((int) field_modifiers.get_value(field));
                    if ((access_flags & ACC_PUBLIC) != 0) {
                        public_fields.add(field);
                    }
                }
                return class_field.new_array(public_fields.toArray(new ZObject[0]));
            } else {
                return z_class.declared_fields0_cache_.clone0();
            }
        }
        private static ZArray getDeclaredFields0_(VM vm, ZMethod method, ZClass z_class) {
            ClassFile.Field[] fields_ = z_class.fields();
            int field_length = fields_.length;
            ZObject[] fields = new ZObject[field_length];

            ZClass field_class = vm.load_class("java/lang/reflect/Field");
            int i = 0;
            String descriptor = "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;IILjava/lang/String;[B)V";
            for (int slot = 0; slot < field_length; slot++) {
                ClassFile.Field field = fields_[slot];
                fields[i] = field_class.new_instance(descriptor, new Object[] {
                        z_class, // declaringClass
                        // 🚨🚨🚨🚨🚨 这里一定要用 intern, 因为 Class.java
                        // private static Field searchFields(Field[] fields, String name) {
                        //        String internedName = name.intern();
                        //        for (int i = 0; i < fields.length; i++) {
                        //            if (fields[i].getName() == internedName) {
                        //                return getReflectionFactory().copyField(fields[i]);
                        //            }
                        //        }
                        //        return null;
                        // }
                        new_intern_string(vm, field.name()),
                        vm.load_class(Descriptor.field_type(field.descriptor()), true),
                        field.access_flags,
                        // 🚨🚨🚨🚨🚨 hack 用 field 顺序做 slot, 给 unsafe 用...
                        slot,
                        new_string(vm, field.generic_signature()),
                        vm.class_byte.new_array(field.annotations_raw)
                });
                i++;
            }
            return vm.load_class("java/lang/reflect/Field", false)
                    .new_array(fields);
        }
    }

    static class java_lang_ClassLoader {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }

        // private static native String findBuiltinLib(String name);
        static ZObject findBuiltinLib(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject name = (ZObject) args[0];
            vm.check_null(name);
            String build_in_lib = Reflect.of(ClassLoader.class)
                    .method("findBuiltinLib", String.class)
                    .invoke(from_string(vm, name));
            if (build_in_lib == null) {
                return null;
            } else {
                return new_string(vm, build_in_lib);
            }
        }
    }

    static class java_lang_Package {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private static native String getSystemPackage0(String name);
//        static ZObject getSystemPackage0(VM vm, ZMethod method, ZObject object, Object[] args) {
//            ZObject name_z_object = (ZObject) args[0];
//            vm.check_null(name_z_object);
//            String name = from_string(vm, name_z_object);
//
//        }
//        // private static native String[] getSystemPackages0();
//        static ZArray intern(VM vm, ZMethod method, ZObject object, Object[] args) {
//
//        }
    }

    static class java_lang_String {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static ZObject intern(VM vm, ZMethod method, ZObject object, Object[] args) {
            return string_intern(vm, object);
        }
    }

    static class java_lang_invoke_MethodHandleNatives {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) {}
        static Class<?> mh_natives_;
        static Reflect.ReflectMethod getConstant_;
        static Reflect.ReflectMethod getNamedCon_;
        static {
            try {
                mh_natives_ = Class.forName("java.lang.invoke.MethodHandleNatives");
                getConstant_ = Reflect.of(mh_natives_).method("getConstant", int.class);
                getNamedCon_ = Reflect.of(mh_natives_).method("getNamedCon", int.class, Object[].class);
            } catch (ClassNotFoundException e) {
                sneakyThrows(e);
            }
        }

        // static native int getConstant(int which);
        static int getConstant(VM vm, ZMethod method, ZObject object, Object[] args) {
            int which = ((int) args[0]);
            return (int) getConstant_.invoke(which);
        }

        // private static native int getNamedCon(int which, Object[] name);
        static int getNamedCon(VM vm, ZMethod method, ZObject object, Object[] args) {
            int which = ((int) args[0]);
            ZArray name = ((ZArray) args[1]);
            return (int) getNamedCon_.invoke(which, name.z_object_array());
        }

        // static native MemberName resolve(MemberName self, Class<?> caller) throws LinkageError, ClassNotFoundException;
        static ZObject resolve(VM vm, ZMethod method, ZObject object, Object[] args) {
            throw new AssertionError();
        }
    }

    static class java_lang_System {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) {
            // todo 移动到 vm 里头...
//            ZClass java_lang_System = method.declared_class(); //vm.load_class("java/lang/System", true);
//            java_lang_System.method("initializeSystemClass()V").invoke(null, new Object[0]);

            // todo !!!  本来应该是   System.initializeSystemClass
//            vm.events.put(java_lang_System, new VM.Event() {
//                @Override
//                public void after_clinit(ZClass z_class) {
//                    ZClass prop_class = vm.load_class("java/util/Properties");
//                    ZObject props = prop_class.new_instance();
//                    ZMethod set_prop = prop_class.virtual_method("setProperty",
//                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");
//                    String[] keys = new String[] {
//                            "file.encoding",
//                            "line.separator",
//                            "file.separator",
//                            "path.separator",
//                            "java.home"
//                    };
//                    for (String key : keys) {
//                        set_prop.invoke(props, new Object[] {
//                                new_string(vm, key),
//                                new_string(vm, System.getProperty(key))
//                        });
//                    }
//                    java_lang_System.field("props").put_value(null, props);
//
//                    // java_lang_system 中 这三个 final static 字段 不是在 <clinit> 中赋值的
//                    vm.java_lang_system_in_ = java_lang_System.field("in");
//                    vm.java_lang_system_out_ = java_lang_System.field("out");
//                    vm.java_lang_system_err_ = java_lang_System.field("err");
//                    vm.java_lang_system_in_.put_value(null, vm.load_class("zvm/Natives$std_in").new_instance());
//                    vm.java_lang_system_out_.put_value(null, vm.load_class("zvm/Natives$std_out").new_instance());
//                    vm.java_lang_system_err_.put_value(null, vm.load_class("zvm/Natives$std_err").new_instance());
//                }
//            });
        }

        // private static native Properties initProperties(Properties props);
        static ZObject initProperties(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject props = (ZObject) args[0];
            String[] keys = new String[] {
                    "java.version",
                    "java.vendor",
                    "java.vendor.url",
                    "java.home",
                    "java.class.version",
                    "java.class.path",
                    "os.name",
                    "os.arch",
                    "os.version",
                    "file.encoding",
                    "file.separator",
                    "path.separator",
                    "line.separator",
                    "user.name",
                    "user.home",
                    "user.dir",
                    "java.library.path",
            };
            ZClass prop_class = vm.load_class("java/util/Properties");
            ZMethod set_prop = prop_class.virtual_method("setProperty",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");
            for (String key : keys) {
                set_prop.invoke(props, new Object[] {
                        new_string(vm, key),
                        new_string(vm, System.getProperty(key))
                });
            }
            return props;
        }

        // public static native long currentTimeMillis();
        static long currentTimeMillis(VM vm, ZMethod method, ZObject object, Object[] args) {
            return System.currentTimeMillis();
        }

        // public static native long nanoTime();
        static long nanoTime(VM vm, ZMethod method, ZObject object, Object[] args) {
            return System.nanoTime();
        }

        // hack_
        static void loadLibrary(VM vm, ZMethod method, ZObject object, Object[] args) { }

        // private static native void setIn0(InputStream in);
        static void setIn0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject in = (ZObject) args[0];
            method.declared_class().field("in").put_value(null, in);
        }

        // private static native void setOut0(PrintStream out);
        static void setOut0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject out = (ZObject) args[0];
            method.declared_class().field("out").put_value(null, out);
        }

        // private static native void setErr0(PrintStream err);
        static void setErr0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject err = (ZObject) args[0];
            method.declared_class().field("err").put_value(null, err);
        }

        // public static native String mapLibraryName(String libname);
//        static ZObject mapLibraryName(VM vm, ZMethod method, ZObject object, Object[] args) {
//            ZObject libname = (ZObject) args[0];
//            vm.check_null(libname);
//            return new_string(vm, System.mapLibraryName(from_string(vm, libname)));
//        }

        // public static native int identityHashCode(Object x);
        static int identityHashCode(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject z_object = (ZObject) args[0];
            return System.identityHashCode(z_object);
        }

        static void arraycopy(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZArray src = ((ZArray) args[0]);
            int src_pos = (int) args[1];
            ZArray dest = ((ZArray) args[2]);
            int dest_pos = (int) args[3];
            int length = (int) args[4];

            vm.check_null(src);
            vm.check_null(dest_pos);

            try {
                ZArray.copy(src, src_pos, dest, dest_pos, length);
            } catch (ArrayStoreException | ArrayIndexOutOfBoundsException e) {
                throw new_throwable(vm, e);
            }
        }
    }

    static class java_lang_Float {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static int floatToRawIntBits(VM vm, ZMethod method, ZObject object, Object[] args) {
            float value = (float) args[0];
            return Float.floatToRawIntBits(value);
        }
    }

    static class java_lang_Double {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static long doubleToRawLongBits(VM vm, ZMethod method, ZObject object, Object[] args) {
            double value = (double) args[0];
            return Double.doubleToRawLongBits(value);
        }
        static double longBitsToDouble(VM vm, ZMethod method, ZObject object, Object[] args) {
            long bits = (long) args[0];
            return Double.longBitsToDouble(bits);
        }
    }

    // static double fillInStackTrace(VM vm, ZMethod method, ZObject object, Object[] args) {}
    static class java_lang_StrictMath {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static double sin(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.sin((double) args[0]); }
        static double cos(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.cos((double) args[0]); }
        static double tan(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.tan((double) args[0]); }
        static double asin(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.asin((double) args[0]); }
        static double acos(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.acos((double) args[0]); }
        static double atan(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.atan((double) args[0]); }
        static double exp(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.exp((double) args[0]); }
        static double log(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.log((double) args[0]); }
        static double log10(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.log10((double) args[0]); }
        static double sqrt(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.sqrt((double) args[0]); }
        static double cbrt(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.cbrt((double) args[0]); }
        static double IEEEremainder(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.IEEEremainder((double) args[0], (double) args[1]); }
        static double atan2(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.atan2((double) args[0], (double) args[1]); }
        static double pow(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.pow((double) args[0], (double) args[1]); }
        static double sinh(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.sinh((double) args[0]); }
        static double cosh(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.cosh((double) args[0]); }
        static double tanh(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.tanh((double) args[0]); }
        static double hypot(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.hypot((double) args[0], (double) args[1]); }
        static double expm1(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.expm1((double) args[0]); }
        static double log1p(VM vm, ZMethod method, ZObject object, Object[] args) { return StrictMath.log1p((double) args[0]); }
    }

    static class java_lang_Thread {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static ZObject currentThread(VM vm, ZMethod method, ZObject object, Object[] args) {
            return vm.main_thread;
        }
        static void setPriority0(VM vm, ZMethod method, ZObject object, Object[] args) {
            int newPriority = (int) args[0];
            // ...
        }
    }

    static class java_lang_Runtime {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static int availableProcessors(VM vm, ZMethod method, ZObject object, Object[] args) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    static class java_lang_Throwable {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        private static ZObject[] stack_trace_elements(VM vm) {
            ZClass stack_trace_element_class = vm.load_class("java/lang/StackTraceElement", true);
            Deque<ZThread.Frame> stack = vm.stacks.get();
            ZObject[] stack_trace_elements = new ZObject[stack.size()];
            int i = 0;
            for (ZThread.Frame frame : stack) {
                // public StackTraceElement(String declaringClass, String methodName, String fileName, int lineNumber) {
                stack_trace_elements[i++] = stack_trace_element_class
                        .new_instance("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V",
                                new Object[] {
                                        new_string(vm, frame.method.class_name()),
                                        new_string(vm, frame.method.name()),
                                        new_string(vm, frame.method.file_name()),
                                        frame.line_number(),
                                });
            }
            return stack_trace_elements;
        }
        private static void fillInStackTrace0(VM vm, ZClass z_class, ZObject z_object) {
            ZClass stack_trace_element_class = vm.load_class("java/lang/StackTraceElement", true);
            z_class.field("backtrace").put_value(z_object, stack_trace_element_class.new_array(stack_trace_elements(vm)));
        }
        // private native Throwable fillInStackTrace(int dummy);
        static void fillInStackTrace(VM vm, ZMethod method, ZObject object, Object[] args) {
            // int dummy = (int) args[0]; // 这货应该是 native 用来区分方法签名故意加了一个 dummy 参数
            // 注意 !!! 这里保存在 backtrace 中 而不是 stackTrace
            fillInStackTrace0(vm, method.declared_class(), object);
        }
        // native int getStackTraceDepth();
        static int getStackTraceDepth(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass stack_trace_element_class = vm.load_class("java/lang/StackTraceElement", true);
            ZArray backtrace = (ZArray) method.declared_class().field("backtrace").get_value(object);
            if (backtrace == null) {
                fillInStackTrace0(vm, method.declared_class(), object);
                return getStackTraceDepth(vm, method, object, args);
            } else {
                return backtrace.length();
            }
        }
        // native StackTraceElement getStackTraceElement(int index);
        static ZObject getStackTraceElement(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass stack_trace_element_class = vm.load_class("java/lang/StackTraceElement", true);
            ZArray backtrace = (ZArray) method.declared_class().field("backtrace").get_value(object);
            if (backtrace == null) {
                fillInStackTrace0(vm, method.declared_class(), object);
                return getStackTraceElement(vm, method, object, args);
            } else {
                int index = (int) args[0];
                return backtrace.z_object_array()[index];
            }
        }
    }

    static class java_lang_reflect_Array {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private static native Object newArray(Class<?> componentType, int length)
        static Object newArray(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass component_type = ((ZClass) args[0]);
            int length = ((int) args[1]);
            return component_type.new_array(length);
        }
        // private static native Object multiNewArray(Class<?> componentType, int[] dimensions)
        static Object multiNewArray(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass component_type = ((ZClass) args[0]);
            int[] dimensions = ((int[]) args[1]);
            return component_type.new_multi_array(dimensions);
        }
        // public static native Object get(Object array, int index)
        //        throws IllegalArgumentException, ArrayIndexOutOfBoundsException;
        static Object get(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject z_object = (ZObject) vm.check_null(args[0]);
            if (z_object instanceof ZArray) {
                ZArray array = ((ZArray) z_object);
                int index = ((int) args[1]);
                // 🚨🚨🚨🚨🚨 这里要处理装箱!!!
                Object val = array.index(index);
                if (val instanceof ZObject) {
                    return val;
                } else {
                    return box(vm, val);
                }
            } else {
                throw new ZThrowable(vm.load_class("java/lang/IllegalArgumentException", true)
                        .new_instance("(Ljava/lang/String;)V", new Object[] {
                                new_string(vm, "Argument is not an array")
                        }));
            }
        }
        // public static native void set(Object array, int index, Object value)
        //         throws IllegalArgumentException, ArrayIndexOutOfBoundsException;
        static void set(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject z_object = (ZObject) vm.check_null(args[0]);
            if (z_object instanceof ZArray) {
                ZArray array = ((ZArray) z_object);
                int index = ((int) args[1]);
                Object value = ((Object) args[2]);
                // 🚨🚨🚨🚨🚨 这里要处理拆箱!!!
                if (array.z_class().component_class().is_primitive()) {
                    array.index(index, unbox(vm, (ZObject) value));
                } else {
                    array.index(index, value);
                }
            } else {
                throw new ZThrowable(vm.load_class("java/lang/IllegalArgumentException", true)
                        .new_instance("(Ljava/lang/String;)V", new Object[] {
                                new_string(vm, "Argument is not an array")
                        }));
            }
        }
    }

    static class java_security_MessageDigest {
        // public static MessageDigest getInstance(String algorithm)
        static ZObject getInstance(VM vm, ZMethod method, ZObject object, Object[] args) {
            // todo hack
            // String algorithm = args[0];
            return vm.load_class("java/security/MessageDigest", false).allocate();
        }
        // public byte[] digest(byte[] input)
        static ZArray digest(VM vm, ZMethod method, ZObject object, Object[] args) throws NoSuchAlgorithmException {
            // todo hack
            byte[] input = ((ZArray) args[0]).byte_array();
            MessageDigest md = MessageDigest.getInstance("SHA");
            byte[] hashBytes = md.digest(input);
            return vm.class_byte.new_array(hashBytes);
        }
    }

    static class java_io_ObjectStreamClass {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private static native void initNative();
        static void initNative(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private native static boolean hasStaticInitializer(Class<?> cl);
        static boolean hasStaticInitializer(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass cl = (ZClass) args[0];
            return cl.method("<clinit>()V") != null;
        }
    }

    static class java_io_UnixFileSystem {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static void initIDs(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // public native int getBooleanAttributes0(File f);
        static int getBooleanAttributes0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject java_io_File = (ZObject) args[0];
            ZObject path = (ZObject) vm.load_class("java/io/File").field("path").get_value(java_io_File);
            File file = new File(from_string(vm, path));

            try {
                Object fs = Reflect.of(File.class).field("fs").get();
                assert Class.forName("java.io.UnixFileSystem").isInstance(fs);
                Reflect.ReflectMethod getBooleanAttributes0 = Reflect.of(fs).method("getBooleanAttributes0", File.class);
                return getBooleanAttributes0.invoke(file);
            } catch (ClassNotFoundException e) {
                sneakyThrows(e);
                throw new AssertionError();
            }
        }
    }

    static class java_io_FileDescriptor {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        /* This routine initializes JNI field offsets for the class */
        // private static native void initIDs();
        static void initIDs(VM vm, ZMethod method, ZObject object, Object[] args) { }
    }

    static class java_io_FileInputStream {
        private static FileInputStream new_file(String path) {
            try {
                return new FileInputStream(path);
            } catch (FileNotFoundException e) {
                sneakyThrows(e);
                return null;
            }
        }
        private static FileInputStream get_file(VM vm, String path) {
            FileInputStream fis = vm.open_files.get().get(path);
            vm.check_null(fis);
            return fis;
        }
        private static FileInputStream open_file(VM vm, String path) {
            return vm.open_files.get().computeIfAbsent(path, it -> new_file(path));
        }
        private static final Reflect.ReflectMethod method_read_bytes
                = Reflect.of(FileInputStream.class).method("readBytes", byte[].class, int.class, int.class);
        private static final Reflect.ReflectMethod method_close0
                = Reflect.of(FileInputStream.class).method("close0");
        private static String get_path(VM vm, ZObject object) {
            return from_string(vm, (ZObject) object.z_class().field("path").get_value(object));
        }

        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private static native void initIDs();
        static void initIDs(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private native void open0(String name) throws FileNotFoundException;
        static void open0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject name = (ZObject) args[0];
            String path = from_string(vm, name);
            open_file(vm, path);
        }
        // private native void close0() throws IOException;
        static void close0(VM vm, ZMethod method, ZObject object, Object[] args) {
            FileInputStream fis = get_file(vm, get_path(vm, object));
            method_close0.instance(fis).invoke();
        }

        // private native int readBytes(byte b[], int off, int len) throws IOException;
        static int readBytes(VM vm, ZMethod method, ZObject object, Object[] args) {
            byte[] b = ((ZArray) args[0]).byte_array();
            int off = (int) args[1];
            int len = (int) args[2];
            FileInputStream fis = get_file(vm, get_path(vm, object));
            return method_read_bytes.instance(fis).invoke(b, off, len);
        }
        // public native int available() throws IOException;
        static int available(VM vm, ZMethod method, ZObject object, Object[] args) throws IOException {
            FileInputStream fis = get_file(vm, get_path(vm, object));
            return fis.available();
        }
    }

    static class java_io_FileOutputStream {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private static native void initIDs();
        static void initIDs(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private native void writeBytes(byte b[], int off, int len, boolean append) throws IOException;
        static void writeBytes(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZArray byte_array = (ZArray) args[0];
            int off = (int) args[1];
            int len = (int) args[2];
            boolean append = Interpreter.bool_val(args[3]);

            int fd = fd_of(vm, object);
            if (fd == 1) {
                System.out.write(byte_array.byte_array(), off, len);
            } else if (fd == 2) {
                System.err.write(byte_array.byte_array(), off, len);
            } else {
                // todo 文件 io...
//                Reflect.of(java.io.FileOutputStream.class)
//                        .method("writeBytes", byte[].class, int.class, int.class, boolean.class)
//                        .invoke(...)
            }
        }

        private static int fd_of(VM vm, ZObject file_output_stream) {
            ZObject fd = (ZObject) vm.java_io_FileOutPutStream$fd.get_value(file_output_stream);
            return  (int) vm.java_io_FileDescriptor$fd.get_value(fd);
        }
    }

    static class java_io_ObjectInputStream {
        @NativeDescriptor(name = "<clinit>")
        static void clinit(VM vm, ZMethod method, ZObject object, Object[] args) { }
    }

    static class sun_misc_VM {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static void initialize(VM vm, ZMethod method, ZObject object, Object[] args) {
            // getSavedProperty 方法会检查 savedProps.isEmpty(), 提前塞点东西进去...
            ZObject saved_props = (ZObject) method.declared_class().field("savedProps").get_value(null);
            assert saved_props != null;
            ZMethod put = saved_props.z_class().virtual_method("put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            put.invoke(saved_props, new Object[] {
                    new_string(vm, "java.lang.Integer.IntegerCache.high"),
                    new_string(vm, "1024")
            });
        }

        // public static native ClassLoader latestUserDefinedLoader();
        static ZObject latestUserDefinedLoader(VM vm, ZMethod method, ZObject object, Object[] args) {
            return null; // todo !!!
        }

        // public static void booted()  hack!
        static void booted(VM vm, ZMethod method, ZObject object, Object[] args) { }
    }

    static class sun_misc_Signal {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }

        // private static native int findSignal(String var0);
        static int findSignal(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject sig_name = (ZObject) args[0];
            vm.check_null(sig_name);
            return Reflect.of(Signal.class)
                    .method("findSignal", String.class)
                    .invoke(from_string(vm, sig_name));
        }

        // private static native long handle0(int var0, long var1);
        static long handle0(VM vm, ZMethod method, ZObject object, Object[] args) {
            int a = (int) args[0];
            long b = (Long) args[1]; // SignalHandler
            return Reflect.of(sun.misc.Signal.class)
                    .method("handle0", int.class, long.class)
                    .invoke(a, b);
        }
    }

    // https://tech.meituan.com/2019/02/14/talk-about-java-magic-class-unsafe.html
    static class sun_misc_Unsafe {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }

        // public native Class<?> defineClass(String name, byte[] b, int off, int len,
        //                                   ClassLoader loader, ProtectionDomain protectionDomain);
        static ZClass defineClass(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject name_z_object = (ZObject) args[0];
            ZArray b = (ZArray) args[1];
            int off = (int) args[2];
            int len = (int) args[3];
            ZObject loader = (ZObject) args[4];
            ZObject pd = (ZObject) args[5];

            vm.check_null(name_z_object);
            String name = from_string(vm, name_z_object);
            byte[] bytes = b.byte_array();

            Class<?> clazz = UNSAFE.defineClass(name, bytes, off, len, null, null);

//            byte[] bytes_dst = new byte[len];
//            System.arraycopy(bytes, off, bytes_dst, 0, len);
//            vm.class_reader.parse_class(bytes_dst)

            return vm.load_class(clazz.getName(), false);
        }


        ////////////////////////////////////////////////////////////
        // 系统相关
        //返回系统指针的大小。返回值为4（32位系统）或 8（64位系统）。
        // public native int addressSize();
        //内存页的大小，此值为2的幂次方。
        // public native int pageSize();
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        // 内存屏障
        //内存屏障，禁止load操作重排序。屏障前的load操作不能被重排序到屏障后，屏障后的load操作不能被重排序到屏障前
        // public native void loadFence();
        //内存屏障，禁止store操作重排序。屏障前的store操作不能被重排序到屏障后，屏障后的store操作不能被重排序到屏障前
        // public native void storeFence();
        //内存屏障，禁止load、store操作重排序
        // public native void fullFence();
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        // 线程调度
        //取消阻塞线程
        // public native void unpark(Object thread);
        //阻塞线程
        // public native void park(boolean isAbsolute, long time);
        //获得对象锁（可重入锁）
        // @Deprecated
        // public native void monitorEnter(Object o);
        //释放对象锁
        // @Deprecated
        // public native void monitorExit(Object o);
        //尝试获取对象锁
        // @Deprecated
        // public native boolean tryMonitorEnter(Object o);
        ////////////////////////////////////////////////////////////


        ////////////////////////////////////////////////////////////
        // 数组相关
        // 返回数组中第一个元素的偏移地址
        // public native int arrayBaseOffset(java.lang.Class<?> aClass);
        static int arrayBaseOffset(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass z_class = ((ZClass) args[0]);
            vm.check_null(z_class);
            return 0;
        }
        // 返回数组中一个元素占用的大小
        // public native int arrayIndexScale(java.lang.Class<?> aClass);
        static int arrayIndexScale(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass z_class = ((ZClass) args[0]);
            vm.check_null(z_class);
            // 这里返回 1 是因为 arrayBaseOffset 与 arrayIndexScale 配合使用来计算数组第 n 个元素的偏移
            // array_idx << (31 - (Integer.numberOfLeadingZeros(arrayIndexScale(?)))) + base
            // arrayIndexScale = 1,  numberOfLeadingZeros = 31,
            // ((array_idx << 0) + 0) == array_idx
            return 1;
        }
        ////////////////////////////////////////////////////////////


        // 说明: 以下维持宿主语义, 对内存操作
        ////////////////////////////////////////////////////////////
        // 内存操作
        //分配内存
        // public native long allocateMemory(long bytes);
        // .... 这里处理成申请的内容需要 putlong getbyte 之类方法使用, 不能跟上面那些用 slot 的混用
        static long allocateMemory(VM vm, ZMethod method, ZObject object, Object[] args) {
            long sz = ((long) args[0]);
            return UNSAFE.allocateMemory(sz);
        }
        //扩充内存
        // public native long reallocateMemory(long address, long bytes);
        //释放内存
        // public native void freeMemory(long address);
        static Object freeMemory(VM vm, ZMethod method, ZObject object, Object[] args) {
            long addr = ((long) args[0]);
            UNSAFE.freeMemory(addr);
            return null;
        }
        //在给定的内存块中设置值
        // public native void setMemory(Object o, long offset, long bytes, byte value);
        //内存拷贝
        // public native void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes);
        //获取给定地址值，忽略修饰限定符的访问限制。与此类似操作还有: getInt，getDouble，getLong，getChar等
        // public native Object getObject(Object o, long offset);
        @NativeDescriptor(name = "getObject", descriptor = "(Ljava/lang/Object;J)LJava/lang/Object;")
        static ZObject getObject(VM vm, ZMethod method, ZObject object, Object[] args) {
            return (ZObject) getXXX(vm, args);
//            @Nullable ZObject o = ((ZObject) args[0]);
//            // todo hack 使用 field_slot 代替 offset， 跟 objectFieldOffset 方法配合
//            long offset = ((long) args[1]); // field_slot
//            if (o == null) {
//                // offset: 绝对地址
//                throw new AssertionError(); // todo
//            } else {
//                return (ZObject) o.get_field(Math.toIntExact(offset));
//            }
        }
        // todo putXXX 没处理数组 reflectField 。。。！！！
        //为给定地址设置值，忽略修饰限定符的访问限制，与此类似操作还有: putInt,putDouble，putLong，putChar等
        // public native void putObject(Object o, long offset, Object x);
        @NativeDescriptor(name = "putObject", descriptor = "(Ljava/lang/Object;JI)V")
        static void putObject(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject o = ((ZObject) args[0]);
            // todo hack 使用 field_slot 代替 offset， 跟 objectFieldOffset 方法配合
            long offset = ((long) args[1]); // field_slot
            ZObject val = (ZObject) args[2];
            assert o != null;
            o.put_field(Math.toIntExact(offset), val);
        }
        // public native putInt putInt(Object o, long offset, int x);
        @NativeDescriptor(name = "putInt", descriptor = "(Ljava/lang/Object;JI)V")
        static void putInt(VM vm, ZMethod method, ZObject object, Object[] args) {
            @Nullable ZObject o = ((ZObject) args[0]);
            // todo hack 使用 field_slot 代替 offset， 跟 objectFieldOffset 方法配合
            long offset = ((long) args[1]); // field_slot
            int val = (int) args[2];
            if (o == null) {
                // offset: 绝对地址
                throw new AssertionError(); // todo
            } else {
                o.put_field(Math.toIntExact(offset), val);
            }
        }
        // public native void    putInt(long address, int x);
//        @NativeDescriptor(name = "putInt", descriptor = "(JI)V")
//        static void putInt1(VM vm, ZMethod method, ZObject object, Object[] args) {
//            long address = ((long) args[0]); //
//            int val = ((int) args[1]);
// todo
//        }
        // public native void putLong(long var1, long var3);
        @NativeDescriptor(name = "putLong", descriptor = "(JJ)V")
        static void putLong(VM vm, ZMethod method, ZObject object, Object[] args) {
            long addr = ((long) args[0]);
            long val = ((long) args[1]);
            UNSAFE.putLong(addr, val);
        }
        //获取给定地址的byte类型的值（当且仅当该内存地址为allocateMemory分配时，此方法结果为确定的）
        // public native byte getByte(long address);
        @NativeDescriptor(name = "getByte", descriptor = "(J)B")
        static byte getByte(VM vm, ZMethod method, ZObject object, Object[] args) {
            long addr = ((long) args[0]);
            return UNSAFE.getByte(addr);
        }
        //为给定地址设置byte类型的值（当且仅当该内存地址为allocateMemory分配时，此方法结果才是确定的）
        // public native void putByte(long address, byte x);
        ////////////////////////////////////////////////////////////


        // Class相关 对象操作相关 中与Field相关的方法, 内存偏移为 0,绝对地址全部当做 slot 使用

        ////////////////////////////////////////////////////////////
        // Class相关
        private static long field_slot(VM vm, ZObject field) {
            ZClass java_lang_reflect_field = vm.load_class("java/lang/reflect/Field", false);
            assert java_lang_reflect_field.is_assignable_from(field.z_class());
            // todo !!!   slot 的语义要全部重写
            ZField field_slot = java_lang_reflect_field.field("slot"); // !!!借用 slot
            return ((Integer) field_slot.get_value(field)).longValue();
        }
        //获取给定静态字段的内存地址偏移量，这个值对于给定的字段是唯一且固定不变的
        // public native long staticFieldOffset(Field f);
        static long staticFieldOffset(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject field = ((ZObject) args[0]);
            vm.check_null(field);
            return field_slot(vm, field);
        }
        //获取一个静态类中给定字段的对象指针
        // public native Object staticFieldBase(Field f);
        static Object staticFieldBase(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject field = ((ZObject) args[0]);
            vm.check_null(field);
            return field; // !!!特么这里直接把字段返回了, 不返回 base 地址或者 slot
            // return new_java_lang_Long(vm, field_slot(vm, field));
        }
        //判断是否需要初始化一个类，通常在获取一个类的静态属性的时候（因为一个类如果没初始化，它的静态属性也不会初始化）使用。 当且仅当ensureClassInitialized方法不生效时返回false。
        // public native boolean shouldBeInitialized(Class<?> c);
        //检测给定的类是否已经初始化。通常在获取一个类的静态属性的时候（因为一个类如果没初始化，它的静态属性也不会初始化）使用。
        // public native void ensureClassInitialized(Class<?> c);
        static void ensureClassInitialized(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass z_class = ((ZClass) args[0]);
            z_class.initialize(vm);
        }
        //定义一个类，此方法会跳过JVM的所有安全检查，默认情况下，ClassLoader（类加载器）和ProtectionDomain（保护域）实例来源于调用者
        // public native Class<?> defineClass(String name, byte[] b, int off, int len, ClassLoader loader, ProtectionDomain protectionDomain);
        //定义一个匿名类
        // public native Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches);
        ////////////////////////////////////////////////////////////


        ////////////////////////////////////////////////////////////
        // 对象操作
        //返回对象成员属性在内存地址相对于此对象的内存地址的偏移量
        // public native long objectFieldOffset(Field f);
        // 这里处理成返回第几个字段..
        static long objectFieldOffset(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject field = ((ZObject) args[0]);
            vm.check_null(field);
            return field_slot(vm, field);
        }
        //获得给定对象的指定地址偏移量的值，与此类似操作还有：getInt，getDouble，getLong，getChar等
        // public native Object getObject(Object o, long offset);
        @NativeDescriptor(name = "getInt", descriptor = "(Ljava/lang/Object;J)Ljava/lang/Object;")
        static int getInt(VM vm, ZMethod method, ZObject object, Object[] args) {
            // 注意这里把第一个字段换成 field 了
            // this.base, this.fieldOffset
            ZObject field = (ZObject) args[0];
            long offset = ((long) args[1]);

            return (Integer) getXXX(vm, args);
        }
        private static Object getXXX(VM vm, Object[] args) {
            ZClass java_lang_reflect_field = vm.load_class("java/lang/reflect/Field", false);

            ZObject obj = ((ZObject) args[0]);
            long offset = ((long) args[1]);
            if (obj instanceof ZArray) {
                // 🚨🚨🚨🚨🚨 这里当做 index
                return ((ZArray) obj).index(((int) offset));
            } else {
                ZClass z_class = obj.z_class();
                if (java_lang_reflect_field == z_class) {
                    // 🚨🚨🚨🚨🚨 这里直接当 Field 用

                    Object clazz_z_object = java_lang_reflect_field.field("clazz").get_value(obj);

                    Object clazz_name_z_object = vm.java_lang_Class.method("getName()Ljava/lang/String;")
                            .invoke((ZObject) clazz_z_object, new Object[0]);
                    String class_name = from_string(vm, (ZObject) clazz_name_z_object);

                    Object field_name_z_object = java_lang_reflect_field.field("name").get_value(obj);
                    String field_name = from_string(vm, ((ZObject) field_name_z_object));

                    ZField field = vm.load_class(class_name, true).field(field_name);
                    assert (field.access_flags() & ACC_STATIC) != 0;
                    return field.get_value(null);

                    // !! 这里的 slot 不对❌❌❌❌❌❌
                    /*
                    long offset0 = field_slot(vm, obj);
                    String field_name = z_class.fields()[((int) offset0)].name();
                    return z_class.field(field_name).get_value(obj);
                    */
                } else {
                    // 🚨🚨🚨🚨🚨 这里当做 field slot
                    String field_name = z_class.fields()[((int) offset)].name();
                    return z_class.field(field_name).get_value(obj);
                }
            }
        }
        //给定对象的指定地址偏移量设值，与此类似操作还有：putInt，putDouble，putLong，putChar等
        // public native void putObject(Object o, long offset, Object x);
        private static Object getXXXVolatile(VM vm, Object[] args) {
            // todo Volatile
            return getXXX(vm, args);
        }
        //从对象的指定偏移量处获取变量的引用，使用volatile的加载语义
        // public native Object getObjectVolatile(Object o, long offset);
        static ZObject getObjectVolatile(VM vm, ZMethod method, ZObject object, Object[] args) {
            return (ZObject) getXXXVolatile(vm, args);
        }
        // public native int getIntVolatile(java.lang.Object o, long l);
        static int getIntVolatile(VM vm, ZMethod method, ZObject object, Object[] args) {
            return (Integer) getXXXVolatile(vm, args);
        }
        //存储变量的引用到对象的指定的偏移量处，使用volatile的存储语义
        // public native void putObjectVolatile(Object o, long offset, Object x);
        //有序、延迟版本的putObjectVolatile方法，不保证值的改变被其他线程立即看到。只有在field被volatile修饰符修饰时有效
        // public native void putOrderedObject(Object o, long offset, Object x);
        //绕过构造方法、初始化代码来创建对象
        // public native Object allocateInstance(Class<?> cls) throws InstantiationException;
        static ZObject allocateInstance(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass cls = (ZClass) args[0];
            return cls.allocate();
        }
        ////////////////////////////////////////////////////////////


        ////////////////////////////////////////////////////////////
        // CAS相关 todo 用unsafe实现cas, 替换 synchronize
        // obj         包含要修改field的对象
        // offset    对象中某field的偏移量
        // expected  期望值
        // update    更新值
        // public final native boolean compareAndSwapObject(Object o, long offset,  Object expected, Object update);
        static boolean compareAndSwapObject(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject obj = ((ZObject) args[0]);
            // 🚨🚨🚨🚨🚨 这里当做 field index
            long offset = ((long) args[1]);
            Object expect = args[2];
            Object update = args[3];

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (obj) {
                if (obj instanceof ZArray) {
                    ZArray z_array = ((ZArray) obj);
                    Object value = z_array.index(((int) offset));
                    if (value == expect) {
                        z_array.index(((int) offset), update);
                        return true;
                    }
                    return false;
                } else {
                    ZClass z_class = obj.z_class();
                    String field_name = z_class.fields()[((int) offset)].name();
                    // todo 这里貌似可能需要基础类型和包装类型
                    ZField field = z_class.field(field_name);
                    if (field.get_value(obj) == expect) {
                        field.put_value(obj, update);
                        return true;
                    }
                    return false;
                }
            }
        }
        // public final native boolean compareAndSwapInt(Object o, long offset, int expected,int update);
        static boolean compareAndSwapInt(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject obj = ((ZObject) args[0]);
            // 🚨🚨🚨🚨🚨 这里当做 field index, 配合 java.lang.Class.getDeclaredFields0
            long offset = ((long) args[1]);
            int expect = ((int) args[2]);
            int update = ((int) args[3]);
            assert !(obj instanceof ZArray); // todo 木有处理数组

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (obj) {
                ZClass z_class = obj.z_class();
                String field_name = z_class.fields()[((int) offset)].name();
                ZField field = z_class.field(field_name);
                if (((int) field.get_value(obj)) == expect) {
                    field.put_value(obj, update);
                    return true;
                }
            }
            return false;
        }
        // public final native boolean compareAndSwapLong(Object o, long offset, long expected, long update);
        static boolean compareAndSwapLong(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject obj = ((ZObject) args[0]);
            // 🚨🚨🚨🚨🚨 这里当做 field index, 配合 java.lang.Class.getDeclaredFields0
            long offset = ((long) args[1]);
            long expect = ((long) args[2]);
            long update = ((long) args[3]);
            assert !(obj instanceof ZArray); // todo 木有处理数组

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (obj) {
                ZClass z_class = obj.z_class();
                String field_name = z_class.fields()[((int) offset)].name();
                ZField field = z_class.field(field_name);
                if (((long) field.get_value(obj)) == expect) {
                    field.put_value(obj, update);
                    return true;
                }
            }
            return false;
        }
        ////////////////////////////////////////////////////////////
    }

    static class sun_reflect_Reflection {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static ZClass getCallerClass(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZThread.Frame[] bt = vm.stacks.get().toArray(new ZThread.Frame[0]);
            assert bt.length >= 3;
//            // 0 是 getCallerClass
//            // 1 是获取 caller 的 frame
//            // 2 是 caller 的 class
//            // todo 需要忽略  java.lang.reflect.Method.invoke 反射调用的帧
//            // todo 需要忽略 MethodHandle 的帧
//            // todo 需要忽略其他隐藏帧
            return vm.load_class(bt[2].method.class_name(), true);
//            String class_name = bt[1].method.class_name();
//            for (int i = 2; i < bt.length; i++) {
//                String class_name1 = bt[i].method.class_name();
//                if (!class_name.equals(class_name1)) {
//                    return vm.load_class(class_name1, true);
//                }
//            }
//            throw new AssertionError();
        }
        static int getClassAccessFlags(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass z_class = ((ZClass) args[0]);
            return z_class.access_flags();
        }
    }

    static class sun_reflect_NativeMethodAccessorImpl {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // private static native Object invoke0(java.lang.reflect.Method var0, Object var1, Object[] var2);
        static Object invoke0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZObject reflect_method = ((ZObject) args[0]);
            @Nullable ZObject method_receiver = (ZObject) args[1];
            ZArray method_args = (ZArray) args[2];

            ZClass reflect_method_class = vm.load_class("java/lang/reflect/Method", true);

            StringBuilder descriptor = new StringBuilder();
            ZObject method_name = (ZObject) reflect_method_class.field("name").get_value(reflect_method);
            descriptor.append(from_string(vm, method_name)).append("(");
            ZArray param_types = (ZArray) reflect_method_class.field("parameterTypes").get_value(reflect_method);
            ZClass[] param_types_array = (ZClass[]) param_types.z_object_array();
            for (ZClass param : param_types_array) {
                descriptor.append(param.descriptor_name());
            }
            descriptor.append(")");
            ZClass return_type_class = (ZClass) reflect_method_class.field("returnType").get_value(reflect_method);
            descriptor.append(return_type_class.descriptor_name());

            int modifiers = (int) reflect_method_class.field("modifiers").get_value(reflect_method);
            ZClass receiver_class;
            if ((modifiers & ACC_STATIC) == 0) {
                vm.check_null(method_receiver);
                //noinspection ConstantConditions
                receiver_class = method_receiver.z_class();
            } else {
                receiver_class = (ZClass) reflect_method_class.field("clazz").get_value(reflect_method);
            }
            ZMethod z_method = receiver_class.method(descriptor.toString());
            Object result = z_method.invoke(method_receiver, method_args.z_object_array());
            return box(vm, result);
        }

    }

    static class sun_reflect_NativeConstructorAccessorImpl {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        // java.lang.reflect.Constructor<?> constructor, java.lang.Object[] objects
        static Object newInstance0(VM vm, ZMethod method, ZObject object, Object[] args) {
            ZClass java_lang_reflect_constructor = vm.load_class("java.lang.reflect.Constructor", false);
            ZObject constructor_object = ((ZObject) args[0]);
            ZArray constructor_object_args = ((ZArray) args[1]);

            // todo !!!   slot 的语义要全部重写
            ZField field_slot = java_lang_reflect_constructor.field("slot");
            ZField field_clazz = java_lang_reflect_constructor.field("clazz");

            // 🚨🚨🚨🚨🚨 这里 hack 了一把, 用 slot 做了构造函数位置
            // 配合 java_lang_Class.getDeclaredConstructors0 一起使用
            int idx = ((int) field_slot.get_value(constructor_object));
            ZClass declared_class = ((ZClass) field_clazz.get_value(constructor_object));
            ZMethod constructor = declared_class.method(idx);
            assert constructor.is_instance_init();

            ZObject instance = declared_class.allocate();
            if (constructor_object_args == null) {
                constructor.invoke(instance, new Object[0]);
            } else {
                constructor.invoke(instance, constructor_object_args.z_object_array());
            }

            return instance;
        }
    }

    static class java_util_concurrent_atomic_AtomicLong {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static boolean VMSupportsCS8(VM vm, ZMethod method, ZObject object, Object[] args) {
            return true;
        }
    }

    static class com_sun_net_ssl_internal_ssl_Provider {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
    }

    static class java_security_AccessController {
        static void registerNatives(VM vm, ZMethod method, ZObject object, Object[] args) { }
        static Object doPrivileged(VM vm, ZMethod method, ZObject object, Object[] args) {
            // PrivilegedAction
            ZObject act = ((ZObject) args[0]);
            // todo test
            return act.z_class().interface_method("run", "()Ljava/lang/Object;")
                    .invoke(act, new Object[0]);
        }

        // private static native AccessControlContext getStackAccessControlContext();
        static ZObject getStackAccessControlContext(VM vm, ZMethod method, ZObject object, Object[] args) {
            // 这里
            return vm.load_class("java/security/AccessControlContext", true)
                    .new_instance("([Ljava/security/ProtectionDomain;)V", new Object[] {
                            vm.load_class("java/security/ProtectionDomain", true).new_array(0)
            });
        }

        // static native AccessControlContext getInheritedAccessControlContext();
        static ZObject getInheritedAccessControlContext(VM vm, ZMethod method, ZObject object, Object[] args) {
            return null;
        }
    }

    //~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-
    //
    //~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-+-~-

    static <T extends Throwable> void sneakyThrows(Throwable e) throws T {
        //noinspection unchecked
        throw ((T) e);
    }

    static Object method_invoke0(java.lang.reflect.Method method, Object obj, Object... args) {
        try {
            return method.invoke(obj, args);
        } catch (InvocationTargetException e) {
            sneakyThrows(e.getTargetException());
        } catch (Exception e) {
            sneakyThrows(e);
        }
        return null;
    }

    static @NotNull Class<?> class_for_name0(String name, boolean initialize) {
        try {
            return Class.forName(name, initialize, Natives.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            sneakyThrows(e);
            return null;
        }
    }
}
