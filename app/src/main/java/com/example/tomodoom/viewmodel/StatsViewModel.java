package com.example.tomodoom.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.PetStateRepository;
import com.example.tomodoom.repository.StatsRepository;
import com.example.tomodoom.stats.StatsReport;
import com.example.tomodoom.stats.StatsSnapshot;

import java.util.TimeZone;

/**
 * ViewModel for the Stats page: turns {@link StatsRepository}'s snapshots into a
 * {@link StatsReport}, recomputed on every new snapshot and on {@link #refresh()} (the page calls
 * it periodically so "time since the last doom-scroll" keeps counting). Also holds which range the
 * health chart shows, and exposes the pet's state for the translucent pet behind the page.
 */
public class StatsViewModel extends ViewModel {

    @NonNull
    private final StatsRepository statsRepository;
    @NonNull
    private final PetStateRepository petStateRepository;
    @NonNull
    private final MediatorLiveData<StatsReport> report = new MediatorLiveData<>();
    @NonNull
    private final MutableLiveData<Boolean> hpWeekSelected = new MutableLiveData<>(false);

    public StatsViewModel(@NonNull StatsRepository statsRepository,
                          @NonNull PetStateRepository petStateRepository) {
        this.statsRepository = statsRepository;
        this.petStateRepository = petStateRepository;
        report.addSource(statsRepository.getStats(), this::rebuild);
    }

    /** The pet, for the backdrop behind the page. */
    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateRepository.getPetState();
    }

    /** Main thread; emits a fresh report for every snapshot and refresh. */
    @NonNull
    public LiveData<StatsReport> getReport() {
        return report;
    }

    /** @return whether the health chart shows 7 days (true) or 24 hours (false). */
    @NonNull
    public LiveData<Boolean> isHpWeekSelected() {
        return hpWeekSelected;
    }

    @MainThread
    public void selectHpRange(boolean week) {
        if (!Boolean.valueOf(week).equals(hpWeekSelected.getValue())) {
            hpWeekSelected.setValue(week);
        }
    }

    /** Recomputes the report for the current time. */
    @MainThread
    public void refresh() {
        rebuild(statsRepository.getCurrentStats());
    }

    private void rebuild(@NonNull StatsSnapshot snapshot) {
        report.setValue(StatsReport.of(snapshot, System.currentTimeMillis(),
                TimeZone.getDefault()));
    }
}
