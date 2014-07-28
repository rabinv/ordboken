package in.rab.ordboken;

import android.provider.BaseColumns;

public class OrdbokenContract {
    public OrdbokenContract() {
    }

    public static abstract class HistoryEntry implements BaseColumns {
        public static final String TABLE_NAME = "history";
        public static final String COLUMN_NAME_DATE = "date";
        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_URL = "url";
    }
}