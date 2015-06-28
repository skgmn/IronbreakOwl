package ironbreakowl;

import junit.framework.TestCase;

public class NonStringArgumentBinderTest extends TestCase {
    public void testBind() {
        NonStringArgumentBinder binder = new NonStringArgumentBinder("a=? and b=?", new Object[] {"A", "B"});
        assertEquals("a=? and b=?", binder.where);
        assertEquals(2, binder.selectionArgs.length);
        assertEquals("A", binder.selectionArgs[0]);
        assertEquals("B", binder.selectionArgs[1]);

        binder = new NonStringArgumentBinder("a=? and b=?", new Object[] {1, "a"});
        assertEquals("a=1 and b=?", binder.where);
        assertEquals(1, binder.selectionArgs.length);
        assertEquals("a", binder.selectionArgs[0]);

        binder = new NonStringArgumentBinder("a=? and b=? or c=?", new Object[] {"a", 1, "b"});
        assertEquals("a=? and b=1 or c=?", binder.where);
        assertEquals(2, binder.selectionArgs.length);
        assertEquals("a", binder.selectionArgs[0]);
        assertEquals("b", binder.selectionArgs[1]);

        binder = new NonStringArgumentBinder("a=? and b=? and c=? and d=?", new Object[] {"a", true, false, 2});
        assertEquals("a=? and b=1 and c=0 and d=2", binder.where);
        assertEquals(1, binder.selectionArgs.length);
        assertEquals("a", binder.selectionArgs[0]);
    }
}