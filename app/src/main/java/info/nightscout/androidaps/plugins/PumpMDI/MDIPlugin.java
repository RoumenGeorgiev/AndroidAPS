package info.nightscout.androidaps.plugins.PumpMDI;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 05.08.2016.
 */
public class MDIPlugin implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(MDIPlugin.class);

    boolean fragmentEnabled = false;
    boolean fragmentVisible = false;

    PumpDescription pumpDescription = new PumpDescription();

    static MDIPlugin plugin = null;

    public static MDIPlugin getPlugin() {
        if (plugin == null)
            plugin = new MDIPlugin();
        return plugin;
    }

    public MDIPlugin() {
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.5d;

        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.isTempBasalCapable = false;
        pumpDescription.isSetBasalProfileCapable = false;
        pumpDescription.isRefillingCapable = false;
    }

    @Override
    public String getFragmentClass() {
        return null;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.mdi);
    }

    @Override
    public String getNameShort() {
        // use long name as fallback (not visible in tabs)
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == PUMP && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return false;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PUMP) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PUMP) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public int setNewBasalProfile(Profile profile) {
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
        return SUCCESS;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @Override
    public Date lastDataTime() {
        return new Date();
    }

    @Override
    public void refreshDataFromPump(String reason) {
        // do nothing
    }

    @Override
    public double getBaseBasalRate() {
        return 0d;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
        return result;
    }

    @Override
    public void stopBolusDelivering() {
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.pumperror);
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.pumperror);
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.pumperror);
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean userRequested) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.pumperror);
        if (Config.logPumpComm)
            log.debug("Cancel temp basal: " + result);
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.pumperror);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        return result;
    }

    @Override
    public JSONObject getJSONStatus() {
        JSONObject pump = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            status.put("status", "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return "MDI";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        return deviceID();
    }

}
