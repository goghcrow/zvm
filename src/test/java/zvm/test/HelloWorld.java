package zvm.test;

import java.util.Arrays;

public class HelloWorld {
    public static void main(String[] args) throws Throwable {
        String name = "xiaofeng";
        System.out.println(hello(name));
        int[][] a = new int[1][1];
        a[0][0] = 42;
        int[][] b = a.clone();

        // wtf ... 这样直接编译不过
//        assertFalse(new Object[0] instanceof C1);
//        assertFalse(new int[0] instanceof I1);


        // new int[0].finalize(); // BUG 这货idea应该直接报错...
        // new Object[0].finalize(); // 这样编译不过
        // 都提示 protected， but
        // new int[0].clone()
        // clone 也是从 Object 继承过来的, 只能说明 array override 了 object 的 clone 方法，修改了modifier

        System.out.println(Arrays.toString(b));
        System.out.println(b[0][0]);
    }

    static String hello(String name) {
        return "Hello " + name + "!";
    }
}
