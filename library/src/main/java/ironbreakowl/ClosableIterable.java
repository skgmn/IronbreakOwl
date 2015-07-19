package ironbreakowl;

public interface ClosableIterable<T> extends Iterable<T> {
    ClosableIterator<T> iterator();
}
