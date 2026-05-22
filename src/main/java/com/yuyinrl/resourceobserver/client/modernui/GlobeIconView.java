package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 地球图标 View —— 从预渲染的 PNG 资源加载 🌐 emoji 图标并使用 ModernUI {@link Canvas#drawImage} 绘制。
 * <p>
 * 解决 ModernUI 字体不支持 emoji 的问题：通过 AWT 预渲染 emoji 为 PNG，
 * 运行时解码为 ModernUI Bitmap → Image → Canvas 绘制。
 */
public final class GlobeIconView extends View {

    private static final Paint IMAGE_PAINT = new Paint();
    private static final String PNG_PATH = "/assets/resourceobserver/textures/gui/globe_icon.png";

    /**
     * 垂直微调 — 正值为图标下移，负值为上移。
     * 范围建议 -0.12 ~ 0.12，对应按钮高度的约 -12% ~ +12%。
     * 调这个值就行，不用重新生成 PNG。
     */
    private static final float VERTICAL_SHIFT = 0.06f;

    @Nullable
    private Image cachedImage;
    private boolean loadAttempted;

    public GlobeIconView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@Nonnull Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;

        Image image = getOrLoadImage();
        if (image != null) {
            int dy = Math.round(getHeight() * VERTICAL_SHIFT);
            if (dy != 0) {
                canvas.translate(0, dy);
            }
            canvas.drawImage(image,
                    0, 0, image.getWidth(), image.getHeight(),
                    0, 0, getWidth(), getHeight(),
                    IMAGE_PAINT);
        }
    }

    @Nullable
    private Image getOrLoadImage() {
        if (cachedImage != null) return cachedImage;
        if (loadAttempted) return null;
        loadAttempted = true;

        try {
            Bitmap bitmap = loadPngFromClasspath(PNG_PATH);
            if (bitmap == null) return null;

            var rc = icyllis.modernui.core.Core.peekUiRecordingContext();
            if (rc == null) return null;

            cachedImage = Image.createTextureFromBitmap(rc, bitmap);
            bitmap.close();
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[GlobeIconView] Failed to load globe icon PNG", e);
        }
        return cachedImage;
    }

    /**
     * 从 classpath 加载 PNG，使用 AWT ImageIO 解码，转换为 ModernUI Bitmap（RGBA_8888）。
     */
    @Nullable
    private static Bitmap loadPngFromClasspath(String path) {
        try (InputStream is = GlobeIconView.class.getResourceAsStream(path)) {
            if (is == null) {
                ResourceObserverMod.LOGGER.warn("[GlobeIconView] PNG not found at classpath: {}", path);
                return null;
            }
            BufferedImage awt = ImageIO.read(is);
            if (awt == null) return null;

            int w = awt.getWidth();
            int h = awt.getHeight();
            int[] pixels = awt.getRGB(0, 0, w, h, null, 0, w);

            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
            buf.order(ByteOrder.nativeOrder());
            for (int p : pixels) {
                buf.put((byte) ((p >> 16) & 0xFF)); // R
                buf.put((byte) ((p >> 8) & 0xFF));  // G
                buf.put((byte) (p & 0xFF));         // B
                buf.put((byte) ((p >> 24) & 0xFF)); // A
            }
            buf.flip();

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Format.RGBA_8888);
            boolean ok = bitmap.copyPixelsFromBuffer(buf, w * 4, 0, 0, w, h);
            if (!ok) {
                bitmap.close();
                return null;
            }
            return bitmap;
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.warn("[GlobeIconView] Failed to decode PNG", e);
            return null;
        }
    }
}
