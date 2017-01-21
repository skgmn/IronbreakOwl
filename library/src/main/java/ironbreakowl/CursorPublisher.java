package ironbreakowl;

import android.database.Cursor;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

abstract class CursorPublisher<T> implements Publisher<T> {
    @SuppressWarnings("WeakerAccess")
    final IndexedCursor indexedCursor;

    CursorPublisher(Cursor cursor) {
        indexedCursor = new IndexedCursor(cursor);
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        s.onSubscribe(new Subscription() {
            private volatile boolean reading;
            private volatile boolean canceled;

            @Override
            public void request(long n) {
                reading = true;
                try {
                    for (int i = 0; i < n && !canceled; ++i) {
                        final boolean complete;
                        if (indexedCursor.moveToNext()) {
                            T obj = readValue(indexedCursor);
                            s.onNext(obj);
                            complete = indexedCursor.cursor.isLast();
                        } else {
                            complete = true;
                        }
                        if (complete) {
                            indexedCursor.cursor.close();
                            s.onComplete();
                            break;
                        }
                    }
                } catch (Throwable e) {
                    s.onError(e);
                } finally {
                    if (canceled && !indexedCursor.cursor.isClosed()) {
                        indexedCursor.cursor.close();
                    }
                    reading = false;
                }
            }

            @Override
            public void cancel() {
                canceled = true;
                if (!reading && !indexedCursor.cursor.isClosed()) {
                    indexedCursor.cursor.close();
                }
            }
        });
    }

    abstract T readValue(IndexedCursor cursor) throws Exception;
}
