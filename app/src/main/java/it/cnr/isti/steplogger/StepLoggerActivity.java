package it.cnr.isti.steplogger;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class StepLoggerActivity extends AppCompatActivity {

    private static final String LOG_TAG = StepLoggerActivity.class.getName();

    //private Button counterButton;
    //private int counterButtonVisibility = View.INVISIBLE;
    private String[] lines = null;
    //private String folder = "";
    public String uid = "";
    //private Integer index = 0;

    /**
     * the service that performes the background logging, wrapped within a helper class
     */
    StepLoggerServiceHelper service = null;


    private boolean testMode = false;
    private boolean recreatedActivity;

    private boolean welcomeScreenDialogShowing = false;
    private boolean userIdDialogShowing = false;
    private boolean configDialogShowing = false;

    private AlertDialog alert;
    private EditText inputUID;

    private Config configuration;

    @Override
    protected void onDestroy() {
        service.doUnbindService();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_steplogger);

        if((Build.VERSION.SDK_INT >= 23) && !Settings.canDrawOverlays(this)) {

            Log.d(LOG_TAG, "Build >= 23, Requesting permission to draw overlays");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            requestOverlayPermissionLauncher.launch(intent);
        }

        else {
            Log.d(LOG_TAG, "Checking permissions");
            checkPermissions();
        }

    }

    private void setup() {

        // start the step-logger-service
        service = new StepLoggerServiceHelper(this);

        // sanity check. ensure that the configuration.ini is accessible and valid
        configuration = new Config();
        configuration.load();
        lines = configuration.get("counter").split(",");

        // ensure all data folders are created and accessible
        this.ensureFoldersExist();


        if (configDialogShowing) showErrorConfigDialog(configuration.getConfigurationFile());


        PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        if (lines != null && !configDialogShowing) {
            if (!recreatedActivity || (recreatedActivity && welcomeScreenDialogShowing))
                this.showWelcomeDialog();
            // String buttonName  = lines[index].split(":")[0];
            if (userIdDialogShowing) startNewMeasuringSession();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissionsList[], int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissionsList, grantResults);
        for (int i = 0; i < permissionsList.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Log.d(LOG_TAG, "Permissions denied " + permissionsList[i]);
            } else {
                Log.d(LOG_TAG, "Permission granted " + permissionsList[i]);
            }
        }

        setup();
        return;
    }

    private void checkPermissions() {

        String[] permissions = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
        };


        Log.d(LOG_TAG, "Check permissions " + permissions.length);

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            int result = ContextCompat.checkSelfPermission(this, p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {

            Log.d(LOG_TAG, "Request permissions " + listPermissionsNeeded.size());

            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 12334);
        } else {

            Log.d(LOG_TAG, "No permissions needed.");
            setup();
        }

    }

    private ActivityResultLauncher<Intent> requestOverlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if(!Settings.canDrawOverlays(this)) {
                            Log.d(LOG_TAG, "Request denied. Exiting application.");
                            finishAndRemoveTask();
                        }
                        checkPermissions();
                    }
            );

    private void showErrorConfigDialog(String filename) {
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title_warning))
                .setMessage(String.format(getString(R.string.warning_content_config_ini_file),
                        filename))
                .setPositiveButton(getString(R.string.warning_title_exit),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                dialog.dismiss();
                                configDialogShowing = false;
                                finish();
                            }
                        })
                .setCancelable(false)
                .show();
    }

    /** ensure all data folders are created and accessible */
    private void ensureFoldersExist() {
        File logFolder = new File(configuration.getLogFilesFolder());
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        testMode = prefs.getBoolean("test_mode", false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_steplogger, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if(lines != null) {
            menu.findItem(R.id.menu_action_newsession).setEnabled(true);
            menu.findItem(R.id.menu_action_settings).setEnabled(true);
        } else {
            menu.findItem(R.id.menu_action_newsession).setVisible(false);
            menu.findItem(R.id.menu_action_settings).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(item.isEnabled()) {
            switch (id) {
                case R.id.menu_action_newsession:
                    this.startNewMeasuringSession();
                    break;

                case R.id.menu_action_settings:
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Se premo il tasto back chiedo conferma prima di uscire
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title_exit))
                .setMessage(String.format(getString(R.string.warning_content_exit_question)))
                .setPositiveButton(getString(R.string.button_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    // Registro le variabili per la Persistenza dello stato dell'app
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //outState.putInt("index", index);
        outState.putStringArray("lines", lines);
        //outState.putInt("counterButtonVisibility", counterButton.getVisibility());
        outState.putBoolean("testMode", testMode);
        //outState.putString("folder", folder);
        outState.putString("uid", userIdDialogShowing ? inputUID.getText().toString() : uid);
        outState.putBoolean("welcomeScreenDialogShowing", welcomeScreenDialogShowing);
        outState.putBoolean("userIdDialogShowing", userIdDialogShowing);
        outState.putBoolean("configDialogShowing", configDialogShowing);

        if(alert != null && alert.isShowing()) alert.dismiss();

        super.onSaveInstanceState(outState);
    }

    /*
    private static String getCurrentTimeStamp(Boolean isFolderLabel){
        Time now = new Time();
        now.setToNow();
        return "" + (isFolderLabel ? now.format("%Y%m%dT%H%M%S") : currentTimeMillis());
    }
    */

    private void showWelcomeDialog() {
        welcomeScreenDialogShowing = true;
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.welcomedialog_title))
                .setMessage(getString(R.string.welcomedialog_text))
                .setNeutralButton(R.string.menu_action_newsession, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        welcomeScreenDialogShowing = false;
                        startNewMeasuringSession();
                        dialog.dismiss();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        welcomeScreenDialogShowing = false;
                        dialog.dismiss();
                    }
                })
                .show();
    }


    private void startNewMeasuringSession() {
        inputUID = new EditText(this);
        inputUID.setSingleLine();
        inputUID.setBackgroundColor(Color.WHITE);
        inputUID.setTextColor(Color.BLACK);
        inputUID.append(uid);

        userIdDialogShowing = true;
        alert = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.uid_title))
                .setMessage(getString(R.string.uid_msg))
                .setView(inputUID)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        uid = inputUID.getText().toString();

                        if (!uid.isEmpty()) {
                            dialog.dismiss();

                            // start a new logging session
                            service.startNewLoggingSession(uid);
                            // shut-down [hide] the activity
                            StepLoggerActivity.this.finish();

                        }
                    }



                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        userIdDialogShowing = false;
                        dialog.dismiss();
                        return;
                    }
                })
                .show();

    }
}