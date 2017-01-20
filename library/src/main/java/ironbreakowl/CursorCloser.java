package ironbreakowl;

import android.database.Cursor;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

class CursorCloser<T> extends PhantomReference<T> {
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
