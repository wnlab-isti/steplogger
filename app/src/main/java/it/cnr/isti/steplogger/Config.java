package it.cnr.isti.steplogger;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {

    private static String LOG_TAG = Config.class.getName();
    private Properties configuration;

    public Config() {
        configuration = new Properties();
    }

    public String getLogFilesFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath().concat("/" + AppSettings.logFolder);
    }

    public String getConfigurationFile() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath().concat("/" + AppSettings.configFile);
    }

    public boolean load() {
        boolean retval = false;

        try {
            configuration.load(new FileInputStream(getConfigurationFile()));
            retval = true;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Configuration error: " + e.getMessage());
        }

        return retval;
    }

    public boolean store() {
        boolean retval = false;

        try {
            configuration.store(new FileOutputStream(getConfigurationFile()), null);
            retval = true;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Configuration error: " + e.getMessage());
        }

        return retval;
    }

    public void set(String key, String value) {
        configuration.setProperty(key, value);
    }

    public String get(String key) {
        return configuration.getProperty(key);
    }

}