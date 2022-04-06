package zvm;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author chuxiaofeng
 */
public class ZObject {
    final VM vm;
    final @NotNull ZClass z_class;
    // 注意父子类的同名属性不同...
    // private Map<String, Object> values;
    private final Object[] properties;
    private Lock monitor;
    private ZMethod to_string_method_cache_;

    ZObject(VM vm, @NotNull ZClass z_class) {
        this.vm = vm;
        this.z_class = z_class;
        int sz = z_class.instance_field_size();
        if (sz == -1) {
            // 数组 接口...
            properties = null;
        } else {
            properties = new Object[sz];
        }
    }

    ZClass z_class() {
        return z_class;
    }

    Object get_field(int slot) {
        return properties[slot];
    }

    void put_field(int slot, Object value) {
        assert !(this instanceof ZArray);
        properties[slot] = value;
    }

//    // 字段读写必须通过 ZField 不能直接使用 get_field_
//    /*private*/Object get_field_(String name) {
//        if (values == null) {
//            return null;
//        }
//        return values.get(name);
//    }
//
//    // 字段读写必须通过 ZField 不能直接使用 get_field_
//    /*private*/void put_field_(String name, Object value) {
//        assert !(this instanceof ZArray);
//        if (values == null) {
//            values = new HashMap<>();
//        }
//        values.put(name, value);
//    }

    // 这里不能全部用 synchronized (z_object) {} 是特么因为有 monitor_enter/exit 字节码
    // 没法用 synchronized 实现这两个字节码
    void monitor_enter() {
        if (monitor == null) {
            // 这货跟 synchronized 的语义应该一样
            monitor = new ReentrantLock();
        }
        try {
            monitor.lockInterruptibly();
        } catch (InterruptedException e) {
            throw Natives.new_throwable(vm, e);
        }
    }

    void monitor_exit() {
        assert monitor != null;
        monitor.unlock();
    }

    @Override
    public String toString() {
        // return z_class().toString() + "@" + System.identityHashCode(this);
        if (to_string_method_cache_ == null) {
            to_string_method_cache_ = z_class.virtual_method("toString", "()Ljava/lang/String;");
        }
        return Natives.from_string(vm, ((ZObject) to_string_method_cache_.invoke(this, new Object[0])));
    }
}