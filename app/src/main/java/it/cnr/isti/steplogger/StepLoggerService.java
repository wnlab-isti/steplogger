package it.cnr.isti.steplogger;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;

import java.io.File;

/**
 * this is a background-service that accepts external calls via an AIDL
 * those calls include: timestamp, x, y, z
 * every called is logged to a file named positions.log
 *
 * furthermore, the service provides a command to start a new measurement session.
 * this will create a new always-on-top UI that is clickable to mark waypoints.
 */
public class StepLoggerService extends Service {

    private static final String LOG_TAG = StepLoggerService.class.getName();

    /** the currently active logging session [if any!] */
    private LoggingSession logSession;

    /** the waypoint configuration */
    final Config configuration = new Config(this);

    @Override
    public void onCreate() {

        super.onCreate();
        Log.d(LOG_TAG, "onCreate()");

        // load the configuration
        configuration.load();

    }

    @Override
    public IBinder onBind(Intent intent) {

        // called when the FIRST consumer wants to access the service
        Log.d(LOG_TAG, "onBind()");
        return mBinder;

    }

    /** start a new logging-session be changing the current log-file-directory */
    public void startNewLog(final String uid) {

        // ensure everything is clean
        logSessionCleanup();

        // get the folder to log the current session to
        final String dirName = getTimestampString() + (uid.length() > 0 ? "_" + uid : "");
        final File logFileDir = new File(configuration.getLogFilesFolder(), dirName);
        if (!logFileDir.exists()) {logFileDir.mkdirs();}

        // create the new logging session [this will also create the overlay view]
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        logSession = new LoggingSession(this, logFileDir, wm, inflater, configuration);

    }

    /** called from the logSession when logging is complete [all waypoints processed] */
    protected void onLogDone() {
        logSession.destroy();
        logSession = null;
    }

    /** cleanup any active logging session */
    private void logSessionCleanup() {
        if (logSession != null) {
            logSession.destroy();
            logSession = null;
        }
    }

    @Override
    public void onDestroy() {

        // service cleanup. also remove the overlay view
        Log.d(LOG_TAG, "onDestroy()");
        logSessionCleanup();
        super.onDestroy();

    }



    /** the service-interface implementation */
    public final IStepLoggerService.Stub mBinder = new IStepLoggerService.Stub() {

        @Override
        public void logPosition(long timestamp, double x, double y, double z) throws RemoteException {

            // the client app sent its current location estimation -> log it to file
            Log.d(LOG_TAG, "Set logPosition: " + timestamp + ", " + x + ", " + y + ", " + z);

            // if there is currently an active session, pass it the position data!
            if (logSession != null) {
                logSession.logPosition(timestamp, x, y, z);
            }
            else {
                Log.d(LOG_TAG, "logSession == null");
            }

        }

        @Override
        public void startNewSession(final String uid) {

            Handler mainHandler = new Handler(Looper.getMainLooper());

            Runnable myRunnable = new Runnable() {
                @Override public void run() {startNewLog(uid);}
            };
            mainHandler.post(myRunnable);

        }

    };

    /** get the current timestamp formatted as string [YYYYMMDD]T[HHMMSS] */
    private static String getTimestampString() {
        Time now = new Time();
        now.setToNow();
        return now.format("%Y%m%dT%H%M%S");
    }

}


