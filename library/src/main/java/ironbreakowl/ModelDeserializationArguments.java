package ironbreakowl;

import java.util.Map;

class ModelDeserializationArguments {
    Map<String, Object> parameters;
    Map<String, Predicate> conditions;

    Predicate getPredicate(String columnName) {
        return conditions == null ? null : conditions.get(columnName);
    }
}
