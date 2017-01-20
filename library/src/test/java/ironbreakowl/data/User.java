package ironbreakowl.data;

import ironbreakowl.Column;
import ironbreakowl.Condition;
import ironbreakowl.Conditional;
import ironbreakowl.Model;

@Model
public class User {
    @Column(UserTable.NAME)
    public String name;
    @Column(UserTable.HAS_PHONE)
    public boolean hasPhone;
    @Column(UserTable.PHONE_NUMBER)
    public String phoneNumber;
    @Column(UserTable.IS_PUBLIC)
    public boolean isPublic;
    @Conditional
    @Column(UserTable.PRIVATE_DATA)
    public String privateData;

    @SuppressWarnings("unused")
    @Condition(UserTable.PRIVATE_DATA)
    private boolean shouldLoadPrivateDate() {
        return isPublic;
    }
}
