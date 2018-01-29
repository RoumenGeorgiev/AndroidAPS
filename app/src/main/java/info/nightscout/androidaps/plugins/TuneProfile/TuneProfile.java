package info.nightscout.androidaps.plugins.TuneProfile;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Created by Rumen Georgiev on 1/29/2018.
 ideia is to port Autotune from OpenAPS to java
 let's start by taking 1 day of data from NS, and comparing it to ideal BG
 # defaults
 CURL_FLAGS="--compressed"
 DIR=""
 NIGHTSCOUT_HOST=""
 START_DATE=""
 END_DATE=""
 START_DAYS_AGO=1  # Default to yesterday if not otherwise specified
 END_DAYS_AGO=1  # Default to yesterday if not otherwise specified
 EXPORT_EXCEL="" # Default is to not export to Microsoft Excel
 TERMINAL_LOGGING=true
 CATEGORIZE_UAM_AS_BASAL=false
 RECOMMENDS_REPORT=true
 UNKNOWN_OPTION=""
 FIRST WE NEED THE DATA PREPARATION
 -- oref0 autotuning data prep tool
 -- Collects and divides up glucose data for periods dominated by carb absorption,
 -- correction insulin, or basal insulin, and adds in avgDelta and deviations,
 -- for use in oref0 autotuning algorithm
 -- get glucoseData and sort it
 -- get profile
 -- get treatments

 */

public class TuneProfile {
    private static Logger log = LoggerFactory.getLogger(TuneProfile.class);
    public Profile profile;
    public List<BgReading> glucose_data;
    
    public void autotunePrep(){
        // funcion to prepare/get the data

        //get active profile
        if (MainApp.getConfigBuilder().getProfile() == null){
            log.debug(MainApp.sResources.getString(R.string.noprofileselected));

            profile = MainApp.getConfigBuilder().getProfile();
        }
        getGlucoseData();
    }


    public void getGlucoseData() {
        //get glucoseData for 1 day back
        long oneDayBack = System.currentTimeMillis() - 24 * 60 * 60 *1000L;
        glucose_data = MainApp.getDbHelper().getBgreadingsDataFromTime(oneDayBack, false);
        if(glucose_data.size() < 1)
            // no BG data
            return;

        for (int i = 1; i < glucose_data.size(); i++) {
            if (glucose_data.get(i).value > 38) {

            }
        }

    }
}
