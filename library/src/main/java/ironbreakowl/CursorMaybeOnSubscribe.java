package ironbreakowl;

import android.database.Cursor;

import io.reactivex.MaybeEmitter;
import io.reactivex.MaybeOnSubscribe;

abstract class CursorMaybeOnSubscribe<T> implements MaybeOnSubscribe<T> {
    private final IndexedCursor indexedCursor;

    CursorMaybeOnSubscribe(Cursor cursor) {
        indexedCursor = new IndexedCursor(cursor);
    }

    @Override
    public void subscribe(MaybeEmitter<T> emitter) throws Exception {
        try {
            if (indexedCursor.moveToNext()) {
                T obj = readValue(indexedCursor);
                emitter.onSuccess(obj);
            } else {
                emitter.onComplete();
            }
        } catch (Throwable e) {
            emitter.onError(e);
        } finally {
            indexedCursor.cursor.close();
        }
    }

    protected abstract T readValue(IndexedCursor cursor) throws Exception;
}