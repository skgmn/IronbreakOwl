package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Flowable;
import rx.Observable;
import rx.subscriptions.Subscriptions;

class PlainDataModel {
    private static final HashMap<Class, PlainDataModel> models = new HashMap<>();

    private static class FieldInfo {
        Column column;
        Class type;
        Parcelable.Creator parcelCreator;

        void setType(Class type) {
            this.type = type;
            parcelCreator = Parcelable.class.isAssignableFrom(type) ? OwlUtils.getParcelCreator(type) : null;
        }
    }

    private final List<Pair<Field, FieldInfo>> fields = new ArrayList<>();
    private Constructor constructor;
    private String[] passedParameterNames;
    private FieldInfo[] parameters;

    static void putInto(ContentValues values, Object o) {
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

    static <T> ArrayList<T> toList(final Cursor cursor, Class<T> clazz, Map<String, Object> passedParameters) {
        final PlainDataModel model = getModel(clazz);
        ArrayList<T> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            try {
                T obj = fetchRow(cursor, model, passedParameters);
                list.add(obj);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        cursor.close();
        return list;
    }

    static <T> Flowable<T> toFlowable(final Cursor cursor, Class<T> clazz, Map<String, Object> passedParameters) {
        return Flowable.unsafeCreate(new Publisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> s) {
                final PlainDataModel model = getModel(clazz);
                s.onSubscribe(new Subscription() {
                    private volatile boolean reading;
                    private volatile boolean canceled;

                    @Override
                    public void request(long n) {
                        reading = true;
                        try {
                            for (int i = 0; i < n && !canceled; ++i) {
                                final boolean complete;
                                if (cursor.moveToNext()) {
                                    T obj = fetchRow(cursor, model, passedParameters);
                                    s.onNext(obj);
                                    complete = cursor.isLast();
                                } else {
                                    complete = true;
                                }
                                if (complete) {
                                    cursor.close();
                                    s.onComplete();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            s.onError(e);
                        } finally {
                            if (canceled && !cursor.isClosed()) {
                                cursor.close();
                            }
                            reading = false;
                        }
                    }

                    @Override
                    public void cancel() {
                        canceled = true;
                        if (!reading && !cursor.isClosed()) {
                            cursor.close();
                        }
                    }
                });
            }
        });
    }

    static <T> Observable<T> toOldObservable(final Cursor cursor, Class<T> clazz,
                                             Map<String, Object> passedParameters) {
        return Observable.create(new Observable.OnSubscribe<T>() {
            private volatile boolean reading;

            @Override
            public void call(rx.Subscriber<? super T> s) {
                final PlainDataModel model = getModel(clazz);
                s.setProducer(n -> {
                    reading = true;
                    try {
                        for (int i = 0; i < n && !s.isUnsubscribed(); ++i) {
                            final boolean complete;
                            if (cursor.moveToNext()) {
                                T obj = fetchRow(cursor, model, passedParameters);
                                s.onNext(obj);
                                complete = cursor.isLast();
                            } else {
                                complete = true;
                            }
                            if (complete) {
                                cursor.close();
                                s.onCompleted();
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        s.onError(e);
                    } finally {
                        if (s.isUnsubscribed() && !cursor.isClosed()) {
                            cursor.close();
                        }
                        reading = false;
                    }
                });
                s.add(Subscriptions.create(() -> {
                    if (!reading && !cursor.isClosed()) {
                        cursor.close();
                    }
                }));
            }
        });
    }

    static <T> Single<T> readSingle(final Cursor cursor, Class<T> clazz, Map<String, Object> passedParameters) {
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

    private static <T> T fetchRow(Cursor cursor, PlainDataModel model,
                                  Map<String, Object> passedParameters)
            throws InstantiationException, IllegalAccessException {
        T obj;
        try {
            FieldInfo[] parameters = model.parameters;
            String[] passedParameterNames = model.passedParameterNames;
            if (parameters == null && passedParameterNames == null) {
                //noinspection unchecked
                obj = (T) model.constructor.newInstance();
            } else {
                int length = parameters != null ? parameters.length : passedParameterNames.length;
                Object[] params = new Object[length];
                for (int i = 0; i < length; ++i) {
                    if (passedParameterNames != null && passedParameters != null) {
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
                obj = (T) model.constructor.newInstance(params);
            }
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        for (Pair<Field, FieldInfo> pair : model.fields) {
            Field field = pair.first;
            FieldInfo fieldInfo = pair.second;
            String columnName = fieldInfo.column.value();
            int columnIndex = cursor.getColumnIndex(columnName);
            Class type = fieldInfo.type;
            if (type == Integer.TYPE) {
                field.setInt(obj, cursor.getInt(columnIndex));
            } else if (type == Integer.class) {
                field.set(obj, cursor.isNull(columnIndex) ? null : cursor.getInt(columnIndex));
            } else if (type == String.class) {
                field.set(obj, cursor.getString(columnIndex));
            } else if (type == Long.TYPE) {
                field.setLong(obj, cursor.getLong(columnIndex));
            } else if (type == Long.class) {
                field.set(obj, cursor.isNull(columnIndex) ? null : cursor.getLong(columnIndex));
            } else if (type == byte[].class) {
                field.set(obj, cursor.getBlob(columnIndex));
            } else if (type == Boolean.TYPE) {
                field.setBoolean(obj, cursor.getInt(columnIndex) != 0);
            } else if (type == Boolean.class) {
                field.set(obj, cursor.isNull(columnIndex) ? null : cursor.getInt(columnIndex) != 0);
            } else if (type == Float.TYPE) {
                field.setFloat(obj, cursor.getFloat(columnIndex));
            } else if (type == Float.class) {
                field.set(obj, cursor.isNull(columnIndex) ? null : cursor.getFloat(columnIndex));
            } else if (type == Double.TYPE) {
                field.setDouble(obj, cursor.getDouble(columnIndex));
            } else if (type == Double.class) {
                field.set(obj, cursor.isNull(columnIndex) ? null : cursor.getDouble(columnIndex));
            } else if (type == Short.TYPE) {
                field.setShort(obj, cursor.getShort(columnIndex));
            } else if (type == Short.class) {
                field.set(obj, cursor.isNull(columnIndex) ? null : cursor.getShort(columnIndex));
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
        synchronized (models) {
            PlainDataModel collector = models.get(clazz);
            if (collector == null) {
                collector = parseClass(clazz);
                models.put(clazz, collector);
            }
            return collector;
        }
    }

    private static PlainDataModel parseClass(Class clazz) {
        if (clazz.isInterface() || (clazz.getModifiers() & Modifier.ABSTRACT) == Modifier.ABSTRACT) {
            throw new IllegalArgumentException("Interface or abstract class is not allowed: "
                    + clazz.getCanonicalName());
        }
        PlainDataModel model = new PlainDataModel();

        for (Constructor ctor : clazz.getDeclaredConstructors()) {
            Annotation[][] annotations = ctor.getParameterAnnotations();
            int length = annotations.length;
            boolean isEligible = length == 0;
            if (!isEligible) {
                for (Annotation[] parameterAnnotations : annotations) {
                    for (Annotation annotation : parameterAnnotations) {
                        if (annotation instanceof Parameter || annotation instanceof Column) {
                            isEligible = true;
                            break;
                        }
                    }
                    if (isEligible) {
                        break;
                    }
                }
            }
            if (isEligible) {
                if (model.constructor != null) {
                    throw new IllegalArgumentException("Multiple constructor found for " + clazz.getCanonicalName());
                }
                model.constructor = ctor;
                ctor.setAccessible(true);
                for (int i = 0; i < length; ++i) {
                    Annotation[] parameterAnnotations = annotations[i];
                    boolean unknown = parameterAnnotations.length == 0;
                    for (Annotation annotation : parameterAnnotations) {
                        if (annotation instanceof Parameter) {
                            if (model.passedParameterNames == null) {
                                model.passedParameterNames = new String[length];
                            }
                            model.passedParameterNames[i] = ((Parameter) annotation).value();
                        } else if (annotation instanceof Column) {
                            if (model.parameters == null) {
                                model.parameters = new FieldInfo[length];
                            }
                            FieldInfo fieldInfo = new FieldInfo();
                            fieldInfo.column = (Column) annotation;
                            fieldInfo.setType(ctor.getParameterTypes()[i]);
                            model.parameters[i] = fieldInfo;
                        } else {
                            unknown = true;
                            break;
                        }
                    }
                    if (unknown) {
                        throw new IllegalArgumentException("All parameter should be annotated with @Parameter or " +
                                "@Column");
                    }
                }
            }
        }
        if (model.constructor == null) {
            throw new IllegalArgumentException("Couldn't find proper constructor for "
                    + clazz.getCanonicalName());
        }

        List<Pair<Field, FieldInfo>> fields = model.fields;
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
        return model;
    }
}
