package ironbreakowl;

import android.database.Cursor;

import rx.Observable;
import rx.subscriptions.Subscriptions;

abstract class CursorOldObservableOnSubscribe<T> implements Observable.OnSubscribe<T> {
    @SuppressWarnings("WeakerAccess")
    final IndexedCursor indexedCursor;
    private volatile boolean reading;

    CursorOldObservableOnSubscribe(Cursor cursor) {
        indexedCursor = new IndexedCursor(cursor);
    }

    @Override
    public void call(rx.Subscriber<? super T> s) {
        s.setProducer(n -> {
            reading = true;
            try {
                for (int i = 0; i < n && !s.isUnsubscribed(); ++i) {
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
                        s.onCompleted();
                        break;
                    }
                }
            } catch (Throwable e) {
                s.onError(e);
            } finally {
                if (s.isUnsubscribed() && !indexedCursor.cursor.isClosed()) {
                    indexedCursor.cursor.close();
                }
                reading = false;
            }
        });
        s.add(Subscriptions.create(() -> {
            if (!reading && !indexedCursor.cursor.isClosed()) {
                indexedCursor.cursor.close();
            }
        }));
    }

    abstract T readValue(IndexedCursor cursor) throws Exception;
}
