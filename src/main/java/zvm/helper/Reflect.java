package zvm.helper;

import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.*;

/**
 * @author chuxiaofeng
 */
@SuppressWarnings("unused")
public class Reflect {
    public static Reflect of(Class<?> clazz) {
        return new Reflect(clazz, null);
    }

    public static Reflect of(Object instance) {
        return new Reflect(instance == null ? Object.class : instance.getClass(), instance);
    }

    final @NotNull Class<?> clazz;
    final Object object;

    Reflect(@NotNull Class<?> clazz, Object instance) {
        this.clazz = clazz;
        this.object = instance;
    }

    public <T> T allocateInstance() {
        // 很少用, 不缓存 unsafe 了 ~
        Unsafe unsafe = of(Unsafe.class).field("theUnsafe").get();
        try {
            //noinspection unchecked
            return (T) unsafe.allocateInstance(clazz);
        } catch (InstantiationException e) {
            return sneakyThrows(e);
        }
    }

    public ReflectField field(String name) {
        try {
            return new ReflectField(field0(name), object);
        } catch (Exception e) {
            return sneakyThrows(e);
        }
    }

    public ReflectMethod constructor(Class<?> ...paramTypes) {
        try {
            return new ReflectMethod(constructor0(paramTypes), object);
        } catch (Exception e) {
            return sneakyThrows(e);
        }
    }

    public ReflectMethod method(String name, Class<?> ...paramTypes) {
        try {
            return new ReflectMethod(method0(name, paramTypes), object);
        } catch (Exception e) {
            return sneakyThrows(e);
        }
    }

    Field field0(String name) throws NoSuchFieldException, IllegalAccessException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return removeFinal(c.getDeclaredField(name));
            } catch (NoSuchFieldException ignored) { }
            c = c.getSuperclass();
        }
        return removeFinal(clazz.getField(name)); // do_throw...
    }

    Field removeFinal(Field field) throws NoSuchFieldException, IllegalAccessException {
        int acc = field.getModifiers();
        if ((acc & Modifier.FINAL) != 0) {
            Field f = Field.class.getDeclaredField("modifiers");
            f.setAccessible(true);
            f.setInt(field, acc & ~Modifier.FINAL);
        }
        return field;
    }

    Method method0(String name, Class<?>[] paramTypes) throws NoSuchMethodException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) { }
            c = c.getSuperclass();
        }
        return clazz.getDeclaredMethod(name, paramTypes); // do_throw...
    }

    Constructor<?> constructor0(Class<?>[] paramTypes) throws NoSuchMethodException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredConstructor(paramTypes);
            } catch (NoSuchMethodException ignored) { }
            c = c.getSuperclass();
        }
        return clazz.getDeclaredConstructor(paramTypes); // do_throw...
    }

    public static class ReflectField {
        Field field;
        Object instance;

        ReflectField(Field field, Object instance) {
            field.setAccessible(true);
            this.field = field;
            this.instance = instance;
        }

        public ReflectField instance(Object instance) {
            this.instance = instance;
            return this;
        }

        public void set(Object value) {
            try {
                field.set(instance, value);
            } catch (IllegalAccessException e) {
                Reflect.sneakyThrows(e);
            }
        }

        public <T> T get() {
            try {
                //noinspection unchecked
                return (T) field.get(instance);
            } catch (IllegalAccessException e) {
                return Reflect.sneakyThrows(e);
            }
        }

        public void set(Object instance, Object value) {
            try {
                field.set(instance, value);
            } catch (IllegalAccessException e) {
                Reflect.sneakyThrows(e);
            }
        }

        public <T> T get(Object instance) {
            try {
                //noinspection unchecked
                return (T) field.get(instance);
            } catch (IllegalAccessException e) {
                return Reflect.sneakyThrows(e);
            }
        }
    }

    public static class ReflectMethod {
        Executable method;
        Object instance;

        ReflectMethod(Executable method, Object instance) {
            method.setAccessible(true);
            this.method = method;
            this.instance = instance;
        }

        public ReflectMethod instance(Object instance) {
            this.instance = instance;
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> T invoke(Object ...args) {
            try {
                if (method instanceof Constructor) {
                    return (T) ((Constructor<?>) method).newInstance(args);
                } else {
                    return (T) ((Method) method).invoke(instance, args);
                }
            } catch (Exception e) {
                return Reflect.sneakyThrows(e);
            }
        }
    }

    static <T extends Throwable, R> R sneakyThrows(Throwable e) throws T {
        //noinspection unchecked
        throw ((T) e);
    }

}
