package zvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zvm.helper.Reflect;

import java.lang.reflect.Array;
import java.util.*;

import static zvm.ClassParser.*;
import static zvm.ClassParser.AccessFlags.*;
import static zvm.ClassParser.ConstantPool.instance_init;
import static zvm.ClassParser.Constants.JAVA_8_VERSION;

/**
 * @author chuxiaofeng
 * 可以拆成 instance_class 和 array_class
 */
final class ZClass extends ZObject {
    /**
     * 在递归加载父类、接口、class 等之前，先标记状态，缓存加载了一半的类
     * 遇到有环递归加载自己时候，先返回半成品
     * 目前没有检查递归加载，只特殊处理了 Object 与 Class 的循环依赖
     */
    static class ZClassState {
        final static int allocated = 0;
        final static int loaded = 1;
        final static int being_initialized = 2;
        final static int fully_initialized = 3;
    }
    private int init_state_;

    private final Object[] properties;
    private final @Nullable ZClass super_class;
    private final @Nullable ZClass[] interfaces;
    private final @Nullable ClassFile class_file;
    // native_class 与 component_class 可以用一个字段表示
    private final @Nullable Class<?> native_class;
    private final @Nullable ZClass component_class;
    @NotNull final FastSubtypeChecker fsc_;

    int access_flags_cache_ = -1;
    private final int instance_field_size_;
    private final int static_field_size_;
    private Map<String, ZField> field_cache_;
    private ZField[] instance_field_cache_;
    private ZField[] static_field_cache_;
    private volatile Map<String, ZMethod> methods_cache_;
    private volatile Map<String, List<ZMethod>> methods_cache0_;
    private volatile ZMethod[] methods_cache1_;
    private ZClass array_class_cache_;
    private String name_cache_;

    ZArray declared_methods0_cache_;
    ZArray declared_fields0_cache_;
    ZArray declared_constructors0_cache_;

    // todo
    // class_loader

    final static ZClass guard_ = Reflect.of(ZClass.class).allocateInstance();

    static ZClass native_class(VM vm, @NotNull ZClass z_class, Class<?> native_class) {
        return new ZClass(vm, z_class, native_class, null, null, new ZClass[0], null);
    }

    // 可以这么来理解 java 数组以及 java 数组的 class
    /*
    public class Java数组 extends Object implements java.lang.Cloneable, java.io.Serializable {
        // 注意这里override object 的 protected 的 clone 为 public
        @Override
        public Object clone() throws CloneNotSupportedException {
            return new 新数组 { 每一个元素的 clone };
        }
    }
    */
    static ZClass array_class(VM vm, @NotNull ZClass z_class, ZClass component_class) {
        // jls: 所有数组 都是 Object Cloneable Serializable 的子类型
        // 所以 array-class 的父类是 Object, 接口是 Cloneable 与 Serializable
        ZClass[] array_interfaces = new ZClass[] {
                vm.java_lang_Cloneable,
                vm.java_io_Serializable
        };
        return new ZClass(vm, z_class, null, null, vm.java_lang_Object,
                array_interfaces, component_class);
    }

    static ZClass object_class(VM vm, @NotNull ZClass z_class, @NotNull ClassFile class_file,
                               @Nullable ZClass super_class, @NotNull ZClass[] interfaces) {
        return new ZClass(vm, z_class, null, class_file, super_class,
                interfaces, null);
    }

    // vm 启动 reflect invoke 的 私有 ctor
    // java.lang.Object java.lang.Class
    private ZClass(VM vm, @NotNull ClassFile class_file) {
        super(vm, guard_);
        this.native_class = null;
        this.class_file = class_file;
        this.super_class = null;
        this.interfaces = new ZClass[0];
        this.component_class = null;
        this.init_state_ = ZClassState.allocated;
        //noinspection ConstantConditions
        this.fsc_ = null;

        String this_name = class_file.this_name();
        if (this_name.equals("java/lang/Class")) {
            instance_field_size_ = class_file.instance_field_size();
            static_field_size_ = class_file.static_field_size();
        } else {
            assert this_name.equals("java/lang/Object");
            instance_field_size_ = 0;
            static_field_size_ = 0;
        }
        properties = new Object[static_field_size_];
    }

    private ZClass(VM vm, @NotNull ZClass z_class,
           @Nullable Class<?> native_class, @Nullable ClassFile class_file,
           @Nullable ZClass super_class, ZClass[] interfaces, @Nullable ZClass component_class) {
        super(vm, z_class);
        this.native_class = native_class;
        this.class_file = class_file;
        this.super_class = super_class;
        this.interfaces = interfaces;
        this.component_class = component_class;
        this.init_state_ = ZClassState.loaded;
        this.fsc_ = new FastSubtypeChecker(this);

        if (class_file != null) {
            int super_instance_field_size = (super_class == null ? 0 : super_class.instance_field_size_);
            instance_field_size_ = class_file.instance_field_size() + super_instance_field_size;
            int super_static_field_size = (super_class == null ? 0 : super_class.static_field_size_);
            static_field_size_ = class_file.static_field_size() + super_static_field_size;
            properties = new Object[class_file.static_field_size()];
        } else {
            instance_field_size_ = -1;
            static_field_size_ = -1;
            properties = null;
        }
    }

    private Map<String, ZField> field_cache_() {
        if (field_cache_ == null) {
            assert !is_array() && !is_primitive();
            int this_instance_field_size;
            int this_static_field_size;
            int instance_slot_start_pos;
            int static_slot_start_pos;
            if (super_class == null) {
                this_static_field_size = static_field_size_;
                this_instance_field_size = instance_field_size_;
                instance_slot_start_pos = 0;
                static_slot_start_pos = 0;
            } else {
                this_static_field_size = static_field_size_ - super_class.static_field_size_;
                this_instance_field_size = instance_field_size_ - super_class.instance_field_size_;
                instance_slot_start_pos = super_class.instance_field_size_;
                static_slot_start_pos = super_class.static_field_size_;
            }
            field_cache_ = new HashMap<>();
            instance_field_cache_ = new ZField[this_instance_field_size];
            static_field_cache_ = new ZField[this_static_field_size];
            ClassFile.Field[] c_fields = fields();
            int i = 0, j = 0;
            for (ClassFile.Field c_field : c_fields) {
                int slot;
                boolean is_static = (c_field.access_flags & ACC_STATIC) != 0;
                if (is_static) {
                    slot = static_slot_start_pos + i++;
                } else {
                    slot = instance_slot_start_pos + j++;
                }
                ZField z_field = new ZField(this, c_field, slot);
                field_cache_.put(c_field.name(), z_field);
                if (is_static) {
                    static_field_cache_[i - 1] = z_field;
                } else {
                    instance_field_cache_[j - 1] = z_field;
                }
            }
        }
        return field_cache_;
    }

//    private ZField[] field_cache0_() {
//        if (field_cache0_ == null) {
//            field_cache_();
//            int instance_slot_start_pos = super_class == null ? 0 : super_class.instance_field_size_;
//            int static_slot_start_pos = super_class == null ? 0 : super_class.static_field_size_;
//            field_cache0_ = new ZField[fields().length];
//            field
//
//        }
//        return field_cache0_;
//    }

    // properties 数组下标 或者 field 的 slot
    ZField instance_field(int slot, boolean absolute) {
        assert super_class != null;
        return instance_field_cache_[absolute ? slot - super_class.instance_field_size_ : slot];
    }

    ZField static_field(int slot, boolean absolute) {
        assert super_class != null;
        return static_field_cache_[absolute ? slot - super_class.static_field_size_ : slot];
    }

    Object get_static_field(int slot) {
        // slot 一定是 field 声明类的 slot, 所以不用判断
        assert super_class != null;
        // 类属性保存在各自的类里头, 所有要根据 slot 做一下分发, 实例属性不需要
//        if (slot >= super_class.static_field_size_) {
            // 子类访问自己的静态属性
            return properties[slot - super_class.static_field_size_];
//        } else {
            // 子类访问超类的静态属性
//            return super_class.get_static_field(slot);
//        }
    }

    void put_static_field(int slot, Object value) {
        // slot 一定是 field 声明类的 slot, 所以不用判断
        assert super_class != null;
//        if (slot >= super_class.static_field_size_) {
            properties[slot - super_class.static_field_size_] = value;
//        } else {
//            super_class.put_static_field(slot, value);
//        }
    }

    int instance_field_size() {
        return instance_field_size_;
    }

    boolean is_initialized() {
        return init_state_ >= ZClassState.being_initialized;
    }

    void initialize(VM vm) {
        if (is_initialized()) {
            return;
        }

        String name = name();

        // 🚨🚨🚨🚨🚨 hack
        {
            if (name.equals("java/lang/ref/Reference")) {
                return;
            }
            if (name.equals("sun/misc/Unsafe")) {
                init_state_ = ZClassState.fully_initialized; // 阻止 Stack Overflow
                field("theUnsafe").put_value(null, vm.load_class("sun/misc/Unsafe").new_instance());
                return;
            }
        }

        init_state_ = ZClassState.being_initialized;

        ZMethod clinit = method("<clinit>()V");
        if (clinit != null) {
            clinit.invoke(null, new Object[0]);
        }

        init_state_ = ZClassState.fully_initialized;
    }

    static String array_name(VM vm, ZClass component_class) {
        String component_name;
        if (component_class.is_array()) {
            component_name = component_class.name();
        } else if (component_class.is_primitive()){
            if (component_class == vm.class_boolean) component_name = "Z";
            else if (component_class == vm.class_byte) component_name = "B";
            else if (component_class == vm.class_short) component_name = "S";
            else if (component_class == vm.class_char) component_name = "C";
            else if (component_class == vm.class_int) component_name = "I";
            else if (component_class == vm.class_long) component_name = "J";
            else if (component_class == vm.class_float) component_name = "F";
            else if (component_class == vm.class_double) component_name = "D";
            else throw new AssertionError();
        } else {
            component_name = "L" + component_class.name() + ";";
        }
        return "[" + component_name;
    }

    String descriptor_name() {
        if (is_primitive()) {
            if (this == vm.class_boolean) return "Z";
            if (this == vm.class_byte) return "B";
            if (this == vm.class_char) return "C";
            if (this == vm.class_short) return "S";
            if (this == vm.class_int) return "I";
            if (this == vm.class_long) return "J";
            if (this == vm.class_float) return "F";
            if (this == vm.class_double) return "D";
            throw new AssertionError();
        } else if (is_array()) {
            return "[" + component_class().descriptor_name();
        } else {
            if (this == vm.java_lang_Void) return "V"; // todo
            return "L" + name().replace('.', '/') + ";";
        }
    }

    String name() {
        if (name_cache_ == null) {
            name_cache_ = name0();
        }
        return name_cache_;
    }

    private String name0() {
        if (is_array()) {
            return array_name(vm, component_class);
        } else if (is_primitive()) {
            return native_class.getName();
        } else {
            assert class_file != null;
            return class_file.this_name();
        }
    }

    /**
     * interface A {} interface B {} interface C extends A,B {}
     * c.super_class == null
     * c.interface == [A, B]
     * 一个疑问 🐷 jdk 反射接口的 superClass 为啥返回 null
     * 但特么 jls 子类型规范说明，Object 是无直接超接口 的 接口的超类
     * 接口编译完的 class 文件中，super 也指向 java/lang/Object
     */
    @Nullable ZClass super_class() {
        if (is_interface()) {
            return null;
        } else {
            return super_class;
        }
    }

    ZClass[] interfaces() {
        return interfaces;
    }

    boolean is_primitive() {
        return native_class != null;
    }

    Class<?> native_class() {
        return native_class;
    }

    boolean is_array() {
        return component_class != null;
    }

    ZClass component_class() {
        assert is_array();
        return component_class;
    }

    boolean is_interface() {
        return class_file != null
                && (class_file.access_flags & ACC_INTERFACE) == ACC_INTERFACE;
    }

    int access_flags() {
        assert class_file != null;
        if (access_flags_cache_ == -1) {
            access_flags_cache_ = access_flags0();
        }
        return access_flags_cache_;
    }

    // inner class 要特殊处理...
    private int access_flags0() {
        String name = name();
        assert class_file != null;
        if (class_file.inner_classes != null) {
            for (ClassFile.InnerClass inner_class : class_file.inner_classes) {
                assert inner_class != null;
                if (name.equals(inner_class.inner_class_info())) {
                    return inner_class.inner_class_access_flags() & (~ACC_SUPER);
                }
            }
        }
        return class_file.access_flags & (~ACC_SUPER);
    }

    // 当前类的字段, 不包括父类
    ClassFile.Field[] fields() {
        if (class_file == null) {
            return new ClassFile.Field[0];
        } else {
            return class_file.fields;
        }
    }

    int major_version() {
        if (class_file == null) {
            return -1;
        } else {
            return class_file.major_version;
        }
    }

    ZClass declaring_class() {
        if (class_file == null) {
            return null;
        } else {
            String name = class_file.host_class();
            if (name == null) {
                return null;
            } else {
                return vm.load_class(name, false);
            }
        }
    }

    Object[] enclosing_method() {
        if (class_file == null) {
            return null;
        } else {
            ClassFile.EnclosingMethod m = class_file.enclosing_method;
            if (m == null) {
                return null;
            } else {
                ConstantPool.NameAndType nt = m.method();
                Object[] arr = new Object[3];
                arr[0] = vm.load_class(m.class_name(), false); // enclosingClass
                arr[1] = nt.name;
                arr[2] = nt.descriptor;
                return arr;
            }
        }
    }

    // todo 重新实现，参照 field resolve 逻辑....
    /**
     * todo IllgalAccessError
     * 字段解析逻辑参见 JLS 5.4.3.2
     */
    @NotNull ZField field(String name) {
        @Nullable ZField field = field0(name);
        if (field == null) {
            ZObject no_such_field = z_class.vm.load_class("java/lang/NoSuchFieldError")
                    .new_instance("(Ljava/lang/String;)V", new Object[]{
                            Natives.new_string(z_class.vm, name)
                    });
            throw new ZThrowable(no_such_field);
        }
        return field;
    }

    @Nullable ZField field0(String name) {
        assert !is_array();
        ZField field = field_cache_().get(name);
        if (field != null) {
            return field;
        }

        // todo 测试类字段访问
        if (interfaces.length != 0) {
            for (ZClass iface : interfaces) {
                assert iface != null;
                ZField iface_field = iface.field0(name);
                if (iface_field != null) {
                    return iface_field;
                }
            }
        }

        if (super_class != null) {
            return super_class.field0(name);
        }

        return null;
    }

    /*private*/ClassFile.Method[] methods() {
        if (class_file == null) {
            return new ClassFile.Method[0];
        } else {
            ensure_cache_initialized();
            return class_file.methods;
        }
    }

    ZMethod method(int slot) {
        ensure_cache_initialized();
        return methods_cache1_[slot];
    }

    private List<ZMethod> method_by_name(String name) {
        //noinspection unchecked
        return ((List<ZMethod>) method0(name, true));
    }

    // 父类子类 method[] 同名方法下标相同....
    ZMethod method(String descriptor) {
        return ((ZMethod) method0(descriptor, false));
    }

    // 父类子类 method[] 同名方法下标相同....
    private Object method0(String key, boolean by_name) {
        ensure_cache_initialized();
        if (by_name) {
            return methods_cache0_.get(key);
        } else {
            return methods_cache_.get(key);
        }
    }

    private void ensure_cache_initialized() {
        if (methods_cache_ == null) {
            synchronized (this) {
                if (methods_cache_ == null) {
                    init_method_cache();
                }
            }
        }
    }

    private void init_method_cache() {
        methods_cache_ = new HashMap<>();
        methods_cache0_ = new HashMap<>();

        // todo 🚨🚨🚨🚨🚨 暂时 hack 一下, 等做了 link 之后加载 array class 之后就把方法连接好
        if (is_array()) {
            methods_cache1_ = new ZMethod[1];
            String clone_descriptor = "clone()Ljava/lang/Object;";
            ZMethod method = vm.load_class("zvm/Natives$array_class").method(clone_descriptor);
            assert method != null;
            // 这里约等于 native 方法直接在代码里头链接 !!!
            // native 方法两种链接方式, 一种是按约定名字写在 native.java 中, 一种是这么搞...
            ZMethod z_clone = new ZMethod(this, method.class_method(), (vm0, method0, object0, args0) -> {
                assert object0 != null && object0.z_class.is_array();
                return ((ZArray) object0).clone0();
            }) {
                @Override
                String class_name() {
                    return ZClass.this.name();
                }
            };
            methods_cache_.put(clone_descriptor, z_clone);

            List<ZMethod> methods = new ArrayList<>(1);
            methods.add(z_clone);
            methods_cache0_.put("clone", methods);
            methods_cache1_[0] = z_clone;

            // 数组的其他方法继承 java/lang/Object, cache.get() return null 会从 Object 继续查找
        } else {
            ClassFile.Method[] methods = methods();
            methods_cache1_ = new ZMethod[methods.length];
            for (int i = 0; i < methods.length; i++) {
                ClassFile.Method method = methods[i];
                ZMethod z_method = new ZMethod(this, method);
                ZMethod old = methods_cache_.put(method.name() + method.descriptor(), z_method);
                assert old == null;

                methods_cache0_.computeIfAbsent(method.name(), k -> new ArrayList<>(1)).add(z_method);
                methods_cache1_[i] = z_method;
            }
        }
    }

    ZMethod virtual_method(String name, String descriptor) {
        return resolve_method(name, descriptor);
    }

    ZMethod interface_method(String name, String descriptor) {
        return resolve_method(name, descriptor);
    }

    ZMethod special_method(String name, String descriptor) {
        ZMethod method = method(name + descriptor);
        // invoke_special 的一种情况，子类构造函数 super.xxx 父类方法
        if (method == null && super_class != null) {
            method = super_class.special_method(name, descriptor);
        }
        assert method != null;
        return method;
    }

    ZMethod static_method(String name, String descriptor) {
        ZMethod z_method = resolve_method(name, descriptor);
        assert (z_method.access_flags() & ACC_STATIC) != 0;
        assert (z_method.access_flags() & ACC_ABSTRACT) == 0;
        assert !z_method.is_instance_init() && !z_method.is_class_init();
        // 放这里不合适, 原因是 invoke_static 字节码需要静态方法被初始化而不是获取时候
        // z_method.declared_class().initialize(vm);
        return z_method;
    }

    private ZMethod resolve_method(String name, String descriptor) {
        ZMethod method = resolve_method(this, name, descriptor);
        assert method != null;
        // 接口静态方法不能为 native, 这里应该不会获取接口的虚方法吧...
        assert !is_interface() || (method.access_flags() & ACC_STATIC) != 0;
        return method;
    }

//    旧的错误实现
//    private static @Nullable ZMethod resolve_method(ZClass z_class, String name, String descriptor) {
//        if (z_class.name().equals("zvm/test/thirdparty/Test_Invoke$TestInterfaceFirstTestClass")) {
//            System.out.println();
//        }
//        ZMethod this_method = z_class.method(name + descriptor);
//        if (this_method != null) {
//            return this_method;
//        }
//
//        // 如果 z_class 是接口, 则先查找 Object 方法 再查找父接口
//        if (z_class.super_class_ != null) {
//            ZMethod super_method = resolve_method(z_class.super_class_, name, descriptor);
//            if (super_method != null) {
//                return super_method;
//            }
//        }
//
//        if (z_class.interfaces_.length > 0) {
//            for (ZClass iface : z_class.interfaces_) {
//                assert iface != null;
//                ZMethod iface_method = resolve_method(iface, name, descriptor);
//                if (iface_method != null) {
//                    return iface_method;
//                }
//            }
//        }
//
//        return null;
//    }


    // method lookup
    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.4.3.3
    private static @Nullable ZMethod resolve_method(ZClass z_class, String name, String descriptor) {
        if (z_class.is_interface()) {
            return resolve_interface_method(z_class, name, descriptor);
        } else {
            return resolve_class_method(z_class, name, descriptor);
        }
    }

    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.4.3.3
    private static @Nullable ZMethod resolve_class_method(ZClass z_class, String name, String descriptor) {
        assert !z_class.is_interface();
        ZMethod z_method = resolve_class_method_step2(z_class, name, descriptor);
        if (z_method != null) {
            return z_method;
        }
        return resolve_class_method_step3(z_class, name, descriptor);
    }

    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.4.3.4
    private static @Nullable ZMethod resolve_interface_method(ZClass z_class, String name, String descriptor) {
        assert z_class.is_interface();
        ZMethod this_method = z_class.method(name + descriptor);
        if (this_method != null) {
            return this_method;
        }

        assert z_class.super_class == z_class.vm.java_lang_Object;
        ZMethod object_method = z_class.super_class.method(name + descriptor);
        if (object_method != null) {
            int acc = object_method.access_flags();
            if ((acc & ACC_PUBLIC) != 0 && (acc & ACC_STATIC) == 0) {
                return object_method;
            }
        }

        return resolve_class_method_step3(z_class, name, descriptor);
    }

    // C and its superclasses
    private static @Nullable ZMethod resolve_class_method_step2(ZClass z_class, String name, String descriptor) {
        if (z_class.major_version() >= JAVA_8_VERSION) {
            List<ZMethod> z_methods = z_class.method_by_name(name);

            // 有仅名字相等的 signature_polymorphic 方法一个
            if (z_methods != null && z_methods.size() == 1 && z_methods.get(0).is_signature_polymorphic()) {
                // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.4.3.1
                // todo: resolve 参数返回值类型, 加入一个 resolve 方法然后改下这里
                // Descriptor.parameter_types(descriptor);
                // Descriptor.return_type(descriptor);
                return z_methods.get(0);
            }
        }

        ZMethod this_method = z_class.method(name + descriptor);
        if (this_method != null) {
            return this_method;
        }

        if (z_class.super_class == null) {
            return null;
        } else {
            return resolve_class_method_step2(z_class.super_class, name, descriptor);
        }
    }

    // superinterfaces of the specified class C:
    private static @Nullable ZMethod resolve_class_method_step3(ZClass z_class, String name, String descriptor) {
        if (z_class.interfaces.length > 0) {
            Set<ZMethod> z_methods = maximally_specific_superinterface_methods(name, descriptor, z_class);
            if (z_methods.size() == 1) {
                ZMethod[] methods = new ZMethod[1];
                z_methods.toArray(methods);
                if ((methods[0].access_flags() & ACC_ABSTRACT) == 0) {
                    return methods[0];
                }
            }

            if (z_methods.isEmpty()) {
                return null;
            } else {
                // !!! 有多个的话随意选择了 ... arbitrarily chosen
                throw new AssertionError(); // todo remove
                // return z_methods.iterator().next();
            }
        } else {
            return null;
        }
    }

    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.4.3.3
    private static Set<ZMethod> maximally_specific_superinterface_methods(String name, String descriptor, ZClass z_class) {
        // interface => list of sub interfaces
        Map<ZClass, Set<ZClass>> interface_map = new HashMap<>();

        Set<ZClass> superinterfaces = new HashSet<>();
        while (z_class != null) {
            superinterfaces.addAll(Arrays.asList(z_class.interfaces));
            z_class = z_class.super_class;
        }

        collect_superinterfaces(interface_map, null, superinterfaces.toArray(new ZClass[0]));
        return maximally_specific_superinterface_methods0(interface_map, name, descriptor, interface_map.keySet());
    }

    private static Set<ZMethod> maximally_specific_superinterface_methods0(Map<ZClass, Set<ZClass>> interface_map,
                                                                            String name, String descriptor, Set<ZClass> superinterfaces) {
        Set<ZMethod> found = new HashSet<>();
        for (ZClass superinterface : superinterfaces) {
            ZMethod superinterface_method = superinterface.method(name + descriptor);
            if (superinterface_method != null) {
                int acc = superinterface_method.access_flags();
                if ((acc & ACC_PRIVATE) == 0 && (acc & ACC_STATIC) == 0) {
                    Set<ZClass> subinterfaces = interface_map.get(superinterface);
                    Set<ZMethod> subinterface_method;
                    // 木有子接口或者一堆子接口里头没找到
                    if (subinterfaces.isEmpty() || ((subinterface_method = maximally_specific_superinterface_methods0(
                            interface_map, name, descriptor, subinterfaces)).isEmpty())) {
                        found.add(superinterface_method);
                    } else {
                        found.addAll(subinterface_method);
                    }
                }
            }
        }
        return found;
    }

    private static void collect_superinterfaces(Map<ZClass, Set<ZClass>> superinterfaces,
                                                ZClass sub_interface, ZClass[] interfaces) {
        for (ZClass iface : interfaces) {
            Set<ZClass> subs = superinterfaces.computeIfAbsent(iface, k -> new HashSet<>());
            if (sub_interface != null) {
                subs.add(sub_interface);
            }
            if (iface.interfaces.length > 0) {
                collect_superinterfaces(superinterfaces, iface, iface.interfaces);
            }
        }
    }

    ZClass array_class() {
        if (array_class_cache_ == null) {
            array_class_cache_ = vm.load_class(array_name(vm, this), false);
        }
        return array_class_cache_;
    }

    ZArray new_array(Object array) {
        if (array == null) {
            return null;
        }
        assert array.getClass().isArray();
        return new ZArray(vm, array_class(), array);
    }

    ZArray new_array(int array_length) {
        if (is_primitive()) {
            // 基础类型数组有默认值
            return new ZArray(vm, array_class(), Array.newInstance(native_class, array_length));
        } else {
            return new ZArray(vm, array_class(), new ZObject[array_length]);
        }
    }

    // 注意 new_multi_array this 不是 component_class
    ZArray new_multi_array(int[] dimensions) {
        assert dimensions.length > 0;
        return new_array0(this, dimensions, 0);
    }

    ZArray new_array0(ZClass array_class, int[] dimensions, int idx) {
        int dimension = dimensions[idx];
        boolean last_dimension = idx == dimensions.length - 1;
        if (last_dimension) {
            return array_class.component_class().new_array(dimension);
        } else {
            ++idx;
            ZArray[] array = new ZArray[dimension];
            for (int i = 0; i < dimension; i++) {
                array[i] = new_array0(array_class.component_class(), dimensions, idx);
            }
            return new ZArray(vm, array_class, array);
        }
    }

    ZObject new_instance() {
        initialize(vm);
        ZObject z_object = allocate();
        ZMethod constructor = special_method(instance_init, "()V");
        constructor.invoke(z_object, new Object[0]);
        if (vm.load_class("java/lang/Throwable", false).is_assignable_from(this)) {
            // todo 填充堆栈
        }
        return z_object;
    }

    // todo 手写的 desc 修改成自动生成???
    ZObject new_instance(String descriptor, Object[] args) {
        ZObject z_object = allocate();
        ZMethod constructor = special_method(instance_init, descriptor);
        constructor.invoke(z_object, args);
        return z_object;
    }

    // 不包括dup invokespecial 调用 <init> 的步骤
    ZObject allocate() {
        assert !is_interface();
        assert this != vm.java_lang_Class;
        ZObject z_object = new ZObject(vm, this);
        return allocate0(this, z_object);
    }

    static <T extends ZObject > T allocate0(ZClass z_class, T z_object) {
        while (z_class != null) {
            assert !z_class.is_interface();
            z_class.init_instance_field(z_object);
            z_class = z_class.super_class;
        }
        return z_object;
    }

    void init_class_fields() {
        assert class_file != null;
        for (ClassFile.Field field : fields()) {
            int access_flags = field.access_flags;
            boolean is_static = (access_flags & ACC_STATIC) != 0;
            boolean is_final = (access_flags & ACC_FINAL) != 0;
            int cvi = field.constant_value_index;
            /*
              包括 public final static String str = "literal";
              包括 public final static int i = 42;
              不包括 public static final int i = new Integer(1);
              不包括 public final static String str = new String("literal");
             */
            if (is_static) {
                if (is_final) {
                    if (cvi != -1) {
                        init_class_const_field(field);
                    }
                } else {
                    init_primitive_field(field, null);
                }
            }
        }
    }

    void init_instance_field(ZObject z_object) {
        for (ClassFile.Field field : fields()) {
            if ((field.access_flags & ACC_STATIC) != 0) {
                continue;
            }
            // final 字段这里不初始化, construct 初始化
            if ((field.access_flags & ACC_FINAL) != 0) {
                continue;
            }
            assert field.constant_value_index == -1;
            init_primitive_field(field, z_object);
        }
    }

    void init_class_const_field(ClassFile.Field field) {
        assert class_file != null;
        ConstantPool cp = class_file.constant_pool();
        String field_name = field.name();
        ZField z_field = field(field_name);

        // 这里不能用 z_field.put_value, 要绕过 实例 final 字段必须在 <init> 赋值的限制
        char sig = field.descriptor().charAt(0);
        int cvi = field.constant_value_index;
        switch (sig) {
            case 'Z': z_field.put_value(null, cp.boolean_at(cvi)); break;
            case 'B': z_field.put_value(null, cp.byte_at(cvi)); break;
            case 'C': z_field.put_value(null, cp.char_at(cvi)); break;
            case 'S': z_field.put_value(null, cp.short_at(cvi)); break;
            case 'I': z_field.put_value(null, cp.int_at(cvi)); break;
            case 'J': z_field.put_value(null, cp.long_at(cvi)); break;
            case 'F': z_field.put_value(null, cp.float_at(cvi)); break;
            case 'D': z_field.put_value(null, cp.double_at(cvi)); break;
            default:
                if (field.descriptor().equals("Ljava/lang/String;")) {
                    // https://stackoverflow.com/questions/5777131/java-string-intern-and-literal
                    // All literal strings and string-valued constant expressions are interned.
                    // 字面量和常量都要放在常量池
                    z_field.put_value(null, Natives.new_intern_string(vm, cp.string_at(cvi)));
                } else {
                    throw new AssertionError();
                }
        }
    }

    void init_primitive_field(ClassFile.Field field, ZObject z_object) {
        assert class_file != null;
        String field_name = field.name();
        ZField z_field = field(field_name);
        char sig = field.descriptor().charAt(0);
        switch (sig) {
            case 'Z': z_field.put_value(z_object, false); break;
            case 'B': z_field.put_value(z_object, (byte) 0); break;
            case 'C': z_field.put_value(z_object, (char) 0); break;
            case 'S': z_field.put_value(z_object, (short) 0); break;
            case 'I': z_field.put_value(z_object, 0); break;
            case 'J': z_field.put_value(z_object, 0L); break;
            case 'F': z_field.put_value(z_object, 0.0F); break;
            case 'D': z_field.put_value(z_object, 0.0D); break;
            // 这里去掉，还省内存
            // default: z_field.put_value(z_object, null); break;
        }
    }

    /**
     * 测试 subtype.cast(this.class)
     * this :> subtype
     *      this is subtype
     *      this is super class|iface of subtype
     *      subtype is or sub class or impl of this
     *
     * https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html#jls-4.10
     * java 的子类型
     *      S is a proper supertype of T, written S > T, if S :> T and S ≠ T.
     *      T is a proper subtype of S, written T < S, if T <: S and S ≠ T.
     *      T is a direct subtype of S, written T <1 S, if S >1 T.
     *      Subtyping does not extend through parameterized types: T <: S does not imply that C<T> <: C<S>.
     *
     * https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html#jls-4.10.2
     * "The supertypes of a type are obtained by reflexive and transitive closure over the direct supertype relation"
     * !!! 自反传递 !!!
     * Given a non-generic type declaration C, the direct supertypes of the type C are all of the following:
     * The direct superclass of C (§8.1.4).
     * The direct superinterfaces of C (§8.1.5).
     * The type Object, if C is an interface type with no direct superinterfaces (§9.1.3).
     * Given a generic type declaration C<F1,...,Fn> (n > 0), the direct supertypes of the raw type C (§4.8) are all of the following:
     *
     * https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html#jls-4.10.3
     * 数组类型的子类型
     * The following rules define the direct supertype relation among array types:
     *      If S and T are both reference types, then S[] >1 T[] iff S >1 T.
     *      Object >1 Object[]
     *      Cloneable >1 Object[]
     *      java.io.Serializable >1 Object[]
     *      If P is a primitive type, then:
     *          Object >1 P[]
     *          Cloneable >1 P[]
     *          java.io.Serializable >1 P[]
     */
    boolean is_assignable_from(ZClass subtype) {
        if (VM.fast_is_subtype_of) {
            return fsc_.fast_is_assignable_from(subtype);
        } else {
            return is_assignable_from0(subtype);
        }
    }

    private boolean is_assignable_from0(ZClass subtype) {
        // ↓ fast-routines ↓

        vm.check_null(subtype);
        // if (subtype == null) { return false; }

        ZClass supertype = this;
        if (supertype == subtype) {
            return true;
        }

        // 如果是基础类型，必须精确相等
        if (supertype.is_primitive() || subtype.is_primitive()) {
            return false;
        }

        // 除了 null 和 基础类型，所有玩意都是对象
        // if (supertype == vm.java_lang_Object) { return true; }

        return supertype.is_assignable_from1(subtype);
    }

    private boolean is_assignable_from1(ZClass subtype) {
        ZClass supertype = this;
        assert supertype != subtype;
        assert subtype != null;
        // assert supertype != vm.java_lang_Object;
        assert !supertype.is_primitive();
        assert !subtype.is_primitive();

        // java 数组协变, 坑爹玩意...
        if (supertype.is_array() && subtype.is_array()) {
            assert supertype.component_class != null;
            return supertype.component_class.is_assignable_from(subtype.component_class);
        }

        // array 不是任何非 array 的超类
        if (supertype.is_array()) {
            return false;
        }

        if (subtype.is_array()) {
            // 理论上这里也要老老实实检查 subtype.interfaces_ 和 subtype.super_class_
            // 但是 jsl 规定死了: 所有数组 都是 Object Cloneable Serializable 的子类型
            // 就不用费劲了...
            if (supertype == vm.java_lang_Cloneable) {
                return true;
            }
            if (supertype == vm.java_io_Serializable) {
                return true;
            }
            return supertype == vm.java_lang_Object;
        }

        // 都不是数组的话...

        // 除 Object 以外, 对象不是任何接口的超类
        if (!supertype.is_interface() && subtype.is_interface()) {
            return supertype == vm.java_lang_Object;
        }

        // 以下是传递性, 懒得 while + stack了，直接递归好了...
        // 话说传递性用递归表达恰到好处...

        // 注意这里排除掉 interface 的 super_class
        if (subtype.super_class() != null) {
            if (supertype.is_assignable_from(subtype.super_class())) {
                return true;
            }
        }
        for (ZClass iface : subtype.interfaces) {
            if (supertype.is_assignable_from(iface)) {
                return true;
            }
        }
        return false;
    }

    boolean is_instance(VM vm, boolean must_be_primitive, Object value) {
        if (value == null) {
            return false;
        }
        return is_assignable_from(type_of(vm, must_be_primitive, value));
    }

    // value 是否能 cast 成 this_class
    // 兼容类基础类型 vm 内部的转换
    /*private */boolean type_check(Object value) {
        if (value == null) {
            return !is_primitive();
        } else {
            if (is_primitive()) {
                Class<?> native_class = native_class();
                Class<?> value_class = value.getClass();
                if (native_class == boolean.class) {
                    return value_class == Boolean.class || value_class == Integer.class;
                } else if (native_class == byte.class) {
                    return value_class == Byte.class || value_class == Integer.class;
                } else if (native_class == char.class) {
                    return value_class == Character.class || value_class == Integer.class;
                } else if (native_class == short.class) {
                    return value_class == Short.class || value_class == Integer.class;
                } else if (native_class == int.class) {
                    return value_class == Integer.class;
                } else if (native_class == long.class) {
                    return value_class == Long.class;
                } else if (native_class == float.class) {
                    return value_class == Float.class;
                } else if (native_class == double.class) {
                    return value_class == Double.class;
                } else {
                    throw new AssertionError();
                }
            } else {
                if (value instanceof ZObject) {
                    return is_assignable_from(((ZObject) value).z_class);
                } else {
                    // 这里是为了处理 基础类型装箱
//                    Class<?> value_class = value.getClass();
//                    if (value_class == Boolean.class) {
//                    } else if (value_class == Byte.class) {
//                    } else if (value_class == Character.class) {
//                    } else if (value_class == Short.class) {
//                    } else if (value_class == Integer.class) {
//                    } else if (value_class == Long.class) {
//                    } else if (value_class == Float.class) {
//                    } else if (value_class == Double.class) {
//                    } else {
                        throw new AssertionError();
//                    }
//                    return true;
                }
            }
        }
    }

    static ZClass type_of(VM vm, boolean must_be_primitive, @NotNull Object value) {
        // !(value instanceof ZObject) 是为了处理 基础类型装箱
        if (must_be_primitive/* || !(value instanceof ZObject)*/) {
            if (value instanceof Boolean) { return vm.class_boolean; }
            if (value instanceof Byte) { return vm.class_byte; }
            if (value instanceof Character) { return vm.class_char; }
            if (value instanceof Short) { return vm.class_short; }
            if (value instanceof Integer) { return vm.class_int; }
            if (value instanceof Long) { return vm.class_long; }
            if (value instanceof Float) { return vm.class_float; }
            if (value instanceof Double) { return vm.class_double; }
            throw new AssertionError();
        } else {
            return ((ZObject) value).z_class;
        }
    }

    static Class<?> type_of_primitive(Object value) {
        if (value == null) {
            return null;
        }
        Class<?> vc = value.getClass();
        boolean is_primitive = vc == Boolean.class
                || vc == Byte.class
                || vc == Character.class
                || vc == Short.class
                || vc == Integer.class
                || vc == Long.class
                || vc == Float.class
                || vc == Double.class;
        if (is_primitive) {
            try {
                return (Class<?>) vc.getDeclaredField("TYPE").get(null);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                Natives.sneakyThrows(e);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        if (is_primitive()) {
            return native_class.toString();
        } else if (is_array()) {
            return component_class.toString() + "[]";
        } else {
            assert class_file != null;
            return class_file.toString().replace('/', '.');
        }
    }
}
