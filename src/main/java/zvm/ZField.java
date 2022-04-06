package zvm;

import zvm.ClassParser.ClassFile;

import static zvm.ClassParser.AccessFlags.ACC_FINAL;
import static zvm.ClassParser.AccessFlags.ACC_STATIC;

/**
 * @author chuxiaofeng
 */
public final class ZField {
    private final ZClass z_class;
    private final ClassFile.Field field;
//    private final String key_;
    private final int slot_;

    ZField(ZClass z_class, ClassFile.Field field, int slot) {
        this.z_class = z_class;
        this.field = field;
//        this.key_ = field_key0();
        this.slot_ = slot;
    }

    // 这里用来区分父子继承类同名属性（应该全部处理成数组+slot）
//    private String field_key0() {
//        if ((field.access_flags & ACC_STATIC) == 0) {
//            return z_class.name() + "." + field.name();
//        } else {
//            return z_class.name() + "::" + field.name();
//        }
//    }

    int access_flags() {
        return field.access_flags;
    }

    String field_name() {
        return field.name();
    }

//    String field_key() {
//        return key_;
//    }

    int field_slot() {
        return slot_;
    }

    ZClass declared_class() {
        return z_class;
    }

//    Object get_value(ZObject instance) {
//        if ((field.access_flags & ACC_STATIC) == 0) {
//            z_class.vm.check_null(instance);
//            return instance.get_field_(field_key());
//        } else {
//            return z_class.get_field_(field_key());
//        }
//    }
//
//    /**
//     * 参考 https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putfield
//     */
//    void put_value(ZObject instance, Object value) {
////        value = concrete_value(value);
//        if ((field.access_flags & ACC_STATIC) == 0) {
//            z_class.vm.check_null(instance);
//            assert z_class.is_assignable_from(instance.z_class);
//            value = put_field_check(instance, value);
//            instance.put_field_(field_key(), value);
//        } else {
//            assert !z_class.is_array();
//            value = put_field_check(null, value);
//            z_class.put_field_(field_key(), value);
//        }
//    }

    Object get_value(ZObject instance) {
        if ((field.access_flags & ACC_STATIC) == 0) {
            z_class.vm.check_null(instance);
            return instance.get_field(field_slot());
        } else {
            return z_class.get_static_field(field_slot());
        }
    }

    /**
     * 参考 https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putfield
     */
    void put_value(ZObject instance, Object value) {
//        value = concrete_value(value);
        if ((field.access_flags & ACC_STATIC) == 0) {
            z_class.vm.check_null(instance);
            assert z_class.is_assignable_from(instance.z_class);
            value = put_field_check(instance, value);
            instance.put_field(field_slot(), value);
        } else {
            assert !z_class.is_array();
            value = put_field_check(null, value);
            z_class.put_static_field(field_slot(), value);
        }
    }


    private Object put_field_check(ZObject instance, Object value) {
        assert !z_class.is_array();

        String descriptor = field.descriptor();
        char c = descriptor.charAt(0);

        // 字段描述符如果是boolean, byte, char, short, int, 值必须是 int
        // 这里不完全遵循规范
        /*
        if (c == 'Z' || c == 'B' || c == 'S' || c == 'I') {
            assert value instanceof Integer;
        }
        */
        if (c == 'Z') {
            assert value instanceof Integer || value instanceof Boolean;
            // 如果值的类型是 int，字段描述符是 boolean，则 int 值需要 按位与 1，否则遵循 §2.8.3 类型转换规则
            if (value instanceof Integer) {
                value = ((Integer) value) & 1;
            } else {
                // todo 类型转换 & 类型检查
            }
        }
        // todo: 这里应该正经处理基础类型的自动转型
        else if (c == 'B' || c == 'S' || c == 'I') {
            value = ((Number) value).intValue();
        }

        // 如果字段描述符是 float, long, double, 则值必须对应是 float, long, or double
        else if (c == 'F') assert value instanceof Float;
        else if (c == 'J') assert value instanceof Long;
        else if (c == 'D') assert value instanceof Double;

        // 如果字段描述符是引用类型，则值的类型必须遵循 JLS §5.2 赋值规则
        else if ((c == 'L' || c == '[') && value != null) {
            ZClass field_class = z_class.vm.load_class(Descriptor.field_type(descriptor), false);
            assert value instanceof ZObject;
            assert field_class.is_assignable_from(((ZObject) value).z_class);
        }

        put_final_field_check(instance);
        return value;
    }

    // final 字段的 put_field 指令必须在声明 class 或者 interface 的类的初始化方法中
    // final 字段的 put_static 指令必须在声明 class 的实例的初始化方法中
    private void put_final_field_check(ZObject instance) {
        if ((field.access_flags & ACC_FINAL) != 0) {
            // java_lang_system 中 这三个 final static 字段 不是在 <clinit> 中赋值的, 已经 hack 掉
            if (field.constant_value_index == -1) {
                ZThread.Frame frame = z_class.vm.stacks.get().peek();
                assert frame != null;
                if (instance == null) {
                    assert frame.method.is_class_init();
                } else {
                    assert frame.method.is_instance_init();
                }
            }
        }
    }

    @Override
    public String toString() {
        return z_class.name().replace('/', '.') + "#" + field.name();
    }

//    private Class<?> field_rt_primitive_type() {
//        String descriptor = field.descriptor();
//        char c = descriptor.charAt(0);
//        switch (c) {
//            case 'Z': return Boolean.class;
//            case 'B': return Byte.class;
//            case 'C': return Character.class;
//            case 'S': return Short.class;
//            case 'I': return Integer.class;
//            case 'F': return Float.class;
//            case 'J': return Long.class;
//            case 'D': return Double.class;
//            // case 'V': return Void.class;
////            case '[':
////            case 'L':
////                return z_class.vm.load_class(Descriptor.field_type(descriptor), false);
////            default:
////                throw new AssertionError();
//        }
//        return null;
//    }
//    Object concrete_value(Object value) {
//        if (field_rt_primitive_type_ == Boolean.class) {
//            return (((Integer) value) & 1) == 1;
//        } else if (field_rt_primitive_type_ == Byte.class) {
//            return ((Integer) value).byteValue();
//        } else if (field_rt_primitive_type_ == Character.class) {
//            return ((char) ((Integer) value).intValue());
//        } else if (field_rt_primitive_type_ == Short.class) {
//            return ((Integer) value).shortValue();
//        } else {
//            return value;
//        }
//
////        String descriptor = field.descriptor();
////        char c = descriptor.charAt(0);
////        switch (c) {
////            case 'Z': return (((Integer) value) & 1) == 1;
////            case 'B': return ((Integer) value).byteValue();
////            case 'C': return ((char) ((Integer) value).intValue());
////            case 'S': return ((Integer) value).shortValue();
//////            case 'I': return value;
//////            case 'F': return ((Float) value);
//////            case 'J': return ((Long) value);
//////            case 'D': return ((Double) value);
////            default: return value;
////        }
//    }
//    private Object put_field_check(ZObject instance, Object value) {
//        assert !z_class.is_array();
//
//        Object ft = field_rt_type();
//        if (ft instanceof Class) {
//            assert value != null && ft == value.getClass();
//        } else {
//            assert value == null || ((ZClass) ft).is_assignable_from(((ZObject) value).z_class());
//        }
//        put_final_field_check(instance);
//        return value;
//    }
}
