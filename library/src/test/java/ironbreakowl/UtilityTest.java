package ironbreakowl;

import org.junit.Test;

import ironbreakowl.data.UserTable;

import static org.junit.Assert.assertEquals;

public class UtilityTest extends DataTestBase {
    @Test
    public void tableName() {
        assertEquals("user", openHelper.getTableName(UserTable.class));
    }
}
