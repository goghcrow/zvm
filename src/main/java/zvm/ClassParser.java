package zvm;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiFunction;

import static zvm.Bytecodes.*;
import static zvm.ClassParser.AccessFlags.*;
import static zvm.ClassParser.ConstantPool.*;
import static zvm.ClassParser.Constants.*;
import static zvm.ClassParser.Constants.ReferenceKind.*;

/**
 * 解析 Class, 但是 🚨🚨🚨🚨🚨 木有做任何 class 文件合法性校验
 * @author chuxiaofeng
 *
 * 参考
 * Java Virtual Machine Specification  Chapter 4. The class File Format
 * https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html
 * https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html
 * https://hg.openjdk.java.net/jdk/jdk14/file/6c954123ee8d/src/hotspot/share/classfile/classFileParser.cpp
 * https://hg.openjdk.java.net/jdk/jdk14/file/6c954123ee8d/src/hotspot/share/classfile/classFileParser.hpp
 */
public interface ClassParser {

    static Bytes bytes(byte[] bytes) {
        return new Bytes(bytes);
    }

    static ClassFile parse(String path) throws IOException {
        return new ClassFile(Files.readAllBytes(Paths.get(path)));
    }

    static ClassFile parse(byte[] bytes) throws IOException {
        return new ClassFile(bytes);
    }

    // DataInputStream -> hand_writing
    @SuppressWarnings("unused")
    class Bytes {
        static class UnReadableByteArray extends ByteArrayInputStream {
            UnReadableByteArray(byte[] buf) { super(buf); }
            void unread(int n) { assert pos >= n; pos -= n; }
            int pos() { return pos; }
            void pos(int pos) { this.pos = pos; }
            byte[] buf() { return this.buf; }
            byte[] copyOfRange(int from, int to) {
                return Arrays.copyOfRange(this.buf, from, to);
            }
        }
        UnReadableByteArray is;
        DataInputStream dis;
        Bytes(byte[] bytes) {
            is = new UnReadableByteArray(bytes);
            dis = new DataInputStream(is);
        }
        int pos() { return is.pos(); }
        void pos(int pos) { is.pos(pos); }
        float f()   throws IOException { return dis.readFloat(); }
        double d()  throws IOException { return dis.readDouble(); }
        long l()    throws IOException { return dis.readLong(); }
        byte s1()   throws IOException { return dis.readByte(); }
        short s2()  throws IOException { return dis.readShort(); }
        int s4()    throws IOException { return dis.readInt(); }
        int u1()    throws IOException { return dis.readUnsignedByte(); }
        int u2()    throws IOException { return dis.readUnsignedShort(); }
        long u4()   throws IOException {
            int ch1 = dis.read();
            int ch2 = dis.read();
            int ch3 = dis.read();
            int ch4 = dis.read();
            if ((ch1 | ch2 | ch3 | ch4) < 0) throw new EOFException();
            return ((((long) ch1) << 24)) + (ch2 << 16) + (ch3 << 8) + ch4;
        }
        String utf() throws IOException     { return dis.readUTF(); }
        int skip(int n) throws IOException  { return ((int) dis.skip(n)); }
        void unread(int n) { is.unread(n); }
        byte[] read(int n) throws IOException {
            byte[] bytes = new byte[n];
            dis.readFully(bytes);
            return bytes;
        }
    }

    class ClassFile {
        private final Bytes bytes;
        ConstantPool constant_pool;

        long magic;
        int minor_version;
        int major_version;
        int access_flags;
        int this_class;
        int super_class;
        int[] interfaces = {};
        Field[] fields;
        Method[] methods;
        // attribute ↓
        int source_file_index;
        @Nullable String source_debug_extension;
        @Nullable InnerClass[] inner_classes;
        boolean deprecated;
        boolean synthetic;
        int generic_signature_index;
        @Nullable Annotation[] annotations;
        @Nullable TypeAnnotation[] type_annotations;
        @Nullable EnclosingMethod enclosing_method;
        @Nullable BootstrapMethod[] bootstrap_methods;
        @Nullable int[] nest_members;
        int host_class_index;
        boolean record;

        ClassFile(byte[] bytes) throws IOException {
            this.bytes = new Bytes(bytes);
            parse();
        }

        @Override
        public String toString() {
            return this_name();
        }

        public ConstantPool constant_pool() { return constant_pool; }

        String source_file() {
            return constant_pool.utf8_at(source_file_index);
        }

        String this_name() {
            return constant_pool.class_at(this_class);
        }

        @Nullable String super_name() {
            if (super_class == 0) {
                return null;
            }
            return constant_pool.class_at(super_class);
        }

        String host_class() {
            if (host_class_index == 0) {
                return null;
            } else {
                return constant_pool.class_at(host_class_index);
            }
        }

        int static_field_size() {
            int size = 0;
            for (ClassFile.Field field : fields) {
                if ((field.access_flags & ACC_STATIC) != 0) {
                    size++;
                }
            }
            return size;
        }

        int instance_field_size() {
            return fields.length - static_field_size();
        }

        void parse() throws IOException {
            magic = bytes.s4();
            assert magic == CLASSFILE_MAGIC;

            minor_version = bytes.u2();
            major_version = bytes.u2();

            parse_constant_pool();
            parse_access_flags();

            this_class = bytes.u2();
            super_class = bytes.u2();

            parse_interfaces();
            parse_fields();
            parse_methods();
            parse_classfile_attributes();
        }

        void parse_constant_pool() throws IOException {
            int constant_pool_count = bytes.u2();
            Entry[] entries = parse_constant_pool_entries(constant_pool_count);
            constant_pool = new ConstantPool(this, entries);
        }

        @SuppressWarnings("DuplicateBranchesInSwitch")
        Entry[] parse_constant_pool_entries(int constant_pool_count) throws IOException {
            Entry[] entries = new Entry[constant_pool_count];
            // 0 无效 size - 1
            for (int i = 1; i < constant_pool_count; i++) {
                int tag = bytes.u1();
                switch (tag) {
                    case CONSTANT_Class:
                        int name_index = bytes.u2();
                        entries[i] = cpEntry(tag, ref1(name_index));
                        break;

                    case CONSTANT_Fieldref:
                    case CONSTANT_Methodref:
                    case CONSTANT_InterfaceMethodref:
                        int class_index = bytes.u2();
                        int name_and_type_index = bytes.u2();
                        entries[i] = cpEntry(tag, ref2(class_index, name_and_type_index));
                        break;

                    case CONSTANT_String:
                        int string_index = bytes.u2();
                        entries[i] = cpEntry(tag, ref1(string_index));
                        break;

                    case CONSTANT_Integer:
                        int integer = bytes.s4();
                        entries[i] = cpEntry(tag, integer);
                        break;

                    case CONSTANT_Float:
                        float f = bytes.f();
                        entries[i] = cpEntry(tag, f);
                        break;

                    case CONSTANT_Long:
                        long l = bytes.l();
                        entries[i] = cpEntry(tag, l);
                        // long 和 double 占两个字节，spec 说这是个poor choice...
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html#jvms-4.4.5
                        // In retrospect, making 8-byte constants take two constant pool entries was a poor choice.
                        i++;
                        break;

                    case CONSTANT_Double:
                        double d = bytes.d();
                        entries[i] = cpEntry(tag, d);
                        i++;
                        break;

                    // 字段 / 方法
                    case CONSTANT_NameAndType:
                        int name_index1 = bytes.u2();
                        int descriptor_index = bytes.u2();
                        entries[i] = cpEntry(tag, ref2(name_index1, descriptor_index));
                        break;

                    case CONSTANT_Utf8:
                        String utf = bytes.utf();
                        entries[i] = cpEntry(tag, utf);
                        break;

                    case CONSTANT_MethodHandle:
                        int reference_kind = bytes.u1();
                        int reference_index = bytes.u2();
                        entries[i] = cpEntry(tag, ref2(reference_kind, reference_index));
                        break;

                    case CONSTANT_MethodType:
                        int descriptor_index1 = bytes.u2();
                        entries[i] = cpEntry(tag, ref1(descriptor_index1));
                        break;

                    case CONSTANT_Dynamic:
                    case CONSTANT_InvokeDynamic:
                        int bootstrap_method_attr_index = bytes.u2();
                        int name_and_type_index1 = bytes.u2();
                        entries[i] = cpEntry(tag, ref2(bootstrap_method_attr_index, name_and_type_index1));
                        break;

                    case CONSTANT_Module:
                    case CONSTANT_Package:
                        int name_index2 = bytes.u2();
                        entries[i] = cpEntry(tag, ref1(name_index2));
                        break;

                    case CONSTANT_Unicode:
                    default:
                        throw new AssertionError();
                }
            }
            return entries;
        }

        void parse_access_flags() throws IOException {
            if (major_version >= JAVA_9_VERSION) {
                access_flags = bytes.u2() & (RECOGNIZED_CLASS_MODIFIERS | ACC_MODULE);
            } else {
                access_flags = bytes.u2() & RECOGNIZED_CLASS_MODIFIERS;
            }
        }

        void parse_interfaces() throws IOException {
            int interfaces_count = bytes.u2();
            interfaces = new int[interfaces_count];
            for (int i = 0; i < interfaces_count; i++) {
                int interface_index = bytes.u2();
                interfaces[i] = interface_index;
            }
        }

        // parse fields ...

        void parse_fields() throws IOException {
            int fields_count = bytes.u2();
            fields = new Field[fields_count];
            for (int i = 0; i < fields_count; i++) {
                fields[i] = parse_field();
            }
        }

        Field parse_field() throws IOException {
            Field field = new Field();
            field.access_flags = bytes.u2() & RECOGNIZED_FIELD_MODIFIERS;
            field.name_index = bytes.u2();
            field.descriptor_index = bytes.u2();

            int attributes_count = bytes.u2();
            parse_field_attributes(field, attributes_count);
            return field;
        }

        void parse_field_attributes(Field field, int attributes_count) throws IOException {
            for (int i = 0; i < attributes_count; i++) {
                parse_field_attribute(field);
            }
        }

        void parse_field_attribute(Field field) throws IOException {
            int attribute_name_index = bytes.u2();
            long attribute_length = bytes.u4();

            // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html#jvms-4.7-320
            String attribute_name = constant_pool.utf8_at(attribute_name_index);
            switch (attribute_name) {
                case tag_constant_value:
                    field.constant_value_index = bytes.u2();
                    break;

                case tag_synthetic:
                    assert attribute_length == 0;
                    field.access_flags |= ACC_SYNTHETIC;
                    field.synthetic = true;
                    break;

                case tag_deprecated:
                    assert attribute_length == 0;
                    field.deprecated = true;
                    break;

                case tag_signature:
                    assert major_version >= JAVA_1_5_VERSION;
                    field.generic_signature_index = parse_generic_signature_attribute();
                    break;

                case tag_runtime_visible_annotations:
                // case tag_runtime_invisible_annotations:
                    assert major_version >= JAVA_1_5_VERSION;
                    Out<byte[]> raw = new Out<>();
                    field.annotations = parse_runtime_visible_annotations(raw);
                    field.annotations_raw = raw.val;
                    break;

                case tag_runtime_visible_type_annotations:
                // case tag_runtime_invisible_type_annotations:
                    assert major_version >= JAVA_1_5_VERSION;
                    field.type_annotations = parse_field_runtime_visible_type_annotations();
                    break;

                default:
                    bytes.skip(Math.toIntExact(attribute_length));
            }
        }

        int parse_generic_signature_attribute() throws IOException {
            @SuppressWarnings("UnnecessaryLocalVariable")
            int generic_signature_index = bytes.u2();
            return generic_signature_index;
        }

        // parse methods ...

        void parse_methods() throws IOException {
            int methods_count = bytes.u2();
            methods = new Method[methods_count];
            for (int i = 0; i < methods_count; i++) {
                methods[i] = parse_method();
            }
        }

        Method parse_method() throws IOException {
            Method method = new Method();
            method.access_flags = bytes.u2() & RECOGNIZED_METHOD_MODIFIERS;
            method.name_index = bytes.u2();
            method.descriptor_index = bytes.u2();

            int attributes_count = bytes.u2();
            parse_method_attributes(method, attributes_count);
            return method;
        }

        void parse_method_attributes(Method method, int attributes_count) throws IOException {
            for (int i = 0; i < attributes_count; i++) {
                parse_method_attribute(method);
            }
        }

        void parse_method_attribute(Method method) throws IOException {
            int attribute_name_index = bytes.u2();
            long attribute_length = bytes.u4();

            String attribute_name = constant_pool.utf8_at(attribute_name_index);
            // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html#jvms-4.7-320
            switch (attribute_name) {
                case tag_code:
                    method.code = parse_method_attribute_code();
                    break;

                case tag_exceptions:
                    method.checked_exceptions = parse_method_checked_exceptions();
                    break;

                case tag_method_parameters:
                    method.parameters = parse_method_parameters();
                    break;

                case tag_synthetic:
                    assert attribute_length == 0;
                    method.access_flags |= ACC_SYNTHETIC;
                    method.synthetic = true;
                    break;

                case tag_deprecated:
                    assert attribute_length == 0;
                    method.deprecated = true;
                    break;

                case tag_signature:
                    assert major_version >= JAVA_1_5_VERSION;
                    method.generic_signature_index = parse_generic_signature_attribute();
                    break;

                case tag_annotation_default:
                {
                    Out<byte[]> raw = new Out<>();
                    method.annotation_default = parse_annotation_default(raw);
                    method.annotation_default_raw = raw.val;
                    break;
                }
                case tag_runtime_visible_annotations:
                // case tag_runtime_invisible_annotations:
                {
                    assert major_version >= JAVA_1_5_VERSION;
                    Out<byte[]> raw = new Out<>();
                    method.annotations = parse_runtime_visible_annotations(raw);
                    method.annotations_raw = raw.val;
                    break;
                }

                case tag_runtime_visible_parameter_annotations:
                // case tag_runtime_invisible_parameter_annotations:
                {
                    assert major_version >= JAVA_1_5_VERSION;
                    Out<byte[]> raw = new Out<>();
                    method.parameter_annotations = parse_runtime_visible_parameter_annotations(raw);
                    method.parameter_annotations_raw = raw.val;
                    break;
                }

                case tag_runtime_visible_type_annotations:
                // case tag_runtime_invisible_type_annotations:
                    assert major_version >= JAVA_1_5_VERSION;
                    method.type_annotations = parse_method_runtime_visible_type_annotations();
                    break;

                default:
                    bytes.skip(Math.toIntExact(attribute_length));
            }
        }

        Code parse_method_attribute_code() throws IOException {
            Code code = new Code();
            code.max_stack = bytes.u2();
            code.max_locals = bytes.u2();

            long code_length = bytes.u4();
            assert code_length > 0 && code_length <= MAX_CODE_SIZE;
            code.bytes = bytes.read(Math.toIntExact(code_length));

            int exception_table_length = bytes.u2();
            Exception[] exception_table = new Exception[exception_table_length];
            for (int i = 0; i < exception_table_length; i++) {
                int start_pc = bytes.u2();
                int end_pc = bytes.u2();
                int handler_pc = bytes.u2();
                int catch_type = bytes.u2();
                exception_table[i] = new Exception(start_pc, end_pc, handler_pc, catch_type);
            }
            code.exception_table = exception_table;

            int attributes_count = bytes.u2();
            for (int i = 0; i < attributes_count; i++) {
                parse_code_attribute(code);
            }

            return code;
        }

        void parse_code_attribute(Code code) throws IOException {
            int attribute_name_index = bytes.u2();
            long attribute_length = bytes.u4();

            String attribute_name = constant_pool.utf8_at(attribute_name_index);
            // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html#jvms-4.7-320
            switch (attribute_name) {
                case tag_line_number_table:
                    parse_code_line_number_table(code);
                    break;

                case tag_local_variable_table:
                    parse_code_local_variable_table(code);
                    break;

                case tag_local_variable_type_table:
                    assert major_version >= JAVA_1_5_VERSION;
                    parse_code_local_variable_type_table(code);
                    break;

                case tag_stack_map_table:
                    assert major_version >= STACKMAP_ATTRIBUTE_MAJOR_VERSION;
                    //noinspection ConstantConditions
                    if (false) {
                        parse_code_stackmap_table(code);
                    } else {
                        bytes.skip(Math.toIntExact(attribute_length));
                    }
                    break;

                case tag_runtime_visible_type_annotations:
                // case tag_runtime_invisible_type_annotations:
                    parse_code_runtime_visible_type_annotations(code);
                    break;

                default:
                    bytes.skip(Math.toIntExact(attribute_length));
            }
        }

        void parse_code_line_number_table(Code code) throws IOException {
            int line_number_table_length = bytes.u2();
            LineNumber[] line_number_table = new LineNumber[line_number_table_length];
            for (int i = 0; i < line_number_table_length; i++) {
                int start_pc = bytes.u2();
                int line_number = bytes.u2();
                line_number_table[i] = new LineNumber(start_pc, line_number);
            }
            code.line_number_table = line_number_table;
        }

        void parse_code_local_variable_table(Code code) throws IOException {
            int local_variable_table_length = bytes.u2();
            LocalVariable[] local_variable_table = new LocalVariable[local_variable_table_length];
            for (int i = 0; i < local_variable_table_length; i++) {
                int start_pc = bytes.u2();
                int length = bytes.u2();
                int name_index = bytes.u2();
                int descriptor_index = bytes.u2();
                int index = bytes.u2();
                local_variable_table[i] = new LocalVariable(start_pc, length, name_index, descriptor_index, index);
            }
            code.local_variable_table = local_variable_table;
        }

        void parse_code_local_variable_type_table(Code code) throws IOException {
            int local_variable_type_table_length = bytes.u2();
            LocalVariableType[] local_variable_type_table = new LocalVariableType[local_variable_type_table_length];
            for (int i = 0; i < local_variable_type_table_length; i++) {
                int start_pc = bytes.u2();
                int length = bytes.u2();
                int name_index = bytes.u2();
                int signature_index = bytes.u2();
                int index = bytes.u2();
                local_variable_type_table[i] = new LocalVariableType(start_pc, length, name_index, signature_index, index);
            }
            code.local_variable_type_table = local_variable_type_table;
        }

        @SuppressWarnings("unused")
        void parse_code_stackmap_table(Code code) throws IOException {
            int number_of_entries = bytes.u2();
            for (int i = 0; i < number_of_entries; i++) {
                parse_stackmap_table_entry();
            }
        }

        @SuppressWarnings("StatementWithEmptyBody")
        void parse_stackmap_table_entry() throws IOException {
            // static class StackMapFrame { }
            int frame_type = bytes.u1();
            if (frame_type < 64) {
                // same_frame
            } else if (frame_type < 128) {
                // same_locals_1_stack_item_frame
            } else if (frame_type < 247) {
                // reserved
            } else if (frame_type == 247) {
                // same_locals_1_stack_item_frame_extended
            } else if (frame_type < 251) {
                // chop_frame
            } else if (frame_type == 251) {
                // same_frame_extended
            } else if (frame_type < 255) {
                // append_frame
            } else if (frame_type == 255) {
                // full_frame
            }
        }

        void parse_code_runtime_visible_type_annotations(Code code) throws IOException {
            int num_annotations = bytes.u2();
            TypeAnnotation[] type_annotations = new TypeAnnotation[num_annotations];
            for (int i = 0; i < num_annotations; i++) {
                type_annotations[i] = parse_code_runtime_visible_type_annotation();
            }
            code.type_annotations = type_annotations;
        }

        int[] parse_method_checked_exceptions() throws IOException {
            int number_of_exceptions = bytes.u2();
            int[] checked_exceptions = new int[number_of_exceptions];
            // exception_index_table[number_of_exceptions];
            for (int i = 0; i < number_of_exceptions; i++) {
                checked_exceptions[i] = parse_checked_exception();
            }
            return checked_exceptions;
        }

        int parse_checked_exception() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int checked_exception_index = bytes.u2();
            return checked_exception_index;
        }

        Parameter[] parse_method_parameters() throws IOException {
            int parameters_count = bytes.u1();
            Parameter[] parameters = new Parameter[parameters_count];
            for (int i = 0; i < parameters_count; i++) {
                parameters[i] = parse_method_parameter();
            }
            return parameters;
        }

        Parameter parse_method_parameter() throws IOException {
            int name_index = bytes.u2();
            int access_flags = bytes.u2();
            return new Parameter(name_index, access_flags);
        }

        // parse class attributes ...

        void parse_classfile_attributes() throws IOException {
            int attributes_count = bytes.u2();
            for (int i = 0; i < attributes_count; i++) {
                parse_classfile_attribute();
            }
        }

        void parse_classfile_attribute() throws IOException {
            int attribute_name_index = bytes.u2();
            long attribute_length = bytes.u4();

            // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-4.html#jvms-4.7-320
            String attribute_name = constant_pool.utf8_at(attribute_name_index);
            switch (attribute_name) {
                case tag_source_file:
                    parse_classfile_source_file_attribute();
                    break;

                case tag_source_debug_extension:
                    parse_classfile_source_debug_extension_attribute();
                    break;

                case tag_inner_classes:
                    parse_classfile_inner_classes_attribute();
                    break;

                case tag_synthetic:
                    assert attribute_length == 0;
                    access_flags |= ACC_SYNTHETIC;
                    synthetic = true;
                    break;

                case tag_deprecated:
                    assert attribute_length == 0;
                    deprecated = true;
                    break;

                case tag_signature:
                    assert major_version >= JAVA_1_5_VERSION;
                    generic_signature_index = parse_generic_signature_attribute();
                    break;

                case tag_runtime_visible_annotations:
                // case tag_runtime_invisible_annotations:
                    assert major_version >= JAVA_1_5_VERSION;
                    annotations = parse_runtime_visible_annotations();
                    break;

                case tag_runtime_visible_type_annotations:
                // case tag_runtime_invisible_type_annotations:
                    assert major_version >= JAVA_1_5_VERSION;
                    type_annotations = parse_classfile_runtime_visible_type_annotations();
                    break;

                case tag_enclosing_method:
                    assert major_version >= JAVA_1_5_VERSION;
                    enclosing_method = parse_classfile_enclosing_method();
                    break;

                case tag_bootstrap_methods:
                    assert major_version >= JAVA_1_5_VERSION;
                    assert major_version >= INVOKEDYNAMIC_MAJOR_VERSION;
                    bootstrap_methods = parse_classfile_bootstrap_methods_attribute();
                    break;

                case tag_nest_members:
                    assert major_version >= JAVA_11_VERSION;
                    nest_members = parse_classfile_nest_members_attribute();
                    break;

                case tag_nest_host:
                    assert major_version >= JAVA_11_VERSION;
                    host_class_index = parse_classfile_nest_host_attribute();
                    break;

                case tag_record:
                    assert major_version >= JAVA_14_VERSION;
                    parse_classfile_record_attribute();
                    bytes.skip(Math.toIntExact(attribute_length));
                    record = true;
                    break;

                // todo module 相关

                default:
                    bytes.skip(Math.toIntExact(attribute_length));
            }
        }

        void parse_classfile_source_file_attribute() throws IOException {
            source_file_index = bytes.u2();
        }

        void parse_classfile_source_debug_extension_attribute() throws IOException {
            bytes.unread(2);
            source_debug_extension = bytes.utf();
        }

        void parse_classfile_inner_classes_attribute() throws IOException {
            int number_of_classes = bytes.u2();
            inner_classes = new InnerClass[number_of_classes];
            for (int i = 0; i < number_of_classes; i++) {
                int inner_class_info_index = bytes.u2();
                int outer_class_info_index = bytes.u2();
                int inner_name_index = bytes.u2();
                int inner_class_access_flags = bytes.u2();
                inner_classes[i] = new InnerClass(inner_class_info_index, outer_class_info_index,
                        inner_name_index, inner_class_access_flags);
            }
        }

        EnclosingMethod parse_classfile_enclosing_method() throws IOException {
            int class_index = bytes.u2();
            int method_index = bytes.u2();
            return new EnclosingMethod(class_index, method_index);
        }

        BootstrapMethod[] parse_classfile_bootstrap_methods_attribute() throws IOException {
            int num_bootstrap_methods = bytes.u2();
            BootstrapMethod[] bootstrapMethods = new BootstrapMethod[num_bootstrap_methods];
            for (int i = 0; i < num_bootstrap_methods; i++) {
                int bootstrap_method_ref = bytes.u2();
                int num_bootstrap_arguments = bytes.u2();
                int[] args = new int[num_bootstrap_arguments];
                // u2 bootstrap_arguments[num_bootstrap_arguments];
                for (int j = 0; j < num_bootstrap_arguments; j++) {
                    int bootstrap_argument_index = bytes.u2();
                    args[j] = bootstrap_argument_index;
                }
                bootstrapMethods[i] = new BootstrapMethod(bootstrap_method_ref, args);
            }
            return bootstrapMethods;
        }

        int[] parse_classfile_nest_members_attribute() throws IOException {
            int number_of_classes = bytes.u2();
            int[] classes = new int[number_of_classes];
            for (int i = 0; i < number_of_classes; i++) {
                int class_index = bytes.u2();
                classes[i] = class_index;
            }
            return classes;
        }

        int parse_classfile_nest_host_attribute() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int host_class_index = bytes.u2();
            return host_class_index;
        }

        void parse_classfile_record_attribute() {
            assert constant_pool.class_at(super_class).equals("java/lang/Record");
            assert (access_flags & ACC_FINAL) == ACC_FINAL;
            assert (access_flags & ACC_ABSTRACT) != ACC_ABSTRACT;
        }

        // parse annotation ...
        static class Out<T> { T val;}
        Annotation[] parse_runtime_visible_annotations(Out<byte[]> out) throws IOException {
            if (out == null) {
                return parse_runtime_visible_annotations();
            } else {
                int from = bytes.pos();
                Annotation[] annotations = parse_runtime_visible_annotations();
                int to = bytes.pos();
                out.val = bytes.is.copyOfRange(from, to);
                return annotations;
            }
        }
        Annotation[] parse_runtime_visible_annotations() throws IOException {
            int num_annotations = bytes.u2();
            Annotation[] annotations = new Annotation[num_annotations];
            for (int i = 0; i < num_annotations; i++) {
                annotations[i] = parse_runtime_visible_annotation();
            }
            return annotations;
        }

        Annotation parse_runtime_visible_annotation() throws IOException {
            int type_index = bytes.u2();
            int num_element_value_pairs = bytes.u2();
            ElementValuePair[] pairs = new ElementValuePair[num_element_value_pairs];
            for (int i = 0; i < num_element_value_pairs; i++) {
                int element_name_index = bytes.u2();
                ElementValue value = parse_annotation_element_value();
                pairs[i] = new ElementValuePair(element_name_index, value);
            }
            return new Annotation(type_index, pairs);
        }

        Annotation[][] parse_runtime_visible_parameter_annotations(Out<byte[]> out) throws IOException {
            if (out == null) {
                return parse_runtime_visible_parameter_annotations();
            } else {
                int from = bytes.pos();
                Annotation[][] annotations = parse_runtime_visible_parameter_annotations();
                int to = bytes.pos();
                out.val = bytes.is.copyOfRange(from, to);
                return annotations;
            }
        }

        Annotation[][] parse_runtime_visible_parameter_annotations() throws IOException {
            int num_parameters = bytes.u1();
            Annotation[][] parameter_annotations = new Annotation[num_parameters][];
            for (int i = 0; i < num_parameters; i++) {
                parameter_annotations[i] = parse_runtime_visible_annotations();
            }
            return parameter_annotations;
        }

        // https://docs.oracle.com/javase/specs/jls/se14/html/jls-4.html#jls-4.11
        TypeAnnotation[] parse_classfile_runtime_visible_type_annotations() throws IOException {
            int num_annotations = bytes.u2();
            TypeAnnotation[] type_annotations = new TypeAnnotation[num_annotations];
            for (int i = 0; i < num_annotations; i++) {
                type_annotations[i] = parse_classfile_runtime_visible_type_annotation();
            }
            return type_annotations;
        }

        TypeAnnotation[]  parse_method_runtime_visible_type_annotations() throws IOException {
            int num_annotations = bytes.u2();
            TypeAnnotation[] type_annotations = new TypeAnnotation[num_annotations];
            for (int i = 0; i < num_annotations; i++) {
                type_annotations[i] = parse_method_runtime_visible_type_annotation();
            }
            return type_annotations;
        }

        TypeAnnotation[] parse_field_runtime_visible_type_annotations() throws IOException {
            int num_annotations = bytes.u2();
            TypeAnnotation[] type_annotations = new TypeAnnotation[num_annotations];
            for (int i = 0; i < num_annotations; i++) {
                type_annotations[i] = parse_field_runtime_visible_type_annotation();
            }
            return type_annotations;
        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        TypeAnnotation parse_classfile_runtime_visible_type_annotation() throws IOException {
            int target_type = bytes.u1();
            Object target_info;

            switch (target_type) {
                case 0x00: // type parameter declaration of generic class or interface
                    int type_parameter_index = parse_anno_type_parameter_target();
                    target_info = type_parameter_index;
                    break;

                case 0x10: // type in extends or implements clause of class declaration (including the direct superclass or direct superinterface of an anonymous class declaration), or in extends clause of interface declaration
                    int supertype_index = parse_anno_supertype_target();
                    target_info = supertype_index;
                    break;

                case 0x11: // type in bound of type parameter declaration of generic class or interface
                    target_info = parse_anno_type_parameter_bound_target();
                    break;

                default:
                    throw new AssertionError();
            }
            AnnotationTypePath[] type_paths = parse_annotation_type_path();
            Annotation annotation = parse_runtime_visible_annotation();
            return new TypeAnnotation(target_type, target_info, type_paths, annotation.type_index, annotation.pairs);
        }

        TypeAnnotation parse_method_runtime_visible_type_annotation() throws IOException {
            int target_type = bytes.u1();
            Object target_info = null;

            switch (target_type) {
                case 0x01: // type parameter declaration of generic method or constructor
                    //noinspection UnnecessaryLocalVariable
                    int type_parameter_index = parse_anno_type_parameter_target();
                    target_info = type_parameter_index;
                    break;

                case 0x12: // type in bound of type parameter declaration of generic method or constructor
                    target_info = parse_anno_type_parameter_bound_target();
                    break;

                case 0x14: // return type of method, or type of newly constructed object
                case 0x15: // receiver type of method or constructor
                    parse_anno_empty_target();
                    break;

                case 0x16: // type in formal parameter declaration of method, constructor, or lambda expression
                    target_info = parse_anno_formal_parameter_target();
                    break;

                case 0x17: // type in throws clause of method or constructor
                    target_info = parse_anno_throws_target();
                    break;

                default:
                    throw new AssertionError();
            }
            AnnotationTypePath[] type_paths = parse_annotation_type_path();
            Annotation annotation = parse_runtime_visible_annotation();
            return new TypeAnnotation(target_type, target_info, type_paths, annotation.type_index, annotation.pairs);
        }

        TypeAnnotation parse_field_runtime_visible_type_annotation() throws IOException {
            int target_type = bytes.u1();
            Object target_info = null;
            //noinspection SwitchStatementWithTooFewBranches
            switch (target_type) {
                case 0x13: // type in field declaration
                    parse_anno_empty_target();
                    break;
                default:
                    throw new AssertionError();
            }
            AnnotationTypePath[] type_paths = parse_annotation_type_path();
            Annotation annotation = parse_runtime_visible_annotation();
            //noinspection ConstantConditions
            return new TypeAnnotation(target_type, target_info, type_paths, annotation.type_index, annotation.pairs);
        }

        TypeAnnotation parse_code_runtime_visible_type_annotation() throws IOException {
            int target_type = bytes.u1();
            Object target_info;

            switch (target_type) {
                case 0x40: // type in local variable declaration
                case 0x41: // type in resource variable declaration
                    target_info = parse_anno_localvar_target();
                    break;

                case 0x42: // type in exception parameter declaration
                    target_info = parse_anno_catch_target();
                    break;

                case 0x43: // type in instanceof expression
                case 0x44: // type in new expression
                case 0x45: // type in method reference expression using ::new
                case 0x46: // type in method reference expression using ::Identifier
                    target_info = parse_anno_offset_target();
                    break;

                case 0x47: // type in cast expression
                case 0x48: // type argument for generic constructor in new expression or explicit constructor invocation statement
                case 0x49: // type argument for generic method in method invocation expression
                case 0x4A: // type argument for generic constructor in method reference expression using ::new
                case 0x4B: // type argument for generic method in method reference expression using ::Identifier
                    target_info = parse_anno_type_argument_target();
                    break;

                default:
                    throw new AssertionError();
            }

            AnnotationTypePath[] type_paths = parse_annotation_type_path();
            Annotation annotation = parse_runtime_visible_annotation();
            return new TypeAnnotation(target_type, target_info, type_paths, annotation.type_index, annotation.pairs);
        }

        int parse_anno_type_parameter_target() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int type_parameter_index = bytes.u1();
            return type_parameter_index;
        }

        int parse_anno_supertype_target() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int supertype_index = bytes.u2();
            return supertype_index;
        }

        TypeParameterBoundTarget parse_anno_type_parameter_bound_target() throws IOException {
            int type_parameter_index = bytes.u1();
            int bound_index = bytes.u1();
            return new TypeParameterBoundTarget(type_parameter_index, bound_index);
        }

        @SuppressWarnings("RedundantThrows")
        void parse_anno_empty_target() throws IOException { }

        int parse_anno_formal_parameter_target() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int formal_parameter_index = bytes.u1();
            return formal_parameter_index;
        }

        int parse_anno_throws_target() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int throws_type_index = bytes.u2();
            return throws_type_index;
        }

        AnnotationLocalVarTarget[] parse_anno_localvar_target() throws IOException {
            int table_length = bytes.u2();
            AnnotationLocalVarTarget[] localvar_targets = new AnnotationLocalVarTarget[table_length];
            for (int i = 0; i < table_length; i++) {
                int start_pc = bytes.u2();
                int length = bytes.u2();
                int index = bytes.u2();
                localvar_targets[i] = new AnnotationLocalVarTarget(start_pc, length, index);
            }
            return localvar_targets;
        }

        int parse_anno_catch_target() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int exception_table_index = bytes.u2();
            return exception_table_index;
        }

        int parse_anno_offset_target() throws IOException {
            //noinspection UnnecessaryLocalVariable
            int offset = bytes.u2();
            return offset;
        }

        TypeArgument parse_anno_type_argument_target() throws IOException {
            int offset = bytes.u2();
            int type_argument_index = bytes.u1();
            return new TypeArgument(offset, type_argument_index);
        }

        AnnotationTypePath[] parse_annotation_type_path() throws IOException {
            // type_path_kind: 1; type_argument_index
            int path_length = bytes.u1();
            AnnotationTypePath[] type_paths = new AnnotationTypePath[path_length];
            for (int i = 0; i < path_length; i++) {
                int type_path_kind = bytes.u1();
                int type_argument_index = bytes.u1();
                switch (type_path_kind) {
                    case 0: // Annotation is deeper in an array type
                    case 1: // Annotation is deeper in a nested type
                    case 2: // Annotation is on the bound of a wildcard type argument of a parameterized type
                        assert type_argument_index == 0;
                        break;
                    case 3: // Annotation is on a type argument of a parameterized type
                        break;
                    default:
                        throw new AssertionError();
                }
                type_paths[i] = new AnnotationTypePath(type_path_kind, type_argument_index);
            }
            return type_paths;
        }

        ElementValue parse_annotation_default(Out<byte[]> out) throws IOException {
            if (out == null) {
                return parse_annotation_element_value();
            } else {
                int from = bytes.pos();
                ElementValue element_value = parse_annotation_element_value();
                int to = bytes.pos();
                out.val = bytes.is.copyOfRange(from, to);
                return element_value;
            }
        }

        ElementValue parse_annotation_element_value() throws IOException {
            int tag = bytes.u1();
            switch (tag) {
                case 'B':
                    byte b = constant_pool.byte_at(bytes.u2());
                    return new ElementValue('B', b);
                case 'C':
                    char c = constant_pool.char_at(bytes.u2());
                    return new ElementValue('C', c);
                case 'D':
                    double d = constant_pool.double_at(bytes.u2());
                    return new ElementValue('D', d);
                case 'F':
                    float f = constant_pool.float_at(bytes.u2());
                    return new ElementValue('F', f);
                case 'I':
                    int iv = constant_pool.int_at(bytes.u2());
                    return new ElementValue('I', iv);
                case 'J':
                    long l = constant_pool.long_at(bytes.u2());
                    return new ElementValue('J', l);
                case 'S':
                    short s = constant_pool.short_at(bytes.u2());
                    return new ElementValue('S', s);
                case 'Z':
                    boolean bl = constant_pool.boolean_at(bytes.u2());
                    return new ElementValue('Z', bl);
                case 's':
                    String str = constant_pool.utf8_at(bytes.u2());
                    return new ElementValue('s', str);
                case 'e': // enum
                    int type_name_index = bytes.u2();
                    int const_name_index = bytes.u2();
                    return new ElementValue('e', new Enum(type_name_index, const_name_index));
                case 'c': // Class
                    int class_info_index = bytes.u2();
                    return new ElementValue('c', class_info_index);
                case '@': // Annotation type
                    Annotation annotation = parse_runtime_visible_annotation();
                    return new ElementValue('@', annotation);
                case '[': // Array type
                    int num_values = bytes.u2();
                    ElementValue[] elements = new ElementValue[num_values];
                    for (int k = 0; k < num_values; k++) {
                        elements[k] = parse_annotation_element_value();
                    }
                    return new ElementValue('[', elements);
                default:
                    throw new AssertionError();
            }
        }


        class Field {
            int access_flags;
            int name_index;
            int descriptor_index;
            // attribute ↓
            int constant_value_index = -1;
            boolean deprecated;
            boolean synthetic;
            int generic_signature_index;
            @Nullable Annotation[] annotations;
            @Nullable byte[] annotations_raw; // 给 jdk 用的...
            @Nullable TypeAnnotation[] type_annotations;
            String name() { return constant_pool.utf8_at(name_index); }
            String descriptor() { return constant_pool.utf8_at(descriptor_index); }
            String generic_signature() {
                if (generic_signature_index == 0) {
                    return descriptor();
                } else {
                    return constant_pool.utf8_at(generic_signature_index);
                }
            }

        }
        class Method {
            int access_flags;
            int name_index;
            int descriptor_index;
            // attribute ↓
            Code code;
            @Nullable int[] checked_exceptions;
            @Nullable Parameter[] parameters;
            boolean deprecated;
            boolean synthetic;
            int generic_signature_index;
            @Nullable ElementValue annotation_default;
            @Nullable byte[] annotation_default_raw;
            @Nullable Annotation[] annotations;
            @Nullable byte[] annotations_raw;
            @Nullable Annotation[][] parameter_annotations;
            @Nullable byte[] parameter_annotations_raw;
            @Nullable TypeAnnotation[] type_annotations;

            ClassFile class_file() { return ClassFile.this; }
            // ConstantPool constant_pool() { return constant_pool; }
            String name() { return constant_pool.utf8_at(name_index); }
            String descriptor() { return constant_pool.utf8_at(descriptor_index); }
            String generic_signature() {
                if (generic_signature_index == 0) {
                    return descriptor();
                } else {
                    return constant_pool.utf8_at(generic_signature_index);
                }
            }
            String[] checked_exceptions() {
                if (checked_exceptions == null) {
                    return null;
                }
                int sz = checked_exceptions.length;
                String[] names = new String[sz];
                for (int i = 0; i < sz; i++) {
                    names[i] = constant_pool.class_at(checked_exceptions[i]);
                }
                return names;
            }
            @Override
            public String toString() {
                return this_name().replace('/', '.') + "." + name() + descriptor();
            }
        }
        static class Code {
            int max_stack;
            int max_locals;
            byte[] bytes; // code
            Exception[] exception_table;
            // attribute ↓
            LineNumber[] line_number_table;
            LocalVariable[] local_variable_table;
            LocalVariableType[] local_variable_type_table;
            // stack_map_table; // todo
            @Nullable TypeAnnotation[] type_annotations;
        }
        // static class Opcode { }
        // static class Instruction { }
        class Exception {
            final int start_pc;
            final int end_pc;
            final int handler_pc;
            final int catch_type;
            Exception(int start_pc, int end_pc, int handler_pc, int catch_type) {
                this.start_pc = start_pc;
                this.end_pc = end_pc;
                this.handler_pc = handler_pc;
                this.catch_type = catch_type;
            }
            boolean catch_any() { return catch_type == 0; }
            String catch_type() { return constant_pool.class_at(catch_type); }
        }
        static class LineNumber {
            final int start_pc;
            final int line_number;
            LineNumber(int start_pc, int line_number) {
                this.start_pc = start_pc;
                this.line_number = line_number;
            }
        }
        static class LocalVariable {
            final int start_pc;
            final int length;
            final int name_index;
            final int descriptor_index;
            final int index;
            LocalVariable(int start_pc, int length, int name_index, int descriptor_index, int index) {
                this.start_pc = start_pc;
                this.length = length;
                this.name_index = name_index;
                this.descriptor_index = descriptor_index;
                this.index = index;
            }
        }
        static class LocalVariableType {
            final int start_pc;
            final int length;
            final int name_index;
            final int signature_index;
            final int index;
            LocalVariableType(int start_pc, int length, int name_index, int signature_index, int index) {
                this.start_pc = start_pc;
                this.length = length;
                this.name_index = name_index;
                this.signature_index = signature_index;
                this.index = index;
            }
        }
        static class Parameter {
            final int name_index;
            final int access_flags;
            Parameter(int name_index, int access_flags) {
                this.name_index = name_index;
                this.access_flags = access_flags;
            }
        }
        static class Enum {
            final int type_name_index;
            final int const_name_index;
            Enum(int type_name_index, int const_name_index) {
                this.type_name_index = type_name_index;
                this.const_name_index = const_name_index;
            }
        }
        // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.6
        class InnerClass {
            final int inner_class_info_index;
            final int outer_class_info_index;
            final int inner_name_index;
            final int inner_class_access_flags;
            InnerClass(int inner_class_info_index, int outer_class_info_index, int inner_name_index, int inner_class_access_flags) {
                this.inner_class_info_index = inner_class_info_index;
                this.outer_class_info_index = outer_class_info_index;
                this.inner_name_index = inner_name_index;
                this.inner_class_access_flags = inner_class_access_flags;
            }
            String inner_class_info() {
                return constant_pool.class_at(inner_class_info_index);
            }
            boolean is_member_of_class() {
                return outer_class_info_index != 0;
            }
            String outer_class_info() {
                assert is_member_of_class();
                return constant_pool.class_at(outer_class_info_index);
            }
            boolean is_anonymous() {
                return inner_name_index == 0;
            }
            String inner_name() {
                assert !is_anonymous();
                return constant_pool.utf8_at(inner_name_index);
            }
            int inner_class_access_flags() {
                return inner_class_access_flags;
            }
        }
        class EnclosingMethod {
            final int class_index; // CONSTANT_Class_info
            final int method_index; // CONSTANT_NameAndType_info
            EnclosingMethod(int class_index, int method_index) {
                this.class_index = class_index;
                this.method_index = method_index;
            }
            String class_name() {
                return constant_pool.class_at(class_index);
            }
            NameAndType method() {
                return constant_pool.name_and_type_at(method_index);
            }
            boolean is_immediately_enclosed() {
                return method_index == 0;
            }
        }
        static class BootstrapMethod {
            final int bootstrap_method_ref;
            final int[] bootstrap_argument_index;
            BootstrapMethod(int bootstrap_method_ref, int[] bootstrap_argument_index) {
                this.bootstrap_method_ref = bootstrap_method_ref;
                this.bootstrap_argument_index = bootstrap_argument_index;
            }
        }

        static class Annotation {
            final int type_index; // utf8_at
            final ElementValuePair[] pairs;
            Annotation(int type_index, ElementValuePair[] pairs) {
                this.type_index = type_index;
                this.pairs = pairs;
            }
        }
        @SuppressWarnings("unused")
        static class ElementValue {
            final char tag;
            final Object value;
            ElementValue(char tag, Object value) {
                this.tag = tag;
                this.value = value;
            }
            // Object value() { switch(tag) {} }
            byte byte_value()               { assert tag == 'B';return (byte) value; }
            char char_value()               { assert tag == 'C';return (char) value; }
            double double_value()           { assert tag == 'D';return (double) value; }
            float float_value()             { assert tag == 'F';return (float) value; }
            int int_value()                 { assert tag == 'I';return (int) value; }
            long long_value()               { assert tag == 'J';return (long) value; }
            short short_value()             { assert tag == 'S';return (short) value; }
            boolean bool_value()            { assert tag == 'Z';return (boolean) value; }
            String string_value()           { assert tag == 's';return (String) value; }
            Enum enum_value()               { assert tag == 'e';return (Enum) value; }
            int class_value()               { assert tag == 'c';return (int) value; }
            Annotation annotation_value()   { assert tag == '@';return (Annotation) value; }
            ElementValue[] array_value()    { assert tag == '[';return (ElementValue[]) value; }
        }
        static class ElementValuePair {
            final int element_name_index;
            final ElementValue value;
            ElementValuePair(int element_name_index, ElementValue value) {
                this.element_name_index = element_name_index;
                this.value = value;
            }
        }
        static class TypeAnnotation {
            final int target_type;
            final Object target_info; //
            final AnnotationTypePath[] type_path;
            final int type_index; // String descriptor = constant_pool.string_value(type_index);
            final ElementValuePair[] pairs;
            public TypeAnnotation(int target_type, Object target_info,
                                  AnnotationTypePath[] type_path,
                                  int type_index, ElementValuePair[] pairs) {
                this.target_type = target_type;
                this.target_info = target_info;
                this.type_path = type_path;
                this.type_index = type_index;
                this.pairs = pairs;
            }
        }
        static class TypeArgument {
            final int offset;
            final int type_argument_index;
            TypeArgument(int offset, int type_argument_index) {
                this.offset = offset;
                this.type_argument_index = type_argument_index;
            }
        }
        static class AnnotationTypePath {
            final int type_path_kind;
            final int type_argument_index;
            AnnotationTypePath(int type_path_kind, int type_argument_index) {
                this.type_path_kind = type_path_kind;
                this.type_argument_index = type_argument_index;
            }
        }
        static class TypeParameterBoundTarget {
            final int type_parameter_index;
            final int bound_index;
            TypeParameterBoundTarget(int type_parameter_index, int bound_index) {
                this.type_parameter_index = type_parameter_index;
                this.bound_index = bound_index;
            }
        }
        static class AnnotationLocalVarTarget {
            final int start_pc;
            final int length;
            final int index;
            AnnotationLocalVarTarget(int start_pc, int length, int index) {
                this.start_pc = start_pc;
                this.length = length;
                this.index = index;
            }
        }
    }

    class ConstantPool implements Iterable<Object> {
        final static String instance_init = "<init>";
        final static String class_init = "<clinit>";

        final ClassFile class_file;
        final Entry[] entries;
        final Object[] cache_; // todo
        ConstantPool(ClassFile class_file, Entry[] entries) {
            this.class_file = class_file;
            this.entries = entries;
            this.cache_ = new Object[entries.length];
        }
        public int tag(int idx) { return entries[idx].tag; }
        public Object at(int idx) {
            switch (entries[idx].tag) {
                case CONSTANT_Class:                return class_at(idx);
                case CONSTANT_Fieldref:             return field_ref_at(idx);
                case CONSTANT_Methodref:            return method_ref_at0(idx);
                case CONSTANT_InterfaceMethodref:   return interface_method_ref_at0(idx);
                case CONSTANT_String:               return string_at(idx);
                case CONSTANT_Integer:              return int_at(idx);
                case CONSTANT_Float:                return float_at(idx);
                case CONSTANT_Long:                 return long_at(idx);
                case CONSTANT_Double:               return double_at(idx);
                case CONSTANT_NameAndType:          return name_and_type_at(idx);
                case CONSTANT_Utf8:                 return utf8_at(idx);
                case CONSTANT_MethodHandle:         return method_handle_at(idx);
                case CONSTANT_MethodType:           return method_type_at(idx);
                case CONSTANT_Dynamic:              return dynamic_at(idx);
                case CONSTANT_InvokeDynamic:        return invoke_dynamic_at(idx);
                case CONSTANT_Module:               return module_at(idx);
                case CONSTANT_Package:              return package_at(idx);
                case CONSTANT_Unicode:
                default: throw new AssertionError();
            }
        }
        boolean boolean_at(int idx)  { return entries[idx].boolean_value(); }
        byte byte_at(int idx)        { return entries[idx].byte_value(); }
        short short_at(int idx)      { return entries[idx].short_value(); }
        int int_at(int idx)          { return entries[idx].int_value(); }
        long long_at(int idx)        { return entries[idx].long_value(); }
        float float_at(int idx)      { return entries[idx].float_value(); }
        double double_at(int idx)    { return entries[idx].double_value(); }
        char char_at(int idx)        { return entries[idx].char_value(); }
        String utf8_at(int idx)      { return entries[idx].utf8_value(); }
        String string_at(int idx)    { return utf8_at(entries[idx].string_value()); }
        String class_at(int idx)     { return utf8_at(entries[idx].class_value()); }
        NameAndType name_and_type_at(int idx) {
            Ref ref = entries[idx].name_and_type_ref();
            int name_index = ref.constant_pool_index1;
            int descriptor_index = ref.constant_pool_index2;
            String name = utf8_at(name_index);
            String descriptor = utf8_at(descriptor_index);
            return new NameAndType(name, descriptor);
        }
        public FieldRef field_ref_at(int idx) {
            FieldRef field_ref = (FieldRef) cache_[idx];
            if (field_ref == null) {
                field_ref = field_ref_at0(idx);
                cache_[idx] = field_ref;
            }
            return field_ref;
        }
        FieldRef field_ref_at0(int idx) {
            Ref ref = entries[idx].field_ref();
            int class_index = ref.constant_pool_index1;
            int name_and_type_index = ref.constant_pool_index2;
            String class_name = class_at(class_index);
            assert '<' != class_name.charAt(0) || class_name.equals(instance_init);
            NameAndType name_and_type = name_and_type_at(name_and_type_index);
            return new FieldRef(class_name, name_and_type);
        }
        MethodRef method_ref_at0(int idx) {
            Ref ref = entries[idx].method_ref();
            int class_index = ref.constant_pool_index1;
            int name_and_type_index = ref.constant_pool_index2;
            String class_name = class_at(class_index);
            assert '<' != class_name.charAt(0) || class_name.equals(instance_init);
            NameAndType name_and_type = name_and_type_at(name_and_type_index);
            return new MethodRef(class_name, name_and_type);
        }
        InterfaceMethodRef interface_method_ref_at0(int idx) {
            Ref ref = entries[idx].interface_method_ref();
            int class_index = ref.constant_pool_index1;
            int name_and_type_index = ref.constant_pool_index2;
            String class_name = class_at(class_index);
            assert '<' != class_name.charAt(0) || class_name.equals(instance_init);
            NameAndType name_and_type = name_and_type_at(name_and_type_index);
            return new InterfaceMethodRef(class_name, name_and_type);
        }
        public MethodRef method_ref_at(int idx, int instruction) {
            MethodRef method_ref = (MethodRef) cache_[idx];
            if (method_ref == null) {
                method_ref = method_ref_at0(idx, instruction);
                cache_[idx] = method_ref;
            }
            return method_ref;
        }
        MethodRef method_ref_at0(int idx, int instruction) {
            int tag = entries[idx].tag;
            assert tag == CONSTANT_Methodref || tag == CONSTANT_InterfaceMethodref;
            switch (instruction) {
                case INVOKEVIRTUAL: return method_ref_at0(idx);
                case INVOKESTATIC:
                case INVOKESPECIAL:
                    if (entries[idx].tag == CONSTANT_Methodref) {
                        return method_ref_at0(idx);
                    } else {
                        return interface_method_ref_at0(idx);
                    }
                case INVOKEINTERFACE: return interface_method_ref_at0(idx);
                default: throw new AssertionError();
            }
        }
        MethodHandle method_handle_at(int idx) {
            assert entries[idx].tag == CONSTANT_MethodHandle;
            Ref ref = entries[idx].ref_value();
            int reference_kind = ref.constant_pool_index1;
            int reference_index = ref.constant_pool_index2;
            ReferenceKind ref_kind = ReferenceKind.of(reference_kind);
            Reference reference;
            switch (ref_kind) {
                case REF_getField:
                case REF_getStatic:
                case REF_putField:
                case REF_putStatic:
                    reference = field_ref_at(reference_index);
                    break;
                case REF_invokeVirtual:
                    reference = method_ref_at0(reference_index);
                    assert !((MethodRef) reference).name_and_type.name.equals(instance_init);
                    break;
                case REF_newInvokeSpecial:
                    reference = method_ref_at0(reference_index);
                    assert ((MethodRef) reference).name_and_type.name.equals(instance_init);
                    break;
                case REF_invokeStatic:
                case REF_invokeSpecial:
                    if (class_file.major_version < JAVA_8_VERSION) {
                        reference = method_ref_at0(reference_index);
                        assert !((MethodRef) reference).name_and_type.name.equals(instance_init);
                    } else {
                        if (entries[reference_index].tag == CONSTANT_Methodref) {
                            reference = method_ref_at0(reference_index);
                            assert !((MethodRef) reference).name_and_type.name.equals(instance_init);
                        } else {
                            reference = interface_method_ref_at0(reference_index);
                            assert !((InterfaceMethodRef) reference).name_and_type.name.equals(instance_init);
                        }
                    }
                    break;
                case REF_invokeInterface: // must not <init>
                    reference = interface_method_ref_at0(reference_index);
                    assert !((InterfaceMethodRef) reference).name_and_type.name.equals(instance_init);
                    break;
                default:
                    throw new AssertionError();
            }
            return new MethodHandle(ref_kind, reference);
        }
        String method_type_at(int idx) { return utf8_at(entries[idx].method_type_ref()); }
        private BootstrapMethod bootstrap_method(Ref ref) {
            int bootstrap_method_attr_index = ref.constant_pool_index1;
            assert class_file.bootstrap_methods != null;
            ClassFile.BootstrapMethod bootstrap_method = class_file.bootstrap_methods[bootstrap_method_attr_index];
            assert bootstrap_method != null;
            MethodHandle method_handle = method_handle_at(bootstrap_method.bootstrap_method_ref);
            int[] args_idx = bootstrap_method.bootstrap_argument_index;
            Object[] arguments = new Object[args_idx.length];
            for (int i = 0; i < args_idx.length; i++) {
                arguments[i] = at(args_idx[i]);
            }
            return new BootstrapMethod(method_handle, arguments);
        }
        Dynamic dynamic_at(int idx) {
            Ref ref = entries[idx].dynamic_ref();
            int name_and_type_index = ref.constant_pool_index2;
            NameAndType name_and_type = name_and_type_at(name_and_type_index);
            return new Dynamic(bootstrap_method(ref), name_and_type);
        }
        public InvokeDynamic invoke_dynamic_at(int idx) {
            InvokeDynamic invoke_dynamic = (InvokeDynamic) cache_[idx];
            if (invoke_dynamic == null) {
                invoke_dynamic = invoke_dynamic_at0(idx);
                cache_[idx] = invoke_dynamic;
            }
            return invoke_dynamic;
        }
        InvokeDynamic invoke_dynamic_at0(int idx) {
            Ref ref = entries[idx].invoke_dynamic_ref();
            int name_and_type_index = ref.constant_pool_index2;
            NameAndType name_and_type = name_and_type_at(name_and_type_index);
            return new InvokeDynamic(bootstrap_method(ref), name_and_type);
        }
        String module_at(int idx) { return utf8_at(entries[idx].module_ref()); }
        String package_at(int idx) { return utf8_at(entries[idx].package_ref()); }

        @NotNull
        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                int i = 1;

                @Override
                public boolean hasNext() {
                    // long double 占两个坑... 不需要 while 不会连续出现空的坑
                    if (i < entries.length && entries[i] == null) i++;
                    return i < entries.length;
                }

                @Override public Object next() {
                    return new CpInfo(entries[i].tag, at(i++));
                }
            };
        }

        static class NameAndType {
            final String name;
            final String descriptor;
            // String[] types_cache_; // todo
            int[] types_size_cache_; // todo
            NameAndType(String name, String descriptor) {
                this.name = name;
                this.descriptor = descriptor;
            }
            @Override
            public String toString() {
                return "NameAndType{" +
                        "name='" + name + '\'' +
                        ", descriptor='" + descriptor + '\'' +
                        '}';
            }
        }
        interface Reference {}
        static class FieldRef implements Reference {
            final String class_name; // class or interface
            final NameAndType name_and_type;
            int slot_cache_ = -1; // todo 暂时先缓存这里吧...
//            ZClass z_class_cache_ = null; // todo 暂时先缓存这里吧...
            ZField z_field_cache_ = null; // todo 暂时先缓存这里吧...
            FieldRef(String class_name, NameAndType name_and_type) {
                this.class_name = class_name;
                this.name_and_type = name_and_type;
            }
            @Override
            public String toString() {
                return "FieldRef{" +
                        "class_name='" + class_name + '\'' +
                        ", name_and_type=" + name_and_type +
                        '}';
            }
        }
        static class MethodRef implements Reference {
            // 对于非 InterfaceMethodRef must be class
            // 对于 InterfaceMethodRef must be interface
            final String class_name;
            final NameAndType name_and_type;
            ZClass z_class_cache_; // todo
            MethodRef(String class_name, NameAndType name_and_type) {
                this.class_name = class_name;
                this.name_and_type = name_and_type;
            }
            @Override
            public String toString() {
                return "MethodRef{" +
                        "class_name='" + class_name + '\'' +
                        ", name_and_type=" + name_and_type +
                        '}';
            }
        }
        static class InterfaceMethodRef extends MethodRef {
            InterfaceMethodRef(String interface_name, NameAndType name_and_type) {
                super(interface_name, name_and_type);
            }
            @Override
            public String toString() {
                return "InterfaceMethodRef{" +
                        "interface_name='" + class_name + '\'' +
                        ", name_and_type=" + name_and_type +
                        '}';
            }
        }
        static class MethodHandle {
            final ReferenceKind reference_kind;
            final Reference reference;
            MethodHandle(ReferenceKind reference_kind, Reference reference) {
                this.reference_kind = reference_kind;
                this.reference = reference;
            }
            @Override
            public String toString() {
                return "MethodHandle{" +
                        "reference_kind=" + reference_kind +
                        ", reference=" + reference +
                        '}';
            }
        }
        static class BootstrapMethod {
            final MethodHandle method_handle;
            final Object[] arguments;
            BootstrapMethod(MethodHandle method_handle, Object[] arguments) {
                this.method_handle = method_handle;
                this.arguments = arguments;
            }
            @Override
            public String toString() {
                return "BootstrapMethod{" +
                        "method_handle=" + method_handle +
                        ", arguments=" + Arrays.toString(arguments) +
                        '}';
            }
        }
        static class Dynamic {
            final BootstrapMethod bootstrap_method;
            final NameAndType name_and_type; // field descriptor
            Dynamic(BootstrapMethod bootstrap_method, NameAndType name_and_type) {
                this.bootstrap_method = bootstrap_method;
                this.name_and_type = name_and_type;
            }
            @Override
            public String toString() {
                return "Dynamic{" +
                        "bootstrap_method=" + bootstrap_method +
                        ", name_and_type=" + name_and_type +
                        '}';
            }
        }
        static class InvokeDynamic {
            final BootstrapMethod bootstrap_method;
            final NameAndType name_and_type; // method descriptor
            InvokeDynamic(BootstrapMethod bootstrap_method, NameAndType name_and_type) {
                this.bootstrap_method = bootstrap_method;
                this.name_and_type = name_and_type;
            }
            @Override
            public String toString() {
                return "InvokeDynamic{" +
                        "bootstrap_method=" + bootstrap_method +
                        ", name_and_type=" + name_and_type +
                        '}';
            }
        }
        static class CpInfo {
            public final int tag;
            public final Object value;
            CpInfo(int tag, Object value) {
                this.tag = tag;
                this.value = value;
            }
            @Override
            public String toString() {
                return "CpInfo{" +
                        "tag=" + tag +
                        ", value=" + value +
                        '}';
            }
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            BiFunction<Integer, String, StringBuilder> ref1 = (i, tag) -> buf.append("#").append(i).append(" = ").append(tag).append("\t")
                    .append("#").append(((Ref) entries[i].val).constant_pool_index1).append("\n");
            BiFunction<Integer, String, StringBuilder> ref2 = (i, tag) -> buf.append("#").append(i).append(" = ").append(tag).append("\t")
                    .append("#").append(((Ref) entries[i].val).constant_pool_index1)
                    .append(".")
                    .append("#").append(((Ref) entries[i].val).constant_pool_index2).append("\n");
            for (int i = 1; i < entries.length; i++) {
                if (entries[i] == null) continue;
                switch (entries[i].tag) {
                    case CONSTANT_Class:                ref1.apply(i, "Class");break;
                    case CONSTANT_Fieldref:             ref2.apply(i, "FieldRef");break;
                    case CONSTANT_Methodref:            ref2.apply(i, "MethodRef");break;
                    case CONSTANT_InterfaceMethodref:   ref2.apply(i, "InterfaceMethodref");break;
                    case CONSTANT_String:               ref1.apply(i, "String");break;
                    case CONSTANT_Integer:              buf.append("#").append(i).append(" = ").append("Integer").append("\t").append(entries[i].val).append("\n");break;
                    case CONSTANT_Float:                buf.append("#").append(i).append(" = ").append("Float").append("\t").append(entries[i].val).append("F").append("\n");break;
                    case CONSTANT_Long:                 buf.append("#").append(i).append(" = ").append("Long").append("\t").append(entries[i].val).append("L").append("\n");break;
                    case CONSTANT_Double:               buf.append("#").append(i).append(" = ").append("Double").append("\t").append(entries[i].val).append("D").append("\n");break;
                    case CONSTANT_NameAndType:          ref2.apply(i, "NameAndType");break;
                    case CONSTANT_Utf8:
                        String escape = ((String) entries[i].val).replace("\n", "\\n").replace("\r", "\\r");
                        buf.append("#").append(i).append(" = ").append("Utf8").append("\t").append(escape).append("\n");
                        break;
                    case CONSTANT_MethodHandle:         ref2.apply(i, "MethodHandle");break;
                    case CONSTANT_MethodType:           ref1.apply(i, "MethodType");break;
                    case CONSTANT_Dynamic:              ref2.apply(i, "Dynamic");break;
                    case CONSTANT_InvokeDynamic:        ref2.apply(i, "InvokeDynamic");break;
                    case CONSTANT_Module:               ref1.apply(i, "Module");break;
                    case CONSTANT_Package:              ref1.apply(i, "Package");break;
                }
            }
            return buf.toString();
        }

        static class Ref {
            final int constant_pool_index1;
            final int constant_pool_index2;
            Ref(int idx1, int idx2) {
                this.constant_pool_index1 = idx1;
                this.constant_pool_index2 = idx2;
            }
        }

        @SuppressWarnings("unused")
        static class Entry {
            final int tag;
            final Object val; // int | float | long | double | string | methodHandle | methodType | dynamic | ref
            Entry(int tag, Object val) {
                this.tag = tag;
                this.val = val;
            }
            private Ref ref_value()     { return ((Ref) val); }
            boolean boolean_value()     { return int_value() == 1; }
            byte byte_value()           { return ((byte) int_value()); }
            short short_value()         { return ((short) int_value()); }
            int int_value()             { assert tag == CONSTANT_Integer; return ((Integer) val); }
            long long_value()           { assert tag == CONSTANT_Long; return ((Long) val); }
            float float_value()         { assert tag == CONSTANT_Float; return ((Float) val); }
            double double_value()       { assert tag == CONSTANT_Double; return ((Double) val); }
            char char_value()           { return ((char) int_value()); }
            String utf8_value()         { assert tag == CONSTANT_Utf8; return ((String) val); }
            int string_value()          { assert tag == CONSTANT_String; return ref_value().constant_pool_index1; }
            int class_value()           { assert tag == CONSTANT_Class; return ref_value().constant_pool_index1; }
            Ref field_ref()             { assert tag == CONSTANT_Fieldref; return ref_value(); }
            Ref method_ref()            { assert tag == CONSTANT_Methodref; return ref_value(); }
            Ref interface_method_ref()  { assert tag == CONSTANT_InterfaceMethodref; return ref_value(); }
            Ref name_and_type_ref()     { assert tag == CONSTANT_NameAndType; return ref_value(); }
            Ref method_handle_ref()     { assert tag == CONSTANT_MethodHandle; return ref_value(); }
            int method_type_ref()       { assert tag == CONSTANT_MethodType; return ref_value().constant_pool_index1; }
            Ref dynamic_ref()           { assert tag == CONSTANT_Dynamic; return ref_value(); }
            Ref invoke_dynamic_ref()    { assert tag == CONSTANT_InvokeDynamic; return ref_value(); }
            int module_ref()            { assert tag == CONSTANT_Module; return ref_value().constant_pool_index1; }
            int package_ref()           { assert tag == CONSTANT_Package; return ref_value().constant_pool_index1; }
        }

        static Ref ref1(int idx) { return new Ref(idx, -1); }
        static Ref ref2(int idx1, int idx2) { return new Ref(idx1, idx2); }
        static Entry cpEntry(int tag, Object val) { return new Entry(tag, val); }
    }

    // 参见 openjdk jdk.hotspot.agent/share/classes/sun/jvm/hotspot/runtime/ClassConstants.java
    @SuppressWarnings("unused")
    interface Constants {
        int CLASSFILE_MAGIC             = 0xCAFEBABE;

        int JAVA_1_5_VERSION            = 49;
        int JAVA_6_VERSION              = 50;
        int JAVA_7_VERSION              = 51;
        int JAVA_8_VERSION              = 52;
        int JAVA_9_VERSION              = 53;
        int JAVA_10_VERSION             = 54;
        int JAVA_11_VERSION             = 55;
        int JAVA_12_VERSION             = 56;
        int JAVA_13_VERSION             = 57;
        int JAVA_14_VERSION             = 58;

        int STACKMAP_ATTRIBUTE_MAJOR_VERSION    = JAVA_6_VERSION;
        int INVOKEDYNAMIC_MAJOR_VERSION         = JAVA_7_VERSION;
        int NO_RELAX_ACCESS_CTRL_CHECK_VERSION  = JAVA_8_VERSION;
        int DYNAMICCONSTANT_MAJOR_VERSION       = JAVA_11_VERSION;

        int MAX_ARGS_SIZE = 255;
        int MAX_CODE_SIZE = 65535;

        // 参见 hotspot/share/classfile/classFileParser.cpp
        // class file format tags
        String tag_source_file =                            "SourceFile";
        String tag_inner_classes =                          "InnerClasses";
        String tag_nest_members =                           "NestMembers";
        String tag_nest_host =                              "NestHost";
        String tag_constant_value =                         "ConstantValue";
        String tag_code =                                   "Code";
        String tag_exceptions =                             "Exceptions";
        String tag_line_number_table =                      "LineNumberTable";
        String tag_local_variable_table =                   "LocalVariableTable";
        String tag_local_variable_type_table =              "LocalVariableTypeTable";
        String tag_method_parameters =                      "MethodParameters";
        String tag_stack_map_table =                        "StackMapTable";
        String tag_synthetic =                              "Synthetic";
        String tag_deprecated =                             "Deprecated";
        String tag_source_debug_extension =                 "SourceDebugExtension";
        String tag_signature =                              "Signature";
        String tag_record =                                 "Record";
        String tag_runtime_visible_annotations =            "RuntimeVisibleAnnotations";
        String tag_runtime_invisible_annotations =          "RuntimeInvisibleAnnotations";
        String tag_runtime_visible_parameter_annotations =  "RuntimeVisibleParameterAnnotations";
        String tag_runtime_invisible_parameter_annotations ="RuntimeInvisibleParameterAnnotations";
        String tag_annotation_default =                     "AnnotationDefault";
        String tag_runtime_visible_type_annotations =       "RuntimeVisibleTypeAnnotations";
        String tag_runtime_invisible_type_annotations =     "RuntimeInvisibleTypeAnnotations";
        String tag_enclosing_method =                       "EnclosingMethod";
        String tag_bootstrap_methods =                      "BootstrapMethods";

        // constant pool constant types - from JVM spec.
        int CONSTANT_Utf8               = 1;
        int CONSTANT_Unicode            = 2; // unused
        int CONSTANT_Integer            = 3;
        int CONSTANT_Float              = 4;
        int CONSTANT_Long               = 5;
        int CONSTANT_Double             = 6;
        int CONSTANT_Class              = 7;
        int CONSTANT_String             = 8;
        int CONSTANT_Fieldref           = 9;
        int CONSTANT_Methodref          = 10;
        int CONSTANT_InterfaceMethodref = 11;
        int CONSTANT_NameAndType        = 12;
        int CONSTANT_MethodHandle       = 15;
        int CONSTANT_MethodType         = 16;
        int CONSTANT_Dynamic            = 17;
        int CONSTANT_InvokeDynamic      = 18;
        int CONSTANT_Module             = 19;
        int CONSTANT_Package            = 20;

        // CONSTANT_MethodHandle subtypes
        enum ReferenceKind {
            REF_getField(1),
            REF_getStatic(2),
            REF_putField(3),
            REF_putStatic(4),
            REF_invokeVirtual(5),
            REF_invokeStatic(6),
            REF_invokeSpecial(7),
            REF_newInvokeSpecial(8),
            REF_invokeInterface(9);
            final int tag;
            ReferenceKind(int tag) { this.tag = tag; }
            static ReferenceKind of(int tag) {
                switch(tag) {
                    case 1: return REF_getField;
                    case 2: return REF_getStatic;
                    case 3: return REF_putField;
                    case 4: return REF_putStatic;
                    case 5: return REF_invokeVirtual;
                    case 6: return REF_invokeStatic;
                    case 7: return REF_invokeSpecial;
                    case 8: return REF_newInvokeSpecial;
                    case 9: return REF_invokeInterface;
                    default: throw new AssertionError();
                }
            }
        }
    }

    // 参见 jvm.h
    @SuppressWarnings("unused")
    interface AccessFlags {
        int ACC_PUBLIC       = 0x0001; /* visible to everyone */
        int ACC_PRIVATE      = 0x0002; /* visible only to the defining class */
        int ACC_PROTECTED    = 0x0004; /* visible to subclasses */
        int ACC_STATIC       = 0x0008; /* instance variable is static */
        int ACC_FINAL        = 0x0010; /* no further subclassing, overriding */
        int ACC_SYNCHRONIZED = 0x0020; /* wrap method call in monitor lock */
        int ACC_SUPER        = 0x0020; /* funky handling of invokespecial */
        int ACC_VOLATILE     = 0x0040; /* can not cache in registers */
        int ACC_BRIDGE       = 0x0040; /* bridge method generated by compiler */
        int ACC_TRANSIENT    = 0x0080; /* not persistant */
        int ACC_VARARGS      = 0x0080; /* method declared with variable number of args */
        int ACC_NATIVE       = 0x0100; /* implemented in C */
        int ACC_INTERFACE    = 0x0200; /* class is an interface */
        int ACC_ABSTRACT     = 0x0400; /* no definition provided */
        int ACC_STRICT       = 0x0800; /* strict floating point */
        int ACC_SYNTHETIC    = 0x1000; /* compiler-generated class, method or field */
        int ACC_ANNOTATION   = 0x2000; /* annotation type */
        int ACC_ENUM         = 0x4000; /* field is declared as element of enum */
        int ACC_MODULE       = 0x8000; /* Is a module, not a class or interface.*/

        int RECOGNIZED_CLASS_MODIFIERS   = (ACC_PUBLIC |
                ACC_FINAL |
                ACC_SUPER |
                ACC_INTERFACE |
                ACC_ABSTRACT |
                ACC_ANNOTATION |
                ACC_ENUM |
                ACC_SYNTHETIC);
        int RECOGNIZED_FIELD_MODIFIERS  = (ACC_PUBLIC |
                ACC_PRIVATE |
                ACC_PROTECTED |
                ACC_STATIC |
                ACC_FINAL |
                ACC_VOLATILE |
                ACC_TRANSIENT |
                ACC_ENUM |
                ACC_SYNTHETIC);
        int RECOGNIZED_METHOD_MODIFIERS  = (ACC_PUBLIC |
                ACC_PRIVATE |
                ACC_PROTECTED |
                ACC_STATIC |
                ACC_FINAL |
                ACC_SYNCHRONIZED |
                ACC_BRIDGE |
                ACC_VARARGS |
                ACC_NATIVE |
                ACC_ABSTRACT |
                ACC_STRICT |
                ACC_SYNTHETIC);
    }
}