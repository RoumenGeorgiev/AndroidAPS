package info.nightscout.androidaps.plugins.Actions.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;

public class NewTempBasalDialog extends DialogFragment implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {

    RadioButton percentRadio;
    RadioButton absoluteRadio;
    RadioGroup basalTypeRadioGroup;
    LinearLayout typeSelectorLayout;

    LinearLayout percentLayout;
    LinearLayout absoluteLayout;

    NumberPicker basalPercent;
    NumberPicker basalAbsolute;
    NumberPicker duration;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public NewTempBasalDialog() {
        mHandlerThread = new HandlerThread(NewTempBasalDialog.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.overview_tempbasal_button));

        View view = inflater.inflate(R.layout.overview_newtempbasal_dialog, container, false);

        percentLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_percent_layout);
        absoluteLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_absolute_layout);
        percentRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_percent_radio);
        basalTypeRadioGroup = (RadioGroup) view.findViewById(R.id.overview_newtempbasal_radiogroup);
        absoluteRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_absolute_radio);
        typeSelectorLayout = (LinearLayout) view.findViewById(R.id.overview_newtempbasal_typeselector_layout);

        PumpDescription pumpDescription = MainApp.getConfigBuilder().getPumpDescription();

        basalPercent =  (NumberPicker) view.findViewById(R.id.overview_newtempbasal_basalpercentinput);
        double maxTempPercent = pumpDescription.maxTempPercent;
        double tempPercentStep = pumpDescription.tempPercentStep;
        basalPercent.setParams(100d, 0d, maxTempPercent, tempPercentStep, new DecimalFormat("0"), true);

        Profile profile = MainApp.getConfigBuilder().getProfile();
        Double currentBasal = profile != null ? profile.getBasal() : 0d;
        basalAbsolute = (NumberPicker) view.findViewById(R.id.overview_newtempbasal_basalabsoluteinput);
        basalAbsolute.setParams(currentBasal, 0d, pumpDescription.maxTempAbsolute, pumpDescription.tempAbsoluteStep, new DecimalFormat("0.00"), true);

        double tempDurationStep = pumpDescription.tempDurationStep;
        double tempMaxDuration = pumpDescription.tempMaxDuration;
        duration = (NumberPicker) view.findViewById(R.id.overview_newtempbasal_duration);
        duration.setParams(tempDurationStep, tempDurationStep, tempMaxDuration, tempDurationStep, new DecimalFormat("0"), false);

        if ((pumpDescription.tempBasalStyle & PumpDescription.PERCENT) == PumpDescription.PERCENT && (pumpDescription.tempBasalStyle & PumpDescription.ABSOLUTE) == PumpDescription.ABSOLUTE) {
            // Both allowed
            typeSelectorLayout.setVisibility(View.VISIBLE);
        } else {
            typeSelectorLayout.setVisibility(View.GONE);
        }

        if ((pumpDescription.tempBasalStyle & PumpDescription.PERCENT) == PumpDescription.PERCENT) {
            percentRadio.setChecked(true);
            absoluteRadio.setChecked(false);
            percentLayout.setVisibility(View.VISIBLE);
            absoluteLayout.setVisibility(View.GONE);
        } else if ((pumpDescription.tempBasalStyle & PumpDescription.ABSOLUTE) == PumpDescription.ABSOLUTE) {
            percentRadio.setChecked(false);
            absoluteRadio.setChecked(true);
            percentLayout.setVisibility(View.GONE);
            absoluteLayout.setVisibility(View.VISIBLE);
        }

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);
        basalTypeRadioGroup.setOnCheckedChangeListener(this);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                try {
                    int percent = 0;
                    Double absolute = 0d;
                    final boolean setAsPercent = percentRadio.isChecked();
                    int durationInMinutes = SafeParse.stringToInt(duration.getText());

                    String confirmMessage = getString(R.string.setbasalquestion);
                    if (setAsPercent) {
                        int basalPercentInput = SafeParse.stringToInt(basalPercent.getText());
                        percent = MainApp.getConfigBuilder().applyBasalConstraints(basalPercentInput);
                        confirmMessage += "\n" + percent + "% ";
                        confirmMessage += "\n" + getString(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (percent != basalPercentInput)
                            confirmMessage += "\n" + getString(R.string.constraintapllied);
                    } else {
                        Double basalAbsoluteInput = SafeParse.stringToDouble(basalAbsolute.getText());
                        absolute = MainApp.getConfigBuilder().applyBasalConstraints(basalAbsoluteInput);
                        confirmMessage += "\n" + absolute + " U/h ";
                        confirmMessage += "\n" + getString(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (absolute - basalAbsoluteInput != 0d)
                            confirmMessage += "\n" + getString(R.string.constraintapllied);
                    }

                    final int finalBasalPercent = percent;
                    final Double finalBasal = absolute;
                    final int finalDurationInMinutes = durationInMinutes;

                    final Context context = getContext();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            final PumpInterface pump = MainApp.getConfigBuilder();
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    PumpEnactResult result;
                                    if (setAsPercent) {
                                        result = pump.setTempBasalPercent(finalBasalPercent, finalDurationInMinutes);
                                    } else {
                                        result = pump.setTempBasalAbsolute(finalBasal, finalDurationInMinutes);
                                    }
                                    if (!result.success) {
                                        if (context instanceof Activity) {
                                            Activity activity = (Activity) context;
                                            if (activity.isFinishing()) {
                                                return;
                                            }
                                        }
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setTitle(MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                                        builder.setMessage(result.comment);
                                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                        builder.show();
                                    }
                                }
                            });
                            Answers.getInstance().logCustom(new CustomEvent("TempBasal"));
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.overview_newtempbasal_percent_radio:
                percentLayout.setVisibility(View.VISIBLE);
                absoluteLayout.setVisibility(View.GONE);
                break;
            case R.id.overview_newtempbasal_absolute_radio:
                percentLayout.setVisibility(View.GONE);
                absoluteLayout.setVisibility(View.VISIBLE);
                break;
        }
    }
}
