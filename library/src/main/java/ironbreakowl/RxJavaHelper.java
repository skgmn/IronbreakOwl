package ironbreakowl;

import io.reactivex.Flowable;

class RxJavaHelper {
    private static Boolean hasRxJava2;

    static boolean hasRxJava2() {
        if (hasRxJava2 == null) {
            try {
                Class.forName(Flowable.class.getName());
                hasRxJava2 = true;
            } catch (Throwable e) {
                hasRxJava2 = false;
            }
        }
        return hasRxJava2;
    }
}
