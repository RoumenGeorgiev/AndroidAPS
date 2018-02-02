package info.nightscout.androidaps.plugins.TuneProfile;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;

/**
 * Created by Rumen Georgiev on 1/29/2018.
 */

public class TuneProfileFragment extends SubscriberFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(TuneProfileFragment.class);

    Button runTuneNowButton;
    TextView profileView;
    TextView glucoseDataView;
    TextView treatmentsView;
    TextView lastRunView;
    //TuneProfile tuneProfile = new TuneProfile();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.tuneprofile_fragment, container, false);

            profileView = (TextView) view.findViewById(R.id.tune_profile);
            glucoseDataView = (TextView) view.findViewById(R.id.tune_glucose);
            treatmentsView = (TextView) view.findViewById(R.id.tune_treatments);
            lastRunView = (TextView) view.findViewById(R.id.tune_lastrun);
            runTuneNowButton = (Button) view.findViewById(R.id.tune_run);
            runTuneNowButton.setOnClickListener(this);

            //updateGUI();
            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }
    @Override
    public void onClick(View view) {
        Date lastRun = new Date();
        glucoseDataView.setText(TuneProfile.basicResult(2));
        // lastrun in minutes ???
        profileView.setText(""+TuneProfile.getBasal(9)+"\nISF is "+TuneProfile.getISF()+"\nTargets:"+TuneProfile.getTargets());
        lastRunView.setText(""+lastRun.toLocaleString());
        //treatmentsView.setText(""+TuneProfile.numberOfTreatments(System.currentTimeMillis()- 24*60*60*1000L, System.currentTimeMillis()));
        //TuneProfile.getAutosensData();
        //updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TuneProfile tuneProfile = new TuneProfile();
                    //profileView.setText(TuneProfile.profile.getBasalList() != null ? TuneProfile.profile.getBasalList() : "");
                    //glucoseDataView.setText(TuneProfile.getPlugin().averageGlucoseString(1));
                    glucoseDataView.setText("Press run");
                    //treatmentsView.setText(LoopPlugin.lastRun.setByPump != null ? LoopPlugin.lastRun.setByPump.toSpanned() : "");
                    //lastRunView.setText(LoopPlugin.lastRun.lastAPSRun != null && LoopPlugin.lastRun.lastAPSRun.getTime() != 0 ? LoopPlugin.lastRun.lastAPSRun.toLocaleString() : "");
                }
            });
    }

}
