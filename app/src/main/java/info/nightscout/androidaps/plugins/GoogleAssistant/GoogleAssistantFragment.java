package info.nightscout.androidaps.plugins.GoogleAssistant;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.utils.SP;

public class GoogleAssistantFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(GoogleAssistantFragment.class);

    TextView assistantEnabledView;
    TextView assistantBolusEnabledView;
    TextView assistantTokensView;
    TextView assistantLastCommandView;


    private static Handler sLoopHandler = new Handler();
    private static Runnable sRefreshLoop = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (sRefreshLoop == null) {
            sRefreshLoop = new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                    sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
                }
            };
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.assistant_fragment, container, false);
            assistantEnabledView = (TextView) view.findViewById(R.id.assistant_enabled);
            assistantBolusEnabledView = (TextView) view.findViewById(R.id.assistant_bolus);
            assistantTokensView = (TextView) view.findViewById(R.id.assistant_tokens);
            assistantLastCommandView = (TextView) view.findViewById(R.id.assistant_last);

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    public void updateTokens(){
        GoogleAssistantPlugin assistant = new GoogleAssistantPlugin();
        String tokens = assistant.getTokens();
        assistantTokensView.setText(tokens);
        if(SP.getBoolean("assistant_enabled", false)) {
            assistantEnabledView.setText("Yes");
        } else assistantEnabledView.setText("Disabled in Preferences!");
        if(SP.getBoolean("assistant_bolus", false)) {
            assistantBolusEnabledView.setText("Yes");
        } else assistantBolusEnabledView.setText("No bolusing from Assistant");
    }

    @Subscribe
    public void onStatusEvent(final EventVirtualPumpUpdateGui ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && assistantEnabledView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GoogleAssistantPlugin assistant = GoogleAssistantPlugin.getPlugin();
                    updateTokens();

                }
            });
    }
}
