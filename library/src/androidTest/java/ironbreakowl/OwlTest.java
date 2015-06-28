package ironbreakowl;

import junit.framework.TestCase;

public class OwlTest extends TestCase {
    @Table("test_owl")
    private interface TestOwl {
    }

    public void testGetTableName() throws Exception {
        assertEquals("test_owl", Owl.getTableName(TestOwl.class));
    }
}