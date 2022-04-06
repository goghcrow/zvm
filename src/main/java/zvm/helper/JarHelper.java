package zvm.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static zvm.helper.ClassHelper.getClassBytes;

/**
 * @author chuxiaofeng
 */
@SuppressWarnings("WeakerAccess")
public class JarHelper {

    public static File createJarFile(String jarName,
                                     Map<Attributes.Name, String> info,
                                     ClassLoader cl,
                                     String... classes) throws IOException {
        return createJarFileFromClassesFiles(jarName, info, cl, classes);
    }

    public static File createJarFile(String jarName,
                                     Map<Attributes.Name, String> info,
                                     Class<?>... classes) throws IOException {
        return createJarFileFromClassesFiles(jarName, info, classes);
    }

    static File createJarFileFromClassesFiles(String jarName,
                                              Map<Attributes.Name, String> info,
                                              Class<?>... classes) throws IOException {
        File jar = File.createTempFile(jarName, ".jar");
        jar.deleteOnExit();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest(info))) {
            for (Class<?> clz : classes) {
                try (InputStream in = ClassHelper.readClass(clz)) {
                    if (in == null) throw new AssertionError("read " + clz.getName() + " fail");
                    String entryName = clz.getName().replace('.', '/') + ".class";
                    out.putNextEntry(new JarEntry(entryName));
                    Misc.copy(in, out);
                    out.closeEntry();
                }
            }
        }
        return jar;
    }

    static File createJarFileFromClassesFiles(String jarName,
                                              Map<Attributes.Name, String> info,
                                              ClassLoader cl, String... classes) throws IOException {
        File jar = File.createTempFile(jarName, ".jar");
        jar.deleteOnExit();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest(info))) {
            for (String clz : classes) {
                try (InputStream in = ClassHelper.readClass(cl, clz)) {
                    if (in == null) throw new AssertionError("read " + clz + " fail");
                    String entryName = clz.replace('.', '/') + ".class";
                    out.putNextEntry(new JarEntry(entryName));
                    Misc.copy(in, out);
                    out.closeEntry();
                }
            }
        }
        return jar;
    }

    static File createJarFileFromInstrumentation(String jarName,
                                                    Map<Attributes.Name, String> info,
                                                    Class<?>... classes) throws IOException {
        File jar = File.createTempFile(jarName, ".jar");
        jar.deleteOnExit();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest(info))) {
            for (Class<?> clz : classes) {
                byte[] bytes = getClassBytes(clz);
                String entryName = clz.getName().replace('.', '/') + ".class";
                out.putNextEntry(new JarEntry(entryName));
                out.write(Objects.requireNonNull(bytes));
                out.closeEntry();
            }
        }
        return jar;
    }

    static Manifest manifest(Map<Attributes.Name, String> info) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (info != null) {
            info.forEach((k, v) -> manifest.getMainAttributes().put(k, v));
        }
        return manifest;
    }
}
