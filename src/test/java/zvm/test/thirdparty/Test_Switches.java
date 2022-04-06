package zvm.test.thirdparty;

import java.util.ArrayList;
import java.util.List;

// 测试用例修改自 github.com/lihaoyi/Metascala
// 测试用例修改自 https://github.com/zxh0/jvm.go/tree/master/test/testclasses/src/main/java/jvm/instructions
public class Test_Switches {

    public static Object LookupSwitch() {
        Object[] r = new Object[10];
        for (int i = 0; i < 10; i++) {
            switch (i) {
                //noinspection ConstantConditions
                case -100: r[i] = i; break;
                case 0: r[i] = i; break;
                case 3: r[i] = i; break;
                case 5: r[i] = i; break;
                default: r[i] = "default";
            }
        }
        return r;
    }

    public static Object TableSwitch() {
        Object[] r = new Object[10];
        for (int i = 0; i < 10; i++) {
            switch (i) {
                case 3: r[i] = i; break;
                case 4: r[i] = i; break;
                case 5: r[i] = i; break;
                default: r[i] = "default";
            }
        }
        return r;
    }

    public static List<Object> test() {
        List<Object> lst = new ArrayList<>();
        for (int i = -1; i < 4; i++) {
            lst.add(Test_Switches.smallSwitch(i));
        }

        for (int i = -1; i < 31; i++) {
            lst.add(Test_Switches.bigDenseSwitch(i));
        }

        for (int i = 1; i <= 2097152; i *= 2) {
            lst.add(Test_Switches.bigSparseSwitch(i));
        }

        for (int i = 'a' - 1; i < 'l'; i++) {
            lst.add(Test_Switches.charSwitch(((char) i)));
        }

        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            lst.add(Test_Switches.byteSwitch(((byte) i)));
        }

        for (int i = -1; i < 4; i++) {
            lst.add(Test_Switches.stringSwitch(i));
        }

        for (String s : new String[]{"omg", "wtf", "bbq", "..."}) {
            lst.add(Test_Switches.stringSwitchTwo(s));
        }

        return lst;
    }


    static int smallSwitch(int a) {
        switch(a){
            case 0:  return 1;
            case 1:  return 0;
            default: return 2;
        }
    }
    static double bigDenseSwitch(int a) {
        switch(a){
            case 0:  return 1123.213;
            case 1:  return 3212.321;
            case 2:  return 123123.123;
            case 3:  return 123.312;
            case 4:  return 123123.1231;
            case 5:  return 1231.3212;
            case 6:  return 123.132123;
            case 7:  return 32123.123;
            case 8:  return 123123.12312;
            case 9:  return 123123.3123;
            case 10: return 123123.1312;
            case 11: return 123123.2123;
            case 12: return 123321.123;
            case 13: return 123123.12312;
            case 14: return 123123.1231;
            case 15: return 1321231.1231;
            case 16: return 23123123.1231;
            case 17: return 123123123.123123;
            case 18: return 123123.1231;
            case 19: return 23123.12321;
            case 20: return 12312312.321;
            case 21: return 1232312.312;
            case 22: return 123123123.132123;
            case 23: return 123123123.1231;
            case 24: return 132123.1231;
            case 25: return 12312321.123;
            case 26: return 1232123.312;
            case 27: return 123123.12312;
            case 28: return 13212312.123123;
            case 29: return 2123123.1231231;
            default: return 123123.123123;
        }
    }
    static double bigSparseSwitch(int a) {
        switch(a){
            case 1:         return 3212.321;
            case 2:         return 123123.123;
            case 4:         return 123.312;
            case 8:         return 123123.1231;
            case 16:        return 1231.3212;
            case 32:        return 123.132123;
            case 64:        return 32123.123;
            case 128:       return 123123.12312;
            case 256:       return 123123.3123;
            case 512:       return 123123.1312;
            case 1024:      return 123123.2123;
            case 2048:      return 123321.123;
            case 4096:      return 123123.12312;
            case 8192:      return 123123.1231;
            case 16384:     return 1321231.1231;
            case 32768:     return 23123123.1231;
            case 65536:     return 123123123.123123;
            case 131072:    return 123123.1231;
            case 262144:    return 23123.12321;
            case 524288:    return 12312312.321;
            case 1048576:   return 1232312.312;
            case 2097152:   return 123123123.132123;
            default: return 123123.123123;
        }
    }
    static int charSwitch(char c) {
        switch(c){
            case 'a': return 1;
            case 'b': return 2;
            case 'c': return 3;
            case 'd': return 4;
            case 'e': return 5;
            case 'f': return 6;
            case 'g': return 7;
            case 'h': return 8;
            case 'i': return 9;
            case 'j': return 0;
            default: return 10;
        }
    }
    static int byteSwitch(byte b) {
        switch(b){
            case 1:     return 1;
            case 2:     return 2;
            case 4:     return 3;
            case 16:    return 4;
            case 32:    return 5;
            case 64:    return 6;
            case 127:   return 7;
            case -128:  return 8;
            default:    return 10;
        }
    }
    static int stringSwitch(int n) {
        switch("" + n){
            case "0": return 0;
            case "1": return 1;
            default:  return 2;
        }
    }
    static String stringSwitchTwo(String s) {
        switch(s){
            case "omg": return "iam";
            case "wtf": return "cow";
            case "bbq": return "hearme";
            default:    return "moo";
        }
    }
}
