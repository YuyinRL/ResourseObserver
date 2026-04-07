package com.yuyinrl.resourceobserver.client.modernui.view;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分组物品表格视图 —— 使用 LinearLayout + TextView 组合实现。
 * <p>
 * 结构：
 * <pre>
 * LinearLayout (VERTICAL, 区段背景)
 * ├── LinearLayout (HORIZONTAL, 标题行 + 物品计数)
 * ├── LinearLayout (HORIZONTAL, 筛选控件行)
 * ├── LinearLayout (HORIZONTAL, 表头行)
 * └── ScrollView
 *     └── LinearLayout (VERTICAL, 数据行容器)
 *         ├── [GroupHeaderRow] 分组标题
 *         ├── [DataRow] 物品数据行
 *         └── ...
 * </pre>
 */
public class TableView extends LinearLayout {

    private static final int MAX_GROUP_LABEL_LENGTH = 12;
    private static final int TRUNCATE_GROUP_LABEL_LENGTH = 10;
    private static final int MAX_ITEM_NAME_LENGTH = 25;
    private static final int TRUNCATE_ITEM_NAME_LENGTH = 23;

    /** 回调接口 */
    public interface Callback {
        void onItemSelected(String itemId);
        void onToggleStar(String itemId);
        void onSortChanged(String sortMode);
        void onGroupFilterChanged(String groupKey);
        void onStatusFilterChanged(String statusFilter);
        void onResetFilters();
    }

    private final Callback callback;
    private List<OverviewViewModel.TableGroup> groups = List.of();
    private OverviewViewModel.UiState uiState;
    private final Map<String, Boolean> expandedState = new HashMap<>();
    private String selectedItemId;

    private final TextView countTv;
    private final LinearLayout filterRow;
    private final LinearLayout dataContainer;

    public TableView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setOrientation(VERTICAL);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setStroke(dp(1), UiThemeTokens.SECTION_BORDER);
        setBackground(bg);
        setPadding(dp(8), dp(8), dp(8), dp(8));

        // === 标题行 ===
        var titleRow = new LinearLayout(context);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        var titleTv = new TextView(context);
        titleTv.setText(Component.translatable("screen.resourceobserver.overview.section.table").getString());
        titleTv.setTextSize(sp(11));
        titleTv.setTextColor(UiThemeTokens.TEXT);
        titleRow.addView(titleTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        countTv = new TextView(context);
        countTv.setTextSize(sp(9));
        countTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        titleRow.addView(countTv, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addView(titleRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // === 筛选控件行 ===
        filterRow = new LinearLayout(context);
        filterRow.setOrientation(HORIZONTAL);
        filterRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        var filterParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filterParams.topMargin = dp(4);
        addView(filterRow, filterParams);

        // === 表头行 ===
        var headerRow = createHeaderRow(context);
        var headerParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(4);
        addView(headerRow, headerParams);

        // 分割线
        View divider = new View(context);
        ShapeDrawable divBg = new ShapeDrawable();
        divBg.setColor(UiThemeTokens.DIVIDER);
        divider.setBackground(divBg);
        var divParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divParams.topMargin = dp(2);
        addView(divider, divParams);

        // === 数据行容器 ===
        dataContainer = new LinearLayout(context);
        dataContainer.setOrientation(VERTICAL);
        var dataParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dataParams.topMargin = dp(2);
        addView(dataContainer, dataParams);
    }

    /**
     * 更新表格数据。
     */
    public void update(List<OverviewViewModel.TableGroup> groups, OverviewViewModel.UiState uiState) {
        this.groups = groups != null ? groups : List.of();
        this.uiState = uiState;

        int totalItems = this.groups.stream().mapToInt(g -> g.rows().size()).sum();
        countTv.setText(Component.translatable(
                "screen.resourceobserver.overview.table.items_active", totalItems
        ).getString());

        updateFilterRow();
        rebuildDataRows();
    }

    // ========== 表头行 ==========

    private LinearLayout createHeaderRow(Context context) {
        var row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);

        // 星标列
        addHeaderCell(row, "☆", dp(20));
        // 名称列
        addHeaderCell(row, "Name", 0, 2.0f);
        // 生产列
        addHeaderCell(row, "Prod", 0, 1.0f);
        // 消耗列
        addHeaderCell(row, "Cons", 0, 1.0f);
        // 净值列
        addHeaderCell(row, "Net", 0, 1.0f);
        // 库存列
        addHeaderCell(row, "Stock", 0, 1.0f);

        return row;
    }

    private void addHeaderCell(LinearLayout row, String text, int width) {
        addHeaderCell(row, text, width, 0);
    }

    private void addHeaderCell(LinearLayout row, String text, int width, float weight) {
        var tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(sp(8));
        tv.setTextColor(UiThemeTokens.TEXT_MUTED);
        if (weight > 0) {
            row.addView(tv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        } else {
            row.addView(tv, new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    // ========== 筛选控件行 ==========

    private void updateFilterRow() {
        filterRow.removeAllViews();
        if (uiState == null) return;

        // 分组筛选按钮
        String groupLabel = uiState.groupNameByKey(uiState.groupFilterKey());
        if (groupLabel.length() > MAX_GROUP_LABEL_LENGTH) groupLabel = groupLabel.substring(0, TRUNCATE_GROUP_LABEL_LENGTH) + "..";
        addFilterButton(filterRow, groupLabel);

        // 状态筛选按钮
        addFilterButton(filterRow, uiState.statusFilter().name());

        // 重置按钮
        var resetBtn = new TextView(getContext());
        resetBtn.setText("Reset");
        resetBtn.setTextSize(sp(8));
        resetBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
        resetBtn.setClickable(true);
        resetBtn.setPadding(dp(6), dp(3), dp(6), dp(3));
        resetBtn.setOnClickListener(v -> {
            if (callback != null) callback.onResetFilters();
        });
        var resetParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.leftMargin = dp(4);
        filterRow.addView(resetBtn, resetParams);
    }

    private void addFilterButton(LinearLayout row, String label) {
        var btn = new TextView(getContext());
        btn.setText(label);
        btn.setTextSize(sp(8));
        btn.setTextColor(UiThemeTokens.TEXT);
        btn.setPadding(dp(6), dp(3), dp(6), dp(3));

        ShapeDrawable btnBg = new ShapeDrawable();
        btnBg.setCornerRadius(dp(3));
        btnBg.setColor(0x5510182C);
        btnBg.setStroke(dp(1), UiThemeTokens.DIVIDER);
        btn.setBackground(btnBg);

        var params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(4);
        row.addView(btn, params);
    }

    // ========== 数据行 ==========

    private void rebuildDataRows() {
        dataContainer.removeAllViews();

        for (OverviewViewModel.TableGroup group : groups) {
            // 分组标题行
            addGroupHeaderRow(group);

            boolean expanded = expandedState.getOrDefault(group.key(), true);
            if (expanded) {
                for (OverviewViewModel.TableRow tableRow : group.rows()) {
                    addDataRow(tableRow);
                }
            }
        }
    }

    private void addGroupHeaderRow(OverviewViewModel.TableGroup group) {
        var row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(3), dp(4), dp(3));
        row.setClickable(true);

        ShapeDrawable rowBg = new ShapeDrawable();
        rowBg.setColor(0x33202A40);
        rowBg.setCornerRadius(dp(2));
        row.setBackground(rowBg);

        boolean expanded = expandedState.getOrDefault(group.key(), true);
        String marker = expanded ? "[-]" : "[+]";

        var markerTv = new TextView(getContext());
        markerTv.setText(marker);
        markerTv.setTextSize(sp(9));
        markerTv.setTextColor(UiThemeTokens.CYAN);
        row.addView(markerTv, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        var titleTv = new TextView(getContext());
        titleTv.setText(" " + group.title());
        titleTv.setTextSize(sp(9));
        titleTv.setTextColor(UiThemeTokens.CYAN);
        row.addView(titleTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        row.setOnClickListener(v -> {
            Boolean current = expandedState.getOrDefault(group.key(), true);
            expandedState.put(group.key(), !current);
            rebuildDataRows();
        });

        var params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(2);
        dataContainer.addView(row, params);
    }

    private void addDataRow(OverviewViewModel.TableRow tr) {
        var row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(2), dp(2), dp(2));
        row.setClickable(true);

        boolean selected = tr.itemId().equals(selectedItemId);

        // 行背景
        ShapeDrawable rowBg = new ShapeDrawable();
        if (selected) {
            rowBg.setColor(0x44224488);
        } else if (tr.critical()) {
            rowBg.setColor(0x22FF4444);
        } else {
            rowBg.setColor(0x00000000);
        }
        row.setBackground(rowBg);

        // 星标
        var starTv = new TextView(getContext());
        starTv.setText(tr.starred() ? "★" : "☆");
        starTv.setTextSize(sp(9));
        starTv.setTextColor(tr.starred() ? UiThemeTokens.AMBER : UiThemeTokens.TEXT_MUTED);
        starTv.setClickable(true);
        starTv.setOnClickListener(v -> {
            if (callback != null) callback.onToggleStar(tr.itemId());
        });
        row.addView(starTv, new LayoutParams(dp(20), ViewGroup.LayoutParams.WRAP_CONTENT));

        // 名称
        var nameTv = new TextView(getContext());
        String name = tr.displayName();
        nameTv.setText(name.length() > MAX_ITEM_NAME_LENGTH ? name.substring(0, TRUNCATE_ITEM_NAME_LENGTH) + ".." : name);
        nameTv.setTextSize(sp(9));
        nameTv.setTextColor(UiThemeTokens.TEXT);
        row.addView(nameTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.0f));

        // 生产
        var prodTv = new TextView(getContext());
        prodTv.setText(formatRate(tr.production()));
        prodTv.setTextSize(sp(9));
        prodTv.setTextColor(UiThemeTokens.CYAN);
        row.addView(prodTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // 消耗
        var consTv = new TextView(getContext());
        consTv.setText(formatRate(tr.consumption()));
        consTv.setTextSize(sp(9));
        consTv.setTextColor(UiThemeTokens.ROSE);
        row.addView(consTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // 净值
        var netTv = new TextView(getContext());
        netTv.setText(formatRate(tr.net()));
        netTv.setTextSize(sp(9));
        netTv.setTextColor(tr.net() >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE);
        row.addView(netTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // 库存
        var stockTv = new TextView(getContext());
        stockTv.setText(formatRate(tr.stock()));
        stockTv.setTextSize(sp(9));
        stockTv.setTextColor(UiThemeTokens.TEXT);
        row.addView(stockTv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // 行点击
        row.setOnClickListener(v -> {
            selectedItemId = tr.itemId();
            if (callback != null) callback.onItemSelected(tr.itemId());
            rebuildDataRows(); // 刷新选中状态
        });

        var params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(1);
        dataContainer.addView(row, params);
    }

    private static String formatRate(long value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) {
            return String.format("%.1fB", value / 1_000_000_000.0);
        } else if (abs >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (abs >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        } else {
            return String.valueOf(value);
        }
    }
}
