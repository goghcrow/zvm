package zvm.test.thirdparty;

// 测试用例修改自 github.com/lihaoyi/Metascala
public class Test_Inheritance {
    /*
    static public class Field_Test {
        static class A {
            int a = 1;
            void foo() {
                System.out.println(a);
            }
        }
        static class B extends A {
            int a = 2;
            void bar() {
                System.out.println(((A) this).a); // 1
                System.out.println(a); // 2
                foo(); // 1
            }
        }
        static void run() {
            new B().bar();
        }
    }
    */


    static class Super {
        int x;
        long y;
    }
    static class Sub extends Super {
        float a;
        double b;
    }

    public static Object[] test() {
        Sub sub = new Sub();
        sub.x = 1;
        sub.y = 2L;
        sub.a = 3.14f;
        sub.b = 2.71828;

        int x = sub.x;
        long y = sub.y;
        float a = sub.a;
        double b = sub.b;
        long z = sub.x + sub.y;
        return new Object[] {
                x,y,a,b,z
        };
    }

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    public static String implement(){
        Baas b = new Sheep();
        return b.baa(10);
    }

    public static String abstractClass(){
        Car toyota = new Toyota();
        return toyota.vroom();
    }

    public static String shadowedInheritedGet(){
        Car honda = new Honda();
        return honda.vroom();
    }

    public static String shadowedInheritedSet(){
        Car honda = new Honda();
        honda.rev();
        honda.cc++;
        ((Honda)honda).cc++;
        return honda.vroom();
    }

    public static String superMethod(){
        return new Toyota().superVStart();
    }

    public static int staticInheritance(){
        int a = Parent.x;
        Child1.x = 100;
        return a + Child1.x + Child2.x;
    }


    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    interface ParentInterface{
        int x = 30;
    }

    static class Parent{
        public static int x = 10;
    }

    static class Child1 extends Parent{
        public static int get(){
            return x;
        }
    }

    static class Cowc{}

    static class Child2 extends Cowc implements ParentInterface{
        public static int get(){
            return x;
        }
    }


    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=


    interface Baas{
        String baa(int n);
    }
    static class Sheep implements Baas{
        public String baa(int n){
            String s = "b";
            for(int i = 0; i < n; i++) s = s + "a";
            return s;
        }
    }


    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    static class Car{
        public int cc;
        public String vStart(){
            return "";
        }
        public void rev(){
            this.cc = this.cc + 1;
        }
        public String vroom(){
            String s = vStart();
            for(int i = 0; i < cc; i++){
                s = s + "o";
            }
            return s + "m";
        }
    }


    static class Toyota extends Car{
        public Toyota(){
            this.cc = 10;
        }

        public String vStart(){
            return "vr";
        }
        public String superVStart(){
            return super.vStart();
        }
    }

    static class Honda extends Car{
        public int cc = 5;
        public String vStart(){
            return "v"  + cc + "r" + ((Car)this).cc + "r" + super.cc;
        }
    }


}