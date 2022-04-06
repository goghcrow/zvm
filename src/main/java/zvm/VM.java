package zvm;

import zvm.ClassParser.ClassFile;
import zvm.helper.Reflect;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static zvm.ClassParser.AccessFlags.*;
import static zvm.ClassParser.ConstantPool.instance_init;
import static zvm.Natives.*;

/**
 * ZVM - Zero VM
 * @author chuxiaofeng
 *
 * 简陋的用来说明字节码如何解释执行的玩具
 *  1. 木有处理多线程
 *  2. 木有处理 classloader
 *  3. 只做了最基础的异常支持
 *
 * 类型实现
 * 复用宿主 jvm 的  null + 基础类型
 * 🐶 不能复用宿主的数组类型 💥💥💥 😈😈😈
 *  1. 因为 java 数组协变, 需要存储运行时类型, 进行类型检查
 *  2. 数组的 instanceof 检查, 需要获取运行时实际类型
 * 因为装箱原因：宿主Boolean Byte Char Short Integer Long Float Double 可以直接认为是基本类型
 *
 * Type:
 *      PrimitiveType
 *      ReferenceType
 * PrimitiveType:
 *      boolean | byte | char | short | int | long | float | double
 * ReferenceType:
 *      null | ObjectType | ArrayType
 * ObjectType:
 *      zvm.VM.ZObject
 * ArrayType:
 *      zvm.VM.ZArray
 */
public class VM {
    int x = 0;

    // 一些弱智的 debug 参数
    // 另外发现使用 Instrumentation 借助宿主 jvm 获取 class file 比直接读文件要快3倍
    // ??? 要么宿主 jvm 已经加载好了许多类, 直接从内存读取字节码, 要么是 c++ 读取 jar 特殊处理了
    final static boolean log_invoke = false;
    final static boolean log_class_load = false;
    final static boolean fast_is_subtype_of = true;

    final boolean initialized;
    final ThreadLocal<Map<String, FileInputStream>> open_files = ThreadLocal.withInitial(HashMap::new);
    final BootstrapClassLoader bootstrap_class_loader;
    final Map<String, Map<String, Object/*Invokable*/>> natives = new ConcurrentHashMap<>();
    final Map<String, ZObject> intern_strings = new ConcurrentHashMap<>();
    final ThreadLocal<Deque<ZThread.Frame>> stacks = ThreadLocal.withInitial(ArrayDeque::new);
    final InlineCache inline_cache_ = new InlineCache();
    private final ZClass[] primitive_class_cache_;

    final ZClass class_boolean;
    final ZClass class_byte;
    final ZClass class_char;
    final ZClass class_short;
    final ZClass class_int;
    final ZClass class_long;
    final ZClass class_float;
    final ZClass class_double;
    final ZClass class_void;
    final ZClass java_lang_Object;
    final ZClass java_lang_Class;
    final ZClass java_lang_System;
    // final ZClass java_lang_ClassLoader;

    final ZClass java_lang_Boolean;
    final ZClass java_lang_Byte;
    final ZClass java_lang_Character;
    final ZClass java_lang_Short;
    final ZClass java_lang_Integer;
    final ZClass java_lang_Long;
    final ZClass java_lang_Float;
    final ZClass java_lang_Double;
    final ZClass java_lang_Void;

    final ZClass java_lang_Cloneable;
    final ZClass java_io_Serializable;

    final ZObject main_thread;

    final ZField java_io_FileDescriptor$fd;
    final ZField java_io_FileOutPutStream$fd;

    // ZObject latest_user_defined_loader;

    public VM(String[] class_paths) {
        assert class_paths.length > 0;
        for (int i = 0; i < class_paths.length; i++) {
            class_paths[i] = Paths.get(class_paths[i]).toAbsolutePath().toString();
        }
        bootstrap_class_loader = new BootstrapClassLoader(this, class_paths);

        // 👿 Bootstrap 👿

        // === === === === === === === === === === === === === === === === === === === ===
        // 初始化 java.lang.Object 与 java.lang.Class
        // 👇顺序不能调整... 特殊设计过的用来处理 Class 与 Object 鸡生蛋蛋生鸡问题：
        //  1. 加载 java.lang.Object 会创建 ZObject, 创建 ZObject 需要传入 ZClass, 则需要加载 java.lang.Class
        //      这时候 java.lang.Object 还没加载完, java.lang.Class 继承自 Object, 又会触发 java.lang.Object 加载, 死循环
        //  2. 如果先加载 java.lang.Class, 因为 Class 继承 Object, 还是死循环

        /*
        ClassFile java_lang_class = class_reader.load_jdk_class_file("java/lang/Class");
        ClassFile java_lang_object = class_reader.load_jdk_class_file("java/lang/Object");

        java_lang_Class = ZClass.object_class(this, null, java_lang_class, null, new ZClass[0]);
        java_lang_Object = ZClass.object_class(this, null, java_lang_object, null, new ZClass[0]);

        java_lang_Class.z_class = java_lang_Class;
        java_lang_Object.z_class = java_lang_Class;

        loaded_classes.put("java/lang/Class", java_lang_Class);
        loaded_classes.put("java/lang/Object", java_lang_Object);

        java_lang_Class.super_class = java_lang_Object;
        java_lang_Class.interfaces = load_class0_interfaces(java_lang_class, false);
        */

        // 改成反射是因为不想因为 object 与 class 破坏 final @NotNull 的 z_class 属性

        ClassFile java_lang_class_file = bootstrap_class_loader.class_reader.load_jdk_class_file("java/lang/Class");
        ClassFile java_lang_object_file = bootstrap_class_loader.class_reader.load_jdk_class_file("java/lang/Object");

        Reflect.ReflectMethod constructor = Reflect.of(ZClass.class).constructor(VM.class, ClassFile.class);
        java_lang_Class = constructor.invoke(this, java_lang_class_file);
        java_lang_Object = constructor.invoke(this, java_lang_object_file);

        Reflect.of(java_lang_Class).field("z_class").set(java_lang_Class);
        Reflect.of(java_lang_Object).field("z_class").set(java_lang_Class);

        // 处理 guard_ 造成的问题
        int class_ins_field_sz = Reflect.of(java_lang_Class).field("instance_field_size_").get();
        Reflect.ReflectField z_object_properties = Reflect.of(ZObject.class).field("properties");
        z_object_properties.set(java_lang_Object, new Object[class_ins_field_sz]);
        z_object_properties.set(java_lang_Class, new Object[class_ins_field_sz]);

        bootstrap_class_loader.put("java/lang/Class", java_lang_Class);
        bootstrap_class_loader.put("java/lang/Object", java_lang_Object);

        Reflect.of(java_lang_Class).field("fsc_").set(new FastSubtypeChecker(java_lang_Class));
        Reflect.of(java_lang_Object).field("fsc_").set(new FastSubtypeChecker(java_lang_Object));

        Reflect.of(java_lang_Class).field("super_class").set(java_lang_Object);
        ZClass[] java_lang_Class_interfaces =
                bootstrap_class_loader.load_class0_interfaces(java_lang_class_file, false);
        Reflect.of(java_lang_Class).field("interfaces").set(java_lang_Class_interfaces);

        Reflect.of(java_lang_Class).field("init_state_").set(ZClass.ZClassState.loaded);
        Reflect.of(java_lang_Object).field("init_state_").set(ZClass.ZClassState.loaded);

        // 初始化实例常量字段
        java_lang_Class.init_class_fields();
        java_lang_Object.init_class_fields();

        // 初始化实例字段默认值
        java_lang_Class.init_instance_field(java_lang_Class);
        java_lang_Class.init_instance_field(java_lang_Object);

        // 调用构造函数
        ZMethod java_lang_class_init = java_lang_Class.special_method(instance_init, "(Ljava/lang/ClassLoader;)V");
        java_lang_class_init.invoke(java_lang_Class, new Object[] { null }); // new java.lang.Class(classloader = null)
        java_lang_class_init.invoke(java_lang_Object, new Object[] { null }); // new java.lang.Class(classloader = null)

        // 理论上应该先执行 java.lang.Class.<clinit> 再执行初始化 new java.lang.Class()
        // 但是对于 Object 和 Class 不行
        //  1. Class 的 static { } 代码会触发其他类加载(Object\Class未加载完不能触发其他类加载)
        //  2. 反过来执行对于 Object 和 Class 无影响

        // static { }
        java_lang_Class.initialize(this); // java.lang.Class.<clinit>
        java_lang_Object.initialize(this); // java.lang.Object.<clinit>

        // === === === === === === === === === === === === === === === === === === === ===

        bootstrap_class_loader.put("void", (class_void = native_class_(void.class)));
        bootstrap_class_loader.put("boolean", (class_boolean = native_class_(boolean.class)));
        bootstrap_class_loader.put("byte", (class_byte = native_class_(byte.class)));
        bootstrap_class_loader.put("char", (class_char = native_class_(char.class)));
        bootstrap_class_loader.put("short", (class_short = native_class_(short.class)));
        bootstrap_class_loader.put("int", (class_int = native_class_(int.class)));
        bootstrap_class_loader.put("long", (class_long = native_class_(long.class)));
        bootstrap_class_loader.put("float", (class_float = native_class_(float.class)));
        bootstrap_class_loader.put("double", (class_double = native_class_(double.class)));

        primitive_class_cache_ = new ZClass[] {
                /*0-3*/null, null, null, null,
                /*4:*/ class_boolean,
                /*5:*/ class_char,
                /*6:*/ class_float,
                /*7:*/ class_double,
                /*8:*/ class_byte,
                /*9:*/ class_short,
                /*10*/ class_int,
                /*11*/ class_long,
        };

        java_lang_Cloneable = load_class("java/lang/Cloneable", false);
        java_io_Serializable = load_class("java/io/Serializable", false);

        java_lang_Boolean = load_class("java/lang/Boolean", true);
        java_lang_Byte = load_class("java/lang/Byte", true);
        java_lang_Character = load_class("java/lang/Character", true);
        java_lang_Short = load_class("java/lang/Short", true);
        java_lang_Integer = load_class("java/lang/Integer", true);
        java_lang_Long = load_class("java/lang/Long", true);
        java_lang_Float = load_class("java/lang/Float", true);
        java_lang_Double = load_class("java/lang/Double", true);
        java_lang_Void = load_class("java/lang/Void", true);

        // === === === === === === === === === === === === === === === === === === === ===
        // 初始化 java.lang.System

        ZObject system_thread_group = load_class("java/lang/ThreadGroup", true).new_instance();

        ZClass thread_class = load_class("java/lang/Thread", true);
        // main_thread = thread_class.new_instance(descriptor, args);
        // 这里不能直接调用 thread 的 constructor, 一堆循环依赖
        main_thread = thread_class.allocate();
        thread_class.field("group").put_value(main_thread, system_thread_group);
        thread_class.field("priority").put_value(main_thread, Thread.MAX_PRIORITY);

        java_lang_System = load_class("java/lang/System", true);
        java_lang_System.method("initializeSystemClass()V").invoke(null, new Object[0]);

        String descriptor = "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;J)V";
        Object[] args = new Object[] {
                system_thread_group, null, Natives.new_string(this, "Main"), 0L
        };
        thread_class.special_method(instance_init, descriptor).invoke(main_thread, args);

        // === === === === === === === === === === === === === === === === === === === ===

        initialized = true;

        java_io_FileDescriptor$fd = load_class("java/io/FileDescriptor", true).field("fd");
        java_io_FileOutPutStream$fd = load_class("java/io/FileOutputStream", true).field("fd");
        // java_lang_ClassLoader = load_class("java/lang/ClassLoader", true);
    }

    public void run(String name, String ...args) {
        ZClass z_class = load_class(name.replace('.', '/'), true);
        assert !z_class.is_primitive() && !z_class.is_array();

        ZMethod main = z_class.method("main([Ljava/lang/String;)V");
        if (main == null) {
            throw new IllegalArgumentException("main 方法没找到...");
        }
        if ((main.access_flags() & ACC_PUBLIC) == 0 || (main.access_flags() & ACC_STATIC) == 0) {
            throw new AssertionError();
        }

        ZObject[] args0 = new ZObject[args.length];
        for (int i = 0; i < args.length; i++) {
            args0[i] = new_string(this, args[i]);
        }

        try {
            main.invoke(null, new Object[] {
                    load_class("java/lang/String", false).new_array(args0)
            });
        } catch (ZThrowable t) {
            dump_stack_trace();
            throw t;
        }
    }

    public ZClass load_class(String name) {
        return load_class(name, true);
    }

    public ZClass load_class(String name, boolean initialize) {
        return bootstrap_class_loader.load_class(name, initialize);
    }

    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.newarray
    ZArray new_primitive_array(int type, int array_length) {
        assert type >= 3 && type <= 11;
        return primitive_class_cache_[type].new_array(array_length);
    }

    <T> T check_null(T obj_ref) {
        if (obj_ref == null) {
            throw new ZThrowable(load_class("java/lang/NullPointerException", true).new_instance());
        }
        return obj_ref;
    }

    void check_div_zero(int i) {
        if (i == 0) {
            ZObject arith_ex = load_class("java/lang/ArithmeticException", true)
                    .new_instance("(Ljava/lang/String;)V", new Object[] {
                            Natives.new_string(this, "/ by zero")
            });
            throw new ZThrowable(arith_ex);
        }
    }
    void check_div_zero(long l) {
        if (l == 0) {
            ZObject arith_ex = load_class("java/lang/ArithmeticException", true)
                    .new_instance("(Ljava/lang/String)V", new Object[] {
                            Natives.new_string(this, "/ by zero")
                    });
            throw new ZThrowable(arith_ex);
        }
    }

    private ZClass native_class_(Class<?> native_class) {
        ZClass z_class = ZClass.native_class(this, java_lang_Class, native_class);
        ZClass.allocate0(java_lang_Class, z_class);
        ZMethod init = java_lang_Class.special_method(instance_init, "(Ljava/lang/ClassLoader;)V");
        init.invoke(z_class, new Object[] { null });
        return z_class;
    }

    private void dump_stack_trace() {
        Deque<ZThread.Frame> stack = stacks.get();
        while (!stack.isEmpty()) {
            System.err.println(stack.pop());
        }
    }

}