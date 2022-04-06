package zvm;

import org.junit.Test;

/**
 * @author chuxiaofeng
 */
public class Scimark2 {
    static final String[] cp = {
            "/Users/chuxiaofeng/Library/Mobile Documents/com~apple~CloudDocs/project/zvm/target/test-classes/",
    };
    // 冷启动 加载类太慢了....
    static VM vm = new VM(cp);

    @Test
    public void test() {
        vm.run("zvm.test.scimark2.commandline");
    }
}
