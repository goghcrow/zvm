package zvm;

import java.util.*;

/**
 * 玩具 Fast Subtype Checking
 * @author chuxiaofeng
 * https://www.zhihu.com/question/21574535
 * 论文 https://dl.acm.org/doi/pdf/10.1145/583810.583821
 */
public final class FastSubtypeChecker {
    private final ZClass z_class;

    private boolean is_primary_type_;
    // private int depth_;
    // ( primary 或 secondary ) subtype 缓存上一次 supertype
    private ZClass[] primary_supertypes_; // 非 primitive 都有
    private ZClass[] secondary_supertypes_; // 非 primitive 都有

    private ZClass secondary_super_monomorphic_cache_;

    FastSubtypeChecker(ZClass z_class) {
        this.z_class = z_class;

        if (!z_class.is_primitive()) {
            if (z_class.is_array()) {
                // 基础类型数组或者 primary 数组
                ZClass component_class = z_class.component_class();
                this.is_primary_type_ = component_class.is_primitive() ||
                        component_class.fsc_.is_primary_type();
            } else {
                this.is_primary_type_ = !z_class.is_interface();
            }
        }
    }

    public boolean fast_is_assignable_from(ZClass subtype) {
        // ↓ fast-routines ↓

        z_class.vm.check_null(subtype);

        ZClass supertype = z_class;
        if (supertype == subtype.fsc_.secondary_super_monomorphic_cache_) {
            return true;
        }

        if (supertype == subtype) {
            subtype.fsc_.secondary_super_monomorphic_cache_ = supertype;
            return true;
        }

        // 如果是基础类型，必须精确相等
        if (supertype.is_primitive() || subtype.is_primitive()) {
            return false;
        }

        // 缓存一下👇的判断结果...
        boolean is = supertype.fsc_.fast_is_assignable_from0(subtype);
        if (is) {
            subtype.fsc_.secondary_super_monomorphic_cache_ = supertype;
        }

        return is;
    }

    // subtype is subtype of this
    private boolean fast_is_assignable_from0(ZClass subtype) {
        ZClass supertype = z_class;
        if (supertype.fsc_.is_primary_type()) {
            return subtype.fsc_.depth() >= supertype.fsc_.depth()
                    && supertype == subtype.fsc_.primary_super_types()[supertype.fsc_.depth()];
        }

        ZClass[] s_s_array = subtype.fsc_.secondary_supertypes();
        for (ZClass s : s_s_array) {
            if (supertype == s) {
                subtype.fsc_.secondary_super_monomorphic_cache_ = supertype;
                return true;
            }
        }
        return false;
    }

    private int depth() {
        // assert is_primary_type();
        return primary_super_types().length - 1;
    }

    private boolean is_primary_type() {
        assert !z_class.is_primitive();
        return is_primary_type_;
    }

    private ZClass[] primary_super_types() {
        if (primary_supertypes_ == null) {
            primary_supertypes_ = primary_super_types0();
        }
        return primary_supertypes_;
    }

    private ZClass[] secondary_supertypes() {
        if (secondary_supertypes_ == null) {
            secondary_supertypes_ = secondary_supertypes0();
        }
        return secondary_supertypes_;
    }

    private ZClass direct_primary_supertype() {
        assert !z_class.is_primitive();
        if (z_class.is_array()) {
            ZClass com_class = z_class.component_class();
            if (com_class.is_primitive() || com_class == z_class.vm.java_lang_Object) {
                return z_class.vm.java_lang_Object;
            }
            // 这里重复计算了
            return com_class.fsc_.direct_primary_supertype().array_class();
        } else {
            if (z_class.is_interface()) {
                return z_class.vm.java_lang_Object;
            } else {
                return z_class.super_class();
            }
        }
    }

    private ZClass[] primary_super_types0() {
        assert !z_class.is_primitive();
        List<ZClass> lst = new ArrayList<>();
        ZClass z_class = this.z_class;
        while (z_class != null) {
            lst.add(z_class); // 其实不用把自己加进去
            z_class = z_class.fsc_.direct_primary_supertype();
        }
        Collections.reverse(lst);
        return lst.toArray(new ZClass[0]);
    }

    private ZClass[] secondary_supertypes0() {
        assert !z_class.is_primitive();
        Set<ZClass> sets = new LinkedHashSet<>();
        if (z_class.is_array()) {
            sets.add(z_class.vm.java_lang_Cloneable);
            sets.add(z_class.vm.java_io_Serializable);
        }
        secondary_supertypes0(sets);
        return sets.toArray(new ZClass[0]);
    }

    private void secondary_supertypes0(Set<ZClass> sets) {
        if (z_class.is_primitive()) {
            return;
        }
        if (z_class.is_array()) {
            sets.add(z_class.vm.java_lang_Cloneable);
            sets.add(z_class.vm.java_io_Serializable);
            Set<ZClass> sets0 = new LinkedHashSet<>();
            z_class.component_class().fsc_.secondary_supertypes0(sets0);
            for (ZClass sst : sets0) {
                sets.add(sst.array_class());
            }
        } else {
            ZClass z_class = this.z_class;
            while (z_class != null) {
                secondary_superinterfaces(sets, z_class.interfaces());
                z_class = z_class.super_class();
            }
        }
    }

    private void secondary_superinterfaces(Set<ZClass> sets, ZClass[] interfaces) {
        for (ZClass iface : interfaces) {
            sets.add(iface);
            secondary_superinterfaces(sets, iface.interfaces());
        }
    }
}
