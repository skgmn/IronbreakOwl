package ironbreakowl.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Ignore;

import ironbreakowl.OwlDatabaseOpenHelper;

public class OpenHelper extends OwlDatabaseOpenHelper {
    public OpenHelper(Context context) {
        super(context, "testdb", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db, UserTable.class,
                column(UserTable.NAME, String.class),
                column(UserTable.HAS_PHONE, Boolean.class),
                column(UserTable.PHONE_NUMBER, String.class));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
