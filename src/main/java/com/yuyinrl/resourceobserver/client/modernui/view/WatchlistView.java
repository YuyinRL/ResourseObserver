package com.yuyinrl.resourceobserver.client.modernui.view;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 关注列表视图 —— 使用 LinearLayout 组合 TextView 实现的物品卡片列表。
 * <p>
 * 水平排列最多 3 张卡片，每张显示物品名称、净流量和库存。
 * 选中的物品使用高亮边框。
 */
public class WatchlistView extends LinearLayout {

    /** 回调接口 */
    public interface Callback {
        void onItemSelected(String itemId);
        void onItemRemoved(String itemId);
    }

    private final Callback callback;
    private final List<WatchlistCard> cards = new ArrayList<>();
    private String selectedItemId;

    public WatchlistView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setOrientation(HORIZONTAL);

        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(UiThemeTokens.SECTION_BG);
        bg.setStroke(dp(1), UiThemeTokens.SECTION_BORDER);
        setBackground(bg);
        setPadding(dp(8), dp(8), dp(8), dp(8));
    }

    public void setSelectedItemId(String itemId) {
        this.selectedItemId = itemId;
        for (WatchlistCard card : cards) {
            card.setSelectedState(itemId != null && itemId.equals(card.itemId));
        }
    }

    /**
     * 更新关注列表数据。
     */
    public void update(List<OverviewViewModel.WatchlistItem> items, String selectedItemId) {
        this.selectedItemId = selectedItemId;
        removeAllViews();
        cards.clear();

        if (items == null || items.isEmpty()) {
            var placeholder = new TextView(getContext());
            placeholder.setText(Component.translatable("screen.resourceobserver.overview.section.watchlist").getString());
            placeholder.setTextSize(sp(11));
            placeholder.setTextColor(UiThemeTokens.TEXT_MUTED);
            placeholder.setGravity(Gravity.CENTER);
            addView(placeholder, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return;
        }

        int displayCount = Math.min(3, items.size());
        for (int i = 0; i < displayCount; i++) {
            OverviewViewModel.WatchlistItem item = items.get(i);
            WatchlistCard card = new WatchlistCard(getContext(), item, callback);
            card.setSelectedState(item.itemId().equals(selectedItemId));
            LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            if (i < displayCount - 1) {
                params.rightMargin = dp(8);
            }
            addView(card, params);
            cards.add(card);
        }
    }

    /**
     * 单张关注列表卡片 —— 使用 LinearLayout + TextView 组合。
     */
    static class WatchlistCard extends FrameLayout {

        private static final int MAX_NAME_LENGTH = 20;
        private static final int TRUNCATE_NAME_LENGTH = 18;

        final String itemId;
        private boolean selectedState;

        WatchlistCard(Context context, OverviewViewModel.WatchlistItem item, Callback callback) {
            super(context);
            this.itemId = item.itemId();
            setClickable(true);
            setPadding(dp(7), dp(6), dp(7), dp(6));
            updateBackground(false);

            setOnClickListener(v -> {
                if (callback != null) {
                    callback.onItemSelected(item.itemId());
                }
            });

            // 垂直文本列
            var column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);

            // 物品名
            var nameTv = new TextView(context);
            nameTv.setTextSize(sp(10));
            nameTv.setTextColor(UiThemeTokens.TEXT);
            String name = item.displayName();
            nameTv.setText(name.length() > MAX_NAME_LENGTH ? name.substring(0, TRUNCATE_NAME_LENGTH) + "..." : name);
            column.addView(nameTv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // 净流量
            int netColor = item.netPerMinute() >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
            String netText = (item.netPerMinute() >= 0 ? "+" : "") + item.netPerMinute() + "/min";
            var netTv = new TextView(context);
            netTv.setTextSize(sp(9));
            netTv.setTextColor(netColor);
            netTv.setText(Component.translatable("screen.resourceobserver.overview.watchlist.net", netText).getString());
            var netParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            netParams.topMargin = dp(3);
            column.addView(netTv, netParams);

            // 库存
            var stockTv = new TextView(context);
            stockTv.setTextSize(sp(9));
            stockTv.setTextColor(UiThemeTokens.TEXT_MUTED);
            stockTv.setText(Component.translatable("screen.resourceobserver.overview.watchlist.stock", item.stock()).getString());
            var stockParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            stockParams.topMargin = dp(1);
            column.addView(stockTv, stockParams);

            addView(column, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.START | Gravity.TOP
            ));

            // 移除按钮
            var removeBtn = new TextView(context);
            removeBtn.setText("×");
            removeBtn.setTextSize(sp(11));
            removeBtn.setTextColor(UiThemeTokens.ROSE);
            removeBtn.setClickable(true);
            removeBtn.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onItemRemoved(item.itemId());
                }
            });
            addView(removeBtn, new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END | Gravity.TOP
            ));
        }

        void setSelectedState(boolean selected) {
            this.selectedState = selected;
            updateBackground(isHovered());
        }

        @Override
        public void onHoverChanged(boolean hovered) {
            super.onHoverChanged(hovered);
            updateBackground(hovered);
        }

        private void updateBackground(boolean hover) {
            ShapeDrawable bg = new ShapeDrawable();
            bg.setCornerRadius(dp(4));
            bg.setColor(selectedState ? 0xCC142338 : UiThemeTokens.CARD_BG);
            bg.setStroke(dp(1), selectedState ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER);
            setBackground(bg);
        }
    }
}
