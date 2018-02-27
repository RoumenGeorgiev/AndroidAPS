package info.nightscout.androidaps.plugins.TuneProfile;

import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BGDatum;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Created by Rumen Georgiev on 1/29/2018.
 ideia is to port Autotune from OpenAPS to java
 let's start by taking 1 day of data from NS, and comparing it to ideal BG
 TODO: Sort glucoseData like autotune-prep does categorize.js
 TODO:
 TODO: Get treatments
 TODO: Add class BGDatum for compatibility with Categorize
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
 -- get profile - done
 -- get treatments

 */

public class TuneProfile implements PluginBase {

    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;

    private static TuneProfile tuneProfile = null;
    private static Logger log = LoggerFactory.getLogger(TuneProfile.class);
    public static Profile profile;
    public static List<Double> tunedBasals = new ArrayList<Double>();
    public static List<Double> basalsResult = new ArrayList<Double>();
    public List<BgReading> glucose_data = new ArrayList<BgReading>();
    public static List<Treatment> treatments;
    private static final Object dataLock = new Object();
    private static double tunedISF = 0d;
    private List<BGDatum> CSFGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> ISFGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();
    private List<BGDatum> UAMGlucoseData = new ArrayList<BGDatum>();
    private List<CRDatum> CRData = new ArrayList<CRDatum>();
    private JSONObject previousResult = null;
    //copied from IobCobCalculator
    private static LongSparseArray<IobTotal> iobTable = new LongSparseArray<>(); // oldest at index 0
    private static volatile List<BgReading> bgReadings = null; // newest at index 0
    private static volatile List<BgReading> bucketed_data = null;
    private static LongSparseArray<AutosensData> autosensDataTable = new LongSparseArray<>(); // oldest at index 0
    List<info.nightscout.androidaps.plugins.TuneProfile.AutosensData.CarbsInPast> activeCarbsList = new ArrayList<>();

    private NSService nsService = new NSService();

    public TuneProfile() throws IOException {
    }

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
            try {
                tuneProfile = new TuneProfile();
            } catch (IOException e) {
                e.printStackTrace();
            }
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

/*    public void getGlucoseData(long start, long end) {
        //get glucoseData for 1 day back
        long oneDayBack = end - 24 * 60 * 60 *1000L;
        glucose_data = MainApp.getDbHelper().getBgreadingsDataFromTime(oneDayBack, true);
//        log.debug("CheckPoint -121 - glucose_data.size is "+glucose_data.size()+" from "+new Date(oneDayBack).toString()+" to "+new Date(end).toString());
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
//        log.debug("CheckPoint 12-2 - glucose_data.size is "+glucose_data.size()+"("+initialSize+") from "+new Date(oneDayBack).toString()+" to "+new Date(end).toString());

    }*/
    public List<BgReading> getBGDataFromNS(long from, long to) throws IOException, ParseException {
        NSService nsService = null;
        nsService = new NSService();
        List<BgReading> fromNS = nsService.getSgvValues(from, to);
        log.debug("CheckPoint 15-0 NS SGV size is "+fromNS.size());
        return fromNS;
    }

    public List<BgReading> getBGFromTo(long from, long to) {
        List<BgReading> bg_data_temp = MainApp.getDbHelper().getBgreadingsDataFromTime(from, true);
        List<BgReading> bg_data = new ArrayList<BgReading>();
        int initialSize = bg_data_temp.size();
        if(initialSize < 1) {
            // no BG data return empty list
            return new ArrayList<BgReading>();
        }
        int counter = 0;
        long location = from;
        long next5min = from + 5 * 60 * 1000L;
        for (int i = 0; i < initialSize-1; i++) {
            if (bg_data_temp.get(i).value > 38) {
                if(bg_data_temp.get(i).date > from && bg_data_temp.get(i).date < to) {
                    bg_data.add(bg_data_temp.get(i));
                    counter++;
                }
            }
        }
        if(bg_data.size()<1)
            return new ArrayList<BgReading>();
        for (int i = 0; i < bg_data.size()-1; i++) {
            if(bg_data_temp.get(i).date > next5min){
                location = next5min;
                next5min = location + 5*60*1000L;
            }

            if(bg_data.get(i).date > location && bg_data.get(i).date < next5min) {
                bg_data.remove(i);
                counter++;
            }
        }

        bg_data_temp = new ArrayList<BgReading>();
        return bg_data;

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
    private void createBucketedData(long endTime) throws IOException, ParseException {
        log.debug("CheckPoint 12-8-0-1 - entered createBucketedData");
        if (isAbout5minData()) {
            log.debug("CheckPoint 12-8-0-2");
            createBucketedDataRecalculated(endTime);
//            createBucketedData5min(endTime);
        } else {
            log.debug("CheckPoint 12-8-0-3 - creating bucketedDataRecalculated(" + new Date(endTime).toLocaleString() + ")");
            createBucketedDataRecalculated(endTime);
        }
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

    private void createBucketedDataRecalculated(long endTime) throws IOException, ParseException {
        // Created by Rumen for timesensitive autosensCalc

        synchronized (dataLock) {
//            log.debug("CheckPoint 10-1 - glucose_data "+glucose_data.size()+" records");
//            glucose_data = getBGFromTo(endTime- 24*60*60*1000l, endTime);
            glucose_data = getBGDataFromNS(endTime- 24*60*60*1000l, endTime);
            log.debug("CheckPoint 10-2 - glucose_data "+glucose_data.size()+" records");
            if (glucose_data == null || glucose_data.size() < 3) {

                bucketed_data = null;
//                log.debug("CheckPoint 10 - no glucose_data");
                return;
            }
//            log.debug("CheckPoint 11");
            bucketed_data = new ArrayList<>();
            long currentTime = glucose_data.get(0).date + 5 * 60 * 1000 - glucose_data.get(0).date % (5 * 60 * 1000) - 5 * 60 * 1000L;
            log.debug("CheckPoint 11-1 First reading: " + new Date(currentTime).toLocaleString());

            while (true) {
                // test if current value is older than current time
                int position = 0;
                int maxPos = glucose_data.size() - 1;
                for(int i=0; i<glucose_data.size(); i++){
                    double currentBg = glucose_data.get(i).value;
                    BgReading newBgreading = new BgReading();
                    if(glucose_data.get(i).date>currentTime) {
                        newBgreading.date = currentTime;
                        newBgreading.value = Math.round(currentBg);
                        bucketed_data.add(newBgreading);
                        currentTime += 5 * 60 * 1000L;
                    }

                }
                log.debug("CheckPoint 11-2-1  bucketed_data.size " + bucketed_data.size());
                break;
            }
               /* BgReading newer = findNewer(glucose_data, currentTime);
                BgReading older = findOlder(glucose_data, currentTime);
                if (newer == null || older == null) {
                    log.debug("CheckPoint 11-2 no newer or older value for" + new Date(currentTime).toLocaleString());
                    //break;
                }

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
            log.debug("CheckPoint 11-3 recalculated bucketed_data size "+bucketed_data.size());*/
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
                log.debug("CheckPoint 9-1 - no bgReadings");
                bucketed_data = null;
                return;
            }

            bucketed_data = new ArrayList<>();
            bucketed_data.add(bgReadings.get(0));
            log.debug("CheckPoint 9-2");
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
                    //log.debug(elapsed_minutes);
                    long nextbgTime;
                    while (elapsed_minutes > 5) {
                        nextbgTime = lastbgTime - 5 * 60 * 1000;
                        j++;
                        BgReading newBgreading = new BgReading();
                        newBgreading.date = nextbgTime;
                        double gapDelta = bgReadings.get(i).value - lastbg;
                        //log.debug(gapDelta, lastbg, elapsed_minutes);
                        double nextbg = lastbg + (5d / elapsed_minutes * gapDelta);
                        newBgreading.value = Math.round(nextbg);
                        //log.debug("Interpolated", bucketed_data[j]);
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
            log.debug("CheckPoint 9-3 Bucketed data created. Size: " + bucketed_data.size());
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
            getBGFromTo(start, end);
//        log.debug("CheckPoint 12-4 glucose_data.size is "+glucose_data.size()+" from "+new Date(start).toString()+" to "+new  Date(end).toString());
        if(glucose_data.size() < 1)
            // no BG data
            return 0;

        int counter = 0; // how many bg readings we have
        int avgGlucose = 0;
        long milisMax = end;
        long milisMin = start;
        //trimGlucose(start, end);
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


    @Nullable
    public static AutosensData getAutosensData(long time) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            if (time > now)
                return null;
            Long previous = findPreviousTimeFromBucketedData(time);
            if (previous == null) {
                log.debug("CheckPoint 4 - no previous time in bucketed_data "+new Date(time));
                return null;
            }
            time = roundUpTime(previous);
//            log.debug("Getting from autosensDataTable for "+new Date(time).toLocaleString());
            AutosensData data = autosensDataTable.get(time);
            if (data != null) {
//                log.debug("CheckPoint 4-1 - autosensDataTable.get("+new Date(time).toString()+") is "+data.autosensRatio);
                //log.debug(">>> getAutosensData Cache hit " + data.log(time));
                return data;
            } else {
                if (time > now) {
                    // data may not be calculated yet, use last data
                    return getLastAutosensData();
                }
                //log.debug(">>> getAutosensData Cache miss " + new Date(time).toLocaleString());
//                log.debug("CheckPoint 4-2 - autosensDataTable.get("+new Date(time).toString()+") is null");
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
//        log.debug("CheckPoint 7-13 Limiting data to oldest available temps: " + new Date(oldestDataAvailable).toString());
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
            return new AutosensResult();
        }

        AutosensData current = getLastAutosensData();
        if (current == null) {
            //log.debug("No current autosens data available");
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

//        log.debug("Records: " + index + "   " + pastSensitivity);
        Arrays.sort(deviations);

        for (double i = 0.9; i > 0.1; i = i - 0.02) {
            if (IobCobCalculatorPlugin.percentile(deviations, (i + 0.02)) >= 0 && IobCobCalculatorPlugin.percentile(deviations, i) < 0) {
//                log.debug(Math.round(100 * i) + "% of non-meal deviations negative (target 45%-50%)");
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
//        log.debug(sensResult);
        ratio = 1 + (basalOff / profile.getMaxDailyBasal());

        double rawRatio = ratio;
        ratio = Math.max(ratio, SafeParse.stringToDouble(SP.getString("openapsama_autosens_min", "0.7")));
        ratio = Math.min(ratio, SafeParse.stringToDouble(SP.getString("openapsama_autosens_max", "1.2")));

        if (ratio != rawRatio) {
            ratioLimit = "Ratio limited from " + rawRatio + " to " + ratio;
//            log.debug(ratioLimit);
        }

        double newisf = Math.round(Profile.toMgdl(sens, profile.getUnits()) / ratio);
        if (ratio != 1) {
//            log.debug("ISF adjusted from " + Profile.toMgdl(sens, profile.getUnits()) + " to " + newisf);
        }

        AutosensResult output = new AutosensResult();
        output.ratio = Round.roundTo(ratio, 0.01);
        output.carbsAbsorbed = Round.roundTo(current.cob, 0.01);
        output.pastSensitivity = pastSensitivity;
        output.ratioLimit = ratioLimit;
        output.sensResult = sensResult;
        return output;
    }

    private void calculateSensitivityData(long startTime, long endTime) throws IOException, ParseException {
        if (MainApp.getConfigBuilder() == null || bucketed_data == null)
            return; // app still initializing
        if (MainApp.getConfigBuilder().getProfile() == null)
            return; // app still initializing
        //log.debug("Locking calculateSensitivityData");
        long oldestTimeWithData = oldestDataAvailable();
//        log.debug("CheckPoint 6-1 getting bucketed data");
        if(bucketed_data.size() == 0)
            createBucketedData(endTime);
//        log.debug("CheckPoint 12-8 bucketed data is recalculated "+bucketed_data.size());
        synchronized (dataLock) {

            if (bucketed_data == null || bucketed_data.size() < 3) {
//                log.debug("CheckPoint 12-8-1 no bucketed data exiting calculateSensitivityData");
                return;
            }

            long prevDataTime = roundUpTime(bucketed_data.get(bucketed_data.size() - 3).date);
//            log.debug("CheckPoint 7-1 Prev data time: " + new Date(prevDataTime).toLocaleString());
            AutosensData previous = autosensDataTable.get(prevDataTime);
            // start from oldest to be able sub cob
            for (int i = bucketed_data.size() - 4; i >= 0; i--) {
                // check if data already exists
                long bgTime = bucketed_data.get(i).date;
//                log.debug("CheckPoint 7-1 bucketed_data.date is " + new Date(bucketed_data.get(i).date).toString());
                bgTime = roundUpTime(bgTime);
//                log.debug("CheckPoint 7-1 after roundup is " + new Date(bgTime).toString());
                if (bgTime > System.currentTimeMillis() || bgTime > endTime){
//                    log.debug("CheckPoint 7-1 bgTime is bigger than endtime - returning");
                    continue;
//                    return;
                }
                Profile profile = MainApp.getConfigBuilder().getProfile(bgTime);
                AutosensData existing;
                if ((existing = autosensDataTable.get(bgTime)) != null && bgTime < endTime) {
//                    log.debug("CheckPoint 7-4-1 existing is not null");
                    previous = existing;
                    continue;
                }

                if (profile.getIsf(bgTime) == null) {
                    log.debug("CheckPoint 7-4-1 exiting no ISF");
                    return; // profile not set yet
                }
//                log.debug("CheckPoint 7-4 before sens");
                double sens = Profile.toMgdl(profile.getIsf(bgTime), profile.getUnits());
//                log.debug("CheckPoint 7-4");
                AutosensData autosensData = new AutosensData();
                autosensData.time = bgTime;
//                log.debug("CheckPoint 7-2 autosensData.time is "+new Date(autosensData.time).toString());
                if (previous != null)
                    activeCarbsList = new ArrayList<>(previous.activeCarbsList);
                else
                    activeCarbsList = new ArrayList<>();

                //log.debug(bgTime , bucketed_data[i].glucose);
                double bg;
                double avgDelta = 0;
                double delta;
                bg = bucketed_data.get(i).value;
                if (bg < 39 || bucketed_data.get(i + 3).value < 39) {
//                    log.error("! value < 39");
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
//                log.debug("CheckPoint 7-2 TIME: " + new Date(bgTime).toString() + " BG: " + bg + " SENS: " + sens + " DELTA: " + delta + " AVGDELTA: " + avgDelta + " IOB: " + iob.iob + " ACTIVITY: " + iob.activity + " BGI: " + bgi + " DEVIATION: " + deviation);

                previous = autosensData;
                Date entering = new Date(bgTime);
//                log.debug("CheckPoint 7-2 putting something in autosesnsDataTable - "+entering.toString()+" - "+autosensData.autosensRatio);
                autosensDataTable.put(bgTime, autosensData);
//                autosensData.autosensRatio = detectSensitivity(oldestTimeWithData, bgTime).ratio;
                autosensData.autosensRatio = detectSensitivity(startTime, bgTime).ratio;
//                log.debug("CheckPoint 7-2 calculated ratio is "+autosensData.autosensRatio);
            }
        }
    }


    @Nullable
    public static AutosensData getLastAutosensData() {
        if (autosensDataTable.size() < 1) {
//            log.debug("CheckPoint 13-1 autosensDataTable is too small");
            return null;
        }
        AutosensData data = autosensDataTable.valueAt(autosensDataTable.size() - 1);
        if (data.time < System.currentTimeMillis() - 11 * 60 * 1000) {
//            log.debug("CheckPoint 13-2 latest autosensData is too old"+new Date(data.time).toString());
            return data;
            //return null;
        } else {
            return data;
        }
    }

    @Nullable
    private static Long findPreviousTimeFromBucketedData(long time) {
        if (bucketed_data == null){
            log.debug("CheckPoint 4-1 - bucketed_data is null");
            return null;}
        for (int index = bucketed_data.size()-1; index > -1 ; index--) {
            if (bucketed_data.get(index).date < time) {

//                log.debug("Previous time in bData is "+new Date(bucketed_data.get(index).date).toLocaleString());
                return bucketed_data.get(index).date;
            }
        }
        // Trying to get new bucketed data
        log.debug("CheckPoint 4-2 - all dates are > "+new Date(time));
//        log.debug("CheckPoint 4-2 - first date is "+new Date(bucketed_data.get(0).date));
//        log.debug("CheckPoint 4-2 - last date is "+new Date(bucketed_data.get(bucketed_data.size()-1).date));
        return null;
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

    public static String bgReadings(int daysBack){
        String result = "";
        long now = System.currentTimeMillis();

        for(int i=daysBack; i>0; i--){
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now - ((i-1) * 24 * 60 * 60 * 1000L));
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            // midnight
            long endTime = c.getTimeInMillis();
            long startTime = endTime - (24 * 60 * 60 * 1000L);
            log.debug("Getting BG for "+new Date(startTime).toLocaleString());
            result += "\nfor "+new Date(startTime).getDate()+" "+new Date(startTime).getMonth()+" "+getPlugin().getBGFromTo(startTime, endTime).size()+" values";
//            log.debug("\nEarliest is:"+new Date(getPlugin().getBGFromTo(startTime, endTime).get(0).date));
        }

        return result;

    }

    public void categorizeBGDatums(long from, long to) throws JSONException, ParseException, IOException {
        // TODO: Although the data from NS should be sorted maybe we need to sort it
        // sortBGdata
        // sort treatments

        //starting variable at 0
        //TODO: Add this to preferences
        boolean categorize_uam_as_basal = false;
        List<BgReading> sgv = new ArrayList<BgReading>();
        CSFGlucoseData = new ArrayList<BGDatum>();
        ISFGlucoseData = new ArrayList<BGDatum>();
        basalGlucoseData = new ArrayList<BGDatum>();
        UAMGlucoseData = new ArrayList<BGDatum>();
        CRData = new ArrayList<CRDatum>();


        int boluses = 0;
        int maxCarbs = 0;
        //log.debug(treatments);
        try {
            treatments = nsService.getTreatments(from,to);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (treatments.size() < 1) {
            log.debug("No treatments");
            return;
        }
        //glucosedata is sgv
        try {
            sgv = nsService.getSgvValues(from, to);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sgv.size() < 1) {
            log.debug("No SGV data");
            return;
        }
        if(profile == null)
            getProfile();
        JSONObject IOBInputs = new JSONObject();
        IOBInputs.put("profile", profile);
        IOBInputs.put("history", "pumpHistory");


        List<BGDatum> bucketedData = new ArrayList<BGDatum>();

        double CRInitialIOB = 0d;
        double CRInitialBG = 0d;
        Date CRInitialCarbTime = null;

        // BasalGlucosedata is null
//        bucketedData.add(basalGlucoseData.get(0));
        int j = 0;
        //for loop to validate and bucket the data
        for (int i=1; i < sgv.size(); ++i) {
            long BGTime = 0;
            long lastBGTime = 0 ;
            if (sgv.get(i).date != 0 ) {
                BGTime = new Date(sgv.get(i).date).getTime();
            } /* We don't need these checks as we get the glucose from NS

            else if (sgv.get(i).displayTime) {
                BGTime = new Date(glucoseData[i].displayTime.replace('T', ' '));
            } else if (glucoseData[i].dateString) {
                BGTime = new Date(glucoseData[i].dateString);
            } else { log.debug("Could not determine BG time"); }
            if (glucoseData[i-1].date) {
                lastBGTime = new Date(glucoseData[i-1].date);
            } else if (glucoseData[i-1].displayTime) {
                lastBGTime = new Date(glucoseData[i-1].displayTime.replace('T', ' '));
            } else if (glucoseData[i-1].dateString) {
                lastBGTime = new Date(glucoseData[i-1].dateString);*/
            else { log.error("Could not determine last BG time"); }
            if(i>1) {
                if (sgv.get(i).value < 39 || sgv.get(i - 1).value < 39) {
                    continue;
                }
            } else if (sgv.get(i).value < 39) {
                continue;
            }
            long elapsedMinutes = (BGTime - lastBGTime)/(60*1000);
            if(Math.abs(elapsedMinutes) > 2) {
                j++;
                if(bucketedData.size()<j)
                    bucketedData.add(new BGDatum(sgv.get(i)));
                else
                    bucketedData.set(j,new BGDatum(sgv.get(i)));
//                bucketedData<j>.date = BGTime;
                /*if (! bucketedData[j].dateString) {
                    bucketedData[j].dateString = BGTime.toISOString();
                }*/
            } else {
                // if duplicate, average the two
                BgReading average = new BgReading();
                average.copyFrom(bucketedData.get(j));
                average.value = (bucketedData.get(j).value + sgv.get(i).value)/2;
                bucketedData.set(j, new BGDatum(average));
            }
        }
        // go through the treatments and remove any that are older than the oldest glucose value
        //log.debug(treatments);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss''");
        log.debug("Treatments size before clear: "+treatments.size());
        log.debug("Treatment(0) "+new Date(treatments.get(0).date).toString()+" last "+new Date(treatments.get(treatments.size()-1).date).toString());
        for (int i=treatments.size()-1; i>0; --i) {
            Treatment treatment = treatments.get(i);
            //log.debug(treatment);
            if (treatment != null) {
                Date treatmentDate = new Date(treatment.date);
                long treatmentTime = treatmentDate.getTime();
                BgReading glucoseDatum = bucketedData.get(bucketedData.size()-1);
                //log.debug(glucoseDatum);
                Date BGDate = new Date(glucoseDatum.date);
                long BGTime = BGDate.getTime();

//                log.debug("Oldest BG data is: "+format.format(BGDate)+" oldest treatment is: "+format.format(new Date(treatments.get(treatments.size()-1).date).toGMTString()));
                if ( treatmentTime < BGTime ) {
                    //treatments.splice(i,1);
                    treatments.remove(i);
                }
//                log.debug("Treatment size after removing of older: "+treatments.size());
            }
        }
        //log.debug(treatments);

        boolean calculatingCR = false;
        int absorbing = 0;
        int uam = 0; // unannounced meal
        double mealCOB = 0d;
        int mealCarbs = 0;
        int CRCarbs = 0;
        String type = "";
        // main for loop
        List<Treatment> fullHistory = new ArrayList<Treatment>();//IOBInputs.history; TODO: get treatments with IOB
        double glucoseDatumAvgDelta = 0d;
        double glucoseDatumDelta = 0d;
        log.debug("Categorize 1 - bucketedData.size() "+bucketedData.size());
        for (int i=bucketedData.size()-5; i > 0; --i) {
            BGDatum glucoseDatum = bucketedData.get(i);
            glucoseDatumAvgDelta = 0d;
            glucoseDatumDelta = 0d;
            //log.debug(glucoseDatum);
            Date BGDate = new Date(glucoseDatum.date);
            long BGTime = BGDate.getTime();
            int myCarbs = 0;
            // As we're processing each data point, go through the treatment.carbs and see if any of them are older than
            // the current BG data point.  If so, add those carbs to COB.
//            log.debug("Categorize 1-1 - Doing it for "+BGDate.toString()+" trSize is "+treatments.size());
            for (int jj=0; jj < treatments.size()-1; jj++) {
                Treatment treatment = treatments.get(jj);
                if (treatment != null) {
                    Date treatmentDate = new Date(treatment.date);
                    long treatmentTime = treatmentDate.getTime();
                    //log.debug(treatmentDate);
//                    log.debug("Categorize 1-2 Treatment at position(" + (treatments.size() - 1) + ") diff is " + new Date(BGTime).toGMTString() + " trDate " + new Date(treatmentTime).toGMTString());
                    if (treatmentTime < BGTime) {
                        if (treatment.carbs >= 1) {
                            //                        Here I parse Integers not float like the original source categorize.js#136
                            log.debug("Categorize 1-3 Adding carbs: " + treatment.carbs + " for date " + new Date(treatment.date).toLocaleString());
                            mealCOB += (int) (treatment.carbs);
                            mealCarbs += (int) (treatment.carbs);
                            myCarbs = (int) treatment.carbs;

                        } //else
                        //                            log.debug("Treatment date: "+new Date(treatmentTime).toString()+" but BGTime: "+new Date(BGTime).toString());
//                        treatments.remove(treatments.size() - 1);
                        treatments.remove(jj);
                    }
                }
            }
            double BG=0;
            double avgDelta = 0;
            double delta;
            // TODO: re-implement interpolation to avoid issues here with gaps
            // calculate avgDelta as last 4 datapoints to better catch more rises after COB hits zero
//            log.debug("Categorize 2");
            if (bucketedData.get(i).value != 0 && bucketedData.get(i+4).value != 0) {
                //log.debug(bucketedData[i]);
                BG = bucketedData.get(i).value;
                if ( BG < 40 || bucketedData.get(i+4).value < 40) {
                    //process.stderr.write("!");
                    continue;
                }
                avgDelta = (BG - bucketedData.get(i+4).value)/4;
                delta = (BG - bucketedData.get(i+4).value);
            } else { log.error("Could not find glucose data"); }

            avgDelta = avgDelta*100 / 100;
            glucoseDatum.AvgDelta = avgDelta;

            //sens = ISF
//            int sens = ISF.isfLookup(IOBInputs.profile.isfProfile,BGDate);
            double sens = profile.getIsf(BGTime);
//            IOBInputs.clock=BGDate.toString();
            // trim down IOBInputs.history to just the data for 6h prior to BGDate
            //log.debug(IOBInputs.history[0].created_at);
            List<Treatment> newHistory = null;
            for (int h=0; h<fullHistory.size(); h++) {
//                Date hDate = new Date(fullHistory.get(h).created_at) TODO: When we get directly from Ns there should be created_at
                Date hDate = new Date(fullHistory.get(h).date);
                //log.debug(fullHistory[i].created_at, hDate, BGDate, BGDate-hDate);
                //if (h == 0 || h == fullHistory.length - 1) {
                //log.debug(hDate, BGDate, hDate-BGDate)
                //}
                if (BGDate.getTime()-hDate.getTime() < 6*60*60*1000 && BGDate.getTime()-hDate.getTime() > 0) {
                    //process.stderr.write("i");
                    //log.debug(hDate);
                    newHistory.add(fullHistory.get(h));
                }
            }
            if(newHistory != null)
                IOBInputs = new JSONObject(newHistory.toString());
            else
                IOBInputs = new JSONObject();
            // process.stderr.write("" + newHistory.length + " ");
            //log.debug(newHistory[0].created_at,newHistory[newHistory.length-1].created_at,newHistory.length);


            // for IOB calculations, use the average of the last 4 hours' basals to help convergence;
            // this helps since the basal this hour could be different from previous, especially if with autotune they start to diverge.
            // use the pumpbasalprofile to properly calculate IOB during periods where no temp basal is set
            Calendar calendar = GregorianCalendar.getInstance(); // creates a new calendar instance
            calendar.setTime(BGDate);   // assigns calendar to given date
            int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY); // gets hour in 24h format
//            double currentPumpBasal = basal.basalLookup(opts.pumpbasalprofile, BGDate);
            double currentPumpBasal = TuneProfile.getBasal(hourOfDay);
            double basal1hAgo = TuneProfile.getBasal(hourOfDay - 1);
            double basal2hAgo = TuneProfile.getBasal(hourOfDay - 2);
            double basal3hAgo = TuneProfile.getBasal(hourOfDay - 3);
            if(hourOfDay>3){
                basal1hAgo = TuneProfile.getBasal(hourOfDay - 1);
                basal2hAgo = TuneProfile.getBasal(hourOfDay - 2);
                basal3hAgo = TuneProfile.getBasal(hourOfDay - 3);
            } else {
                if(hourOfDay-1 < 0)
                    basal1hAgo = TuneProfile.getBasal(24 - 1);
                else
                    basal1hAgo = TuneProfile.getBasal(hourOfDay - 1);
                if(hourOfDay-2 < 0)
                    basal2hAgo = TuneProfile.getBasal(24 - 2);
                else
                    basal2hAgo = TuneProfile.getBasal(hourOfDay - 2);
                if(hourOfDay-3 < 0)
                    basal3hAgo = TuneProfile.getBasal(24 - 3);
                else
                    basal3hAgo = TuneProfile.getBasal(hourOfDay - 3);
            }
            double sum = currentPumpBasal+basal1hAgo+basal2hAgo+basal3hAgo;
//            IOBInputs.profile.currentBasal = Math.round((sum/4)*1000)/1000;

            // this is the current autotuned basal, used for everything else besides IOB calculations
            double currentBasal = TuneProfile.getBasal(hourOfDay);

            //log.debug(currentBasal,basal1hAgo,basal2hAgo,basal3hAgo,IOBInputs.profile.currentBasal);
            // basalBGI is BGI of basal insulin activity.
            double basalBGI = Math.round(( currentBasal * sens / 60 * 5 )*100)/100; // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
            //console.log(JSON.stringify(IOBInputs.profile));
            // call iob since calculated elsewhere
            IobTotal iob = TuneProfile.calculateFromTreatmentsAndTemps(BGDate.getTime());
            //log.debug(JSON.stringify(iob));

            // activity times ISF times 5 minutes is BGI
            double BGI = Math.round(( -iob.activity * sens * 5 )*100)/100;
            // datum = one glucose data point (being prepped to store in output)
            glucoseDatum.BGI = BGI;
            // calculating deviation
            double deviation = avgDelta-BGI;

            // set positive deviations to zero if BG is below 80
            if ( BG < 80 && deviation > 0 ) {
                deviation = 0;
            }

            // rounding and storing deviation
            deviation = round(deviation,2);
            glucoseDatum.deviation = deviation;


            // Then, calculate carb absorption for that 5m interval using the deviation.
            if ( mealCOB > 0 ) {
                Profile profile;
                if (MainApp.getConfigBuilder().getProfile() == null){
                    log.debug("No profile selected");
                    return;
                }
                profile = MainApp.getConfigBuilder().getProfile();
                double ci = Math.max(deviation, SP.getDouble("openapsama_min_5m_carbimpact", 3.0));
                double absorbed = ci * profile.getIc() / sens;
                // Store the COB, and use it as the starting point for the next data point.
                mealCOB = Math.max(0, mealCOB-absorbed);
            }


            // Calculate carb ratio (CR) independently of CSF and ISF
            // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
            // For now, if another meal IOB/COB stacks on top of it, consider them together
            // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
            // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.
            if (mealCOB > 0 || calculatingCR ) {
                // set initial values when we first see COB
                CRCarbs += myCarbs;
                if (calculatingCR == false) {
                    CRInitialIOB = iob.iob;
                    CRInitialBG = glucoseDatum.value;
                    CRInitialCarbTime = new Date(glucoseDatum.date);
                    log.debug("CRInitialIOB: "+CRInitialIOB+" CRInitialBG: "+CRInitialBG+" CRInitialCarbTime: "+CRInitialCarbTime.toString());
                }
                // keep calculatingCR as long as we have COB or enough IOB
                if ( mealCOB > 0 && i>1 ) {
                    calculatingCR = true;
                } else if ( iob.iob > currentBasal/2 && i>1 ) {
                    calculatingCR = true;
                    // when COB=0 and IOB drops low enough, record end values and be done calculatingCR
                } else {
                    double CREndIOB = iob.iob;
                    double CREndBG = glucoseDatum.value;
                    Date CREndTime = new Date(glucoseDatum.date);
                    log.debug("CREndIOB: "+CREndIOB+" CREndBG: "+CREndBG+" CREndTime: "+CREndTime);
                    //TODO:Fix this one as it produces error
//                    JSONObject CRDatum = new JSONObject("{\"CRInitialIOB\": "+CRInitialIOB+",   \"CRInitialBG\": "+CRInitialBG+",   \"CRInitialCarbTime\": "+CRInitialCarbTime+",   \"CREndIOB\": " +CREndIOB+",   \"CREndBG\": "+CREndBG+",   \"CREndTime\": "+CREndTime+                            ",   \"CRCarbs\": "+CRCarbs+"}");
                    String crDataString = "{\"CRInitialIOB\": "+CRInitialIOB+",   \"CRInitialBG\": "+CRInitialBG+",   \"CRInitialCarbTime\": "+CRInitialCarbTime.getTime()+",   \"CREndIOB\": " +CREndIOB+",   \"CREndBG\": "+CREndBG+",   \"CREndTime\": "+CREndTime.getTime()+",   \"CRCarbs\": "+CRCarbs+"}";
//                    log.debug("CRData is: "+crDataString);
                    CRDatum crDatum = new CRDatum();
                    crDatum.CRInitialBG = CRInitialBG;
                    crDatum.CRInitialIOB = CRInitialIOB;
                    crDatum.CRInitialCarbTime = CRInitialCarbTime.getTime();
                    crDatum.CREndBG = CREndBG;
                    crDatum.CREndIOB = CREndIOB;
                    crDatum.CREndTime = CREndTime.getTime();
                    //log.debug(CRDatum);

                    int CRElapsedMinutes = Math.round((CREndTime.getTime() - CRInitialCarbTime.getTime()) / (1000 * 60));
                    //log.debug(CREndTime - CRInitialCarbTime, CRElapsedMinutes);
                    if ( CRElapsedMinutes < 60 || ( i==1 && mealCOB > 0 ) ) {
                        log.debug("Ignoring "+CRElapsedMinutes+" m CR period.");
                    } else {
                        CRData.add(crDatum);
                    }

                    CRCarbs = 0;
                    calculatingCR = false;
                }
            }


            // If mealCOB is zero but all deviations since hitting COB=0 are positive, assign those data points to CSFGlucoseData
            // Once deviations go negative for at least one data point after COB=0, we can use the rest of the data to tune ISF or basals
            if (mealCOB > 0 || absorbing != 0 || mealCarbs > 0) {
                // if meal IOB has decayed, then end absorption after this data point unless COB > 0
                if ( iob.iob < currentBasal/2 ) {
                    absorbing = 0;
                    // otherwise, as long as deviations are positive, keep tracking carb deviations
                } else if (deviation > 0) {
                    absorbing = 1;
                } else {
                    absorbing = 0;
                }
                if ( absorbing != 0 && mealCOB != 0) {
                    mealCarbs = 0;
                }
                // check previous "type" value, and if it wasn't csf, set a mealAbsorption start flag
                //log.debug(type);
                if ( type.equals("csf") == false ) {
                    glucoseDatum.mealAbsorption = "start";
                    log.debug(glucoseDatum.mealAbsorption+" carb absorption");
                }
                type="csf";
                glucoseDatum.mealCarbs = mealCarbs;
                //if (i == 0) { glucoseDatum.mealAbsorption = "end"; }
                CSFGlucoseData.add(glucoseDatum);
            } else {
                // check previous "type" value, and if it was csf, set a mealAbsorption end flag
                if ( type == "csf" ) {
                    CSFGlucoseData.get(CSFGlucoseData.size()-1).mealAbsorption = "end";
                    log.debug(CSFGlucoseData.get(CSFGlucoseData.size()-1).mealAbsorption+" carb absorption");
                }

                if ((iob.iob > currentBasal || deviation > 6 || uam > 0) ) {
                    if (deviation > 0) {
                        uam = 1;
                    } else {
                        uam = 0;
                    }
                    if ( type != "uam" ) {
                        glucoseDatum.uamAbsorption = "start";
                        log.debug(glucoseDatum.uamAbsorption+" unannnounced meal absorption");
                    }
                    type="uam";
                    UAMGlucoseData.add(glucoseDatum);
                } else {
                    if ( type == "uam" ) {
                        log.debug("end unannounced meal absorption");
                    }


                    // Go through the remaining time periods and divide them into periods where scheduled basal insulin activity dominates. This would be determined by calculating the BG impact of scheduled basal insulin (for example 1U/hr * 48 mg/dL/U ISF = 48 mg/dL/hr = 5 mg/dL/5m), and comparing that to BGI from bolus and net basal insulin activity.
                    // When BGI is positive (insulin activity is negative), we want to use that data to tune basals
                    // When BGI is smaller than about 1/4 of basalBGI, we want to use that data to tune basals
                    // When BGI is negative and more than about 1/4 of basalBGI, we can use that data to tune ISF,
                    // unless avgDelta is positive: then that's some sort of unexplained rise we don't want to use for ISF, so that means basals
                    if (basalBGI > -4 * BGI) {
                        type="basal";
                        basalGlucoseData.add(glucoseDatum);
                    } else {
                        if ( avgDelta > 0 && avgDelta > -2*BGI ) {
                            //type="unknown"
                            type="basal";
                            basalGlucoseData.add(glucoseDatum);
                        } else {
                            type="ISF";
                            ISFGlucoseData.add(glucoseDatum);
                        }
                    }
                }
            }
            // debug line to print out all the things
//            BGDateArray = BGDate.toString().split(" ");
//            BGTime = BGDateArray[4];
            log.debug(absorbing+" mealCOB: "+mealCOB+" mealCarbs: "+mealCarbs+" basalBGI: "+round(basalBGI,1)+" BGI: "+BGI+" IOB: "+iob.iob+" at "+new Date(BGTime).toString()+" dev: "+deviation+" avgDelta: "+avgDelta +" "+ type);
        }

        try {
            List<Treatment> treatments = nsService.getTreatments(from, to);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BgReading CRDatum;
        /*CRData.forEach(function(CRDatum) {
            JSONObject dosedOpts = {
                    "treatments": treatments
                    , "profile": profile
                    , "start": CRDatum.CRInitialCarbTime
                    , "end": CRDatum.CREndTime};
        */
        // TODO: What this one does ?!?
        //double insulinDosed = dosed(dosedOpts);
        //CRDatum.CRInsulin = insulinDosed.insulin;
        //log.debug(CRDatum);});

        int CSFLength = CSFGlucoseData.size();
        int ISFLength = ISFGlucoseData.size();
        int UAMLength = UAMGlucoseData.size();
        int basalLength = basalGlucoseData.size();
        if (categorize_uam_as_basal) {
            log.debug("Categorizing all UAM data as basal.");
            basalGlucoseData.addAll(UAMGlucoseData);
        } else if (2*basalLength < UAMLength) {
            //log.debug(basalGlucoseData, UAMGlucoseData);
            log.debug("Warning: too many deviations categorized as UnAnnounced Meals");
            log.debug("Adding",UAMLength,"UAM deviations to",basalLength,"basal ones");
            basalGlucoseData.addAll(UAMGlucoseData);
            //log.debug(basalGlucoseData);
            // if too much data is excluded as UAM, add in the UAM deviations, but then discard the highest 50%
            // Todo: Try to sort it here
            /*basalGlucoseData.sort(function (a, b) {
                return a.deviation - b.deviation;
            });*/
            List<BGDatum> newBasalGlucose = new ArrayList<BGDatum>();;
            for(int i=0;i < basalGlucoseData.size()/2;i++){
                newBasalGlucose.add(basalGlucoseData.get(i));
            }
            //log.debug(newBasalGlucose);
            basalGlucoseData = newBasalGlucose;
            log.debug("and selecting the lowest 50%, leaving"+ basalGlucoseData.size()+"basal+UAM ones");

            log.debug("Adding "+UAMLength+" UAM deviations to "+ISFLength+" ISF ones");
            ISFGlucoseData.addAll(UAMGlucoseData);
            //log.debug(ISFGlucoseData.length, UAMLength);
        }
        basalLength = basalGlucoseData.size();
        ISFLength = ISFGlucoseData.size();
        if ( 4*basalLength + ISFLength < CSFLength && ISFLength < 10 ) {
            log.debug("Warning: too many deviations categorized as meals");
            //log.debug("Adding",CSFLength,"CSF deviations to",basalLength,"basal ones");
            //var basalGlucoseData = basalGlucoseData.concat(CSFGlucoseData);
            log.debug("Adding",CSFLength,"CSF deviations to",ISFLength,"ISF ones");
            for(int ii = 0; ii< CSFGlucoseData.size()-1;ii++) {
                ISFGlucoseData.add(CSFGlucoseData.get(ii));
            }
            CSFGlucoseData = new ArrayList<>();
        }

        log.debug("CRData: "+CRData.size());
        log.debug("CSFGlucoseData: "+CSFGlucoseData.size());
        log.debug("ISFGlucoseData: "+ISFGlucoseData.size());
        log.debug("BasalGlucoseData: "+basalGlucoseData.size());
//        String returnJSON = "{\"CRData\":"+CRData.toString()+",\"CSFGlucoseData\": "+CSFGlucoseData.toString()+",\"ISFGlucoseData\": "+ISFGlucoseData.toString()+",\"basalGlucoseData\": "+basalGlucoseData.toString()+"}";
//        log.debug("Returning: "+returnJSON);
        return;
    }


    public String tuneAllTheThings() throws JSONException {

//                var previousAutotune = inputs.previousAutotune;
        //log.debug(previousAutotune);
        JSONObject previousAutotune = new JSONObject();
        if(previousResult != null)
            previousAutotune = previousResult.optJSONObject("basalProfile");

        List<Double> basalProfile  = new ArrayList<Double>();
        //Parsing last result
        if(previousAutotune != null) {
            for (int i = 0; i < 24; i++) {
                basalProfile.add(previousAutotune.optDouble("" + i));
            }
        }

        Profile pumpProfile = profile;
        Profile pumpBasalProfile = profile;

        Profile pumpISFProfile = null;
        double pumpISF = 0d;
        double pumpCarbRatio = 0d;
        double pumpCSF = 0d;
        // Autosens constraints
        double autotuneMax = SafeParse.stringToDouble(SP.getString("openapsama_autosens_max", "1.2"));
        double autotuneMin = SafeParse.stringToDouble(SP.getString("openapsama_autosens_min", "0.7"));
        double min5minCarbImpact = SP.getDouble("openapsama_min_5m_carbimpact", 3.0);
        //log.debug(pumpBasalProfile);
//        var basalProfile = previousAutotune.basalprofile;
        //log.debug(basalProfile);
//        var isfProfile = previousAutotune.isfProfile;
        //log.debug(isfProfile);
        int toMgDl = 1;
        if(profile.equals(null))
            return null;
        if(profile.getUnits().equals("mmol"))
            toMgDl = 18;
        double ISF = profile.getIsf()*toMgDl;
        //log.debug(ISF);
        double carbRatio = profile.getIc();
        //log.debug(carbRatio);
        double CSF = ISF / carbRatio;
        // conditional on there being a pump profile; if not then skip
        if (pumpProfile != null) { pumpISFProfile = pumpProfile; }
        if (pumpISFProfile != null && pumpISFProfile.getIsf() != null) {
            pumpISF = pumpISFProfile.getIsf();
            pumpCarbRatio = pumpProfile.getIc();
            pumpCSF = pumpISF / pumpCarbRatio;
        }
        if (carbRatio == 0d) { carbRatio = pumpCarbRatio; }
        if (CSF == 0d) { CSF = pumpCSF; }
        if (ISF == 0d) { ISF = pumpISF; }
        //log.debug(CSF);
//        List<BGDatum> preppedGlucose = preppedGlucose;
        List<BGDatum> CSFGlucose = CSFGlucoseData;
        //log.debug(CSFGlucose[0]);
        List<BGDatum> ISFGlucose = ISFGlucoseData;
        //log.debug(ISFGlucose[0]);
        List<BGDatum> basalGlucose = basalGlucoseData;
        //log.debug(basalGlucose[0]);
        List<CRDatum> CRData = this.CRData;
        //log.debug(CRData);

        // Calculate carb ratio (CR) independently of CSF and ISF
        // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
        // For now, if another meal IOB/COB stacks on top of it, consider them together
        // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
        // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.

        double CRTotalCarbs = 0;
        double CRTotalInsulin = 0;
        List<CRDatum> CRDatum = new ArrayList<CRDatum>();
        for (int i=0; i<CRData.size()-1; i++) {
                double CRBGChange = CRData.get(i).CREndBG - CRData.get(i).CRInitialBG;
                double CRInsulinReq = CRBGChange / ISF;
                double CRIOBChange = CRData.get(i).CREndIOB - CRData.get(i).CRInitialIOB;
                CRData.get(i).CRInsulinTotal = CRData.get(i).CRInitialIOB + CRData.get(i).CRInsulin + CRInsulinReq;
                //log.debug(CRDatum.CRInitialIOB, CRDatum.CRInsulin, CRInsulinReq, CRInsulinTotal);
                double CR = Math.round(CRData.get(i).CRCarbs / CRData.get(i).CRInsulinTotal * 1000) / 1000;
                //log.debug(CRBGChange, CRInsulinReq, CRIOBChange, CRInsulinTotal);
                //log.debug("CRCarbs:",CRDatum.CRCarbs,"CRInsulin:",CRDatum.CRInsulinTotal,"CR:",CR);
                if (CRData.get(i).CRInsulin > 0) {
                    CRTotalCarbs += CRData.get(i).CRCarbs;
                    CRTotalInsulin += CRData.get(i).CRInsulinTotal;
                }
        }

        CRTotalInsulin = Math.round(CRTotalInsulin*1000)/1000;
        double totalCR = Math.round( CRTotalCarbs / CRTotalInsulin * 1000 )/1000;
        log.debug("CRTotalCarbs: "+CRTotalCarbs+" CRTotalInsulin: "+CRTotalInsulin+" totalCR: "+totalCR);

        // convert the basal profile to hourly if it isn't already
        List<Double> hourlyBasalProfile = new ArrayList<Double>();
        List<Double> hourlyPumpProfile = new ArrayList<Double>();
        for(int i=0; i<24; i++) {
            hourlyBasalProfile.add(i, getBasal(i));
//            log.debug("StartBasal at hour "+i+" is "+hourlyBasalProfile.get(i));
            hourlyPumpProfile.add(i, getBasal(i));
        }
        log.debug("1-1:"+hourlyBasalProfile.toString());
        //List<Double> basalProfile = new List<Double>;
        /*for (int i=0; i < 24; i++) {
            // autotuned basal profile
            for (int j=0; j < basalProfile.size(); ++j) {
                if (basalProfile[j].minutes <= i * 60) {
                    if (basalProfile[j].rate == 0) {
                        log.debug("ERROR: bad basalProfile",basalProfile[j]);
                        return;
                    }
                    hourlyBasalProfile[i] = JSON.parse(JSON.stringify(basalProfile[j]));
                }
            }
            hourlyBasalProfile[i].i=i;
            hourlyBasalProfile[i].minutes=i*60;
            var zeroPadHour = ("000"+i).slice(-2);
            hourlyBasalProfile[i].start=zeroPadHour + ":00:00";
            hourlyBasalProfile[i].rate=Math.round(hourlyBasalProfile[i].rate*1000)/1000
            // pump basal profile
            if (pumpBasalProfile && pumpBasalProfile[0]) {
                for (int j=0; j < pumpBasalProfile.length; ++j) {
                    //log.debug(pumpBasalProfile[j]);
                    if (pumpBasalProfile[j].rate == 0) {
                        log.debug("ERROR: bad pumpBasalProfile",pumpBasalProfile[j]);
                        return;
                    }
                    if (pumpBasalProfile[j].minutes <= i * 60) {
                        hourlyPumpProfile[i] = JSON.parse(JSON.stringify(pumpBasalProfile[j]));
                    }
                }
                hourlyPumpProfile[i].i=i;
                hourlyPumpProfile[i].minutes=i*60;
                hourlyPumpProfile[i].rate=Math.round(hourlyPumpProfile[i].rate*1000)/1000
            }
        }
        *///log.debug(hourlyPumpProfile);
        //log.debug(hourlyBasalProfile);
        List<Double> newHourlyBasalProfile = new ArrayList<Double>();
        for(int i=0; i<hourlyBasalProfile.size();i++){
            newHourlyBasalProfile.add(hourlyBasalProfile.get(i));
        }

        // look at net deviations for each hour
        for (int hour=0; hour < 24; hour++) {
            double deviations = 0;
            for (int i=0; i < basalGlucose.size(); ++i) {
                Date BGTime = null;

                if (basalGlucose.get(i).date != 0) {
                    BGTime = new Date(basalGlucose.get(i).date);
                }  else {
                    log.debug("Could not determine last BG time");
                }

                int myHour = BGTime.getHours();
                if (hour == myHour) {
                    //log.debug(basalGlucose[i].deviation);
                    deviations += basalGlucose.get(i).deviation;
                }
            }
            deviations = round( deviations,3);
            log.debug("Hour "+hour+" total deviations: "+deviations+" mg/dL");
            // calculate how much less or additional basal insulin would have been required to eliminate the deviations
            // only apply 20% of the needed adjustment to keep things relatively stable
            double basalNeeded = 0.2 * deviations / ISF;
            basalNeeded = round( basalNeeded,2);
            // if basalNeeded is positive, adjust each of the 1-3 hour prior basals by 10% of the needed adjustment
            log.debug("Hour "+hour+" basal adjustment needed: "+basalNeeded+" U/hr");
            if (basalNeeded > 0 ) {
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    //log.debug(offsetHour);
                    newHourlyBasalProfile.set(offsetHour, newHourlyBasalProfile.get(offsetHour) + basalNeeded / 3);
                    newHourlyBasalProfile.set(offsetHour, round(newHourlyBasalProfile.get(offsetHour),3));
                }
                // otherwise, figure out the percentage reduction required to the 1-3 hour prior basals
                // and adjust all of them downward proportionally
            } else if (basalNeeded < 0) {
                double threeHourBasal = 0;
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    threeHourBasal += newHourlyBasalProfile.get(offsetHour);
                }
                double adjustmentRatio = 1.0 + basalNeeded / threeHourBasal;
                //log.debug(adjustmentRatio);
                for (int offset=-3; offset < 0; offset++) {
                    int offsetHour = hour + offset;
                    if (offsetHour < 0) { offsetHour += 24; }
                    newHourlyBasalProfile.set(offsetHour, newHourlyBasalProfile.get(offsetHour) * adjustmentRatio);
                    newHourlyBasalProfile.set(offsetHour, round(newHourlyBasalProfile.get(offsetHour),3));
                }
            }
        }
        log.debug("1-2:"+hourlyBasalProfile.toString());
        if (pumpBasalProfile != null && pumpBasalProfile.getBasalValues() != null) {
            for (int hour=0; hour < 24; hour++) {
                //log.debug(newHourlyBasalProfile[hour],hourlyPumpProfile[hour].rate*1.2);
                // cap adjustments at autosens_max and autosens_min

                double maxRate = newHourlyBasalProfile.get(hour) * autotuneMax;
                double minRate = newHourlyBasalProfile.get(hour) * autotuneMin;
                if (newHourlyBasalProfile.get(hour) > maxRate ) {
                    log.debug("Limiting hour"+hour+"basal to"+round(maxRate,2)+"(which is "+round(autotuneMax,2)+"* pump basal of"+hourlyPumpProfile.get(hour)+")");
                    //log.debug("Limiting hour",hour,"basal to",maxRate.toFixed(2),"(which is 20% above pump basal of",hourlyPumpProfile[hour].rate,")");
                    newHourlyBasalProfile.set(hour,maxRate);
                } else if (newHourlyBasalProfile.get(hour) < minRate ) {
                    log.debug("Limiting hour",hour,"basal to"+round(minRate,2)+"(which is"+autotuneMin+"* pump basal of"+newHourlyBasalProfile.get(hour)+")");
                    //log.debug("Limiting hour",hour,"basal to",minRate.toFixed(2),"(which is 20% below pump basal of",hourlyPumpProfile[hour].rate,")");
                    newHourlyBasalProfile.set(hour, minRate);
                }
                newHourlyBasalProfile.set(hour, round(newHourlyBasalProfile.get(hour),3));
            }
        }

        // some hours of the day rarely have data to tune basals due to meals.
        // when no adjustments are needed to a particular hour, we should adjust it toward the average of the
        // periods before and after it that do have data to be tuned
        int lastAdjustedHour = 0;
        log.debug("1-3:"+hourlyBasalProfile.toString());
        // scan through newHourlyBasalProfile and find hours where the rate is unchanged
        for (int hour=0; hour < 24; hour++) {
            if (hourlyBasalProfile.get(hour).equals(newHourlyBasalProfile.get(hour))) {
                int nextAdjustedHour = 23;
                for (int nextHour = hour; nextHour < 24; nextHour++) {
                    if (! (hourlyBasalProfile.get(nextHour) == newHourlyBasalProfile.get(nextHour))) {
                        nextAdjustedHour = nextHour;
                        break;
                        } else {
                        log.debug("At hour: "+nextHour +" " +hourlyBasalProfile.get(nextHour)+" " +newHourlyBasalProfile.get(nextHour));
                    }
                }
                //log.debug(hour, newHourlyBasalProfile);
                newHourlyBasalProfile.set(hour, round( (0.8*hourlyBasalProfile.get(hour) + 0.1*newHourlyBasalProfile.get(lastAdjustedHour) + 0.1*newHourlyBasalProfile.get(nextAdjustedHour)),3));
                log.debug("Adjusting hour "+hour+" basal from "+hourlyBasalProfile.get(hour)+" to "+newHourlyBasalProfile.get(hour)+" based on hour "+lastAdjustedHour+" = "+newHourlyBasalProfile.get(lastAdjustedHour)+" and hour "+nextAdjustedHour+"="+newHourlyBasalProfile.get(nextAdjustedHour));
            } else {
                lastAdjustedHour = hour;
            }
        }
        log.debug("1-4:"+hourlyBasalProfile.toString());
        log.debug(newHourlyBasalProfile.toString());
        log.debug(hourlyBasalProfile.toString());
        basalProfile = newHourlyBasalProfile;

        // Calculate carb ratio (CR) independently of CSF and ISF
        // Use the time period from meal bolus/carbs until COB is zero and IOB is < currentBasal/2
        // For now, if another meal IOB/COB stacks on top of it, consider them together
        // Compare beginning and ending BGs, and calculate how much more/less insulin is needed to neutralize
        // Use entered carbs vs. starting IOB + delivered insulin + needed-at-end insulin to directly calculate CR.



        // calculate net deviations while carbs are absorbing
        // measured from carb entry until COB and deviations both drop to zero

        double deviations = 0;
        double mealCarbs = 0;
        double totalMealCarbs = 0;
        double totalDeviations = 0;
        double fullNewCSF;
        //log.debug(CSFGlucose[0].mealAbsorption);
        //log.debug(CSFGlucose[0]);
        for (int i=0; i < CSFGlucose.size(); ++i) {
            //log.debug(CSFGlucose[i].mealAbsorption, i);
            if ( CSFGlucose.get(i).mealAbsorption == "start" ) {
                deviations = 0;
                mealCarbs = CSFGlucose.get(i).mealCarbs;
            } else if (CSFGlucose.get(i).mealAbsorption == "end") {
                deviations += CSFGlucose.get(i).deviation;
                // compare the sum of deviations from start to end vs. current CSF * mealCarbs
                //log.debug(CSF,mealCarbs);
                double csfRise = CSF * mealCarbs;
                //log.debug(deviations,ISF);
                //log.debug("csfRise:",csfRise,"deviations:",deviations);
                totalMealCarbs += mealCarbs;
                totalDeviations += deviations;

            } else {
                deviations += Math.max(0*min5minCarbImpact,CSFGlucose.get(i).deviation);
                mealCarbs = Math.max(mealCarbs, CSFGlucose.get(i).mealCarbs);
            }
        }
        // at midnight, write down the mealcarbs as total meal carbs (to prevent special case of when only one meal and it not finishing absorbing by midnight)
        // TODO: figure out what to do with dinner carbs that don't finish absorbing by midnight
        if (totalMealCarbs == 0) { totalMealCarbs += mealCarbs; }
        if (totalDeviations == 0) { totalDeviations += deviations; }
        //log.debug(totalDeviations, totalMealCarbs);
        if (totalMealCarbs == 0) {
            // if no meals today, CSF is unchanged
            fullNewCSF = CSF;
        } else {
            // how much change would be required to account for all of the deviations
            fullNewCSF = Math.round( (totalDeviations / totalMealCarbs)*100 )/100;
        }
        // only adjust by 20%
        double newCSF = ( 0.8 * CSF ) + ( 0.2 * fullNewCSF );
        // safety cap CSF
        if (pumpCSF != 0d) {
            double maxCSF = pumpCSF * autotuneMax;
            double minCSF = pumpCSF * autotuneMin;
            if (newCSF > maxCSF) {
                log.debug("Limiting CSF to"+round(maxCSF,2)+"(which is"+autotuneMax+"* pump CSF of"+pumpCSF+")");
                newCSF = maxCSF;
            } else if (newCSF < minCSF) {
                log.debug("Limiting CSF to"+round(minCSF,2)+"(which is"+autotuneMin+"* pump CSF of"+pumpCSF+")");
                newCSF = minCSF;
            } //else { log.debug("newCSF",newCSF,"is close enough to",pumpCSF); }
        }
        double oldCSF = Math.round( CSF * 1000 ) / 1000;
        newCSF = Math.round( newCSF * 1000 ) / 1000;
        totalDeviations = Math.round ( totalDeviations * 1000 )/1000;
        log.debug("totalMealCarbs: "+totalMealCarbs+" totalDeviations: "+totalDeviations+" oldCSF "+oldCSF+" fullNewCSF: "+fullNewCSF+" newCSF: "+newCSF);
        // this is where CSF is set based on the outputs
        if (newCSF == 0) {
            CSF = newCSF;
        }
        double fullNewCR;
        if (totalCR == 0) {
            // if no meals today, CR is unchanged
            fullNewCR = carbRatio;
        } else {
            // how much change would be required to account for all of the deviations
            fullNewCR = totalCR;
        }
        // safety cap fullNewCR
        if (pumpCarbRatio != 0) {
            double maxCR = pumpCarbRatio * autotuneMax;
            double minCR = pumpCarbRatio * autotuneMin;
            if (fullNewCR > maxCR) {
                log.debug("Limiting fullNewCR from"+fullNewCR+"to"+round(maxCR,2)+"(which is"+autotuneMax+"* pump CR of"+pumpCarbRatio+")");
                fullNewCR = maxCR;
            } else if (fullNewCR < minCR) {
                log.debug("Limiting fullNewCR from"+fullNewCR+"to"+round(minCR,2)+"(which is"+autotuneMin+"* pump CR of"+pumpCarbRatio+")");
                fullNewCR = minCR;
            } //else { log.debug("newCR",newCR,"is close enough to",pumpCarbRatio); }
        }
        // only adjust by 20%
        double newCR = ( 0.8 * carbRatio ) + ( 0.2 * fullNewCR );
        // safety cap newCR
        if (pumpCarbRatio != 0) {
            double maxCR = pumpCarbRatio * autotuneMax;
            double minCR = pumpCarbRatio * autotuneMin;
            if (newCR > maxCR) {
                log.debug("Limiting CR to "+round(maxCR,2)+"(which is"+autotuneMax+"* pump CR of"+pumpCarbRatio+")");
                newCR = maxCR;
            } else if (newCR < minCR) {
                log.debug("Limiting CR to "+round(minCR,2)+"(which is"+autotuneMin+"* pump CR of"+pumpCarbRatio+")");
                newCR = minCR;
            } //else { log.debug("newCR",newCR,"is close enough to",pumpCarbRatio); }
        }
        newCR = Math.round( newCR * 1000 ) / 1000;
        log.debug("oldCR: "+carbRatio+" fullNewCR: "+fullNewCR+" newCR: "+newCR);
        // this is where CR is set based on the outputs
        //var ISFFromCRAndCSF = ISF;
        if (newCR != 0) {
            carbRatio = newCR;
            //ISFFromCRAndCSF = Math.round( carbRatio * CSF * 1000)/1000;
        }



        // calculate median deviation and bgi in data attributable to ISF
        List<Double> ISFdeviations =  new ArrayList<Double>();
        List<Double> BGIs =   new ArrayList<Double>();
        List<Double> avgDeltas =   new ArrayList<Double>();
        List<Double> ratios =   new ArrayList<Double>();
        int count = 0;
        for (int i=0; i < ISFGlucose.size(); ++i) {
            double deviation = ISFGlucose.get(i).deviation;
            ISFdeviations.add(deviation);
            double BGI = ISFGlucose.get(i).BGI;
            BGIs.add(BGI);
            double avgDelta = ISFGlucose.get(i).AvgDelta;
            avgDeltas.add(avgDelta);
            double ratio = 1 + deviation / BGI;
            //log.debug("Deviation:",deviation,"BGI:",BGI,"avgDelta:",avgDelta,"ratio:",ratio);
            ratios.add(ratio);
            count++;
        }
        Collections.sort(avgDeltas);
        Collections.sort(BGIs);
        Collections.sort(ISFdeviations);
        Collections.sort(ratios);
        double p50deviation = IobCobCalculatorPlugin.percentile(ISFdeviations.toArray(new Double[ISFdeviations.size()]), 0.50);
        double p50BGI =  IobCobCalculatorPlugin.percentile(BGIs.toArray(new Double[BGIs.size()]), 0.50);
        double p50ratios = Math.round(  IobCobCalculatorPlugin.percentile(ratios.toArray(new Double[ratios.size()]), 0.50) * 1000)/1000;
        double fullNewISF;
        if (count < 10) {
            // leave ISF unchanged if fewer than 5 ISF data points
            fullNewISF = ISF;
        } else {
            // calculate what adjustments to ISF would have been necessary to bring median deviation to zero
            fullNewISF = ISF * p50ratios;
        }
        fullNewISF = Math.round( fullNewISF * 1000 ) / 1000;
        // adjust the target ISF to be a weighted average of fullNewISF and pumpISF
        double adjustmentFraction;
/*
        if (typeof(pumpProfile.autotune_isf_adjustmentFraction) !== 'undefined') {
            adjustmentFraction = pumpProfile.autotune_isf_adjustmentFraction;
        } else {*/
            adjustmentFraction = 1.0;
//        }

        // low autosens ratio = high ISF
        double maxISF = pumpISF / autotuneMin;
        // high autosens ratio = low ISF
        double minISF = pumpISF / autotuneMax;
        double adjustedISF = 0d;
        double newISF = 0d;
        if (pumpISF == 0) {
            if ( fullNewISF < 0 ) {
                adjustedISF = ISF;
            } else {
                adjustedISF = adjustmentFraction*fullNewISF + (1-adjustmentFraction)*pumpISF;
            }
            // cap adjustedISF before applying 10%
            //log.debug(adjustedISF, maxISF, minISF);
            if (adjustedISF > maxISF) {
                log.debug("Limiting adjusted ISF of"+round(adjustedISF,2)+"to"+round(maxISF,2)+"(which is pump ISF of"+pumpISF+"/"+autotuneMin+")");
                adjustedISF = maxISF;
            } else if (adjustedISF < minISF) {
                log.debug("Limiting adjusted ISF of"+round(adjustedISF,2)+"to"+round(minISF,2)+"(which is pump ISF of"+pumpISF+"/"+autotuneMax+")");
                adjustedISF = minISF;
            }

            // and apply 20% of that adjustment
            newISF = ( 0.8 * ISF ) + ( 0.2 * adjustedISF );

            if (newISF > maxISF) {
                log.debug("Limiting ISF of"+round(newISF,2)+"to"+round(maxISF,2)+"(which is pump ISF of"+pumpISF+"/"+autotuneMin+")");
                newISF = maxISF;
            } else if (newISF < minISF) {
                log.debug("Limiting ISF of"+round(newISF,2)+"to"+round(minISF,2)+"(which is pump ISF of"+pumpISF+"/"+autotuneMax+")");
                newISF = minISF;
            }
        }
        newISF = Math.round( newISF * 1000 ) / 1000;
        //log.debug(avgRatio);
        //log.debug(newISF);
        p50deviation = Math.round( p50deviation * 1000 ) / 1000;
        p50BGI = Math.round( p50BGI * 1000 ) / 1000;
        adjustedISF = Math.round( adjustedISF * 1000 ) / 1000;
        log.debug("p50deviation: "+p50deviation+" p50BGI "+p50BGI+" p50ratios: "+p50ratios+" Old ISF: "+ISF+" fullNewISF: "+fullNewISF+" adjustedISF: "+adjustedISF+" newISF: "+newISF);

        if (newISF != 0d) {
            ISF = newISF;
        }


        // reconstruct updated version of previousAutotune as autotuneOutput
        JSONObject autotuneOutput = previousAutotune;
        autotuneOutput.put("basalprofile",  basalProfile.toString());
        //isfProfile.sensitivity = ISF;
        //autotuneOutput.put("isfProfile", isfProfile);
        autotuneOutput.put("sens", ISF);
        autotuneOutput.put("csf", CSF);
        //carbRatio = ISF / CSF;
        carbRatio = Math.round( carbRatio * 1000 ) / 1000;
        autotuneOutput.put("carb_ratio" , carbRatio);
        previousResult = autotuneOutput;
        return autotuneOutput.toString();
    }

    public  String result(int daysBack) throws IOException, ParseException {

        tunedISF = 0;
        double isfResult = 0;
        basalsResultInit();
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now - ((daysBack-1) * 24 * 60 * 60 * 1000L));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        // midnight
        long endTime = c.getTimeInMillis();
        long starttime = endTime - (24 * 60 * 60 * 1000L);
        Date lastProfileChange = NSService.lastProfileChange();


        //Check if Wifi is Connected
        if(!nsService.isWifiConnected()){
           // return "READ THE WARNING!";
        }

        // check if daysBack goes before the last profile switch
        if((System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)) < lastProfileChange.getTime()){
            return "ERROR -> I cannot tune before the last profile switch!("+(System.currentTimeMillis() - lastProfileChange.getTime()) / (24 * 60 * 60 * 1000L)+" days ago)";
        }
        if(daysBack < 1){
            return "Sorry I cannot do it for less than 1 day!";
        } else {
            for (int i = daysBack; i > 0; i--) {
                tunedBasalsInit();
                try {
                    categorizeBGDatums(starttime, endTime);
                    tuneAllTheThings();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        //
        return "Something was tuned(maybe)!";
    }

    String basicResult(int daysBack) throws IOException, ParseException {
        // get some info and spit out a suggestion
        // TODO: Same function for ISF and CR
        // time now
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now - ((daysBack-1) * 24 * 60 * 60 * 1000L));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        // midnight
        long endTime = c.getTimeInMillis();
        long starttime = endTime - (24 * 60 * 60 * 1000L);
        // now we have our start and end of day
        int averageBG = 0;
        double basalNeeded = 0d;
        double isf = getISF();
        double daylyISF = isf;
        double deviation = 0d;
        double netDeviation = 0d;
        double averageRatio = 0;
        int counter = 0;
        tunedBasalsInit();
        log.debug("CheckPoint 12-8-0 creating bucketed_data for "+new Date(endTime).toLocaleString());
        createBucketedData(endTime);
        if(bucketed_data != null) {
            // No data return same basals
            log.debug("CheckPoint 12-8-1 bucketed_data size " + bucketed_data.size() + " end is " + new Date(endTime).toLocaleString());
//            return "No BG data for this day";
        }
        calculateSensitivityData(starttime, endTime);
        if(autosensDataTable.size() >0 ) {
            log.debug("CheckPoint 12-8-2 calculated sensitivityDataTable size " + autosensDataTable.size() + " end is " + new Date(endTime));
            log.debug("CheckPoint 12-8-2 start:" + new Date(autosensDataTable.valueAt(0).time).toLocaleString());
            log.debug("CheckPoint 12-8-2 end  :" + new Date(autosensDataTable.valueAt(autosensDataTable.size()-1).time).toLocaleString());
        } else
            log.debug("CheckPoint 12-8-3 - autoSensDataTable is "+autosensDataTable.size());
        AutosensData autosensData = getAutosensData(endTime);

        // Detecting sensitivity for the whole day
        //AutosensResult autosensResult = detectSensitivity(starttime, endTime);
//        AutosensResult autosensResult = new AutosensResult();
//        log.debug("CheckPoint 7-12 sensitivity is "+autosensResult.ratio +" from "+new Date(starttime).toString()+" to "+new Date(endTime));

        for (int i = 0; i < 24; i++) {

            // get average BG
            //log.debug("CheckPoint 12-3 - getting glucose");
            getPlugin().getBGFromTo(starttime, endTime);
            //log.debug("CheckPoint 12-3 - records "+getPlugin().glucose_data.size());
            averageBG = getPlugin().averageGlucose(starttime + (i * 60 * 60 * 1000l), starttime + (i + 1) * 60 * 60 * 1000L);
            // get ISF
            isf = getISF();

            // Look at netDeviations for each hour
            // Get AutoSensData for 5 min deviations

            // initialize
            deviation = 0;

            for (long time = starttime + (i * 60 * 60 * 1000l); time <= starttime + (i + 1) * 60 * 60 * 1000L; time += 5 * 60 * 1000L) {
                //getPlugin().createBucketedData(time);
                //Try to get autosens for the whole day not exact time like here
                //getPlugin().calculateSensitivityData(starttime, time);
//                log.debug("Getting autosens for "+new Date(time).toLocaleString());
                autosensData = getAutosensData(time);
                if(autosensData == null) {
                    autosensData = getLastAutosensData();
                    if(autosensData == null)
                        return "I cannot live without autosens!";
                }

                if (autosensData != null) {
                    deviation += autosensData.deviation;
//                    log.debug("Dev is:"+deviation+" at "+new Date(autosensData.time).toLocaleString());
//                    counter++;
                } //else
//                    log.debug("CheckPoint 6-3 Cannot get autosens data for "+time);

            }
            // use net dev not average
            netDeviation = deviation;
            averageRatio += autosensData.autosensRatio;
            counter++;
            log.debug("netDeviation at "+i+" "+netDeviation+" ratio "+autosensData.autosensRatio);
            // calculate how much less or additional basal insulin would have been required to eliminate the deviations
            // only apply 20% of the needed adjustment to keep things relatively stable
            basalNeeded = 0.2 * netDeviation / isf;
            basalNeeded = round(basalNeeded,2);
            log.debug("insulin needed: "+basalNeeded);
                // if basalNeeded is positive, adjust each of the 1-3 hour prior basals by 10% of the needed adjustment
                if (basalNeeded > 0) {
                    for (int offset = -3; offset < 0; offset++) {
                        int offsetHour = i + offset;
                        if (offsetHour < 0) {
                            offsetHour += 24;
                        }
                        //log.debug(offsetHour);
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
                    //log.debug(adjustmentRatio);
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
                                //log.debug(nextHour, hourlyBasalProfile[nextHour].rate, newHourlyBasalProfile[nextHour].rate);
                            }
                        }
                        //log.debug(hour, newHourlyBasalProfile);
                        tunedBasals.set(hour, round((0.8 * profile.getBasal(hour * 3600) + 0.1 * tunedBasals.get(lastAdjustedHour) + 0.1 * tunedBasals.get(nextAdjustedHour) * 1000) / 1000, 2));
                    } else {
                        lastAdjustedHour = hour;
                    }
                }


            }

            // Needded for the ISF tuning
            averageRatio = averageRatio / counter;
            for (int ii = 0; ii < 24; ii++) {
                //log.debug(newHourlyBasalProfile[hour],hourlyPumpProfile[hour].rate*1.2);
                // cap adjustments at autosens_max and autosens_min
                double autotuneMax = SafeParse.stringToDouble(SP.getString("openapsama_autosens_max", "1.2"));
                double autotuneMin = SafeParse.stringToDouble(SP.getString("openapsama_autosens_min", "0.7"));
                double maxRate = tunedBasals.get(ii) * autotuneMax;
                double minRate = tunedBasals.get(ii) * autotuneMin;
                if (tunedBasals.get(ii) > maxRate ) {
//                    log.debug("Limiting hour",hour,"basal to",maxRate.toFixed(2),"(which is",autotuneMax,"* pump basal of",hourlyPumpProfile[hour].rate,")");
                    log.debug("Limiting hour"+tunedBasals.get(ii)+"basal to"+maxRate+"(which is 20% above pump basal of");
                    tunedBasals.set(ii, maxRate);
                } else if (tunedBasals.get(ii) < minRate ) {
//                    log.debug("Limiting hour",hour,"basal to",minRate.toFixed(2),"(which is",autotuneMin,"* pump basal of",hourlyPumpProfile[hour].rate,")");
                    log.debug("Limiting hour "+tunedBasals.get(ii)+"basal to "+minRate+"(which is 20% below pump basal of");
                    tunedBasals.set(ii, minRate);
                } else {
                    tunedBasals.set(ii, round(tunedBasals.get(ii), 3));
                    daylyISF += isf / averageRatio;
                    log.debug("Tuned at " + ii + " is " + tunedBasals.get(ii) + " ratio is " + autosensData.autosensRatio + " average " + averageRatio);
                }

            }
            tunedISF = daylyISF / 24;
            if (averageBG > 0){
//                tunedISF += isf / autosensData.autosensRatio;
                log.debug("Tuned ISF is "+tunedISF);
                log.debug("Tuning from "+new Date(starttime).toLocaleString()+" to "+new Date(endTime).toLocaleString()+" took "+((System.currentTimeMillis()-now)/1000L)+" s");
                return averageBG + "\n" + displayBasalsResult();
            }
            else return "No BG data!(basicResult()";

    }


    public static String displayBasalsResult(){
        String result = "";
        for(int i=0;i<24; i++){
            if(i<10)
                result += "\n"+i+"  | "+getBasal(i)+" -> "+basalsResult.get(i);
            else
                result += "\n"+i+" | "+getBasal(i)+" -> "+basalsResult.get(i);
        }
        return result;
    }

    public static void tunedBasalsInit(){
        // initialize tunedBasals
        if(tunedBasals.isEmpty()) {
            //log.debug("TunedBasals is called!!!");
            for (int i = 0; i < 24; i++) {
                tunedBasals.add(getBasal(i));
            }
        } else {
            for (int i = 0; i < 24; i++) {
                tunedBasals.set(i, getBasal(i));
            }
        }
    }

    public static void basalsResultInit(){
        // initialize basalsResult if
//        log.debug(" basalsResult init");
        if(basalsResult.isEmpty()) {
            for (int i = 0; i < 24; i++) {
                basalsResult.add(0d);
            }
        } else {
            for (int i = 0; i < 24; i++) {
                basalsResult.set(i, 0d);
            }
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
