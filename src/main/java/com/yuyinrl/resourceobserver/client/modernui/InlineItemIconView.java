package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

import javax.annotation.Nonnull;

/**
 * 内联物品图标 View —— 替代浮动渲染方案的 {@code ItemIconView}。
 * <p>
 * 直接在 MUI 的 {@link Canvas#drawImage} 中绘制预缓存的 {@link Image}，
 * 图标与列表项在同一渲染流中，跟随滚动自然移动，无需外部坐标捕获和后期合成。
 * <p>
 * 首次显示时若缓存尚未就绪（{@link ItemTextureCache} 需等待
 * {@code ScreenEvent.Render.Post} 离屏渲染），通过 {@code postInvalidateDelayed}
 * 延迟重绘，直到 Bitmap 就绪后在 MUI 线程上延迟创建 Image 并绘制。
 */
public final class InlineItemIconView extends View {

    private static final Paint ICON_PAINT = new Paint();
    /** 最大重试次数（约 120 帧 ≈ 2 秒），防止无限重绘循环 */
    private static final int MAX_RETRIES = 120;

    private String itemId;
    private int retryCount;
    private boolean drawn;

    public InlineItemIconView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void setItemId(String id) {
        this.itemId = id;
        this.retryCount = 0;
        this.drawn = false;
        if (id != null && !id.isBlank()) {
            ItemTextureCache.getInstance().requestItem(id);
        }
        invalidate();
    }

    public String getItemId() {
        return itemId;
    }

    @Override
    protected void onDraw(@Nonnull Canvas canvas) {
        if (itemId == null || getWidth() <= 0 || getHeight() <= 0) return;

        Image image = ItemTextureCache.getInstance().getOrCreateImage(itemId);
        if (image != null) {
            canvas.drawImage(image,
                    0, 0, image.getWidth(), image.getHeight(),
                    0, 0, getWidth(), getHeight(),
                    ICON_PAINT);
            if (!drawn) {
                drawn = true;
                ResourceObserverMod.LOGGER.debug("InlineItemIconView: drew {} ({}x{}→{}x{})",
                        itemId, image.getWidth(), image.getHeight(), getWidth(), getHeight());
            }
            retryCount = 0;
        } else if (retryCount < MAX_RETRIES) {
            retryCount++;
            postInvalidateDelayed(16);
        } else if (retryCount == MAX_RETRIES) {
            retryCount++;
            ResourceObserverMod.LOGGER.warn("InlineItemIconView: gave up retrying for {}", itemId);
        }
    }
}
