package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;
import info.nightscout.utils.SP;

/**
 * Created by mike on 24.02.2016.
 */
public class BroadcastStatus {
    private static Logger log = LoggerFactory.getLogger(BroadcastStatus.class);

    public static void handleNewStatus(NSSettingsStatus status, Context context, boolean isDelta) {

        Bundle bundle = new Bundle();
        try {
            bundle.putString("nsclientversionname", MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionName);
            bundle.putInt("nsclientversioncode", MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        bundle.putString("nightscoutversionname", NSClientService.nightscoutVersionName);
        bundle.putInt("nightscoutversioncode", NSClientService.nightscoutVersionCode);
        bundle.putString("status", status.getData().toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_STATUS);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            bundle = new Bundle();
            try {
                bundle.putString("nsclientversionname", MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionName);
                bundle.putInt("nsclientversioncode", MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionCode);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            bundle.putString("nightscoutversionname", NSClientService.nightscoutVersionName);
            bundle.putInt("nightscoutversioncode", NSClientService.nightscoutVersionCode);
            bundle.putString("status", status.getData().toString());
            bundle.putBoolean("delta", isDelta);
            intent = new Intent(Intents.ACTION_NEW_STATUS);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }
}
