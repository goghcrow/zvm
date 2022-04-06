package zvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 玩具内联缓存
 * @author chuxiaofeng
 */
public final class InlineCache {
    final static boolean polymorphic = true;

    final static int call_site_size = 1024;
    final static int polymorphic_size = 4;

    private final MegamorphicCache<CallSite, Cache<ZClass, ZMethod>> polymorphic_inline_cache_ = new MegamorphicCache<>(call_site_size);
    private final Map<CallSite, CacheItem> monomorphic_inline_cache_ = new HashMap<>();
    static CallSite call_site(@NotNull ZMethod method, int pc) {
        return new CallSite(method, pc);
    }

    synchronized
    ZMethod get(CallSite call_site, @Nullable ZObject object_ref) {
        if (polymorphic) {
            Cache<ZClass, ZMethod> cache = polymorphic_inline_cache_.get(call_site);
            if (cache == null) {
                return null;
            }
            if (object_ref == null) {
                return cache.get(null);
            } else {
                return cache.get(object_ref.z_class());
            }
        } else {
            CacheItem cache = monomorphic_inline_cache_.get(call_site);
            if (cache == null) {
                return null;
            }
            if (object_ref == null) {
                if (cache.z_class == null) {
                    return cache.z_method;
                }
            } else {
                if (cache.z_class == object_ref.z_class()) {
                    return cache.z_method;
                }
            }
            return null;
        }
    }

    // get = null 之后 put，这里 put 一定是加新的
    synchronized
    void put(CallSite call_site, @Nullable ZObject object_ref, ZMethod z_method) {
        if (polymorphic) {
            // cache = polymorphic_inline_cache_.computeIfAbsent(call_site, cs -> new LRUCache<>(polymorphic_size))
            Cache<ZClass, ZMethod> cache = polymorphic_inline_cache_.get(call_site);

//            if (cache == null) {
//                // 默认单态
//                cache = new MonomorphicCache();
//                polymorphic_inline_cache_.put(call_site, cache);
//            } else {
//                // 单态切换成多态
//                if (cache instanceof MonomorphicCache) {
//                    MonomorphicCache cache0 = (MonomorphicCache) cache;
//                    // cache = new MegamorphicCache<>(polymorphic_size);
//                    cache = new PolymorphicCache(polymorphic_size);
//                    cache.put(cache0.z_class, cache0.z_method);
//                }
//            }

            if (cache == null) {
                cache = new PolymorphicCache(polymorphic_size);
                polymorphic_inline_cache_.put(call_site, cache);
            }

            if (object_ref == null) {
                cache.put(null, z_method);
            } else {
                cache.put(object_ref.z_class(), z_method);
            }
        } else {
            monomorphic_inline_cache_.put(call_site,
                    new CacheItem(object_ref == null ? null : object_ref.z_class(), z_method));
        }
    }

    static class CallSite {
        final @NotNull ZMethod z_method;
        final int pc;

        CallSite(@NotNull ZMethod z_method, int pc) {
            this.z_method = z_method;
            this.pc = pc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return pc == ((CallSite) o).pc && z_method == ((CallSite) o).z_method;
        }

        @Override
        public int hashCode() {
            return Objects.hash(z_method, pc);
        }
    }

    static class CacheItem {
        final ZClass z_class;
        final ZMethod z_method;
        CacheItem(ZClass z_class, ZMethod z_method) {
            this.z_class = z_class;
            this.z_method = z_method;
        }
    }

    interface Cache<K, V> {
        V get(Object z_class);
        V put(K z_class, V z_method);
    }

    static class MonomorphicCache implements Cache<ZClass, ZMethod> {
        ZClass z_class;
        ZMethod z_method;
        @Override
        public ZMethod get(Object z_class) {
            if (z_class == this.z_class) {
                return z_method;
            } else {
                return null;
            }
        }

        @Override
        public ZMethod put(ZClass z_class, ZMethod z_method) {
            this.z_class = z_class;
            this.z_method = z_method;
            return z_method;
        }
    }

    static class PolymorphicCache implements Cache<ZClass, ZMethod> {
        int pos;
        CacheItem[] buffer;

        PolymorphicCache(int cap) {
            assert cap > 0;
            this.buffer = new CacheItem[cap];
            this.pos = cap - 1;
        }

        // 不保证重复, 必须调用 get() 返回 null 再调用 add
        @Override
        public ZMethod put(ZClass z_class, ZMethod z_method) {
            // 反方向加
            pos = pos - 1;
            pos = pos < 0 ? pos + buffer.length : pos;
            buffer[pos] = new CacheItem(z_class, z_method);
            return z_method;
        }

        @Override
        public ZMethod get(Object z_class) {
            // 正方向加
            // todo 实际容量!!!~~~
            int cap = buffer.length;
            int cap0 = cap;
            // 下一次从上一次找到的开始找~~~
            while (cap0-- > 0) {
                CacheItem it = buffer[pos];
                if (it != null && it.z_class == z_class) {
                    return it.z_method;
                }
                pos = (pos + 1) % cap;
            }
            return null;
        }
    }

    // LRUCache
    static class MegamorphicCache<K, V> extends LinkedHashMap<K, V> implements Cache<K, V> {
        final int capacity;

        public MegamorphicCache(int capacity) {
            super(capacity + 1, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return (size() > this.capacity);
        }
    }
}
