package ironbreakowl;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import ironbreakowl.data.OpenHelper;
import ironbreakowl.data.UserTable;
import ironbreakowl.library.BuildConfig;

@Ignore
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class DataTestBase {
    OpenHelper openHelper;
    UserTable userTable;

    @Before
    public void setup() {
        openHelper = new OpenHelper(RuntimeEnvironment.application);
        userTable = openHelper.getTable(UserTable.class);
    }

    @After
    public void dispose() {
        openHelper.close();
    }
}
