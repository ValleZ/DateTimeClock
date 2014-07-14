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
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static android.text.format.DateFormat.is24HourFormat;


public final class FaceActivity extends Activity {

    private static final String TAG = "FaceActivity";
    private boolean dimmed;
    private View contentView;
    private View sunView;
    private TextView timeView, dateView, timeSuffixView, secondsView;

    final BroadcastReceiver timeUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive " + intent);
            update();
            if (Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
                locationUpdate();
            }
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
    private boolean isLocationKnown = true;
    private double latitude = 37.37;
    private double longitude = -122;
    private long lastSunPositionCalculatedTime;

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
        sunView = findViewById(R.id.sun);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                locationUpdate();
            }
        }, 24 * 3600 * 1000L);
        locationUpdate();
        update();
    }

    private void locationUpdate() {

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
            timeView.setText(builder.toString());
            dateView.setText(SimpleDateFormat.getDateInstance(DateFormat.FULL).format(time));
            if (isLocationKnown && contentView.getWidth() > 0) {
                if (Math.abs(time - lastSunPositionCalculatedTime) > 60000) {
                    lastSunPositionCalculatedTime = time;
                    double sunPosition = calcSunset(latitude, longitude);
                    if (sunPosition < 0) {
                        Log.d(TAG, "sun position " + sunPosition + " night");
                        sunView.setVisibility(View.INVISIBLE);
                    } else {
                        sunView.setVisibility(View.VISIBLE);
                        if (sunPosition > 1) {
                            sunPosition = 0.5;//arctic day
                        }
                        LinearLayout.LayoutParams lp = ((LinearLayout.LayoutParams) sunView.getLayoutParams());
                        int marginLeft = (int) (contentView.getWidth() * sunPosition - sunView.getWidth() / 2);
                        Log.d(TAG, "sun position " + sunPosition + " day offs " + marginLeft + " of " + contentView.getWidth() + " old " + lp.getMarginStart());
                        lp.setMargins(marginLeft, 0, 0, 0);
                        sunView.setLayoutParams(lp);
                    }
                }
            } else {
                sunView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private static double calcSunset(double latitude, double longitude) {
        long unixTime = System.currentTimeMillis();
        longitude = -longitude;//west should be positive
        double julianDate = unixTime / 86400000.0 + 2440587.5;
        long julianCycle = (long) Math.floor(julianDate - 2451545.0009 - longitude / 360 + 0.5);
        double solarNoonApprox = 2451545.0009 + longitude / 360 + julianCycle;
        double meanAnomaly = (357.52911 + 0.98560028 * (solarNoonApprox - 2451545)) % 360;
        double meanAnomalySin = Math.sin(meanAnomaly * Math.PI / 180);
        double center = 1.9148 * meanAnomalySin
                + 0.0200 * Math.sin(2 * meanAnomaly * Math.PI / 180)
                + 0.0003 * Math.sin(3 * meanAnomaly * Math.PI / 180);
        double eclipticalLongitudeRad = ((meanAnomaly + 102.9372 + center + 180) % 360) * Math.PI / 180;
        double setDiff = 0.0053 * meanAnomalySin - 0.0069 * Math.sin(2 * eclipticalLongitudeRad);
        double solarNoon = solarNoonApprox + setDiff;
        double sunDeclinationRad = Math.asin(Math.sin(eclipticalLongitudeRad) * Math.sin(23.45 * Math.PI / 180));
        double solarDeclinationSin = Math.sin(sunDeclinationRad);
        double solarDeclinationCos = Math.cos(sunDeclinationRad);
        double latitudeCos = Math.cos(latitude * Math.PI / 180);
        double latitudeSin = Math.sin(latitude * Math.PI / 180);
        double sunSizeRad = 0.83 * Math.PI / 180;
        double hourAngleCos = (Math.sin(-sunSizeRad) - latitudeSin * solarDeclinationSin)
                / (latitudeCos * solarDeclinationCos);
        if (hourAngleCos < -1) {
            return Double.MAX_VALUE;
        } else if (hourAngleCos > 1) {
            return Double.MIN_VALUE;
        } else {
            double hourAngle = Math.acos(hourAngleCos) * 180 / Math.PI;//half of the arc length of the sun at this latitude at this declination of the sun
            double solarNoon2 = 2451545.0009 + ((hourAngle + longitude) / 360) + julianCycle;
            double setTimeJulian = solarNoon2 + setDiff;
            double riseTimeJulian = solarNoon - (setTimeJulian - solarNoon);
            if (julianDate >= riseTimeJulian && julianDate <= setTimeJulian) {
                return (julianDate - riseTimeJulian) / (setTimeJulian - riseTimeJulian);
            } else {
                //night
                return -1;
            }
        }
    }

    static String formatJulian(double julianDate) {
        return DateFormat.getDateTimeInstance().format((julianDate - 2440587.5) * 86400000);
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
