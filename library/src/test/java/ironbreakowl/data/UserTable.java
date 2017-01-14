package ironbreakowl.data;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import ironbreakowl.Delete;
import ironbreakowl.Insert;
import ironbreakowl.Query;
import ironbreakowl.Table;
import ironbreakowl.Value;
import ironbreakowl.Where;
import rx.Observable;

@Table("user")
public interface UserTable {
    String NAME = "name";
    String HAS_PHONE = "has_phone";
    String PHONE_NUMBER = "phone_number";

    @Delete
    void clear();

    @Insert
    void add(@Value(NAME) String name,
             @Value(HAS_PHONE) boolean hasPhone,
             @Value(PHONE_NUMBER) String phoneNumber);

    @Query
    int getCount();

    @Query(where = NAME + "=?")
    boolean userExists(@Where String name);

    @Query
    List<User> getAllList();

    @Query
    Observable<User> getAllObservable();

    @Query
    Flowable<User> getAllFlowable();

    @Query(where = NAME + "=?")
    Maybe<User> findUser(@Where String name);
}
