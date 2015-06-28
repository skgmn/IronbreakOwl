package ironbreakowl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
    }
    
    public String tableName;
    public HashMap<Method, QueryInfo> queryInfos = new HashMap<>();

    private static HashMap<Class, Owl> sOwls;

    public static <T> T create(final SQLiteDatabase db, Class<T> clazz) {
        Owl owl = sOwls == null ? null : sOwls.get(clazz);
        if (owl == null) {
            owl = createOwl(clazz);
        }
        final Owl o = owl;
        //noinspection unchecked
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                QueryInfo queryInfo = o.queryInfos.get(method);
                if (queryInfo == null) {
                    throw new UnsupportedOperationException();
                }

                Annotation annotation = queryInfo.query;
                String where = getSelection(annotation);
                String[] selectionArgs;
                if (where == null || where.length() == 0) {
                    where = null;
                    selectionArgs = null;
                } else {
                    NonStringArgumentBinder binder = new NonStringArgumentBinder(where, args);
                    where = binder.where;
                    selectionArgs = binder.selectionArgs;
                }

                if (annotation instanceof Query) {
                    Query query = (Query) annotation;
                    String[] columns = query.columns();
                    if (columns.length == 0) {
                        columns = null;
                    }
                    final Cursor cursor = db.query(o.tableName, columns, where, selectionArgs, null, null, null);
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
                    int affected = db.delete(o.tableName, where, selectionArgs);
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
                    String[] columns = insert.columns();
                    int conflictAlgorithm = insert.onConflict();
                    ContentValues values = makeValues(columns, args);
                    long retVal;
                    if (conflictAlgorithm == -1) {
                        retVal = db.insert(o.tableName, null, values);
                    } else {
                        retVal = db.insertWithOnConflict(o.tableName, null, values, conflictAlgorithm);
                    }
                    switch (queryInfo.returnType) {
                        case RETURN_TYPE_VOID:
                            return null;
                        case RETURN_TYPE_LONG:
                            return retVal;
                        case RETURN_TYPE_BOOLEAN:
                            return retVal != -1;
                    }
                }
                return null;
            }
        });
    }

    static ContentValues makeValues(String[] columns, Object[] args) {
        int length = args.length;
        if (columns.length != length) {
            throw new IllegalArgumentException("Mismatch: column count = " + columns.length + ", argument count = " +
                    length);
        }
        ContentValues values = new ContentValues();
        for (int i = 0; i < length; i++) {
            String column = columns[i];
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
        return values;
    }

    @Nullable
    static String getSelection(Annotation annotation) {
        if (annotation instanceof Query) {
            return ((Query) annotation).where();
        } else if (annotation instanceof Delete) {
            return ((Delete) annotation).where();
        } else {
            return null;
        }
    }

    static boolean isNumber(Object o) {
        return o instanceof Byte ||
                o instanceof Short ||
                o instanceof Integer ||
                o instanceof Long ||
                o instanceof Float ||
                o instanceof Double;
    }

    public static String getTableName(Class clazz) {
        return getOwl(clazz).tableName;
    }

    @NonNull
    private static Owl getOwl(Class clazz) {
        Owl owl = sOwls == null ? null : sOwls.get(clazz);
        return owl == null ? createOwl(clazz) : owl;
    }

    private static Owl createOwl(Class clazz) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Only interface is allowed: " + clazz.getCanonicalName());
        }

        Owl owl = new Owl();

        Table table = (Table) clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException("@Table missing");
        }
        owl.tableName = table.value();

        for (Method method : clazz.getMethods()) {
            QueryInfo queryInfo = new QueryInfo();
            boolean returnTypeValid = true;

            Query query = method.getAnnotation(Query.class);
            if (query != null) {
                queryInfo.query = query;

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
        }

        if (sOwls == null) {
            sOwls = new HashMap<>();
        }
        sOwls.put(clazz, owl);

        return owl;
    }
}
