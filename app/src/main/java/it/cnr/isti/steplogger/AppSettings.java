package it.cnr.isti.steplogger;

import android.os.Environment;

import java.io.File;


/**
 * contains all used constants
 */
public class AppSettings {

    /** the config.ini file */

//    public static final String logFolder = "";
    public static final String logFolder = "it.cnr.isti.steplogger";


//    public static final String configFile = "config.ini";
    public static final String configFile = "it.cnr.isti.steplogger.config.ini";

    /** filename to log button-pressing to */
    public static final String LOG_STEPLOGGER = "buttonsPressed.log";

    /** filename to log position callbacks to */
    public static final String LOG_POSITION = "positions.log";

}
