package it.cnr.isti.steplogger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * starts the StepLoggerService
 * and provides a convenience method to advice the service to start a new logging session
 */
public class StepLoggerServiceHelper {

    private IStepLoggerService mService;
    private final Context ctx;

    /** ctor */
    public StepLoggerServiceHelper(final Context ctx) {
        this.ctx = ctx;
        doBindService();
    }

    /** dtor */
    public void finalize() {
        doUnbindService();
    }


    /** send the Service an event to start a new logging session */
    public void startNewLoggingSession(final String uid) {
        try {
            mService.startNewSession(uid);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }





    /** connect/disconnect events for the service */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IStepLoggerService.Stub.asInterface(service);
        }
        @Override public void onServiceDisconnected(ComponentName name) {

        }
    };

    /** start and bind the StepLoggerService */
    private void doBindService() {

        // very important! just binding the service would lead to a service-destroy
        // after our activity goes out of scope.
        // separately starting the service keeps him active "forever"
        final Intent intent1 = new Intent(ctx, StepLoggerService.class);
        ctx.startService(intent1);

        // also bind to the service as we want to sent events to the service
        final Intent intent2 = new Intent(ctx, StepLoggerService.class);
        ctx.bindService(intent2, mConnection, Context.BIND_AUTO_CREATE);


    }

    /** cleanup */
    void doUnbindService() {
        ctx.unbindService(mConnection);
    }

}
