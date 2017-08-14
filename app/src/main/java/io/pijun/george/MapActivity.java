package io.pijun.george;

import android.Manifest;
import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.firebase.crash.FirebaseCrash;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Locale;

import io.pijun.george.api.LocationIQClient;
import io.pijun.george.api.Message;
import io.pijun.george.api.OscarAPI;
import io.pijun.george.api.OscarClient;
import io.pijun.george.api.OscarError;
import io.pijun.george.api.PackageWatcher;
import io.pijun.george.api.RevGeocoding;
import io.pijun.george.api.User;
import io.pijun.george.api.UserComm;
import io.pijun.george.crypto.EncryptedData;
import io.pijun.george.crypto.KeyPair;
import io.pijun.george.event.FriendRemoved;
import io.pijun.george.event.LocationSharingGranted;
import io.pijun.george.event.LocationSharingRevoked;
import io.pijun.george.models.FriendLocation;
import io.pijun.george.models.FriendRecord;
import io.pijun.george.models.MovementType;
import io.pijun.george.models.UserRecord;
import io.pijun.george.service.FcmTokenRegistrar;
import io.pijun.george.service.LimitedShareService;
import io.pijun.george.service.LocationUploadService;
import io.pijun.george.service.MessageQueueService;
import io.pijun.george.view.MyLocationView;
import retrofit2.Call;
import retrofit2.Response;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMarkerClickListener, AvatarsAdapter.AvatarsAdapterListener {

    private static final int REQUEST_LOCATION_PERMISSION = 18;
    private static final int REQUEST_LOCATION_SETTINGS = 20;

    private MapView mMapView;
    private MapboxMap mMapboxMap;
    private volatile PackageWatcher mPkgWatcher;
    private MarkerTracker mMarkerTracker = new MarkerTracker();
    private FusedLocationProviderClient mLocationProviderClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private MarkerView mMeMarker;
    private boolean mCameraTracksMyLocation = false;
    private EditText mUsernameField;

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, MapActivity.class);
    }

    @Override
    @UiThread
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Is there a user account here? If not, send them to the login/sign up screen
        if (!Prefs.get(this).isLoggedIn()) {
            Intent welcomeIntent = WelcomeActivity.newIntent(this);
            startActivity(welcomeIntent);
            finish();
            return;
        }

        getWindow().getDecorView().setBackground(null);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_map);

        final Button button = (Button) findViewById(R.id.drawer_button);
        final ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) button.getLayoutParams();
        params.topMargin = getStatusBarHeight();

        mMapView = (MapView) findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(this);

        final View myLocFab = findViewById(R.id.my_location_fab);
        myLocFab.setOnClickListener(v -> {
            myLocFab.setSelected(true);
            mCameraTracksMyLocation = true;
            flyCameraToMyLocation();
        });

        NavigationView navView = (NavigationView) findViewById(R.id.navigation);
        navView.setNavigationItemSelectedListener(navItemListener);

        mLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5 * DateUtils.SECOND_IN_MILLIS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();

        startService(FcmTokenRegistrar.newIntent(this));
//        startService(ActivityMonitor.newIntent(this));
    }

    @Override
    @UiThread
    protected void onStart() {
        super.onStart();

        App.isInForeground = true;
        checkForLocationPermission();
        App.registerOnBus(this);
        mMapView.onStart();

        App.runInBackground(() -> {
            Prefs prefs = Prefs.get(MapActivity.this);
            String token = prefs.getAccessToken();
            if (token == null) {
                return;
            }
            mPkgWatcher = PackageWatcher.createWatcher(MapActivity.this, token);
            if (mPkgWatcher == null) {
                L.w("unable to create package watcher");
                return;
            }

            ArrayList<FriendRecord> friends = DB.get(MapActivity.this).getFriends();
            for (FriendRecord fr: friends) {
                if (fr.receivingBoxId != null) {
                    mPkgWatcher.watch(fr.receivingBoxId);
                }
            }

            // Request a location update from any friend that hasn't given us an update for
            // 3 minutes
            long now = System.currentTimeMillis();
            KeyPair keypair = prefs.getKeyPair();
            for (FriendRecord fr : friends) {
                // check if this friend shares location with us
                if (fr.receivingBoxId == null) {
                    continue;
                }
                FriendLocation loc = DB.get(MapActivity.this).getFriendLocation(fr.id);
                if (loc == null || (now-loc.time) > 180 * DateUtils.SECOND_IN_MILLIS) {
                    if (keypair != null) {
                        UserComm comm = UserComm.newLocationUpdateRequest();
                        byte[] msgBytes = comm.toJSON();
                        EncryptedData encMsg = Sodium.publicKeyEncrypt(msgBytes, fr.user.publicKey, keypair.secretKey);
                        if (encMsg != null) {
                            OscarClient.queueSendMessage(MapActivity.this, token, fr.user.userId, encMsg, true);
                        } else {
                            L.w("Failed to encrypt a location update request message to " + fr.user.username);
                        }
                    }
                }
            }

            OscarAPI api = OscarClient.newInstance(token);
            try {
                Response<Message[]> response = api.getMessages().execute();
                if (!response.isSuccessful()) {
                    OscarError err = OscarError.fromResponse(response);
                    L.w("error checking for messages: " + err);
                    return;
                }
                Message[] msgs = response.body();
                if (msgs == null) {
                    return;
                }
                for (Message msg : msgs) {
                    MessageQueueService.queueMessage(MapActivity.this, msg);
                }
            } catch (IOException ignore) {
                // meh, we'll try again later
            }
        });
    }

    @Override
    @UiThread
    protected void onResume() {
        super.onResume();

        mMapView.onResume();
    }

    @Override
    @UiThread
    protected void onPause() {
        super.onPause();

        mMapView.onPause();
    }

    @Override
    @UiThread
    protected void onStop() {
        super.onStop();

        mMapView.onStop();

        if (mMapboxMap != null) {
            CameraPosition pos = mMapboxMap.getCameraPosition();
            Prefs.get(this).setCameraPosition(pos);
        }

        // hide visible info windows, so outdated info is not visible in case the activity is
        // brought back into view
        for (Marker m : mMarkerTracker.getMarkers()) {
            if (m.isInfoWindowShown()) {
                m.hideInfoWindow();
            }
        }

        // stop receiving location updates
        mLocationProviderClient.removeLocationUpdates(mLocationCallbackHelper);

        App.unregisterFromBus(this);
        App.runInBackground(() -> {
            if (mPkgWatcher != null) {
                mPkgWatcher.disconnect();
                mPkgWatcher = null;
            }
        });

        App.isInForeground = false;
    }

    @Override
    @UiThread
    protected void onDestroy() {
        mMarkerTracker.clear();

        super.onDestroy();
        if (mMapView != null) {
            mMapView.onDestroy();
            mMapView = null;
        }
    }

    @Override
    @UiThread
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mMapView.onSaveInstanceState(outState);
    }

    @Override
    @UiThread
    public void onLowMemory() {
        super.onLowMemory();

        mMapView.onLowMemory();
    }

    private void flyCameraToMyLocation() {
        if (mMapboxMap == null) {
            return;
        }
        if (mMeMarker == null) {
            return;
        }
        CameraPosition cp = new CameraPosition.Builder()
                .target(mMeMarker.getPosition())
                .zoom(13)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        mMapboxMap.animateCamera(cu, 1000);
    }

    @Override
    @UiThread
    public void onMapReady(MapboxMap mapboxMap ) {
        if (mapboxMap == null) {
            L.i("onMapReady has a null map arg");
            FirebaseCrash.log("MapboxMap is null");
            return;
        }
        mMapboxMap = mapboxMap;
        CameraPosition pos = Prefs.get(this).getCameraPosition();
        if (pos != null) {
            mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
        mMapboxMap.setOnMarkerClickListener(this);
        mMapboxMap.getUiSettings().setCompassEnabled(false);
        mMapboxMap.setOnScrollListener(() -> {
            mCameraTracksMyLocation = false;
            findViewById(R.id.my_location_fab).setSelected(false);
        });

        // add markers for all friends
        App.runInBackground(() -> {
            DB db = DB.get(MapActivity.this);
            ArrayList<FriendRecord> friends = db.getFriends();
            for (final FriendRecord f : friends) {
                final FriendLocation location = db.getFriendLocation(f.id);
                if (location == null) {
                    continue;
                }

                App.runOnUiThread(() -> addMapMarker(f, location));
            }
        });
    }

    @AnyThread
    private void beginLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This should never happen. Nobody should be calling this method before permission has been obtained.
            L.w("MapActivity.beginLocationUpdates was called before obtaining location permission");
            FirebaseCrash.report(new Exception("Location updates requested before acquiring permission"));
            return;
        }

        mLocationProviderClient.requestLocationUpdates(mLocationRequest, mLocationCallbackHelper, Looper.getMainLooper());
    }

    private void addMyLocation(Location location) {
        if (mMapboxMap == null) {
            return;
        }

//        int fortyFive = getResources().getDimensionPixelSize(R.dimen.fortyFive);
        Bitmap bitmap = MyLocationView.getBitmap(this);// Bitmap.createBitmap(fortyFive, fortyFive, Bitmap.Config.ARGB_8888);
//        MyLocationView.draw(this, bitmap);

        Icon descriptor = IconFactory.getInstance(this).fromBitmap(bitmap);
        MarkerViewOptions opts = new MarkerViewOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .rotation(location.getBearing())
                .icon(descriptor);
        mMeMarker = mMapboxMap.addMarker(opts);
    }

    @UiThread
    private void addMapMarker(FriendRecord friend, FriendLocation loc) {
        if (mMapboxMap == null) {
            return;
        }
        int fortyEight = getResources().getDimensionPixelSize(R.dimen.thirtyTwo);
        Bitmap bitmap = Bitmap.createBitmap(fortyEight, fortyEight, Bitmap.Config.ARGB_8888);
        Identicon.draw(bitmap, friend.user.username);

        Icon descriptor = IconFactory.getInstance(this).fromBitmap(bitmap);
        MarkerOptions opts = new MarkerOptions()
                .position(new LatLng(loc.latitude, loc.longitude))
                .icon(descriptor)
                .title(friend.user.username);
        Marker marker = mMapboxMap.addMarker(opts);
        mMarkerTracker.add(marker, friend.id, loc);
    }

    @UiThread
    private void checkForLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionVerified();
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // show the reasoning
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
            builder.setTitle("Permission request");
            builder.setMessage("Pijun uses your location to show your position on the map, and to securely share it with friends that you've authorized. It's never used for any other purpose.");
            builder.setPositiveButton(R.string.ok, (dialog, which) -> ActivityCompat.requestPermissions(
                    MapActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION));
            builder.show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            L.w("onRequestPermissionsResult called with unknown request code: " + requestCode);
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            L.i("permission granted");
            locationPermissionVerified();
        } else {
            L.i("permission denied");
        }
    }

    @UiThread
    private void locationPermissionVerified() {
        startService(LocationUploadService.newIntent(this));

        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnFailureListener(this, e -> {
                    int statusCode = ((ApiException) e).getStatusCode();
                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                ResolvableApiException rae = (ResolvableApiException) e;
                                rae.startResolutionForResult(this, REQUEST_LOCATION_SETTINGS);
                            } catch (IntentSender.SendIntentException sie) {
                                L.w("Unable to start settings resolution", sie);
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String errMsg = "Location settings are inadequate, and cannot be fixed here. Fix in Settings.";
                            Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                            break;
                    }

                });

        beginLocationUpdates();
    }

    @UiThread
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }

        return result;
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationSharingGranted(final LocationSharingGranted grant) {
        L.i("onLocationSharingGranted");
        if (mPkgWatcher == null) {
            return;
        }

        App.runInBackground(() -> {
            final FriendRecord friend = DB.get(MapActivity.this).getFriendByUserId(grant.userId);
            if (friend == null) {
                L.w("MapActivity.onLocationSharingGranted didn't find friend record");
                return;
            }
            L.i("onLocationSharingGranted: friend found. will watch");
            mPkgWatcher.watch(friend.receivingBoxId);
        });
    }

    @Subscribe
    @Keep
    @UiThread
    public void onLocationSharingRevoked(final LocationSharingRevoked revoked) {
        // remove the map marker, if there is one
        App.runInBackground(() -> {
            DB db = DB.get(this);
            FriendRecord friend = db.getFriendByUserId(revoked.userId);
            if (friend == null) {
                return;
            }
            App.runOnUiThread(() -> {
                Marker marker = mMarkerTracker.removeMarker(friend.id);
                if (marker == null) {
                    return;
                }
                mMapboxMap.removeMarker(marker);
            });
        });
    }

    @Subscribe
    @Keep
    @UiThread
    public void onFriendLocationUpdated(final FriendLocation loc) {
        // check if we already have a marker for this friend
        Marker marker = mMarkerTracker.getById(loc.friendId);
        if (marker == null) {
            App.runInBackground(() -> {
                final FriendRecord friend = DB.get(MapActivity.this).getFriendById(loc.friendId);
                App.runOnUiThread(() -> addMapMarker(friend, loc));
            });
        } else {
            if (marker.isInfoWindowShown()) {
                marker.hideInfoWindow();
            }
            marker.setPosition(new LatLng(loc.latitude, loc.longitude));
            mMarkerTracker.updateLocation(loc.friendId, loc);
        }
    }

    @UiThread
    public void onShowDrawerAction(View v) {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.openDrawer(GravityCompat.START, true);
    }

    @UiThread
    private void onLogOutAction() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setMessage(R.string.confirm_log_out_msg);
        builder.setCancelable(true);
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.log_out, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Utils.logOut(MapActivity.this, new UiRunnable() {
                    @Override
                    public void run() {
                        Intent welcomeIntent = WelcomeActivity.newIntent(MapActivity.this);
                        startActivity(welcomeIntent);
                        finish();
                    }
                });
            }
        });
        builder.show();
    }

    @UiThread
    private void onShowLogs() {
        Intent i = LogActivity.newIntent(this);
        startActivity(i);
    }

    private NavigationView.OnNavigationItemSelectedListener navItemListener = item -> {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawers();

        int id = item.getItemId();
        switch (id) {
            case R.id.profile:
                showProfile();
                break;
            case R.id.about:
                break;
            case R.id.log_out:
                onLogOutAction();
                break;
            case R.id.view_logs:
                onShowLogs();
                break;
        }

        return false;
    };

    @Override
    @UiThread
    public boolean onMarkerClick(@NonNull final Marker marker) {
        if (marker == mMeMarker) {
            return true;
        }

        final FriendLocation loc = mMarkerTracker.getLocation(marker);
        long now = System.currentTimeMillis();
        final CharSequence relTime;
        if (loc.time >= now-60*DateUtils.SECOND_IN_MILLIS) {
            relTime = getString(R.string.now);
        } else {
            relTime = DateUtils.getRelativeTimeSpanString(
                    loc.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
        }
        StringBuilder snippetBuilder = new StringBuilder(relTime.toString() + ", ");
        if (loc.speed != null) {
            snippetBuilder.append(loc.speed).append(" m/s, ");
        }
        if (loc.bearing != null) {
            snippetBuilder.append(loc.bearing).append("°, ");
        }
        if (loc.accuracy != null) {
            snippetBuilder.append("±").append(loc.accuracy).append(" m, ");
        }
        String movements = MovementType.serialize(loc.movements);
        if (movements.length() > 0) {
            snippetBuilder.append(movements).append(", ");
        }
        final String snippet = snippetBuilder.toString();

        marker.setSnippet(snippet);

        App.runInBackground(() -> {
            try {
                Call<RevGeocoding> call = LocationIQClient.get(MapActivity.this).getReverseGeocoding("" + loc.latitude, "" + loc.longitude);
                Response<RevGeocoding> response = call.execute();
                if (response.isSuccessful()) {
                    final RevGeocoding revGeocoding = response.body();
                    if (revGeocoding == null) {
                        return;
                    }
                    App.runOnUiThread(() -> marker.setSnippet(snippet + revGeocoding.getArea()));
                } else {
                    L.w("error calling locationiq");
                }
            } catch (Exception ex) {
                L.w("network error obtaining reverse geocoding", ex);
            }
        });
        return false;
    }

    @Override
    public void onAvatarSelected(FriendRecord fr) {
        L.i("selected: " + fr.user.username);
        Marker marker = mMarkerTracker.getById(fr.id);
        if (marker == null) {
            return;
        }

        mCameraTracksMyLocation = false;
        findViewById(R.id.my_location_fab).setSelected(false);
        CameraPosition cp = new CameraPosition.Builder()
                .target(marker.getPosition())
                .zoom(13)
                .bearing(0)
                .tilt(0).build();
        CameraUpdate cu = CameraUpdateFactory.newCameraPosition(cp);
        mMapboxMap.animateCamera(cu, 1000);
    }

    public void onAddFriendAction(View v) {
        L.i("onAddFriendAction");
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setPositiveButton("Add friend", (dialog, which) -> showAddFriendDialog())
                .setNeutralButton("Limited Share",
                        (dialog, which) -> startService(LimitedShareService.newIntent(MapActivity.this, LimitedShareService.ACTION_START)))
                .setNegativeButton("Cancel", null)
                .setCancelable(true).setMessage("Add friend?").show();
    }

    private void showAddFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(R.string.add_friend);
        builder.setView(R.layout.friend_request_form);
        builder.setPositiveButton(R.string.send, (dialog, which) -> {
            final String username = mUsernameField.getText().toString();
            App.runInBackground(() -> attemptLocationGrant(username));
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.show();

        mUsernameField = (EditText) dialog.findViewById(R.id.username);
    }

    private void showProfile() {
        Prefs prefs = Prefs.get(this);
        String username = prefs.getUsername();
        KeyPair kp = prefs.getKeyPair();
        String msg;
        if (kp == null) {
            msg = "You're not logged in";
        } else {
            msg = String.format(
                    Locale.US,
                    "Public key:\n%s",
                    Hex.toHexString(kp.publicKey));
        }
        Utils.showStringAlert(this, username, msg);
    }

    @WorkerThread
    private void attemptLocationGrant(String username) {
        Prefs prefs = Prefs.get(this);
        String accessToken = prefs.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Utils.showStringAlert(this, null, "How are you not logged in right now? (missing access token)");
            return;
        }
        KeyPair keyPair = prefs.getKeyPair();
        if (keyPair == null) {
            Utils.showStringAlert(this, null, "How are you not logged in right now? (missing key pair)");
            return;
        }
        OscarAPI api = OscarClient.newInstance(accessToken);
        try {
            DB db = DB.get(this);
            UserRecord userRecord = db.getUser(username);
            if (userRecord == null) {
                Response<User> searchResponse = api.searchForUser(username).execute();
                if (!searchResponse.isSuccessful()) {
                    OscarError err = OscarError.fromResponse(searchResponse);
                    Utils.showStringAlert(this, null, "Unable to find username: " + err);
                    return;
                }
                User userToRequest = searchResponse.body();
                if (userToRequest == null) {
                    Utils.showStringAlert(this, null, "Unknown error while retrieving info about username");
                    return;
                }
                userRecord = db.addUser(userToRequest.id, userToRequest.username, userToRequest.publicKey);
            }

            // check if we already have this user as a friend, and if we're already sharing with them
            final FriendRecord friend = db.getFriendByUserId(userRecord.id);
            if (friend != null) {
                if (friend.sendingBoxId != null) {
                    // send the sending box id to this person one more time, just in case
                    UserComm comm = UserComm.newLocationSharingGrant(friend.sendingBoxId);
                    EncryptedData msg = Sodium.publicKeyEncrypt(comm.toJSON(), userRecord.publicKey, keyPair.secretKey);
                    if (msg != null) {
                        OscarClient.queueSendMessage(this, accessToken, userRecord.userId, msg, false);
                    }
                    Utils.showStringAlert(this, null, "You're already sharing your location with " + username);
                    return;
                }
            }

            byte[] sendingBoxId = new byte[Constants.DROP_BOX_ID_LENGTH];
            new SecureRandom().nextBytes(sendingBoxId);
            UserComm comm = UserComm.newLocationSharingGrant(sendingBoxId);
            EncryptedData msg = Sodium.publicKeyEncrypt(comm.toJSON(), userRecord.publicKey, keyPair.secretKey);
            if (msg == null) {
                Utils.showStringAlert(this, null, "Unable to create sharing grant");
                return;
            }
            OscarClient.queueSendMessage(this, accessToken, userRecord.userId, msg, false);

            db.startSharingWith(userRecord, sendingBoxId);
            Utils.showStringAlert(this, null, "You're now sharing with " + username);
        } catch (IOException ex) {
            Utils.showStringAlert(this, null, "Network problem trying to share your location. Check your connection thent ry again.");
        } catch (DB.DBException dbe) {
            Utils.showStringAlert(this, null, "Error adding friend into database");
            FirebaseCrash.report(dbe);
        }
    }

    @Subscribe
    @Keep
    public void onFriendRemoved(FriendRemoved evt) {
        Marker marker = mMarkerTracker.removeMarker(evt.friendId);
        if (marker != null) {
            mMapboxMap.removeMarker(marker);
        }
    }

    private LocationCallback mLocationCallbackHelper = new LocationCallback() {
        @Override
        @UiThread
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (mMeMarker == null) {
                addMyLocation(location);
            } else {
                ValueAnimator posAnimator = ObjectAnimator.ofObject(
                        mMeMarker,
                        "position",
                        new Utils.LatLngEvaluator(),
                        mMeMarker.getPosition(),
                        new LatLng(location.getLatitude(), location.getLongitude()));
                posAnimator.setDuration(300);

                ValueAnimator rotAnimator = ObjectAnimator.ofObject(
                        mMeMarker,
                        "rotation",
                        new FloatEvaluator(),
                        mMeMarker.getRotation(),
                        location.getBearing());
                rotAnimator.setDuration(300);

                posAnimator.start();
                rotAnimator.start();
            }

            if (mCameraTracksMyLocation) {
                if (mMapboxMap != null) {
                    CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
                    mMapboxMap.animateCamera(update, 300);
                }
            }
            App.postOnBus(location);
        }
    };
}
