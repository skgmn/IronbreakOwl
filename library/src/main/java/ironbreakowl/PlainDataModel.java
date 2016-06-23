package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;

class PlainDataModel {
    private static final HashMap<Class, PlainDataModel> sCollectors = new HashMap<>();

    private static class FieldInfo {
        public Column column;
        public Class type;
        public Parcelable.Creator parcelCreator;

        public void setType(Class type) {
            this.type = type;
            parcelCreator = Parcelable.class.isAssignableFrom(type) ? OwlUtils.getParcelCreator(type) : null;
        }
    }

    public final List<Pair<Field, FieldInfo>> fields = new ArrayList<>();
    public Constructor constructor;
    public String[] passedParameterNames;
    public FieldInfo[] parameters;

    public static void putInto(ContentValues values, Object o) {
        Class<?> clazz = o.getClass();
        PlainDataModel model = getModel(clazz);
        if (model != null) {
            for (Pair<Field, FieldInfo> entry : model.fields) {
                try {
                    Field field = entry.first;
                    FieldInfo info = entry.second;
                    Column column = info.column;
                    OwlUtils.putValue(values, column.value(), field.get(o));
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static <T> ArrayList<T> collect(final Cursor cursor, Class<T> clazz, Map<String, Object> passedParameters) {
        final PlainDataModel collector = getModel(clazz);
        ArrayList<T> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            try {
                T obj = fetchRow(cursor, collector, passedParameters);
                list.add(obj);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return list;
    }

    public static <T> Observable<T> observe(final Cursor cursor, Class<T> clazz, Map<String, Object> passedParameters) {
        final PlainDataModel collector = getModel(clazz);
        return Observable.create(subscriber -> {
            try {
                while (cursor.moveToNext()) {
                    T obj = fetchRow(cursor, collector, passedParameters);
                    subscriber.onNext(obj);
                }
            } catch (Throwable e) {
                subscriber.onError(e);
            } finally {
                cursor.close();
                subscriber.onCompleted();
            }
        });
    }

    public static <T> Single<T> readSingle(final Cursor cursor, Class<T> clazz, Map<String, Object> passedParameters) {
        final PlainDataModel collector = getModel(clazz);
        if (cursor.moveToNext()) {
            try {
                return Single.of(fetchRow(cursor, collector, passedParameters));
            } catch (Exception e) {
                return Single.empty();
            }
        } else {
            return Single.empty();
        }
    }

    private static <T> T fetchRow(Cursor cursor, PlainDataModel collector,
                                  Map<String, Object> passedParameters)
            throws InstantiationException, IllegalAccessException {
        T obj;
        try {
            FieldInfo[] parameters = collector.parameters;
            String[] passedParameterNames = collector.passedParameterNames;
            if (parameters == null && passedParameterNames == null) {
                //noinspection unchecked
                obj = (T) collector.constructor.newInstance();
            } else {
                int length = parameters != null ? parameters.length : passedParameterNames.length;
                Object[] params = new Object[length];
                for (int i = 0; i < length; ++i) {
                    if (passedParameterNames != null) {
                        String passedParameterName = passedParameterNames[i];
                        if (passedParameterName != null) {
                            params[i] = passedParameters.get(passedParameterName);
                            continue;
                        }
                    }
                    if (parameters != null) {
                        FieldInfo fieldInfo = parameters[i];
                        if (fieldInfo != null) {
                            params[i] = OwlUtils.readValue(cursor,
                                    cursor.getColumnIndex(fieldInfo.column.value()),
                                    fieldInfo.type,
                                    fieldInfo.parcelCreator);
                            continue;
                        }
                    }
                    throw new IllegalStateException();
                }
                //noinspection unchecked
                obj = (T) collector.constructor.newInstance(params);
            }
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

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
            } else if (type == Boolean.TYPE || type == Boolean.class) {
                field.setBoolean(obj, cursor.getInt(columnIndex) != 0);
            } else if (type == Float.TYPE || type == Float.class) {
                field.setFloat(obj, cursor.getFloat(columnIndex));
            } else if (type == Double.TYPE || type == Double.class) {
                field.setDouble(obj, cursor.getDouble(columnIndex));
            } else if (type == Short.TYPE || type == Short.class) {
                field.setShort(obj, cursor.getShort(columnIndex));
            } else if (Parcelable.class.isAssignableFrom(type)) {
                Parcel parcel = Parcel.obtain();
                byte[] bytes = cursor.getBlob(columnIndex);
                parcel.unmarshall(bytes, 0, bytes.length);
                parcel.setDataPosition(0);
                field.set(obj, fieldInfo.parcelCreator.createFromParcel(parcel));
                parcel.recycle();
            } else {
                throw new IllegalArgumentException("Unsupported type: " + type.getCanonicalName());
            }
        }
        return obj;
    }

    private static PlainDataModel getModel(Class clazz) {
        synchronized (sCollectors) {
            PlainDataModel collector = sCollectors.get(clazz);
            if (collector == null) {
                collector = parseClass(clazz);
                sCollectors.put(clazz, collector);
            }
            return collector;
        }
    }

    private static PlainDataModel parseClass(Class clazz) {
        if (clazz.isInterface() || (clazz.getModifiers() & Modifier.ABSTRACT) == Modifier.ABSTRACT) {
            throw new IllegalArgumentException("Interface or abstract class is not allowed: "
                    + clazz.getCanonicalName());
        }
        PlainDataModel collector = new PlainDataModel();

        for (Constructor ctor : clazz.getDeclaredConstructors()) {
            Annotation[][] annotations = ctor.getParameterAnnotations();
            int length = annotations.length;
            boolean isEligible = length == 0;
            if (!isEligible) {
                for (Annotation[] parameterAnnotations : annotations) {
                    for (Annotation annotation : parameterAnnotations) {
                        if (annotation instanceof DecoderParam || annotation instanceof Column) {
                            isEligible = true;
                            break;
                        }
                    }
                }
            }
            if (isEligible) {
                if (collector.constructor != null) {
                    throw new IllegalArgumentException("Multiple constructor found for " + clazz.getCanonicalName());
                }
                collector.constructor = ctor;
                ctor.setAccessible(true);
                for (int i = 0; i < length; ++i) {
                    Annotation[] parameterAnnotations = annotations[i];
                    boolean unknown = true;
                    for (Annotation annotation : parameterAnnotations) {
                        if (annotation instanceof DecoderParam) {
                            unknown = false;
                            if (collector.passedParameterNames == null) {
                                collector.passedParameterNames = new String[length];
                            }
                            collector.passedParameterNames[i] = ((DecoderParam) annotation).value();
                        } else if (annotation instanceof Column) {
                            unknown = false;
                            if (collector.parameters == null) {
                                collector.parameters = new FieldInfo[length];
                            }
                            FieldInfo fieldInfo = new FieldInfo();
                            fieldInfo.column = (Column) annotation;
                            fieldInfo.setType(ctor.getParameterTypes()[i]);
                            collector.parameters[i] = fieldInfo;
                        }
                    }
                    if (unknown) {
                        throw new IllegalArgumentException("All parameter should be annotated with @DecoderParam or @Column");
                    }
                }
            }
        }
        if (collector.constructor == null) {
            throw new IllegalArgumentException("Couldn't find proper constructor for "
                    + clazz.getCanonicalName());
        }

        List<Pair<Field, FieldInfo>> fields = collector.fields;
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column == null) continue;

                FieldInfo fieldInfo = new FieldInfo();
                Class<?> fieldType = field.getType();
                fieldInfo.column = column;
                fieldInfo.setType(fieldType);
                field.setAccessible(true);
                fields.add(new Pair<>(field, fieldInfo));
            }
            clazz = clazz.getSuperclass();
        }
        return collector;
    }
}
