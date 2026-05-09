package com.yuyinrl.resourceobserver.client.screen.dialog;

import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 模态弹窗基类（V2 / Vanilla）。
 *
 * <p>所有 V2 Dialog 都继承自本类，由 {@link DialogHost} 统一管理：
 * <ul>
 *   <li>输入拦截 → 由 host 负责</li>
 *   <li>默认不绘制 dim、不缩放、不淡入，保持接近原版 MC 的朴素实体窗口</li>
 *   <li>关闭路径：✕ 按钮 / dim 区域点击 / ESC 键 / 内部业务回调</li>
 * </ul>
 *
 * <p>子类只需：
 * <ol>
 *   <li>构造时调用 {@code preferredSize()} 给出期望尺寸（host 会居中并 setBounds）</li>
 *   <li>实现 {@link #render(GuiGraphics, int, int, float)}（应用 {@link #withAlpha(int)} 透明度）</li>
 *   <li>必要时重写 {@link #dismissOnDimClick()} / {@link #dismissOnEsc()} / 事件钩子</li>
 * </ol>
 */
public abstract class Dialog extends BaseWidget {

    /** 入场动画时长（毫秒）。 */
    protected static final long OPEN_MS = 250L;
    /** 出场动画时长（毫秒）。 */
    protected static final long CLOSE_MS = 100L;

    /** 期望尺寸（外部 host 用来计算居中位置）。 */
    public record Size(int width, int height) {}

    private DialogHost host;
    private final Component title;
    private long openedAtMs = -1L;
    private long closingAtMs = -1L;

    protected Dialog(Component title) {
        super(Rect.EMPTY);
        this.title = title;
    }

    /** 子类返回期望尺寸；host 据此居中布局。 */
    public abstract Size preferredSize();

    /** 标题（可空）。 */
    public Component title() {
        return title;
    }

    /** 由 host 在 push 时调用一次。 */
    void attach(DialogHost host) {
        this.host = host;
        this.openedAtMs = System.currentTimeMillis();
        onOpen();
    }

    /** 当前 host（可空）。 */
    public DialogHost host() {
        return host;
    }

    /** 请求关闭（开始播放出场动画）。重复调用幂等。 */
    public void requestClose() {
        if (closingAtMs < 0L) {
            closingAtMs = System.currentTimeMillis();
            onClose();
        }
    }

    /** 出场动画是否已结束（host 据此从栈中移除）。 */
    boolean isFullyClosed() {
        return closingAtMs >= 0L && (System.currentTimeMillis() - closingAtMs) >= CLOSE_MS;
    }

    /** 入场进度 0..1（线性）。 */
    protected float openProgress() {
        if (openedAtMs < 0L) {
            return 0f;
        }
        long dt = System.currentTimeMillis() - openedAtMs;
        return Math.min(1f, dt / (float) OPEN_MS);
    }

    /** 出场进度 0..1（线性，未关闭返回 0）。 */
    protected float closeProgress() {
        if (closingAtMs < 0L) {
            return 0f;
        }
        long dt = System.currentTimeMillis() - closingAtMs;
        return Math.min(1f, dt / (float) CLOSE_MS);
    }

    /** 当前透明度（0..1），结合入场 + 出场。 */
    public float currentAlpha() {
        return closingAtMs >= 0L ? 0f : 1f;
    }

    /** 当前缩放（0.85→1.0 OVERSHOOT，关闭时 1.0→0.95 ACCELERATE）。 */
    public float currentScale() {
        return 1.0f;
    }

    /** ease-out 平方。 */
    protected static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u;
    }

    /** ease-out OVERSHOOT（标准 1.70158）。 */
    protected static float easeOutOvershoot(float t) {
        final float s = 1.70158f;
        float u = t - 1f;
        return u * u * ((s + 1f) * u + s) + 1f;
    }

    /** 把当前 alpha 应用到 ARGB 颜色的 alpha 通道。 */
    protected int withAlpha(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int scaled = Math.round(a * currentAlpha());
        if (scaled < 0) scaled = 0;
        else if (scaled > 0xFF) scaled = 0xFF;
        return (scaled << 24) | (argb & 0x00FF_FFFF);
    }

    /** 默认：点 dim 区域关闭。模态进度类弹窗可覆盖返回 false。 */
    public boolean dismissOnDimClick() {
        return true;
    }

    /** 默认：ESC 关闭。 */
    public boolean dismissOnEsc() {
        return true;
    }

    /** 是否绘制全屏 dim 遮罩；默认关闭，避免压暗主界面标题和底层文字。 */
    public boolean dimEnabled() {
        return false;
    }

    /** 是否启用居中入场缩放动画；默认关闭，避免绘制坐标和点击坐标错位。 */
    public boolean centerScaleAnimation() {
        return false;
    }

    /** 入场前钩子。 */
    protected void onOpen() {}

    /** 关闭前钩子。 */
    protected void onClose() {}

    @Override
    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
}
