package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;

public class Owl {
    private static final int RETURN_TYPE_BOOLEAN = 0;
    private static final int RETURN_TYPE_ITERABLE = 1;
    private static final int RETURN_TYPE_INT = 2;
    private static final int RETURN_TYPE_VOID = 3;
    private static final int RETURN_TYPE_LONG = 4;

    private static class QueryInfo {
        public int returnType;
        public Class modelClass;
        public Annotation query;
        public String where;
        public ConstantValues constValues;
        public boolean[] argWhereTarget;
        public String[] argColumnNames;
    }

    private static final HashMap<Class, Owl> sOwls = new HashMap<>();

    public final String tableName;
    public final HashMap<Method, QueryInfo> queryInfos = new HashMap<>();

    public Owl(String tableName) {
        this.tableName = tableName;
    }

    public static <T> T create(final SQLiteDatabase db, Class<T> clazz) {
        final Owl owl = getOwl(clazz);
        //noinspection unchecked
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                QueryInfo queryInfo = owl.queryInfos.get(method);
                if (queryInfo == null) {
                    throw new UnsupportedOperationException();
                }

                String selection = queryInfo.where;
                String[] selectionArgs;
                if (selection == null || selection.length() == 0) {
                    selection = null;
                    selectionArgs = null;
                } else {
                    NonStringArgumentBinder binder = new NonStringArgumentBinder(selection, args, queryInfo.argWhereTarget);
                    selection = binder.selection;
                    selectionArgs = binder.selectionArgs;
                }

                Annotation annotation = queryInfo.query;
                if (annotation instanceof Query) {
                    Query query = (Query) annotation;
                    String[] columns = query.select();
                    if (columns.length == 0) {
                        columns = null;
                    }
                    final Cursor cursor = db.query(owl.tableName, columns, selection, selectionArgs, null, null, null);
                    switch (queryInfo.returnType) {
                        case RETURN_TYPE_BOOLEAN:
                            boolean retVal = cursor.moveToNext();
                            cursor.close();
                            return retVal;
                        case RETURN_TYPE_ITERABLE:
                            final Object cursorReader = CursorReader.create(cursor, queryInfo.modelClass);
                            return new Iterable() {
                                @Override
                                public Iterator iterator() {
                                    return new CursorIterator(cursor, cursorReader);
                                }
                            };
                    }
                } else if (annotation instanceof Delete) {
                    int affected = db.delete(owl.tableName, selection, selectionArgs);
                    switch (queryInfo.returnType) {
                        case RETURN_TYPE_VOID:
                            return null;
                        case RETURN_TYPE_INT:
                            return affected;
                        case RETURN_TYPE_BOOLEAN:
                            return affected != 0;
                    }
                } else if (annotation instanceof Insert) {
                    Insert insert = (Insert) annotation;
                    int conflictAlgorithm = insert.onConflict();
                    ContentValues values = makeValues(queryInfo.argColumnNames, args, queryInfo.constValues);
                    long retVal = db.insertWithOnConflict(owl.tableName, null, values, conflictAlgorithm);
                    switch (queryInfo.returnType) {
                        case RETURN_TYPE_VOID:
                            return null;
                        case RETURN_TYPE_LONG:
                            return retVal;
                        case RETURN_TYPE_BOOLEAN:
                            return retVal != -1;
                    }
                } else if (annotation instanceof Update) {
                    ContentValues values = makeValues(queryInfo.argColumnNames, args, queryInfo.constValues);
                    int retVal = db.update(owl.tableName, values, selection, selectionArgs);
                    switch (queryInfo.returnType) {
                        case RETURN_TYPE_VOID:
                            return null;
                        case RETURN_TYPE_INT:
                            return retVal;
                        case RETURN_TYPE_BOOLEAN:
                            return retVal != 0;
                    }
                }
                return null;
            }
        });
    }

    static ContentValues makeValues(String[] columns, Object[] args, ConstantValues constValues) {
        int length = args.length;
        if (columns.length != length) {
            throw new IllegalArgumentException("Mismatch: column count = " + columns.length + ", argument count = " +
                    length);
        }
        ContentValues values = new ContentValues();
        for (int i = 0; i < length; i++) {
            String column = columns[i];
            if (column == null) continue;

            Object arg = args[i];
            if (arg == null) {
                values.putNull(column);
            } else if (arg instanceof Boolean) {
                values.put(column, (Boolean) arg);
            } else if (arg instanceof Byte) {
                values.put(column, (Byte) arg);
            } else if (arg instanceof byte[]) {
                values.put(column, (byte[]) arg);
            } else if (arg instanceof Double) {
                values.put(column, (Double) arg);
            } else if (arg instanceof Float) {
                values.put(column, (Float) arg);
            } else if (arg instanceof Integer) {
                values.put(column, (Integer) arg);
            } else if (arg instanceof Long) {
                values.put(column, (Long) arg);
            } else if (arg instanceof Short) {
                values.put(column, (Short) arg);
            } else if (arg instanceof String) {
                values.put(column, (String) arg);
            }
        }
        if (constValues != null) {
            String[] intKeys = constValues.intKeys();
            int[] intValues = constValues.intValues();
            int length2 = intKeys.length;
            if (length2 != intValues.length) {
                throw new IllegalArgumentException("intKeys and intValues should have same size");
            }
            for (int i = 0; i < length2; i++) {
                values.put(intKeys[i], intValues[i]);
            }
        }
        return values;
    }

    public static String getTableName(Class clazz) {
        return getOwl(clazz).tableName;
    }

    @NonNull
    private static Owl getOwl(Class clazz) {
        synchronized (sOwls) {
            Owl owl = sOwls.get(clazz);
            if (owl == null) {
                owl = parseClass(clazz);
                sOwls.put(clazz, owl);
            }
            return owl;
        }
    }

    private static Owl parseClass(Class clazz) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Only interface is allowed: " + clazz.getCanonicalName());
        }

        Table table = (Table) clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException("@Table missing");
        }
        String tableName = table.value();

        Owl owl = new Owl(tableName);
        for (Method method : clazz.getMethods()) {
            QueryInfo queryInfo = new QueryInfo();
            queryInfo.constValues = method.getAnnotation(ConstantValues.class);

            Query query = method.getAnnotation(Query.class);
            boolean returnTypeValid = true;
            if (query != null) {
                queryInfo.query = query;
                queryInfo.where = query.where();
                parseParameters(method, queryInfo, true, false);

                Type returnType = method.getGenericReturnType();
                if (returnType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) returnType;
                    Type rawType = pt.getRawType();
                    if (rawType == Iterable.class) {
                        queryInfo.returnType = RETURN_TYPE_ITERABLE;
                        queryInfo.modelClass = (Class) pt.getActualTypeArguments()[0];
                    } else {
                        returnTypeValid = false;
                    }
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    queryInfo.returnType = RETURN_TYPE_BOOLEAN;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("Only Iterable<T> is supported.");
                }

                owl.queryInfos.put(method, queryInfo);
                continue;
            }

            Delete delete = method.getAnnotation(Delete.class);
            if (delete != null) {
                queryInfo.query = delete;
                queryInfo.where = delete.where();
                parseParameters(method, queryInfo, true, false);

                Class returnType = method.getReturnType();
                if (returnType == Void.TYPE || returnType == Void.class) {
                    queryInfo.returnType = RETURN_TYPE_VOID;
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    queryInfo.returnType = RETURN_TYPE_BOOLEAN;
                } else if (returnType == Integer.TYPE || returnType == Integer.class) {
                    queryInfo.returnType = RETURN_TYPE_INT;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("void, boolean or int is supported for @Delete");
                }

                owl.queryInfos.put(method, queryInfo);
                continue;
            }

            Insert insert = method.getAnnotation(Insert.class);
            if (insert != null) {
                queryInfo.query = insert;
                parseParameters(method, queryInfo, false, true);

                Class returnType = method.getReturnType();
                if (returnType == Void.TYPE || returnType == Void.class) {
                    queryInfo.returnType = RETURN_TYPE_VOID;
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    queryInfo.returnType = RETURN_TYPE_BOOLEAN;
                } else if (returnType == Long.TYPE || returnType == Long.class) {
                    queryInfo.returnType = RETURN_TYPE_LONG;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("void, boolean or long is supported for @Insert");
                }

                owl.queryInfos.put(method, queryInfo);
                continue;
            }

            Update update = method.getAnnotation(Update.class);
            if (update != null) {
                queryInfo.query = update;
                queryInfo.where = update.where();
                parseParameters(method, queryInfo, true, true);

                Class returnType = method.getReturnType();
                if (returnType == Void.TYPE || returnType == Void.class) {
                    queryInfo.returnType = RETURN_TYPE_VOID;
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    queryInfo.returnType = RETURN_TYPE_BOOLEAN;
                } else if (returnType == Long.TYPE || returnType == Integer.class) {
                    queryInfo.returnType = RETURN_TYPE_INT;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("void, boolean or int is supported for @Update");
                }

                owl.queryInfos.put(method, queryInfo);
//                continue;
            }
        }

        sOwls.put(clazz, owl);
        return owl;
    }

    private static void parseParameters(Method method, QueryInfo queryInfo, boolean where, boolean value) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        int length = parameterAnnotations.length;
        boolean[] whereTarget = where ? new boolean[length] : null;
        String[] columnNames = value ? new String[length] : null;
        for (int i = 0; i < length; i++) {
            Annotation[] annotations = parameterAnnotations[i];
            for (Annotation annotation : annotations) {
                if (where && (annotation instanceof Where)) {
                    whereTarget[i] = true;
                }
                if (value && (annotation instanceof Value)) {
                    columnNames[i] = ((Value) annotation).value();
                }
            }
        }
        queryInfo.argWhereTarget = whereTarget;
        queryInfo.argColumnNames = columnNames;
    }
}
