package com.yuyinrl.resourceobserver.client.ui.modern;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

/**
 * ModernUI 版 Overview 预览 Fragment（Mock 数据）。
 */
public class ModernUiOverviewFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setGravity(Gravity.TOP);

        root.addView(section("HEADER"));
        root.addView(section("KPI GRID"));
        root.addView(section("CHART"));
        root.addView(section("WATCHLIST"));
        root.addView(section("TABLE"));
        return root;
    }

    private View section(String title) {
        TextView textView = new TextView(requireContext());
        textView.setText(title);
        textView.setPadding(16, 12, 16, 12);
        return textView;
    }
}
