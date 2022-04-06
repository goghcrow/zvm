package zvm.test.thirdparty;

// 测试用例修改自 github.com/lihaoyi/Metascala
// 测试用例修改自 https://github.com/zxh0/jvm.go/tree/master/test/testclasses/src/main/java/jvm/instructions
public class Test_Arrays {

    public static int short_array_test() {
        short[] arr = new short[1];
        arr[0] = 42;
        return arr[0];
    }

    public static float float_array_test() {
        float[] arr = new float[1];
        arr[0] = 42.0f;
        return arr[0];
    }

    public static double double_array_test() {
        double[] arr = new double[1];
        arr[0] = 42.0;
        return arr[0];
    }

    static int[][] make2D(int a, int b){
        return new int[a][b];
    }
    static int[][][] make3D1(int a, int b, int c){
        return new int[a][b][c];
    }
    static int[][][] make3D2(int a, int b){ return new int[a][b][]; }
    public static Object[] test() {
        Object[] r = new Object[4];
        r[0] = make2D(2, 3);
        r[1] = make3D1(2, 3, 4);
        r[2] = make3D2(2, 3);
        r[3] = make3D2(2, 0);
        return r;
    }

    public static int getAndSet(){
        int[][] arr = new int[10][10];
        for(int i = 0; i < 10; i++){
            for(int j = 0; j < 10; j++){
                arr[i][j] = i + j;
            }
        }
        int total = 0;
        for(int i = 0; i < 10; i++){
            for(int j = 0; j < 10; j++){
                total += arr[i][j];
            }
        }
        return total;
    }


    public static Object ANewArray() {
        Object[] arr = new Object[8];
        int[][][] y = {
                {
                        {1},
                        {1, 2},
                        {1, 2, 3}
                }
        };
        return new Object[] {
                arr.length,
                y.length,y[0].length,y[0][0].length,y[0][1].length,y[0][2].length
        };
    }

    public static Object MultiANewArrayTest() {
        int[][][] x = new int[2][3][5];
        x[1][2][3] = 7;
        return new Object[] {
                x.length,
                x[0].length,
                x[1][2].length,
                x[1][2][3]
        };
    }
}
