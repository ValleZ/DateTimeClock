package ru.valle.datetimeclock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static android.text.format.DateFormat.is24HourFormat;


public final class FaceActivity extends Activity {

    private static final String TAG = "FaceActivity";
    private boolean dimmed;
    private View contentView;
    private TextView timeView, dateView, timeSuffixView, secondsView;

    final BroadcastReceiver timeUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive " + intent);
            update();
        }
    };
    final Runnable tickTockRunnable = new Runnable() {
        @Override
        public void run() {
//            Log.d(TAG, "tick!");
            update();
            if (!dimmed) {
                handler.removeCallbacks(this);
                handler.postDelayed(this, getDelayForNextSecond());
            }
        }
    };
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.common_face);
        handler = new Handler();
        contentView = findViewById(R.id.content);
        timeView = (TextView) findViewById(R.id.time);
        timeView.setTypeface(Typeface.createFromAsset(getAssets(), "AndroidClock.ttf"));
        timeSuffixView = (TextView) findViewById(R.id.time_suffix);
        secondsView = (TextView) findViewById(R.id.seconds);
        dateView = (TextView) findViewById(R.id.date);
        update();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(timeUpdateReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        registerReceiver(timeUpdateReceiver, new IntentFilter(Intent.ACTION_TIME_CHANGED));
        registerReceiver(timeUpdateReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
    }

    @Override
    protected void onResume() {
        super.onResume();
        dimmed = false;
        Log.d(TAG, "onResume");
        update();
        handler.postDelayed(tickTockRunnable, getDelayForNextSecond());
    }

    private long getDelayForNextSecond() {
        return 1000 - System.currentTimeMillis() % 1000;
    }

    @Override
    protected void onPause() {
        super.onPause();
        dimmed = true;
        Log.d(TAG, "onPause");
        update();
        handler.removeCallbacks(tickTockRunnable);
    }

    private StringBuilder builder = new StringBuilder();

    private void update() {
        if (contentView != null) {
            long time = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            boolean amPm = !is24HourFormat(this);
            builder.setLength(0);
            builder.append(calendar.get(amPm ? Calendar.HOUR : Calendar.HOUR_OF_DAY));
            builder.append(':');
            int minutes = calendar.get(Calendar.MINUTE);
            if (minutes < 10) {
                builder.append('0');
            }
            builder.append(minutes);
            if (amPm) {
                timeSuffixView.setVisibility(View.VISIBLE);
                timeSuffixView.setText(calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
            } else {
                timeSuffixView.setVisibility(View.GONE);
            }
            if (!dimmed) {
                int seconds = calendar.get(Calendar.SECOND);
                secondsView.setText((seconds < 10 ? "0" : "") + seconds);
            } else {
                secondsView.setText("");
            }
            Log.d(TAG, "set time " + builder.toString() + " minutes " + minutes);
            timeView.setText(builder.toString());
            dateView.setText(SimpleDateFormat.getDateInstance(DateFormat.FULL).format(time));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        dimmed = true;
        unregisterReceiver(timeUpdateReceiver);
        handler.removeCallbacks(tickTockRunnable);
    }

}
