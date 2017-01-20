package ironbreakowl;

import android.database.Cursor;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

abstract class CursorPublisher<T> implements Publisher<T> {
    @SuppressWarnings("WeakerAccess")
    final Cursor cursor;

    CursorPublisher(Cursor cursor) {
        this.cursor = cursor;
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
                        if (cursor.moveToNext()) {
                            T obj = readValue(cursor);
                            s.onNext(obj);
                            complete = cursor.isLast();
                        } else {
                            complete = true;
                        }
                        if (complete) {
                            cursor.close();
                            s.onComplete();
                            break;
                        }
                    }
                } catch (Throwable e) {
                    s.onError(e);
                } finally {
                    if (canceled && !cursor.isClosed()) {
                        cursor.close();
                    }
                    reading = false;
                }
            }

            @Override
            public void cancel() {
                canceled = true;
                if (!reading && !cursor.isClosed()) {
                    cursor.close();
                }
            }
        });
    }

    protected abstract T readValue(Cursor cursor) throws Exception;
}
