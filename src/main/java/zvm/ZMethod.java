package zvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zvm.ClassParser.ClassFile;

import java.util.Deque;

import static zvm.ClassParser.AccessFlags.*;
import static zvm.ClassParser.ConstantPool.*;
import static zvm.ClassParser.Constants.JAVA_8_VERSION;
import static zvm.ClassParser.Constants.JAVA_9_VERSION;

/**
 * @author chuxiaofeng
 */
public class ZMethod {
    private final ZClass z_class;
    private final @NotNull ClassFile.Method method;
    private @Nullable final Invokable invokable;
    private @Nullable Invokable invokable_cache_;
    private Boolean is_signature_polymorphic_cache_;
    private final String return_type;
    private final String[] parameter_types;
    final int[] parameter_type_size_cache_;
    private ZClass[] param_types_cache_;
    private ZClass return_type_cache_;

    ZMethod(ZClass z_class, ClassFile.Method method) {
        this(z_class, method, null);
    }

    ZMethod(ZClass z_class, @NotNull ClassFile.Method method, @Nullable Invokable invokable) {
        this.z_class = z_class;
        this.method = method;
        this.invokable = invokable;
        this.return_type = Descriptor.return_type(descriptor());
        this.parameter_types = Descriptor.parameter_types(descriptor());

        this.parameter_type_size_cache_ = new int[parameter_types.length];
        for (int i = 0; i < parameter_types.length; i++) {
            parameter_type_size_cache_[i] =
                    (parameter_types[i].equals("long") || parameter_types[i].equals("double")) ? 1 : 0;
        }
    }

    String name() {
        return method.name();
    }

    String file_name() { return method.class_file().source_file(); }

    String class_name() {
        return z_class.name();
    }

    ClassFile.Method class_method() {
        return method;
    }

    ZClass declared_class() {
        return z_class;
    }

    int access_flags() {
        return method.access_flags;
    }

    boolean is_signature_polymorphic() {
        if (is_signature_polymorphic_cache_ == null) {
            is_signature_polymorphic_cache_ = is_signature_polymorphic0();
        }
        return is_signature_polymorphic_cache_;
    }

    // instance initialization method
    boolean is_instance_init() {
        return name().equals(instance_init);
    }

    // class or interface initialization method
    boolean is_class_init() {
        return name().equals(class_init);
    }

    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-2.html#jvms-2.9.3
    private boolean is_signature_polymorphic0() {
        if (z_class.major_version() < JAVA_8_VERSION) {
            return false;
        }

        // todo 直接使用 openjdk 的 @PolymorphicSignature 注解判断
        ZClass declared_class = declared_class();
        boolean declared_in_invoke_handle;
        ZClass method_handle = z_class.vm.load_class("java/lang/invoke/MethodHandle", false);
        if (z_class.major_version() >= JAVA_9_VERSION) {
            ZClass var_handle = z_class.vm.load_class("java/lang/invoke/VarHandle", false);
            declared_in_invoke_handle = declared_class == method_handle || declared_class == var_handle;
        } else {
            declared_in_invoke_handle = declared_class == method_handle;
        }

        if (declared_in_invoke_handle) {
            if (parameter_types.length == 1 && parameter_types[0].equals("[Ljava/lang/Object;")) {
                return (access_flags() & ACC_VARARGS) != 0 && (access_flags() & ACC_NATIVE) != 0;
            }
        }
        return false;
    }

    String descriptor() {
        return method.descriptor();
    }

    String[] parameter_types() {
        return parameter_types;
    }

    String return_type() {
        return return_type;
    }

    boolean has_return() {
        return !return_type.equals("void");
    }

    @Nullable ClassFile.LineNumber[] line_number_table() {
        if (method.code == null) {
            return null;
        }
        return method.code.line_number_table;
    }

    @Nullable ClassFile.Code code() {
        return method.code;
    }

    ClassParser.ConstantPool constant_pool() {
        return method.class_file().constant_pool();
    }

    private Invokable resolve_invokable() {
        Invokable invokable;
        if (this.invokable == null) {
            boolean is_native = (access_flags() & ACC_NATIVE) != 0;
            if (is_native) {
                invokable = Natives.resolve(z_class.vm, this);
            } else {
                invokable = Interpreter::interpret;
            }
        } else {
            invokable = this.invokable;
        }

        boolean synchronized0 = (access_flags() & ACC_SYNCHRONIZED) != 0;
        if (synchronized0) {
            return (vm, z_method, object_ref, args) -> {
                synchronized (lock_object(object_ref)) {
                    return invokable.invoke(vm, z_method, object_ref, args);
                }
            };
        } else {
            return invokable;
        }
    }

    private Object lock_object(ZObject object_ref) {
        if ((access_flags() & ACC_STATIC) != 0) {
            assert object_ref == null : "静态方法不应该有 this";
            return this;
        } else {
            assert object_ref != null : "非静态方法 this 不能为 null";
            return object_ref;
        }
    }

    // todo var_args
    Object invoke(ZObject object_ref, Object[] args) {
        Deque<ZThread.Frame> stack = z_class.vm.stacks.get();
        try {
            stack.push(new ZThread.Frame(this));
            debug();
            if (invokable_cache_ == null) {
                invokable_cache_ = resolve_invokable();
            }
            return invokable_cache_.invoke(z_class.vm, this, object_ref, args);
        } finally {
            if (!stack.isEmpty()) { // 非异常
                stack.pop();
            }
        }
    }

    void check_args(Object[] args) {
        if (param_types_cache_ == null) {
            param_types_cache_ = new ZClass[parameter_types.length];
            for (int i = 0; i < parameter_types.length; i++) {
                param_types_cache_[i] = z_class.vm.load_class(parameter_types[i], false);
            }
        }
        for (int i = 0; i < param_types_cache_.length; i++) {
            // todo 参数类型检查失败抛异常
            param_types_cache_[i].type_check(args[i]);
        }
    }

    void check_return(Object return_value) {
        if (has_return()) {
            if (return_type_cache_ == null) {
                return_type_cache_ = z_class.vm.load_class(Descriptor.return_type(descriptor()), false);
            }
            // todo 返回类型检查失败抛异常
            assert return_type_cache_.type_check(return_value);
        }
    }

    private void debug() {
        if (!z_class.vm.log_invoke) {
            return;
        }

        Deque<ZThread.Frame> frames = z_class.vm.stacks.get();
        String padding = new String(new char[frames.size() - 1]).replace("\0", "  ");
        System.err.print(padding + " invoke " + this);
        if (frames.size() > 1) {
            ZThread.Frame top = frames.pop();
            System.err.println(" at " + frames.peek());
            frames.push(top);
        } else {
            System.err.println();
        }
    }

    @Override
    public String toString() {
        return method.toString();
    }
}
