package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.v4.util.ArrayMap;
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
import java.util.List;
import java.util.Map;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import rx.Observable;
import rx.subscriptions.Subscriptions;

class PlainDataModel {
    private static final Map<Class, PlainDataModel> models = new ArrayMap<>();

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
                T obj = model.fetchRow(cursor, passedParameters);
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
                                    T obj = model.fetchRow(cursor, passedParameters);
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

    static <T> Maybe<T> toMaybe(final Cursor cursor, Class<T> clazz,
                                Map<String, Object> passedParameters) {
        return Maybe.create(emitter -> {
            final PlainDataModel model = getModel(clazz);
            try {
                if (cursor.moveToNext()) {
                    T obj = model.fetchRow(cursor, passedParameters);
                    emitter.onSuccess(obj);
                } else {
                    emitter.onComplete();
                }
            } catch (Throwable e) {
                emitter.onError(e);
            } finally {
                cursor.close();
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
                                T obj = model.fetchRow(cursor, passedParameters);
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

    @SuppressWarnings("WeakerAccess")
    <T> T fetchRow(Cursor cursor, Map<String, Object> passedParameters)
            throws InstantiationException, IllegalAccessException {
        T obj;
        try {
            FieldInfo[] parameters = this.parameters;
            String[] passedParameterNames = this.passedParameterNames;
            if (parameters == null && passedParameterNames == null) {
                //noinspection unchecked
                obj = (T) constructor.newInstance();
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
                                    fieldInfo, null, null);
                            continue;
                        }
                    }
                    throw new IllegalStateException();
                }
                //noinspection unchecked
                obj = (T) constructor.newInstance(params);
            }
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        for (Pair<Field, FieldInfo> pair : fields) {
            Field field = pair.first;
            FieldInfo fieldInfo = pair.second;
            String columnName = fieldInfo.column.value();
            int columnIndex = cursor.getColumnIndex(columnName);
            OwlUtils.readValue(cursor, columnIndex, fieldInfo, obj, field);
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
