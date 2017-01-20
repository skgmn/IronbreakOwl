package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.v4.util.ArrayMap;
import android.util.Pair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import rx.Observable;

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

    static <T> ArrayList<T> newList(final Cursor cursor, Class<T> clazz,
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

    static <T> Iterable<T> newIterable(Cursor cursor, Class<T> clazz,
                                       ModelDeserializationArguments args) {
        return new CursorIterable<T>(cursor) {
            private PlainDataModel model;

            @Override
            protected T readValue(Cursor cursor) throws Exception {
                if (model == null) {
                    model = getModel(clazz);
                }
                return model.fetchRow(cursor, args);
            }
        };
    }

    static <T> Flowable<T> newFlowable(final Cursor cursor, Class<T> clazz,
                                       ModelDeserializationArguments args) {
        return Flowable.unsafeCreate(new CursorPublisher<T>(cursor) {
            private PlainDataModel model;

            @Override
            protected T readValue(Cursor cursor) throws Exception {
                if (model == null) {
                    model = getModel(clazz);
                }
                return model.fetchRow(cursor, args);
            }
        });
    }

    static <T> Maybe<T> toMaybe(Cursor cursor, Class<T> clazz,
                                ModelDeserializationArguments args) {
        return Maybe.create(new CursorMaybeOnSubscribe<T>(cursor) {
            private PlainDataModel model;

            @Override
            protected T readValue(Cursor cursor) throws Exception {
                if (model == null) {
                    model = getModel(clazz);
                }
                return model.fetchRow(cursor, args);
            }
        });
    }

    static <T> Observable<T> newOldObservable(Cursor cursor, Class<T> clazz,
                                              ModelDeserializationArguments args) {
        return Observable.create(new CursorOldObservableOnSubscribe<T>(cursor) {
            private PlainDataModel model;

            @Override
            T readValue(Cursor cursor) throws Exception {
                if (model == null) {
                    model = getModel(clazz);
                }
                return model.fetchRow(cursor, args);
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
            Map<String, Object> parameters = args.ctorParams;
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
                                fieldInfo.type, null, null);
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
                OwlUtils.readValue(cursor, columnIndex, fieldInfo.type, obj, field);
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
        if (!OwlUtils.isModel(clazz)) {
            throw new IllegalArgumentException("Model class should be annotated with @Model");
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
                            fieldInfo.type = ctor.getParameterTypes()[i];
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
                fieldInfo.type = (Class) fieldType;
                fieldInfo.conditional = field.getAnnotation(Conditional.class) != null;
                field.setAccessible(true);
                fields.add(new Pair<>(field, fieldInfo));
            }
            clazz = clazz.getSuperclass();
        }
        return model;
    }
}
