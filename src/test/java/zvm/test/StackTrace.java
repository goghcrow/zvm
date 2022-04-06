package zvm.test;

public class StackTrace {
    void a() {
        b();
    }
    void b() {
        c();
    }
    void c() {
        d();
    }
    void d() {
        throw new RuntimeException();
    }

    public static void main(String[] args) {
        try {
            new StackTrace().a();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
