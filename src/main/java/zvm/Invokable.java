package zvm;

import org.jetbrains.annotations.Nullable;

/**
 * JNI
 * @author chuxiaofeng
 */
public interface Invokable {
    default @Nullable String descriptor() { return ""; }
    Object invoke(VM vm, ZMethod method, @Nullable ZObject object, Object[] args);
}
