package zvm.helper;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Objects;
import java.util.jar.JarFile;

import static zvm.helper.Misc.sneakyThrows;

/**
 * @author chuxiaofeng
 */
@SuppressWarnings("WeakerAccess")
public class ClassHelper {

    public static @Nullable byte[] getClassBytes(Class<?> cls) {
        Instrumentation instr = InstrumentationHelper.getInstrumentation();
        if (instr == null) {
            throw new AssertionError();
        }

        byte[][] ret = new byte[1][];
        ClassFileTransformer trans = (a, name, b, c, bytes) -> {
            if (cls.getName().replace('.', '/').equals(name)) {
                ret[0] = bytes;
            }
            return null;
        };
        instr.addTransformer(trans, true);
        try {
            // 这两行之间如果有其他线程的其他逻辑 调用 retransformClasses,
            // 会导致 ClassFileTransformer 收到并不是我们想要的 class 的 bytes，所以要重试，没想到什么好办法
            int cn = 3;
            while (--cn >= 0) {
                instr.retransformClasses(cls);
                if (ret[0] != null) {
                    return ret[0];
                }
            }
        } catch (UnmodifiableClassException ignored) {
        } finally {
            instr.removeTransformer(trans);
        }
        throw new AssertionError();
    }

    public static InputStream readClass(Class<?> c) {
        String name = c.getName().replace('.', '/') + ".class";
        InputStream is = c.getResourceAsStream(name);
        return is != null ? is : c.getResourceAsStream('/' + name);
    }

    public static InputStream readClass(ClassLoader cl, String className) {
        String name = className.replace('.', '/') + ".class";
        InputStream is = cl.getResourceAsStream(name);
        return is != null ? is : cl.getResourceAsStream('/' + name);
    }

    public static void appendToBootstrapPath(JarFile jarfile) {
        InstrumentationHelper.getInstrumentation().appendToBootstrapClassLoaderSearch(jarfile);
    }

    public static void appendToSystemPath(URL path)  {
        try {
            Objects.requireNonNull(path);
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(ClassLoader.getSystemClassLoader(), path);
        } catch (Exception e) {
            sneakyThrows(e);
        }
    }

    public static Class<?> defineClass(ClassLoader loader, InputStream is)  {
        try {
            byte[] bytes = Misc.toBytes(is);
            Method defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass",
                    String.class, byte[].class, int.class, int.class);
            defineClassMethod.setAccessible(true);
            return (Class<?>) defineClassMethod.invoke(loader, null, bytes, 0, bytes.length);
        } catch (Exception e) {
            sneakyThrows(e);
            return null;
        }
    }

    public static @Nullable URL locateClass(Class<?> c, boolean createTempFile) throws IOException {
        URL url = locateClass1(c);
        if (url != null) {
            return url;
        }

        url = locateClass2(c);
        if (url != null) {
            return url;
        }

        if (createTempFile) {
            url = locateClass3(c);
            if (url != null) {
                return url;
            }
            return locateClass4(c);
        }
        return null;
    }

    static @Nullable URL locateClass1(Class<?> c) {
        ProtectionDomain pd = c.getProtectionDomain();
        if (pd == null) {
            return null;
        }
        CodeSource cs = pd.getCodeSource();
        if (cs == null) {
            return null;
        }

        URL url = cs.getLocation();
        // spring-boot 那种 fat-jar 这里返回 null
        if (!"file".equals(url.getProtocol())) {
            return null;
        }
        if (url.getPath().indexOf('!') != -1) {
            return null;
        }
        return url;
    }

    static @Nullable URL locateClass2(Class<?> c) {
        int idx = c.getName().lastIndexOf('.');
        String fileName = (idx >= 0 ? c.getName().substring(idx + 1) : c.getName()) + ".class";

        URL url = c.getResource(fileName);
        if (url == null) {
            return null;
        }
        // spring-boot 那种 fat-jar 这里返回 null
        if (!"file".equals(url.getProtocol())) {
            return null;
        }
        if (url.getPath().indexOf('!') != -1) {
            return null;
        }
        return url;
    }

    static @Nullable URL locateClass3(Class<?> c) throws IOException {
        File file = File.createTempFile(c.getName(), ".class");
        file.deleteOnExit();
        try (OutputStream out = new FileOutputStream(file)) {
            try(InputStream in = readClass(c)) {
                if (in == null) {
                    return null;
                }
                Misc.copy(in, out);
            }
        }
        return file.toURI().toURL();
    }

    static @Nullable URL locateClass4(Class<?> c) throws IOException {
        File file = File.createTempFile(c.getName(), ".class");
        file.deleteOnExit();
        try (OutputStream out = new FileOutputStream(file)) {
            byte[] bytes = getClassBytes(c);
            if (bytes == null) {
                return null;
            }
            out.write(bytes);
        }
        return file.toURI().toURL();
    }
}
