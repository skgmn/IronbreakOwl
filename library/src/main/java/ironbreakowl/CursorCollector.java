package ironbreakowl;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;

class CursorCollector {
    private static final HashMap<Class, CursorCollector> sCollectors = new HashMap<>();

    private static class FieldInfo {
        public Column column;
        public Class type;
        public Parcelable.Creator parcelCreator;
    }

    public final ArrayList<Pair<Field, FieldInfo>> fields = new ArrayList<>();

    public static <T> ArrayList<T> collect(final Cursor cursor, Class<T> clazz) {
        final CursorCollector collector = getCollector(clazz);
        ArrayList<T> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            try {
                T obj = clazz.newInstance();
                for (Pair<Field, FieldInfo> pair : collector.fields) {
                    Field field = pair.first;
                    FieldInfo fieldInfo = pair.second;
                    String columnName = fieldInfo.column.value();
                    int columnIndex = cursor.getColumnIndex(columnName);
                    Class type = fieldInfo.type;
                    if (type == Integer.TYPE || type == Integer.class) {
                        field.setInt(obj, cursor.getInt(columnIndex));
                    } else if (type == String.class) {
                        field.set(obj, cursor.getString(columnIndex));
                    } else if (type == Long.TYPE || type == Long.class) {
                        field.setLong(obj, cursor.getLong(columnIndex));
                    } else if (type == byte[].class) {
                        field.set(obj, cursor.getBlob(columnIndex));
                    } else if (type == Float.TYPE || type == Float.class) {
                        field.setFloat(obj, cursor.getFloat(columnIndex));
                    } else if (type == Double.TYPE || type == Double.class) {
                        field.setDouble(obj, cursor.getDouble(columnIndex));
                    } else if (type == Short.TYPE || type == Short.class) {
                        field.setShort(obj, cursor.getShort(columnIndex));
                    } else if (Parcelable.class.isAssignableFrom(type)) {
                        Parcelable.Creator parcelCreator = fieldInfo.parcelCreator;
                        if (parcelCreator == null) {
                            throw new IllegalArgumentException("Cannot find CREATOR for " + type.getCanonicalName());
                        } else {
                            Parcel parcel = Parcel.obtain();
                            byte[] bytes = cursor.getBlob(columnIndex);
                            parcel.unmarshall(bytes, 0, bytes.length);
                            parcel.setDataPosition(0);
                            field.set(obj, parcelCreator.createFromParcel(parcel));
                            parcel.recycle();
                        }
                    } else {
                        throw new IllegalArgumentException("Unsupported type: " + type.getCanonicalName());
                    }
                }
                list.add(obj);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return list;
    }

    private static CursorCollector getCollector(Class clazz) {
        synchronized (sCollectors) {
            CursorCollector collector = sCollectors.get(clazz);
            if (collector == null) {
                collector = parseClass(clazz);
                sCollectors.put(clazz, collector);
            }
            return collector;
        }
    }

    private static CursorCollector parseClass(Class clazz) {
        if (clazz.isInterface() || (clazz.getModifiers() & Modifier.ABSTRACT) == Modifier.ABSTRACT) {
            throw new IllegalArgumentException("Interface or abstract class is not allowed: "
                    + clazz.getCanonicalName());
        }
        CursorCollector collector = new CursorCollector();
        ArrayList<Pair<Field, FieldInfo>> fields = collector.fields;
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column == null) continue;

                FieldInfo fieldInfo = new FieldInfo();
                Class<?> fieldType = field.getType();
                fieldInfo.column = column;
                fieldInfo.type = fieldType;
                if (Parcelable.class.isAssignableFrom(fieldType)) {
                    try {
                        Field creatorField = fieldType.getField("CREATOR");
                        creatorField.setAccessible(true);
                        Parcelable.Creator parcelCreator = (Parcelable.Creator) creatorField.get(fieldType);
                        if (parcelCreator == null) {
                            throw new NullPointerException();
                        }
                        fieldInfo.parcelCreator = parcelCreator;
                    } catch (Exception ignored) {
                        throw new IllegalArgumentException("Cannot find CREATOR for " + fieldType.getCanonicalName());
                    }
                }
                field.setAccessible(true);
                fields.add(new Pair<>(field, fieldInfo));
            }
            clazz = clazz.getSuperclass();
        }
        return collector;
    }
}
