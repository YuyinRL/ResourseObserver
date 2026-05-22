package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * ModernUI 总览预览启动器。
 */
public final class OverviewPreviewLauncher {
    private OverviewPreviewLauncher() {
    }

    public static void open() {
        try {
            Minecraft mc = Minecraft.getInstance();
            OverviewPreviewState state = OverviewPreviewFixture.createSample();
            Fragment fragment = new OverviewPreviewFragment(state);
            ScreenCallback callback = new ScreenCallback() {
                @Override
                public boolean isPauseScreen() {
                    return false;
                }
            };
            var previewScreen = MuiModApi.get().createScreen(
                    fragment,
                    callback,
                    mc.screen,
                    Component.translatable("screen.resourceobserver.modernui.preview.title").getString()
            );
            mc.setScreen(previewScreen);
        } catch (Throwable throwable) {
            ResourceObserverMod.LOGGER.error("Failed to open ModernUI overview preview", throwable);
        }
    }
}
