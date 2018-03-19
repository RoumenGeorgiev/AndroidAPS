package info.nightscout.androidaps.events;

import android.support.annotation.Nullable;

import info.nightscout.androidaps.db.BgReading;

/**
 * Created by mike on 05.06.2016.
 */
public class EventNewBG extends EventLoop {
    @Nullable
    public final BgReading bgReading;
    public final boolean isNew;
    public final boolean isFromActiveBgSource;

    /** Whether the BgReading is current (enough to use for treatment decisions). */
    public boolean isCurrent() {
        return bgReading != null && bgReading.date + 9 * 60 * 1000 > System.currentTimeMillis();
    }

    public EventNewBG(@Nullable BgReading bgReading, boolean isNew, boolean isFromActiveBgSource) {
        this.bgReading = bgReading;
        this.isNew = isNew;
        this.isFromActiveBgSource = isFromActiveBgSource;
    }
}
