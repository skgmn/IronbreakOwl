package ironbreakowl;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import ironbreakowl.data.User;
import ironbreakowl.data.UserTable;
import rx.observers.AssertableSubscriber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QueryTest extends DataTestBase {
    @Override
    public void setup() {
        super.setup();
        userTable.add("User1", true, "12341234", true, "111");
        userTable.add("User2", false, null, true, "222");
        userTable.add("User3", true, "00000000", false, "333");
    }

    @Test
    public void simpleReturnTypes() {
        assertEquals(3, userTable.getCount());
        assertTrue(userTable.userExists("User1"));
        assertTrue(userTable.userExists("User2"));
        assertTrue(userTable.userExists("User3"));
        assertFalse(userTable.userExists("User4"));
    }

    @Test
    public void queryIterable() {
        List<User> list = new ArrayList<>();
        Iterable<User> iterable = userTable.getAllIterable();
        for (User user : iterable) {
            list.add(user);
        }
        assertValues(list);
    }

    @Test
    public void queryList() {
        assertValues(userTable.getAllList());
    }

    @Test
    public void queryObservable() {
        AssertableSubscriber<User> test = userTable.getAllObservable().test();
        test.assertCompleted();
        test.assertValueCount(3);
        assertValues(test.getOnNextEvents());
    }

    @Test
    public void queryFlowable() {
        TestSubscriber<User> test = userTable.getAllFlowable().test();
        test.assertComplete();
        test.assertValueCount(3);
        assertValues(test.values());
    }

    @Test
    public void queryMaybe() {
        TestObserver<User> test;

        test = userTable.findUser("User1").test();
        test.assertComplete();
        test.assertValueCount(1);
        test.assertValueAt(0, user -> user.hasPhone && "12341234".equals(user.phoneNumber));

        test = userTable.findUser("User2").test();
        test.assertComplete();
        test.assertValueCount(1);
        test.assertValueAt(0, user -> !user.hasPhone && user.phoneNumber == null);

        test = userTable.findUser("User4").test();
        test.assertComplete();
        test.assertNoValues();
    }

    @Test
    public void predicateArgument() {
        List<User> values = userTable.getAllList(user -> true);
        assertEquals("333", values.get(2).privateData);
    }

    @Test
    public void singleColumn() {
        List<String> names = userTable.getNames();
        assertEquals(3, names.size());
        assertEquals("User1", names.get(0));
        assertEquals("User2", names.get(1));
        assertEquals("User3", names.get(2));
    }

    private void assertValues(List<User> values) {
        assertEquals("User1", values.get(0).name);
        assertTrue(values.get(0).hasPhone);
        assertEquals("12341234", values.get(0).phoneNumber);
        assertTrue(values.get(0).isPublic);
        assertEquals("111", values.get(0).privateData);

        assertEquals("User2", values.get(1).name);
        assertEquals(false, values.get(1).hasPhone);
        assertNull(values.get(1).phoneNumber);
        assertTrue(values.get(1).isPublic);
        assertEquals("222", values.get(1).privateData);

        assertEquals("User3", values.get(2).name);
        assertEquals(true, values.get(2).hasPhone);
        assertEquals("00000000", values.get(2).phoneNumber);
        assertFalse(values.get(2).isPublic);
        assertNull(values.get(2).privateData);
    }

    @Override
    public void dispose() {
        super.dispose();
        openHelper.getTable(UserTable.class).clear();
    }
}
