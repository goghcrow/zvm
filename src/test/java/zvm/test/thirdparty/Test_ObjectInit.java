package zvm.test.thirdparty;

// 测试用例修改自 https://github.com/zxh0/jvm.go/blob/master/test/testclasses/src/main/java/jvm/ObjectInit.java
public class Test_ObjectInit {
    char x;
    static long a;


    public static Object test() {
        return new Object[] { new Test_ObjectInit().x, a };
    }

    // todo 补充
}
