package ironbreakowl;

import android.database.Cursor;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

class CursorCloser<T> extends WeakReference<T> {
    private Cursor cursor;

    CursorCloser(Cursor cursor, T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        this.cursor = cursor;
    }

    void close() {
        Cursor c = cursor;
        if (c != null) {
            if (!c.isClosed()) {
                c.close();
            }
            cursor = null;
        }
    }
}
