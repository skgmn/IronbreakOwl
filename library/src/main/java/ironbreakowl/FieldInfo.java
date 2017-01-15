package ironbreakowl;

import android.os.Parcelable;

class FieldInfo {
    Column column;
    Class type;
    Parcelable.Creator parcelCreator;

    void setType(Class type) {
        this.type = type;
        parcelCreator = Parcelable.class.isAssignableFrom(type) ? OwlUtils.getParcelCreator(type) : null;
    }
}