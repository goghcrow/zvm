package zvm;

/**
 * @author chuxiaofeng
 */
public final class ZThrowable extends RuntimeException {
    final ZObject z_throwable;

    ZThrowable(ZObject z_throwable) {
        this.z_throwable = z_throwable;
    }

    @Override
    public String toString() {
        return z_throwable.toString();
    }
}
