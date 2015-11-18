package ironbreakowl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConstantValues {
    String[] intKeys() default {};
    int[] intValues() default {};

    String[] stringKeys() default {};
    String[] stringValues() default {};

    String[] booleanKeys() default {};
    boolean[] booleanValues() default {};

    String[] nullKeys() default {};
}
