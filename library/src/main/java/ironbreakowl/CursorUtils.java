package ironbreakowl;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

public class CursorUtils {
    public static Object readValue(Cursor cursor, int columnIndex, Class clazz,
                                   @Nullable Parcelable.Creator parcelCreator) {
        if (clazz == Integer.TYPE || clazz == Integer.class) {
            return cursor.getInt(columnIndex);
        } else if (clazz == String.class) {
            return cursor.getString(columnIndex);
        } else if (clazz == Long.TYPE || clazz == Long.class) {
            return cursor.getLong(columnIndex);
        } else if (clazz == byte[].class) {
            return cursor.getBlob(columnIndex);
        } else if (clazz == Float.TYPE || clazz == Float.class) {
            return cursor.getFloat(columnIndex);
        } else if (clazz == Double.TYPE || clazz == Double.class) {
            return cursor.getDouble(columnIndex);
        } else if (clazz == Short.TYPE || clazz == Short.class) {
            return cursor.getShort(columnIndex);
        } else if (Parcelable.class.isAssignableFrom(clazz)) {
            Parcel parcel = Parcel.obtain();
            byte[] bytes = cursor.getBlob(columnIndex);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            if (parcelCreator == null) {
                parcelCreator = ReflectionUtils.getParcelCreator(clazz);
            }
            Object obj = parcelCreator.createFromParcel(parcel);
            parcel.recycle();
            return obj;
        } else {
            throw new IllegalArgumentException("Unsupported type: " + clazz.getCanonicalName());
        }
    }
}
