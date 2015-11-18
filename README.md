# IronbreakOwl

![](http://media-hearth.cursecdn.com/avatars/147/679/500.png)

IronbreakOwl is a library that provides SQLite query mapping on Android. It can be used like this:

```java
@Table("person")
public interface PersonTable {
    String ID = "id";
    String NAME = "name";
    String AGE = "age";
    String GENDER = "gender";
    
    String GENDER_MALE = "male";
    String GENDER_FEMALE = "female";

    @Query(select = {ID, NAME, AGE, GENDER}, where = AGE + ">=?")
    Iterable<PersonReader> findPeopleOlderThan(@Where int age);
    
    @Insert
    void addNewPerson(@Value(NAME) String name, 
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
                column(PersonTable.ID, Long.class, PRIMARY_KEY, AUTO_INCREMENT, NOT_NULL),
                column(PersonTable.NAME, String.class, NOT_NULL),
                column(PersonTable.AGE, Integer.TYPE, NOT_NULL),
                column(PersonTable.GENDER, String.class, NOT_NULL));
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
        personTable.addNewPerson("John", 24, PersonTable.GENDER_MALE);
        personTable.addNewPerson("Mary", 18, PersonTable.GENDER_FEMALE);
        personTable.addNewPerson("Tom", 32, PersonTable.GENDER_MALE);
        
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

## Features

* Mapping each query to a method
* Thread safe
* <code>Iterable</code> interface can be used to iterate <code>Cursor</code> by for-each loop
* Supports <code>Parcelable</code>, <code>boolean</code> as a data type

## Queries

### SELECT

<code>SELECT</code> query can be annotated by <code>@Query</code>.

```java
@Query(select = {ID, NAME}, where = NAME + "=? or " + AGE + "=?", orderBy = ADDRESS + " desc")
List<PersonData> getPeople(@Where String name, @Where int age);
```

Attributes are optional.

#### Supported return types

1. List<T>
   - When return type is <code>List</code>, method reads all data from <code>Cursor</code> at once and collect them into a <code>List</code>. In this case, <code>T</code> should be a POJO class of which fields are annotated by <code>@Column</code>.
   ```java
   public class PersonData {
       @Column(PersonTable.ID)
       public int id;
    
       @Column(PersonTable.NAME)
       public String name;
   }
   ```
2. Iterable<T>
   - When return type is <code>Iterable</code>, method returns an <code>Iterable</code> object which iterates <code>Cursor</code>. In this case, <code>T</code> should be a reader interface of which methods are annotated by <code>@Column</code>.
   - Each method of a reader should contain no parameters, and should have return type which correspond to data type.
   ```java
   public interface PersonReader {
       @Column(PersonTable.NAME)
       String name();
    
       @Column(PersonTable.AGE)
       int age();
    
       @Column(PersonTable.GENDER)
       String gender();
   }
   ```
   - Reader do not read value from <code>Cursor</code> until a method is called.
   - Closing cursor
     - <code>Cursor.close()</code> is automatically called when <code>Iterable.hasNext()</code> becomes <code>false</code>.
     - When a loop does not continue until <code>Iterable.hasNext()</code> become <code>false</code> (such like when <code>break</code> is called during loop), it is **vital** to call <code>OwlDatabaseOpenHelper.closeCursors()</code> after loop. This method close all the cursors which are opened from the current thread.
     ```java
     try {
         for (PersonReader reader : db.findPeopleOlderThan(13)) {
             if (someConditions()) break;
             // ...
         }
     } finally {
         db.closeCursors();
     }
     ```
3. Single<T>
   - When return type is <code>Single</code> (from package <code>ironbreakowl</code>), method reads the first tuple from <Code>Cursor</code> and return it with <code>Single</code> object wrapped. A value can be fetched by <code>Single.getValue()</code> while the existence can be checked by inspecting <code>Single.hasValue()</code>.
4. int
   - When return type is <code>int</code> or <code>Integer</code>, method just returns <code>Cursor.getCount()</code>.
5. boolean
   - When return type is <code>boolean</code> or <code>Boolean</code>, method returns <code>Cursor.moveToNext()</code>, which means if there is any data satisfying conditions of a query.

### INSERT

<code>INSERT</code> query can be annotated by <code>@Insert</code>.

```java
@Insert(onConflict = SQLiteDatabase.CONFLICT_IGNORE)
void addNewPerson(@Value(NAME) String name, @Value(AGE) int age);
```

If you want to insert with constant values, <code>@ConstantValues</code> can be used.

```java
@Insert
@ConstantValues(stringKeys = GENDER, stringValues = "male")
void addMale(@Value(NAME) String name, @Value(AGE) int age);
```

When <code>onConflict</code> is <code>CONFLICT_REPLACE</code>, <code>@InsertOrReplace</code> can be used instead.

```java
@InsertOrReplace
void addNewPerson(@Value(NAME) String name, @Value(AGE) int age);
```

#### Supported return types

1. void
2. long
   - Returns count of affected rows.
3. boolean
   - Returns if there are any row affected.

### UPDATE

<code>UPDATE</code> query can be annotated by <code>@Update</code>.

```java
@Update(where = NAME + "=?")
void updateAge(@Where String name, @Value(AGE) int age);
```

If you want to set constant values, <code>@ConstantValues</code> can be used.

```java
@Update(where = ID + "=?")
@ConstantValues(
    intKeys = AGE, intValues = 1,
    stringKeys = {NAME, GENDER}, stringValues = {"Mr. Terminator", "unknown"}
)
void makeTerminator(@Where int id);
```

This is actually not so elegant form but as far as I know, it is still the best and only way to set constant values through an annotation.

#### Supported return types

1. void
2. long
   - Returns count of affected rows.
3. boolean
   - Returns if there are any row affected.
   
### DELETE

<code>DELETE</code> query can be annotated by <code>@Delete</code>.

```java
@Delete(where = GENDER + "='" + GENDER_MALE + "'")
void destroyMales();
```

#### Supported return types

1. void
2. long
   - Returns count of affected rows.
3. boolean
   - Returns if there are any row affected.
