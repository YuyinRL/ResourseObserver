package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.CraftingViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.client.util.ItemNames;
import com.yuyinrl.resourceobserver.network.UiActionType;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.PopupWindow;
import icyllis.modernui.widget.HorizontalScrollView;
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
            // 进度条文本：百分比 + 已耗时 + 剩余产物数；
            // 不再展示"X / Y items"，因为 AE2 的 totalItems() 是 deprecated 的标定值（Integer.MAX_VALUE）。
            int pct = (int) Math.round(job.progress() * 100.0);
            String elapsed = formatElapsed(job.elapsedMillis());
            String text = pct + "%";
            if (!elapsed.isEmpty()) text += "  ·  " + elapsed;
            if (job.remaining() > 0L) text += "  ·  " + tr("screen.resourceobserver.crafting.jobs_remaining_short")
                    + " " + compact(job.remaining());
            TextView amountText = new TextView(terminal.getContext());
            amountText.setText(text);
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
        if (job.busy() && job.treeId() != null && !job.treeId().isEmpty()) {
            row.setClickable(true);
            row.setOnClickListener(v -> requestAndShowTree(terminal, job.treeId(), job.progress()));
        }
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

        // 一键下单按钮
        TextView orderBtn = new TextView(terminal.getContext());
        orderBtn.setText(tr("screen.resourceobserver.crafting.order"));
        orderBtn.setTextSize(8 * getTextScale());
        orderBtn.setTextColor(UiThemeTokens.CYAN);
        orderBtn.setGravity(Gravity.CENTER);
        orderBtn.setPadding(terminal.dp(6), terminal.dp(2), terminal.dp(6), terminal.dp(2));
        ShapeDrawable btnBg = new ShapeDrawable();
        btnBg.setColor(UiThemeTokens.SECTION_BG);
        btnBg.setCornerRadius(terminal.dp(3));
        btnBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        orderBtn.setBackground(btnBg);
        orderBtn.setOnClickListener(v -> showOrderPopup(terminal, row));
        line.addView(orderBtn, leftGap(terminal.dp(6)));
        return line;
    }

    /** 弹出下单输入框：让玩家输入数量后通过 UI Action payload 发送 PLACE_CRAFT_ORDER。 */
    private static void showOrderPopup(ResourceTerminalFragment terminal, CraftingViewModel.CraftableRow row) {
        View rootView = terminal.getView();
        if (rootView == null) return;

        int dp4 = terminal.dp(4);
        int dp8 = terminal.dp(8);
        int dp12 = terminal.dp(12);

        LinearLayout container = new LinearLayout(terminal.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp12, dp12, dp12, dp12);
        ShapeDrawable cardBg = new ShapeDrawable();
        cardBg.setColor(UiThemeTokens.PANEL_BG);
        cardBg.setCornerRadius(terminal.dp(10));
        cardBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        container.setBackground(cardBg);

        TextView title = new TextView(terminal.getContext());
        title.setText(tr("screen.resourceobserver.crafting.order_title",
                row.displayName() == null || row.displayName().isEmpty() ? row.itemId() : row.displayName()));
        title.setTextSize(11 * getTextScale());
        title.setTextColor(UiThemeTokens.TITLE);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp8;
        container.addView(title, titleLp);

        EditText input = new EditText(terminal.getContext());
        input.setHint(tr("screen.resourceobserver.crafting.order_hint"));
        input.setText("1");
        input.setSingleLine(true);
        input.setTextSize(10 * getTextScale());
        input.setTextColor(UiThemeTokens.TEXT);
        ShapeDrawable inputBg = new ShapeDrawable();
        inputBg.setColor(UiThemeTokens.SECTION_BG);
        inputBg.setCornerRadius(dp4);
        inputBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        input.setBackground(inputBg);
        input.setPadding(dp8, dp4, dp8, dp4);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp4;
        container.addView(input, inputLp);

        LinearLayout buttonRow = new LinearLayout(terminal.getContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = dp8;

        PopupWindow popup = new PopupWindow(container, terminal.dp(180), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(terminal.dp(8));
        popup.setOnDismissListener(() -> terminal.setTextInputActive(false));

        TextView cancelBtn = makeDialogButton(terminal,
                tr("screen.resourceobserver.crafting.order_cancel"), UiThemeTokens.TEXT_MUTED);
        cancelBtn.setOnClickListener(v -> popup.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.rightMargin = dp8;
        buttonRow.addView(cancelBtn, cancelLp);

        TextView confirmBtn = makeDialogButton(terminal,
                tr("screen.resourceobserver.crafting.order_confirm"), UiThemeTokens.CYAN);
        confirmBtn.setOnClickListener(v -> {
            String s = input.getText().toString().trim();
            long amount;
            try {
                amount = Long.parseLong(s);
            } catch (NumberFormatException e) {
                amount = 0L;
            }
            if (amount <= 0L) return;
            popup.dismiss();
            // 先注册接收计划摘要的回调，再发送计划请求
            final long requestedAmount = amount;
            terminal.setCraftingPlanCallback(plan ->
                    showReviewPopup(terminal, row, requestedAmount, plan));
            terminal.sendUiAction(UiActionType.PLACE_CRAFT_ORDER,
                    row.itemId(),
                    java.util.List.of(row.networkId() == null ? "" : row.networkId()),
                    String.valueOf(amount));
            // 同步显示一个"计算中"的小提示弹窗
            showPlanningPopup(terminal, row, requestedAmount);
        });
        buttonRow.addView(confirmBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container.addView(buttonRow, brLp);

        terminal.setTextInputActive(true);
        terminal.trackPopup(popup);
        popup.showAtLocation(rootView, Gravity.CENTER, 0, 0);
        input.requestFocus();
    }

    private static TextView makeDialogButton(ResourceTerminalFragment terminal, String label, int color) {
        TextView btn = new TextView(terminal.getContext());
        btn.setText(label);
        btn.setTextSize(10 * getTextScale());
        btn.setTextColor(color);
        btn.setPadding(terminal.dp(8), terminal.dp(4), terminal.dp(8), terminal.dp(4));
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setCornerRadius(terminal.dp(3));
        bg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        btn.setBackground(bg);
        return btn;
    }

    /** "计算中…" 进度提示。仅在等待服务端推送 CraftingPlanResultPayload 时短暂显示。 */
    private static PopupWindow currentPlanningPopup;

    private static void showPlanningPopup(ResourceTerminalFragment terminal,
                                           CraftingViewModel.CraftableRow row, long amount) {
        View rootView = terminal.getView();
        if (rootView == null) return;
        if (currentPlanningPopup != null) {
            try { currentPlanningPopup.dismiss(); } catch (Throwable ignored) {}
            currentPlanningPopup = null;
        }
        int dp12 = terminal.dp(12);

        LinearLayout container = new LinearLayout(terminal.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp12, dp12, dp12, dp12);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(UiThemeTokens.PANEL_BG);
        bg.setCornerRadius(terminal.dp(10));
        bg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        container.setBackground(bg);

        TextView msg = new TextView(terminal.getContext());
        msg.setText(tr("screen.resourceobserver.crafting.plan_calculating",
                row.displayName() == null || row.displayName().isEmpty() ? row.itemId() : row.displayName(),
                String.valueOf(amount)));
        msg.setTextSize(10 * getTextScale());
        msg.setTextColor(UiThemeTokens.TEXT);
        container.addView(msg, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        PopupWindow popup = new PopupWindow(container, terminal.dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setOutsideTouchable(false);
        popup.setElevation(terminal.dp(8));
        terminal.trackPopup(popup);
        popup.showAtLocation(rootView, Gravity.CENTER, 0, 0);
        currentPlanningPopup = popup;
    }

    private static void dismissPlanningPopup() {
        if (currentPlanningPopup != null) {
            try { currentPlanningPopup.dismiss(); } catch (Throwable ignored) {}
            currentPlanningPopup = null;
        }
    }

    /**
     * 服务端推送计划摘要后弹出审阅对话框。
     * 显示：状态/字节数/产物 + 原料/缺料/副产物列表；底部 Cancel + Confirm（仅 ok 时启用）。
     */
    private static void showReviewPopup(ResourceTerminalFragment terminal,
                                         CraftingViewModel.CraftableRow row,
                                         long requestedAmount,
                                         com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload plan) {
        dismissPlanningPopup();
        View rootView = terminal.getView();
        if (rootView == null) return;

        int dp4 = terminal.dp(4);
        int dp6 = terminal.dp(6);
        int dp8 = terminal.dp(8);
        int dp12 = terminal.dp(12);

        LinearLayout container = new LinearLayout(terminal.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp12, dp12, dp12, dp12);
        ShapeDrawable cardBg = new ShapeDrawable();
        cardBg.setColor(UiThemeTokens.PANEL_BG);
        cardBg.setCornerRadius(terminal.dp(10));
        cardBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        container.setBackground(cardBg);

        // 标题：目标 ×N（优先使用样板真实产出 perExecOut × times，避免显示玩家下单数）
        String outName = (plan.finalOutputDisplayName() == null || plan.finalOutputDisplayName().isEmpty())
                ? (row.displayName() == null || row.displayName().isEmpty() ? row.itemId() : row.displayName())
                : plan.finalOutputDisplayName();
        com.yuyinrl.resourceobserver.integration.CraftingTreeNode rootNode = plan.treeRoot();
        long patternOut = (rootNode != null && rootNode.timesExecuted() > 0)
                ? rootNode.perExecOutAmount() * rootNode.timesExecuted() : 0L;
        long outAmt = patternOut > 0
                ? patternOut
                : (plan.finalOutputAmount() > 0 ? plan.finalOutputAmount() : requestedAmount);
        TextView title = new TextView(terminal.getContext());
        title.setText(tr("screen.resourceobserver.crafting.plan_title", outName, String.valueOf(outAmt)));
        title.setTextSize(12 * getTextScale());
        title.setTextColor(UiThemeTokens.TITLE);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp6;
        container.addView(title, titleLp);

        // 状态/字节数行
        boolean hasPlanId = plan.planId() != null && !plan.planId().isEmpty();
        boolean ok = "SUCCESS".equals(plan.status()) && !plan.simulation();
        int statusColor = ok ? UiThemeTokens.CYAN : 0xFFFF6464;
        TextView statusLine = new TextView(terminal.getContext());
        String statusKey = ok ? "screen.resourceobserver.crafting.plan_ok"
                : plan.simulation() ? "screen.resourceobserver.crafting.plan_simulation"
                : "screen.resourceobserver.crafting.plan_error";
        String msg = plan.message() == null ? "" : plan.message();
        statusLine.setText(tr(statusKey, plan.status(), msg, compact(plan.bytes())));
        statusLine.setTextSize(10 * getTextScale());
        statusLine.setTextColor(statusColor);
        LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statLp.bottomMargin = dp6;
        container.addView(statusLine, statLp);

        // 最终产物卡片（突出显示）：图标 + 名称 + ×数量
        {
            LinearLayout outCard = new LinearLayout(terminal.getContext());
            outCard.setOrientation(LinearLayout.HORIZONTAL);
            outCard.setGravity(Gravity.CENTER_VERTICAL);
            outCard.setPadding(dp8, dp8, dp8, dp8);
            ShapeDrawable outBg = new ShapeDrawable();
            outBg.setColor(UiThemeTokens.SECTION_BG);
            outBg.setCornerRadius(terminal.dp(8));
            outBg.setStroke(terminal.dp(1), UiThemeTokens.CYAN);
            outCard.setBackground(outBg);

            String outItemId = plan.finalOutputItemId();
            if (outItemId != null && !outItemId.isEmpty()) {
                InlineItemIconView outIcon = new InlineItemIconView(terminal.getContext());
                outIcon.setItemId(outItemId);
                LinearLayout.LayoutParams oiLp = new LinearLayout.LayoutParams(terminal.dp(22), terminal.dp(22));
                oiLp.rightMargin = dp8;
                outCard.addView(outIcon, oiLp);
            }
            LinearLayout outNameCol = new LinearLayout(terminal.getContext());
            outNameCol.setOrientation(LinearLayout.VERTICAL);
            TextView outLabel = new TextView(terminal.getContext());
            outLabel.setText(outName);
            outLabel.setTextSize(11 * getTextScale());
            outLabel.setTextColor(UiThemeTokens.TITLE);
            outLabel.setSingleLine(true);
            outNameCol.addView(outLabel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (rootNode != null && rootNode.timesExecuted() > 0 && rootNode.perExecOutAmount() > 0) {
                TextView outHint = new TextView(terminal.getContext());
                outHint.setText(compact(rootNode.perExecOutAmount()) + " / 次 × " + compact(rootNode.timesExecuted()) + " 次");
                outHint.setTextSize(8 * getTextScale());
                outHint.setTextColor(0xFF80B0C8);
                outHint.setSingleLine(true);
                outNameCol.addView(outHint, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            outCard.addView(outNameCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView outAmtTv = new TextView(terminal.getContext());
            outAmtTv.setText("×" + compact(outAmt));
            outAmtTv.setTextSize(13 * getTextScale());
            outAmtTv.setTextColor(UiThemeTokens.CYAN);
            outCard.addView(outAmtTv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            outLp.bottomMargin = dp6;
            container.addView(outCard, outLp);
        }

        // 滚动区：用料/缺料/副产物（不再显示样板，下单只关心物品输入输出）
        ScrollView scroll = new ScrollView(terminal.getContext());
        LinearLayout listCol = new LinearLayout(terminal.getContext());
        listCol.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listCol, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        appendStackList(terminal, listCol,
                tr("screen.resourceobserver.crafting.plan_used"),
                plan.usedItems(), UiThemeTokens.TEXT);
        appendStackList(terminal, listCol,
                tr("screen.resourceobserver.crafting.plan_missing"),
                plan.missingItems(), 0xFFFF6464);
        appendStackList(terminal, listCol,
                tr("screen.resourceobserver.crafting.plan_emitted"),
                plan.emittedItems(), 0xFFFFC864);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, terminal.dp(260));
        container.addView(scroll, scrollLp);

        // CPU 选择器（单选，第一项为 Auto）
        final String[] selectedCpu = {null};
        final java.util.List<TextView> cpuChips = new java.util.ArrayList<>();
        java.util.List<String> cpus = plan.cpus();
        if (cpus != null && cpus.size() >= 4) {
            TextView cpuHeader = new TextView(terminal.getContext());
            cpuHeader.setText(tr("screen.resourceobserver.crafting.plan_cpu_select"));
            cpuHeader.setTextSize(9 * getTextScale());
            cpuHeader.setTextColor(UiThemeTokens.TEXT_MUTED);
            LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chLp.topMargin = dp6;
            container.addView(cpuHeader, chLp);

            LinearLayout cpuRow = new LinearLayout(terminal.getContext());
            cpuRow.setOrientation(LinearLayout.HORIZONTAL);
            // Auto chip
            TextView autoChip = makeCpuChip(terminal, tr("screen.resourceobserver.crafting.plan_cpu_auto"), true);
            autoChip.setOnClickListener(v -> {
                selectedCpu[0] = null;
                for (TextView tv : cpuChips) styleCpuChip(tv, false);
                styleCpuChip(autoChip, true);
            });
            cpuRow.addView(autoChip, chipLp(terminal));
            for (int i = 0; i + 3 < cpus.size(); i += 4) {
                String name = cpus.get(i);
                String storage = cpus.get(i + 1);
                String coProc = cpus.get(i + 2);
                boolean busy = "1".equals(cpus.get(i + 3));
                long sb;
                int cp;
                try { sb = Long.parseLong(storage); } catch (NumberFormatException e) { sb = 0L; }
                try { cp = Integer.parseInt(coProc); } catch (NumberFormatException e) { cp = 0; }
                String label = (name == null || name.isBlank() ? "CPU#" + (i / 4 + 1) : name)
                        + " (" + compact(sb) + "/" + cp + (busy ? "★" : "") + ")";
                TextView chip = makeCpuChip(terminal, label, false);
                final String chipName = name == null ? "" : name;
                chip.setOnClickListener(v -> {
                    selectedCpu[0] = chipName.isEmpty() ? null : chipName;
                    styleCpuChip(autoChip, false);
                    for (TextView tv : cpuChips) styleCpuChip(tv, false);
                    styleCpuChip(chip, true);
                });
                cpuChips.add(chip);
                cpuRow.addView(chip, chipLp(terminal));
            }
            ScrollView cpuScroll = new ScrollView(terminal.getContext());
            cpuScroll.setHorizontalScrollBarEnabled(true);
            cpuScroll.addView(cpuRow, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            crLp.topMargin = dp4;
            container.addView(cpuScroll, crLp);
        }

        // 底部按钮
        LinearLayout buttonRow = new LinearLayout(terminal.getContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = dp8;

        PopupWindow popup = new PopupWindow(container, terminal.dp(620), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(terminal.dp(8));

        TextView cancelBtn = makeDialogButton(terminal,
                tr("screen.resourceobserver.crafting.order_cancel"), UiThemeTokens.TEXT_MUTED);
        cancelBtn.setOnClickListener(v -> {
            if (plan.planId() != null && !plan.planId().isEmpty()) {
                terminal.sendUiAction(UiActionType.CANCEL_CRAFT_PLAN, "", java.util.List.of(), plan.planId());
            }
            popup.dismiss();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.rightMargin = dp8;
        // 查看合成树按钮（如果计划包含树）
        if (plan.treeRoot() != null) {
            TextView treeBtn = makeDialogButton(terminal,
                    tr("screen.resourceobserver.crafting.tree_view_btn"), 0xFFA0E0A0);
            treeBtn.setOnClickListener(v -> showTreePopup(terminal, plan.treeRoot(), 0.0));
            LinearLayout.LayoutParams treeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            treeLp.rightMargin = dp8;
            buttonRow.addView(treeBtn, treeLp);
        }
        buttonRow.addView(cancelBtn, cancelLp);

        TextView confirmBtn = makeDialogButton(terminal,
                tr(plan.simulation()
                        ? "screen.resourceobserver.crafting.plan_submit_anyway"
                        : "screen.resourceobserver.crafting.plan_submit"),
                hasPlanId ? (plan.simulation() ? 0xFFFFC864 : UiThemeTokens.CYAN) : UiThemeTokens.TEXT_MUTED);
        confirmBtn.setOnClickListener(v -> {
            if (!hasPlanId) return;
            String pid = plan.planId();
            String value = (selectedCpu[0] == null || selectedCpu[0].isBlank())
                    ? pid : pid + "|" + selectedCpu[0];
            terminal.sendUiAction(UiActionType.CONFIRM_CRAFT_ORDER, "", java.util.List.of(), value);
            popup.dismiss();
        });
        buttonRow.addView(confirmBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container.addView(buttonRow, brLp);

        terminal.trackPopup(popup);
        popup.showAtLocation(rootView, Gravity.CENTER, 0, 0);
    }

    private static LinearLayout.LayoutParams chipLp(ResourceTerminalFragment terminal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = terminal.dp(4);
        lp.topMargin = terminal.dp(2);
        return lp;
    }

    private static TextView makeCpuChip(ResourceTerminalFragment terminal, String label, boolean selected) {
        TextView chip = new TextView(terminal.getContext());
        chip.setText(label);
        chip.setTextSize(9 * getTextScale());
        chip.setPadding(terminal.dp(6), terminal.dp(2), terminal.dp(6), terminal.dp(2));
        styleCpuChip(chip, selected);
        return chip;
    }

    private static void styleCpuChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? UiThemeTokens.CYAN : UiThemeTokens.TEXT_MUTED);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(selected ? 0x33A8FFFF : UiThemeTokens.SECTION_BG);
        bg.setCornerRadius(8);
        bg.setStroke(1, selected ? UiThemeTokens.CYAN : UiThemeTokens.DIVIDER);
        chip.setBackground(bg);
    }

    private static void appendStackList(ResourceTerminalFragment terminal, LinearLayout parent,
                                         String title, java.util.List<String> flatList, int amountColor) {
        if (flatList == null || flatList.isEmpty()) return;
        int dp2 = terminal.dp(2);
        int dp6 = terminal.dp(6);

        TextView header = new TextView(terminal.getContext());
        header.setText(title);
        header.setTextSize(9 * getTextScale());
        header.setTextColor(UiThemeTokens.TEXT_MUTED);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.topMargin = dp6;
        parent.addView(header, headLp);

        for (int i = 0; i + 2 < flatList.size(); i += 3) {
            String itemId = flatList.get(i);
            String displayName = flatList.get(i + 1);
            if (displayName == null || displayName.isEmpty()) displayName = itemId;
            String amount;
            try {
                amount = compact(Long.parseLong(flatList.get(i + 2)));
            } catch (NumberFormatException e) {
                amount = flatList.get(i + 2);
            }
            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            if (itemId != null && !itemId.isEmpty()) {
                InlineItemIconView icon = new InlineItemIconView(terminal.getContext());
                icon.setItemId(itemId);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        terminal.dp(14), terminal.dp(14));
                iconLp.rightMargin = terminal.dp(4);
                row.addView(icon, iconLp);
            }

            TextView nameTv = new TextView(terminal.getContext());
            nameTv.setText(displayName);
            nameTv.setTextSize(9 * getTextScale());
            nameTv.setTextColor(UiThemeTokens.TEXT);
            nameTv.setSingleLine(true);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(nameTv, nameLp);

            TextView amtTv = new TextView(terminal.getContext());
            amtTv.setText("×" + amount);
            amtTv.setTextSize(9 * getTextScale());
            amtTv.setTextColor(amountColor);
            row.addView(amtTv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp2;
            parent.addView(row, rowLp);
        }
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

        TextView clearBtn = new TextView(terminal.getContext());
        clearBtn.setText("×");
        clearBtn.setTextSize(12 * getTextScale());
        clearBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(dp4, 0, dp4, 0);
        clearBtn.setClickable(true);
        clearBtn.setFocusable(true);
        clearBtn.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
        clearBtn.setOnClickListener(v -> {
            searchEdit.setText("");
            terminal.scheduleSearchUpdate("");
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearLp.rightMargin = dp6;
        container.addView(clearBtn, clearLp);

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String text = s == null ? "" : s.toString();
                clearBtn.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                if (!text.equals(terminal.getBridge().getSearchQuery())) {
                    terminal.scheduleSearchUpdate(text);
                }
            }
        });

        searchEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                terminal.getBridge().setSearchBoxFocused(true);
            } else if (!terminal.isRebuilding()) {
                terminal.getBridge().setSearchBoxFocused(false);
            }
        });

        if (terminal.getBridge().isSearchBoxFocused()) {
            searchEdit.post(() -> {
                searchEdit.requestFocus();
                searchEdit.setSelection(searchEdit.getText().length());
            });
        }

        parent.addView(container);
    }

    /** 把毫秒格式化成 "12s" / "1m23s" / "1h05m"。 */
    private static String formatElapsed(long millis) {
        if (millis <= 0L) return "";
        long s = millis / 1000L;
        if (s < 60L) return s + "s";
        long m = s / 60L;
        long rs = s % 60L;
        if (m < 60L) return m + "m" + (rs < 10 ? "0" : "") + rs + "s";
        long h = m / 60L;
        long rm = m % 60L;
        return h + "h" + (rm < 10 ? "0" : "") + rm + "m";
    }

    // ---------------------------------------------------------- crafting tree

    /** 用任务进度比例向服务端请求树并展示。 */
    private static void requestAndShowTree(ResourceTerminalFragment terminal, String treeId, double progressFraction) {
        terminal.setCraftingTreeCallback(resp -> {
            if (resp.root() == null) {
                return;
            }
            // 服务端返回的进度可能为 0；以客户端 jobs 中已知的 progressFraction 为准
            showTreePopup(terminal, resp.root(), progressFraction);
        });
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.yuyinrl.resourceobserver.network.CraftingTreeRequestPayload(
                        terminal.getBridge().getObserverPos(), treeId));
    }

    /** 弹出合成树对话框：横向树形（左=最终产物，右=基础材料），仿 Web UI 风格。 */
    static void showTreePopup(ResourceTerminalFragment terminal,
                              com.yuyinrl.resourceobserver.integration.CraftingTreeNode root,
                              double progressFraction) {
        View rootView = terminal.getView();
        if (rootView == null || root == null) return;
        int dp4 = terminal.dp(4);
        int dp8 = terminal.dp(8);
        int dp12 = terminal.dp(12);

        LinearLayout container = new LinearLayout(terminal.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp12, dp12, dp12, dp12);
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(UiThemeTokens.PANEL_BG);
        bg.setCornerRadius(terminal.dp(10));
        bg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        container.setBackground(bg);

        TextView title = new TextView(terminal.getContext());
        title.setText(tr("screen.resourceobserver.crafting.tree_title", root.displayName(),
                String.valueOf(root.requiredAmount())));
        title.setTextSize(12 * getTextScale());
        title.setTextColor(UiThemeTokens.TITLE);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp4;
        container.addView(title, titleLp);

        if (progressFraction > 0.0) {
            LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, terminal.dp(6));
            pbLp.bottomMargin = dp8;
            container.addView(buildProgressBar(terminal, progressFraction), pbLp);
        }

        // 双向滚动：垂直 -> 水平 -> 树内容；Ctrl + 滚轮 = 缩放
        final View tree = buildHorizontalNode(terminal, root, 0);
        tree.setPivotX(0f);
        tree.setPivotY(0f);
        ScrollView vScroll = new ScrollView(terminal.getContext()) {
            @Override
            public boolean onGenericMotionEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_SCROLL
                        && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                    float dy = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    if (dy != 0f) {
                        float curr = tree.getScaleX();
                        if (curr <= 0f) curr = 1f;
                        float factor = dy > 0 ? 1.1f : (1f / 1.1f);
                        float next = Math.max(0.4f, Math.min(2.5f, curr * factor));
                        tree.setScaleX(next);
                        tree.setScaleY(next);
                        return true;
                    }
                }
                return super.onGenericMotionEvent(event);
            }
        };
        ShapeDrawable vbg = new ShapeDrawable();
        vbg.setColor(UiThemeTokens.SECTION_BG);
        vbg.setCornerRadius(terminal.dp(8));
        vbg.setStroke(terminal.dp(1), UiThemeTokens.SECTION_BORDER);
        vScroll.setBackground(vbg);
        HorizontalScrollView hScroll = new HorizontalScrollView(terminal.getContext());
        hScroll.setPadding(dp12, dp12, dp12, dp12);
        hScroll.addView(tree, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        vScroll.addView(hScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, terminal.dp(560));
        container.addView(vScroll, scrollLp);

        PopupWindow popup = new PopupWindow(container, terminal.dp(880), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(terminal.dp(8));
        terminal.trackPopup(popup);
        popup.showAtLocation(rootView, Gravity.CENTER, 0, 0);
    }

    /** 递归构建一行 = [节点卡片] + (有子节点时) [水平连线] [垂直主干 + 子节点列]。 */
    private static View buildHorizontalNode(ResourceTerminalFragment t,
                                            com.yuyinrl.resourceobserver.integration.CraftingTreeNode node,
                                            int depth) {
        LinearLayout row = new LinearLayout(t.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(buildNodeCard(t, node, depth));

        if (!node.children().isEmpty()) {
            // 父→列水平连线
            View hStub = new View(t.getContext());
            ShapeDrawable hbg = new ShapeDrawable();
            hbg.setColor(UiThemeTokens.DIVIDER);
            hStub.setBackground(hbg);
            LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(t.dp(14), t.dp(1));
            row.addView(hStub, hLp);

            // 垂直主干
            View vStripe = new View(t.getContext());
            ShapeDrawable vbg = new ShapeDrawable();
            vbg.setColor(UiThemeTokens.DIVIDER);
            vStripe.setBackground(vbg);
            LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                    t.dp(1), ViewGroup.LayoutParams.MATCH_PARENT);
            row.addView(vStripe, vLp);

            // 子节点列
            LinearLayout childCol = new LinearLayout(t.getContext());
            childCol.setOrientation(LinearLayout.VERTICAL);
            childCol.setPadding(t.dp(10), 0, 0, 0);
            for (var c : node.children()) {
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.topMargin = t.dp(4);
                clp.bottomMargin = t.dp(4);
                childCol.addView(buildHorizontalNode(t, c, depth + 1), clp);
            }
            row.addView(childCol);
        }
        return row;
    }

    /** 节点卡片：图标 + 名称 + itemId + ×N + 稀有度边框（按层级语义着色）。 */
    private static View buildNodeCard(ResourceTerminalFragment t,
                                      com.yuyinrl.resourceobserver.integration.CraftingTreeNode node,
                                      int depth) {
        // 稀有度配色（与项目深色主题一致：CARD_BG 底 + 强调色描边）
        int strokeColor;
        String tierLabel;
        if (node.isMissing() || node.isLoop() || node.truncated()) {
            strokeColor = UiThemeTokens.ROSE; tierLabel = "rare";
        } else if (depth == 0) {
            strokeColor = UiThemeTokens.AMBER; tierLabel = "legendary";
        } else if (!node.children().isEmpty()) {
            strokeColor = UiThemeTokens.EMERALD; tierLabel = "uncommon";
        } else {
            strokeColor = UiThemeTokens.CARD_BORDER; tierLabel = "common";
        }

        LinearLayout card = new LinearLayout(t.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(t.dp(8), t.dp(6), t.dp(8), t.dp(6));
        ShapeDrawable cbg = new ShapeDrawable();
        cbg.setColor(UiThemeTokens.CARD_BG);
        cbg.setCornerRadius(t.dp(8));
        cbg.setStroke(t.dp(1), strokeColor);
        card.setBackground(cbg);

        // 第 1 行: 图标 + 名称
        LinearLayout headRow = new LinearLayout(t.getContext());
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        if (node.itemId() != null && !node.itemId().isEmpty()) {
            InlineItemIconView icon = new InlineItemIconView(t.getContext());
            icon.setItemId(node.itemId());
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(t.dp(16), t.dp(16));
            iconLp.rightMargin = t.dp(6);
            headRow.addView(icon, iconLp);
        }
        TextView name = new TextView(t.getContext());
        String prefix = node.isMissing() ? "[缺] " : (node.isLoop() ? "[环] " : (node.truncated() ? "[…] " : ""));
        String dn = ItemNames.localize(node.itemId(),
                node.displayName() == null ? node.itemId() : node.displayName());
        name.setText(prefix + dn);
        name.setTextSize(10 * getTextScale());
        name.setTextColor(UiThemeTokens.TITLE);
        name.setSingleLine(true);
        headRow.addView(name);
        card.addView(headRow);

        // 第 2 行: itemId 灰字
        if (node.itemId() != null && !node.itemId().isEmpty()) {
            TextView idTv = new TextView(t.getContext());
            idTv.setText(node.itemId());
            idTv.setTextSize(7.5f * getTextScale());
            idTv.setTextColor(UiThemeTokens.TEXT_MUTED);
            idTv.setSingleLine(true);
            LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            idLp.topMargin = t.dp(2);
            card.addView(idTv, idLp);
        }

        // 第 3 行: ×N + 比例 + tier chip
        LinearLayout footRow = new LinearLayout(t.getContext());
        footRow.setOrientation(LinearLayout.HORIZONTAL);
        footRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = new TextView(t.getContext());
        count.setText("×" + compact(node.requiredAmount()));
        count.setTextSize(11 * getTextScale());
        count.setTextColor(node.isMissing() ? UiThemeTokens.ROSE : UiThemeTokens.CYAN);
        footRow.addView(count);
        if (!node.children().isEmpty() && node.timesExecuted() > 0 && node.perExecOutAmount() > 0) {
            TextView ratio = new TextView(t.getContext());
            ratio.setText("  1→" + compact(node.perExecOutAmount()) + " × " + compact(node.timesExecuted()));
            ratio.setTextSize(8 * getTextScale());
            ratio.setTextColor(UiThemeTokens.TEXT_MUTED);
            ratio.setSingleLine(true);
            footRow.addView(ratio);
        }
        // tier chip 推到右边
        View spacer = new View(t.getContext());
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 0, 1f);
        footRow.addView(spacer, spLp);
        TextView chip = new TextView(t.getContext());
        chip.setText(tierLabel + " · L" + depth);
        chip.setTextSize(7 * getTextScale());
        chip.setTextColor(strokeColor);
        chip.setPadding(t.dp(4), t.dp(1), t.dp(4), t.dp(1));
        ShapeDrawable chipBg = new ShapeDrawable();
        chipBg.setColor(0);
        chipBg.setCornerRadius(t.dp(4));
        chipBg.setStroke(t.dp(1), strokeColor);
        chip.setBackground(chipBg);
        footRow.addView(chip);

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = t.dp(3);
        card.addView(footRow, footLp);

        // 卡片宽度
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(t.dp(170),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardLp);
        return card;
    }
}
