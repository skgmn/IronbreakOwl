package ironbreakowl;

import android.database.Cursor;
import android.os.Parcelable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;

class CursorReader {
    private static final HashMap<Class, CursorReader> sReaders = new HashMap<>();

    private static final int LOGIC_READ_VALUE = 0;
    private static final int LOGIC_INVESTIGATE_NULL = 1;
    private static final int LOGIC_INVESTIGATE_NOT_NULL = 2;

    private static class MethodInfo {
        public Column column;
        public Class returnType;
        public int logic;
        public Parcelable.Creator parcelCreator;
    }

    public final HashMap<Method, MethodInfo> methods = new HashMap<>();

    public static <T> T create(final Cursor cursor, Class<T> clazz) {
        final CursorReader cr = getReader(clazz);
        //noinspection unchecked
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                MethodInfo methodInfo = cr.methods.get(method);
                String columnName = methodInfo.column.value();
                Class returnType = methodInfo.returnType;
                int columnIndex = cursor.getColumnIndex(columnName);
                int logic = methodInfo.logic;
                switch (logic) {
                    case LOGIC_READ_VALUE:
                    default:
                        return OwlUtils.readValue(cursor, columnIndex, returnType, methodInfo.parcelCreator);
                    case LOGIC_INVESTIGATE_NULL:
                    case LOGIC_INVESTIGATE_NOT_NULL:
                        if (returnType != Boolean.TYPE && returnType != Boolean.class) {
                            throw new IllegalArgumentException("Only boolean type is allowed for @IsNull or " +
                                    "@IsNotNull");
                        }
                        boolean isNull = cursor.isNull(columnIndex);
                        return logic == LOGIC_INVESTIGATE_NULL ? isNull : !isNull;
                }
            }
        });
    }

    private static CursorReader getReader(Class clazz) {
        synchronized (sReaders) {
            CursorReader reader = sReaders.get(clazz);
            if (reader == null) {
                reader = parseClass(clazz);
                sReaders.put(clazz, reader);
            }
            return reader;
        }
    }

    private static CursorReader parseClass(Class clazz) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Only interface is allowed: " + clazz.getCanonicalName());
        }
        CursorReader proxy = new CursorReader();
        HashMap<Method, MethodInfo> methods = proxy.methods;
        for (Method method : clazz.getMethods()) {
            Column column = method.getAnnotation(Column.class);
            if (column == null) continue;

            MethodInfo methodInfo = new MethodInfo();
            Class<?> returnType = method.getReturnType();
            methodInfo.column = column;
            methodInfo.returnType = returnType;
            if (Parcelable.class.isAssignableFrom(returnType)) {
                methodInfo.parcelCreator = OwlUtils.getParcelCreator(returnType);
            }

            if (method.isAnnotationPresent(IsNull.class)) {
                methodInfo.logic = LOGIC_INVESTIGATE_NULL;
            } else if (method.isAnnotationPresent(IsNotNull.class)) {
                methodInfo.logic = LOGIC_INVESTIGATE_NOT_NULL;
            } else {
                methodInfo.logic = LOGIC_READ_VALUE;
            }

            methods.put(method, methodInfo);
        }
        return proxy;
    }
}
