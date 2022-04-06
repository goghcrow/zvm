package zvm;

import org.jetbrains.annotations.NotNull;
import zvm.ClassParser.ClassFile;
import zvm.helper.ClassHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static zvm.ClassParser.AccessFlags.ACC_FINAL;
import static zvm.ClassParser.AccessFlags.ACC_NATIVE;
import static zvm.Natives.class_for_name0;
import static zvm.Natives.sneakyThrows;

/**
 * @author chuxiaofeng
 */
public final class ClassReader {
    final String[] class_paths;
    final static boolean load_class_by_instrumentation = true;
    final static String rt_jar_file_ = new File(System.getProperty("java.home"), "lib/rt.jar").getPath();
    final static String jsse_jar_file_ = new File(System.getProperty("java.home"), "lib/jsse.jar").getPath();

    public ClassReader(String[] class_paths) {
        this.class_paths = class_paths;
    }

    public ClassFile read_class(String name) {
        name = name.replace('.', '/');
        ClassFile class_file = read_class0(name);
        hack_(class_file);
        return class_file;
    }

    // hack 一些 jdk8 方法...
    private void hack_(ClassFile class_file) {
        String this_class_name = class_file.this_name();
        if (this_class_name.equals("java/lang/System")) {
            for (ClassFile.Method method : class_file.methods) {
                if (method.name().equals("loadLibrary")) {
                    method.access_flags = method.access_flags | ACC_NATIVE;
                    break;
                }
            }
            for (ClassFile.Field field : class_file.fields) {
                // java_lang_system 中 这三个 final static 字段 不是在 <clinit> 中赋值的
                // 检查 final 字段是否初始化时候, 不想特殊处理
                String name = field.name();
                if (name.equals("in") || name.equals("out") || name.equals("err")) {
                    field.access_flags = field.access_flags & ~ACC_FINAL;
                }
            }
            return;
        }
        if (this_class_name.equals("sun/misc/VM")) {
            for (ClassFile.Method method : class_file.methods) {
                if (method.name().equals("booted")) {
                    method.access_flags = method.access_flags | ACC_NATIVE;
                    break;
                }
            }
            return;
        }
        if (this_class_name.equals("java/security/MessageDigest")) {
            for (ClassFile.Method method : class_file.methods) {
                if (method.name().equals("getInstance")) {
                    method.access_flags = method.access_flags | ACC_NATIVE;
                } else if (method.name().equals("digest")) {
                    method.access_flags = method.access_flags | ACC_NATIVE;
                }
            }
            return;
        }
        if (this_class_name.equals("java/io/ObjectInputStream")) {
            for (ClassFile.Method method : class_file.methods) {
                if (method.name().equals("<clinit>")) {
                    method.access_flags = method.access_flags | ACC_NATIVE;
                    break;
                }
            }
            return;
        }
    }

    ClassFile read_class0(String name) {
        if (name.startsWith("java/") ||
                name.startsWith("sun/") ||
                name.startsWith("com/intellij/rt") ||
                name.startsWith("org/jcp/") ||
                name.startsWith("apple/")
        ) {
            return load_jdk_class_file(name);
        } else if (name.startsWith("com/sun")) {
            return load_jsse_class_file(name);
        } else if (name.startsWith("zvm/Natives$")) {
            return load_zvm_class_file(name);
        } else {
            return parse_class(name);
        }
    }

    ClassFile parse_class(String name) {
        for (String class_path : class_paths) {
            if (class_path.endsWith(".jar")) {
                try {
                    return load_class_from_jar(class_path, name);
                } catch (IOException ignored) { }
            } else {
                try {
                    return ClassParser.parse(class_path + "/" + name + ".class");
                } catch (IOException ignored) { }
            }
        }
        Natives.sneakyThrows(new ClassNotFoundException(name));
        return null;
    }

    @NotNull ClassFile parse_class(byte[] bytes) {
        try {
            return ClassParser.parse(bytes);
        } catch (IOException e) {
            sneakyThrows(e);
            return null;
        }
    }

    @NotNull ClassFile parse_class(InputStream is) {
        try {
            return ClassParser.parse(read_all(is));
        } catch (IOException e) {
            sneakyThrows(e);
            return null;
        }
    }

    static byte[] read_all(InputStream input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (-1 != (n = input.read(buf))) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    @NotNull
    ClassFile load_jdk_class_file(String name) {
        if (load_class_by_instrumentation) {
            Class<?> c = class_for_name0(name.replace('/', '.'), false);
            byte[] bytes = ClassHelper.getClassBytes(c);
            return parse_class(bytes);
        } else {
            try {
                return load_class_from_jar(rt_jar_file_, name);
            } catch (IOException e) {
                sneakyThrows(e);
                return null;
            }
        }
    }

    @NotNull
    ClassFile load_jsse_class_file(String name) {
        if (load_class_by_instrumentation) {
            Class<?> c = class_for_name0(name.replace('/', '.'), false);
            byte[] bytes = ClassHelper.getClassBytes(c);
            return parse_class(bytes);
        } else {
            try {
                return load_class_from_jar(jsse_jar_file_, name);
            } catch (IOException e) {
                sneakyThrows(e);
                return null;
            }
        }
    }

    @NotNull
    ClassFile load_class_from_jar(String jar_file, String name) throws IOException {
        JarFile rt_jar = new JarFile(jar_file);
        JarEntry class_file = rt_jar.stream()
                .filter(it -> !it.isDirectory() && it.getName().equals(name + ".class"))
                .findFirst().orElseThrow(AssertionError::new);
        InputStream is = rt_jar.getInputStream(class_file);
        return parse_class(is);
    }

    ClassFile load_zvm_class_file(String name) {
        if (load_class_by_instrumentation) {
            Class<?> c = class_for_name0(name.replace('/', '.'), false);
            byte[] bytes = ClassHelper.getClassBytes(c);
            return parse_class(bytes);
        } else {
            try {
                String file_name = "/" + name + ".class";
                byte[] bytes = Files.readAllBytes(Paths.get(VM.class.getResource(file_name).toURI()));
                return parse_class(bytes);
            } catch (IOException | URISyntaxException e) {
                sneakyThrows(e);
                return null;
            }
        }
    }
}
