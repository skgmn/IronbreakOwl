package ironbreakowl;

import org.junit.Test;

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
        userTable.add("User1", true, "12341234");
        userTable.add("User2", false, null);
        userTable.add("User3", true, "00000000");
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

    private void assertValues(List<User> values) {
        assertEquals("User1", values.get(0).name);
        assertEquals(true, values.get(0).hasPhone);
        assertEquals("12341234", values.get(0).phoneNumber);

        assertEquals("User2", values.get(1).name);
        assertEquals(false, values.get(1).hasPhone);
        assertNull(values.get(1).phoneNumber);

        assertEquals("User3", values.get(2).name);
        assertEquals(true, values.get(2).hasPhone);
        assertEquals("00000000", values.get(2).phoneNumber);
    }

    @Override
    public void dispose() {
        super.dispose();
        openHelper.getTable(UserTable.class).clear();
    }
}
