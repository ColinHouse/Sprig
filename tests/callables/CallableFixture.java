package fixture;
import sprig.runtime.*;
public final class CallableFixture {
 private final Fn0<Long> callback;
 public CallableFixture(Fn0<Long> callback) {this.callback=callback;}
 public long invoke() {return callback.apply();}
 public static int int32(Fn1<Integer,Integer> f) {return f.apply(3);}
 public static double floating(Fn1<Double,Double> f) {return f.apply(0.5);}
 public static boolean booleanValue(Fn1<Boolean,Boolean> f) {return f.apply(false);}

 public static long nested(Fn1<Fn0<Long>,Long> f) {return f.apply(() -> 17L);}
 public static void shortSlot(Fn1<Short,Short> f) {}
 public static void wildcard(Fn1<? super Long,String> f) {}
 public static long zero(Fn0<Long> f) {return f.apply();}
 public static String one(Fn1<Long,String> f) {return f.apply(7L);}
 public static long two(Fn2<Long,Long,Long> f) {return f.apply(2L,3L);}
 public static long three(Fn3<Long,Long,Long,Long> f) {return f.apply(1L,2L,3L);}
 public static StringBuilder reference(Fn1<StringBuilder,StringBuilder> f) {return f.apply(new StringBuilder("ref"));}
 public static Fn1<Long,Long> returned() {return x -> x + 1;}
 public static Fn1<Long,String> badResult() {return x -> null;}
 public static String nullInput(Fn1<String,String> f) {return f.apply(null);}
 public static void unit(Fn0<Void> f) {f.apply();}
 public static void sam(java.util.function.Function<Long,String> f) {}
 public static String samResult(java.util.function.Function<Long,String> f) {return f.apply(7L);}
 public static void raw(Fn1 f) {}
 public static <T> void unresolved(Fn1<T,T> f) {}
 public static String ambiguous(Fn0<Long> f, Object x) {return "object";}
 public static String ambiguous(Fn0<Long> f, java.io.Serializable x) {return "serializable";}
}
