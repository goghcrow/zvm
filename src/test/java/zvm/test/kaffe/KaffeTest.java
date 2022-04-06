package zvm.test.kaffe;

import org.junit.Test;
import zvm.VM;

/**
 * @author chuxiaofeng
 */
public class KaffeTest {
    static final String[] cp = {
            "/Users/chuxiaofeng/Library/Mobile Documents/com~apple~CloudDocs/project/enable-criteria/j/target/test-classes/",
    };
    static VM vm = new VM(cp);

    @Test
    public void test() {
        vm.run("zvm.test.kaffe.Alias");
    }
}
