package ironbreakowl;

public class Single<T> {
    private final T mValue;
    private final boolean mHasValue;

    private Single(T value, boolean hasValue) {
        mValue = value;
        mHasValue = hasValue;
    }

    public T getValue() {
        return mValue;
    }

    public boolean hasValue() {
        return mHasValue;
    }

    public static <T> Single<T> of(T value) {
        return new Single<>(value, true);
    }

    public static <T> Single<T> empty() {
        return new Single<>(null, false);
    }
}
