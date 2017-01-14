package ironbreakowl;

import org.junit.Test;

import java.lang.annotation.Annotation;

import static org.junit.Assert.assertEquals;

public class OwlDatabaseOpenHelperTest {
    @Test
    public void testBuildPredicate() throws Exception {
        String result = OwlDatabaseOpenHelper.buildPredicate("a = %d, b = %s, c = %s, d = %b, e = %b",
                new ConstantWhere() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return ConstantWhere.class;
                    }

                    @Override
                    public int[] ints() {
                        return new int[]{1234};
                    }

                    @Override
                    public String[] strings() {
                        return new String[]{"a'b", "ab"};
                    }

                    @Override
                    public boolean[] booleans() {
                        return new boolean[]{true, false};
                    }
                });
        assertEquals("a = 1234, b = 'a''b', c = 'ab', d = 1, e = 0", result);
    }
}