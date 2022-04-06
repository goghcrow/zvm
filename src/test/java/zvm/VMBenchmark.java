package zvm;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import zvm.test.Test_instanceof;
import zvm.test.Test_isAssignableFrom;
import zvm.test.Test_getName0;
import zvm.test.thirdparty.Test_Inheritance;
import zvm.test.thirdparty.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * @author chuxiaofeng
 */
@SuppressWarnings("WeakerAccess")
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class VMBenchmark {

    static final String[] cp = {
            "/Users/chuxiaofeng/Library/Mobile Documents/com~apple~CloudDocs/project/zvm/target/test-classes/",
            "/Users/chuxiaofeng/Documents/~tms/maven/junit/junit/4.12/junit-4.12.jar"
    };
    // 冷启动 加载类太慢了....
    static VM vm = new VM(cp);

    static List<Object> run(Class<?> cls) {
        ZClass z_class = vm.load_class(cls.getName(), true);
        List<Object> lst = new ArrayList<>();
        for (Method method : cls.getMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
                Object result = z_class.static_method(method.getName(),
                        Descriptor.method(method)).invoke(null, new Object[0]);
                lst.add(result);
            }
        }
        return lst;
    }
//
    @org.openjdk.jmh.annotations.Benchmark
    public void a() {
        run(Test_stdlib.class);
        run(Test_Lambda.class);
        run(Test_ObjectInit.class);
        run(Test_IfElse.class);
        run(Test_Loops.class);
        run(Test_Inheritance.class);
        run(Test_Exceptions.class);
        run(Test_CheckCast.class);
        run(Test_Fields.class);
        run(Test_Invoke.class);
        run(Test_Arrays.class);
        run(Test_Switches.class);
        run(Test_JLS8.class);

        run(Prime.class);
        run(Sudoku.class);

        run(Test_getName0.class);
        run(Test_isAssignableFrom.class);
        run(Test_instanceof.class);
    }

//    @org.openjdk.jmh.annotations.Benchmark
//    public void b() {
//        run(Test_isAssignableFrom.class);
//    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(VMBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(1)
                .measurementIterations(1)
                .build();
        new Runner(opt).run();
    }

}
