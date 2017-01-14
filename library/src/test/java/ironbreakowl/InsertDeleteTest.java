package ironbreakowl;

import android.database.Cursor;

import org.junit.Test;

import ironbreakowl.data.UserTable;

import static org.junit.Assert.assertEquals;

public class InsertDeleteTest extends DataTestBase {
    @Test
    public void insertAndClear() {
        UserTable userTable = openHelper.getTable(UserTable.class);
        userTable.add("User1", true, "12341234");
        userTable.add("User2", false, null);
        userTable.add("User3", true, "00000000");

        Cursor cursor;
        cursor = openHelper.getReadableDatabase().rawQuery(
                "select * from " + openHelper.getTableName(UserTable.class), null);
        assertEquals(3, cursor.getCount());
        cursor.close();

        userTable.clear();
        cursor = openHelper.getReadableDatabase().rawQuery(
                "select * from " + openHelper.getTableName(UserTable.class), null);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }
}
