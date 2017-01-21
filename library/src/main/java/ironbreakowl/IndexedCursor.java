package ironbreakowl;

import android.database.Cursor;

import java.util.concurrent.atomic.AtomicInteger;

class IndexedCursor {
    final Cursor cursor;
    final AtomicInteger index = new AtomicInteger(-1);

    IndexedCursor(Cursor cursor) {
        this.cursor = cursor;
    }

    boolean moveToNext() {
        boolean moved = cursor.moveToNext();
        if (moved) {
            index.incrementAndGet();
        }
        return moved;
    }
}