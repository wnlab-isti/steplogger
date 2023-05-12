package it.cnr.isti.steplogger;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by federico on 20/04/15.
 */
public class SettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
    }
}
