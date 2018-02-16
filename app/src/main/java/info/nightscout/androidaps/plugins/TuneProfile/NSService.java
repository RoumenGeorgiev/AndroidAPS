package info.nightscout.androidaps.plugins.TuneProfile;

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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.utils.SP;


/**
 * Created by Rumen Georgiev on 2/16/2018.
 * Class to read data from NS directly
 *
 */

public class NSService {
    private static Logger log = LoggerFactory.getLogger(NSService.class);
    public List<BgReading> sgv = new ArrayList<BgReading>();

    public NSService() throws IOException {
        String nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        String sgvValues = "api/v1/entries/sgv.json";
        URL url = new URL(nsURL+sgvValues);
        log.debug("URL is:"+nsURL+sgvValues);
        List<BgReading> sgv = new ArrayList<BgReading>();
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder total = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line).append('\n');
            }
            log.debug("NS-values:"+total);
            JSONArray values = new JSONArray(total.toString());
            for(int i = 0; i<values.length(); i++) {
                log.debug("\n"+i+" -> " + values.get(i).toString());
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
    }


    public List<BgReading> getSgvValues() throws IOException {
        String nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        String sgvValues = "api/v1/entries/sgv.json";
        URL url = new URL(nsURL+sgvValues);
        log.debug("URL is:"+nsURL+sgvValues);
        List<BgReading> sgv = new ArrayList<BgReading>();
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder total = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                total.append(line).append('\n');
            }
            log.debug("NS-values:"+total);
            JSONArray values = new JSONArray(total.toString());
            for(int i = 0; i<values.length(); i++) {
                log.debug("\n"+i+" -> " + values.get(i).toString());
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
        return sgv;
    }
}
