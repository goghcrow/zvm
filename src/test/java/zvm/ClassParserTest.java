package zvm;

import org.junit.Test;
import zvm.ClassParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class ClassParserTest {
    @Test
    public void test() throws IOException {
        String path = "/Users/chuxiaofeng/Library/Mobile Documents/com~apple~CloudDocs/project/zvm/target/test-classes/zvm/";
        String[] classes = new String[] {
                "HelloWorld.class",
                "HelloWorld$Color.class",
                "HelloWorld$Interface.class",
                "HelloWorld$AbstractInnerClass.class",
                "HelloWorld$AbstractInnerStaticClass.class",

                "HelloWorld$Outer.class",
                "HelloWorld$Outer$MiddleStatic1.class",
                "HelloWorld$Outer$MiddleStatic1$Inner.class",
                "HelloWorld$Outer$MiddleStatic2.class",
                "HelloWorld$Outer$MiddleStatic2$InnerStatic.class",
                "HelloWorld$Outer$Middle1.class",
                "HelloWorld$Outer$Middle2.class",
                "HelloWorld$Outer$Middle1$Inner.class",
                "HelloWorld$Outer$Middle2$Inner.class",
        };
        for (String file : classes) {
            ClassParser.ClassFile classFile = ClassParser.parse(path + file);
            ClassParser.ConstantPool cp = classFile.constant_pool();
            for (Object o : cp) {
                System.out.println(o);
            }
        }

        String[] jars = new String[] {
                "/Library/Java/JavaVirtualMachines/jdk1.8.0_101.jdk/Contents/Home/jre/lib/rt.jar",
                "/Library/Java/JavaVirtualMachines/jdk1.8.0_101.jdk/Contents/Home/lib/tools.jar",
                "/Library/Java/JavaVirtualMachines/jdk1.8.0_101.jdk/Contents/Home/lib/sa-jdi.jar",
                "/Library/Java/JavaVirtualMachines/jdk1.8.0_101.jdk/Contents/Home/jre/lib/ext/nashorn.jar",
        };
        for (String jar : jars) {
            JarFile jarFile = new JarFile(jar);
            Enumeration<JarEntry> enumOfJar = jarFile.entries();
            while (enumOfJar.hasMoreElements()) {
                JarEntry jarEntry = enumOfJar.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    System.out.println(jarEntry.getName());
                    try (InputStream stream = jarFile.getInputStream(jarEntry)) {
                        byte[] bytes = copyStream(stream, jarEntry);
                        ClassParser.ClassFile classFile = ClassParser.parse(bytes);
                        ClassParser.ConstantPool cp = classFile.constant_pool();
                        for (Object o : cp) {
                            System.out.println(o);
                        }
                    }
                }
            }
        }
    }
    private static byte[] copyStream(InputStream in, ZipEntry entry) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long size = entry.getSize();
        if (size > -1) {
            byte[] buffer = new byte[1024 * 4];
            int n = 0;
            long count = 0;
            while (-1 != (n = in.read(buffer)) && count < size) {
                baos.write(buffer, 0, n);
                count += n;
            }
        } else {
            while (true) {
                int b = in.read();
                if (b == -1) {
                    break;
                }
                baos.write(b);
            }
        }
        baos.close();
        return baos.toByteArray();
    }


    static void test_shift() {
        int ch1 = 0xff;
        int ch2 = 0xff;
        int ch3 = 0xff;
        int ch4 = 0xff;
        // 0xffffffff;

        System.out.println(((((long) ch1) << 24)) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
        System.out.println((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));;

        int i = -1 & 0xff;
        long l = i;

        System.out.println(i);
        System.out.println(l);
        System.out.println(Long.toBinaryString(i));
        System.out.println(Long.toBinaryString(l));

        int x = i << 24;
        long x1 = l << 24;

        System.out.println(Integer.toBinaryString(x));
        System.out.println(Long.toBinaryString(x1));

        System.out.println(x);
        System.out.println(((long) x));
        System.out.println((long) (x & 0xffffffff));
        System.out.println(x & 0xffffffffL); // !!!
        System.out.println(x1);
    }
}
