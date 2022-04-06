package zvm;

import org.jetbrains.annotations.NotNull;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static zvm.ClassParser.AccessFlags.ACC_ABSTRACT;
import static zvm.ClassParser.AccessFlags.ACC_STATIC;
import static zvm.ClassParser.ConstantPool.instance_init;

/**
 * @author chuxiaofeng
 *
 * JDK 的 Class.forName 的参数规则感觉不是很一致，这里重新造了一份轮子
 * 1. 支持 primitive type, java int byte 等 keywords 的概念虽然在 jvm中不存在，
 *      但是如果紧用来处理 合法 java 代码生成的 class 没问题
 * 2. 支持数组 primitive 与对象类类型数组  [I  [Ljava/lang/String;
 * 3. replace('/', '.') 则兼容 Class.forName
 * ----------------------------------------
 * {}  : 0 个或多个
 * | : 或
 * 换行 : 或
 * NAME: 合法标识符，但不包括 boolean | byte | char | short | int | long | float | double
 * ----------------------------------------
 * 文法：
 * type
 *      primitive_type
 *      reference_type
 *
 * reference_type
 *      object_type
 *      array_type
 *
 * primitive_type
 *      boolean | byte | char | short | int | long | float | double
 *
 * object_type
 *      NAME { / NAME }
 *
 * 这里不和 primitive_type 统一了, 和 Class.forName 保持一致
 * 解析起来因为 对象有 L 前缀 `;` 后缀，没歧义， 不统一的话解析也简单
 * component_primitive_type
 *      Z | B | C | S | I | J | F | D
 *
 * component_object_type
 *      L object_type ;
 *
 * component_type
 *      component_primitive_type
 *      component_object_type
 *      array_type
 *
 * array_type
 *      [ component_type
 *
 * 没有完全参照 jvmspec
 * https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.5
 */
public final class BootstrapClassLoader {

    private final VM vm;
    final Map<String, ZClass> loaded_classes = new HashMap<>();
    private ZMethod class_init_method_cache_;
    private Map<String, Object> load_lock_map_/* = new ConcurrentHashMap<>()*/;

    final ClassReader class_reader;

    BootstrapClassLoader(VM vm, String[] class_paths) {
        this.vm = vm;
        class_reader = new ClassReader(class_paths);
    }

    private Object load_class_lock(String name) {
        // name.intern()应该也可以, 但是记得 intern() 的数量有限，内部好像是个 c++实现的一个 map
        // return name.intern();
        if (load_lock_map_ == null) {
            return this; // 全局 lock
        }
        Object new_lock = new Object();
        Object lock = load_lock_map_.putIfAbsent(name, new_lock);
        return lock == null ? new_lock : lock;
    }

    public void put(@NotNull String name, @NotNull ZClass z_class) {
        loaded_classes.put(name, z_class);
    }

    public ZClass load_class(String name, boolean initialize) {
        // 应该检查 init 发生的异常，标记类加载失败
        name = name.replace('.', '/');

        synchronized (load_class_lock(name)) {
            ZClass z_class = loaded_classes.get(name);
            if (z_class == ZClass.guard_) {
                throw new AssertionError();
            }

            if (z_class == null) {
                loaded_classes.put(name, ZClass.guard_);
                z_class = load_class0(name, initialize);
                loaded_classes.put(name, z_class);

                if (z_class.is_primitive() || z_class.is_array()) {
                    if (load_lock_map_ != null) {
                        load_lock_map_.remove(name);
                    }
                    return z_class;
                }

                // todo 做 link 把 Method[] 换成正经的方法表, 先都 resolve 好....

                // 在调用 <clinit>()V 之前。
                // 类的初始化阶段会对类里的带有 ConstantValue 的 final static 变量做初始化
                z_class.init_class_fields();
            }

            // 这里可以处理开关断言断言

            if (z_class.is_primitive() || z_class.is_array()) {
                if (load_lock_map_ != null) {
                    load_lock_map_.remove(name);
                }
                return z_class;
            }

            if (initialize && !z_class.is_initialized()) {
                z_class.initialize(vm);
            }

            if (load_lock_map_ != null) {
                load_lock_map_.remove(name);
            }

            return z_class;
        }
    }

    ZClass load_class0(String name, boolean initialize) {
        if (VM.log_class_load) {
            Deque<ZThread.Frame> frames = vm.stacks.get();
            System.err.println(" [load class " + name.replace('/', '.') + "]" + " at " + frames.peek());
        }

        if (class_init_method_cache_ == null) {
            class_init_method_cache_ = vm.java_lang_Class.special_method(instance_init, "(Ljava/lang/ClassLoader;)V");
        }

        boolean is_array = name.startsWith("[");
        if (is_array) {
            // array_type
            String component_name = name.substring(1);
            char c = component_name.charAt(0);
            switch (c) {
                // component_object_type -> object_type
                case 'L': component_name = component_name.substring(1, component_name.length() - 1); break;
                // component_primitive_type -> primitive_type
                case 'Z': component_name = "boolean"; break;
                case 'B': component_name = "byte"; break;
                case 'C': component_name = "char"; break;
                case 'S': component_name = "short"; break;
                case 'I': component_name = "int"; break;
                case 'J': component_name = "long"; break;
                case 'F': component_name = "float"; break;
                case 'D': component_name = "double"; break;
                case '[': break; // array_type 这里不处理, 下面递归 load_class (component_name)
                default: throw new AssertionError();
            }
            if (c == 'L') {
                assert name.charAt(name.length() - 1) == ';';
            } else if (c == '[') {
                assert component_name.length() > 1;
            } else {
                assert name.length() == 2; // primitive type [?
            }
            // 这里不能是do_load_class, 因为 component_class 需要 init
            ZClass component_class = load_class(component_name, initialize);
            ZClass z_class = ZClass.array_class(vm, vm.java_lang_Class, component_class);
            ZClass.allocate0(vm.java_lang_Class, z_class);
            class_init_method_cache_.invoke(z_class, new Object[] { null }); // todo class_loader 参数 !!!
            return z_class;

        } else {

            // primitive_type
            /*
            switch (name) {
                case "boolean": return class_boolean;
                case "byte":    return class_byte;
                case "char":    return class_char;
                case "short":   return class_short;
                case "int":     return class_int;
                case "long":    return class_long;
                case "float":   return class_float;
                case "double":  return class_double;
            }
            */

            if (name.equals("void")) {
                name = "java/lang/Void";
            }

            // object_type
            ClassParser.ClassFile class_file = class_reader.read_class(name);

            // 规范说, 接口不用递归加载父类，这里一把梭...
//            boolean is_iface = (class_file.access_flags & ACC_INTERFACE) != 0;
            ZClass z_class;
//            if (is_iface) {
//                z_class = ZClass.object_class(this, class_class, class_file, null, new ZClass[0]);
//            } else {
            ZClass super_class = null;
            assert class_file != null;
            String super_name = class_file.super_name();
            if (super_name != null) {
                super_class = load_class(super_name, initialize);
            }
            ZClass[] interfaces = load_class0_interfaces(class_file, initialize);
            z_class = ZClass.object_class(vm, vm.java_lang_Class, class_file, super_class, interfaces);
//            }
            ZClass.allocate0(vm.java_lang_Class, z_class);
            class_init_method_cache_.invoke(z_class, new Object[] { null }); // todo class_loader 参数 !!!
            return z_class;
        }
    }

    ZClass[] load_class0_interfaces(ClassParser.ClassFile class_file, boolean initialize) {
        int[] iface_idx_arr = class_file.interfaces;
        ZClass[] interfaces = new ZClass[iface_idx_arr.length];

        for (int i = 0; i < iface_idx_arr.length; i++) {
            String iface_name = class_file.constant_pool().class_at(iface_idx_arr[i]);
            ZClass iface = load_class(iface_name, false);
            interfaces[i] = iface;
            // 规范说：只有实现了任意声明了 non-static, non-abstract 的 concrete method 方法的接口需要初始化
            // 这块逻辑不知道对不对, 如果传入 initialize 是 true, 是否直接 初始化接口, 而不用羡慕那一坨判断
            // todo 这里 || ?? &&
            if (initialize && is_interface_has_nonstatic_concrete_method(iface)) {
                ZClass iface_class = iface;
                while (iface_class != null) {
                    iface_class.initialize(vm);
                    iface_class = iface_class.super_class();
                }
            }
        }
        return interfaces;
    }

    private boolean is_interface_has_nonstatic_concrete_method(ZClass iface_class) {
        while (iface_class != null) {
            assert iface_class.is_interface();
            for (ClassParser.ClassFile.Method method : iface_class.methods()) {
                // 只有实现了任意声明了 non-static, non-abstract 的concrete method 方法的接口的情况下
                if ((method.access_flags & ACC_STATIC) == 0 && (method.access_flags & ACC_ABSTRACT) == 0) {
                    assert method.code != null; // remove
                    return true;
                }
            }
            iface_class = iface_class.super_class();
        }
        return false;
    }
}
