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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import rx.Observable;
import rx.subscriptions.Subscriptions;

class PlainDataModel {
    private static final Map<Class, PlainDataModel> models = new ArrayMap<>();

    private final List<Pair<Field, FieldInfo>> fields = new ArrayList<>();
    private Constructor ctor;
    private String[] ctorParamNames;
    private FieldInfo[] ctorColumns;
    private Map<String, Method> conditionMethods;

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

    static <T> ArrayList<T> toList(final Cursor cursor, Class<T> clazz,
                                   ModelDeserializationArguments args) {
        final PlainDataModel model = getModel(clazz);
        ArrayList<T> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            try {
                T obj = model.fetchRow(cursor, args);
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

    static <T> Iterable<T> toIterable(final Cursor cursor, Class<T> clazz,
                                      ModelDeserializationArguments args) {
        final PlainDataModel model = getModel(clazz);
        return new Iterable<T>() {
            private boolean iteratorCreated;

            @Override
            public Iterator<T> iterator() {
                if (iteratorCreated) {
                    throw new IllegalStateException("This Iterable only allows one Iterator instance");
                }
                iteratorCreated = true;
                return new Iterator<T>() {
                    private boolean hasNext;
                    private boolean hasNextCalled;

                    @Override
                    public boolean hasNext() {
                        if (!hasNextCalled) {
                            hasNextCalled = true;
                            hasNext = cursor.moveToNext();
                            if (!hasNext) {
                                cursor.close();
                            }
                        }
                        return hasNext;
                    }

                    @Override
                    public T next() {
                        try {
                            T obj = model.fetchRow(cursor, args);
                            if (cursor.isLast()) {
                                hasNextCalled = true;
                                hasNext = false;
                                cursor.close();
                            } else {
                                hasNextCalled = false;
                            }
                            return obj;
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            }
        };
    }

    static <T> Flowable<T> toFlowable(final Cursor cursor, Class<T> clazz,
                                      ModelDeserializationArguments args) {
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
                                    T obj = model.fetchRow(cursor, args);
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
                                ModelDeserializationArguments args) {
        return Maybe.create(emitter -> {
            final PlainDataModel model = getModel(clazz);
            try {
                if (cursor.moveToNext()) {
                    T obj = model.fetchRow(cursor, args);
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
                                             ModelDeserializationArguments args) {
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
                                T obj = model.fetchRow(cursor, args);
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
    <T> T fetchRow(Cursor cursor, ModelDeserializationArguments args)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        T obj;
        FieldInfo[] columns = this.ctorColumns;
        String[] parameterNames = this.ctorParamNames;
        if (columns == null && parameterNames == null) {
            //noinspection unchecked
            obj = (T) ctor.newInstance();
        } else {
            int length = columns != null ? columns.length : parameterNames.length;
            Map<String, Object> parameters = args.parameters;
            Object[] params = new Object[length];
            for (int i = 0; i < length; ++i) {
                if (parameterNames != null && parameters != null) {
                    String parameterName = parameterNames[i];
                    if (parameterName != null) {
                        params[i] = parameters.get(parameterName);
                        continue;
                    }
                }
                if (columns != null) {
                    FieldInfo fieldInfo = columns[i];
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
            obj = (T) ctor.newInstance(params);
        }

        for (int i = 0; i < 2; ++i) {
            boolean cond = i == 1;
            for (Pair<Field, FieldInfo> pair : fields) {
                FieldInfo fieldInfo = pair.second;
                if (fieldInfo.conditional != cond) {
                    continue;
                }
                String columnName = fieldInfo.column.value();
                if (cond) {
                    boolean pass;
                    Predicate predicate = args.getPredicate(columnName);
                    if (predicate != null) {
                        //noinspection unchecked
                        pass = !predicate.test(obj);
                    } else {
                        pass = false;
                        if (conditionMethods != null) {
                            Method method = conditionMethods.get(columnName);
                            if (method != null) {
                                try {
                                    pass = !(boolean) method.invoke(obj);
                                } catch (InvocationTargetException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                    if (pass) {
                        continue;
                    }
                }
                Field field = pair.first;
                int columnIndex = cursor.getColumnIndex(columnName);
                OwlUtils.readValue(cursor, columnIndex, fieldInfo, obj, field);
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
                if (model.ctor != null) {
                    throw new IllegalArgumentException("Multiple constructor found for " + clazz.getCanonicalName());
                }
                model.ctor = ctor;
                ctor.setAccessible(true);
                for (int i = 0; i < length; ++i) {
                    Annotation[] parameterAnnotations = annotations[i];
                    boolean unknown = parameterAnnotations.length == 0;
                    for (Annotation annotation : parameterAnnotations) {
                        if (annotation instanceof Parameter) {
                            if (model.ctorParamNames == null) {
                                model.ctorParamNames = new String[length];
                            }
                            model.ctorParamNames[i] = ((Parameter) annotation).value();
                        } else if (annotation instanceof Column) {
                            if (model.ctorColumns == null) {
                                model.ctorColumns = new FieldInfo[length];
                            }
                            FieldInfo fieldInfo = new FieldInfo();
                            fieldInfo.column = (Column) annotation;
                            fieldInfo.setType(ctor.getParameterTypes()[i]);
                            model.ctorColumns[i] = fieldInfo;
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
        if (model.ctor == null) {
            throw new IllegalArgumentException("Couldn't find proper constructor for "
                    + clazz.getCanonicalName());
        }

        for (Method method : clazz.getDeclaredMethods()) {
            Condition cond = method.getAnnotation(Condition.class);
            if (cond != null) {
                if (model.conditionMethods == null) {
                    model.conditionMethods = new ArrayMap<>();
                }
                String conditionName = cond.value();
                method.setAccessible(true);
                if (model.conditionMethods.put(conditionName, method) != null) {
                    throw new IllegalArgumentException("Multiple condition methods for "
                            + conditionName);
                }
            }
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
                fieldInfo.conditional = field.getAnnotation(Conditional.class) != null;
                field.setAccessible(true);
                fields.add(new Pair<>(field, fieldInfo));
            }
            clazz = clazz.getSuperclass();
        }
        return model;
    }
}
