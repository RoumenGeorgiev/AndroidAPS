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
import info.nightscout.androidaps.plugins.Treatments.Treatment;
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
//        log.debug("URL is:"+nsURL+sgvValues);
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
            log.debug("Treatment Size of treatments: "+treatments.size()+" from "+fromDateString+" to "+toDateString );


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
