package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

class OwlUtils {
    static Object readValue(Cursor cursor, int columnIndex, Class clazz,
                            @Nullable Parcelable.Creator parcelCreator) {
        if (clazz == Integer.TYPE) {
            return cursor.getInt(columnIndex);
        } else if (clazz == Integer.class) {
            return cursor.isNull(columnIndex) ? null : cursor.getInt(columnIndex);
        } else if (clazz == String.class) {
            return cursor.getString(columnIndex);
        } else if (clazz == Long.TYPE) {
            return cursor.getLong(columnIndex);
        } else if (clazz == Long.class) {
            return cursor.isNull(columnIndex) ? null : cursor.getLong(columnIndex);
        } else if (clazz == Boolean.TYPE) {
            return cursor.getInt(columnIndex) != 0;
        } else if (clazz == Boolean.class) {
            return cursor.isNull(columnIndex) ? null : cursor.getInt(columnIndex) != 0;
        } else if (clazz == byte[].class) {
            return cursor.getBlob(columnIndex);
        } else if (clazz == Float.TYPE) {
            return cursor.getFloat(columnIndex);
        } else if (clazz == Float.class) {
            return cursor.isNull(columnIndex) ? null : cursor.getFloat(columnIndex);
        } else if (clazz == Double.TYPE) {
            return cursor.getDouble(columnIndex);
        } else if (clazz == Double.class) {
            return cursor.isNull(columnIndex) ? null : cursor.getDouble(columnIndex);
        } else if (clazz == Short.TYPE) {
            return cursor.getShort(columnIndex);
        } else if (clazz == Short.class) {
            return cursor.isNull(columnIndex) ? null : cursor.getShort(columnIndex);
        } else if (Parcelable.class.isAssignableFrom(clazz)) {
            Parcel parcel = Parcel.obtain();
            byte[] bytes = cursor.getBlob(columnIndex);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            if (parcelCreator == null) {
                parcelCreator = getParcelCreator(clazz);
            }
            Object obj = parcelCreator.createFromParcel(parcel);
            parcel.recycle();
            return obj;
        } else {
            throw new IllegalArgumentException("Unsupported type: " + clazz.getCanonicalName());
        }
    }

    static Parcelable.Creator getParcelCreator(Class clazz) {
        try {
            Field creatorField = clazz.getField("CREATOR");
            creatorField.setAccessible(true);
            Parcelable.Creator parcelCreator = (Parcelable.Creator) creatorField.get(clazz);
            if (parcelCreator == null) {
                throw new NullPointerException();
            }
            return parcelCreator;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Cannot find CREATOR for " + clazz.getCanonicalName());
        }
    }

    static void putValue(ContentValues values, String column, Object value) {
        if (value == null) {
            values.putNull(column);
        } else if (value instanceof Boolean) {
            values.put(column, (Boolean) value ? 1 : 0);
        } else if (value instanceof Byte) {
            values.put(column, (Byte) value);
        } else if (value instanceof byte[]) {
            values.put(column, (byte[]) value);
        } else if (value instanceof Double) {
            values.put(column, (Double) value);
        } else if (value instanceof Float) {
            values.put(column, (Float) value);
        } else if (value instanceof Integer) {
            values.put(column, (Integer) value);
        } else if (value instanceof Long) {
            values.put(column, (Long) value);
        } else if (value instanceof Short) {
            values.put(column, (Short) value);
        } else if (value instanceof String) {
            values.put(column, (String) value);
        } else if (value instanceof CharSequence) {
            values.put(column, value.toString());
        } else if (value instanceof Parcelable) {
            Parcel parcel = Parcel.obtain();
            ((Parcelable) value).writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            byte[] bytes = parcel.marshall();
            values.put(column, bytes);
        } else {
            PlainDataModel.putInto(values, value);
        }
    }

    static Class getActualType(ParameterizedType pt, int index) {
        Type type = pt.getActualTypeArguments()[index];
        if (type instanceof GenericArrayType) {
            Type dataType = ((GenericArrayType) type).getGenericComponentType();
            if (dataType == Boolean.TYPE) {
                return boolean[].class;
            } else if (dataType == Character.TYPE) {
                return char[].class;
            } else if (dataType == Byte.TYPE) {
                return byte[].class;
            } else if (dataType == Short.TYPE) {
                return short[].class;
            } else if (dataType == Integer.TYPE) {
                return int[].class;
            } else if (dataType == Long.TYPE) {
                return long[].class;
            } else if (dataType == Float.TYPE) {
                return float[].class;
            } else if (dataType == Double.TYPE) {
                return double[].class;
            }
        }
        //noinspection ConstantConditions
        return (Class) type;
    }
}
