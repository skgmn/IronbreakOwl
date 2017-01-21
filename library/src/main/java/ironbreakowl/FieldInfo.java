package ironbreakowl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

class FieldInfo {
    final Column column;
    final Class type;
    final boolean lazy;

    FieldInfo(Column column, Type type) {
        Class clazz = null;
        boolean lazy = false;
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type rawType = pt.getRawType();
            if (rawType == Lazy.class) {
                lazy = true;
                Type actualType = OwlUtils.getActualType(pt, 0);
                if (actualType instanceof Class) {
                    clazz = (Class) actualType;
                }
            }
        } else if (type instanceof Class) {
            clazz = (Class) type;
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        this.lazy = lazy;
        this.type = clazz;
        this.column = column;
    }
}