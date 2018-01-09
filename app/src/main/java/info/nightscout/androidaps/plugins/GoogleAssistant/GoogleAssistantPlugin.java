package info.nightscout.androidaps.plugins.GoogleAssistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSTreatment;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalAdapterAMAJS;

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
            log.debug("Assistant plugin is null!");
            plugin = new GoogleAssistantPlugin();
        return plugin;
    }


    // Idea is: Google assitant should create treatments in Nightscout
    // in the notes of these treatments should be createdBy "assistant" and note should be token
    // If token is the first form tokents - execute it, remove token from list, remove treatment from NS and create it by AAPS
    // if token is wrong - wrongToken++
    // if wrongToken >2 disable plugin

    String[] tokens = new String[10];

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

    public void updateTokens(){
        // first token is no more so second becomes first and number 5 is random
        for(int i = 1; i < 5; i++){
            this.tokens[i-1] = this.tokens[i];
        }

        //Prevention from repeating tokens
        while(this.tokens[3] == this.tokens[4]) {
            this.tokens[4] = randomToken();
        }
    }

    public String randomToken(){
        int randomNumber = (int) Math.ceil(Math.random() * 10);
        // if randomnumber is 10 ->try again
        while(randomNumber == 10) {
            randomNumber = (int) Math.ceil(Math.random() * 10);
        }
        //log.debug("Assistant random token is: "+this.tokens[randomNumber]);
        return this.tokens[randomNumber];
    }

    public String tokensString = "No tokens yet";
    public String tokensToString(){
        setTokens();
        tokensString = this.tokens[0] + ";" + this.tokens[1] + ";" + this.tokens[2] + ";" + this.tokens[3] + ";" + this.tokens[4] + ";";
        log.debug("Assistant tokenString is:"+tokensString);
        //log.debug("Assistant random token is:"+randomToken());
        updateTokens();
        tokensString = this.tokens[0] + ";" + this.tokens[1] + ";" + this.tokens[2] + ";" + this.tokens[3] + ";" + this.tokens[4] + ";";
        log.debug("Assistant updated tokenString is:"+tokensString);
        updateTokens();
        tokensString = this.tokens[0] + ";" + this.tokens[1] + ";" + this.tokens[2] + ";" + this.tokens[3] + ";" + this.tokens[4] + ";";
        log.debug("Assistant updated 2 tokenString is:"+tokensString);
        return tokensString;
    }


    String getTokens(){
        if(tokensString == null || tokensString == "No tokens yet"){
            tokensToString();
        }

        return tokensString;
    }

    public void newTreatment(NSTreatment treatment){
        // check if treatment is from assisant
        if(treatment.getEnteredBy() != "assistant")
            return;
        // ok treatment is entered from assistant but is it right
        // check for insulin
        // check for carbs
        // check for token ?!?


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
