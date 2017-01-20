package ironbreakowl;

import android.database.Cursor;

import io.reactivex.MaybeEmitter;
import io.reactivex.MaybeOnSubscribe;

abstract class CursorMaybeOnSubscribe<T> implements MaybeOnSubscribe<T> {
    private final Cursor cursor;

    CursorMaybeOnSubscribe(Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public void subscribe(MaybeEmitter<T> emitter) throws Exception {
        try {
            if (cursor.moveToNext()) {
                T obj = readValue(cursor);
                emitter.onSuccess(obj);
            } else {
                emitter.onComplete();
            }
        } catch (Throwable e) {
            emitter.onError(e);
        } finally {
            cursor.close();
        }
    }

    protected abstract T readValue(Cursor cursor) throws Exception;
}