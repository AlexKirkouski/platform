package lsfusion.base;

public class Pair<Class1, Class2> {

    public final Class1 first;
    public final Class2 second;

    public Pair(Class1 first, Class2 second) {
        this.first = first;
        this.second = second;
    }

    // чтобы короче ситнаксис был
    public static <Class1,Class2> Pair<Class1,Class2> create(Class1 first, Class2 second) {
        return new Pair<>(first, second);
    }

    public String toString() { return "(" + first.toString() + "," + second.toString() + ")"; }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Pair && first.equals(((Pair) o).first) && second.equals(((Pair) o).second);

    }

    @Override
    public int hashCode() {
        return 31 * first.hashCode() + second.hashCode();
    }
}
