package info.nightscout.androidaps.plugins.GoogleAssistant;

import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.NSClientInternal.UploadQueue;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by Rumen on 09.01.2018.
 */
public class GoogleAssistantPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(GoogleAssistantPlugin.class);


    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = false;

    private static GoogleAssistantPlugin plugin = null;
    public static GoogleAssistantPlugin getPlugin() {
        if (plugin == null)
            //log.debug("Assistant plugin is null!");
            plugin = new GoogleAssistantPlugin();
        return plugin;
    }


    // Idea is: Google assitant should create treatments in Nightscout
    // in these treatments should be createdBy "assistant" and note should be token
    // If token is the first form tokents - execute it, remove token from list, remove treatment from NS and create it by AAPS
    // if token is wrong - wrongToken++
    // if wrongToken >2 disable plugin

    String[] tokens = new String[10];
    String[] tokensAvailable = new String[10];

    public String tokensString = "No tokens yet";

    //initialize tokens
    public void setTokens(){
        this.tokens[0] = "one";
        this.tokens[1] = "two";
        this.tokens[2] = "three";
        this.tokens[3] = "four";
        this.tokens[4] = "five";
        this.tokens[5] = "six";
        this.tokens[6] = "seven";
        this.tokens[7] = "eight";
        this.tokens[8] = "nine";
        this.tokens[9] = "ten";
    }

    public void setTokensAvailable(){
        tokensAvailable[0] = "London";
        tokensAvailable[1] = "Rome";
        tokensAvailable[2] = "New York";
        tokensAvailable[3] = "Chicago";
        tokensAvailable[4] = "Boston";
        tokensAvailable[5] = "Madrid";
        tokensAvailable[6] = "Paris";
        tokensAvailable[7] = "Tokyo";
        tokensAvailable[8] = "Sydney";
        tokensAvailable[9] = "Liverpool";

    }

    public void updateTokens(){
        // we need the availableTokens to be set
        setTokensAvailable();
        // first token is no more so second becomes first and number 5 is random
        for(int i = 1; i < 5; i++){
            this.tokens[i-1] = this.tokens[i];
            SP.putString("token"+(i-1),this.tokens[i]);
        }

        //Prevention from repeating tokens
        while(this.tokens[3] == this.tokens[4]) {
            this.tokens[4] = randomToken();
        }
        SP.putString("token4", this.tokens[4]);
        log.debug("Assistant: updated first token is: " + this.tokens[0]);
        log.debug("Assistant: updating Tokenstring also "+tokensToString());

    }

    public String randomToken(){
        int randomNumber = (int) Math.ceil(Math.random() * 10);
        // if randomnumber is 10 ->try again
        while(randomNumber == 10) {
            randomNumber = (int) Math.ceil(Math.random() * 10);
        }
        //log.debug("Assistant random token is: "+this.tokens[randomNumber]);
        return this.tokensAvailable[randomNumber];
    }


    public String tokensToString(){
        if(this.tokens[0] == null) {
            log.debug("Assistant: tokens[0] is null");
            getTokens();
        }
        tokensString = this.tokens[0] + ";" + this.tokens[1] + ";" + this.tokens[2] + ";" + this.tokens[3] + ";" + this.tokens[4] + ";";
        log.debug("Assistant:(tokenToString()) tokenString is:"+tokensString);
        //log.debug("Assistant random token is:"+randomToken());
        //updateTokens();
        //tokensString = this.tokens[0] + ";" + this.tokens[1] + ";" + this.tokens[2] + ";" + this.tokens[3] + ";" + this.tokens[4] + ";";
        //log.debug("Assistant updated tokenString is:"+tokensString);
        //updateTokens();
        //tokensString = this.tokens[0] + ";" + this.tokens[1] + ";" + this.tokens[2] + ";" + this.tokens[3] + ";" + this.tokens[4] + ";";
        //log.debug("Assistant updated 2 tokenString is:"+tokensString);
        return tokensString;
    }


    String getTokens(){
        if(this.tokens[0] == null) {
            setTokensAvailable();
            this.tokens[0] = SP.getString("token0", "one");
            this.tokens[1] = SP.getString("token1", "two");
            this.tokens[2] = SP.getString("token2", "three");
            this.tokens[3] = SP.getString("token3", "four");
            this.tokens[4] = SP.getString("token4", "five");
            /*
            this.tokens[0] = tokensAvailable[0];
            this.tokens[1] = tokensAvailable[1];
            this.tokens[2] = tokensAvailable[2];
            this.tokens[3] = tokensAvailable[3];
            this.tokens[4] = tokensAvailable[4];
            */
        }
        /*this.tokens[0] = "one";
        this.tokens[1] = "two";
        this.tokens[2] = "three";
        this.tokens[3] = "four";
        this.tokens[4] = "five";
        /*
        this.tokens[0] = SP.getString("token0", "one");
        this.tokens[1] = SP.getString("token1", "two");
        this.tokens[2] = SP.getString("token2", "three");
        this.tokens[3] = SP.getString("token3", "four");
        this.tokens[4] = SP.getString("token4", "five");

        this.tokens[5] = SP.getString("token5", "six");

        this.tokens[6] = SP.getString("token6", "seven");
        this.tokens[7] = SP.getString("token7", "eight");
        this.tokens[8] = SP.getString("token8", "nine");
        this.tokens[9] = SP.getString("token9", "ten");
         */
        tokensToString();

        return tokensString;
    }

    public void newTreatment(JSONObject treatment){
        // check if treatment is from assisant
        //log.debug("Assistant: entered function for newTreatment");
        try {
            if (treatment.has("enteredBy")) {
                //log.debug("Assistant: treatment entered by " + treatment.getString("enteredBy"));
                // Now check token
                if(treatment.has("notes")) {
                    // ok treatment is entered from assistant but is it right
                    // check for insulin
                    // check for carbs
                    // check for token ?!?
                    log.debug("Assistant: treatment token is " + treatment.getString("notes"));
                    //log.debug("Assistant: treatment is: "+ treatment.toString());
                    long minutesAgo = (System.currentTimeMillis() - treatment.getLong("mills")) / (60 * 1000L);
                    log.debug("Assistant: treatment was entered: "+ minutesAgo +" min ago");
                    log.debug("Assistant: (newTreatment()) tokenString is: "+ getTokens());
                    log.debug("Assistant: token is: "+ this.tokens[0]);
                    log.debug("Assistant: insulin is: "+ treatment.optDouble("insulin", 0d));
                    log.debug("Assistant: carbs is: "+ treatment.optDouble("carbs", 0d));
                    log.debug("Assistant: is equal to token " + treatment.getString("notes").toString().equals(this.tokens[0]));
                    updateTokens();
                    log.debug("Assistant: tokens updated: "+ getTokens());
                    // Adding to last command
                    SP.putString("assistantLastCommand", treatment.toString());
                    //remove from NS
                    final String _id = treatment.getString("_id");
                    if (NSUpload.isIdValid(_id)) {
                        NSUpload.removeCareportalEntryFromNS(_id);
                    } else {
                        UploadQueue.removeID("dbAdd", _id);
                    }

                }
                return;
            } else {

                //log.debug("Assistant: treatment got"+" insulin is "+treatment.getDouble("insulin")+" token is "+treatment.getString("notes"));
            }

        } catch (JSONException e) {
            log.error("Unhandled exception parsing treatment", e);
        }

    }

    @Override
    public String getFragmentClass() {
        return GoogleAssistantFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getName() {
        return "Google Assistant";
    }

    @Override
    public String getNameShort() {
        // use long name as fallback (not visible in tabs)
        return "GA";
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
        return true;
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
        return R.xml.pref_assistant;
    }

}
