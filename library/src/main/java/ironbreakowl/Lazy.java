package ironbreakowl;

import android.support.annotation.NonNull;

import java.util.concurrent.Callable;

public class Lazy<T> {
    private final Object lock = new Object();
    private Callable<T> callable;
    private T value;

    Lazy(@NonNull Callable<T> callable) {
        this.callable = callable;
    }

    public T get() {
        synchronized (lock) {
            if (callable == null) {
                return value;
            } else {
                try {
                    Callable c = callable;
                    callable = null;
                    //noinspection unchecked
                    return value = (T) c.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
