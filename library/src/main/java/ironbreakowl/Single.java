package ironbreakowl;

public class Single<T> {
    public final T value;
    public final boolean hasValue;

    private Single(T value, boolean hasValue) {
        this.value = value;
        this.hasValue = hasValue;
    }

    public static <T> Single<T> of(T value) {
        return new Single<>(value, true);
    }

    public static <T> Single<T> empty() {
        return new Single<>(null, false);
    }
}
