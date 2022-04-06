package zvm.helper;

import com.sun.tools.attach.VirtualMachine;

/**
 * @author chuxiaofeng
 * 这货作为内部 innerClass 在 mac 的 jvm 下有问题
 */
public class Attacher {
    public static void main(String[] args) {
        try {
            attach(args[0], args[1]);
        } catch (Exception ignored) {
            System.exit(1);
        }
    }

    static void attach(String processId, String agent) throws Exception {
        final VirtualMachine attach = VirtualMachine.attach(processId);
        if (attach == null) {
            throw new IllegalStateException("attach 失败");
        }
        try {
            attach.loadAgent(agent, null);
        } finally {
            attach.detach();
        }
    }
}
