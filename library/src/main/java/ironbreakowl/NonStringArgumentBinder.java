package ironbreakowl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NonStringArgumentBinder {
    private static final Pattern PATTERN_ARGUMENT_PLACEHOLDER_OR_STRING = Pattern.compile("'(?:[^']|\\\\')'|`[^`]`|\\?");

    public String[] selectionArgs;
    public String where;

    public NonStringArgumentBinder(String where, Object[] args) {
        int selectionArgCount = 0;
        boolean hasNonString = false;
        for (Object arg : args) {
            if (isNumber(arg) || arg instanceof Boolean) {
                hasNonString = true;
            } else {
                ++selectionArgCount;
            }
        }
        int argLength = args.length;
        if (!hasNonString) {
            selectionArgs = new String[argLength];
            for (int i = 0; i < argLength; i++) {
                selectionArgs[i] = args[i].toString();
            }
            this.where = where;
            return;
        }

        Matcher m = PATTERN_ARGUMENT_PLACEHOLDER_OR_STRING.matcher(where);
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int selectionIndex = 0;
        int lastEnd = 0;
        selectionArgs = new String[selectionArgCount];
        while (m.find()) {
            String s = m.group();
            if (!"?".equals(s)) {
                continue;
            }
            int start = m.start();
            Object arg = args[argIndex++];
            String stringValue;
            if (isNumber(arg)) {
                stringValue = arg.toString();
            } else if (arg instanceof Boolean) {
                stringValue = (Boolean) arg ? "1" : "0";
            } else {
                stringValue = "?";
                selectionArgs[selectionIndex++] = arg.toString();
            }
            sb.append(where, lastEnd, start);
            sb.append(stringValue);
            lastEnd = m.end();
        }
        sb.append(where, lastEnd, where.length());
        this.where = sb.toString();
    }

    private static boolean isNumber(Object o) {
        return o instanceof Byte ||
                o instanceof Short ||
                o instanceof Integer ||
                o instanceof Long ||
                o instanceof Float ||
                o instanceof Double;
    }
}
