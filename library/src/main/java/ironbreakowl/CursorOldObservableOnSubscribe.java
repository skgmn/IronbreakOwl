package ironbreakowl;

import android.database.Cursor;

import rx.Observable;
import rx.subscriptions.Subscriptions;

abstract class CursorOldObservableOnSubscribe<T> implements Observable.OnSubscribe<T> {
    @SuppressWarnings("WeakerAccess")
    final Cursor cursor;
    private volatile boolean reading;

    CursorOldObservableOnSubscribe(Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public void call(rx.Subscriber<? super T> s) {
        s.setProducer(n -> {
            reading = true;
            try {
                for (int i = 0; i < n && !s.isUnsubscribed(); ++i) {
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
                        s.onCompleted();
                        break;
                    }
                }
            } catch (Throwable e) {
                s.onError(e);
            } finally {
                if (s.isUnsubscribed() && !cursor.isClosed()) {
                    cursor.close();
                }
                reading = false;
            }
        });
        s.add(Subscriptions.create(() -> {
            if (!reading && !cursor.isClosed()) {
                cursor.close();
            }
        }));
    }

    abstract T readValue(Cursor cursor) throws Exception;
}
