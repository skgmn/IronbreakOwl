package ironbreakowl;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import rx.Observable;

final class SingleColumn {
    static <T> Iterable<T> newIterable(Cursor cursor, Class<T> clazz) {
        return new CursorIterable<T>(cursor) {
            @Override
            protected T readValue(IndexedCursor cursor) throws Exception {
                //noinspection unchecked
                return (T) OwlUtils.readValue(cursor.cursor, 0, clazz, null, null);
            }
        };
    }

    static <T> List<T> newList(Cursor cursor, Class<T> clazz) {
        List<T> list = new ArrayList<T>();
        while (cursor.moveToNext()) {
            //noinspection unchecked
            T value = (T) OwlUtils.readValue(cursor, 0, clazz, null, null);
            list.add(value);
        }
        return list;
    }

    static <T> Observable<T> newOldObservable(Cursor cursor, Class<T> clazz) {
        return Observable.create(new CursorOldObservableOnSubscribe<T>(cursor) {
            @Override
            T readValue(IndexedCursor cursor) throws Exception {
                //noinspection unchecked
                return (T) OwlUtils.readValue(cursor.cursor, 0, clazz, null, null);
            }
        });
    }

    static <T> Flowable<T> newFlowable(Cursor cursor, Class<T> clazz) {
        return Flowable.unsafeCreate(new CursorPublisher<T>(cursor) {
            @Override
            protected T readValue(IndexedCursor cursor) throws Exception {
                //noinspection unchecked
                return (T) OwlUtils.readValue(cursor.cursor, 0, clazz, null, null);
            }
        });
    }

    static <T> Maybe<T> newMaybe(Cursor cursor, Class<T> clazz) {
        return Maybe.create(new CursorMaybeOnSubscribe<T>(cursor) {
            @Override
            protected T readValue(IndexedCursor cursor) throws Exception {
                //noinspection unchecked
                return (T) OwlUtils.readValue(cursor.cursor, 0, clazz, null, null);
            }
        });
    }
}
