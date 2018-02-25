package info.nightscout.androidaps.plugins.TuneProfile;
// These two are needed for wifi check
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.R2;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BGDatum;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.utils.SP;

import static info.nightscout.androidaps.plugins.TuneProfile.TuneProfile.profile;


/**
 * Created by Rumen Georgiev on 2/16/2018.
 * Class to read data from NS directly
 *
 */

public class NSService {
    private static Logger log = LoggerFactory.getLogger(NSService.class);
    public List<BgReading> sgv = new ArrayList<BgReading>();
    public List<Treatment> treatments = new ArrayList<Treatment>();
    Context mContext = MainApp.instance().getApplicationContext();


    public NSService() throws IOException {

    }

    public boolean isWifiConnected(){
        ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        return mWifi.isConnected();
    }

    public List<BgReading> getSgvValues(long from, long to) throws IOException, ParseException {
        String nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        if((nsURL.charAt(nsURL.length() - 1)) != '/')
            nsURL = nsURL + '/';
        // URL should look like http://localhost:1337/api/v1/entries/sgv.json?find[dateString][$gte]=2015-08-28&find[dateString][$lte]=2015-08-30
        String sgvValues = "api/v1/entries/sgv.json?find[date][$gte]="+from+"&find[date][$lte]="+to;
        URL url = new URL(nsURL+sgvValues+"&[count]=400");
//        log.debug("URL is:"+nsURL+sgvValues);
        List<BgReading> sgv = new ArrayList<BgReading>();
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder total = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line);
            }
//            log.debug("NS-values:"+total);
            JSONArray values = new JSONArray(total.toString());
            for(int i = 0; i<values.length(); i++) {
//                log.debug("\n"+i+" -> " + values.get(i).toString());
                JSONObject sgvJson = new JSONObject(values.get(i).toString());
                BgReading bgReading = new BgReading();
                bgReading.date = sgvJson.getLong("date");
                bgReading.value = sgvJson.getDouble("sgv");
                bgReading.direction = sgvJson.getString("direction");
                //bgReading.raw = sgvJson.getLong("raw");
                bgReading._id = sgvJson.getString("_id");
                sgv.add(bgReading);
            }
            log.debug("Size of SGV: "+sgv.size());


        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            urlConnection.disconnect();
        }
        // SGV values returned by NS are in descending order we need to put them in ascending
        // reverse the list
        /*List<BgReading> reversedSGV = new ArrayList<BgReading>();
        for(int i=sgv.size()-1; i>-1; i--){
            reversedSGV.add(sgv.get(i));
        }*/
        return sgv;
    }

    public List<Treatment> getTreatments(long from, long to) throws IOException, ParseException {
        String nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        if((nsURL.charAt(nsURL.length() - 1)) != '/')
            nsURL = nsURL + '/';

        Date fromDate = new Date(from);
        Date toDate = new Date(to);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat NSdateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String fromDateString = fromDate.toString();
        String toDateString = toDate.toString();
        String sgvValues = "api/v1/treatments.json?find[created_at][$gte]="+dateFormat.format(fromDate)+"&find[created_at][$lte]="+dateFormat.format(toDate);
        URL url = new URL(nsURL+sgvValues+"&[count]=400");
        log.debug("URL is:"+nsURL+sgvValues);
        List<Treatment> treatments = new ArrayList<Treatment>();
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder total = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line);
            }
//            log.debug("NS-values:"+total);
            JSONArray values = new JSONArray(total.toString());
            for(int i = 0; i<values.length(); i++) {
//                log.debug("\n"+i+" -> " + values.get(i).toString());
                JSONObject treatmentJson = new JSONObject(values.get(i).toString());
                Treatment treatment = new Treatment();
                String date = treatmentJson.optString("created_at");
                treatment.date = NSdateFormat.parse(date).getTime();
//                log.debug("Treatment date is "+date+" but formated is "+new Date(treatment.date).toString());
                treatment.insulin = treatmentJson.optDouble("insulin", 0d);
                treatment.carbs = treatmentJson.optDouble("carbs", 0d);
                treatment._id = treatmentJson.getString("_id");
                treatments.add(treatment);
//                log.debug("Treatment got has date "+new Date(treatment.date).toGMTString());
            }
//            log.debug("Treatment Size of treatments: "+treatments.size());


        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            urlConnection.disconnect();
        }
        // SGV values returned by NS are in descending order we need to put them in ascending
        // reverse the list
/*        List<Treatment> reversedTreatments = new ArrayList<Treatment>();
        for(int i=treatments.size()-1; i>-1; i--){
            reversedTreatments.add(treatments.get(i));
        }*/
        return treatments;
    }

    public JSONObject categorizeBGDatums(long from, long to) throws JSONException, ParseException {
        // TODO: Although the data from NS should be sorted maybe we need to sort it
        // sortBGdata
        // sort treatments

        //starting variable at 0
        //TODO: Add this to preferences
        boolean categorize_uam_as_basal = false;
        int boluses = 0;
        int maxCarbs = 0;
        //console.error(treatments);
        try {
            treatments = getTreatments(from,to);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (treatments.size() < 1) {
            log.debug("No treatments");
            return null;
        }
        //glucosedata is sgv
        try {
            sgv = getSgvValues(from, to);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sgv.size() < 1) {
            log.debug("No SGV data");
            return null;
        }
        JSONObject IOBInputs = new JSONObject();
        IOBInputs.put("profile", profile);
        IOBInputs.put("history", "pumpHistory");
        List<BGDatum> CSFGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> ISFGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> basalGlucoseData = new ArrayList<BGDatum>();
        List<BGDatum> UAMGlucoseData = new ArrayList<BGDatum>();
        List<JSONObject> CRData = new ArrayList<JSONObject>();

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
            } else { console.error("Could not determine BG time"); }
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
        //console.error(treatments);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss''");
        log.debug("Treatments size before clear: "+treatments.size());
        log.debug("Treatment(0) "+new Date(treatments.get(0).date).toString()+" last "+new Date(treatments.get(treatments.size()-1).date).toString());
        for (int i=treatments.size()-1; i>0; --i) {
            Treatment treatment = treatments.get(i);
            //console.error(treatment);
            if (treatment != null) {
                Date treatmentDate = new Date(treatment.date);
                long treatmentTime = treatmentDate.getTime();
                BgReading glucoseDatum = bucketedData.get(bucketedData.size()-1);
                //console.error(glucoseDatum);
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
        //console.error(treatments);

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
            //console.error(glucoseDatum);
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
                    //console.error(treatmentDate);
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
                //console.error(bucketedData[i]);
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
            //console.error(IOBInputs.history[0].created_at);
            List<Treatment> newHistory = null;
            for (int h=0; h<fullHistory.size(); h++) {
//                Date hDate = new Date(fullHistory.get(h).created_at) TODO: When we get directly from Ns there should be created_at
                Date hDate = new Date(fullHistory.get(h).date);
                //console.error(fullHistory[i].created_at, hDate, BGDate, BGDate-hDate);
                //if (h == 0 || h == fullHistory.length - 1) {
                //console.error(hDate, BGDate, hDate-BGDate)
                //}
                if (BGDate.getTime()-hDate.getTime() < 6*60*60*1000 && BGDate.getTime()-hDate.getTime() > 0) {
                    //process.stderr.write("i");
                    //console.error(hDate);
                    newHistory.add(fullHistory.get(h));
                }
            }
            if(newHistory != null)
                IOBInputs = new JSONObject(newHistory.toString());
            else
                IOBInputs = new JSONObject();
            // process.stderr.write("" + newHistory.length + " ");
            //console.error(newHistory[0].created_at,newHistory[newHistory.length-1].created_at,newHistory.length);


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

            //console.error(currentBasal,basal1hAgo,basal2hAgo,basal3hAgo,IOBInputs.profile.currentBasal);
            // basalBGI is BGI of basal insulin activity.
            double basalBGI = Math.round(( currentBasal * sens / 60 * 5 )*100)/100; // U/hr * mg/dL/U * 1 hr / 60 minutes * 5 = mg/dL/5m
            //console.log(JSON.stringify(IOBInputs.profile));
            // call iob since calculated elsewhere
            IobTotal iob = TuneProfile.calculateFromTreatmentsAndTemps(BGDate.getTime());
            //console.error(JSON.stringify(iob));

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
                    return null;
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
                    JSONObject CRDatum = new JSONObject(crDataString);
                    //console.error(CRDatum);

                    int CRElapsedMinutes = Math.round((CREndTime.getTime() - CRInitialCarbTime.getTime()) / (1000 * 60));
                    //console.error(CREndTime - CRInitialCarbTime, CRElapsedMinutes);
                    if ( CRElapsedMinutes < 60 || ( i==1 && mealCOB > 0 ) ) {
                        log.debug("Ignoring "+CRElapsedMinutes+" m CR period.");
                    } else {
                        CRData.add(CRDatum);
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
                //console.error(type);
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
            List<Treatment> treatments = getTreatments(from, to);
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
            //console.error(CRDatum);});

        int CSFLength = CSFGlucoseData.size();
        int ISFLength = ISFGlucoseData.size();
        int UAMLength = UAMGlucoseData.size();
        int basalLength = basalGlucoseData.size();
        if (categorize_uam_as_basal) {
            log.debug("Categorizing all UAM data as basal.");
            basalGlucoseData.addAll(UAMGlucoseData);
        } else if (2*basalLength < UAMLength) {
            //console.error(basalGlucoseData, UAMGlucoseData);
            log.debug("Warning: too many deviations categorized as UnAnnounced Meals");
            log.debug("Adding",UAMLength,"UAM deviations to",basalLength,"basal ones");
            basalGlucoseData.addAll(UAMGlucoseData);
            //console.error(basalGlucoseData);
            // if too much data is excluded as UAM, add in the UAM deviations, but then discard the highest 50%
            // Todo: Try to sort it here
            /*basalGlucoseData.sort(function (a, b) {
                return a.deviation - b.deviation;
            });*/
            List<BGDatum> newBasalGlucose = new ArrayList<BGDatum>();;
            for(int i=0;i < basalGlucoseData.size()/2;i++){
                newBasalGlucose.add(basalGlucoseData.get(i));
            }
            //console.error(newBasalGlucose);
            basalGlucoseData = newBasalGlucose;
            log.debug("and selecting the lowest 50%, leaving"+ basalGlucoseData.size()+"basal+UAM ones");

            log.debug("Adding "+UAMLength+" UAM deviations to "+ISFLength+" ISF ones");
            ISFGlucoseData.addAll(UAMGlucoseData);
            //console.error(ISFGlucoseData.length, UAMLength);
        }
        basalLength = basalGlucoseData.size();
        ISFLength = ISFGlucoseData.size();
        if ( 4*basalLength + ISFLength < CSFLength && ISFLength < 10 ) {
            log.debug("Warning: too many deviations categorized as meals");
            //console.error("Adding",CSFLength,"CSF deviations to",basalLength,"basal ones");
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
        String returnJSON = "{\"CRData\":"+CRData.toString()+",\"CSFGlucoseData\": "+CSFGlucoseData.toString()+",\"ISFGlucoseData\": "+ISFGlucoseData.toString()+",\"basalGlucoseData\": "+basalGlucoseData.toString()+"}";
//        log.debug("Returning: "+returnJSON);
        return new JSONObject(returnJSON);
    }


    static Date lastProfileChange() throws IOException {
        // Find a way to get the time of last profile change from ns so we should run the tune not before it
        String profileDateSting = "";
        long profileDate;
        Date lastChange = new Date(0);
        String nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        // URL should look like http://localhost:1337/api/v1/entries/sgv.json?find[dateString][$gte]=2015-08-28&find[dateString][$lte]=2015-08-30
        String profileQuery = "/api/v1/treatments.json?find[eventType]=Profile%20Switch&[count]=1";
        URL url = new URL(nsURL+profileQuery);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder total = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line);
            }
            JSONArray values = new JSONArray(total.toString());
            for(int i = 0; i<values.length(); i++) {
                JSONObject profileJson = new JSONObject(values.get(i).toString());
                profileDateSting = profileJson.getString("created_at");
                if(profileDateSting == null)
                    return new Date(0);
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                try {
                    lastChange = format.parse(profileDateSting);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }


        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            urlConnection.disconnect();
        }
        return lastChange;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
