package com.example.tomodoom.ui.stats;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.FragmentStatsBinding;
import com.example.tomodoom.databinding.ItemStatsRowBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.stats.DoomScrollLog;
import com.example.tomodoom.ui.MainPagerAdapter;
import com.example.tomodoom.ui.PetStage;
import com.example.tomodoom.stats.HpHistory;
import com.example.tomodoom.stats.StatsReport;
import com.example.tomodoom.stats.TokenUsageLedger;
import com.example.tomodoom.viewmodel.StatsViewModel;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Stats page (the rightmost tab): time since the last doom-scroll, doom-scrolling today and this
 * week, the pet's health over 24 hours or 7 days, and LLM tokens per model. It only formats the
 * {@link StatsReport} from {@link StatsViewModel}, and refreshes it every half minute while
 * visible so the clean-streak timer keeps counting. The activity's pet ({@link PetStage}) shows
 * translucent behind the cards.
 */
public class StatsFragment extends Fragment {

    private static final long REFRESH_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final int MAX_APP_ROWS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            if (viewModel != null) {
                viewModel.refresh();
            }
            handler.postDelayed(this, REFRESH_MILLIS);
        }
    };

    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;
    @Nullable
    private StatsReport lastReport;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(requireContext()))
                .get(StatsViewModel.class);

        binding.hpRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                viewModel.selectHpRange(checkedId == R.id.hp_range_week);
            }
        });
        viewModel.isHpWeekSelected().observe(getViewLifecycleOwner(), week -> {
            int id = Boolean.TRUE.equals(week) ? R.id.hp_range_week : R.id.hp_range_day;
            if (binding.hpRange.getCheckedButtonId() != id) {
                binding.hpRange.check(id);
            }
            if (lastReport != null) {
                bindHp(lastReport);
            }
        });
        viewModel.getReport().observe(getViewLifecycleOwner(), this::bind);
        stage().registerBackdrop(MainPagerAdapter.PAGE_STATS, binding.getRoot());
        viewModel.getPetState().observe(getViewLifecycleOwner(),
                state -> stage().render(binding.getRoot(), state));
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
        stage().attach(binding.getRoot(), viewModel.getPetState().getValue());
    }

    @Override
    public void onPause() {
        stage().detach(binding.getRoot());
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    @Override
    public void onDestroyView() {
        stage().detach(binding.getRoot());
        stage().unregisterBackdrop(binding.getRoot());
        super.onDestroyView();
        handler.removeCallbacks(refresher);
        lastReport = null;
        binding = null;
    }

    /** The activity's shared pet renderer, shown faintly behind this page. */
    @NonNull
    private PetStage stage() {
        return ((PetStage.Host) requireActivity()).getPetStage();
    }

    private void bind(@NonNull StatsReport report) {
        if (binding == null) {
            return;
        }
        lastReport = report;
        bindCleanStreak(report);
        bindDoomScrolling(report);
        bindHp(report);
        bindTokens(report);
    }

    private void bindCleanStreak(@NonNull StatsReport report) {
        Context context = requireContext();
        binding.cleanValue.setText(StatsFormatter.duration(context,
                report.getCurrentCleanMillis()));
        if (report.hasDoomScrolled()) {
            binding.cleanDetail.setText(getString(R.string.stats_since_last_seen,
                    relativeTime(report.getLastDoomScrollMillis(), report.getNowMillis())));
        } else {
            binding.cleanDetail.setText(getString(R.string.stats_since_never,
                    relativeTime(report.getTrackingSinceMillis(), report.getNowMillis())));
        }
        binding.cleanLongest.setText(getString(R.string.stats_longest_clean,
                StatsFormatter.duration(context, report.getLongestCleanMillis())));
    }

    private void bindDoomScrolling(@NonNull StatsReport report) {
        Context context = requireContext();
        binding.doomTodayValue.setText(StatsFormatter.duration(context, report.getTodayMillis()));
        binding.doomSessionsValue.setText(StatsFormatter.number(report.getSessionsToday()));
        binding.doomLongestValue.setText(StatsFormatter.duration(context,
                report.getLongestSessionWeekMillis()));

        long[] starts = report.getWeekDayStarts();
        long[] millis = report.getWeekDayMillis();
        String[] valueLabels = new String[millis.length];
        String[] names = new String[millis.length];
        StringBuilder description = new StringBuilder(getString(R.string.stats_doom_chart_description));
        for (int i = 0; i < millis.length; i++) {
            valueLabels[i] = StatsFormatter.shortMinutes(context, millis[i]);
            names[i] = i == millis.length - 1 ? getString(R.string.stats_today)
                    : DateFormat.format("EEE", new Date(starts[i])).toString();
            description.append(' ').append(names[i]).append(": ")
                    .append(StatsFormatter.duration(context, millis[i])).append('.');
        }
        binding.doomWeekChart.setData(millis, valueLabels, names);
        binding.doomWeekChart.setContentDescription(description);
        binding.doomWeekTotal.setText(getString(R.string.stats_doom_week_total,
                StatsFormatter.duration(context, report.getWeekTotalMillis())));

        binding.doomApps.removeAllViews();
        List<DoomScrollLog.AppTime> apps = report.getWeekApps();
        if (apps.isEmpty()) {
            addEmpty(binding.doomApps, R.string.stats_doom_apps_empty);
        }
        for (int i = 0; i < apps.size() && i < MAX_APP_ROWS; i++) {
            DoomScrollLog.AppTime app = apps.get(i);
            addRow(binding.doomApps, StatsFormatter.appLabel(context, app.getPackageName()), null,
                    StatsFormatter.duration(context, app.getMillis()));
        }
    }

    private void bindHp(@NonNull StatsReport report) {
        boolean week = Boolean.TRUE.equals(viewModel.isHpWeekSelected().getValue());
        long window = week ? StatsReport.WEEK_WINDOW_MILLIS : StatsReport.DAY_WINDOW_MILLIS;
        List<HpHistory.Sample> samples = week ? report.getHpWeek() : report.getHpDay();
        long now = report.getNowMillis();
        binding.hpChart.setData(samples, now - window, now);
        binding.hpChartStart.setText(week ? R.string.stats_chart_week_ago
                : R.string.stats_chart_day_ago);
        if (samples.isEmpty()) {
            binding.hpChart.setContentDescription(getString(R.string.stats_hp_chart_empty));
        } else {
            HpHistory.Sample last = samples.get(samples.size() - 1);
            binding.hpChart.setContentDescription(getString(R.string.stats_hp_chart_description,
                    last.getHp(), last.getMaxHp()));
        }

        HpHistory.Totals totals = report.getHpWeekTotals();
        binding.hpWeekGained.setText(getString(R.string.stats_hp_week_gained,
                totals.getGainedFromChat()));
        binding.hpWeekLost.setText(getString(R.string.stats_hp_week_lost,
                totals.getLostToDoomScroll()));
    }

    private void bindTokens(@NonNull StatsReport report) {
        binding.tokensModels.removeAllViews();
        List<TokenUsageLedger.ModelUsage> usage = report.getTokenUsage();
        if (usage.isEmpty()) {
            addEmpty(binding.tokensModels, R.string.stats_tokens_empty);
            binding.tokensTotal.setVisibility(View.GONE);
            return;
        }
        for (TokenUsageLedger.ModelUsage model : usage) {
            String detail = getResources().getQuantityString(R.plurals.stats_tokens_detail,
                    (int) Math.min(Integer.MAX_VALUE, model.getRequests()),
                    StatsFormatter.number(model.getRequests()),
                    StatsFormatter.number(model.getPromptTokens()),
                    StatsFormatter.number(model.getCompletionTokens()));
            addRow(binding.tokensModels, model.getModel(), detail,
                    StatsFormatter.number(model.getTotalTokens()));
        }
        TokenUsageLedger.ModelUsage total = report.getTokenTotal();
        binding.tokensTotal.setVisibility(View.VISIBLE);
        binding.tokensTotal.setText(getResources().getQuantityString(R.plurals.stats_tokens_total,
                (int) Math.min(Integer.MAX_VALUE, total.getRequests()),
                StatsFormatter.number(total.getTotalTokens()),
                StatsFormatter.number(total.getRequests())));
    }

    private void addRow(@NonNull LinearLayout parent, @NonNull String name,
                        @Nullable String detail, @NonNull String value) {
        ItemStatsRowBinding row = ItemStatsRowBinding.inflate(getLayoutInflater(), parent, true);
        row.rowName.setText(name);
        row.rowDetail.setVisibility(detail == null ? View.GONE : View.VISIBLE);
        row.rowDetail.setText(detail);
        row.rowValue.setText(value);
    }

    private void addEmpty(@NonNull LinearLayout parent, @StringRes int message) {
        ItemStatsRowBinding row = ItemStatsRowBinding.inflate(getLayoutInflater(), parent, true);
        row.rowName.setVisibility(View.GONE);
        row.rowValue.setVisibility(View.GONE);
        row.rowDetail.setVisibility(View.VISIBLE);
        row.rowDetail.setText(message);
    }

    @NonNull
    private static CharSequence relativeTime(long timeMillis, long nowMillis) {
        return DateUtils.getRelativeTimeSpanString(timeMillis, nowMillis,
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }
}
