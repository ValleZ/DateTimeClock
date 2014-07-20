package ru.valle.datetimeclock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static android.text.format.DateFormat.is24HourFormat;


public final class FaceActivity extends Activity {

    private static final String TAG = "FaceActivity";
    private boolean dimmed;
    private SurfaceView surfaceView;

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
    private boolean isLocationKnown = true;
    private double latitude = 37.37;
    private double longitude = -122;
    private long lastSunPositionCalculatedTime;
    private final View.OnLayoutChangeListener layoutListener = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            update();
        }
    };
    private SurfaceHolder surfaceHolder;
    private final Paint timePaint = new Paint();
    private final Paint amPmPaint = new Paint();
    private final Paint secondsPaint = new Paint();
    private final Paint datePaint = new Paint();
    private final Paint sunPaint = new Paint();
    private final Rect boundsRect = new Rect();
    private float pmWidth;
    private float timeGapSize, dateGapSize;
    private Bitmap sunBitmap;
    private double sunPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.surfaced_face);
        handler = new Handler();
        surfaceView = (SurfaceView) findViewById(R.id.content);
        surfaceView.addOnLayoutChangeListener(layoutListener);
        surfaceHolder = surfaceView.getHolder();
        Typeface clockTypeface = Typeface.createFromAsset(getAssets(), "AndroidClock.ttf");
        timePaint.setTypeface(clockTypeface);
        timePaint.setColor(getResources().getColor(R.color.white));
        timePaint.setTextSize(dp2px(50));
        timePaint.setAntiAlias(true);
        amPmPaint.setColor(getResources().getColor(R.color.light_gray));
        amPmPaint.setTextSize(dp2px(18));
        amPmPaint.setAntiAlias(true);
        secondsPaint.setColor(getResources().getColor(R.color.white));
        secondsPaint.setTextSize(dp2px(18));
        secondsPaint.setAntiAlias(true);
        datePaint.setColor(getResources().getColor(R.color.white));
        datePaint.setTextSize(dp2px(16));
        datePaint.setAntiAlias(true);
        sunPaint.setAntiAlias(true);
        pmWidth = amPmPaint.measureText("PM");
        timeGapSize = dp2px(8);
        dateGapSize = dp2px(16);
        GradientDrawable sunShape = (GradientDrawable) getResources().getDrawable(R.drawable.sun);
        int sunSize = dp2px(8);
        sunShape.setBounds(0, 0, sunSize, sunSize);
        sunBitmap = Bitmap.createBitmap(sunSize, sunSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sunBitmap);
        sunShape.draw(canvas);
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
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
        if (surfaceHolder != null && surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                draw(canvas);
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void draw(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        long time = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        boolean amPm = !is24HourFormat(this);
        builder.setLength(0);
        int minutes = calendar.get(Calendar.MINUTE);
        if (amPm) {
            int hour = calendar.get(Calendar.HOUR);
            if (hour == 0) {
                hour = 12;
            }
            builder.append(hour);
        } else {
            builder.append(calendar.get(Calendar.HOUR_OF_DAY));
        }
        builder.append(':');

        if (minutes < 10) {
            builder.append('0');
        }
        builder.append(minutes);
        String timeStr = builder.toString();
        timePaint.getTextBounds(timeStr, 0, timeStr.length(), boundsRect);

        float timeWidth = boundsRect.width();
        float timeHeight = boundsRect.height();
        float timeTop = dp2px(18);
        float timeBaseline = timeTop + timeHeight;
        float secondsX = canvas.getWidth() - pmWidth - dp2px(4);
        float timeStart = secondsX - timeWidth - timeGapSize;
        canvas.drawText(timeStr, timeStart, timeBaseline, timePaint);
        if (amPm) {
            String amPmStr = calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
            canvas.drawText(amPmStr, secondsX, timeBaseline, amPmPaint);
        }
        if (!dimmed) {
            int seconds = calendar.get(Calendar.SECOND);
            String secondsStr = (seconds < 10 ? "0" : "") + seconds;
            float secondsWidth = secondsPaint.measureText(secondsStr);
            canvas.drawText(secondsStr, secondsX + (pmWidth - secondsWidth) / 2, timeTop + secondsPaint.getTextSize() - secondsPaint.descent(), secondsPaint);
        }
        String dateStr = SimpleDateFormat.getDateInstance(DateFormat.FULL).format(time);
        float dateWidth = datePaint.measureText(dateStr);
        canvas.drawText(dateStr, (canvas.getWidth() - dateWidth) / 2, timeBaseline + datePaint.getTextSize() + dateGapSize, datePaint);

        if (isLocationKnown && canvas.getWidth() > 0) {
            if (Math.abs(time - lastSunPositionCalculatedTime) > 60000) {
                lastSunPositionCalculatedTime = time;
                sunPosition = calcSunset(latitude, longitude);
                if (sunPosition >= 0) {
                    if (sunPosition > 1) {
                        sunPosition = 0.5;//arctic day
                    }
                }
            }
            canvas.drawBitmap(sunBitmap, (float) (canvas.getWidth() * sunPosition - sunBitmap.getWidth()),
                    timeBaseline + (dateGapSize - sunBitmap.getHeight()), sunPaint);
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
