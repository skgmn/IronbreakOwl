package ironbreakowl;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;

public class Owl {
    private static final int RETURN_TYPE_LIST = 0;
    private static final int RETURN_TYPE_ITERABLE = 1;
    private static final int RETURN_TYPE_LONG = 2;
    private static final int RETURN_TYPE_OBJECT = 3;

    private static class QueryInfo {
        public int returnType;
        public Class modelClass;
    }

    private static class SelectInfo extends QueryInfo {
        public Query query;
    }

    public String tableName;
    public HashMap<Method, QueryInfo> queryInfos = new HashMap<>();

    private static HashMap<Class, Owl> sOwls;

    public static <T> T create(final SQLiteOpenHelper dbOpener, Class<T> clazz) {
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
                if (queryInfo instanceof SelectInfo) {
                    SelectInfo selectInfo = (SelectInfo) queryInfo;
                    Query query = selectInfo.query;
                    String where = query.where();
                    String[] selectionArgs;
                    if (where.length() == 0) {
                        where = null;
                        selectionArgs = null;
                    } else {
                        NonStringArgumentBinder binder = new NonStringArgumentBinder(where, args);
                        where = binder.where;
                        selectionArgs = binder.selectionArgs;
                    }
                    SQLiteDatabase db = dbOpener.getReadableDatabase();
                    Cursor cursor = db.query(o.tableName, query.value(), where, selectionArgs, null, null, null);
                    return CursorProxy.create(cursor, selectInfo.modelClass);
                }
                return null;
            }
        });
    }

    static boolean isNumber(Object o) {
        return o instanceof Byte ||
                o instanceof Short ||
                o instanceof Integer ||
                o instanceof Long ||
                o instanceof Float ||
                o instanceof Double;
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
            Query query = method.getAnnotation(Query.class);
            if (query != null) {
                SelectInfo selectInfo = new SelectInfo();
                selectInfo.query = query;

                Type returnType = method.getGenericReturnType();
                boolean returnTypeValid = true;
                if (returnType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) returnType;
                    Type rawType = pt.getRawType();
                    if (rawType == Iterable.class) {
                        selectInfo.returnType = RETURN_TYPE_ITERABLE;
                        selectInfo.modelClass = (Class) pt.getActualTypeArguments()[0];
                    } else {
                        returnTypeValid = false;
                    }
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("Only Iterable<T> is supported.");
                }

                owl.queryInfos.put(method, selectInfo);
                continue;
            }
        }
        return owl;
    }
}
