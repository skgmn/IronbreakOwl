package ironbreakowl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import rx.Observable;

public abstract class OwlDatabaseOpenHelper extends SQLiteOpenHelper {
    private static final int RETURN_TYPE_BOOLEAN = 0;
    private static final int RETURN_TYPE_INT = 1;
    private static final int RETURN_TYPE_VOID = 2;
    private static final int RETURN_TYPE_LONG = 3;
    private static final int RETURN_TYPE_LIST = 4;
    private static final int RETURN_TYPE_OLD_OBSERVABLE = 5;
    private static final int RETURN_TYPE_FLOWABLE = 6;
    private static final int RETURN_TYPE_MAYBE = 7;

    protected static final String PRIMARY_KEY = "primary key";
    protected static final String AUTO_INCREMENT = "autoincrement";
    protected static final String NOT_NULL = "not null";
    protected static final String DEFAULT_NULL = "default null";

    private static final Pattern PATTERN_CONSTANT_ARGUMENT_PLACEHOLDER_OR_STRING =
            Pattern.compile("'(?:[^']|\\\\')'|`[^`]`|%[dsb]");

    static abstract class QueryInfo {
        int returnType;
        Class modelClass;

        public abstract Object query(OwlTable table, Object[] args);
    }

    static abstract class SelectableQueryInfo extends QueryInfo {
        String selection;
        boolean[] isSelectionArgument;

        @NonNull
        NonStringArgumentBinder bind(Object[] args) {
            if (!TextUtils.isEmpty(selection)) {
                return new NonStringArgumentBinder(selection, args, isSelectionArgument);
            } else {
                return new NonStringArgumentBinder();
            }
        }
    }

    private static class ValueSetter {
        String[] argumentColumnNames;
        boolean[] optional;
        List<Map.Entry<String, Object>> constantValues;
    }

    interface ValueSettableQueryInfo {
        ValueSetter valueSetter();
    }

    private class SelectInfo extends SelectableQueryInfo {
        String[] projection;
        String orderBy;
        String[] passedParameterNames;

        private Map<String, Object> buildPassedParameters(Object[] args) {
            if (passedParameterNames != null) {
                Map<String, Object> params = new HashMap<>(args.length);
                int length = passedParameterNames.length;
                for (int i = 0; i < length; i++) {
                    String name = passedParameterNames[i];
                    if (name == null) continue;
                    params.put(name, args[i]);
                }
                return params;
            } else {
                return null;
            }
        }

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
                    case RETURN_TYPE_INT:
                        int count = cursor.getCount();
                        cursor.close();
                        return count;
                    case RETURN_TYPE_LIST:
                        return PlainDataModel.toList(cursor, modelClass, buildPassedParameters(args));
                    case RETURN_TYPE_OLD_OBSERVABLE:
                        return PlainDataModel.toOldObservable(cursor, modelClass, buildPassedParameters(args));
                    case RETURN_TYPE_FLOWABLE:
                        return PlainDataModel.toFlowable(cursor, modelClass, buildPassedParameters(args));
                    case RETURN_TYPE_MAYBE:
                        return PlainDataModel.toMaybe(cursor, modelClass, buildPassedParameters(args));
                }
            } finally {
                mLock.unlock();
            }
            return null;
        }
    }

    private static boolean isPrimitiveWrapper(Class clazz) {
        return clazz == Boolean.class ||
                clazz == Character.class ||
                clazz == Byte.class ||
                clazz == Short.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Float.class ||
                clazz == Double.class ||
                clazz == byte[].class;
    }

    private class DeleteInfo extends SelectableQueryInfo {
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

    private class InsertInfo extends QueryInfo implements ValueSettableQueryInfo {
        ValueSetter valueSetter = new ValueSetter();
        int conflictAlgorithm;

        @Override
        public ValueSetter valueSetter() {
            return valueSetter;
        }

        @Override
        public Object query(OwlTable owl, Object[] args) {
            mLock.lock();
            try {
                SQLiteDatabase db = getWritableDatabase();
                ContentValues values = makeValues(valueSetter, args);
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

    private class UpdateInfo extends SelectableQueryInfo implements ValueSettableQueryInfo {
        ValueSetter valueSetter = new ValueSetter();

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
                ContentValues values = makeValues(valueSetter, args);
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

    private static class OwlTable {
        private final String mTableName;
        private final HashMap<Method, QueryInfo> mQueryInfos = new HashMap<>();

        Object tableInterface;

        OwlTable(String tableName) {
            this.mTableName = tableName;
        }
    }

    private final HashMap<Class, OwlTable> mTables = new HashMap<>();
    private final ReentrantLock mLock = new ReentrantLock();
    private WeakReference<SQLiteDatabase> mLockingDisabledDatabase;

    public OwlDatabaseOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version); // Don't call this(...)
        init();
    }

    private void init() {
        getWritableDatabase(); // Make the locking disabled
    }

    public <T> T getTable(Class<T> clazz) {
        final OwlTable owl = getOwlTable(clazz);
        Object tableInterface = owl.tableInterface;
        if (tableInterface == null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (owl) {
                if (owl.tableInterface == null) {
                    tableInterface = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                            (proxy, method, args) -> {
                                QueryInfo queryInfo = owl.mQueryInfos.get(method);
                                if (queryInfo == null) {
                                    throw new UnsupportedOperationException();
                                }
                                return queryInfo.query(owl, args);
                            });
                    owl.tableInterface = tableInterface;
                } else {
                    tableInterface = owl.tableInterface;
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
                info.selection = buildPredicate(query.where(), method.getAnnotation(ConstantWhere.class));
                info.projection = query.select();
                info.orderBy = query.orderBy();
                if (info.projection.length == 0) {
                    info.projection = null;
                }
                if (info.orderBy.length() == 0) {
                    info.orderBy = null;
                }
                parseParameters(method, info);

                Annotation[][] annotations = method.getParameterAnnotations();
                int length = annotations.length;
                if (length != 0) {
                    info.passedParameterNames = new String[length];
                    for (int i = 0; i < length; ++i) {
                        Annotation[] parameterAnnotations = annotations[i];
                        for (Annotation annotation : parameterAnnotations) {
                            if (annotation instanceof Parameter) {
                                info.passedParameterNames[i] = ((Parameter) annotation).value();
                                break;
                            }
                        }
                    }
                }

                Type returnType = method.getGenericReturnType();
                if (returnType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) returnType;
                    Type rawType = pt.getRawType();
                    if (rawType == List.class || rawType == ArrayList.class) {
                        info.returnType = RETURN_TYPE_LIST;
                        info.modelClass = OwlUtils.getActualType(pt, 0);
                    } else if (isFlowable(rawType)) {
                        info.returnType = RETURN_TYPE_FLOWABLE;
                        info.modelClass = OwlUtils.getActualType(pt, 0);
                    } else if (isMaybe(rawType)) {
                        info.returnType = RETURN_TYPE_MAYBE;
                        info.modelClass = OwlUtils.getActualType(pt, 0);
                    } else if (isOldObservable(rawType)) {
                        info.returnType = RETURN_TYPE_OLD_OBSERVABLE;
                        info.modelClass = OwlUtils.getActualType(pt, 0);
                    } else {
                        returnTypeValid = false;
                    }
                } else if (returnType == Boolean.TYPE || returnType == Boolean.class) {
                    info.returnType = RETURN_TYPE_BOOLEAN;
                } else if (returnType == Integer.TYPE || returnType == Integer.class) {
                    info.returnType = RETURN_TYPE_INT;
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
                info.selection = buildPredicate(delete.where(), method.getAnnotation(ConstantWhere.class));
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
                info.selection = buildPredicate(update.where(), method.getAnnotation(ConstantWhere.class));
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

    private static boolean isOldObservable(Type type) {
        return RxJavaHelper.hasRxJava1() && type == Observable.class;
    }

    private static boolean isFlowable(Type type) {
        return RxJavaHelper.hasRxJava2() && type == Flowable.class;
    }

    private static boolean isMaybe(Type type) {
        return RxJavaHelper.hasRxJava2() && type == Maybe.class;
    }

    static String buildPredicate(String predicate, ConstantWhere annotation) {
        if (annotation == null) {
            return predicate;
        }
        int indexString = 0;
        int indexInteger = 0;
        int indexBoolean = 0;
        Matcher m = PATTERN_CONSTANT_ARGUMENT_PLACEHOLDER_OR_STRING.matcher(predicate);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String replacement;
            String group = m.group();
            switch (group) {
                case "%d":
                    replacement = String.valueOf(annotation.ints()[indexInteger++]);
                    break;
                case "%s":
                    replacement = escape(annotation.strings()[indexString++]);
                    break;
                case "%b":
                    replacement = annotation.booleans()[indexBoolean++] ? "1" : "0";
                    break;
                default:
                    replacement = group;
                    break;
            }
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
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
        boolean[] optional;
        if (valueSettableQueryInfo != null) {
            argumentColumnNames = new String[length];
            optional = new boolean[length];
            ValueSetter valueSetter = valueSettableQueryInfo.valueSetter();
            valueSetter.argumentColumnNames = argumentColumnNames;
            valueSetter.optional = optional;
        } else {
            argumentColumnNames = null;
            optional = null;
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
                if (optional != null && annotation instanceof Optional) {
                    optional[i] = true;
                }
            }
        }
    }

    private static ContentValues makeValues(ValueSetter valueSetter, Object[] args) {
        String[] names = valueSetter.argumentColumnNames;
        boolean[] optional = valueSetter.optional;
        List<Map.Entry<String, Object>> constValues = valueSetter.constantValues;

        int length = args == null ? 0 : args.length;
        ContentValues values = new ContentValues();
        for (int i = 0; i < length; i++) {
            String column = names[i];
            if (column == null) continue;

            boolean isOptional = optional[i];
            Object value = args[i];
            if (!isOptional || value != null) {
                OwlUtils.putValue(values, column, value);
            }
        }
        if (constValues != null) {
            for (Map.Entry<String, Object> entry : constValues) {
                OwlUtils.putValue(values, entry.getKey(), entry.getValue());
            }
        }
        return values;
    }

    private static List<Map.Entry<String, Object>> parseConstantValues(Method method) {
        ConstantValues values = method.getAnnotation(ConstantValues.class);
        if (values == null) return null;

        ArrayList<Map.Entry<String, Object>> list = new ArrayList<>();

        String[] keys = values.intKeys();
        int[] intValues = values.intValues();
        int length = keys.length;
        if (length != intValues.length) {
            throw new IllegalArgumentException("intKeys.length should be equal to intValues.length");
        }
        for (int i = 0; i < length; i++) {
            list.add(new AbstractMap.SimpleEntry<>(keys[i], intValues[i]));
        }

        keys = values.stringKeys();
        String[] stringValues = values.stringValues();
        length = keys.length;
        if (length != stringValues.length) {
            throw new IllegalArgumentException("stringKeys.length should be equal to stringValues.length");
        }
        for (int i = 0; i < length; i++) {
            list.add(new AbstractMap.SimpleEntry<>(keys[i], stringValues[i]));
        }

        keys = values.booleanKeys();
        boolean[] booleanValues = values.booleanValues();
        length = keys.length;
        if (length != booleanValues.length) {
            throw new IllegalArgumentException("booleanKeys.length should be equal to booleanValues.length");
        }
        for (int i = 0; i < length; i++) {
            list.add(new AbstractMap.SimpleEntry<>(keys[i], booleanValues[i]));
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

    public void addColumn(SQLiteDatabase db, Class clazz, String name, Class dataType) {
        String tableName = getTableName(clazz);
        db.execSQL("alter table " + tableName + " add column " + column(name, dataType));
    }

    protected static String column(String name, Class dataType, String... attributes) {
        String dataTypeString;
        if (dataType == Boolean.TYPE || dataType == Boolean.class ||
                dataType == Integer.TYPE || dataType == Integer.class ||
                dataType == Long.TYPE || dataType == Long.class) {
            dataTypeString = "integer";
        } else if (dataType == String.class) {
            dataTypeString = "text";
        } else if (dataType == byte[].class || Parcelable.class.isAssignableFrom(dataType)) {
            dataTypeString = "blob";
        } else if (dataType == Float.TYPE || dataType == Float.class ||
                dataType == Double.TYPE || dataType == Double.class) {
            dataTypeString = "real";
        } else {
            throw new IllegalArgumentException("Unsupported type: " + dataType.getName());
        }
        return name + " " + dataTypeString + " " + TextUtils.join(" ", attributes);
    }

    protected static String defaultValue(long value) {
        return "default " + value;
    }

    protected static String defaultValue(String strValue) {
        return "default " + escape(strValue);
    }

    protected static String primaryKey(String... columns) {
        if (columns.length == 0) {
            throw new IllegalArgumentException("No columns were provided as primary key");
        }
        return "primary key (" + TextUtils.join(",", columns) + ")";
    }

    private static String escape(String s) {
        return "'" + s.replaceAll("'", "''") + "'";
    }

    public void beginTransaction() {
        mLock.lock();
        getWritableDatabase().beginTransaction();
    }

    public void endTransaction() {
        getWritableDatabase().endTransaction();
        mLock.unlock();
    }

    public void setTransactionSuccessful() {
        getWritableDatabase().setTransactionSuccessful();
    }

    @SuppressWarnings("deprecation")
    private void setLockingDisabled(SQLiteDatabase db) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) return;
        SQLiteDatabase lockingDisabledDatabase = mLockingDisabledDatabase == null ? null :
                mLockingDisabledDatabase.get();
        if (lockingDisabledDatabase != db) {
            db.setLockingEnabled(false);
            mLockingDisabledDatabase = new WeakReference<>(db);
        }
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        SQLiteDatabase db = super.getReadableDatabase();
        setLockingDisabled(db);
        return db;
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        SQLiteDatabase db = super.getWritableDatabase();
        setLockingDisabled(db);
        return db;
    }
}
