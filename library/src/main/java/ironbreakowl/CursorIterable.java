package ironbreakowl;

import android.database.Cursor;

import java.util.Iterator;

abstract class CursorIterable<T> implements Iterable<T> {
    @SuppressWarnings("WeakerAccess")
    final IndexedCursor indexedCursor;
    private boolean iteratorCreated;

    CursorIterable(Cursor cursor) {
        indexedCursor = new IndexedCursor(cursor);
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
                    if (!(hasNext = indexedCursor.moveToNext())) {
                        indexedCursor.cursor.close();
                    }
                }
                return hasNext;
            }

            @Override
            public T next() {
                try {
                    T obj = readValue(indexedCursor);
                    if (indexedCursor.cursor.isLast()) {
                        hasNextCalled = true;
                        hasNext = false;
                        indexedCursor.cursor.close();
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

    abstract T readValue(IndexedCursor cursor) throws Exception;
}