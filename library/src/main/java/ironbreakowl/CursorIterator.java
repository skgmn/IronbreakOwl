package ironbreakowl;

import android.database.Cursor;

public class CursorIterator implements ClosableIterator {
    private Cursor mCursor;
    private Object mCursorReader;

    public CursorIterator(Cursor cursor, Object cursorReader) {
        mCursor = cursor;
        mCursorReader = cursorReader;
    }

    @Override
    public boolean hasNext() {
        if (mCursor == null) {
            return false;
        }
        boolean hasNext = mCursor.moveToNext();
        if (!hasNext) {
            mCursor.close();
            mCursor = null;
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
        }
    }
}
