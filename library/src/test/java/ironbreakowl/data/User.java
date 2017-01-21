package ironbreakowl.data;

import ironbreakowl.Column;
import ironbreakowl.Condition;
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
    @Column(value = UserTable.PRIVATE_DATA, conditional = true)
    public String privateData;

    @SuppressWarnings("unused")
    @Condition(UserTable.PRIVATE_DATA)
    private boolean shouldLoadPrivateDate() {
        return isPublic;
    }
}