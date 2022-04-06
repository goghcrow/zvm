package zvm;

import org.jetbrains.annotations.Nullable;
import zvm.ClassParser.ClassFile;
import zvm.ClassParser.ConstantPool;
import zvm.ClassParser.ConstantPool.InvokeDynamic;

import java.io.UnsupportedEncodingException;

import static zvm.Bytecodes.*;
import static zvm.ClassParser.AccessFlags.*;
import static zvm.ClassParser.Constants.*;
import static zvm.ClassParser.Constants.ReferenceKind.REF_invokeStatic;

/**
 * 参考 https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-2.html#jvms-2.11.1
 * @author chuxiaofeng
 */
class Interpreter {

    static class CodeBytes extends ClassParser.Bytes {
        CodeBytes(byte[] bytes) { super(bytes); }
        int pc() { return is.pos(); }
        void pc(int pos) { is.pos(pos); }
    }

    /*
    https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-2.html#jvms-2.11.1
    https://ozh.github.io/ascii-tables/
    http://asciiflow.com/
    https://textik.com/

    Table 2.11.1-A. Type support in the Java Virtual Machine instruction set
    ┌───────────┬─────────┬─────────┬───────────┬─────────┬─────────┬─────────┬─────────┬───────────┐
    │  opcode   │  byte   │  short  │    int    │  long   │  float  │ double  │  char   │ reference │
    ├───────────┼─────────┼─────────┼───────────┼─────────┼─────────┼─────────┼─────────┼───────────┤
    │ Tipush    │ bipush  │ sipush  │           │         │         │         │         │           │
    │ Tconst    │         │         │ iconst    │ lconst  │ fconst  │ dconst  │         │ aconst    │
    │ Tload     │         │         │ iload     │ lload   │ fload   │ dload   │         │ aload     │
    │ Tstore    │         │         │ istore    │ lstore  │ fstore  │ dstore  │         │ astore    │
    │ Tinc      │         │         │ iinc      │         │         │         │         │           │
    │ Taload    │ baload  │ saload  │ iaload    │ laload  │ faload  │ daload  │ caload  │ aaload    │
    │ Tastore   │ bastore │ sastore │ iastore   │ lastore │ fastore │ dastore │ castore │ aastore   │
    │ Tadd      │         │         │ iadd      │ ladd    │ fadd    │ dadd    │         │           │
    │ Tsub      │         │         │ isub      │ lsub    │ fsub    │ dsub    │         │           │
    │ Tmul      │         │         │ imul      │ lmul    │ fmul    │ dmul    │         │           │
    │ Tdiv      │         │         │ idiv      │ ldiv    │ fdiv    │ ddiv    │         │           │
    │ Trem      │         │         │ irem      │ lrem    │ frem    │ drem    │         │           │
    │ Tneg      │         │         │ ineg      │ lneg    │ fneg    │ dneg    │         │           │
    │ Tshl      │         │         │ ishl      │ lshl    │         │         │         │           │
    │ Tshr      │         │         │ ishr      │ lshr    │         │         │         │           │
    │ Tushr     │         │         │ iushr     │ lushr   │         │         │         │           │
    │ Tand      │         │         │ iand      │ land    │         │         │         │           │
    │ Tor       │         │         │ ior       │ lor     │         │         │         │           │
    │ Txor      │         │         │ ixor      │ lxor    │         │         │         │           │
    │ i2T       │ i2b     │ i2s     │           │ i2l     │ i2f     │ i2d     │         │           │
    │ l2T       │         │         │ l2i       │         │ l2f     │ l2d     │         │           │
    │ f2T       │         │         │ f2i       │ f2l     │         │ f2d     │         │           │
    │ d2T       │         │         │ d2i       │ d2l     │ d2f     │         │         │           │
    │ Tcmp      │         │         │           │ lcmp    │         │         │         │           │
    │ Tcmpl     │         │         │           │         │ fcmpl   │ dcmpl   │         │           │
    │ Tcmpg     │         │         │           │         │ fcmpg   │ dcmpg   │         │           │
    │ if_TcmpOP │         │         │ if_icmpOP │         │         │         │         │ if_acmpOP │
    │ Treturn   │         │         │ ireturn   │ lreturn │ freturn │ dreturn │         │ areturn   │
    └───────────┴─────────┴─────────┴───────────┴─────────┴─────────┴─────────┴─────────┴───────────┘

    Table 2.11.1-B. Actual and Computational types in the Java Virtual Machine
    ┌───────────────┬────────────────────┬──────────┐
    │  Actual type  │ Computational type │ Category │
    ├───────────────┼────────────────────┼──────────┤
    │ boolean       │ int                │        1 │
    │ byte          │ int                │        1 │
    │ char          │ int                │        1 │
    │ short         │ int                │        1 │
    │ int           │ int                │        1 │
    │ float         │ float              │        1 │
    │ reference     │ reference          │        1 │
    │ returnAddress │ returnAddress      │        1 │
    │ long          │ long               │        2 │
    │ double        │ double             │        2 │
    └───────────────┴────────────────────┴──────────┘
    */

    // 这个方法可以做成 OO 的方式（不过我不想）：
    // 把局部变量表、操作数栈、pc、sp 等等直接维护在 frame 中
    // 把不同字节码的 handler 的都做成单独的与字节码同名的方法
    // 然后使用反射调用来分派字节码的处理逻辑..
    @SuppressWarnings("RedundantCast")
    static Object interpret(VM vm, ZMethod method, @Nullable ZObject instance, Object[] method_args) throws ZThrowable {
        ClassFile.Code code = method.code();
        ConstantPool cp = method.constant_pool();
        CodeBytes bytes = new CodeBytes(code.bytes);
        assert bytes.pos() == 0;

        // 参考 https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html
        // stack slot 默认 4byte
        // 8byte-value 压栈约定 stack[++sp] = null; stack[++sp] = long/double
        // 8byte-value 弹栈约定 stack[--sp]; stack[--sp] = null; (或者 sp--;)
        // 8byte-value 局部变量表约定 local[idx] = long/double; local[idx++] = null;

        // 局部变量表 slot = 4bytes
        // long double 理论需要占用两个 slot, 但是这里用 Object[] 表示, 不需要占用两个 slot, 第二个 slot 留空
        // [long|double, null] 字节码索引位置指向第一个 slot
        Object[] locals = new Object[code.max_locals];
        int locals_idx = 0;

        // 操作数栈 stack slot = 4bytes
        // long double 理论需要占用两个 slot, 但是这里用 Object[] 表示, 第一个 slot 留空
        // [null, long|double] 写的时候用了第二个存值, 懒得改了
        Object[] stack = new Object[code.max_stack];
        // stack pointer 栈指针, 标记栈顶
        int sp = 0;

        // 程序计数器，其实是上一条指令的地址，而不是常规的下一条，因为这里直接用byte_stream 按固定格式读，只要格式没问题
        // 所以这里偷懒用 byte_stream 的 pos 当 pc 用了
        // 下一条 byte_code 地址可以在"读完当前 byte_code 剩余数据之后", 用 bytes.pc() 来获取
        int pc;

        // 给 需要 index 的指令用的, bci: byte code index
        int idx;
        // 给跳转偏移用的
        int offset; // jmp offset

        // 用于 wide 指令, 扩展局部变量表索引
        boolean next_wide = false;

        // 主要给 Math 系列指令用的
        Object b1; // byte boolean
        char c1;
        short s1;
        int i1, i2;
        long l1, l2;
        float f1, f2;
        double d1, d2;
        Object a1, a2;

        ZArray z_arr;

        // 主要给 Stack manipulation 指令用的，比如 dup 系列
        Object fst, snd, trd, fth;

        ConstantPool.FieldRef field_ref;
        ConstantPool.MethodRef method_ref;

        // 填充 this 到局部变量表
        if ((method.access_flags() & ACC_STATIC) == 0) {
            locals[locals_idx++] = instance;
        }

        // 填充参数到局部变量表
//        String[] parameter_types = method.parameter_types();
//        assert parameter_types.length == method_args.length : method + " 参数个数错误";
//        for (int i = 0; i < parameter_types.length; i++) {
//            String type = parameter_types[i];
//            locals[locals_idx++] = method_args[i];
//            if (type.equals("long") || type.equals("double")) {
//                locals_idx++;
//                assert method_args[i] instanceof Long || method_args[i] instanceof Double;
//            }
//        }

        int[] parameter_type_size_cache_ = method.parameter_type_size_cache_;
        assert parameter_type_size_cache_.length == method_args.length : method + " 参数个数错误";
        for (int i = 0; i < parameter_type_size_cache_.length; i++) {
            locals[locals_idx++] = method_args[i];
            locals_idx += parameter_type_size_cache_[i];
        }

        // 把这些关键局部变量全部转移到 frame ???
        ZThread.Frame vm_stack_frame = vm.stacks.get().peek();
        assert vm_stack_frame != null;
//        vm_stack_frame.local_variables = locals;
//        vm_stack_frame.operand_stack = stack;

        while (true) {
            pc = bytes.pc();
            try {
                int instruction = bytes.u1();
                {
                    vm_stack_frame.program_counter = pc;
//                    vm_stack_frame.operand_stack_pointer = sp;
//                    vm_stack_frame.instruction = instruction;
                }

                switch (instruction) {
                    case NOP                  : //  0    0x00
                        break;
                    case ACONST_NULL          : //  1    0x01
                        stack[sp++] = null;
                        break;
                    case ICONST_M1            : //  2    0x02
                        stack[sp++] = -1;
                        break;
                    case ICONST_0             : //  3    0x03
                    case ICONST_1             : //  4    0x04
                    case ICONST_2             : //  5    0x05
                    case ICONST_3             : //  6    0x06
                    case ICONST_4             : //  7    0x07
                    case ICONST_5             : //  8    0x08
                        stack[sp++] = instruction - 3;
                        break;
                    case LCONST_0             : //  9    0x09
                    case LCONST_1             : // 10    0x0A
                        // spec: 把 long value push 到 stack
                        stack[sp++] = null;
                        stack[sp++] = ((long) instruction) - 9;
                        break;
                    case FCONST_0             : // 11    0x0B
                    case FCONST_1             : // 12    0x0C
                    case FCONST_2             : // 13    0x0D
                        // spec: 把 float value push 到 stack
                        stack[sp++] = ((float) instruction) - 11;
                        break;
                    case DCONST_0             : // 14    0x0E
                    case DCONST_1             : // 15    0x0F
                        // spec: 把 double value push 到 stack
                        stack[sp++] = null;
                        stack[sp++] = ((double) instruction) - 14;
                        break;
                    case BIPUSH               : // 16    0x10
                        // spec: 把 int value push 到 stack
                        stack[sp++] = (int) bytes.s1();
                        break;
                    case SIPUSH               : // 17    0x11
                        // spec: 把 int value push 到 stack
                        stack[sp++] = (int) bytes.s2();
                        break;
                    case LDC                  : // 18    0x12
                    case LDC_W                : // 19    0x13
                        idx = instruction == LDC ? bytes.u1() : bytes.u2();
                        switch (cp.tag(idx)) {
                            case CONSTANT_Integer:
                                stack[sp++] = cp.int_at(idx);
                                break;
                            case CONSTANT_Float:
                                stack[sp++] = cp.float_at(idx);
                                break;
                            case CONSTANT_String:
                                // https://stackoverflow.com/questions/5777131/java-string-intern-and-literal
                                // All literal strings and string-valued constant expressions are interned.
                                // 字面量和常量都要放在常量池
                                stack[sp++] = Natives.new_intern_string(vm, cp.string_at(idx));
                                break;
                            case CONSTANT_Class:
                                stack[sp++] = vm.load_class(cp.class_at(idx), false);
                                break;
                            // 给动态语言用的... 先不管
                            case CONSTANT_MethodHandle:
                            case CONSTANT_MethodType:
                            case CONSTANT_Dynamic:
                                throw new UnsupportedEncodingException(); // todo
                            default: throw new AssertionError();
                        }
                        break;
                    case LDC2_W               : // 20    0x14
                        idx = bytes.u2();
                        switch (cp.tag(idx)) {
                            case CONSTANT_Long:
                                stack[sp++] = null;
                                stack[sp++] = cp.long_at(idx);
                                break;
                            case CONSTANT_Double:
                                stack[sp++] = null;
                                stack[sp++] = cp.double_at(idx);
                                break;
                            default: throw new AssertionError();
                        }
                        break;
                    case ILOAD                : // 21    0x15
                    case FLOAD                : // 23    0x17
                    case ALOAD                : // 25    0x19
                        if (next_wide) {
                            idx = bytes.u2();
                            next_wide = false;
                        } else {
                            idx = bytes.u1();
                        }
                        stack[sp++] = locals[idx];
                        break;
                    case LLOAD                : // 22    0x16
                    case DLOAD                : // 24    0x18
                        if (next_wide) {
                            idx = bytes.u2();
                            next_wide = false;
                        } else {
                            idx = bytes.u1();
                        }
                        stack[sp++] = null;
                        stack[sp++] = locals[idx];
                        break;
                    case ILOAD_0              : // 26    0x1A
                    case ILOAD_1              : // 27    0x1B
                    case ILOAD_2              : // 28    0x1C
                    case ILOAD_3              : // 29    0x1D
                        stack[sp++] = locals[instruction - 26];
                        break;
                    case LLOAD_0              : // 30    0x1E
                    case LLOAD_1              : // 31    0x1F
                    case LLOAD_2              : // 32    0x20
                    case LLOAD_3              : // 33    0x21
                        stack[sp++] = null;
                        stack[sp++] = locals[instruction - 30];
                        break;
                    case FLOAD_0              : // 34    0x22
                    case FLOAD_1              : // 35    0x23
                    case FLOAD_2              : // 36    0x24
                    case FLOAD_3              : // 37    0x25
                        stack[sp++] = locals[instruction - 34];
                        break;
                    case DLOAD_0              : // 38    0x26
                    case DLOAD_1              : // 39    0x27
                    case DLOAD_2              : // 40    0x28
                    case DLOAD_3              : // 41    0x29
                        stack[sp++] = null;
                        stack[sp++] = locals[instruction - 38];
                        break;
                    case ALOAD_0              : // 42    0x2A
                    case ALOAD_1              : // 43    0x2B
                    case ALOAD_2              : // 44    0x2C
                    case ALOAD_3              : // 45    0x2D
                        stack[sp++] = locals[instruction - 42];
                        break;
                    // {ilfdabcs}aload 不能复用代码, 是因为 Object[] 不是 {int,long,float,double}[]的父类
                    // 不过这样也好, 可以检查类型
                    case IALOAD               : // 46    0x2E
                        idx = int_val(stack[--sp]);
                        z_arr = vm.check_null(((ZArray) stack[--sp]));
                        stack[sp++] = ((int) z_arr.index(idx));
                        break;
                    case LALOAD               : // 47    0x2F
                        idx = int_val(stack[--sp]);
                        z_arr = vm.check_null(((ZArray) stack[--sp]));
                        stack[sp++] = null;
                        stack[sp++] = ((long) z_arr.index(idx));
                        break;
                    case FALOAD               : // 48    0x30
                        idx = int_val(stack[--sp]);
                        z_arr = vm.check_null(((ZArray) stack[--sp]));
                        stack[sp++] = ((float) z_arr.index(idx));
                        break;
                    case DALOAD               : // 49    0x31
                        idx = int_val(stack[--sp]);
                        z_arr = vm.check_null(((ZArray) stack[--sp]));
                        stack[sp++] = null;
                        stack[sp++] = ((double) z_arr.index(idx));
                        break;
                    case AALOAD               : // 50    0x32
                        idx = int_val(stack[--sp]);
                        z_arr = vm.check_null(((ZArray) stack[--sp]));
                        stack[sp++] = ((ZObject) z_arr.index(idx));
                        break;
                    case BALOAD               : // 51    0x33
                        // spec : baload 只处理 byte and boolean arrays.
                        // spec: 把 int value push 到 stack
                        idx = int_val(stack[--sp]);
                        z_arr = (ZArray) stack[--sp];
                        if (z_arr.is_bool_array()) {
                            stack[sp++] = ((boolean) z_arr.index(idx)) ? 1 : 0;
                        } else if (z_arr.is_byte_array()) {
                            stack[sp++] = ((int) ((byte) z_arr.index(idx)));
                        } else {
                            throw new AssertionError();
                        }
                        break;
                    case CALOAD               : // 52    0x34
                        idx = int_val(stack[--sp]);
                        z_arr = vm.check_null(((ZArray) stack[--sp]));
                        stack[sp++] = ((int) ((char) z_arr.index(idx)));
                        break;
                    case SALOAD               : // 53    0x35
                        idx = int_val(stack[--sp]);
                        z_arr = vm.check_null(((ZArray) stack[--sp]));
                        stack[sp++] = ((int) ((short) z_arr.index(idx)));
                        break;
                    case LSTORE               : // 55    0x37
                    case DSTORE               : // 57    0x39
                        if (next_wide) {
                            idx = bytes.u2();
                            next_wide = false;
                        } else {
                            idx = bytes.u1();
                        }
                        locals[idx] = stack[--sp]; sp--;
                        break;
                    case ISTORE               : // 54    0x36
                    case FSTORE               : // 56    0x38
                    case ASTORE               : // 58    0x3A
                        if (next_wide) {
                            idx = bytes.u2();
                            next_wide = false;
                        } else {
                            idx = bytes.u1();
                        }
                        locals[idx] = stack[--sp];
                        break;
                    case ISTORE_0             : // 59    0x3B
                    case ISTORE_1             : // 60    0x3C
                    case ISTORE_2             : // 61    0x3D
                    case ISTORE_3             : // 62    0x3E
                        locals[instruction - 59] = stack[--sp];
                        break;
                    case LSTORE_0             : // 63    0x3F
                    case LSTORE_1             : // 64    0x40
                    case LSTORE_2             : // 65    0x41
                    case LSTORE_3             : // 66    0x42
                        locals[instruction - 63] = stack[--sp]; sp--;
                        break;
                    case FSTORE_0             : // 67    0x43
                    case FSTORE_1             : // 68    0x44
                    case FSTORE_2             : // 69    0x45
                    case FSTORE_3             : // 70    0x46
                        locals[instruction - 67] = stack[--sp];
                        break;
                    case DSTORE_0             : // 71    0x47
                    case DSTORE_1             : // 72    0x48
                    case DSTORE_2             : // 73    0x49
                    case DSTORE_3             : // 74    0x4A
                        locals[instruction - 71] = stack[--sp]; sp--;
                        break;
                    case ASTORE_0             : // 75    0x4B
                    case ASTORE_1             : // 76    0x4C
                    case ASTORE_2             : // 77    0x4D
                    case ASTORE_3             : // 78    0x4E
                        locals[instruction - 75] = stack[--sp];
                        break;
                    // {ilfdabcs}astore 代码不能复用原因同上
                    case IASTORE              : // 79    0x4F
                        i1 = int_val(stack[--sp]);
                        idx = int_val(stack[--sp]);
                        vm.check_null(((ZArray) stack[--sp])).index(idx, i1);
                        break;
                    case LASTORE              : // 80    0x50
                        l1 = long_val(stack[--sp]); sp--;
                        idx = int_val(stack[--sp]);
                        vm.check_null(((ZArray) stack[--sp])).index(idx, l1);
                        break;
                    case FASTORE              : // 81    0x51
                        f1 = ((float) stack[--sp]);
                        idx = int_val(stack[--sp]);
                        vm.check_null(((ZArray) stack[--sp])).index(idx, f1);
                        break;
                    case DASTORE              : // 82    0x52
                        d1 = ((double) stack[--sp]); sp--;
                        idx = int_val(stack[--sp]);
                        vm.check_null(((ZArray) stack[--sp])).index(idx, d1);
                        break;
                    case AASTORE              : // 83    0x53
                        a1 = stack[--sp];
                        idx = int_val(stack[--sp]);
                        vm.check_null(((ZArray) stack[--sp])).index(idx, (ZObject) a1);
                        break;
                    case BASTORE              : // 84    0x54
                        b1 = stack[--sp];
                        idx = int_val(stack[--sp]);
                        z_arr = ((ZArray) stack[--sp]);
                        if (z_arr.is_bool_array()) {
                            z_arr.index(idx, bool_val(b1));
                        } else if (z_arr.is_byte_array()) {
                            z_arr.index(idx, byte_val(b1));
                        } else {
                            throw new AssertionError();
                        }
                        break;
                    case CASTORE              : // 85    0x55
                        c1 = char_val(stack[--sp]);
                        idx = int_val(stack[--sp]);
                        vm.check_null(((ZArray) stack[--sp])).index(idx, c1);
                        break;
                    case SASTORE              : // 86    0x56
                        s1 = short_val(stack[--sp]);
                        idx = int_val(stack[--sp]);
                        vm.check_null(((ZArray) stack[--sp])).index(idx, s1);
                        break;
                    case POP                  : // 87    0x57
                        stack[--sp] = null;
                        break;
                    case POP2                 : // 88    0x58
                        stack[--sp] = null;
                        stack[--sp] = null;
                        break;
                    case DUP                  : // 89    0x59
                        //..., value →
                        //..., value, value
                        fst = stack[sp - 1];
                        stack[sp++] = fst;
                        break;
                    case DUP_X1               : // 90    0x5A
                        //..., value2, value1 →
                        //..., value1, value2, value1
                        fst = stack[--sp];
                        snd = stack[--sp];
                        stack[sp++] = fst;
                        stack[sp++] = snd;
                        stack[sp++] = fst;
                        break;
                    case DUP_X2               : // 91    0x5B
                        //..., value3, value2, value1 →
                        //..., value1, value3, value2, value1
                        fst = stack[--sp];
                        snd = stack[--sp];
                        trd = stack[--sp];
                        stack[sp++] = fst;
                        stack[sp++] = trd;
                        stack[sp++] = snd;
                        stack[sp++] = fst;
                        break;
                    case DUP2                 : // 92    0x5C
                        //..., value2, value1 →
                        //..., value2, value1, value2, value1
                        fst = stack[sp - 1];
                        snd = stack[sp - 2];
                        stack[sp++] = snd;
                        stack[sp++] = fst;
                        break;
                    case DUP2_X1              : // 93    0x5D
                        //..., value3, value2, value1 →
                        //..., value2, value1, value3, value2, value1
                        fst = stack[--sp];
                        snd = stack[--sp];
                        trd = stack[--sp];
                        stack[sp++] = snd;
                        stack[sp++] = fst;
                        stack[sp++] = trd;
                        stack[sp++] = snd;
                        stack[sp++] = fst;
                        break;
                    case DUP2_X2              : // 94    0x5E
                        //..., value4, value3, value2, value1 →
                        //..., value2, value1, value4, value3, value2, value1
                        fst = stack[--sp];
                        snd = stack[--sp];
                        trd = stack[--sp];
                        fth = stack[--sp];
                        stack[sp++] = snd;
                        stack[sp++] = fst;
                        stack[sp++] = fth;
                        stack[sp++] = trd;
                        stack[sp++] = snd;
                        stack[sp++] = fst;
                        break;
                    case SWAP                 : // 95    0x5F
                        fst = stack[sp - 1];
                        stack[sp - 1] = stack[sp - 2];
                        stack[sp - 2] = fst;
                        break;
                    case IADD                 : // 96    0x60
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 + i2;
                        break;
                    case LADD                 : // 97    0x61
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++]  = null;
                        stack[sp++] = l1 + l2;
                        break;
                    case FADD                 : // 98    0x62
                        f2 = ((float) stack[--sp]);
                        f1 = ((float) stack[--sp]);
                        stack[sp++] = f1 + f2;
                        break;
                    case DADD                 : // 99    0x63
                        d2 = (double) stack[--sp]; sp--;
                        d1 = (double) stack[--sp]; sp--;
                        stack[sp++] = null;
                        stack[sp++] = d1 + d2;
                        break;
                    case ISUB                 : // 100    0x64
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 - i2;
                        break;
                    case LSUB                 : // 101    0x65
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 - l2;
                        break;
                    case FSUB                 : // 102    0x66
                        f2 = ((float) stack[--sp]);
                        f1 = ((float) stack[--sp]);
                        stack[sp++] = f1 - f2;
                        break;
                    case DSUB                 : // 103    0x67
                        d2 = (double) stack[--sp]; sp--;
                        d1 = (double) stack[--sp]; sp--;
                        stack[sp++] = null;
                        stack[sp++] = d1 - d2;
                        break;
                    case IMUL                 : // 104    0x68
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 * i2;
                        break;
                    case LMUL                 : // 105    0x69
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 * l2;
                        break;
                    case FMUL                 : // 106    0x6A
                        f2 = ((float) stack[--sp]);
                        f1 = ((float) stack[--sp]);
                        stack[sp++] = f1 * f2;
                        break;
                    case DMUL                 : // 107    0x6B
                        d2 = (double) stack[--sp]; sp--;
                        d1 = (double) stack[--sp]; sp--;
                        stack[sp++] = null;
                        stack[sp++] = d1 * d2;
                        break;
                    case IDIV                 : // 108    0x6C
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        vm.check_div_zero(i2);
                        stack[sp++] = i1 / i2;
                        break;
                    case LDIV                 : // 109    0x6D
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        vm.check_div_zero(l2);
                        stack[sp++] = null;
                        stack[sp++] = l1 / l2;
                        break;
                    case FDIV                 : // 110    0x6E
                        f2 = ((float) stack[--sp]);
                        f1 = ((float) stack[--sp]);
                        stack[sp++] = f1 / f2;
                        break;
                    case DDIV                 : // 111    0x6F
                        d2 = (double) stack[--sp]; sp--;
                        d1 = (double) stack[--sp]; sp--;
                        stack[sp++] = null;
                        stack[sp++] = d1 / d2;
                        break;
                    case IREM                 : // 112    0x70
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        vm.check_div_zero(i2);
                        stack[sp++] = i1 % i2;
                        break;
                    case LREM                 : // 113    0x71
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        vm.check_div_zero(l2);
                        stack[sp++] = null;
                        stack[sp++] = l1 % l2;
                        break;
                    case FREM                 : // 114    0x72
                        f2 = ((float) stack[--sp]);
                        f1 = ((float) stack[--sp]);
                        stack[sp++] = f1 % f2;
                        break;
                    case DREM                 : // 115    0x73
                        d2 = (double) stack[--sp]; sp--;
                        d1 = (double) stack[--sp]; sp--;
                        stack[sp++] = null;
                        stack[sp++] = d1 % d2;
                        break;
                    case INEG                 : // 116    0x74
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = -i1;
                        break;
                    case LNEG                 : // 117    0x75
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = -l1;
                        break;
                    case FNEG                 : // 118    0x76
                        f1 = ((float) stack[--sp]);
                        stack[sp++] = -f1;
                        break;
                    case DNEG                 : // 119    0x77
                        d1 = ((double) stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = -d1;
                        break;
                    case ISHL                 : // 120    0x78
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 << i2;
                        break;
                    case LSHL                 : // 121    0x79
                        i2 = int_val(stack[--sp]);
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 << i2;
                        break;
                    case ISHR                 : // 122    0x7A
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 >> i2;
                        break;
                    case LSHR                 : // 123    0x7B
                        i2 = int_val(stack[--sp]);
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 >> i2;
                        break;
                    case IUSHR                : // 124    0x7C
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 >>> i2;
                        break;
                    case LUSHR                : // 125    0x7D
                        i2 = int_val(stack[--sp]);
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 >>> i2;
                        break;
                    case IAND                 : // 126    0x7E
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 & i2;
                        break;
                    case LAND                 : // 127    0x7F
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 & l2;
                        break;
                    case IOR                  : // 128    0x80
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 | i2;
                        break;
                    case LOR                  : // 129    0x81
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 | l2;
                        break;
                    case IXOR                 : // 130    0x82
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        stack[sp++] = i1 ^ i2;
                        break;
                    case LXOR                 : // 131    0x83
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = null;
                        stack[sp++] = l1 ^ l2;
                        break;
                    case IINC                 : // 132    0x84
                        if (next_wide) {
                            idx = bytes.u2();
                            i1 = bytes.s2();
                            next_wide = false;
                        } else {
                            idx = bytes.u1();
                            i1 = bytes.s1();
                        }
                        locals[idx] = ((int) locals[idx]) + i1;
                        break;
                    case I2L                  : // 133    0x85
                        i1 = (int) stack[sp - 1];
                        stack[sp - 1] = null;
                        stack[sp++] = (long) i1;
                        break;
                    case I2F                  : // 134    0x86
                        i1 = (int) stack[sp - 1];
                        stack[sp - 1] = (float) i1;
                        break;
                    case I2D                  : // 135    0x87
                        i1 = (int) stack[sp - 1];
                        stack[sp - 1] = null;
                        stack[sp++] = (double) i1;
                        break;
                    case L2I                  : // 136    0x88
                        l1 = ((long) stack[sp - 1]);
                        stack[--sp] = null;
                        stack[sp - 1] = (int) l1;
                        break;
                    case L2F                  : // 137    0x89
                        l1 = ((long) stack[sp - 1]);
                        stack[--sp] = null;
                        stack[sp - 1] = (float) l1;
                        break;
                    case L2D                  : // 138    0x8A
                        l1 = ((long) stack[sp - 1]);
                        stack[sp - 1] = (double) l1;
                        break;
                    case F2I                  : // 139    0x8B
                        f1 = (float) stack[sp - 1];
                        stack[sp - 1] = (int) f1;
                        break;
                    case F2L                  : // 140    0x8C
                        f1 = (float) stack[sp - 1];
                        stack[sp - 1] = null;
                        stack[sp++] = (long) f1;
                        break;
                    case F2D                  : // 141    0x8D
                        f1 = (float) stack[sp - 1];
                        stack[sp - 1] = null;
                        stack[sp++] = (double) f1;
                        break;
                    case D2I                  : // 142    0x8E
                        d1 = (double) stack[sp - 1];
                        stack[--sp] = null;
                        stack[sp - 1] = (int) d1;
                        break;
                    case D2L                  : // 143    0x8F
                        d1 = (double) stack[sp - 1];
                        stack[sp - 1] = (long) d1;
                        break;
                    case D2F                  : // 144    0x90
                        d1 = (double) stack[sp - 1];
                        stack[--sp] = null;
                        stack[sp - 1] = (float) d1;
                        break;
                    case I2B                  : // 145    0x91
                        i1 = (int) stack[sp - 1];
                        stack[sp - 1] = (int) ((byte) i1);
                        break;
                    case I2C                  : // 146    0x92
                        i1 = (int) stack[sp - 1];
                        stack[sp - 1] = (int) ((char) i1);
                        break;
                    case I2S                  : // 147    0x93
                        i1 = (int) stack[sp - 1];
                        stack[sp - 1] = (int) ((short) i1);
                        break;
                    case LCMP                 : // 148    0x94
                        l2 = long_val(stack[--sp]); sp--;
                        l1 = long_val(stack[--sp]); sp--;
                        stack[sp++] = Long.compare(l1, l2);
                        break;
                    case FCMPL                : // 149    0x95
                    case FCMPG                : // 150    0x96
                        f2 = ((float) stack[--sp]);
                        f1 = ((float) stack[--sp]);
                        if (Float.isNaN(f1) || Float.isNaN(f2)) {
                            stack[sp++] = instruction == FCMPG ? 1 : -1;
                        } else {
                            stack[sp++] = Float.compare(f1, f2);
                        }
                        break;
                    case DCMPL                : // 151    0x97
                    case DCMPG                : // 152    0x98
                        d2 = ((double) stack[--sp]); sp--;
                        d1 = ((double) stack[--sp]); sp--;
                        if (Double.isNaN(d1) || Double.isNaN(d2)) {
                            stack[sp++] = instruction == DCMPG ? 1 : -1;
                        } else {
                            stack[sp++] = Double.compare(d1, d2);
                        }
                        break;
                    case IFEQ                 : // 153    0x99
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 == 0) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IFNE                 : // 154    0x9A
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 != 0) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IFLT                 : // 155    0x9B
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 < 0) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IFGE                 : // 156    0x9C
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 >= 0) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IFGT                 : // 157    0x9D
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 > 0) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IFLE                 : // 158    0x9E
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 <= 0) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ICMPEQ            : // 159    0x9F
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 == i2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ICMPNE            : // 160    0xA0
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 != i2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ICMPLT            : // 161    0xA1
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 < i2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ICMPGE            : // 162    0xA2
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 >= i2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ICMPGT            : // 163    0xA3
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 > i2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ICMPLE            : // 164    0xA4
                        i2 = int_val(stack[--sp]);
                        i1 = int_val(stack[--sp]);
                        offset = bytes.s2();
                        if (i1 <= i2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ACMPEQ            : // 165    0xA5
                        a2 = stack[--sp];
                        a1 = stack[--sp];
                        offset = bytes.s2();
                        if (a1 == a2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IF_ACMPNE            : // 166    0xA6
                        a2 = stack[--sp];
                        a1 = stack[--sp];
                        offset = bytes.s2();
                        if (a1 != a2) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case GOTO                 : // 167    0xA7
                        offset = bytes.s2();
                        bytes.pc(pc + offset);
                        break;
                    case GOTO_W               : // 200    0xC8
                        offset = bytes.s4();
                        bytes.pc(pc + offset);
                        break;
                    // jsr & ret 用来配合实现 finally
                    case JSR                  : // 168    0xA8
                        offset = bytes.s2();
                        stack[sp++] = bytes.pc();
                        bytes.pc(pc + offset);
                        break;
                    case JSR_W                : // 201    0xC9
                        offset = bytes.s4();
                        stack[sp++] = bytes.pc();
                        bytes.pc(pc + offset);
                        break;
                    case RET                  : // 169    0xA9
                        if (next_wide) {
                            idx = bytes.u2();
                            next_wide = false;
                        } else {
                            idx = bytes.u1();
                        }
                        bytes.pc(((int) locals[idx]));
                        break;
                    case TABLESWITCH          : // 170    0xAA
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.tableswitch
                        idx = int_val(stack[--sp]);
                        // 从当前方法开始(第一条操作码指令)计算的地址的 4bytes 偏移
                        bytes.pc(pc + (4 - (pc % 4)));
                        offset = bytes.s4(); // default
                        int low = bytes.s4();
                        int high = bytes.s4();
                        assert low <= high;
                        /*
                        逻辑上有一张 jmp table, 实际直接跳到指定位置
                        int n = high - low + 1;
                        int[] jmp_tbl = new int[n];
                        for (int i = 0; i < n; i++) {
                            jmp_tbl[i] = bytes.s4();
                        }
                        if (idx >= low && idx <= high) {
                            offset = jmp_tbl[idx - low];
                        }*/
                        if (idx >= low && idx <= high) {
                            bytes.pc(bytes.pc() + (idx - low) * 4);
                            offset = bytes.s4();
                        }
                        bytes.pc(pc + offset);
                        break;
                    case LOOKUPSWITCH         : // 171    0xAB
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.lookupswitch
                        idx = int_val(stack[--sp]);
                        // 从当前方法开始(第一条操作码指令)计算的地址的 4bytes 偏移
                        bytes.pc(pc + (4 - (pc % 4)));
                        offset = bytes.s4(); // default_offset
                        int n_pairs = bytes.s4();
                        assert n_pairs >= 0;
                        if (n_pairs > 0) {
                            // 逻辑上是一个key 从小到大排序的二维表 list<pair<value, offset>>
                            // int[][] jmp_tbl = new int[n_pairs][2];
                            for (int i = 0; i < n_pairs; i++) {
                                int key = bytes.s4();
                                if (idx < key) {
                                    break;
                                } else if (key == idx) {
                                    offset = bytes.s4();
                                    break;
                                } else {
                                    bytes.skip(4);
                                }
                            }
                        }
                        bytes.pc(pc + offset);
                        break;
                    case IRETURN              : // 172    0xAC
                    case FRETURN              : // 174    0xAE
                    case ARETURN              : // 176    0xB0
                    case LRETURN              : // 173    0xAD
                    case DRETURN              : // 175    0xAF
                        return stack[--sp];
                    case RETURN               : // 177    0xB1
                        return null;
                    case GETSTATIC            : // 178    0xB2
                    {
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.getstatic
                        idx = bytes.u2();
                        field_ref = cp.field_ref_at(idx);
                        if (field_ref.z_field_cache_ == null) {
                            ZClass z_class = vm.load_class(field_ref.class_name, false);
                            field_ref.z_field_cache_ = z_class.field(field_ref.name_and_type.name);
                            // 🦋 只有声明该属性的类需要初始化
                            field_ref.z_field_cache_.declared_class().initialize(vm);
                        }
                        ZField z_field = field_ref.z_field_cache_;
                        Object static_value =  z_field.declared_class().get_static_field(z_field.field_slot());
                        String type = field_ref.name_and_type.descriptor;
                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
                            // 这里本来应该是 long double 被装箱了...
                            assert static_value instanceof Long || static_value instanceof Double;
                            stack[sp++] = null;
                        }
                        stack[sp++] = static_value;
                        break;


//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.getstatic
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//                        // 🦋 只有声明该属性的类需要初始化
//                        if (field_ref.z_class_cache_ == null) {
//                            field_ref.z_class_cache_ = vm.load_class(field_ref.class_name, false);
//                        }
//                        ZClass z_class = field_ref.z_class_cache_;
//
//                        Object static_value;
//                        if (field_ref.slot_cache_ == -1) {
//                            ZField z_field = z_class.field(field_ref.name_and_type.name);
//                            ZClass z_field_decl_class = z_field.declared_class();
//                            z_field_decl_class.initialize(vm);
//                            if (z_field_decl_class.is_interface()) {
//                                // 对于数组可以缓存 z_field !!!
//                                static_value = z_field_decl_class.get_static_field(z_field.field_slot());
//                            } else {
//                                field_ref.slot_cache_ = z_field.field_slot();
//                                static_value = z_class.get_static_field(field_ref.slot_cache_);
//                            }
//                        } else {
//                            static_value = z_class.get_static_field(field_ref.slot_cache_);
//                        }
//
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            // 这里本来应该是 long double 被装箱了...
//                            assert static_value instanceof Long || static_value instanceof Double;
//                            stack[sp++] = null;
//                        }
//                        stack[sp++] = static_value;
//                        break;


//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.getstatic
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//                        // 🦋 只有声明该属性的类需要初始化
//                        ZClass z_class = vm.load_class(field_ref.class_name, false);
//                        ZField z_field = z_class.field(field_ref.name_and_type.name);
//                        z_field.declared_class().initialize(vm);
//
//                        Object static_value = z_field.get_value(null);
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            // 这里本来应该是 long double 被装箱了...
//                            assert static_value instanceof Long || static_value instanceof Double;
//                            stack[sp++] = null;
//                        }
//                        stack[sp++] = static_value;
//                        break;
                    }
                    case PUTSTATIC            : // 179    0xB3
                    {
                        // putstatic 不需要处理 interface,
                        // ~因为interface的字段都是final的...~ 反射需要...
                        // 不对, interface I { int i = val(1); } 如果属性不是常量，接口的 clinit 仍旧需要 putstatic
                        // 但是, 接口静态属性的 putstatic 的 field_ref 一定不涉及多态, 所以不需要像 getstatic 那样处理
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putstatic
                        idx = bytes.u2();
                        field_ref = cp.field_ref_at(idx);
                        if (field_ref.z_field_cache_ == null) {
                            ZClass z_class = vm.load_class(field_ref.class_name, false);
                            field_ref.z_field_cache_ = z_class.field(field_ref.name_and_type.name);
                            // 🦋 只有声明该属性的类需要初始化
                            field_ref.z_field_cache_.declared_class().initialize(vm);
                        }
                        ZField z_field = field_ref.z_field_cache_;
                        Object static_value = stack[--sp];
                        String type = field_ref.name_and_type.descriptor;
                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
                            assert static_value instanceof Long || static_value instanceof Double;
                            sp--;
                        }
                        z_field.declared_class().put_static_field(z_field.field_slot(), static_value);
                        break;

//                        // putstatic 不需要处理 interface,
//                        // ~因为interface的字段都是final的...~ 反射需要...
//                        // 不对, interface I { int i = val(1); } 如果属性不是常量，接口的 clinit 仍旧需要 putstatic
//                        // 但是, 接口静态属性的 putstatic 的 field_ref 一定不涉及多态, 所以不需要像 getstatic 那样处理
//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putstatic
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//                        // 🦋 只有声明该属性的类需要初始化
//                        if (field_ref.z_class_cache_ == null) {
//                            field_ref.z_class_cache_ = vm.load_class(field_ref.class_name, false);
//                        }
//                        ZClass z_class = field_ref.z_class_cache_;
//
//                        if (field_ref.slot_cache_ == -1) {
//                            ZField z_field = z_class.field(field_ref.name_and_type.name);
//                            ZClass z_field_decl_class = z_field.declared_class();
//                            z_field_decl_class.initialize(vm);
//                            field_ref.slot_cache_ = z_field.field_slot();
//                        }
//
//                        Object static_value = stack[--sp];
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            assert static_value instanceof Long || static_value instanceof Double;
//                            sp--;
//                        }
//                        z_class.put_static_field(field_ref.slot_cache_, static_value);
//                        break;

//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putstatic
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//                        // 🦋 只有声明该属性的类需要初始化
//                        ZClass z_class = vm.load_class(field_ref.class_name, false);
//                        ZField z_field = z_class.field(field_ref.name_and_type.name);
//                        z_field.declared_class().initialize(vm);
//
//                        Object static_value = stack[--sp];
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            assert static_value instanceof Long || static_value instanceof Double;
//                            sp--;
//                        }
//                        z_field.put_value(null, static_value);
//                        break;
                    }
                    case GETFIELD             : // 180    0xB4
                    {
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.getfield
                        idx = bytes.u2();
                        field_ref = cp.field_ref_at(idx);

                        if (field_ref.slot_cache_ == -1) {
                            ZClass z_class = vm.load_class(field_ref.class_name, false); // 🦋 new 的时候类已经加载并初始化过了
                            ZField field = z_class.field(field_ref.name_and_type.name);
                            assert field.field_name().equals(field_ref.name_and_type.name);
                            field_ref.slot_cache_ = field.field_slot();
                        }

                        ZObject object_ref = ((ZObject) stack[--sp]);
                        vm.check_null(object_ref);
                        Object value = object_ref.get_field(field_ref.slot_cache_);
                        String type = field_ref.name_and_type.descriptor;
                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
                            assert value instanceof Long || value instanceof Double;
                            stack[sp++] = null;
                        }
                        stack[sp++] = value;
                        break;

//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.getfield
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//
//                        if (field_ref.z_field_cache_ == null) {
//                            ZClass z_class = vm.load_class(field_ref.class_name, false); // 🦋 new 的时候类已经加载并初始化过了
//                            ZField field = z_class.field(field_ref.name_and_type.name);
//                            assert field.field_name().equals(field_ref.name_and_type.name);
//                            field_ref.z_field_cache_ = field;
//                        }
//
//                        ZObject object_ref = ((ZObject) stack[--sp]);
//                        vm.check_null(object_ref);
//                        Object value = object_ref.get_field(field_ref.z_field_cache_.field_slot());
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            assert value instanceof Long || value instanceof Double;
//                            stack[sp++] = null;
//                        }
//                        stack[sp++] = value;
//                        break;

//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.getfield
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//                        ZClass z_class = vm.load_class(field_ref.class_name, false); // 🦋 new 的时候类已经加载并初始化过了
//                        ZField z_field = z_class.field(field_ref.name_and_type.name);
//
//                        ZObject object_ref = ((ZObject) stack[--sp]);
//                        Object value = z_field.get_value(object_ref);
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            assert value instanceof Long || value instanceof Double;
//                            stack[sp++] = null;
//                        }
//                        stack[sp++] = value;
//                        break;
                    }
                    case PUTFIELD             : // 181    0xB5
                    {
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putfield
                        idx = bytes.u2();
                        field_ref = cp.field_ref_at(idx);

                        if (field_ref.slot_cache_ == -1) {
                            ZClass z_class = vm.load_class(field_ref.class_name, false); // 🦋 new 的时候类已经加载并初始化过了
                            ZField field = z_class.field(field_ref.name_and_type.name);
                            assert field.field_name().equals(field_ref.name_and_type.name);
                            field_ref.slot_cache_ = field.field_slot();
                        }

                        Object value = stack[--sp];
                        String type = field_ref.name_and_type.descriptor;
                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
                            assert value instanceof Long || value instanceof Double;
                            sp--;
                        }
                        ZObject object_ref = ((ZObject) stack[--sp]);
                        vm.check_null(object_ref);
                        object_ref.put_field(field_ref.slot_cache_, value);
                        break;

//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putfield
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//
//                        if (field_ref.z_field_cache_ == null) {
//                            ZClass z_class = vm.load_class(field_ref.class_name, false); // 🦋 new 的时候类已经加载并初始化过了
//                            ZField field = z_class.field(field_ref.name_and_type.name);
//                            assert field.field_name().equals(field_ref.name_and_type.name);
//                            field_ref.z_field_cache_ = field;
//                        }
//
//                        Object value = stack[--sp];
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            assert value instanceof Long || value instanceof Double;
//                            sp--;
//                        }
//                        ZObject object_ref = ((ZObject) stack[--sp]);
//                        vm.check_null(object_ref);
//                        object_ref.put_field(field_ref.z_field_cache_.field_slot(), value);
//                        break;

//                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.putfield
//                        idx = bytes.u2();
//                        field_ref = cp.field_ref_at(idx);
//                        ZClass z_class = vm.load_class(field_ref.class_name, false); // 🦋 new 的时候类已经加载并初始化过了
//                        ZField z_field = z_class.field(field_ref.name_and_type.name);
//
//                        Object value = stack[--sp];
//                        String type = field_ref.name_and_type.descriptor;
//                        if (type.charAt(0) == 'J' || type.charAt(0) == 'D') {
//                            assert value instanceof Long || value instanceof Double;
//                            sp--;
//                        }
//                        ZObject object_ref = ((ZObject) stack[--sp]);
//                        z_field.put_value(object_ref, value);
//                        break;
                    }
                    // 用于调用非私有实例方法
                    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.invokevirtual
                    case INVOKEVIRTUAL        : // 182    0xB6
                    // 用于调用私有实例方法、构造器，以及使用 super 关键字调用父类的实例方法或构造器，和所实现接口的默认方法
                    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.invokespecial
                    case INVOKESPECIAL        : // 183    0xB7
                    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.invokestatic
                    case INVOKESTATIC         : // 184    0xB8
                    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.invokeinterface
                    case INVOKEINTERFACE      : // 185    0xB9
                    {
                        idx = bytes.u2();
                        // INVOKESPECIAL + INVOKESTATIC: 实例方法或接口方法
                        // INVOKEVIRTUAL: 非私有实例方法
                        // INVOKEINTERFACE: 接口方法
                        method_ref = cp.method_ref_at(idx, instruction);
                        if (instruction == INVOKEINTERFACE) {
                            // 这俩玩意有毛用....
                            // int cnt = bytes.u1(); assert cnt > 0;
                            // int zero = bytes.u1(); assert zero == 0;
                            bytes.skip(2);
                        }


                        if (method_ref.name_and_type.types_size_cache_ == null) {
                            String[] types = Descriptor.parameter_types(method_ref.name_and_type.descriptor);
                            int[] type_size_cache = new int[types.length];
                            for (int i = types.length - 1; i >= 0; i--) {
                                type_size_cache[i] = (types[i].equals("long") || types[i].equals("double")) ? 1 : 0;
                            }
                            method_ref.name_and_type.types_size_cache_ = type_size_cache;
                        }

                        int[] type_size_cache = method_ref.name_and_type.types_size_cache_;
                        Object[] args = new Object[type_size_cache.length];
                        for (int i = type_size_cache.length - 1; i >= 0; i--) {
                            args[i] = stack[--sp];
                            sp = sp - type_size_cache[i];
                        }

//                        if (method_ref.name_and_type.types_cache_ == null) {
//                            method_ref.name_and_type.types_cache_ = Descriptor.parameter_types(method_ref.name_and_type.descriptor);
//                        }
//                        String[] types = method_ref.name_and_type.types_cache_;
//                        int n_args = types.length;
//                        Object[] args = new Object[n_args];
//                        while (--n_args >= 0) {
//                            Object arg = stack[--sp];
//                            if (types[n_args].equals("long") || types[n_args].equals("double")) {
//                                assert arg instanceof Long || arg instanceof Double;
//                                sp--;
//                            }
//                            // 这里应该做参数类型检查...
//                            args[n_args] = arg;
//                        }


                        // 🦋 对于非 invokestatic, 其实 new 的时候类已经加载并初始化过了
                        // 🦋 如果 invokestatic, 只有声明该属方法的类或接口需要初始化
                        if (method_ref.z_class_cache_ == null) {
                            method_ref.z_class_cache_ = vm.load_class(method_ref.class_name, false);
                        }
                        ZClass method_class = method_ref.z_class_cache_;

                        ZObject object_ref;
                        ZClass object_class;
                        if (instruction == INVOKESTATIC) {
                            object_ref = null;
                            object_class = null;
                        } else {
                            object_ref = (ZObject) stack[--sp];
                            vm.check_null(object_ref);
                            object_class = object_ref.z_class();
                            assert method_class.is_assignable_from(object_class);
                        }

                        // 其实这里可以把方法 resolve 过程全部统一掉, 不区分指令, 统一缓存
                        // 目前只有 INVOKESPECIAL 处理方式比较简单直接
//                        static ZMethod resolve_method(VM vm, InlineCache.CallSite call_site, int invoke_inst,
//                        ConstantPool.MethodRef method_ref, ZClass method_class, ZObject object_ref) {}

                        // todo invoke special 不需要内联缓存
                        ZMethod z_method = resolve_method(vm,
                                InlineCache.call_site(method, pc), instruction, method_ref, method_class, object_ref);
                        if (instruction == INVOKEVIRTUAL) {
                            // z_method = object_class.virtual_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);

                            // todo 提取一个方法, 看不下去了...
                            // !!! 可以提前缓存 invoke 、 invokeExact 等
                            // !!! 这里不能初始化... bug...
//                            if (z_method.is_signature_polymorphic()) {
//                                ZClass method_handle_class = vm.load_class("java/lang/invoke/MethodHandle", false);
//                                if (z_method.declared_class() == method_handle_class) {
//                                    // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.invokevirtual
//                                    ZObject method_type = Natives.method_type(vm, method_ref.name_and_type.descriptor);
//
//                                    // object_ref instanceof java/lang/invoke/MethodHandle
//                                    ZClass z_class = object_ref.z_class();
//                                    ZMethod get_type = z_class.virtual_method("type", "()Ljava/lang/invoke/MethodType;");
//                                    ZObject method_type_in_handle = (ZObject) get_type.invoke(object_ref, new Object[0]);
//                                    ZMethod as_type = z_class.virtual_method("asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodType;");
//
//                                    String method_name = z_method.name();
//                                    if (method_name.equals("invokeExact")) {
//                                        assert method_type == method_type_in_handle;
//                                    } else if (method_name.equals("invoke")) {
//                                        if (method_type != method_type_in_handle) {
//                                            object_ref = (ZObject) as_type.invoke(object_ref, new Object[] { method_type });
//                                        }
//                                    }
//                                    // 实际参数类型放后面了
//                                    // todo  this frame is not visible 隐藏栈帧
//                                }
//                                if (z_method.declared_class().major_version() >= JAVA_9_VERSION) {
//                                    ZClass var_handle_class = vm.load_class("java/lang/invoke/VarHandle", false);
//                                    if (z_method.declared_class() == var_handle_class) {
//                                        throw new UnsupportedOperationException(); // todo
//                                    }
//                                }
//                            }

                            if (instruction == INVOKEVIRTUAL) {
                                assert !z_method.is_instance_init() && !z_method.is_class_init();
                            }
                        } else if (instruction == INVOKESPECIAL) {
                            // z_method = method_class.special_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);
                        } else if (instruction == INVOKEINTERFACE) {
                            // assert method_class.is_interface();
                            // z_method = object_class.interface_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);
                        } else if (instruction == INVOKESTATIC) {
                            // z_method = method_class.static_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);
                            // !!! 注意这里, 只有声明该属方法的类或接口需要初始化
                            z_method.declared_class().initialize(vm);
                        } else {
                            throw new AssertionError();
                        }

                        vm_stack_frame.program_counter = bytes.pc();
//                        vm_stack_frame.operand_stack_pointer = sp;

                        // 参数类型检查
                        z_method.check_args(args);
                        Object return_value = z_method.invoke(object_ref, args);
                        if (z_method.has_return()) {
                            // 返回类型检查
                            z_method.check_return(return_value);
                            if (return_value instanceof Long || return_value instanceof Double) {
                                String return_type = z_method.return_type();
                                assert return_type.equals("long") || return_type.equals("double");
                                stack[sp++] = null;
                            }
                            stack[sp++] = return_value;
                        }
                        break;
                    }
                    case INVOKEDYNAMIC        : // 186    0xBA
                        idx = bytes.u2();
                        InvokeDynamic invoke_dynamic = cp.invoke_dynamic_at(idx);
                        int a = bytes.u1(); assert a == 0;
                        int b = bytes.u1(); assert b == 0;
                        // 注意 CallSite 是个抽象类....
                        // "java/lang/invoke/CallSite"
                        // todo 缓存起来...
                        ConstantPool.BootstrapMethod bootstrap_method = invoke_dynamic.bootstrap_method;
                        ConstantPool.MethodHandle bsm_mt = bootstrap_method.method_handle;
                        ConstantPool.Reference ref = bsm_mt.reference;

                        ConstantPool.MethodRef bsm_mt_ref = (ConstantPool.MethodRef) ref;
                        ZClass method_class = vm.load_class(bsm_mt_ref.class_name, false);
                        ZMethod bsm = method_class.static_method(bsm_mt_ref.name_and_type.name, bsm_mt_ref.name_and_type.descriptor);
                        // String[] types = Descriptor.parameter_types(bsm_mt_ref.name_and_type.descriptor);
                        // int n_args = types.length;
                        // Object[] args = new Object[n_args];


                        ZClass method_handles = vm.load_class("java/lang/invoke/MethodHandles", false);
                        ZMethod lookup = method_handles
                                .static_method("lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");



                        ZObject caller = (ZObject) lookup.invoke(null, new Object[0]);
                        ZObject invoked_name = Natives.new_string(vm, invoke_dynamic.name_and_type.name);
                        ZObject invoked_type = Natives.method_type(vm, invoke_dynamic.name_and_type.descriptor);
                        Object[] bsm_args = invoke_dynamic.bootstrap_method.arguments;
                        ZObject sam_method_type = Natives.method_type(vm, ((String) bsm_args[0]));
                        ZObject instantiated_method_type = Natives.method_type(vm, ((String) bsm_args[2]));


                        ConstantPool.MethodHandle impl_mh = ((ConstantPool.MethodHandle) bsm_args[1]);
                        // todo 这里不一定是 static，不一定是 methodRef
                        assert impl_mh.reference_kind == REF_invokeStatic;
                        ConstantPool.MethodRef mh_method_ref = (ConstantPool.MethodRef) impl_mh.reference;
                        ZMethod find_static = caller.z_class().static_method("findStatic",
                                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)");
                        ZObject impl_method = (ZObject) find_static.invoke(caller, new Object[]{
                                vm.load_class(mh_method_ref.class_name, false),
                                Natives.new_string(vm, mh_method_ref.name_and_type.name),
                                Natives.method_type(vm, mh_method_ref.name_and_type.descriptor)
                        });

                        Object[] args = new Object[] {
                                caller,
                                invoked_name,
                                invoked_type,
                                sam_method_type,
                                impl_method,
                                instantiated_method_type
                        };

                        ZObject call_site = (ZObject) bsm.invoke(null, args);


                        // System.out.println();


                        switch (bsm_mt.reference_kind) {
                            case REF_getField:
                            case REF_getStatic:
                            case REF_putField:
                            case REF_putStatic:
//                                ((ConstantPool.FieldRef) ref);
                                break;
                            case REF_invokeStatic:
                            case REF_invokeSpecial:
                                if (ref instanceof ConstantPool.MethodRef) {
                                } else if (ref instanceof ConstantPool.InterfaceMethodRef) {
                                } else {
                                    throw new AssertionError();
                                }
                                break;
                            case REF_invokeVirtual:
                            case REF_newInvokeSpecial:
//                                ConstantPool.MethodRef;
                                break;
                            case REF_invokeInterface:
//                                ConstantPool.InterfaceMethodRef;
                                break;
                            default:
                                throw new AssertionError();
                        }
                        // bootstrap_method.arguments;
                        // vm.load_class("java/lang/invoke/CallSite", true)
                        throw new UnsupportedOperationException(); // todo
                    case NEW                  : // 187    0xBB
                        String z_class = cp.class_at(bytes.u2()); // class
                        // 🦋 如果类木有初始化需要初始化 💥💥💥
                        // todo 缓存加载的 class
                        stack[sp++] = vm.load_class(z_class, true).allocate();
                        break;
                    case NEWARRAY             : // 188    0xBC
                        // https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-6.html#jvms-6.5.newarray
                        i1 = int_val(stack[--sp]); // array_length
                        int a_type = bytes.u1(); // a_type
                        if (i1 < 0) {
                            throw new ZThrowable(vm.load_class("java/lang/NegativeArraySizeException", true).new_instance());
                        }
                        stack[sp++] = vm.new_primitive_array(a_type, i1);
                        break;
                    case ANEWARRAY            : // 189    0xBD
                        i1 = int_val(stack[--sp]); // array_length
                        if (i1 < 0) {
                            throw new ZThrowable(vm.load_class("java/lang/NegativeArraySizeException", true).new_instance());
                        }
                        String component_type = cp.class_at(bytes.u2()); // class | array | interface
                        // todo 缓存加载的 class
                        stack[sp++] = vm.load_class(component_type, false).new_array(i1); // 🦋 不需要初始化
                        break;
                    case ARRAYLENGTH          : // 190    0xBE
                        a1 = stack[--sp];
                        vm.check_null(a1);
                        stack[sp++] = ((ZArray) a1).length();
                        break;
                    case ATHROW               : // 191    0xBF
                        ZObject throwable = (ZObject) stack[--sp];
                        throw new ZThrowable(throwable);
                    case CHECKCAST            : // 192    0xC0
                        String cast_type = cp.class_at(bytes.u2()); // class | array | interface
                        a1 = stack[sp - 1];
                        if (a1 == null) {
                            // null 不处理, jls 允许将 null cast 成其他类型
                        } else {
                            // todo 缓存加载的 class
                            if (!vm.load_class(cast_type, false).is_instance(vm, false, a1)) { // 🦋 不需要初始化
                                throw new ZThrowable(vm.load_class("java/lang/ClassCastException", true).new_instance());
                            }
                        }
                        break;
                    case INSTANCEOF           : // 193    0xC1
                        a1 = stack[--sp];
                        String ins_type = cp.class_at(bytes.u2()); // // class | array | interface
                        //if (a1 == null) {
                        //    stack[sp++] = 0;
                        //} else {
                            // todo 缓存加载的 class
                            stack[sp++] = vm.load_class(ins_type, false).is_instance(vm, false, a1) ? 1 : 0; // 🦋 不需要初始化
                        //}
                        break;
                    case MONITORENTER         : // 194    0xC2
                        a1 = vm.check_null(stack[--sp]);
                        ((ZObject) a1).monitor_enter();
                        break;
                    case MONITOREXIT          : // 195    0xC3
                        a1 = vm.check_null(stack[--sp]);
                        ((ZObject) a1).monitor_exit();
                        break;
                    case WIDE                 : // 196    0xC4
                        next_wide = true;
                        break;
                    case MULTIANEWARRAY       : // 197    0xC5
                        // int array_length = int_val(stack[--sp]);
                        String ma_array_type = cp.class_at(bytes.u2()); // class | array | interface
                        int dims = bytes.u1();
                        assert dims >= 1;
                        int[] dimensions = new int[dims];
                        while (--dims >= 0) {
                            dimensions[dims] = int_val(stack[--sp]);
                        }
                        // todo 缓存加载的 class
                        stack[sp++] = vm.load_class(ma_array_type, false).new_multi_array(dimensions); // 🦋 不需要初始化
                        break;
                    case IFNULL               : // 198    0xC6
                        a1 = stack[--sp];
                        offset = bytes.s2();
                        if (a1 == null) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case IFNONNULL            : // 199    0xC7
                        a1 = stack[--sp];
                        offset = bytes.s2();
                        if (a1 != null) {
                            bytes.pc(pc + offset);
                        }
                        break;
                    case BREAKPOINT           : // 202    0xCA
                    case IMDEP_1              : // 254    0xFE
                    case IMDEP_2              : // 255    0xFF
                        throw new UnsupportedOperationException(); // todo
                    default: throw new AssertionError();
                }
            } catch (ZThrowable zt) {
                {
                    vm_stack_frame.program_counter = bytes.pc();
//                    vm_stack_frame.operand_stack_pointer = sp;
                }

//                // debug
//                for (ClassFile.Exception exception : code.exception_table) {
//                    System.out.println(exception.start_pc + "-" + exception.end_pc + " > " + exception.catch_type());
//                }

                ZObject z_throwable = zt.z_throwable;
                vm.check_null(z_throwable);

                // todo 优化下这里的查找过程
                ClassFile.Exception caught = null;
                for (ClassFile.Exception exception : code.exception_table) {
                    // 包括 from 不包括 to
                    if (pc >= exception.start_pc && pc < exception.end_pc) {
                        if (exception.catch_any() ||
                                vm.load_class(exception.catch_type(), false) // 🦋 不需要初始化
                                        .is_assignable_from(z_throwable.z_class())
                        ) {
                            bytes.pc(exception.handler_pc);
                            caught = exception;
                            break;
                        }
                    }
                }
                if (caught == null) {
                    Natives.sneakyThrows(zt);
                    return null;
                } else {
                    stack[sp++] = z_throwable;
                    bytes.pc(caught.handler_pc);
                }
            } catch (Throwable t) {
                Natives.sneakyThrows(t);
            }
        }
    }

    static ZMethod resolve_method(VM vm, InlineCache.CallSite call_site, int invoke_inst,
                           ConstantPool.MethodRef method_ref, ZClass method_class, ZObject object_ref) {
        ZMethod method = vm.inline_cache_.get(call_site, object_ref);
        if (method == null) {
            method = resolve_method0(invoke_inst, method_ref, method_class, object_ref);
            vm.inline_cache_.put(call_site, object_ref, method);
        }
        return method;
    }

    private static ZMethod resolve_method0(int invoke_inst, ConstantPool.MethodRef method_ref, ZClass method_class, ZObject object_ref) {
        ZMethod z_method;
        if (invoke_inst == INVOKEVIRTUAL) {
            z_method = object_ref.z_class().virtual_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);
        } else if (invoke_inst == INVOKESPECIAL) {
            z_method = method_class.special_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);
        } else if (invoke_inst == INVOKEINTERFACE) {
            assert method_class.is_interface();
            z_method = object_ref.z_class().interface_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);
        } else if (invoke_inst == INVOKESTATIC) {
            z_method = method_class.static_method(method_ref.name_and_type.name, method_ref.name_and_type.descriptor);
        } else {
            throw new AssertionError();
        }
        return z_method;
    }


    static boolean bool_val(Object object) {
        return ((Integer) object) == 1;
    }

    static byte byte_val(Object object) {
        return ((Integer) object).byteValue();
    }

    static char char_val(Object object) {
        return ((char) ((Integer) object).intValue());
    }

    static short short_val(Object object) {
        return ((Integer) object).shortValue();
    }

    static int int_val(Object object) {
        if (object instanceof Number) {
            return ((Number) object).intValue();
        }
        if (object instanceof Character) {
            return (Character) object;
        }
        if (object instanceof Boolean) {
            return ((Boolean) object) ? 1 : 0;
        }
        return ((int) object);
    }

    static long long_val(Object object) {
        return ((Long) object);
    }
}
