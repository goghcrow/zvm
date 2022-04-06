package zvm.test.thirdparty;

import java.util.ArrayList;

// 修改自 https://github.com/zxh0/jvm.go/blob/master/test/testclasses/src/main/java/PrimeTest.java
public class Prime {

    public static Object run() {
        // 性能特别渣渣 ...
        return prime_list(999);
    }

    static Object prime_list(int max) {
        ArrayList<Integer> res = new ArrayList<>();
        int last = 3;
        res.add(last);
        while (true) {
            last = last + 2;
            boolean prime = true;
            for (int v : res) {
                if (v * v > last) {
                    break;
                }
                if (last % v == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) {
                res.add(last);
                if (last > max) {
                    break;
                }
            }
        }
        return res;
    }
}