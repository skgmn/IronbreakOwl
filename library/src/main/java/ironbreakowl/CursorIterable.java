package ironbreakowl;

import android.database.Cursor;

import java.util.Iterator;

abstract class CursorIterable<T> implements Iterable<T> {
    @SuppressWarnings("WeakerAccess")
    final Cursor cursor;
    private boolean iteratorCreated;

    CursorIterable(Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public Iterator<T> iterator() {
        if (iteratorCreated) {
            throw new IllegalStateException("This Iterable only allows one Iterator instance");
        }
        iteratorCreated = true;
        return new Iterator<T>() {
            private boolean hasNext;
            private boolean hasNextCalled;

            @Override
            public boolean hasNext() {
                if (!hasNextCalled) {
                    hasNextCalled = true;
                    hasNext = cursor.moveToNext();
                    if (!hasNext) {
                        cursor.close();
                    }
                }
                return hasNext;
            }

            @Override
            public T next() {
                try {
                    T obj = readValue(cursor);
                    if (cursor.isLast()) {
                        hasNextCalled = true;
                        hasNext = false;
                        cursor.close();
                    } else {
                        hasNextCalled = false;
                    }
                    return obj;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    protected abstract T readValue(Cursor cursor) throws Exception;
}