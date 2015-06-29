package ironbreakowl;

import android.database.sqlite.SQLiteDatabase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Insert {
    int onConflict() default SQLiteDatabase.CONFLICT_NONE;
}
