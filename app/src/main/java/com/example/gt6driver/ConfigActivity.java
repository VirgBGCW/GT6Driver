// app/src/main/java/com/example/gt6driver/ConfigActivity.java
package com.example.gt6driver;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

public class ConfigActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new ConfigFragment())
                .commit();
    }

    public static class ConfigFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.prefs_config, rootKey);

            // Optional: show current defaults as summaries
            EditTextPreference pDirPrefix = findPreference("dir_prefix");            // e.g. "GT6"
            EditTextPreference pVideoName = findPreference("video_name");            // e.g. "intake"
            EditTextPreference pVinName   = findPreference("vin_name");              // e.g. "vin"
            EditTextPreference pKeyName   = findPreference("keycheck_name");         // e.g. "keycheck_intake"
            EditTextPreference pMilesName = findPreference("mileage_name");          // e.g. "mileage_intake"
            SwitchPreferenceCompat pTime  = findPreference("append_timestamp");

            if (pDirPrefix != null)  pDirPrefix.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            if (pVideoName != null)  pVideoName.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            if (pVinName != null)    pVinName.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            if (pKeyName != null)    pKeyName.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            if (pMilesName != null)  pMilesName.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
