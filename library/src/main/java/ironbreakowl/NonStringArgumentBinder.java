package ironbreakowl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NonStringArgumentBinder {
    private static final Pattern PATTERN_ARGUMENT_PLACEHOLDER_OR_STRING = Pattern.compile("'(?:[^']|\\\\')'|`[^`]`|\\?");

    String[] selectionArgs;
    String selection;

    NonStringArgumentBinder() {
    }

    NonStringArgumentBinder(String selection, Object[] args, boolean[] whereTarget) {
        int selectionArgCount = 0;
        boolean hasNonString = false;
        int argLength = args == null ? 0 : args.length;
        for (int i = 0; i < argLength; i++) {
            if (!whereTarget[i]) continue;
            Object arg = args[i];
            if (isNumber(arg) || arg instanceof Boolean) {
                hasNonString = true;
            } else {
                ++selectionArgCount;
            }
        }
        if (!hasNonString) {
            selectionArgs = new String[selectionArgCount];
            for (int i = 0, j = 0; i < argLength; i++) {
                if (!whereTarget[i]) continue;
                selectionArgs[j++] = args[i].toString();
            }
            this.selection = selection;
            return;
        }

        Matcher m = PATTERN_ARGUMENT_PLACEHOLDER_OR_STRING.matcher(selection);
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

            while (argIndex < argLength && !whereTarget[argIndex]) {
                ++argIndex;
            }
            if (argIndex >= argLength) {
                break;
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
            sb.append(selection, lastEnd, start);
            sb.append(stringValue);
            lastEnd = m.end();
        }
        sb.append(selection, lastEnd, selection.length());
        this.selection = sb.toString();
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
