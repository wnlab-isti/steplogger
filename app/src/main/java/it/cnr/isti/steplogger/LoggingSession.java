package it.cnr.isti.steplogger;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Timer;
import java.util.TimerTask;

/**
 * describes the currently active logging session that will log
 * - position updates
 * - button presses
 * into two files that reside within the same folder
 * this also creates a screen overlay,
 * that shows a clickable button,
 * that is always-on-top no matter which app is currently in foreground!
 * MUST be instantiated from a backgroundService to survive app-switching
 * TODO: open both logfiles once instead of opening/closing them over and over again
 */
public class LoggingSession {

    private static final String LOG_TAG = LoggingSession.class.getName();

    //ui
    private final WindowManager wm;
    private View overlayView;
    private Button counterButton;
    private TextView lblInfo;

    /** the service the logger belongs to */
    private final StepLoggerService service;

    private final FusedLocationProviderClient fusedLocationClient;
    private final CurrentLocationRequest currentLocationRequest;

    /** the folder (including the timestamp during time-of-start) to write log-files to */
    private final File logFileDir;

    /** parsed configuration lines */
    private final String[] lines;

    /** current waypoint index */
    private Integer index = 0;

    /** track the number of received position updates */
    private final Stats stats = new Stats();

    /** ctor */
    public LoggingSession(final StepLoggerService service, final File logFileDir, WindowManager wm, LayoutInflater inflater, Config configuration) {

        this.service = service;
        this.logFileDir = logFileDir;
        this.wm = wm;

        // split configuration file
        lines = configuration.get("counter").split(",");

        // create the overlay window
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        overlayView = inflater.inflate(R.layout.activity_steplogger_overlay, null);
        wm.addView(overlayView, params);

        // build the UI
        setupUi();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(overlayView.getContext());
        currentLocationRequest = new CurrentLocationRequest.Builder().
                setGranularity(Granularity.GRANULARITY_FINE).
                setPriority(Priority.PRIORITY_HIGH_ACCURACY).
                setMaxUpdateAgeMillis(200).
                setDurationMillis(5000).
                build();

        // shown an information that the logging starts
        final Context ctx = overlayView.getContext();
        Toast.makeText(ctx, "new logging session\n" + logFileDir, Toast.LENGTH_LONG).show();

    }

    /** must be called from the service when the logging session is complete */
    public void destroy() {

        if (overlayView != null) {
            wm.removeView(overlayView);
            overlayView = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void setupUi() {

        counterButton = (Button) overlayView.findViewById(R.id.counterButton);
        lblInfo = (TextView) overlayView.findViewById(R.id.lblInfo);

        // attach an event listener to the button
        counterButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        // get the entry-string to log for the current waypoint
                        final String[] logButton  = lines[index].split(":");


                        fusedLocationClient.getCurrentLocation(currentLocationRequest, null).
                                addOnSuccessListener(location -> {
                                            double longitude = Double.NaN;
                                            double latitude = Double.NaN;
                                            if(location != null) {
                                                longitude = location.getLongitude();
                                                latitude = location.getLatitude();
                                            }
                                            Log.d(LOG_TAG, "Location = " + longitude + ", " + latitude);

                                            // try to create a new log entry
                                            final boolean saveOK = (logCurrentWaypoint(
                                                    System.currentTimeMillis() + " : " + logButton[0]+ " : " + logButton[1] + " : " + logButton[2] + " : " +logButton[3]
                                                            + " : " + longitude + " : " + latitude
                                                            + "\n",
                                                    lines[index]));

                                            // if saving was OK, proceed with the next waypoint and block the button for some time
                                            if (saveOK) {
                                                index++;
                                                disableButtonForSomeTime();
                                            }

                                            // update the system state
                                            if(index < lines.length) {

                                                final String buttonName  = lines[index].split(":")[0];
                                                counterButton.setText(buttonName);

                                            } else {

                                                // logging complete. kill the service!
                                                loggingComplete();

                                            }

                                        }

                                );

                    }
                }
        );

        // start empty
        updateInfoLabel();
        resetCounter();

    }

    /** set the text to display within the info label */
    public void setInfoLabelText(final String txt) {
        lblInfo.setText(txt);
    }

    /** disable the waypoint-button for some time */
    private void disableButtonForSomeTime() {
        counterButton.setClickable(false);
        new Timer().schedule(new TimerTask() {
            @Override public void run() {
                counterButton.setClickable(true);
            }
        }, 2000);
    }

    /** helper method to get the context the logging session belongs to */
    private Context getContext() {
        return overlayView.getContext();
    }



    /** log the given position to file */
    protected boolean logPosition(final long timestmap, final double x, final double y, final double z) {

        // sanity check
        if (logFileDir == null) {throw new RuntimeException("log folder is null. should not happen!");}

        try {

            final File fileToWrite = new File(logFileDir, AppSettings.LOG_POSITION);
            final BufferedWriter bw = new BufferedWriter(new FileWriter(fileToWrite, true));
//            bw.write("A " + String.valueOf(timestmap) + " " + x + " " + y + " " + z + "\n");
            bw.write(String.valueOf(System.currentTimeMillis()) + " " + x + " " + y + " " + z + "\n");
            bw.flush();
            bw.close();
            MediaScannerConnection.scanFile(getContext(), new String[]{fileToWrite.getAbsolutePath()}, null, null);
            stats.inc();
            updateInfoLabel();
            return true;

        } catch (Exception e) {

            e.printStackTrace();
            Log.e(LOG_TAG, "error: " + e.getMessage());
            return false;

        }

    }

    /** log the given waypoint to file */
    private boolean logCurrentWaypoint(final String content, final String label){

        // sanity check
        if (logFileDir == null) {throw new RuntimeException("log folder is null. should not happen!");}

        try {

            final File fileToWrite = new File(logFileDir, AppSettings.LOG_STEPLOGGER);
            final BufferedWriter bw = new BufferedWriter(new FileWriter(fileToWrite, true));
            bw.write(content);
            bw.close();
            Log.d(LOG_TAG, fileToWrite.toURI()+" written");
            return true;

        } catch (Exception e) {

            Log.e(LOG_TAG, e.getMessage() != null ? e.getMessage() : "Error");
            e.printStackTrace();
            return false;

        }

    }

    /** when logging is complete [all waypoints processed] show an information and kill the service including the overlay view */
    private void loggingComplete() {

        // show an information
        Toast.makeText(getContext(), "logging complete!\n" + logFileDir, Toast.LENGTH_LONG).show();

        // inform the service the service
        service.onLogDone();

    }


    /** reset the logging process [not really needed, as the Session is killed after one walk is completed] */
    private void resetCounter() {
        index = 0;
        String buttonName  = lines[index].split(":")[0];
        counterButton.setText(buttonName);
    }



    /** update the info-label below the button that shows the number of received position updates */
    private void updateInfoLabel() {

        // must be called from the android main-thread
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(
            new Runnable() {
                @Override public void run() {

                    lblInfo.setText("Estimations: " + stats.getCount() + " @ " + stats.getUpdateRate() + " ms");
                }
            }
        );

    }

}


/** helper class to show some stats below the button */
class Stats {

    public int numTotal = 0;
    public int numWindow = 0;
    public long windowStartTS = System.currentTimeMillis();

    public void inc() {
        ++numTotal;

        if (numWindow > 10) {
            numWindow = 0;
            windowStartTS = System.currentTimeMillis();
        }
        ++numWindow;


    }

    public int getCount() {
        return numTotal;
    }

    public int getUpdateRate() {
        if (numWindow == 0) {return 0;}
        final long duration = System.currentTimeMillis() - windowStartTS;
        return (int) (duration / numWindow);
    }

}