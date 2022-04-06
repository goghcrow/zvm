package zvm.helper;


import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static java.lang.Boolean.TRUE;

/**
 * 另种方式获取自身 jvm 进程 Instrumentation
 * @author chuxiaofeng
 */
@SuppressWarnings("WeakerAccess")
public class InstrumentationHelper {

    public static synchronized Instrumentation getInstrumentation() {
        Instrumentation instrumentation = doGetInstrumentation();
        // 第一次肯定没有，需要先 attach 一次
        if (instrumentation == null) {
            attach();
            return doGetInstrumentation();
        } else {
            return instrumentation;
        }
    }

    static Instrumentation doGetInstrumentation() {
        try {
//            // 这里要保证 Agent 一定在 SystemClassLoader 的路径里头
//            if (ClassLoader.getSystemClassLoader() != Agent.class.getClassLoader()) {
//                ClassHelper.appendToSystemPath(ClassHelper.locateClass(Agent.class));
//            }
            return (Instrumentation) ClassLoader.getSystemClassLoader()
                    .loadClass(Agent.class.getName())
                    .getMethod("instrumentation")
                    .invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    static void attach() {
        String pid = Misc.pid();
        String agent = null;
        try {
            agent = AgentProvider.agentJar().getAbsolutePath();
        } catch (IOException e) {
            Misc.sneakyThrows(e);
        }

        boolean attached = false;
        if (ToolsJarProvider.inClassPath()) {
            try {
                Attacher.attach(pid, agent);
                attached = true;
            } catch (Throwable ignored) { }
        }
        if (!attached) {
            ExternalAttacher.attach(pid, agent);
        }
    }

    private interface ExternalAttacher {
        static void attach(String processId, String agent) {
            try {
                File toolsJar = ToolsJarProvider.toolsJar();
                File attacherJar = attacherJarFile();
                char sep = File.pathSeparatorChar;
                String classPath = quote(attacherJar.getCanonicalPath()) + sep + quote(toolsJar.getCanonicalPath());
                execJava(classPath, quote(Attacher.class.getName()), processId, quote(agent));
            } catch (Exception e) {
                Misc.sneakyThrows(e);
            }
        }

        static void execJava(String cp, String main, String arg1, String arg2)
                throws IOException, InterruptedException
        {
            Process process = new ProcessBuilder(java(), "-cp", cp, main, arg1, arg2)
                    .redirectErrorStream(true)
                    .start();
            if (process.waitFor() != 0) {
                InputStream in = process.getInputStream();
                byte[] bytes = new byte[in.available()];
                //noinspection ResultOfMethodCallIgnored
                in.read(bytes);
                throw new IllegalStateException("新进程 Self-attach 失败: " + new String(bytes));
            }
        }

        static String quote(String value) {
            return (value.contains(" ") ? '"' + value + '"' : value);
        }

        static File attacherJarFile() throws IOException {
            return JarHelper.createJarFile(Attacher.class.getSimpleName(), null, Attacher.class);
        }

        static String java() {
            boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.US).contains("windows");
            char sep = File.separatorChar;
            return quote(System.getProperty("java.home") + sep + "bin" + sep + (isWin ? "java.exe" : "java"));
        }
    }


    private interface ToolsJarProvider {
        static File toolsJar() {
            // 可能位置不全~~
            final String[] toolsJars = {"../lib/tools.jar", "lib/tools.jar", "../Classes/classes.jar"};
            for (String jar : toolsJars) {
                File toolsJar = new File(System.getProperty("java.home"), jar);
                if (toolsJar.isFile() && toolsJar.canRead()) {
                    return toolsJar;
                }
            }
            throw new IllegalStateException("木有找到 tools.jar");
        }

        static boolean inClassPath() {
            try {
                Class.forName("com.sun.tools.attach.VirtualMachine");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    }

    private interface AgentProvider {
        static File agentJar() throws IOException {
            try {
                File agentFile = tryGetAgentJarFile();
                if (agentFile == null) {
                    return createJarFile();
                } else {
                    return agentFile;
                }
            } catch (Exception ignored) {
                return createJarFile();
            }
        }

        static @Nullable File tryGetAgentJarFile() throws IOException {
            URL url = ClassHelper.locateClass(Agent.class, false);
            if (url == null) {
                return null;
            }

            File agentJar;
            try {
                agentJar = new File(url.toURI());
            } catch (URISyntaxException ignored) {
                agentJar = new File(url.getPath());
            }
            if (!agentJar.isFile() || !agentJar.canRead()) {
                return null;
            }

            try (JarInputStream jarIs = new JarInputStream(new FileInputStream(agentJar))) {
                Manifest manifest = jarIs.getManifest();
                if (manifest == null) {
                    return null;
                }

                Attributes attributes = manifest.getMainAttributes();
                if (attributes == null) {
                    return null;
                }

                if (Agent.class.getName().equals(attributes.getValue("Agent-Class"))
                        && Boolean.parseBoolean(attributes.getValue("Can-Redefine-Classes"))
                        && Boolean.parseBoolean(attributes.getValue("Can-Retransform-Classes"))
                        && Boolean.parseBoolean(attributes.getValue("Can-Set-Native-Method-Prefix"))) {
                    return agentJar;
                } else {
                    return null;
                }
            }
        }

        static File createJarFile() throws IOException {
            Map<Attributes.Name, String> attrs = new HashMap<>();
            attrs.put(new Attributes.Name("Agent-Class"), Agent.class.getName());
            attrs.put(new Attributes.Name("Can-Redefine-Classes"), TRUE.toString());
            attrs.put(new Attributes.Name("Can-Retransform-Classes"), TRUE.toString());
            attrs.put(new Attributes.Name("Can-Set-Native-Method-Prefix"), TRUE.toString());
            return JarHelper.createJarFile(Agent.class.getSimpleName(), attrs, Agent.class);
        }
    }


    public static class Agent {
        static volatile Instrumentation instr;
        public static Instrumentation instrumentation() {
            if (instr == null) {
                throw new IllegalStateException("Agent 没加载或者没用 SystemClassLoader 加载 Agent 调用 getInstrumentation");
            } else {
                return instr;
            }
        }
        public static void premain(String args, Instrumentation instr) { Agent.instr = instr; }
        public static void agentmain(String args, Instrumentation instr) { Agent.instr = instr; }
    }

}
