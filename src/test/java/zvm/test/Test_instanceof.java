package zvm.test;

import java.io.Serializable;

/**
 * @author chuxiaofeng
 */
public class Test_instanceof {
    // https://www.zhihu.com/question/21574535
    // int[][][] <: Serializable[][] <: Serializable[] <: Serializable （上面几个例子的延伸⋯开始好玩了吧？）
    // int[][][] <: Object[][] <: Object[] <: Object
    @SuppressWarnings("ConstantConditions")
    public static Object test_instanceof() {
        Object[] r = new Object[6];
        r[0] = Serializable.class.isAssignableFrom(Serializable[].class);
        r[1] = Serializable[].class.isAssignableFrom(Serializable[][].class);
        r[2] = Serializable[][].class.isAssignableFrom(int[][][].class);

        r[3] = Object.class.isAssignableFrom(Object[].class);
        r[4] = Object[].class.isAssignableFrom(Object[][].class);
        r[5] = Object[][].class.isAssignableFrom(int[][][].class);
        return r;
    }
}
