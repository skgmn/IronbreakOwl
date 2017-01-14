package ironbreakowl;

import io.reactivex.Flowable;
import rx.Observable;

class RxJavaHelper {
    private static Boolean hasRxJava1;
    private static Boolean hasRxJava2;

    static boolean hasRxJava1() {
        if (hasRxJava1 == null) {
            try {
                Class.forName(Observable.class.getName());
                hasRxJava1 = true;
            } catch (Throwable e) {
                hasRxJava1 = false;
            }
        }
        return hasRxJava1;
    }

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
