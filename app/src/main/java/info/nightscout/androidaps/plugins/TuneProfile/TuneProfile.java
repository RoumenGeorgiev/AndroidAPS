package info.nightscout.androidaps.plugins.TuneProfile;

import android.provider.Settings;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
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

public class TuneProfile implements PluginBase {

    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;

    private static TuneProfile tuneProfile = null;
    private static Logger log = LoggerFactory.getLogger(TuneProfile.class);
    public static Profile profile;
    public List<BgReading> glucose_data;
    public List<BgReading> basalGlucose;
    public static List<Treatment> treatments;
    private JSONArray mIobData;
    private IobTotal iob;

    @Override
    public String getFragmentClass() {
        return TuneProfileFragment.class.getName();
    }

    @Override
    public String getName() { return "TuneProfile"; }

    @Override
    public String getNameShort() {
        String name = "TP";
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return !Config.NSCLIENT && !Config.G5UPLOADER;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return -1;
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    public static TuneProfile getPlugin() {
        if (tuneProfile == null)
            tuneProfile = new TuneProfile();
        return tuneProfile;
    }

    public void invoke(String initiator, boolean allowNotification) {
        // invoke
    }

    public void autotunePrep(){
        // funcion to prepare/get the data

        //get active profile
        if (MainApp.getConfigBuilder().getProfile() == null){
            log.debug(MainApp.sResources.getString(R.string.noprofileselected));

            profile = MainApp.getConfigBuilder().getProfile();
        }
        getGlucoseData();
    }

    public void getProfile(){
        //get active profile
        if (MainApp.getConfigBuilder().getProfile() == null){
            log.debug("TuneProfile: No profile selected");
            return;
        }
        profile = MainApp.getConfigBuilder().getProfile();
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

    public void getTreatments(){
        //get treatments 24h back
        double dia = MainApp.getConfigBuilder() == null ? Constants.defaultDIA : MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        treatments = MainApp.getDbHelper().getTreatmentDataFromTime(fromMills, false);
        //log.debug("Treatments size:"+treatments.size());
    }

    public static boolean carbsInTreatments(int hour){
        // return true if there is a treatment with carbs during this time
        Treatment tempTreatment = new Treatment();
        boolean carbs = false;
        // seconds from midnight
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        //long passed = now - c.getTimeInMillis();
        long milisMax = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, hour-1);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long milisMin = c.getTimeInMillis();
        getPlugin().getTreatments();
        for(int i=0;i<treatments.size(); i++){
            tempTreatment = treatments.get(i);
            if(tempTreatment.carbs > 0 && tempTreatment.date > milisMin && tempTreatment.date < milisMax)
                carbs = true;
        }

        return carbs;
    }

    public static Integer numberOfTreatments(){
        getPlugin().getTreatments();
        return treatments.size();
    }

    public String numberOfTreatments(int hour){
        return "";
    }

    public synchronized Integer averageGlucose(int hour){
        // initialize glucose_data
        getGlucoseData();
        if(hour > 24 || hour < 0){
            return 0;
        }

        if(glucose_data.size() < 1)
            // no BG data
            return 0;

        int counter = 0; // how many bg readings we have
        int avgGlucose = 0;
        // seconds from midnight
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        //long passed = now - c.getTimeInMillis();
        long milisMax = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, hour-1);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long milisMin = c.getTimeInMillis();

        for (int i = 1; i < glucose_data.size(); i++) {
            if (glucose_data.get(i).value > 38 && glucose_data.get(i).date < milisMax && glucose_data.get(i).date > milisMin) {
                avgGlucose += glucose_data.get(i).value;
                counter++;
                //log.debug("TuneProfile: avgGlucose is "+avgGlucose/counter);

            }
        }
        //getAutosensData(milisMax);
        //avoid division by 0
        if(counter == 0)
            counter = 1;
        return (int) (avgGlucose / counter);
    }

    public static synchronized String averageGlucoseString(int hour){
        Integer avgBG = getPlugin().averageGlucose(hour);
        //log.debug("TuneProfile: avgGlucose is "+avgBG);
        return avgBG.toString();
    }

    public static synchronized double getISF(int hour){
        getPlugin().getProfile();
        int toMgDl = 1;
        if(profile.equals(null))
            return 0d;
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        Double profileISF = (double) Math.round((profile.getIsf()*toMgDl*100)/100);
        //log.debug("TuneProfile: ISF is "+profileISF.toString());
        return profileISF;
    }

    public static synchronized Double getBasal(Integer hour){
        getPlugin().getProfile();
        if(profile.equals(null))
            return 0d;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return profile.getBasal(c.getTimeInMillis());
    }

    public double basalBGI(int hour){
        double currentBasal = profile.getBasal();
        double sens = getISF(hour);
        // basalBGI is BGI of basal insulin activity.
        double basalBGI = Math.round(( currentBasal * sens / 60 * 5 )*100)/100; // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
        return basalBGI;
    }

    public void getIOBdata(long bgTime){
        //IobTotal[] iobArray = IobCobCalculatorPlugin.calculateIobArrayInDia();
        IobTotal iob = IobCobCalculatorPlugin.calculateFromTreatmentsAndTemps(bgTime);
        //mIobData = IobCobCalculatorPlugin.convertToJSONArray(iobArray);
        //mIobData.toString();
    }

    public static AutosensData getAutosensData(long time){
        // first we need to go back in time
        AutosensData autosensData = IobCobCalculatorPlugin.getAutosensData(time);
        if(autosensData.equals(null)){
            log.debug("AutosensData is null!!!");
            return null;
        }
        //log.debug("Autosens"+autosensData.toString());
        //log.debug("Autosense:"+autosensData.log(time));
        return autosensData;
    }

    public static synchronized String getBasalIst(){
        getPlugin().getProfile();
        if(profile.equals(null))
            return "Profile is null";

        String basals = profile.getBasalList();

        return basals;
    }

    public static synchronized String getTargets(){
        getPlugin().getProfile();
        int toMgDl = 1;
        if(profile.equals(null))
            return "Profile is null";
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        String targets = ""+(((profile.getTargetLow() * toMgDl)*100)/100);

        return targets;
    }

    public static synchronized int getTargets(int hour){
        getPlugin().getProfile();
        int toMgDl = 1;
        if(profile.equals(null))
            return 0;
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        int targets = (int) (((profile.getTargetLow() * toMgDl)*100)/100);

        return targets;
    }


    public static String basicResult(){
        // get some info and spit out a suggestion
        int averageBG;
        double basal;
        double isf;
        double target;
        double result;
        String maxedOutFlag = "";
        String basicResult = "";
        for(int i=0; i<24; i++){
            // get average BG
            averageBG = getPlugin().averageGlucose(i);
            // get ISF
            isf =  getISF(i);
            //get basal
            basal =getBasal(i);
            // get target
            target = getTargets(i);
            // result should be basal -(average - target)/ISF units
            result = round((basal + ((averageBG-target)/isf)), 2);
            // if correction is more than 20% limit it to 20%
            if( result/basal > 1.2 ){
                result = basal * 1.2;
                maxedOutFlag = "(M)";
            } else if (result/basal < 0.8){
                result = basal * 0.8;
                maxedOutFlag = "(m)";
            } else
                maxedOutFlag = "";
            if (carbsInTreatments(i))
                maxedOutFlag += "(c)";
            if (averageBG >0){
                basicResult += "\n"+i+" dev is "+(averageBG-target)+" so "+basal+" should be "+round(result,2)+" U"+maxedOutFlag;
            } else
                basicResult += "\n"+i+" -- no data ";

        }

        return basicResult;
    }

    public static Integer secondsFromMidnight(long date) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long passed = date - c.getTimeInMillis();
        return (int) (passed / 1000);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
    // end of TuneProfile Plugin
}
