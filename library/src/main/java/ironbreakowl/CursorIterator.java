package ironbreakowl;

import android.database.Cursor;

class CursorIterator implements ClosableIterator {
    private Cursor mCursor;
    private Object mCursorReader;
    private final OwlDatabaseOpenHelper mOpenHelper;

    CursorIterator(Cursor cursor, Object cursorReader, OwlDatabaseOpenHelper openHelper) {
        mCursor = cursor;
        mCursorReader = cursorReader;
        mOpenHelper = openHelper;
        if (cursor != null) {
            openHelper.mLock.lock();
            openHelper.addCursorIterator(this);
        }
    }

    @Override
    public boolean hasNext() {
        if (mCursor == null) {
            return false;
        }
        boolean hasNext = mCursor.moveToNext();
        if (!hasNext) {
            close();
        }
        return hasNext;
    }

    @Override
    public Object next() {
        return mCursorReader;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            mOpenHelper.removeCursorIterator(this);
            mOpenHelper.mLock.unlock();
        }
    }
}
