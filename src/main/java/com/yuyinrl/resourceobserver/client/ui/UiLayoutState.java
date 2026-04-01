package com.yuyinrl.resourceobserver.client.ui;

import net.minecraft.util.Mth;

public record UiLayoutState(
        UiRect panel,
        UiRect fixedChrome,
        UiRect scrollViewport,
        UiRect closeButton,
        UiRect sizeButton,
        int sectionGap,
        int headerHeight,
        int kpiHeight,
        int chartHeight,
        int watchlistHeight,
        int scrollbarWidth
) {
    public static UiLayoutState compute(
            int screenWidth,
            int screenHeight,
            UiLayoutSpec spec,
            float manualScale
    ) {
        UiLayoutSpec.Profile profile = spec.resolveProfile(screenWidth);

        float autoScale = Math.min(
                (screenWidth - 32.0f) / spec.basePanelWidth(),
                (screenHeight - 32.0f) / spec.basePanelHeight()
        );
        float panelScale = Mth.clamp(autoScale, 0.70f, 1.18f);
        float microScale = Mth.clamp(manualScale, 0.95f, 1.05f);

        int panelWidth = Math.max(spec.minPanelWidth(), Math.round(spec.basePanelWidth() * panelScale));
        int panelHeight = Math.max(spec.minPanelHeight(), Math.round(spec.basePanelHeight() * panelScale));
        panelWidth = Math.min(panelWidth, screenWidth - 16);
        panelHeight = Math.min(panelHeight, screenHeight - 16);

        UiRect panel = new UiRect((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2, panelWidth, panelHeight);
        UiRect content = panel.inset(profile.margin());

        int chromeHeight = Math.max(20, Math.round(profile.chromeHeight() * microScale));
        UiRect fixedChrome = new UiRect(content.x(), content.y(), content.width(), Math.min(chromeHeight, content.height()));

        int viewportY = fixedChrome.bottom() + profile.sectionGap();
        UiRect scrollViewport = new UiRect(
                content.x(),
                viewportY,
                content.width(),
                Math.max(40, content.bottom() - viewportY)
        );

        int closeSize = 16;
        UiRect closeButton = new UiRect(panel.right() - closeSize - 8, panel.y() + 8, closeSize, closeSize);
        UiRect sizeButton = new UiRect(closeButton.x() - 20, closeButton.y(), 16, 16);

        return new UiLayoutState(
                panel,
                fixedChrome,
                scrollViewport,
                closeButton,
                sizeButton,
                profile.sectionGap(),
                Math.max(36, Math.round(profile.headerHeight() * microScale)),
                Math.max(64, Math.round(profile.kpiHeight() * microScale)),
                Math.max(120, Math.round(profile.chartHeight() * microScale)),
                Math.max(82, Math.round(profile.watchlistHeight() * microScale)),
                Math.max(6, profile.scrollbarWidth())
        );
    }
}
