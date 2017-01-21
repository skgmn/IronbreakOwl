package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

class OwlUtils {
    private static Map<Class, Parcelable.Creator> parcelCreators;

    static Object readValue(Cursor cursor, int columnIndex, Class clazz,
                            @Nullable Object obj, @Nullable Field field) {
        try {
            if (clazz == Integer.TYPE) {
                int value = cursor.getInt(columnIndex);
                if (field != null) {
                    field.setInt(obj, value);
                    return null;
                } else {
                    return value;
                }
            } else if (clazz == Long.TYPE) {
                long value = cursor.getLong(columnIndex);
                if (field != null) {
                    field.setLong(obj, value);
                    return null;
                } else {
                    return value;
                }
            } else if (clazz == Boolean.TYPE) {
                boolean value = cursor.getInt(columnIndex) != 0;
                if (field != null) {
                    field.setBoolean(obj, value);
                    return null;
                } else {
                    return value;
                }
            } else if (clazz == Double.TYPE) {
                double value = cursor.getDouble(columnIndex);
                if (field != null) {
                    field.setDouble(obj, value);
                    return null;
                } else {
                    return value;
                }
            } else if (clazz == Float.TYPE) {
                float value = cursor.getFloat(columnIndex);
                if (field != null) {
                    field.setFloat(obj, value);
                    return null;
                } else {
                    return value;
                }
            } else if (clazz == Short.TYPE) {
                short value = cursor.getShort(columnIndex);
                if (field != null) {
                    field.setShort(obj, value);
                    return null;
                } else {
                    return value;
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        Object value;
        if (cursor.isNull(columnIndex)) {
            value = null;
        } else if (clazz == Integer.class) {
            value = cursor.getInt(columnIndex);
        } else if (clazz == String.class) {
            value = cursor.getString(columnIndex);
        } else if (clazz == Long.class) {
            value = cursor.getLong(columnIndex);
        } else if (clazz == Boolean.class) {
            value = cursor.getInt(columnIndex) != 0;
        } else if (clazz == byte[].class) {
            value = cursor.getBlob(columnIndex);
        } else if (clazz == Float.class) {
            value = cursor.getFloat(columnIndex);
        } else if (clazz == Double.class) {
            value = cursor.getDouble(columnIndex);
        } else if (clazz == Short.class) {
            value = cursor.getShort(columnIndex);
        } else if (Parcelable.class.isAssignableFrom(clazz)) {
            Parcel parcel = Parcel.obtain();
            byte[] bytes = cursor.getBlob(columnIndex);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            value = getParcelCreator(clazz).createFromParcel(parcel);
            parcel.recycle();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + clazz.getCanonicalName());
        }
        try {
            if (field != null) {
                field.set(obj, value);
                return null;
            } else {
                return value;
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Parcelable.Creator getParcelCreator(Class clazz) {
        synchronized (OwlUtils.class) {
            Parcelable.Creator creator = parcelCreators == null ? null : parcelCreators.get(clazz);
            if (creator != null) {
                return creator;
            }
        }
        try {
            Field creatorField = clazz.getField("CREATOR");
            creatorField.setAccessible(true);
            Parcelable.Creator creator = (Parcelable.Creator) creatorField.get(clazz);
            if (creator == null) {
                throw new NullPointerException();
            }
            synchronized (OwlUtils.class) {
                if (parcelCreators == null) {
                    parcelCreators = new ArrayMap<>();
                }
                parcelCreators.put(clazz, creator);
            }
            return creator;
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

    static Type getActualType(ParameterizedType pt, int index) {
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
        return type;
    }

    static boolean isModel(Class clazz) {
        return clazz.isAnnotationPresent(Model.class);
    }

    static boolean isColumnType(Class clazz) {
        return clazz == Integer.TYPE ||
                clazz == Long.TYPE ||
                clazz == Boolean.TYPE ||
                clazz == Double.TYPE ||
                clazz == Float.TYPE ||
                clazz == Short.TYPE ||
                clazz == Integer.class ||
                clazz == String.class ||
                clazz == Long.class ||
                clazz == Boolean.class ||
                clazz == byte[].class ||
                clazz == Float.class ||
                clazz == Double.class ||
                clazz == Short.class ||
                (Parcelable.class.isAssignableFrom(clazz) && !isModel(clazz));
    }
}
