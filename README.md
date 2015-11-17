# IronbreakOwl

![](http://media-hearth.cursecdn.com/avatars/147/679/500.png)

IronbreakOwl is a library that provides SQLite query mapping. It can be used like this:

```java
@Table("person")
public interface PersonTable {
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String AGE = "age";
    public static final String GENDER = "gender";
    
    public static final String GENDER_MALE = "male";
    public static final String GENDER_FEMALE = "female";

    @Query(select = {ID, NAME, AGE, GENDER}, where = AGE + ">=?")
    Iterable<PersonReader> findPeopleOlderThan(@Where int age);
    
    @Insert
    void createNewPerson(@Value(NAME) String name, 
                         @Value(AGE) int age,
                         @Value(GENDER) String gender);
    
    @Update(where = NAME + "=?")
    void updateAge(@Where String name, @Value(AGE) int age);
    
    @Delete(where = GENDER + "='" + GENDER_MALE + "'")
    void destroyMales();
}

public interface PersonReader {
    @Column(PersonTable.NAME)
    String name();
    
    @Column(PersonTable.AGE)
    int age();
    
    @Column(PersonTable.GENDER)
    String gender();
}

public class DatabaseOpenHelper extends OwlDatabaseOpenHelper {
    private static final String DB_NAME = "my_database.db";
    private static final int DB_VERSION = 1;

    public DatabaseOpenHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db, PersonTable.class,
                PersonTable.ID + " integer primary key autoincrement not null",
                PersonTable.NAME + " varchar not null",
                PersonTable.AGE + " integer not null",
                PersonTable.GENDER = " varchar not null");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}

public class MainActivity extends Activity {
    private DatabaseeOpenHelper mDb;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDb = new DatabseOpenHelper(this);
        
        PersonTable personTable = mDb.getTable(PersonTable.class);
        personTable.createNewPerson("John", 24, PersonTable.GENDER_MALE);
        personTable.createNewPerson("Mary", 18, PersonTable.GENDER_FEMALE);
        personTable.createNewPerson("Tom", 32, PersonTable.GENDER_MALE);
        
        personTable.updateAge("Mary", 26);

        for (PersonReader reader : personTable.findPeopleOlderThan(24)) {
            String name = reader.name();
            int age = reader.age();
            String gender = reader.gender();
            
            // ...
        }

        personTable.destroyMales();
    }
}
```
