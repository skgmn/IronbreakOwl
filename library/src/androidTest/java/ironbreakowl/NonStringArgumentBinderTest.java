package ironbreakowl;

import junit.framework.TestCase;

import java.util.Arrays;

public class NonStringArgumentBinderTest extends TestCase {
    public void testBind() {
        NonStringArgumentBinder binder = new NonStringArgumentBinder("a=? and b=?", new Object[] {"A", "B"},
                makeTrueArrays(2));
        assertEquals("a=? and b=?", binder.selection);
        assertEquals(2, binder.selectionArgs.length);
        assertEquals("A", binder.selectionArgs[0]);
        assertEquals("B", binder.selectionArgs[1]);

        binder = new NonStringArgumentBinder("a=? and b=?", new Object[] {1, "a"}, makeTrueArrays(2));
        assertEquals("a=1 and b=?", binder.selection);
        assertEquals(1, binder.selectionArgs.length);
        assertEquals("a", binder.selectionArgs[0]);

        binder = new NonStringArgumentBinder("a=? and b=? or c=?", new Object[] {"a", 1, "b"}, makeTrueArrays(3));
        assertEquals("a=? and b=1 or c=?", binder.selection);
        assertEquals(2, binder.selectionArgs.length);
        assertEquals("a", binder.selectionArgs[0]);
        assertEquals("b", binder.selectionArgs[1]);

        binder = new NonStringArgumentBinder("a=? and b=? and c=? and d=?", new Object[] {"a", true, false, 2},
                makeTrueArrays(4));
        assertEquals("a=? and b=1 and c=0 and d=2", binder.selection);
        assertEquals(1, binder.selectionArgs.length);
        assertEquals("a", binder.selectionArgs[0]);

        binder = new NonStringArgumentBinder("a='?' and b=?", new Object[] {2}, makeTrueArrays(1));
        assertEquals("a='?' and b=2", binder.selection);
        assertEquals(0, binder.selectionArgs.length);
    }

    private static boolean[] makeTrueArrays(int length) {
        boolean[] arrays = new boolean [length];
        Arrays.fill(arrays, true);
        return arrays;
    }
}