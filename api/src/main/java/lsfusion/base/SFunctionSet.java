package lsfusion.base;

public abstract class SFunctionSet<K> implements FunctionSet<K> {

    public boolean isEmpty() {
        return false;
    }

    public boolean isFull() {
        return false;
    }
}
