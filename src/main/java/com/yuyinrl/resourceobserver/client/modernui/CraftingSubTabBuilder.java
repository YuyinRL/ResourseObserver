package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.CraftingViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.List;

import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.addSection;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.addText;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.clamp;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.compact;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.getTextScale;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.leftGap;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.section;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.spacer;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.spacerParams;
import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.tr;

/**
 * Crafting 子 Tab 构建器 —— 在 Storage Network 页内渲染 AE2 合成数据。
 * <p>
 * 布局：
 * <pre>
 *   ┌──────── CPU 汇总 KPI（全宽） ────────┐
 *   ├──── 左列 33% ────┬──── 右列 67% ────┤
 *   │ Active Jobs 列表  │ 搜索栏 + Craftables 表格 │
 *   └─────────────────┴───────────────────┘
 * </pre>
 */
final class CraftingSubTabBuilder {

    private CraftingSubTabBuilder() {}

    static void build(ResourceTerminalFragment terminal, LinearLayout content, int contentW, int contentH) {
        CraftingViewModel vm = terminal.getBridge().getCraftingViewModel();
        if (vm == null) vm = CraftingViewModel.empty(terminal.getBridge().getSearchQuery());

        int sectionGap = terminal.dp(6);
        int scrollbarW = terminal.dp(8);
        int innerW = Math.max(terminal.dp(160), contentW - scrollbarW);

        int kpiH = clamp(Math.round(contentH * 0.12f), terminal.dp(48), terminal.dp(80));

        LinearLayout kpiSection = section(terminal, innerW, kpiH);
        buildStorageSummaryKpi(terminal, kpiSection, innerW, kpiH, vm);
        addSection(content, kpiSection, 0);

        if (!vm.hasData()) {
            LinearLayout emptySection = section(terminal, innerW, ViewGroup.LayoutParams.WRAP_CONTENT);
            emptySection.setGravity(Gravity.CENTER);
            emptySection.setPadding(0, terminal.dp(24), 0, terminal.dp(24));
            addText(emptySection,
                    tr("screen.resourceobserver.crafting.empty"),
                    UiThemeTokens.TEXT_MUTED, 11, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            addSection(content, emptySection, sectionGap);
            return;
        }

        int colGap = terminal.dp(6);
        int leftW = Math.max(terminal.dp(80), (int) (innerW * 0.33f));
        int rightW = Math.max(terminal.dp(80), innerW - leftW - colGap);

        LinearLayout columns = new LinearLayout(terminal.getContext());
        columns.setOrientation(LinearLayout.HORIZONTAL);

        // Left: active jobs
        LinearLayout jobsSection = section(terminal, leftW, ViewGroup.LayoutParams.WRAP_CONTENT);
        buildJobsList(terminal, jobsSection, leftW, vm);
        columns.addView(jobsSection, new LinearLayout.LayoutParams(leftW, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Right: search + craftables
        LinearLayout rightCol = new LinearLayout(terminal.getContext());
        rightCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout searchBar = new LinearLayout(terminal.getContext());
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(terminal.dp(8), terminal.dp(4), terminal.dp(8), terminal.dp(4));
        addText(searchBar,
                tr("screen.resourceobserver.crafting.craftables_count",
                        vm.craftables().size(), vm.totalCraftables()),
                UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        searchBar.addView(spacer(terminal), spacerParams());
        buildSearchBox(terminal, searchBar);
        addSection(rightCol, searchBar, 0);

        LinearLayout craftablesSection = section(terminal, rightW, ViewGroup.LayoutParams.WRAP_CONTENT);
        buildCraftablesList(terminal, craftablesSection, rightW, vm);
        addSection(rightCol, craftablesSection, sectionGap);

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                rightW, ViewGroup.LayoutParams.WRAP_CONTENT);
        rightParams.leftMargin = colGap;
        columns.addView(rightCol, rightParams);

        addSection(content, columns, sectionGap);
    }

    /** CPU 存储汇总 KPI —— 横向 4 个指标。 */
    private static void buildStorageSummaryKpi(ResourceTerminalFragment terminal,
                                               LinearLayout section, int innerW, int kpiH,
                                               CraftingViewModel vm) {
        CraftingViewModel.StorageSummary s = vm.storageSummary();
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setPadding(terminal.dp(10), terminal.dp(8), terminal.dp(10), terminal.dp(8));
        section.setGravity(Gravity.CENTER_VERTICAL);

        addKpiCell(terminal, section,
                tr("screen.resourceobserver.crafting.kpi.cpu_total"),
                String.valueOf(s.cpuCount()));
        addKpiCell(terminal, section,
                tr("screen.resourceobserver.crafting.kpi.cpu_busy"),
                s.busyCpuCount() + " / " + s.cpuCount());
        addKpiCell(terminal, section,
                tr("screen.resourceobserver.crafting.kpi.storage_bytes"),
                compact(s.totalStorageBytes()) + " B");
        addKpiCell(terminal, section,
                tr("screen.resourceobserver.crafting.kpi.coprocessors"),
                String.valueOf(s.totalCoProcessors()));
        if (!s.reliable()) {
            addText(section,
                    tr("screen.resourceobserver.crafting.unreliable"),
                    UiThemeTokens.AMBER, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        }
    }

    private static void addKpiCell(ResourceTerminalFragment terminal, LinearLayout parent,
                                   String label, String value) {
        LinearLayout cell = new LinearLayout(terminal.getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = terminal.dp(8);
        parent.addView(cell, lp);

        TextView labelView = new TextView(terminal.getContext());
        labelView.setText(label);
        labelView.setTextSize(9 * getTextScale());
        labelView.setTextColor(UiThemeTokens.TEXT_MUTED);
        labelView.setSingleLine(true);
        cell.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(terminal.getContext());
        valueView.setText(value);
        valueView.setTextSize(14 * getTextScale());
        valueView.setTextColor(UiThemeTokens.TEXT);
        valueView.setSingleLine(true);
        cell.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Active Jobs 列表 —— 每行显示：CPU 名 / 输出物品 / 进度条 / remaining。 */
    private static void buildJobsList(ResourceTerminalFragment terminal,
                                      LinearLayout section, int width,
                                      CraftingViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));

        addText(section,
                tr("screen.resourceobserver.crafting.jobs_title"),
                UiThemeTokens.TEXT, 11, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        List<CraftingViewModel.JobRow> jobs = vm.jobs();
        if (jobs.isEmpty()) {
            addText(section,
                    tr("screen.resourceobserver.crafting.jobs_empty"),
                    UiThemeTokens.TEXT_MUTED, 10, terminal.dp(6),
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);
            return;
        }
        for (CraftingViewModel.JobRow job : jobs) {
            section.addView(buildJobRow(terminal, width, job), jobRowParams(terminal));
        }
    }

    private static LinearLayout.LayoutParams jobRowParams(ResourceTerminalFragment terminal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = terminal.dp(4);
        return lp;
    }

    private static LinearLayout buildJobRow(ResourceTerminalFragment terminal, int rowW,
                                            CraftingViewModel.JobRow job) {
        LinearLayout row = new LinearLayout(terminal.getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(terminal.dp(6), terminal.dp(4), terminal.dp(6), terminal.dp(4));

        LinearLayout topLine = new LinearLayout(terminal.getContext());
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(topLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Item icon (if busy and has output)
        if (job.busy() && job.outputItemId() != null && !job.outputItemId().isEmpty()) {
            InlineItemIconView icon = new InlineItemIconView(terminal.getContext());
            icon.setItemId(job.outputItemId());
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    terminal.dp(12), terminal.dp(12));
            iconLp.rightMargin = terminal.dp(4);
            topLine.addView(icon, iconLp);
        }

        String cpuLabel = job.cpuName() == null || job.cpuName().isEmpty() ? "CPU" : job.cpuName();
        TextView cpuName = new TextView(terminal.getContext());
        cpuName.setText(cpuLabel);
        cpuName.setTextSize(10 * getTextScale());
        cpuName.setTextColor(job.busy() ? UiThemeTokens.CYAN : UiThemeTokens.TEXT_MUTED);
        cpuName.setSingleLine(true);
        topLine.addView(cpuName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        topLine.addView(spacer(terminal), spacerParams());

        if (job.busy()) {
            TextView amountText = new TextView(terminal.getContext());
            amountText.setText(compact(Math.max(job.total() - job.remaining(), 0L))
                    + " / " + compact(job.total()));
            amountText.setTextSize(9 * getTextScale());
            amountText.setTextColor(UiThemeTokens.TEXT_MUTED);
            amountText.setSingleLine(true);
            topLine.addView(amountText, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            TextView idleText = new TextView(terminal.getContext());
            idleText.setText(tr("screen.resourceobserver.crafting.jobs_idle"));
            idleText.setTextSize(9 * getTextScale());
            idleText.setTextColor(UiThemeTokens.TEXT_MUTED);
            idleText.setSingleLine(true);
            topLine.addView(idleText, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // Item name line (busy only)
        if (job.busy() && job.outputDisplayName() != null && !job.outputDisplayName().isEmpty()) {
            TextView itemName = new TextView(terminal.getContext());
            itemName.setText(job.outputDisplayName());
            itemName.setTextSize(9 * getTextScale());
            itemName.setTextColor(UiThemeTokens.TEXT);
            itemName.setSingleLine(true);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameLp.topMargin = terminal.dp(2);
            row.addView(itemName, nameLp);
        }

        // Progress bar (busy only)
        if (job.busy()) {
            row.addView(buildProgressBar(terminal, job.progress()),
                    progressBarParams(terminal));
        }

        row.setBackground(ModernUiTheme.cardBackground(row));
        return row;
    }

    private static LinearLayout.LayoutParams progressBarParams(ResourceTerminalFragment terminal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, terminal.dp(4));
        lp.topMargin = terminal.dp(3);
        return lp;
    }

    private static FrameLayout buildProgressBar(ResourceTerminalFragment terminal, double progress) {
        FrameLayout bar = new FrameLayout(terminal.getContext());
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setCornerRadius(terminal.dp(2));
        bar.setBackground(bg);

        double clamped = Math.max(0.0, Math.min(1.0, progress));
        View fill = new View(terminal.getContext());
        ShapeDrawable fillBg = new ShapeDrawable();
        fillBg.setColor(UiThemeTokens.CYAN);
        fillBg.setCornerRadius(terminal.dp(2));
        fill.setBackground(fillBg);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                (int) Math.max(terminal.dp(1), terminal.dp(100) * clamped),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        bar.addView(fill, fp);
        return bar;
    }

    /** Craftables 表格 —— 图标 + 名称，支持搜索过滤。 */
    private static void buildCraftablesList(ResourceTerminalFragment terminal,
                                            LinearLayout section, int width,
                                            CraftingViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));

        addText(section,
                tr("screen.resourceobserver.crafting.craftables_title"),
                UiThemeTokens.TEXT, 11, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        List<CraftingViewModel.CraftableRow> rows = vm.craftables();
        if (rows.isEmpty()) {
            String emptyKey = vm.searchQuery() == null || vm.searchQuery().isEmpty()
                    ? "screen.resourceobserver.crafting.craftables_empty"
                    : "screen.resourceobserver.crafting.craftables_no_match";
            addText(section, tr(emptyKey),
                    UiThemeTokens.TEXT_MUTED, 10, terminal.dp(6),
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);
            return;
        }

        // 限制渲染条目（防止大网络卡顿）；用户可通过搜索进一步缩小
        int maxVisible = 120;
        int renderCount = Math.min(rows.size(), maxVisible);
        for (int i = 0; i < renderCount; i++) {
            section.addView(buildCraftableRow(terminal, rows.get(i)),
                    jobRowParams(terminal));
        }
        if (rows.size() > renderCount) {
            addText(section,
                    tr("screen.resourceobserver.crafting.craftables_truncated",
                            rows.size() - renderCount),
                    UiThemeTokens.TEXT_MUTED, 9, terminal.dp(6),
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);
        }
    }

    private static LinearLayout buildCraftableRow(ResourceTerminalFragment terminal,
                                                  CraftingViewModel.CraftableRow row) {
        LinearLayout line = new LinearLayout(terminal.getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));

        InlineItemIconView icon = new InlineItemIconView(terminal.getContext());
        icon.setItemId(row.itemId());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                terminal.dp(10), terminal.dp(10));
        iconLp.rightMargin = terminal.dp(4);
        line.addView(icon, iconLp);

        TextView name = new TextView(terminal.getContext());
        name.setText(row.displayName() == null || row.displayName().isEmpty()
                ? row.itemId() : row.displayName());
        name.setTextSize(9 * getTextScale());
        name.setTextColor(UiThemeTokens.TEXT);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        line.addView(name, nameLp);

        TextView idText = new TextView(terminal.getContext());
        idText.setText(row.itemId());
        idText.setTextSize(8 * getTextScale());
        idText.setTextColor(UiThemeTokens.TEXT_MUTED);
        idText.setSingleLine(true);
        line.addView(idText, leftGap(terminal.dp(4)));
        return line;
    }

    /** 复刻 StorageNetworkPageBuilder 的搜索框（共享同一搜索词 via bridge）。 */
    private static void buildSearchBox(ResourceTerminalFragment terminal, LinearLayout parent) {
        int dp4 = terminal.dp(4);
        int dp6 = terminal.dp(6);
        int dp8 = terminal.dp(8);

        LinearLayout container = new LinearLayout(terminal.getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        EditText searchEdit = new EditText(terminal.getContext());
        searchEdit.setHint(tr("screen.resourceobserver.search.hint"));
        searchEdit.setSingleLine(true);
        searchEdit.setTextSize(10 * getTextScale());
        searchEdit.setTextColor(UiThemeTokens.TEXT);
        searchEdit.setHintTextColor(UiThemeTokens.TEXT_MUTED);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setCornerRadius(dp4);
        bg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        searchEdit.setBackground(bg);
        searchEdit.setPadding(dp8, dp4, dp8, dp4);

        String currentQuery = terminal.getBridge().getSearchQuery();
        if (!currentQuery.isEmpty()) searchEdit.setText(currentQuery);

        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                terminal.dp(100), ViewGroup.LayoutParams.WRAP_CONTENT);
        editLp.rightMargin = dp6;
        container.addView(searchEdit, editLp);

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                terminal.scheduleSearchUpdate(s == null ? "" : s.toString());
            }
        });
        parent.addView(container);
    }
}
