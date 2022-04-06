package zvm;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author chuxiaofeng
 *
 * https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html#jvms-4.3
 * A descriptor is a string representing the type of a field or method.
 * 描述符是一个描述字段或方法的类型的字符串.
 *
 * https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html#jvms-4.7.9
 * Signatures encode declarations written in the Java programming language
 * that use types outside the type system of the Java Virtual Machine.
 * 签名是用于描述字段、方法和类型定义中的泛型信息的字符串
 * 签名是用于给 Java 语言使用的描述信息编码，不在 Java 虚拟机系统使用 的类型中。泛型类型、方法描述和参数化类型描述等都属于签名。
 */
public final class Descriptor {

    static String[] parameter_types(String desc) {
        int end = desc.indexOf(")");
        assert end != -1;
        return parse(desc, 0, end + 1);
    }

    static String return_type(String desc) {
        int end = desc.indexOf(")");
        assert end != -1;
        return parse(desc, end + 1, desc.length())[0];
    }

    static String field_type(String desc) {
        return parse(desc, 0, desc.length())[0];
    }

    private static String[] parse(String desc, int start, int end) {
        int pos = start;
        int dims = 0;

        List<String> types = new ArrayList<>();
        while (pos < end) {
            String type;
            switch (desc.charAt(pos++)) {
                case '(':
                case ')':
                    continue;
                case '[': dims++;
                    continue;

                // 配合 load_class, 数组和非数组区分处理
                case 'B': type = dims > 0 ? "B" : "byte";break;
                case 'C': type = dims > 0 ? "C" : "char";break;
                case 'D': type = dims > 0 ? "D" : "double";break;
                case 'F': type = dims > 0 ? "F" : "float";break;
                case 'I': type = dims > 0 ? "I" : "int";break;
                case 'J': type = dims > 0 ? "J" : "long";break;
                case 'S': type = dims > 0 ? "S" : "short";break;
                case 'Z': type = dims > 0 ? "Z" : "boolean";break;
                case 'V': type = dims > 0 ? "V" : "void";break;
                case 'L':
                    int sep = desc.indexOf(';', pos);
                    assert sep != -1;
                    if (dims > 0) {
                        type = desc.substring(pos - 1, sep + 1);
                    } else {
                        type = desc.substring(pos, sep);
                    }
                    pos = sep + 1;
                    break;
                default: throw new AssertionError();
            }
            String array_padding = new String(new char[dims]).replace('\0', '[');
            types.add(array_padding + type);
            dims = 0;
        }
        return types.toArray(new String[0]);
    }

    static String method(Executable method) {
        Class<?>[] parameter_types = method.getParameterTypes();
        StringBuilder buf = new StringBuilder("(");
        for (Class<?> parameter_type : parameter_types) {
            buf.append(type(parameter_type));
        }
        buf.append(")");
        String return_type;
        if (method instanceof Method) {
            return_type = type(((Method) method).getReturnType());
        } else {
            return_type = "V";
        }
        buf.append(return_type);
        return buf.toString();
    }

    static String type(Class<?> c) {
        if (c.isArray()) {
            return "[" + type(c.getComponentType());
        } else {
            if (c == byte.class) {
                return "B";
            } else if (c == boolean.class) {
                return "Z";
            } else if (c == short.class) {
                return "S";
            } else if (c == char.class) {
                return "C";
            } else if (c == int.class) {
                return "I";
            } else if (c == long.class) {
                return "J";
            } else if (c == float.class) {
                return "F";
            } else if (c == double.class) {
                return "D";
            } else if (c == void.class) {
                return "V";
            } else {
                return "L" + c.getName().replace('.', '/') + ";";
            }
        }
    }
}
