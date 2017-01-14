package ironbreakowl.data;

import ironbreakowl.Column;

public class User {
    @Column(UserTable.NAME)
    public String name;
    @Column(UserTable.HAS_PHONE)
    public boolean hasPhone;
    @Column(UserTable.PHONE_NUMBER)
    public String phoneNumber;
}
