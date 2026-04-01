package com.yuyinrl.resourceobserver.client.ui;

public record UiLayoutSpec(
        int basePanelWidth,
        int basePanelHeight,
        int minPanelWidth,
        int minPanelHeight,
        int smallMaxWidth,
        int mediumMaxWidth,
        Profile smallProfile,
        Profile mediumProfile,
        Profile largeProfile
) {
    public Profile resolveProfile(int viewportWidth) {
        if (viewportWidth <= smallMaxWidth) {
            return smallProfile;
        }
        if (viewportWidth <= mediumMaxWidth) {
            return mediumProfile;
        }
        return largeProfile;
    }

    public static UiLayoutSpec defaultOverview() {
        return new UiLayoutSpec(
                980,
                640,
                700,
                460,
                1120,
                1520,
                new Profile(8, 24, 8, 54, 92, 148, 98, 8),
                new Profile(10, 24, 8, 58, 100, 168, 108, 8),
                new Profile(12, 24, 8, 62, 108, 182, 116, 8)
        );
    }

    public record Profile(
            int margin,
            int chromeHeight,
            int sectionGap,
            int headerHeight,
            int kpiHeight,
            int chartHeight,
            int watchlistHeight,
            int scrollbarWidth
    ) {
    }
}
