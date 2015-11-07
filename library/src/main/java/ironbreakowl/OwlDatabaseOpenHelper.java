package ironbreakowl;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

public abstract class OwlDatabaseOpenHelper extends SQLiteOpenHelper {
    private static final int RETURN_TYPE_BOOLEAN = 0;
    private static final int RETURN_TYPE_ITERABLE = 1;
    private static final int RETURN_TYPE_INT = 2;
    private static final int RETURN_TYPE_VOID = 3;
    private static final int RETURN_TYPE_LONG = 4;
    private static final int RETURN_TYPE_LIST = 5;

    static abstract class QueryInfo {
        public int returnType;
        public Class modelClass;

        public abstract Object query(OwlTable table, Object[] args);
    }

    static abstract class SelectableQueryInfo extends QueryInfo {
        public String selection;
        public boolean[] isSelectionArgument;

        @NonNull
        protected NonStringArgumentBinder bind(Object[] args) {
            if (!TextUtils.isEmpty(selection)) {
                return new NonStringArgumentBinder(selection, args, isSelectionArgument);
            } else {
                return new NonStringArgumentBinder();
            }
        }
    }

    static class ValueSetter {
        public String[] argumentColumnNames;
        public List<Map.Entry<String, Object>> constantValues;
    }

    interface ValueSettableQueryInfo {
        ValueSetter valueSetter();
    }

    class SelectInfo extends SelectableQueryInfo {
        public String[] projection;
        public String orderBy;

        @Override
        public Object query(OwlTable owl, Object[] args) {
            NonStringArgumentBinder argBinder = bind(args);
            mLock.lock();
            try {
                SQLiteDatabase db = getReadableDatabase();
                final Cursor cursor = db.query(owl.mTableName, projection, argBinder.selection, argBinder.selectionArgs,
                        null, null, orderBy);
                switch (returnType) {
                    case RETURN_TYPE_BOOLEAN:
                        boolean retVal = cursor.moveToNext();
                        cursor.close();
                        return retVal;
                    case RETURN_TYPE_ITERABLE:
                        final Object cursorReader = CursorReader.create(cursor, modelClass);
                        final CursorIterator cursorIterator = new CursorIterator(cursor, cursorReader,
                                OwlDatabaseOpenHelper.this);
                        return new Iterable() {
                            @Override
                            public ClosableIterator iterator() {
                                return cursorIterator;
                            }
                        };
                    case RETURN_TYPE_LIST:
                        return PlainDataModel.collect(cursor, modelClass);
                }
            } finally {
                mLock.unlock();
            }
            return null;
        }
    }

    class DeleteInfo extends SelectableQueryInfo {
        @Override
        public Object query(OwlTable owl, Object[] args) {
            NonStringArgumentBinder argBinder = bind(args);
            mLock.lock();
            try {
                SQLiteDatabase db = getWritableDatabase();
                int affected = db.delete(owl.mTableName, argBinder.selection, argBinder.selectionArgs);
                switch (returnType) {
                    case RETURN_TYPE_VOID:
                        return null;
                    case RETURN_TYPE_INT:
                        return affected;
                    case RETURN_TYPE_BOOLEAN:
                        return affected != 0;
                }
            } finally {
                mLock.unlock();
            }
            return null;
        }
    }

    class InsertInfo extends QueryInfo implements ValueSettableQueryInfo {
        public ValueSetter valueSetter = new ValueSetter();
        public int conflictAlgorithm;

        @Override
        public ValueSetter valueSetter() {
            return valueSetter;
        }

        @Override
        public Object query(OwlTable owl, Object[] args) {
            mLock.lock();
            try {
                SQLiteDatabase db = getWritableDatabase();
                ContentValues values = makeValues(valueSetter.argumentColumnNames, args, valueSetter.constantValues);
                long retVal = db.insertWithOnConflict(owl.mTableName, null, values, conflictAlgorithm);
                switch (returnType) {
                    case RETURN_TYPE_VOID:
                        return null;
                    case RETURN_TYPE_LONG:
                        return retVal;
                    case RETURN_TYPE_BOOLEAN:
                        return retVal != -1;
                }
            } finally {
                mLock.unlock();
            }
            return null;
        }
    }

    class UpdateInfo extends SelectableQueryInfo implements ValueSettableQueryInfo {
        public ValueSetter valueSetter = new ValueSetter();

        @Override
        public ValueSetter valueSetter() {
            return valueSetter;
        }

        @Override
        public Object query(OwlTable owl, Object[] args) {
            NonStringArgumentBinder argBinder = bind(args);
            mLock.lock();
            try {
                SQLiteDatabase db = getWritableDatabase();
                ContentValues values = makeValues(valueSetter.argumentColumnNames, args, valueSetter.constantValues);
                int retVal = db.update(owl.mTableName, values, argBinder.selection, argBinder.selectionArgs);
                switch (returnType) {
                    case RETURN_TYPE_VOID:
                        return null;
                    case RETURN_TYPE_INT:
                        return retVal;
                    case RETURN_TYPE_BOOLEAN:
                        return retVal != 0;
                }
            } finally {
                mLock.unlock();
            }
            return null;
        }
    }

    static class OwlTable {
        private final String mTableName;
        private final HashMap<Method, QueryInfo> mQueryInfos = new HashMap<>();

        public Object tableInterface;

        public OwlTable(String tableName) {
            this.mTableName = tableName;
        }
    }

    private final HashMap<Class, OwlTable> mTables = new HashMap<>();
    final ReentrantLock mLock = new ReentrantLock();
    private final ThreadLocal<Set<CursorIterator>> mCursorIterators = new ThreadLocal<>();

    public OwlDatabaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        this(context, name, factory, version, null);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public OwlDatabaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version,
                                 DatabaseErrorHandler errorHandler) {
        super(context, name, factory, version, errorHandler);
    }

    public <T> T getTable(Class<T> clazz) {
        final OwlTable owl = getOwlTable(clazz);
        Object tableInterface = owl.tableInterface;
        if (tableInterface == null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (owl) {
                if (owl.tableInterface == null) {
                    tableInterface = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                            new InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                    QueryInfo queryInfo = owl.mQueryInfos.get(method);
                                    if (queryInfo == null) {
                                        throw new UnsupportedOperationException();
                                    }
                                    return queryInfo.query(owl, args);
                                }
                            });
                    owl.tableInterface = tableInterface;
                }
            }
        }
        //noinspection unchecked
        return (T) tableInterface;
    }

    @NonNull
    private OwlTable getOwlTable(Class clazz) {
        synchronized (mTables) {
            OwlTable owl = mTables.get(clazz);
            if (owl == null) {
                owl = parseClass(clazz);
                mTables.put(clazz, owl);
            }
            return owl;
        }
    }

    private OwlTable parseClass(Class clazz) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Only interface is allowed: " + clazz.getCanonicalName());
        }

        Table table = (Table) clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException("@Table missing");
        }
        String tableName = table.value();

        OwlTable owl = new OwlTable(tableName);
        for (Method method : clazz.getMethods()) {
            Query query = method.getAnnotation(Query.class);
            boolean returnTypeValid = true;
            if (query != null) {
                SelectInfo info = new SelectInfo();
                info.selection = query.where();
                info.projection = query.select();
                info.orderBy = query.orderBy();
                if (info.projection.length == 0) {
                    info.projection = null;
                }
                if (info.orderBy.length() == 0) {
                    info.orderBy = null;
                }
                parseParameters(method, info);

                Type returnType = method.getGenericReturnType();
                if (returnType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) returnType;
                    Type rawType = pt.getRawType();
                    if (rawType == Iterable.class) {
                        info.returnType = RETURN_TYPE_ITERABLE;
                        info.modelClass = (Class) pt.getActualTypeArguments()[0];
                    } else if (rawType == List.class || rawType == ArrayList.class) {
                        info.returnType = RETURN_TYPE_LIST;
                        info.modelClass = (Class) pt.getActualTypeArguments()[0];
                    } else {
                        returnTypeValid = false;
                    }
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    info.returnType = RETURN_TYPE_BOOLEAN;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("Supported return types for @Query: Iterable<T>, boolean");
                }

                owl.mQueryInfos.put(method, info);
                continue;
            }

            Delete delete = method.getAnnotation(Delete.class);
            if (delete != null) {
                DeleteInfo info = new DeleteInfo();
                info.selection = delete.where();
                parseParameters(method, info);

                Class returnType = method.getReturnType();
                if (returnType == Void.TYPE || returnType == Void.class) {
                    info.returnType = RETURN_TYPE_VOID;
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    info.returnType = RETURN_TYPE_BOOLEAN;
                } else if (returnType == Integer.TYPE || returnType == Integer.class) {
                    info.returnType = RETURN_TYPE_INT;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("void, boolean or int is supported for @Delete");
                }

                owl.mQueryInfos.put(method, info);
                continue;
            }

            Insert insert = method.getAnnotation(Insert.class);
            InsertOrReplace insertOrReplace = method.getAnnotation(InsertOrReplace.class);
            if (insert != null || insertOrReplace != null) {
                InsertInfo info = new InsertInfo();
                info.valueSetter.constantValues = parseConstantValues(method);
                parseParameters(method, info);

                if (insertOrReplace != null) {
                    info.conflictAlgorithm = SQLiteDatabase.CONFLICT_REPLACE;
                } else {
                    info.conflictAlgorithm = insert.onConflict();
                }

                Class returnType = method.getReturnType();
                if (returnType == Void.TYPE || returnType == Void.class) {
                    info.returnType = RETURN_TYPE_VOID;
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    info.returnType = RETURN_TYPE_BOOLEAN;
                } else if (returnType == Long.TYPE || returnType == Long.class) {
                    info.returnType = RETURN_TYPE_LONG;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("void, boolean or long is supported for @Insert");
                }

                owl.mQueryInfos.put(method, info);
                continue;
            }

            Update update = method.getAnnotation(Update.class);
            if (update != null) {
                UpdateInfo info = new UpdateInfo();
                info.selection = update.where();
                info.valueSetter.constantValues = parseConstantValues(method);
                parseParameters(method, info);

                Class returnType = method.getReturnType();
                if (returnType == Void.TYPE || returnType == Void.class) {
                    info.returnType = RETURN_TYPE_VOID;
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    info.returnType = RETURN_TYPE_BOOLEAN;
                } else if (returnType == Long.TYPE || returnType == Integer.class) {
                    info.returnType = RETURN_TYPE_INT;
                } else {
                    returnTypeValid = false;
                }
                if (!returnTypeValid) {
                    throw new IllegalArgumentException("void, boolean or int is supported for @Update");
                }

                owl.mQueryInfos.put(method, info);
            }
        }

        mTables.put(clazz, owl);
        return owl;
    }

    private static void parseParameters(Method method, QueryInfo queryInfo) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        int length = parameterAnnotations.length;

        SelectableQueryInfo selectableQueryInfo = queryInfo instanceof SelectableQueryInfo ? ((SelectableQueryInfo)
                queryInfo) : null;
        boolean[] isSelectionArgument;
        if (selectableQueryInfo != null) {
            isSelectionArgument = new boolean[length];
            selectableQueryInfo.isSelectionArgument = isSelectionArgument;
        } else {
            isSelectionArgument = null;
        }

        ValueSettableQueryInfo valueSettableQueryInfo = queryInfo instanceof ValueSettableQueryInfo ? (
                (ValueSettableQueryInfo) queryInfo) : null;
        String[] argumentColumnNames;
        if (valueSettableQueryInfo != null) {
            argumentColumnNames = new String[length];
            valueSettableQueryInfo.valueSetter().argumentColumnNames = argumentColumnNames;
        } else {
            argumentColumnNames = null;
        }

        for (int i = 0; i < length; i++) {
            Annotation[] annotations = parameterAnnotations[i];
            for (Annotation annotation : annotations) {
                if (isSelectionArgument != null && annotation instanceof Where) {
                    isSelectionArgument[i] = true;
                }
                if (argumentColumnNames != null && annotation instanceof Value) {
                    argumentColumnNames[i] = ((Value) annotation).value();
                }
            }
        }
    }

    static ContentValues makeValues(String[] names, Object[] args, @Nullable List<Map.Entry<String, Object>>
            constValues) {
        int length = args == null ? 0 : args.length;
        ContentValues values = new ContentValues();
        for (int i = 0; i < length; i++) {
            String column = names[i];
            if (column == null) continue;
            putValue(values, column, args[i]);
        }
        if (constValues != null) {
            for (Map.Entry<String, Object> entry : constValues) {
                putValue(values, entry.getKey(), entry.getValue());
            }
        }
        return values;
    }

    static void putValue(ContentValues values, String column, Object value) {
        if (value == null) {
            values.putNull(column);
        } else if (value instanceof Boolean) {
            values.put(column, (Boolean) value);
        } else if (value instanceof Byte) {
            values.put(column, (Byte) value);
        } else if (value instanceof byte[]) {
            values.put(column, (byte[]) value);
        } else if (value instanceof Double) {
            values.put(column, (Double) value);
        } else if (value instanceof Float) {
            values.put(column, (Float) value);
        } else if (value instanceof Integer) {
            values.put(column, (Integer) value);
        } else if (value instanceof Long) {
            values.put(column, (Long) value);
        } else if (value instanceof Short) {
            values.put(column, (Short) value);
        } else if (value instanceof String) {
            values.put(column, (String) value);
        } else if (value instanceof CharSequence) {
            values.put(column, value.toString());
        } else if (value instanceof Parcelable) {
            Parcel parcel = Parcel.obtain();
            ((Parcelable) value).writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            byte[] bytes = parcel.marshall();
            values.put(column, bytes);
        } else {
            PlainDataModel.putInto(values, value);
        }
    }

    static List<Map.Entry<String, Object>> parseConstantValues(Method method) {
        ConstantValues values = method.getAnnotation(ConstantValues.class);
        if (values == null) return null;

        ArrayList<Map.Entry<String, Object>> list = new ArrayList<>();

        String[] intKeys = values.intKeys();
        int[] intValues = values.intValues();
        int length = intKeys.length;
        if (length != intValues.length) {
            throw new IllegalArgumentException("intKeys.length should be equal to intValues.length");
        }
        for (int i = 0; i < length; i++) {
            list.add(new AbstractMap.SimpleEntry<String, Object>(intKeys[i], intValues[i]));
        }

        for (String s : values.nullKeys()) {
            list.add(new AbstractMap.SimpleEntry<>(s, null));
        }

        list.trimToSize();
        return list;
    }

    public String getTableName(Class clazz) {
        return getOwlTable(clazz).mTableName;
    }

    public void createTable(SQLiteDatabase db, Class clazz, String... columns) {
        db.execSQL("create table " + getTableName(clazz) + '(' + TextUtils.join(",", columns) + ')');
    }

    public void createIndex(SQLiteDatabase db, Class clazz, String... columns) {
        String tableName = getTableName(clazz);
        db.execSQL("create index " + tableName + '_' + TextUtils.join("_", columns) + " on "
                + tableName + '(' + TextUtils.join(",", columns) + ')');
    }

    public void beginTransaction() {
        mLock.lock();
        getWritableDatabase().beginTransaction();
    }

    public void endTransaction() {
        getWritableDatabase().endTransaction();
        mLock.unlock();
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        SQLiteDatabase db = super.getReadableDatabase();
        //noinspection deprecation
        db.setLockingEnabled(false);
        return db;
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        SQLiteDatabase db = super.getWritableDatabase();
        //noinspection deprecation
        db.setLockingEnabled(false);
        return db;
    }

    void addCursorIterator(CursorIterator it) {
        Set<CursorIterator> set = mCursorIterators.get();
        if (set == null) {
            set = Collections.newSetFromMap(new WeakHashMap<CursorIterator, Boolean>());
            mCursorIterators.set(set);
        }
        set.add(it);
    }

    void removeCursorIterator(CursorIterator it) {
        Set<CursorIterator> set = mCursorIterators.get();
        if (set != null) {
            set.remove(it);
        }
    }

    public void closeCursors() {
        Set<CursorIterator> set = mCursorIterators.get();
        if (set != null) {
            for (CursorIterator it : set) {
                it.close();
            }
            set.clear();
        }
    }
}
