package zvm;

/**
 * @author chuxiaofeng
 */
public final class ZThread {
    // todo classloader
    // todo native thread
    // todo frame: 把 Deque<ZThread.Frame> 迁移到 这里
    // todo pc: 从 Interpreter 迁移到这里
    // todo locals: 从 Interpreter 迁移到这里
    // todo stack: 从 Interpreter 迁移到这里

    public static class Frame {
        final ZMethod method;
        volatile int program_counter = -1;
//不引用这两个玩意了, 不知道 interpret1 这么长会不会做优化，如果做逃逸分析话，会导致这两个局部数组逃逸
//        Object[] local_variables;
//        Object[] operand_stack;
//        int operand_stack_pointer = -1;
//        int instruction = -1;

        Frame(ZMethod method) {
            this.method = method;
        }

        int line_number() {
            int line_number = -1;
            ClassParser.ClassFile.LineNumber[] lnt = method.line_number_table();
            if (lnt == null || lnt.length == 0) {
                return line_number;
            }
            for (ClassParser.ClassFile.LineNumber ln : lnt) {
                if (program_counter >= ln.start_pc) {
                    line_number = ln.line_number;
                    break;
                }
            }
            return line_number;
        }

        @Override
        public String toString() {
            return method + ":" + line_number();
        }
    }

}
