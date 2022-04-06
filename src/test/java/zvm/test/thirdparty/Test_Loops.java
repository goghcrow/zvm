package zvm.test.thirdparty;

// 测试用例修改自 github.com/lihaoyi/Metascala
public class Test_Loops {

    public static Number[] test() {
        Number[] r = new Number[5];
        r[0] = nullFor(42);
        r[1] = basicFor(42);
        r[2] = nullWhile(42);
        r[3] = basicWhile(42);
        r[4] = sqrtFinder(0.3);
        return r;
    }

    static int nullFor(int a){
        int c = 0;
        for(int i = 0; i > a; i++) c++;
        return c;
    }

    static int basicFor(int a){
        int c = 1;
        for(int i = 0; i < a; i++) c = c * 2;
        return c;
    }

    static int nullWhile(int a){
        int c = 1;
        while(c > a) c++;
        return c;
    }

    static int basicWhile(int a){
        int c = 1;
        while(c < a) c = c * 2;
        return c;
    }

    static double sqrtFinder(double n){
        double guess = n / 2 + 5;

        while(true){
            double errorSquared = guess*guess - n;
            errorSquared = errorSquared * errorSquared;
            if (errorSquared / n < 0.1) return guess;
            else{
                guess = ((guess * guess) - n) / (2 * guess);
            }
        }
    }
}
