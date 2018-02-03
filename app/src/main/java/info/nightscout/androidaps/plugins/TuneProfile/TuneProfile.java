package info.nightscout.androidaps.plugins.TuneProfile;

import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.TuneProfile.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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
    public static List<Double> tunedBasals = new ArrayList<Double>();
    public List<BgReading> glucose_data = new ArrayList<BgReading>();
    public List<BgReading> basalGlucose;
    public static List<Treatment> treatments;
    private JSONArray mIobData;
    private IobTotal iob;
    private static final Object dataLock = new Object();

    //cpied from IobCobCalculator
    private static LongSparseArray<IobTotal> iobTable = new LongSparseArray<>(); // oldest at index 0
    private static volatile List<BgReading> bgReadings = null; // newest at index 0
    private static volatile List<BgReading> bucketed_data = null;
    private static LongSparseArray<AutosensData> autosensDataTable = new LongSparseArray<>(); // oldest at index 0
    List<info.nightscout.androidaps.plugins.TuneProfile.AutosensData.CarbsInPast> activeCarbsList = new ArrayList<>();

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


    public void getProfile(){
        //get active profile
        if (MainApp.getConfigBuilder().getProfile() == null){
            log.debug("TuneProfile: No profile selected");
            return;
        }
        profile = MainApp.getConfigBuilder().getProfile();
    }

    public void getGlucoseData(long start, long end) {
        //get glucoseData for 1 day back
        long oneDayBack = end - 24 * 60 * 60 *1000L;
        glucose_data = MainApp.getDbHelper().getBgreadingsDataFromTime(oneDayBack, false);
        log.debug("CheckPoint 12-1 - glucose_data.size is "+glucose_data.size()+" from "+new Date(oneDayBack).toString()+" to "+new Date(end).toString());
        int initialSize = glucose_data.size();
        if(glucose_data.size() < 1) {
            // no BG data
            log.debug("CheckPoint 12-4 - No BG data");
            return;
        }
        for (int i = 1; i < glucose_data.size(); i++) {
            if (glucose_data.get(i).value > 38) {
                if(glucose_data.get(i).date > end || glucose_data.get(i).date == glucose_data.get(i-1).date)
                    glucose_data.remove(i);
            }
        }
        log.debug("CheckPoint 12-2 - glucose_data.size is "+glucose_data.size()+"("+initialSize+") from "+new Date(oneDayBack).toString()+" to "+new Date(end).toString());
    }

    public void getTreatments(long end){
        //get treatments 24h back
        //double dia = MainApp.getConfigBuilder() == null ? Constants.defaultDIA : MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (end - 60 * 60 * 1000L * 24);
        //TODO from is OK but TO is not set
        treatments = MainApp.getDbHelper().getTreatmentDataFromTime(fromMills, true);
        // if treatment.date > end - remove treatment from list
        int treatmentsRemoved = 0;
        for(int i=0;i<treatments.size(); i++){
            if(treatments.get(i).date > end) {
                treatments.remove(i);
                treatmentsRemoved++;
            }
        }
        log.debug(treatmentsRemoved+ " treatments removed");
        //log.debug("Treatments size:"+treatments.size());
    }

    public static boolean carbsInTreatments(long start, long end){
        // return true if there is a treatment with carbs during this time

        Treatment tempTreatment = new Treatment();
        Date tempDate;
        boolean carbs = false;
        long milisMax = end;
        long milisMin = start;
            getPlugin().getTreatments(end);
        for(int i=0;i<treatments.size(); i++){
            tempTreatment = treatments.get(i);
            tempDate = new Date((tempTreatment.date));
            if(tempTreatment.carbs > 0 && tempTreatment.date > milisMin && tempTreatment.date < milisMax)
                carbs = true;
        }
        Date from = new Date(milisMin);
        Date to = new Date(milisMax);
        //log.debug("check for carbs from "+((System.currentTimeMillis() - milisMin)/(60*60*1000L))+" to "+((System.currentTimeMillis() - milisMax)/(60*60*1000))+" - "+carbs+"("+treatments.size()+"");
        //log.debug("check for carbs from "+from.toString()+" to "+to.toString()+" - "+carbs+"("+treatments.size()+"");
        return carbs;
    }

    public static Integer numberOfTreatments(long start, long end){
        getPlugin().getTreatments(end);
        return treatments.size();
    }

// Mass copy of IobCobCalculator functions
    private void createBucketedData(long endTime) {
        log.debug("CheckPoint 6 - entered createBucketedData");
        if (isAbout5minData()) {
            log.debug("CheckPoint 7");
            createBucketedData5min(endTime);
        } else
            log.debug("CheckPoint 8");
            createBucketedDataRecalculated();
    }

    private boolean isAbout5minData() {
        synchronized (dataLock) {
            if (bgReadings == null || bgReadings.size() < 3) {
                return true;
            }
            long totalDiff = 0;
            for (int i = 1; i < bgReadings.size(); ++i) {
                long bgTime = bgReadings.get(i).date;
                long lastbgTime = bgReadings.get(i - 1).date;
                long diff = lastbgTime - bgTime;
                totalDiff += diff;
                if (diff > 30 * 1000 && diff < 270 * 1000) { // 0:30 - 4:30
                    log.debug("Interval detection: values: " + bgReadings.size() + " diff: " + (diff / 1000) + "sec is5minData: " + false);
                    return false;
                }
            }
            double intervals = totalDiff / (5 * 60 * 1000d);
            double variability = Math.abs(intervals - Math.round(intervals));
            boolean is5mindata = variability < 0.02;
            log.debug("Interval detection: values: " + bgReadings.size() + " variability: " + variability + " is5minData: " + is5mindata);
            return is5mindata;
        }
    }

    private void createBucketedDataRecalculated(long startTime, long endTime) {
        // Created by Rumen for timesensitive autosensCalc

        synchronized (dataLock) {
            if (glucose_data == null || glucose_data.size() < 3) {

                bucketed_data = null;
                log.debug("CheckPoint 10 - no glucose_data");
                return;
            }
            log.debug("CheckPoint 11");
            bucketed_data = new ArrayList<>();
            long currentTime = glucose_data.get(0).date + 5 * 60 * 1000 - glucose_data.get(0).date % (5 * 60 * 1000) - 5 * 60 * 1000L;
            //log.debug("First reading: " + new Date(currentTime).toLocaleString());

            while (true) {
                // test if current value is older than current time
                BgReading newer = findNewer(glucose_data, currentTime);
                BgReading older = findOlder(glucose_data, currentTime);
                if (newer == null || older == null)
                    break;

                double bgDelta = newer.value - older.value;
                long timeDiffToNew = newer.date - currentTime;

                double currentBg = newer.value - (double) timeDiffToNew / (newer.date - older.date) * bgDelta;
                BgReading newBgreading = new BgReading();
                newBgreading.date = currentTime;
                newBgreading.value = Math.round(currentBg);
                bucketed_data.add(newBgreading);
                //log.debug("BG: " + newBgreading.value + " (" + new Date(newBgreading.date).toLocaleString() + ") Prev: " + older.value + " (" + new Date(older.date).toLocaleString() + ") Newer: " + newer.value + " (" + new Date(newer.date).toLocaleString() + ")");
                currentTime -= 5 * 60 * 1000L;

            }
        }
    }

    private void createBucketedDataRecalculated() {
        synchronized (dataLock) {
            if (bgReadings == null || bgReadings.size() < 3) {

                bucketed_data = null;
                log.debug("CheckPoint 10 - ng bg");
                return;
            }
            log.debug("CheckPoint 11");
            bucketed_data = new ArrayList<>();
            long currentTime = bgReadings.get(0).date + 5 * 60 * 1000 - bgReadings.get(0).date % (5 * 60 * 1000) - 5 * 60 * 1000L;
            //log.debug("First reading: " + new Date(currentTime).toLocaleString());

            while (true) {
                // test if current value is older than current time
                BgReading newer = findNewer(bgReadings, currentTime);
                BgReading older = findOlder(bgReadings, currentTime);
                if (newer == null || older == null)
                    break;

                double bgDelta = newer.value - older.value;
                long timeDiffToNew = newer.date - currentTime;

                double currentBg = newer.value - (double) timeDiffToNew / (newer.date - older.date) * bgDelta;
                BgReading newBgreading = new BgReading();
                newBgreading.date = currentTime;
                newBgreading.value = Math.round(currentBg);
                bucketed_data.add(newBgreading);
                //log.debug("BG: " + newBgreading.value + " (" + new Date(newBgreading.date).toLocaleString() + ") Prev: " + older.value + " (" + new Date(older.date).toLocaleString() + ") Newer: " + newer.value + " (" + new Date(newer.date).toLocaleString() + ")");
                currentTime -= 5 * 60 * 1000L;

            }
        }
    }

    public void createBucketedData5min(long endTime) {
        //log.debug("Locking createBucketedData");
        bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime((endTime - (24*60*60*1000L)), false);
        log.debug("CheckPoint 9 - bgData got");

        synchronized (dataLock) {
            if (bgReadings == null || bgReadings.size() < 3) {
                log.debug("CheckPoint 2 - no bgReadings");
                bucketed_data = null;
                return;
            }

            bucketed_data = new ArrayList<>();
            bucketed_data.add(bgReadings.get(0));
            log.debug("CheckPoint 3");
            int j = 0;
            for (int i = 1; i < bgReadings.size(); ++i) {
                long bgTime = bgReadings.get(i).date;
                long lastbgTime = bgReadings.get(i - 1).date;
                //log.error("Processing " + i + ": " + new Date(bgTime).toString() + " " + bgReadings.get(i).value + "   Previous: " + new Date(lastbgTime).toString() + " " + bgReadings.get(i - 1).value);
                if (bgReadings.get(i).value < 39 || bgReadings.get(i - 1).value < 39) {
                    continue;
                }

                long elapsed_minutes = (bgTime - lastbgTime) / (60 * 1000);
                if (Math.abs(elapsed_minutes) > 8) {
                    // interpolate missing data points
                    double lastbg = bgReadings.get(i - 1).value;
                    elapsed_minutes = Math.abs(elapsed_minutes);
                    //console.error(elapsed_minutes);
                    long nextbgTime;
                    while (elapsed_minutes > 5) {
                        nextbgTime = lastbgTime - 5 * 60 * 1000;
                        j++;
                        BgReading newBgreading = new BgReading();
                        newBgreading.date = nextbgTime;
                        double gapDelta = bgReadings.get(i).value - lastbg;
                        //console.error(gapDelta, lastbg, elapsed_minutes);
                        double nextbg = lastbg + (5d / elapsed_minutes * gapDelta);
                        newBgreading.value = Math.round(nextbg);
                        //console.error("Interpolated", bucketed_data[j]);
                        bucketed_data.add(newBgreading);
                        //log.error("******************************************************************************************************* Adding:" + new Date(newBgreading.date).toString() + " " + newBgreading.value);

                        elapsed_minutes = elapsed_minutes - 5;
                        lastbg = nextbg;
                        lastbgTime = nextbgTime;
                    }
                    j++;
                    BgReading newBgreading = new BgReading();
                    newBgreading.value = bgReadings.get(i).value;
                    newBgreading.date = bgTime;
                    bucketed_data.add(newBgreading);
                    //log.error("******************************************************************************************************* Copying:" + new Date(newBgreading.date).toString() + " " + newBgreading.value);
                } else if (Math.abs(elapsed_minutes) > 2) {
                    j++;
                    BgReading newBgreading = new BgReading();
                    newBgreading.value = bgReadings.get(i).value;
                    newBgreading.date = bgTime;
                    bucketed_data.add(newBgreading);
                    //log.error("******************************************************************************************************* Copying:" + new Date(newBgreading.date).toString() + " " + newBgreading.value);
                } else {
                    bucketed_data.get(j).value = (bucketed_data.get(j).value + bgReadings.get(i).value) / 2;
                    //log.error("***** Average");
                }
            }
            log.debug("Bucketed data created. Size: " + bucketed_data.size());
        }
        //log.debug("Releasing createBucketedData");
    }

    @Nullable
    private BgReading findNewer(List<BgReading> bgReadings,long time) {
        BgReading lastFound = bgReadings.get(0);
        if (lastFound.date < time) return null;
        for (int i = 1; i < bgReadings.size(); ++i) {
            if (bgReadings.get(i).date > time) continue;
            lastFound = bgReadings.get(i);
            if (bgReadings.get(i).date < time) break;
        }
        return lastFound;
    }

    @Nullable
    private BgReading findOlder(List<BgReading> bgReadings, long time) {
        BgReading lastFound = bgReadings.get(bgReadings.size() - 1);
        if (lastFound.date > time) return null;
        for (int i = bgReadings.size() - 2; i >= 0; --i) {
            if (bgReadings.get(i).date < time) continue;
            lastFound = bgReadings.get(i);
            if (bgReadings.get(i).date > time) break;
        }
        return lastFound;
    }


    public synchronized Integer averageGlucose(long start, long end){
        // initialize glucose_data
        if(glucose_data.size() < 2)
            getGlucoseData(start, end);
        log.debug("CheckPoint 12-4 glucose_data.size is "+glucose_data.size()+" from "+new Date(start).toString()+" to "+new  Date(end).toString());
        if(glucose_data.size() < 1)
            // no BG data
            return 0;

        int counter = 0; // how many bg readings we have
        int avgGlucose = 0;
        long milisMax = end;
        long milisMin = start;
        trimGlucose(start, end);
        for (int i = 1; i < glucose_data.size(); i++) {
            if (glucose_data.get(i).value > 38 || glucose_data.get(i).date < milisMax || glucose_data.get(i).date > milisMin) {
                if(glucose_data.get(i).date == glucose_data.get(i-1).date)
                    log.debug("CheckPoint 12-5 We have duplicate");
                 else {
                     avgGlucose += glucose_data.get(i).value;
                     counter++;
                }

                //log.debug("TuneProfile: avgGlucose is "+avgGlucose/counter);

            }
        }
        //getAutosensData(milisMax);
        //avoid division by 0
        if(counter == 0)
            counter = 1;
        return (int) (avgGlucose / counter);
    }

    public synchronized void trimGlucose(long start, long end){
        // initialize glucose_data
        if(glucose_data.size() == 0)
            getGlucoseData(start, end);
        if(glucose_data.size() < 1)
            // no BG data
            return ;

        long milisMax = end;
        long milisMin = start;

        for (int i = 1; i < glucose_data.size(); i++) {
            if (glucose_data.get(i).value > 38 || glucose_data.get(i).date < milisMax || glucose_data.get(i).date > milisMin) {
                if(glucose_data.get(i).date == glucose_data.get(i-1).date){
                    log.debug("CheckPoint 12-5 We have duplicate");
                }
                glucose_data.remove(i);
            }
        }
        log.debug("CheckPoint 12-6 size after trim is "+glucose_data.size());
        return;
    }


    public static synchronized double getISF(){
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
        double sens = getISF();
        // basalBGI is BGI of basal insulin activity.
        double basalBGI = Math.round(( currentBasal * sens / 60 * 5 )*100)/100; // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
        return basalBGI;
    }

    public double basalBGI(long time){
        double currentBasal = profile.getBasal();
        double sens = getISF();
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

    @Nullable
    public static AutosensData getAutosensData(long time) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            if (time > now)
                return null;
            Long previous = findPreviousTimeFromBucketedData(time);
            if (previous == null) {
                log.debug("CheckPoint 4");
                return null;
            }
            time = roundUpTime(previous);
            AutosensData data = autosensDataTable.get(time);
            if (data != null) {
                log.debug("CheckPoint 4-1 - autosensDataTable.get("+new Date(time).toString()+") is "+data.autosensRatio);
                //log.debug(">>> getAutosensData Cache hit " + data.log(time));
                return data;
            } else {
                if (time > now) {
                    // data may not be calculated yet, use last data
                    return getLastAutosensData();
                }
                //log.debug(">>> getAutosensData Cache miss " + new Date(time).toLocaleString());
                log.debug("CheckPoint 5 - autosensDataTable.get("+new Date(time).toString()+") is null");
                return null;
            }
        }
    }

    public static long roundUpTime(long time) {
        if (time % 60000 == 0)
            return time;
        long rouded = (time / 60000 + 1) * 60000;
        return rouded;
    }

    public static long oldestDataAvailable() {
        long now = System.currentTimeMillis();

        long oldestDataAvailable = MainApp.getConfigBuilder().oldestDataAvailable();
        long getBGDataFrom = Math.max(oldestDataAvailable, (long) (now - 60 * 60 * 1000L * (24 + MainApp.getConfigBuilder().getProfile().getDia())));
        log.debug("CheckPoint 7-13 Limiting data to oldest available temps: " + new Date(oldestDataAvailable).toString());
        return getBGDataFrom;
    }

    public static IobTotal calculateFromTreatmentsAndTemps(long time) {
        long now = System.currentTimeMillis();
        time = roundUpTime(time);
        if (time < now && iobTable.get(time) != null) {
            //og.debug(">>> calculateFromTreatmentsAndTemps Cache hit " + new Date(time).toLocaleString());
            return iobTable.get(time);
        } else {
            //log.debug(">>> calculateFromTreatmentsAndTemps Cache miss " + new Date(time).toLocaleString());
        }
        IobTotal bolusIob = MainApp.getConfigBuilder().getCalculationToTimeTreatments(time).round();
        IobTotal basalIob = MainApp.getConfigBuilder().getCalculationToTimeTempBasals(time).round();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();
        if (time < System.currentTimeMillis()) {
            iobTable.put(time, iobTotal);
        }
        return iobTotal;
    }

    private static AutosensResult detectSensitivity(long fromTime, long toTime) {
        return getPlugin().detectSensitivityOref0(fromTime, toTime);
    }

    public static LongSparseArray<AutosensData> getAutosensDataTable() {
        return autosensDataTable;
    }

    public AutosensResult detectSensitivityOref0(long fromTime, long toTime) {
        LongSparseArray<AutosensData> autosensDataTable = getPlugin().getAutosensDataTable();

        String age = SP.getString(R.string.key_age, "");
        int defaultHours = 24;
        if (age.equals(MainApp.sResources.getString(R.string.key_adult))) defaultHours = 24;
        if (age.equals(MainApp.sResources.getString(R.string.key_teenage))) defaultHours = 24;
        if (age.equals(MainApp.sResources.getString(R.string.key_child))) defaultHours = 24;
        int hoursForDetection = SP.getInt(R.string.key_openapsama_autosens_period, defaultHours);

        long now = System.currentTimeMillis();

        if (autosensDataTable == null || autosensDataTable.size() < 4) {
            log.debug("No autosens data available");
            return new AutosensResult();
        }

        info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData current = IobCobCalculatorPlugin.getLastAutosensData();
        if (current == null) {
            log.debug("No current autosens data available");
            return new AutosensResult();
        }


        List<Double> deviationsArray = new ArrayList<>();
        String pastSensitivity = "";
        int index = 0;
        while (index < autosensDataTable.size()) {
            AutosensData autosensData = autosensDataTable.valueAt(index);

            if (autosensData.time < fromTime) {
                index++;
                continue;
            }

            if (autosensData.time > toTime) {
                index++;
                continue;
            }

            if (autosensData.time > toTime - hoursForDetection * 60 * 60 * 1000L)
                deviationsArray.add(autosensData.nonEqualDeviation ? autosensData.deviation : 0d);
            if (deviationsArray.size() > hoursForDetection * 60 / 5)
                deviationsArray.remove(0);

            pastSensitivity += autosensData.pastSensitivity;
            int secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time);
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + Math.round(secondsFromMidnight / 3600d) + ")";
            }
            index++;
        }

        Double[] deviations = new Double[deviationsArray.size()];
        deviations = deviationsArray.toArray(deviations);

        Profile profile = MainApp.getConfigBuilder().getProfile();

        double sens = profile.getIsf();

        double ratio = 1;
        String ratioLimit = "";
        String sensResult = "";

        log.debug("Records: " + index + "   " + pastSensitivity);
        Arrays.sort(deviations);

        for (double i = 0.9; i > 0.1; i = i - 0.02) {
            if (IobCobCalculatorPlugin.percentile(deviations, (i + 0.02)) >= 0 && IobCobCalculatorPlugin.percentile(deviations, i) < 0) {
                log.debug(Math.round(100 * i) + "% of non-meal deviations negative (target 45%-50%)");
            }
        }
        double pSensitive = IobCobCalculatorPlugin.percentile(deviations, 0.50);
        double pResistant = IobCobCalculatorPlugin.percentile(deviations, 0.45);

        double basalOff = 0;

        if (pSensitive < 0) { // sensitive
            basalOff = pSensitive * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
            sensResult = "Excess insulin sensitivity detected";
        } else if (pResistant > 0) { // resistant
            basalOff = pResistant * (60 / 5) / Profile.toMgdl(sens, profile.getUnits());
            sensResult = "Excess insulin resistance detected";
        } else {
            sensResult = "Sensitivity normal";
        }
        log.debug(sensResult);
        ratio = 1 + (basalOff / profile.getMaxDailyBasal());

        double rawRatio = ratio;
        ratio = Math.max(ratio, SafeParse.stringToDouble(SP.getString("openapsama_autosens_min", "0.7")));
        ratio = Math.min(ratio, SafeParse.stringToDouble(SP.getString("openapsama_autosens_max", "1.2")));

        if (ratio != rawRatio) {
            ratioLimit = "Ratio limited from " + rawRatio + " to " + ratio;
            log.debug(ratioLimit);
        }

        double newisf = Math.round(Profile.toMgdl(sens, profile.getUnits()) / ratio);
        if (ratio != 1) {
            log.debug("ISF adjusted from " + Profile.toMgdl(sens, profile.getUnits()) + " to " + newisf);
        }

        AutosensResult output = new AutosensResult();
        output.ratio = Round.roundTo(ratio, 0.01);
        output.carbsAbsorbed = Round.roundTo(current.cob, 0.01);
        output.pastSensitivity = pastSensitivity;
        output.ratioLimit = ratioLimit;
        output.sensResult = sensResult;
        return output;
    }

    private void calculateSensitivityData(long startTime, long endTime) {
        log.debug("CheckPoint 12-8 bucketed data is now "+bucketed_data.size());
        if (MainApp.getConfigBuilder() == null)
            return; // app still initializing
        if (MainApp.getConfigBuilder().getProfile() == null)
            return; // app still initializing
        //log.debug("Locking calculateSensitivityData");
        long oldestTimeWithData = oldestDataAvailable();
        log.debug("CheckPoint 6-1 getting bucketed data");
        createBucketedData(endTime);
        log.debug("CheckPoint 12-8 bucketed data is recalculated "+bucketed_data.size());
        synchronized (dataLock) {

            if (bucketed_data == null || bucketed_data.size() < 3) {
                log.debug("CheckPoint 6-1 no bucketed data");
                return;
            }

            long prevDataTime = roundUpTime(bucketed_data.get(bucketed_data.size() - 3).date);
            log.debug("CheckPoint 7-1 Prev data time: " + new Date(prevDataTime).toLocaleString());
            AutosensData previous = autosensDataTable.get(prevDataTime);
            // start from oldest to be able sub cob
            for (int i = bucketed_data.size() - 4; i >= 0; i--) {
                // check if data already exists
                long bgTime = bucketed_data.get(i).date;
                log.debug("CheckPoint 7-1 bucketed_data.date is " + new Date(bucketed_data.get(i).date).toString());
                bgTime = roundUpTime(bgTime);
                log.debug("CheckPoint 7-1 after roundup is " + new Date(bgTime).toString());
                if (bgTime > System.currentTimeMillis() || bgTime > endTime){
                    log.debug("CheckPoint 7-1 bgTime is bigger than endtime - returning");
                    continue;
//                    return;
                }
                Profile profile = MainApp.getConfigBuilder().getProfile(bgTime);
                AutosensData existing;
                if ((existing = autosensDataTable.get(bgTime)) != null && bgTime < endTime) {
                    log.debug("CheckPoint 7-4-1 existing is not null");
                    previous = existing;
                    continue;
                }

                if (profile.getIsf(bgTime) == null) {
                    log.debug("CheckPoint 7-4-1 exiting no ISF");
                    return; // profile not set yet
                }
                log.debug("CheckPoint 7-4 before sens");
                double sens = Profile.toMgdl(profile.getIsf(bgTime), profile.getUnits());
                log.debug("CheckPoint 7-4");
                AutosensData autosensData = new AutosensData();
                autosensData.time = bgTime;
                log.debug("CheckPoint 7-2 autosensData.time is "+new Date(autosensData.time).toString());
                if (previous != null)
                    activeCarbsList = new ArrayList<>(previous.activeCarbsList);
                else
                    activeCarbsList = new ArrayList<>();

                //console.error(bgTime , bucketed_data[i].glucose);
                double bg;
                double avgDelta = 0;
                double delta;
                bg = bucketed_data.get(i).value;
                if (bg < 39 || bucketed_data.get(i + 3).value < 39) {
                    log.error("! value < 39");
                    continue;
                }
                delta = (bg - bucketed_data.get(i + 1).value);

                IobTotal iob = calculateFromTreatmentsAndTemps(bgTime);

                double bgi = -iob.activity * sens * 5;
                double deviation = delta - bgi;
                List<Treatment> recentTreatments = MainApp.getConfigBuilder().getTreatments5MinBackFromHistory(bgTime);
                for (int ir = 0; ir < recentTreatments.size(); ir++) {
                    autosensData.carbsFromBolus += recentTreatments.get(ir).carbs;
                    autosensData.activeCarbsList.add(new AutosensData.CarbsInPast(recentTreatments.get(ir)));
                }


                // if we are absorbing carbs
                if (previous != null && previous.cob > 0) {
                    // calculate sum of min carb impact from all active treatments
                    double totalMinCarbsImpact = 0d;
                    for (int ii = 0; ii < autosensData.activeCarbsList.size(); ++ii) {
                        AutosensData.CarbsInPast c = autosensData.activeCarbsList.get(ii);
                        totalMinCarbsImpact += c.min5minCarbImpact;
                    }

                    // figure out how many carbs that represents
                    // but always assume at least 3mg/dL/5m (default) absorption per active treatment
                    double ci = Math.max(deviation, totalMinCarbsImpact);
                    autosensData.absorbed = ci * profile.getIc(bgTime) / sens;
                    // and add that to the running total carbsAbsorbed
                    autosensData.cob = Math.max(previous.cob - autosensData.absorbed, 0d);
                    autosensData.substractAbosorbedCarbs();
                }
                autosensData.removeOldCarbs(bgTime);
                autosensData.cob += autosensData.carbsFromBolus;
                autosensData.deviation = deviation;
                autosensData.bgi = bgi;
                autosensData.delta = delta;

                // calculate autosens only without COB
                if (autosensData.cob <= 0) {
                    if (Math.abs(deviation) < Constants.DEVIATION_TO_BE_EQUAL) {
                        autosensData.pastSensitivity += "=";
                        autosensData.nonEqualDeviation = true;
                    } else if (deviation > 0) {
                        autosensData.pastSensitivity += "+";
                        autosensData.nonEqualDeviation = true;
                    } else {
                        autosensData.pastSensitivity += "-";
                        autosensData.nonEqualDeviation = true;
                    }
                    autosensData.nonCarbsDeviation = true;
                } else {
                    autosensData.pastSensitivity += "C";
                }
                log.debug("CheckPoint 7-2 TIME: " + new Date(bgTime).toString() + " BG: " + bg + " SENS: " + sens + " DELTA: " + delta + " AVGDELTA: " + avgDelta + " IOB: " + iob.iob + " ACTIVITY: " + iob.activity + " BGI: " + bgi + " DEVIATION: " + deviation);

                previous = autosensData;
                Date entering = new Date(bgTime);
                log.debug("CheckPoint 7-2 putting something in autosesnsDataTable - "+entering.toString()+" - "+autosensData.autosensRatio);
                autosensDataTable.put(bgTime, autosensData);
//                autosensData.autosensRatio = detectSensitivity(oldestTimeWithData, bgTime).ratio;
                autosensData.autosensRatio = detectSensitivity(startTime, bgTime).ratio;
                log.debug("CheckPoint 7-2 calculated ratio is "+autosensData.autosensRatio);
            }
        }
        // MainApp.bus().post(new EventAutosensCalculationFinished());
        //log.debug("Releasing calculateSensitivityData");
    }


    @Nullable
    public static AutosensData getLastAutosensData() {
        if (autosensDataTable.size() < 1)
            return null;
        AutosensData data = autosensDataTable.valueAt(autosensDataTable.size() - 1);
        if (data.time < System.currentTimeMillis() - 11 * 60 * 1000) {
            return null;
        } else {
            return data;
        }
    }

    @Nullable
    private static Long findPreviousTimeFromBucketedData(long time) {
        if (bucketed_data == null){
            log.debug("CheckPoint 4-1 - bucketed_data is null");
            return null;}
        for (int index = 0; index < bucketed_data.size(); index++) {
            if (bucketed_data.get(index).date < time)
                return bucketed_data.get(index).date;
        }
        log.debug("CheckPoint 4-2 - all dates in bucketed data are bigger then time ?!?");
        return null;
    }

/*
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
*/
    public static synchronized String getBasalIst(){
        getPlugin().getProfile();
        if(profile.equals(null))
            return "Profile is null";

        String basals = profile.getBasalList();

        return basals;
    }

    public static synchronized Integer getTargets(){
        getPlugin().getProfile();
        int toMgDl = 1;
        if(profile.equals(null))
            return null;
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        Integer targets = (int) (((profile.getTargetLow() * toMgDl)*100)/100);

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

    public static String basicResult(int daysBack) {
        // get some info and spit out a suggestion
        // Time should be 1 day back
        // time now
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now - (daysBack * 24 * 60 * 60 * 1000L));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        // midnight
        long endTime = c.getTimeInMillis();
        long starttime = endTime - (24 * 60 * 60 * 1000L);
        // now we have our start and end of day
        int averageBG = 0;
        double basal;
        double basalNeeded = 0d;
        double isf;
        double target;
        double result;
        double sensitivity;
        double sensitivityCarbs = 0d;
        String basicResult = "";
        double deviation = 0d;
        double netDeviation = 0d;
        tunedBasalsInit();
        getPlugin().createBucketedData(endTime);
        log.debug("CheckPoint 12-8 bucketed_data size "+bucketed_data.size());
        getPlugin().calculateSensitivityData(starttime, endTime);
        //getPlugin().getGlucoseData(starttime, endTime);
        //AutosensData autosensData = getAutosensData(endTime);

        // Detecting sensitivity for the whole day
        AutosensResult autosensResult = detectSensitivity(starttime, endTime);
        log.debug("CheckPoint 7-12 sensitivity is "+autosensResult.ratio +" from "+new Date(starttime).toString()+" to "+new Date(endTime));

        for (int i = 0; i < 24; i++) {

            // get average BG
            log.debug("CheckPoint 12-3 - getting glucose");
            getPlugin().getGlucoseData(starttime, endTime);
            log.debug("CheckPoint 12-3 - records "+getPlugin().glucose_data.size());
            averageBG = getPlugin().averageGlucose(starttime + (i * 60 * 60 * 1000l), starttime + (i + 1) * 60 * 60 * 1000L);
            // get ISF
            isf = getISF();

            // Look at netDeviations for each hour
            // Get AutoSensData for 5 min deviations

            // initialize
            deviation = 0;
            int counter = 0;

            for (long time = starttime + (i * 60 * 60 * 1000l); time <= starttime + (i + 1) * 60 * 60 * 1000L; time += 5 * 60 * 1000L) {

//                autosensData = getAutosensData(time);
                /*if(autosensData == null) {
                    autosensData = getLastAutosensData();
                    log.debug("CheckPoint 6-5 - autosensData is "+((time - autosensData.time)/(60*1000L))+" min older");
                    if(time - autosensData.time < 0){
                        // Autosens is newer than needed

                    }
                }

                if (autosensData != null) {
                    deviation += autosensData.deviation;
                    counter++;
                } else
                    log.debug("CheckPoint 6-3 Cannot get autosens data for "+time);

            }
            // use net dev not average
            netDeviation = deviation;
            log.debug("netDeviation "+netDeviation);
            // calculate how much less or additional basal insulin would have been required to eliminate the deviations
            // only apply 20% of the needed adjustment to keep things relatively stable
            basalNeeded = 0.2 * netDeviation / isf;
            basalNeeded = round(basalNeeded,2);
*/
                // if basalNeeded is positive, adjust each of the 1-3 hour prior basals by 10% of the needed adjustment
                if (basalNeeded > 0) {
                    for (int offset = -3; offset < 0; offset++) {
                        int offsetHour = i + offset;
                        if (offsetHour < 0) {
                            offsetHour += 24;
                        }
                        //console.error(offsetHour);
                        if (tunedBasals.get(offsetHour) == 0d) {
                            log.debug("Tuned at " + offsetHour + " is zero!");
                            tunedBasals.set(offsetHour, getBasal(offsetHour));
                        }
                        tunedBasals.set(offsetHour, (tunedBasals.get(offsetHour) + round(basalNeeded / 3, 3)));
                    }
                    // otherwise, figure out the percentage reduction required to the 1-3 hour prior basals
                    // and adjust all of them downward proportionally
                } else if (basalNeeded < 0) {
                    double threeHourBasal = 0;
                    for (int offset = -3; offset < 0; offset++) {
                        int offsetHour = i + offset;
                        if (offsetHour < 0) {
                            offsetHour += 24;
                        }
                        threeHourBasal += tunedBasals.get(offsetHour);
                    }
                    double adjustmentRatio = 1.0 + basalNeeded / threeHourBasal;
                    //console.error(adjustmentRatio);
                    for (int offset = -3; offset < 0; offset++) {
                        int offsetHour = i + offset;
                        if (offsetHour < 0) {
                            offsetHour += 24;
                        }
                        if (tunedBasals.get(offsetHour) == 0d) {
                            log.debug("Tuned at " + offsetHour + " is zero!");
                            tunedBasals.set(offsetHour, getBasal(offsetHour));
                        }
                        tunedBasals.set(offsetHour, round(getBasal(offsetHour) * adjustmentRatio, 3));
                    }
                }
                // some hours of the day rarely have data to tune basals due to meals.
                // when no adjustments are needed to a particular hour, we should adjust it toward the average of the
                // periods before and after it that do have data to be tuned
                int lastAdjustedHour = 0;
                // scan through newHourlyBasalProfile(tunedBasals and find hours where the rate is unchanged
                for (int hour = 0; hour < 24; hour++) {
                    if (profile.getBasal(hour * 3600) == tunedBasals.get(hour)) {
                        int nextAdjustedHour = 23;
                        for (int nextHour = hour; nextHour < 24; nextHour++) {
                            if (!(profile.getBasal(nextHour * 3600) == tunedBasals.get(nextHour))) {
                                nextAdjustedHour = nextHour;
                                break;
                                //} else {
                                //console.error(nextHour, hourlyBasalProfile[nextHour].rate, newHourlyBasalProfile[nextHour].rate);
                            }
                        }
                        //console.error(hour, newHourlyBasalProfile);
                        tunedBasals.set(hour, round((0.8 * profile.getBasal(hour * 3600) + 0.1 * tunedBasals.get(lastAdjustedHour) + 0.1 * tunedBasals.get(nextAdjustedHour) * 1000) / 1000, 2));
                    } else {
                        lastAdjustedHour = hour;
                    }
                }
                for (int ii = 0; ii < 24; ii++) {
                    log.debug("Tuned is " + ii + " is " + tunedBasals.get(ii));
                }

            }
            if (averageBG > 0) {
                return averageBG + "\n" + displayBasalsResult();
            } else return "No BG data!(basicResult()";
        }
        return displayBasalsResult();
    }



    public static String displayBasalsResult(){
        String result = "";
        for(int i=0;i<24; i++){
            result += "\n"+i+" | "+getBasal(i)+" -> "+tunedBasals.get(i);
        }
        return result;
    }

    public static void tunedBasalsInit(){
        // initialize tunedBasals if
            if(tunedBasals.isEmpty()) {
                for (int i = 0; i < 24; i++) {
                    tunedBasals.add(getBasal(i));
                }
            } else
                for (int i = 0; i < 24; i++) {
                    tunedBasals.set(i, getBasal(i));
                }
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
