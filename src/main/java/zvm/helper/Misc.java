package zvm.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * @author chuxiaofeng
 */
public interface Misc {

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int index;
        while ((index = in.read(buffer)) != -1) {
            out.write(buffer, 0, index);
        }
    }

    static byte[] toBytes(InputStream is) throws IOException {
        byte[] buf = new byte[Math.max(1024, is.available())];
        int bytesRead, offset = 0;
        while ((bytesRead = is.read(buf, offset, buf.length - offset)) != -1) {
            offset += bytesRead;
            if (offset == buf.length) {
                buf = Arrays.copyOf(buf,
                        buf.length + Math.max(is.available(), buf.length >> 1));
            }
        }
        return (offset == buf.length) ? buf : Arrays.copyOf(buf, offset);
    }

    static String pid() {
        String mxBean = ManagementFactory.getRuntimeMXBean().getName();
        int idx = mxBean.indexOf('@');
        if (idx == -1) {
            throw new IllegalStateException("获取 pid 失败");
        }
        return mxBean.substring(0, idx);
    }

    static <T extends Throwable> void sneakyThrows(Throwable e) throws T { throw ((T) e); }
}
