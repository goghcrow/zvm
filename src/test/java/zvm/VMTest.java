package zvm;

import org.junit.Test;
import zvm.test.Test_instanceof;
import zvm.test.Test_isAssignableFrom;
import zvm.test.Test_getName0;
import zvm.test.thirdparty.Test_Inheritance;
import zvm.test.thirdparty.*;

import static org.junit.Assert.*;

/**
 * @author chuxiaofeng
 * run with cover 有 bug..
 */
public class VMTest {

    static final String[] cp = {
            "/Users/chuxiaofeng/Library/Mobile Documents/com~apple~CloudDocs/project/zvm/target/test-classes/",
            "/Users/chuxiaofeng/Documents/~tms/maven/junit/junit/4.12/junit-4.12.jar"
    };
    // 冷启动 加载类太慢了....
    static VM vm = new VM(cp);

//    VM vm;
//    @Before
//    public void setup() {
//        vm = new VM(cp);
//    }

    @Test
    public void diff() {
        assertTrue(zvm.Test.diff(vm, Test_stdlib.class));
        assertTrue(zvm.Test.diff(vm, Test_Lambda.class));
        assertTrue(zvm.Test.diff(vm, Test_ObjectInit.class));
        assertTrue(zvm.Test.diff(vm, Test_IfElse.class));
        assertTrue(zvm.Test.diff(vm, Test_Loops.class));
        assertTrue(zvm.Test.diff(vm, Test_Inheritance.class));
        assertTrue(zvm.Test.diff(vm, Test_Exceptions.class));
        assertTrue(zvm.Test.diff(vm, Test_CheckCast.class));
        assertTrue(zvm.Test.diff(vm, Test_Fields.class));
        assertTrue(zvm.Test.diff(vm, Test_Invoke.class));
        assertTrue(zvm.Test.diff(vm, Test_Arrays.class));
        assertTrue(zvm.Test.diff(vm, Test_Switches.class));
        assertTrue(zvm.Test.diff(vm, Test_JLS8.class));

        assertTrue(zvm.Test.diff(vm, Prime.class));
        assertTrue(zvm.Test.diff(vm, Sudoku.class));

        assertTrue(zvm.Test.diff(vm, Test_getName0.class));
        assertTrue(zvm.Test.diff(vm, Test_isAssignableFrom.class));
        assertTrue(zvm.Test.diff(vm, Test_instanceof.class));
    }

    @Test
    public void test() {
        vm.load_class("java.lang.Integer", true);
        vm.run("zvm/test/HelloWorld");
        vm.run("zvm/test/StackTrace");

//        {
//            ZClass z_class = vm.load_class("[[[Ljava/lang/Integer;");
//            System.out.println(Arrays.toString(z_class.primary_super_types()));
//            System.out.println(Arrays.toString(z_class.secondary_supertypes()));
//            System.out.println(z_class.depth());
//        }
//
//        {
//            ZClass z_class = vm.load_class("[[[Ljava/util/List;");
//            System.out.println(Arrays.toString(z_class.primary_super_types()));
//            System.out.println(Arrays.toString(z_class.secondary_supertypes()));
//            System.out.println(z_class.depth());
//        }
//
//        System.out.println(Arrays.toString(vm.load_class("zvm.test.thirdparty.Test_Invoke$ThirdTest").primary_super_types()));;
//        System.out.println(Arrays.toString(vm.load_class("zvm.test.thirdparty.Test_Invoke$ThirdTest").secondary_supertypes()));
    }


    @Test
    public void test_load_class() {
        vm.load_class("java/lang/Object", true);
        vm.load_class("java/lang/Class", true);

        String[] wrapper_types = new String[] { "java/lang/Boolean", "java/lang/Byte", "java/lang/Character",
                "java/lang/Short", "java/lang/Integer", "java/lang/Long", "java/lang/Float", "java/lang/Double" };
        for (String wrapper_type : wrapper_types) {
            vm.load_class(wrapper_type);
        }

        String[] primitive_types = new String[] { "boolean", "byte", "char", "short", "int", "long", "float", "double" };
        for (String primitive_type : primitive_types) {
            assertEquals(primitive_type, vm.load_class(primitive_type).name());
        }
        String[] primitive_array_types = new String[] { "Z", "B", "C", "S", "I", "J", "F", "D" };
        int idx = 0;
        for (String primitive_array_type : primitive_array_types) {
            for (int i = 1; i < 5; i++) {
                String array_padding = new String(new char[i]).replace('\0', '[');
                String primitive_array_type1 = array_padding + primitive_array_type;
                ZClass array_type = vm.load_class(primitive_array_type1);
                assertEquals(primitive_array_type1, array_type.name());
                int j = i;
                ZClass component = array_type;
                while (j > 0) {
                    component = component.component_class();
                    j--;
                }
                assertEquals(vm.load_class(primitive_types[idx]), component);
            }
            idx++;
        }
    }

    @Test
    public void test_java_class_for_name_vs_load_class() throws ClassNotFoundException {

        // forName() 只能 处理 non-primitive-type
        assertEquals(int.class, Integer.TYPE);
        assertEquals(Class.forName("[I").getComponentType(), int.class);
        assertEquals(Class.forName("[I").getComponentType(), Integer.TYPE);
        assertEquals(Class.forName("[B").getComponentType(), byte.class); // byte[]
        assertEquals(Class.forName("[Ljava.lang.String;").getComponentType(), String.class); // String[]
        assertEquals(Class.forName("[[Ljava.lang.String;").getComponentType().getComponentType(), String.class); // String[][]
        assertEquals(Class.forName("java.lang.String"), String.class); // String

        assertEquals(
                vm.load_class("void"),
                vm.load_class("java/lang/Void", true).field("TYPE").get_value(null)
        );

        assertEquals(
                vm.load_class("int"),
                vm.load_class("java/lang/Integer", true).field("TYPE").get_value(null)
        );
        assertEquals(
                vm.load_class("int"),
                vm.load_class("java.lang.Integer").field("TYPE").get_value(null)
        );
        assertEquals(
                vm.load_class("int"),
                vm.load_class("[I").component_class()
        );
        assertEquals(
                vm.load_class("int"),
                vm.load_class("[[I").component_class().component_class()
        );
        assertEquals(
                vm.load_class("java/lang/String"),
                vm.load_class("[Ljava/lang/String;").component_class()
        );
        assertEquals(
                vm.load_class("java.lang.String"),
                vm.load_class("[Ljava.lang.String;").component_class()
        );
        assertEquals(
                vm.load_class("java/lang/String"),
                vm.load_class("[[Ljava/lang/String;").component_class().component_class()
        );
        assertEquals(
                vm.load_class("java.lang.String"),
                vm.load_class("[[Ljava.lang.String;").component_class().component_class()
        );
    }


    @Test
    public void test_arrays() {
        ZClass z_class = vm.load_class(Test_Arrays.class.getName());
        Object result1 = z_class.static_method("make2D", "(II)[[I").invoke(null, new Object[] {1, 2});
        Object result2 = z_class.static_method("make3D1", "(III)[[[I").invoke(null, new Object[] {1, 2, 3});
        Object result3 = z_class.static_method("make3D2", "(II)[[[I").invoke(null, new Object[] {1, 2});
        Object result4 = z_class.static_method("make3D2", "(II)[[[I").invoke(null, new Object[] {1, 0});

        ZArray a1 = ((ZArray) result1);
        assertSame(a1.z_class(), vm.load_class("[[I"));
        assertEquals(1, a1.length());
        assertTrue(a1.index(0) instanceof ZArray);
        assertSame(((ZArray) a1.index(0)).z_class(), vm.load_class("[I"));
        assertEquals(2, ((ZArray) a1.index(0)).length());
        assertEquals(new Integer(0), ((ZArray) a1.index(0)).index(0));
        assertEquals(new Integer(0), ((ZArray) a1.index(0)).index(1));


        ZArray a2 = ((ZArray) result2);
        assertSame(a2.z_class(), vm.load_class("[[[I"));
        assertEquals(1, a2.length());
        assertTrue(a2.index(0) instanceof ZArray);
        assertSame(((ZArray) a2.index(0)).z_class(), vm.load_class("[[I"));
        assertEquals(2, ((ZArray) a2.index(0)).length());
        assertTrue(((ZArray) a2.index(0)).index(0) instanceof ZArray);
        assertEquals(3, ((ZArray) ((ZArray) a2.index(0)).index(0)).length());
        assertTrue(((ZArray) a2.index(0)).index(1) instanceof ZArray);
        assertEquals(3, ((ZArray) ((ZArray) a2.index(0)).index(1)).length());
        assertEquals(new Integer(0), ((ZArray) ((ZArray) a2.index(0)).index(0)).index(0));
        assertEquals(new Integer(0), ((ZArray) ((ZArray) a2.index(0)).index(0)).index(1));
        assertEquals(new Integer(0), ((ZArray) ((ZArray) a2.index(0)).index(0)).index(2));
        assertEquals(new Integer(0), ((ZArray) ((ZArray) a2.index(0)).index(1)).index(0));
        assertEquals(new Integer(0), ((ZArray) ((ZArray) a2.index(0)).index(1)).index(1));
        assertEquals(new Integer(0), ((ZArray) ((ZArray) a2.index(0)).index(1)).index(2));


        ZArray a3 = ((ZArray) result3);
        assertSame(a3.z_class(), vm.load_class("[[[I"));
        assertEquals(1, a3.length());
        assertTrue(a3.index(0) instanceof ZArray);
        assertSame(((ZArray) a3.index(0)).z_class(), vm.load_class("[[I"));
        assertEquals(2, ((ZArray) a3.index(0)).length());
        assertNull(((ZArray) a3.index(0)).index(0));
        assertNull(((ZArray) a3.index(0)).index(1));


        ZArray a4 = ((ZArray) result4);
        assertSame(a4.z_class(), vm.load_class("[[[I"));
        assertEquals(1, a4.length());
        assertTrue(a4.index(0) instanceof ZArray);
        assertSame(((ZArray) a4.index(0)).z_class(), vm.load_class("[[I"));
        assertEquals(0, ((ZArray) a4.index(0)).length());


        Object sum = z_class.static_method("getAndSet", "()I").invoke(null, new ZObject[0]);
        assertEquals(((Integer) sum).intValue(), Test_Arrays.getAndSet());
    }
}
