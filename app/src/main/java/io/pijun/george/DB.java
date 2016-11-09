package io.pijun.george;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;

import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.RequestRecord;
import io.pijun.george.models.RequestResponse;
import io.pijun.george.models.UserRecord;

@SuppressWarnings("WeakerAccess")
public class DB {

    private static final String FRIENDS_TABLE = "friends";
    private static final String FRIENDS_COL_ID = "id";
    private static final String FRIENDS_COL_USER_ID = "user_id";
    private static final String FRIENDS_COL_SENDING_BOX_ID = "sending_box_id";
    private static final String FRIENDS_COL_RECEIVING_BOX_ID = "receiving_box_id";
    private static final String[] FRIENDS_COLUMNS = new String[]{
            FRIENDS_COL_ID,
            FRIENDS_COL_USER_ID,
            FRIENDS_COL_SENDING_BOX_ID,
            FRIENDS_COL_RECEIVING_BOX_ID
    };

    private static final String LOCATIONS_TABLE = "locations";
    private static final String LOCATIONS_COL_FRIEND_ID = "friend_id";
    private static final String LOCATIONS_COL_LATITUDE = "latitude";
    private static final String LOCATIONS_COL_LONGITUDE = "longitude";
    private static final String LOCATIONS_COL_TIME = "time";
    private static final String LOCATIONS_COL_ACCURACY = "accuracy";
    private static final String LOCATIONS_COL_SPEED = "speed";
    private static final String[] LOCATIONS_COLUMNS = new String[]{
            LOCATIONS_COL_FRIEND_ID,
            LOCATIONS_COL_LATITUDE,
            LOCATIONS_COL_LONGITUDE,
            LOCATIONS_COL_TIME,
            LOCATIONS_COL_ACCURACY,
            LOCATIONS_COL_SPEED
    };

    private static final String OUTGOING_REQUESTS_TABLE = "outgoing_requests";
    private static final String OUTGOING_REQUESTS_COL_ID = "id";
    private static final String OUTGOING_REQUESTS_COL_USER_ID = "user_id";
    private static final String OUTGOING_REQUESTS_COL_SENT_DATE = "sent_date";
    private static final String OUTGOING_REQUESTS_COL_RESPONSE = "response";
    private static final String[] OUTGOING_REQUESTS_COLUMNS = new String[]{
            OUTGOING_REQUESTS_COL_ID,
            OUTGOING_REQUESTS_COL_USER_ID,
            OUTGOING_REQUESTS_COL_SENT_DATE,
            OUTGOING_REQUESTS_COL_RESPONSE
    };

    private static final String INCOMING_REQUESTS_TABLE = "incoming_requests";
    private static final String INCOMING_REQUESTS_COL_ID = "id";
    private static final String INCOMING_REQUESTS_COL_USER_ID = "user_id";
    private static final String INCOMING_REQUESTS_COL_SENT_DATE = "sent_date";
    private static final String INCOMING_REQUESTS_COL_RESPONSE = "response";
    private static final String[] INCOMING_REQUESTS_COLUMNS = new String[]{
            INCOMING_REQUESTS_COL_ID,
            INCOMING_REQUESTS_COL_USER_ID,
            INCOMING_REQUESTS_COL_SENT_DATE,
            INCOMING_REQUESTS_COL_RESPONSE
    };

    private static final String USERS_TABLE = "users";
    private static final String USERS_COL_ID = "id";
    private static final String USERS_COL_USER_ID = "user_id";
    private static final String USERS_COL_USERNAME = "username";
    private static final String USERS_COL_PUBLIC_KEY = "public_key";
    private static final String[] USERS_COLUMNS = new String[]{
            USERS_COL_ID,
            USERS_COL_USER_ID,
            USERS_COL_USERNAME,
            USERS_COL_PUBLIC_KEY
    };

//    private static final String REQUEST_RESPONSE_GRANTED = "granted";
//    private static final String REQUEST_RESPONSE_REJECTED = "rejected";

    private static volatile DB sDb;

    static class DBException extends Exception {
        DBException(String msg) {
            super(msg);
        }
    }

    private DBHelper mDbHelper;

    private DB(Context context) {
        mDbHelper = new DBHelper(context);
    }

    public static DB get(Context context) {
        if (sDb == null) {
            synchronized (DB.class) {
                if (sDb == null) {
                    sDb = new DB(context);
                }
            }
        }

        return sDb;
    }

    @WorkerThread
    public long addFriend(long userId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId,
                          @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] receivingBoxId) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FRIENDS_COL_USER_ID, userId);
        cv.put(FRIENDS_COL_SENDING_BOX_ID, sendingBoxId);
        cv.put(FRIENDS_COL_RECEIVING_BOX_ID, receivingBoxId);
        long result = db.insert(FRIENDS_TABLE, null, cv);
        if (result == -1) {
            throw new DBException("Error creating friend " + userId);
        }
        return result;
    }

    public long addIncomingRequest(long userId, long sentDate) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(INCOMING_REQUESTS_COL_USER_ID, userId);
        cv.put(INCOMING_REQUESTS_COL_SENT_DATE, sentDate);
        long result = db.insert(INCOMING_REQUESTS_TABLE, null, cv);
        if (result == -1) {
            throw new DBException("Error creating incoming request - userId:" + userId + ", sentDate: " + sentDate);
        }
        return result;
    }

    public long addOutgoingRequest(long userId, long sentDate) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(OUTGOING_REQUESTS_COL_USER_ID, userId);
        cv.put(OUTGOING_REQUESTS_COL_SENT_DATE, sentDate);
        long result = db.insert(OUTGOING_REQUESTS_TABLE, null, cv);
        if (result == -1) {
            throw new DBException("Error creating outgoing requests - userId: " + userId + ", sentDate: " + sentDate);
        }
        return result;
    }

    @WorkerThread
    @NonNull
    public UserRecord addUser(@NonNull @Size(Constants.USER_ID_LENGTH) final byte[] userId,
                              @NonNull String username,
                              @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey) throws DBException {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(USERS_COL_USER_ID, userId);
        cv.put(USERS_COL_USERNAME, username);
        cv.put(USERS_COL_PUBLIC_KEY, publicKey);
        long result = db.insert(USERS_TABLE, null, cv);
        if (result == -1) {
            throw new DBException("Error inserting user " + username);
        }

        UserRecord user = new UserRecord();
        user.id = result;
        user.userId = userId;
        user.username = username;
        user.publicKey = publicKey;
        return user;
    }

    @WorkerThread
    public void deleteUserData() {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.delete(FRIENDS_TABLE, null, null);
        db.delete(INCOMING_REQUESTS_TABLE, null, null);
        db.delete(LOCATIONS_TABLE, null, null);
        db.delete(OUTGOING_REQUESTS_TABLE, null, null);
        db.delete(USERS_TABLE, null, null);
    }

    /*
    @WorkerThread
    @Nullable
    public FriendRecord getFriend(@NonNull @Size(Constants.USER_ID_LENGTH) final byte[] userId) {
        return getFriendMatchingBlob(userId, FRIENDS_COL_USER_ID);
    }
    */

    @WorkerThread
    @Nullable
    public FriendRecord getFriendById(long friendId) {
        FriendRecord friend = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = FRIENDS_COL_ID + "=?";
        String[] selectionArgs = new String[]{
                String.valueOf(friendId)
        };
        try (Cursor c = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, selectionArgs, null, null, null)) {
            if (c.moveToNext()) {
                friend = readFriend(c);
                friend.user = getUserById(friend.userId);
            }
        }
        return friend;
    }

    @WorkerThread
    @Nullable
    public FriendRecord getFriendByReceivingBoxId(@NonNull @Size(Constants.DROP_BOX_ID_LENGTH) final byte[] boxId) {
        return getFriendMatchingBlob(boxId, FRIENDS_COL_RECEIVING_BOX_ID);
    }

    @WorkerThread
    @Nullable
    public FriendRecord getFriendByUserId(long userId) {
        FriendRecord friend = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = FRIENDS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(userId)};
        try (Cursor c = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, args, null, null, null)) {
            if (c.moveToNext()) {
                friend = readFriend(c);
                friend.user = getUserById(userId);
            }
        }

        return friend;
    }

    @Nullable
    public FriendLocation getFriendLocation(long friendRecordId) {
        FriendLocation fl = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = LOCATIONS_COL_FRIEND_ID + "=?";
        String[] selectionArgs = new String[]{String.valueOf(friendRecordId)};
        try (Cursor c = db.query(LOCATIONS_TABLE, LOCATIONS_COLUMNS, selection, selectionArgs, null, null, null)) {
            if (c.moveToNext()) {
                double lat = c.getDouble(c.getColumnIndexOrThrow(LOCATIONS_COL_LATITUDE));
                double lng = c.getDouble(c.getColumnIndexOrThrow(LOCATIONS_COL_LONGITUDE));
                long time = c.getLong(c.getColumnIndexOrThrow(LOCATIONS_COL_TIME));
                Float acc = null;
                int accColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_ACCURACY);
                if (!c.isNull(accColIdx)) {
                    acc = c.getFloat(accColIdx);
                }
                Float speed = null;
                int speedColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_SPEED);
                if (!c.isNull(speedColIdx)) {
                    speed = c.getFloat(speedColIdx);
                }
                fl = new FriendLocation(friendRecordId, lat, lng, time, acc, speed);
            }
        }

        return fl;
    }

    @WorkerThread
    @Nullable
    private FriendRecord getFriendMatchingBlob(@NonNull final byte[] blob, @NonNull String matchingColumn) {
        StringBuilder sql = new StringBuilder("SELECT ");
        String delim = "";
        for (String col : FRIENDS_COLUMNS) {
            sql.append(delim).append(col);
            delim = ",";
        }
        sql.append(" FROM ").append(FRIENDS_TABLE).append(" WHERE ").append(matchingColumn).append("=?");
        SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
            @Override
            public Cursor newCursor(SQLiteDatabase sqLiteDatabase, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
                query.bindBlob(1, blob);
                return new SQLiteCursor(driver, editTable, query);
            }
        };
        FriendRecord fr = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor c = db.rawQueryWithFactory(factory, sql.toString(), null, FRIENDS_TABLE)) {
            if (c.moveToNext()) {
                fr = readFriend(c);
                // obtain the UserRecord for this friend
                fr.user = getUserById(fr.userId);
            }
        }

        return fr;
    }

    @WorkerThread
    public int getFriendRequestsCount() {
        int count = 0;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + INCOMING_REQUESTS_TABLE + " WHERE " + INCOMING_REQUESTS_COL_RESPONSE + " ISNULL";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToNext()) {
                count = cursor.getInt(0);
            }
        }

        return count;
    }

    @WorkerThread
    @NonNull
    public ArrayList<FriendRecord> getFriends() {
        ArrayList<FriendRecord> records = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                FriendRecord fr = readFriend(cursor);
                fr.user = getUserById(fr.userId);
                records.add(fr);
            }
        }

        return records;
    }

    @WorkerThread
    @NonNull
    public ArrayList<FriendRecord> getFriendsToShareWith() {
        ArrayList<FriendRecord> records = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = FRIENDS_COL_SENDING_BOX_ID + " IS NOT NULL";
        try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, null, null, null, null)) {
            while (cursor.moveToNext()) {
                FriendRecord fr = readFriend(cursor);
                fr.user = getUserById(fr.userId);
                records.add(fr);
            }
        }

        return records;
    }

    @WorkerThread
    @NonNull
    public ArrayList<RequestRecord> getIncomingRequests(boolean notRespondedOnly) {
        ArrayList<RequestRecord> requests = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = null;
        if (notRespondedOnly) {
            selection = INCOMING_REQUESTS_COL_RESPONSE + " ISNULL";
        }
        try (Cursor c = db.query(INCOMING_REQUESTS_TABLE, INCOMING_REQUESTS_COLUMNS, selection, null, null, null, null)) {
            while (c.moveToNext()) {
                RequestRecord rr = readRequest(c);
                requests.add(rr);
            }
        }

        return requests;
    }

    @WorkerThread
    @NonNull
    public ArrayList<RequestRecord> getOutgoingRequests() {
        ArrayList<RequestRecord> requests = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor c = db.query(OUTGOING_REQUESTS_TABLE, OUTGOING_REQUESTS_COLUMNS, null, null, null, null, null)) {
            while (c.moveToNext()) {
                RequestRecord rr = readRequest(c);
                requests.add(rr);
            }
        }

        return requests;
    }

    @WorkerThread
    @Nullable
    public UserRecord getUser(@NonNull final byte[] id) {
        StringBuilder sql = new StringBuilder("SELECT ");
        String delim = "";
        for (String col : USERS_COLUMNS) {
            sql.append(delim).append(col);
            delim = ",";
        }
        sql.append(" FROM ").append(USERS_TABLE).append(" WHERE ").append(USERS_COL_USER_ID).append("=?");
        SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
            @Override
            public Cursor newCursor(SQLiteDatabase sqLiteDatabase, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
                query.bindBlob(1, id);
                return new SQLiteCursor(driver, editTable, query);
            }
        };
        UserRecord ur = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor c = db.rawQueryWithFactory(factory, sql.toString(), null, USERS_TABLE)) {
            if (c.moveToNext()) {
                ur = readUser(c);
            }
        }

        return ur;
    }

    @WorkerThread
    @Nullable
    public UserRecord getUser(@NonNull String username) {
        UserRecord ur = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = USERS_COL_USERNAME + "=?";
        String[] args = new String[]{username};
        try (Cursor c = db.query(USERS_TABLE, USERS_COLUMNS, selection, args, null, null, null)) {
            if (c.moveToNext()) {
                ur = readUser(c);
            }
        }

        return ur;
    }

    @WorkerThread
    @Nullable
    public UserRecord getUserById(long id) {
        UserRecord ur = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String selection = USERS_COL_ID + "=?";
        String[] args = new String[]{String.valueOf(id)};
        try (Cursor c = db.query(USERS_TABLE, USERS_COLUMNS, selection, args, null, null, null)) {
            if (c.moveToNext()) {
                ur = readUser(c);
            }
        }

        return ur;
    }

    @WorkerThread
    public void grantSharingTo(@NonNull byte[] userId, @NonNull byte[] sendingBoxId) throws DBException {
        UserRecord user = getUser(userId);
        if (user == null) {
            throw new DBException("You can't grant a share to an unknown user");
        }

        FriendRecord fr = getFriendByUserId(user.id);
        if (fr == null) {
            addFriend(user.id, sendingBoxId, null);
        } else {
            // if we already have a friend record, just add the sending box id
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_SENDING_BOX_ID, sendingBoxId);
            String selection = FRIENDS_COL_USER_ID + "=?";
            String[] args = new String[]{String.valueOf(user.id)};
            long result = db.update(FRIENDS_TABLE, cv, selection, args);
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + user.username + "'");
            }
        }

        // try to update the incoming request table, in case this was a response to a request
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(INCOMING_REQUESTS_COL_RESPONSE, RequestResponse.Granted.val);
        String selection = INCOMING_REQUESTS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(user.id)};
        db.update(INCOMING_REQUESTS_TABLE, cv, selection, args);
    }

    @WorkerThread
    @NonNull
    private static FriendRecord readFriend(Cursor c) {
        FriendRecord f = new FriendRecord();
        f.id = c.getLong(c.getColumnIndexOrThrow(FRIENDS_COL_ID));
        f.userId = c.getLong(c.getColumnIndexOrThrow(FRIENDS_COL_USER_ID));
        f.sendingBoxId = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_SENDING_BOX_ID));
        f.receivingBoxId = c.getBlob(c.getColumnIndexOrThrow(FRIENDS_COL_RECEIVING_BOX_ID));

        return f;
    }

    @WorkerThread
    @NonNull
    private static RequestRecord readRequest(Cursor c) {
        RequestRecord rr = new RequestRecord();
        rr.id = c.getLong(c.getColumnIndexOrThrow(INCOMING_REQUESTS_COL_ID));
        rr.userId = c.getLong(c.getColumnIndexOrThrow(INCOMING_REQUESTS_COL_USER_ID));
        rr.sentDate = c.getLong(c.getColumnIndexOrThrow(INCOMING_REQUESTS_COL_SENT_DATE));
        int respColIdx = c.getColumnIndexOrThrow(INCOMING_REQUESTS_COL_RESPONSE);
        if (c.isNull(respColIdx)) {
            rr.response = RequestResponse.NoResponse;
        } else {
            rr.response = RequestResponse.get(c.getString(respColIdx));
        }
        return rr;
    }

    @WorkerThread
    @NonNull
    private static UserRecord readUser(Cursor c) {
        UserRecord ur = new UserRecord();
        ur.id = c.getLong(c.getColumnIndexOrThrow(USERS_COL_ID));
        ur.publicKey = c.getBlob(c.getColumnIndexOrThrow(USERS_COL_PUBLIC_KEY));
        ur.userId = c.getBlob(c.getColumnIndexOrThrow(USERS_COL_USER_ID));
        ur.username = c.getString(c.getColumnIndexOrThrow(USERS_COL_USERNAME));
        return ur;
    }

    @WorkerThread
    public void rejectRequest(@NonNull UserRecord user) throws DBException {
        // mark the incoming request as rejected
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(INCOMING_REQUESTS_TABLE, RequestResponse.Rejected.val);
        String selection = INCOMING_REQUESTS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(user.id)};
        long result = db.update(INCOMING_REQUESTS_TABLE, cv, selection, args);
        if (result != 1) {
            throw new DBException("Num affected rows was " + result + " for username '" + user.username + "'");
        }
    }

    @WorkerThread
    public void setFriendLocation(long friendId, double lat, double lng, long time, Float accuracy, Float speed) throws DBException {
        L.i("setFriendLocation: {id: " + friendId + ", lat: " + lat + ", lng: " + lng);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(LOCATIONS_COL_FRIEND_ID, friendId);
        cv.put(LOCATIONS_COL_LATITUDE, lat);
        cv.put(LOCATIONS_COL_LONGITUDE, lng);
        cv.put(LOCATIONS_COL_TIME, time);
        if (accuracy != null) {
            cv.put(LOCATIONS_COL_ACCURACY, accuracy);
        }
        if (speed != null) {
            cv.put(LOCATIONS_COL_SPEED, speed);
        }
        long result = db.replace(LOCATIONS_TABLE, null, cv);
        if (result == -1) {
            throw new DBException("Error occurred while setting friend location");
        }
    }

    @WorkerThread
    public void sharingGrantedBy(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
        UserRecord userRecord = getUser(username);
        if (userRecord == null) {
            throw new DBException("You can't add a share from an unknown user");
        }
        FriendRecord friend = getFriendByUserId(userRecord.id);
        if (friend == null) {
            // add a friend record including the drop box id
            addFriend(userRecord.id, null, boxId);
        } else {
            // add the drop box id to the existing friend record
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_RECEIVING_BOX_ID, boxId);
            String selection = FRIENDS_COL_USER_ID + "=?";
            String[] args = new String[]{String.valueOf(userRecord.id)};
            long result = db.update(FRIENDS_TABLE, cv, selection, args);
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + username + "'");
            }
        }

        // did we have an outstanding request for sharing from this user? If so, set the response on it
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(OUTGOING_REQUESTS_COL_RESPONSE, RequestResponse.Granted.val);
        String selection = OUTGOING_REQUESTS_COL_USER_ID + "=?";
        String[] args = new String[]{String.valueOf(userRecord.id)};
        db.update(OUTGOING_REQUESTS_TABLE, cv, selection, args);
    }

    private static class DBHelper extends SQLiteOpenHelper {

        private DBHelper(Context context) {
            super(context, "thedata", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            L.i("DBHelper.onCreate");

            String createFriends = "CREATE TABLE "
                    + FRIENDS_TABLE + " ("
                    + FRIENDS_COL_ID + " INTEGER PRIMARY KEY, "
                    + FRIENDS_COL_USER_ID + " INTEGER UNIQUE NOT NULL, "
                    + FRIENDS_COL_SENDING_BOX_ID + " BLOB, "
                    + FRIENDS_COL_RECEIVING_BOX_ID + " BLOB)";
            db.execSQL(createFriends);

            String createLocations = "CREATE TABLE "
                    + LOCATIONS_TABLE + " ("
                    + LOCATIONS_COL_FRIEND_ID + " INTEGER PRIMARY KEY, "
                    + LOCATIONS_COL_LATITUDE + " REAL NOT NULL, "
                    + LOCATIONS_COL_LONGITUDE + " REAL NOT NULL, "
                    + LOCATIONS_COL_TIME + " INTEGER NOT NULL, "
                    + LOCATIONS_COL_ACCURACY + " REAL, "
                    + LOCATIONS_COL_SPEED + " REAL)";
            db.execSQL(createLocations);

            String createUsers = "CREATE TABLE "
                    + USERS_TABLE + " ("
                    + USERS_COL_ID + " INTEGER PRIMARY KEY, "
                    + USERS_COL_USER_ID + " BLOB UNIQUE NOT NULL, "
                    + USERS_COL_USERNAME + " TEXT NOT NULL, "
                    + USERS_COL_PUBLIC_KEY + " BLOB NOT NULL)";
            db.execSQL(createUsers);

            String createOutgoingRequests = "CREATE TABLE "
                    + OUTGOING_REQUESTS_TABLE + " ("
                    + OUTGOING_REQUESTS_COL_ID + " INTEGER PRIMARY KEY, "
                    + OUTGOING_REQUESTS_COL_USER_ID + " INTEGER UNIQUE NOT NULL, "
                    + OUTGOING_REQUESTS_COL_SENT_DATE + " INTEGER NOT NULL, "
                    + OUTGOING_REQUESTS_COL_RESPONSE + " TEXT)";
            db.execSQL(createOutgoingRequests);

            String createIncomingRequests = "CREATE TABLE "
                    + INCOMING_REQUESTS_TABLE + " ("
                    + INCOMING_REQUESTS_COL_ID + " INTEGER PRIMARY KEY, "
                    + INCOMING_REQUESTS_COL_USER_ID + " INTEGER UNIQUE NOT NULL, "
                    + INCOMING_REQUESTS_COL_SENT_DATE + " INTEGER NOT NULL, "
                    + INCOMING_REQUESTS_COL_RESPONSE + " TEXT)";
            db.execSQL(createIncomingRequests);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            L.i("onUpgrade - old: " + oldVersion + ", new: " + newVersion);
        }

        /*
        @WorkerThread
        private long addFriend(@NonNull String username,
                               long userId,
                               @NonNull @Size(Constants.PUBLIC_KEY_LENGTH) byte[] publicKey,
                               @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] sendingBoxId,
                               @Nullable @Size(Constants.DROP_BOX_ID_LENGTH) byte[] receivingBoxId) throws DBException {
            //noinspection ConstantConditions
            if (username == null) {
                throw new IllegalArgumentException("username must not be null");
            }
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_USER_ID, userId);
            cv.put(FRIENDS_COL_SENDING_BOX_ID, sendingBoxId);
            cv.put(FRIENDS_COL_RECEIVING_BOX_ID, receivingBoxId);
            long result = db.insert(FRIENDS_TABLE, null, cv);
            if (result == 0) {
                throw new DBException("Error creating friend " + username);
            }
            return result;
        }
        */

        /*
        @WorkerThread
        @Nullable
        private FriendRecord getFriendById(long userId) {
            FriendRecord friend = null;
            SQLiteDatabase db = getReadableDatabase();
            String selection = FRIENDS_COL_ID + "=?";
            String[] selectionArgs = new String[]{
                    String.valueOf(userId)
            };
            try (Cursor c = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, selection, selectionArgs, null, null, null)) {
                if (c.moveToNext()) {
                    friend = readFromCursor(c);
                }
            }
            return friend;
        }
        */

        /*
        @WorkerThread
        @Nullable
        private FriendLocation getFriendLocation(long friendRecordId) {
            FriendLocation fl = null;
            SQLiteDatabase db = getReadableDatabase();
            String selection = LOCATIONS_COL_FRIEND_ID + "=?";
            String[] selectionArgs = new String[]{String.valueOf(friendRecordId)};
            try (Cursor c = db.query(LOCATIONS_TABLE, LOCATIONS_COLUMNS, selection, selectionArgs, null, null, null)) {
                if (c.moveToNext()) {
                    double lat = c.getDouble(c.getColumnIndexOrThrow(LOCATIONS_COL_LATITUDE));
                    double lng = c.getDouble(c.getColumnIndexOrThrow(LOCATIONS_COL_LONGITUDE));
                    long time = c.getLong(c.getColumnIndexOrThrow(LOCATIONS_COL_TIME));
                    Float acc = null;
                    int accColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_ACCURACY);
                    if (!c.isNull(accColIdx)) {
                        acc = c.getFloat(accColIdx);
                    }
                    Float speed = null;
                    int speedColIdx = c.getColumnIndexOrThrow(LOCATIONS_COL_SPEED);
                    if (!c.isNull(speedColIdx)) {
                        speed = c.getFloat(speedColIdx);
                    }
                    fl = new FriendLocation(friendRecordId, lat, lng, time, acc, speed);
                }
            }

            return fl;
        }
        */

//        @WorkerThread
//        @Nullable
//        private FriendRecord getFriendMatchingBlob(@NonNull final byte[] blob, @NonNull String matchingColumn) {
//            FriendRecord fr = null;
//            SQLiteDatabase db = getReadableDatabase();
//            StringBuilder sql = new StringBuilder("SELECT ");
//            String delim = "";
//            for (String col : FRIENDS_COLUMNS) {
//                sql.append(delim).append(col);
//                delim = ",";
//            }
//            sql.append(" FROM ").append(FRIENDS_TABLE).append(" WHERE ").append(matchingColumn).append("=?");
//            SQLiteDatabase.CursorFactory factory = new SQLiteDatabase.CursorFactory() {
//                @Override
//                public Cursor newCursor(SQLiteDatabase sqLiteDatabase, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
//                    query.bindBlob(1, blob);
//                    return new SQLiteCursor(driver, editTable, query);
//                }
//            };
//            try (Cursor c = db.rawQueryWithFactory(factory, sql.toString(), null, FRIENDS_TABLE)) {
//                if (c.moveToNext()) {
//                    fr = readFriend(c);
//                }
//            }
//
//            return fr;
//        }

        /*
        @WorkerThread
        private int getFriendRequestsCount() {
            int count = 0;
            SQLiteDatabase db = getReadableDatabase();
            String sql = "SELECT COUNT(*) FROM " + INCOMING_REQUESTS_TABLE + " WHERE " + INCOMING_REQUESTS_COL_RESPONSE + " ISNULL";
            try (Cursor cursor = db.rawQuery(sql, null)) {
                if (cursor.moveToNext()) {
                    count = cursor.getInt(0);
                }
            }

            return count;
        }
        */

        /*
        @WorkerThread
        @NonNull
        private ArrayList<FriendRecord> getFriends() {
            ArrayList<FriendRecord> records = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    FriendRecord fr = readFriend(cursor);
                    records.add(fr);
                }
            }

            return records;
        }
        */

        /*
        @WorkerThread
        @NonNull
        private ArrayList<FriendRecord> getFriendsToShareWith() {
            ArrayList<FriendRecord> records = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(FRIENDS_TABLE, FRIENDS_COLUMNS, FRIENDS_COL_SENDING_BOX_ID + " IS NOT NULL", null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    FriendRecord fr = readFromCursor(cursor);
                    records.add(fr);
                }
            }

            return records;
        }
        */

        /*
        @WorkerThread
        private void setFriendLocation(long userId, double lat, double lng, long time, Float accuracy, Float speed) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(LOCATIONS_COL_FRIEND_ID, userId);
            cv.put(LOCATIONS_COL_LATITUDE, lat);
            cv.put(LOCATIONS_COL_LONGITUDE, lng);
            cv.put(LOCATIONS_COL_TIME, time);
            if (accuracy != null) {
                cv.put(LOCATIONS_COL_ACCURACY, accuracy);
            }
            if (speed != null) {
                cv.put(LOCATIONS_COL_SPEED, speed);
            }
            long result = db.replace(LOCATIONS_TABLE, null, cv);
            if (result == -1) {
                throw new DBException("Error occurred while setting friend location");
            }
        }*/

        /*
        @WorkerThread
        private void setReceivingBoxId(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_RECEIVING_BOX_ID, boxId);
            long result = db.update(FRIENDS_TABLE, cv, "username=?", new String[]{username});
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + username + "'");
            }
        }
        */

        /*
        @WorkerThread
        private void setShareRequestedOfMe(@NonNull String username, boolean shareRequested) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_SHARE_REQUESTED_OF_ME, shareRequested);
            long result = db.update(FRIENDS_TABLE, cv, "username=?", new String[]{username});
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + username + "'");
            }
        }
        */

        /*
        @WorkerThread
        private void setShareGranted(@NonNull String username, @NonNull @Size(Constants.DROP_BOX_ID_LENGTH) byte[] boxId) throws DBException {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(FRIENDS_COL_SENDING_BOX_ID, boxId);
            cv.put(FRIENDS_COL_SHARE_REQUESTED_OF_ME, false);
            long result = db.update(FRIENDS_TABLE, cv, FRIENDS_COL_USERNAME + "=?", new String[]{username});
            if (result != 1) {
                throw new DBException("Num affected rows was " + result + " for username '" + username + "'");
            }
        }
        */
    }
}
