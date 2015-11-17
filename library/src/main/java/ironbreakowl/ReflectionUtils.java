package ironbreakowl;

import android.os.Parcelable;

import java.lang.reflect.Field;

public class ReflectionUtils {
    public static Parcelable.Creator getParcelCreator(Class clazz) {
        try {
            Field creatorField = clazz.getField("CREATOR");
            creatorField.setAccessible(true);
            Parcelable.Creator parcelCreator = (Parcelable.Creator) creatorField.get(clazz);
            if (parcelCreator == null) {
                throw new NullPointerException();
            }
            return parcelCreator;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Cannot find CREATOR for " + clazz.getCanonicalName());
        }
    }
}
