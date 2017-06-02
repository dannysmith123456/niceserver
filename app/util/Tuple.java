package util;

public class Tuple<K, V> {
    public final K k;
    public final V v;

    public Tuple(K k, V v) {
        this.k = k;
        this.v = v;
    }

    @Override
    public String toString() {
        return "(" + this.k.toString() + "," + this.v.toString() + ")";
    }
}
