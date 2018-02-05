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
    TextView warningView;
    TextView resultView;
    TextView lastRunView;
    //TuneProfile tuneProfile = new TuneProfile();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.tuneprofile_fragment, container, false);

            warningView = (TextView) view.findViewById(R.id.tune_warning);
            resultView = (TextView) view.findViewById(R.id.tune_result);
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
        resultView.setText(TuneProfile.result(5));
        // lastrun in minutes ???
        warningView.setText("You already pressed RUN - NO WARNING NEEDED!");
        lastRunView.setText(""+lastRun.toLocaleString());
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
                    warningView.setText("Don't run tune for more than 5 days back! It will cause app crashesh and too much data usage");
                    resultView.setText("Press run");
                }
            });
    }

}
