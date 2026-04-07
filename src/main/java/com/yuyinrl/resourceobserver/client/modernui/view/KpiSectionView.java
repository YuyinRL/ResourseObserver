package com.yuyinrl.resourceobserver.client.modernui.view;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * KPI 区段容器视图 —— 水平排列 4 张 KPI 卡片。
 * <p>
 * 每张卡片等宽，间距 8dp。点击卡片触发回调。
 */
public class KpiSectionView extends LinearLayout {

    private final List<KpiCardView> cardViews = new ArrayList<>();
    private final Consumer<OverviewViewModel.KpiType> clickCallback;

    public KpiSectionView(Context context, Consumer<OverviewViewModel.KpiType> clickCallback) {
        super(context);
        this.clickCallback = clickCallback;
        setOrientation(HORIZONTAL);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setStroke(dp(1), UiThemeTokens.SECTION_BORDER);
        setBackground(bg);
        setPadding(dp(8), dp(8), dp(8), dp(8));

        // 预创建 4 张 KPI 卡片
        for (int i = 0; i < 4; i++) {
            KpiCardView card = new KpiCardView(context);
            card.setOnKpiClickListener(type -> {
                if (this.clickCallback != null) {
                    this.clickCallback.accept(type);
                }
            });
            LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            if (i < 3) {
                params.rightMargin = dp(8);
            }
            addView(card, params);
            cardViews.add(card);
        }
    }

    /**
     * 更新 KPI 数据。
     *
     * @param kpis KPI 指标列表（通常 4 个）
     */
    public void update(List<OverviewViewModel.KpiMetric> kpis) {
        for (int i = 0; i < cardViews.size() && i < kpis.size(); i++) {
            cardViews.get(i).update(kpis.get(i));
        }
    }
}
