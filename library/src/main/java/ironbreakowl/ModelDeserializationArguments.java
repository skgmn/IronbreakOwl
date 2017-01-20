package ironbreakowl;

import java.util.Map;

class ModelDeserializationArguments {
    Map<String, Object> ctorParams;
    Map<String, Predicate> conditions;

    Predicate getPredicate(String columnName) {
        return conditions == null ? null : conditions.get(columnName);
    }
}
