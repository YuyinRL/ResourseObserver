package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.text.Spannable;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.text.style.RelativeSizeSpan;
import icyllis.modernui.text.style.StrikethroughSpan;
import icyllis.modernui.text.style.StyleSpan;
import icyllis.modernui.text.style.UnderlineSpan;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.Menu;
import icyllis.modernui.view.MenuItem;
import icyllis.modernui.view.SubMenu;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;

import java.util.Arrays;
import java.util.List;

import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.*;

/**
 * Dev Components 页面构建器 —— 展示所有可用 Modern UI 控件的可滚动参考页面。
 * <p>
 * 基于 Modern UI 框架 TestFragment.java 及 CenterFragment2.java 的实现，
 * 包含：Material3 风格按钮、禁用态、动画、RippleDrawable、ContextMenu + SubMenu、
 * 丰富文本 Spannable、ShapeDrawable 分隔线、Tooltip 等全部组件。
 */
final class DevComponentsPageBuilder {

    private DevComponentsPageBuilder() {}

    static void build(ResourceTerminalFragment terminal, FrameLayout container, int contentW, int contentH) {
        ScrollView scroll = new ScrollView(terminal.getContext());
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(scroll);

        LinearLayout root = new LinearLayout(terminal.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));
        // LayoutTransition gives animated add/remove of children
        root.setLayoutTransition(new LayoutTransition());
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Page title
        addText(root, "Dev Components Showcase", UiThemeTokens.TITLE, 14, 0,
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        addText(root, "Modern UI 3.12.0 — All framework widgets", UiThemeTokens.TEXT_MUTED, 9,
                terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);

        int gap = terminal.dp(6);

        buildMaterial3ButtonsSection(terminal, root, gap);
        buildButtonStatesSection(terminal, root, gap);
        buildTextInputSection(terminal, root, gap);
        buildCheckboxSection(terminal, root, gap);
        buildRadioSection(terminal, root, gap);
        buildSwitchSection(terminal, root, gap);
        buildSeekBarSection(terminal, root, gap);
        buildProgressBarSection(terminal, root, gap);
        buildSpinnerSection(terminal, root, gap);
        buildContextMenuSection(terminal, root, gap);
        buildShapeDrawableSection(terminal, root, gap);
        buildGradientDrawableSection(terminal, root, gap);
        buildStateListDrawableSection(terminal, root, gap);
        buildRichTextSection(terminal, root, gap);
        buildAnimationSection(terminal, root, gap);
        buildToastSection(terminal, root, gap);
        buildPopupMenuSection(terminal, root, gap);
    }

    // ===================== Section helpers =====================

    private static void sectionTitle(LinearLayout parent, ResourceTerminalFragment t, String title) {
        TextView tv = createText(parent, title, UiThemeTokens.CYAN, 11, true);
        tv.setPadding(0, t.dp(4), 0, t.dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = t.dp(8);
        parent.addView(tv, lp);
    }

    private static LinearLayout panel(ResourceTerminalFragment t) {
        LinearLayout p = new LinearLayout(t.getContext());
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(t.dp(8), t.dp(6), t.dp(8), t.dp(6));
        p.setBackground(makeBackground(p, UiThemeTokens.CARD_BG, UiThemeTokens.CARD_BORDER, 8, 1));
        return p;
    }

    private static void label(LinearLayout parent, ResourceTerminalFragment t, String text) {
        addText(parent, text, UiThemeTokens.TEXT_MUTED, 8, t.dp(4),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
    }

    private static LinearLayout.LayoutParams wp(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private static LinearLayout hRow(ResourceTerminalFragment t) {
        LinearLayout row = new LinearLayout(t.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    // ===================== 1. Material3 Styled Buttons =====================

    private static void buildMaterial3ButtonsSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "1. Material3 Buttons (from TestFragment)");
        LinearLayout p = panel(t);

        // Filled button (default) — TestFragment i=8,9
        label(p, t, "Button() — Filled (default Material3 style)");
        Button filled = new Button(t.getContext(), null);
        filled.setText("Filled Button");
        filled.setTooltipText("Default filled button style");
        p.addView(filled, wp(t.dp(2)));

        // Outlined button — TestFragment i=1
        label(p, t, "Button(ctx, null, R.attr.buttonOutlinedStyle) — Outlined");
        Button outlined = new Button(t.getContext(), null, R.attr.buttonOutlinedStyle);
        outlined.setText("Outlined Button");
        outlined.setTooltipText("Outlined style with border");
        p.addView(outlined, wp(t.dp(2)));

        // Elevated button — TestFragment i=4
        label(p, t, "ToggleButton(ctx, null, R.attr.buttonElevatedStyle) — Elevated");
        ToggleButton elevated = new ToggleButton(t.getContext(), null, R.attr.buttonElevatedStyle);
        elevated.setText("Elevated Toggle");
        elevated.setTooltipText("Elevated style with shadow");
        p.addView(elevated, wp(t.dp(2)));

        // Our custom colored buttons (existing)
        label(p, t, "Custom colored buttons (statefulBackground)");
        LinearLayout btnRow = hRow(t);
        for (var entry : new int[][]{
                {UiThemeTokens.CYAN, 0xFF0A2838}, {UiThemeTokens.AMBER, 0xFF2A1A08},
                {UiThemeTokens.ROSE, 0xFF2A0A18}, {UiThemeTokens.EMERALD, 0xFF0A2A1A}}) {
            TextView colorBtn = new TextView(t.getContext());
            colorBtn.setText("  Action  ");
            colorBtn.setTextColor(entry[0]);
            colorBtn.setTextSize(9);
            colorBtn.setGravity(Gravity.CENTER);
            colorBtn.setPadding(t.dp(8), t.dp(4), t.dp(8), t.dp(4));
            colorBtn.setBackground(statefulBackground(colorBtn, entry[1], entry[0], 0, 1));
            colorBtn.setClickable(true);
            colorBtn.setFocusable(true);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = t.dp(4);
            btnRow.addView(colorBtn, cp);
        }
        p.addView(btnRow, wp(t.dp(2)));

        // tinyButton from our theme
        label(p, t, "tinyButton() — compact helper from ModernUiTheme");
        TextView tiny = tinyButton(p, "Tiny Button");
        p.addView(tiny, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 2. Button States (enabled/disabled/tooltip) =====================

    private static void buildButtonStatesSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "2. Button States (Enabled / Disabled / Tooltip)");
        LinearLayout p = panel(t);

        label(p, t, "Enabled filled button — clickable with hover feedback");
        Button enabledBtn = new Button(t.getContext(), null);
        enabledBtn.setText("Enabled (click me)");
        enabledBtn.setOnClickListener(v ->
                Toast.makeText(t.getContext(), "Clicked!", Toast.LENGTH_SHORT).show());
        p.addView(enabledBtn, wp(t.dp(2)));

        label(p, t, "Disabled filled button — setEnabled(false)");
        Button disabledFilled = new Button(t.getContext(), null);
        disabledFilled.setText("Disabled Filled");
        disabledFilled.setEnabled(false);
        disabledFilled.setTooltipText("This button is intentionally disabled");
        p.addView(disabledFilled, wp(t.dp(2)));

        label(p, t, "Disabled outlined button — setEnabled(false)");
        Button disabledOutlined = new Button(t.getContext(), null, R.attr.buttonOutlinedStyle);
        disabledOutlined.setText("Disabled Outlined");
        disabledOutlined.setEnabled(false);
        disabledOutlined.setTooltipText("Disabled by design");
        p.addView(disabledOutlined, wp(t.dp(2)));

        label(p, t, "Disabled elevated button");
        ToggleButton disabledElevated = new ToggleButton(t.getContext(), null, R.attr.buttonElevatedStyle);
        disabledElevated.setText("Disabled Elevated");
        disabledElevated.setEnabled(false);
        p.addView(disabledElevated, wp(t.dp(2)));

        label(p, t, "Button with tooltip — hover to see");
        Button tooltipBtn = new Button(t.getContext(), null, R.attr.buttonOutlinedStyle);
        tooltipBtn.setText("Hover for Tooltip");
        tooltipBtn.setTooltipText("This is a tooltip! (setTooltipText)");
        p.addView(tooltipBtn, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 3. Text Input =====================

    private static void buildTextInputSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "3. Text Input");
        LinearLayout p = panel(t);

        // Filled style (from TestFragment i=3)
        label(p, t, "EditText(ctx, null, R.attr.editTextFilledStyle) — Material3 filled");
        EditText filled = new EditText(t.getContext(), null, R.attr.editTextFilledStyle);
        filled.setHint("Your Name");
        filled.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        p.addView(filled, wp(t.dp(2)));

        // Title + value inline (from TestFragment i=10)
        label(p, t, "Inline label + EditText (from TestFragment)");
        LinearLayout inlineRow = hRow(t);
        TextView titleTv = new TextView(t.getContext());
        titleTv.setText("Title");
        titleTv.setTextSize(11);
        titleTv.setTextColor(UiThemeTokens.TEXT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        inlineRow.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        EditText inlineInput = new EditText(t.getContext());
        inlineInput.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        inlineInput.setTextSize(11);
        inlineInput.setTextColor(UiThemeTokens.CYAN);
        inlineInput.setPadding(t.dp(3), 0, t.dp(3), 0);
        inlineInput.setText("Value");
        inlineRow.addView(inlineInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        p.addView(inlineRow, wp(t.dp(2)));

        // Simple custom-styled (our theme)
        label(p, t, "Custom styled EditText (dark bg + border)");
        EditText custom = new EditText(t.getContext());
        custom.setHint("Enter text here...");
        custom.setTextColor(UiThemeTokens.TEXT);
        custom.setHintTextColor(UiThemeTokens.TEXT_MUTED);
        custom.setTextSize(10);
        custom.setSingleLine(true);
        custom.setPadding(t.dp(6), t.dp(4), t.dp(6), t.dp(4));
        custom.setBackground(makeBackground(custom, UiThemeTokens.SECTION_BG, UiThemeTokens.DIVIDER, 6, 1));
        p.addView(custom, wp(t.dp(2)));

        // Multi-line
        label(p, t, "EditText — multi-line (3-5 lines)");
        EditText multiLine = new EditText(t.getContext());
        multiLine.setHint("Multi-line input...");
        multiLine.setTextColor(UiThemeTokens.TEXT);
        multiLine.setHintTextColor(UiThemeTokens.TEXT_MUTED);
        multiLine.setTextSize(10);
        multiLine.setMinLines(3);
        multiLine.setMaxLines(5);
        multiLine.setPadding(t.dp(6), t.dp(4), t.dp(6), t.dp(4));
        multiLine.setBackground(makeBackground(multiLine, UiThemeTokens.SECTION_BG, UiThemeTokens.DIVIDER, 6, 1));
        LinearLayout.LayoutParams mlP = wp(t.dp(2));
        mlP.height = t.dp(60);
        p.addView(multiLine, mlP);

        addSection(root, p, gap);
    }

    // ===================== 4. Checkboxes =====================

    private static void buildCheckboxSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "4. Checkboxes");
        LinearLayout p = panel(t);

        // Tri-state checkbox (from TestFragment i=6)
        label(p, t, "CheckBox — tri-state: STATE_INDETERMINATE");
        CheckBox triState = new CheckBox(t.getContext());
        triState.setCheckedState(CheckBox.STATE_INDETERMINATE);
        triState.setText("Indeterminate (tri-state)");
        triState.setTextColor(UiThemeTokens.TEXT);
        triState.setTooltipText("Hello, this is a tooltip.");
        p.addView(triState, wp(t.dp(2)));

        // Regular checkboxes
        String[] labels = {"Enable notifications", "Auto-refresh data", "Show debug overlay"};
        boolean[] defaults = {true, false, true};
        for (int i = 0; i < labels.length; i++) {
            CheckBox cb = new CheckBox(t.getContext());
            cb.setChecked(defaults[i]);
            cb.setText(labels[i]);
            cb.setTextColor(UiThemeTokens.TEXT);
            p.addView(cb, wp(t.dp(2)));
        }

        // Disabled checkbox
        label(p, t, "Disabled checkbox");
        CheckBox disabledCb = new CheckBox(t.getContext());
        disabledCb.setChecked(true);
        disabledCb.setText("Locked option");
        disabledCb.setTextColor(UiThemeTokens.TEXT_MUTED);
        disabledCb.setEnabled(false);
        p.addView(disabledCb, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 5. Radio Buttons =====================

    private static void buildRadioSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "5. Radio Buttons (from TestFragment)");
        LinearLayout p = panel(t);

        // Nested RadioGroup pattern (from TestFragment i=5)
        label(p, t, "Nested RadioGroup — 3x3 grid + extra (from TestFragment)");
        RadioGroup outerGroup = new RadioGroup(t.getContext());
        String[] options = {"English", "Chinese", "Spanish", "Hindi", "Arabic", "French", "Bengali", "Portuguese", "Russian"};
        int idx = 0;
        for (int k = 0; k < 3; k++) {
            RadioGroup innerGroup = new RadioGroup(t.getContext());
            innerGroup.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 3 && idx < options.length; j++, idx++) {
                RadioButton rb = new RadioButton(t.getContext());
                rb.setText(options[idx]);
                rb.setTextColor(UiThemeTokens.TEXT);
                rb.setId(1 + idx);
                innerGroup.addView(rb);
            }
            outerGroup.addView(innerGroup);
            if (k == 0) {
                RadioButton extra = new RadioButton(t.getContext());
                extra.setText("Esperanto");
                extra.setTextColor(UiThemeTokens.AMBER);
                extra.setId(99);
                outerGroup.addView(extra);
            }
        }
        outerGroup.setOnCheckedChangeListener((__, checkedId) ->
                Toast.makeText(t.getContext(), "Checked ID: " + checkedId, Toast.LENGTH_SHORT).show());
        p.addView(outerGroup, wp(t.dp(2)));

        // Simple vertical group
        label(p, t, "Simple vertical RadioGroup");
        RadioGroup simple = new RadioGroup(t.getContext());
        simple.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(t.getContext());
            rb.setText("Option " + (char)('A' + i));
            rb.setTextColor(UiThemeTokens.TEXT);
            rb.setId(200 + i);
            simple.addView(rb, wp(t.dp(1)));
        }
        simple.check(200);
        p.addView(simple, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 6. Switches =====================

    private static void buildSwitchSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "6. Switches (from TestFragment)");
        LinearLayout p = panel(t);

        // Switch with label toggle (from TestFragment i=0)
        label(p, t, "Switch — toggles a label's visibility");
        TextView toggleTarget = createText(p, "This text is controlled by the switch!", UiThemeTokens.EMERALD, 10, true);
        Switch sw = new Switch(t.getContext());
        sw.setText("Show text block");
        sw.setTextColor(UiThemeTokens.TEXT);
        sw.setPadding(t.dp(12), 0, t.dp(12), 0);
        sw.setChecked(true);
        sw.setOnCheckedChangeListener((button, checked) -> toggleTarget.setVisibility(checked ? View.VISIBLE : View.GONE));
        LinearLayout.LayoutParams swP = wp(t.dp(2));
        swP.width = t.dp(200);
        swP.height = t.dp(28);
        p.addView(sw, swP);
        p.addView(toggleTarget, wp(t.dp(2)));

        // SwitchButton
        label(p, t, "SwitchButton — compact switch variant");
        LinearLayout sbRow = hRow(t);
        SwitchButton sb = new SwitchButton(t.getContext());
        sb.setChecked(false);
        sbRow.addView(sb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sbLabel = createText(sbRow, "  SwitchButton off", UiThemeTokens.TEXT, 10, true);
        sbRow.addView(sbLabel);
        sb.setOnCheckedChangeListener((btn, checked) -> sbLabel.setText(checked ? "  SwitchButton on" : "  SwitchButton off"));
        p.addView(sbRow, wp(t.dp(4)));

        // ToggleButton
        label(p, t, "ToggleButton — text changes on toggle");
        ToggleButton toggle = new ToggleButton(t.getContext());
        toggle.setTextSize(10);
        toggle.setPadding(t.dp(12), t.dp(4), t.dp(12), t.dp(4));
        p.addView(toggle, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 7. Seek Bars =====================

    private static void buildSeekBarSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "7. Seek Bars");
        LinearLayout p = panel(t);

        // Discrete slider Material3 style (from TestFragment i=11)
        label(p, t, "SeekBar(ctx, null, null, R.style.Widget_Material3_SeekBar_Discrete_Slider)");
        SeekBar discrete = new SeekBar(t.getContext(), null, null,
                R.style.Widget_Material3_SeekBar_Discrete_Slider);
        discrete.setMax(10);
        discrete.setProgress(5);
        discrete.setUserAnimationEnabled(true);
        LinearLayout.LayoutParams discP = wp(t.dp(2));
        discP.width = t.dp(200);
        p.addView(discrete, discP);

        // Regular SeekBars with live value readout
        label(p, t, "Standard SeekBar with live value display");
        LinearLayout seekRow = hRow(t);
        SeekBar seek = new SeekBar(t.getContext());
        seek.setMax(100);
        seek.setProgress(50);
        LinearLayout.LayoutParams seekP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        seekP.height = t.dp(20);
        seekRow.addView(seek, seekP);
        TextView seekVal = createText(seekRow, "50", UiThemeTokens.CYAN, 10, true);
        seekVal.setPadding(t.dp(6), 0, 0, 0);
        seekRow.addView(seekVal, new LinearLayout.LayoutParams(
                t.dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                seekVal.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        p.addView(seekRow, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 8. Progress Bars =====================

    private static void buildProgressBarSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "8. Progress Bars (from TestFragment)");
        LinearLayout p = panel(t);

        // Horizontal indeterminate (from TestFragment i=12)
        label(p, t, "ProgressBar(ctx, null, R.attr.progressBarStyleHorizontal) — indeterminate");
        ProgressBar hInd = new ProgressBar(t.getContext(), null, R.attr.progressBarStyleHorizontal);
        hInd.setIndeterminate(true);
        p.addView(hInd, wp(t.dp(2)));

        // Circular indeterminate (from TestFragment i=13)
        label(p, t, "ProgressBar(ctx) — circular indeterminate");
        ProgressBar cInd = new ProgressBar(t.getContext());
        cInd.setIndeterminate(true);
        LinearLayout.LayoutParams cP = wp(t.dp(2));
        cP.width = t.dp(32);
        cP.height = t.dp(32);
        p.addView(cInd, cP);

        // Determinate with button control
        label(p, t, "Determinate — click button to toggle indeterminate");
        ProgressBar det = new ProgressBar(t.getContext(), null, R.attr.progressBarStyleHorizontal);
        det.setMax(100);
        det.setProgress(60);
        det.setIndeterminate(false);
        p.addView(det, wp(t.dp(2)));

        Button toggleBtn = new Button(t.getContext(), null, R.attr.buttonOutlinedStyle);
        toggleBtn.setText("Toggle Indeterminate");
        toggleBtn.setOnClickListener(v -> {
            boolean next = !det.isIndeterminate();
            det.setIndeterminate(next);
            if (!next) {
                det.setProgress(60);
            }
        });
        p.addView(toggleBtn, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 9. Spinner / Dropdown =====================

    private static void buildSpinnerSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "9. Spinner / Dropdown (from TestFragment)");
        LinearLayout p = panel(t);

        // From TestFragment i=7 — uses FontFamily.getSystemFontMap, we use sample strings
        label(p, t, "Spinner with ArrayAdapter");
        List<String> items = Arrays.asList(
                "Iron Ingot", "Gold Ingot", "Diamond", "Emerald",
                "Netherite Scrap", "Redstone", "Lapis Lazuli", "Copper Ingot");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(t.getContext(), items);
        Spinner spinner = new Spinner(t.getContext());
        spinner.setAdapter(adapter);
        spinner.setMinimumWidth(t.dp(200));
        p.addView(spinner, wp(t.dp(2)));

        // Second spinner
        label(p, t, "Another Spinner (auto-sorted)");
        List<String> fonts = Arrays.asList(
                "Sans Serif", "Serif", "Monospace", "Cursive", "Fantasy");
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(t.getContext(), fonts);
        fontAdapter.sort(null);
        Spinner fontSpinner = new Spinner(t.getContext());
        fontSpinner.setAdapter(fontAdapter);
        fontSpinner.setMinimumWidth(t.dp(200));
        p.addView(fontSpinner, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 10. Context Menu + SubMenu =====================

    private static void buildContextMenuSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "10. ContextMenu + SubMenu (from TestFragment i=8)");
        LinearLayout p = panel(t);

        label(p, t, "Right-click the box below to open a ContextMenu with SubMenu");

        LinearLayout ctxBox = new LinearLayout(t.getContext());
        ctxBox.setOrientation(LinearLayout.HORIZONTAL);
        ctxBox.setGravity(Gravity.CENTER);
        ctxBox.setPadding(t.dp(16), t.dp(12), t.dp(16), t.dp(12));
        ctxBox.setBackground(statefulBackground(ctxBox, UiThemeTokens.SECTION_BG, UiThemeTokens.CYAN, 0, 1));
        ctxBox.setClickable(true);
        ctxBox.setFocusable(true);
        ctxBox.setLongClickable(true);

        addText(ctxBox, "Right-click here  →  ContextMenu", UiThemeTokens.CYAN, 10, 0,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        // setOnCreateContextMenuListener — exact pattern from TestFragment
        ctxBox.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
            menu.setQwertyMode(true);
            menu.setGroupDividerEnabled(true);

            MenuItem item;
            item = menu.add(2, Menu.NONE, Menu.NONE, "Align start");
            item.setAlphabeticShortcut('s');
            item.setChecked(true);
            item = menu.add(2, Menu.NONE, Menu.NONE, "Align center");
            item.setAlphabeticShortcut('d');
            item = menu.add(2, Menu.NONE, Menu.NONE, "Align end");
            item.setAlphabeticShortcut('f');
            menu.setGroupCheckable(2, true, true);

            SubMenu subMenu = menu.addSubMenu("New");
            subMenu.add("Document");
            subMenu.add("Image");
            subMenu.add("Spreadsheet");

            menu.add(1, Menu.NONE, Menu.NONE, "Delete");

            // Per-item click handlers
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setOnMenuItemClickListener(mi -> {
                    Toast.makeText(t.getContext(), "Selected: " + mi.getTitle(), Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        });

        p.addView(ctxBox, wp(t.dp(4)));

        label(p, t, "Also supports long-press to trigger the same menu");

        addSection(root, p, gap);
    }

    // ===================== 11. ShapeDrawable =====================

    private static void buildShapeDrawableSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "11. ShapeDrawable (from TestFragment)");
        LinearLayout p = panel(t);

        // Pill shape (cornerRadius=1000)
        label(p, t, "ShapeDrawable — pill (cornerRadius=1000)");
        View pill = new View(t.getContext());
        ShapeDrawable pillBg = new ShapeDrawable();
        pillBg.setCornerRadius(1000);
        pillBg.setColor(UiThemeTokens.TAB_ACTIVE);
        pill.setBackground(pillBg);
        LinearLayout.LayoutParams pillP = wp(t.dp(2));
        pillP.width = t.dp(120);
        pillP.height = t.dp(28);
        p.addView(pill, pillP);

        // Rounded rectangle
        label(p, t, "ShapeDrawable — rounded rect (r=8dp)");
        View roundRect = new View(t.getContext());
        ShapeDrawable rrBg = new ShapeDrawable();
        rrBg.setCornerRadius(t.dp(8));
        rrBg.setColor(0xFF1E3A5F);
        roundRect.setBackground(rrBg);
        roundRect.setElevation(t.dp(4));
        LinearLayout.LayoutParams rrP = wp(t.dp(2));
        rrP.height = t.dp(32);
        p.addView(roundRect, rrP);

        // Horizontal line divider (from TestFragment divider setup)
        label(p, t, "ShapeDrawable.HLINE — divider (1dp height)");
        View dividerBox = new View(t.getContext());
        ShapeDrawable hline = new ShapeDrawable();
        hline.setShape(ShapeDrawable.HLINE);
        hline.setSize(-1, t.dp(1));
        hline.setColor(UiThemeTokens.DIVIDER);
        dividerBox.setBackground(hline);
        LinearLayout.LayoutParams divP = wp(t.dp(4));
        divP.height = t.dp(1);
        p.addView(dividerBox, divP);

        // LinearLayout with dividers
        label(p, t, "LinearLayout with setDividerDrawable + SHOW_DIVIDER_MIDDLE");
        LinearLayout divLayout = new LinearLayout(t.getContext());
        divLayout.setOrientation(LinearLayout.VERTICAL);
        ShapeDrawable dividerDrawable = new ShapeDrawable();
        dividerDrawable.setShape(ShapeDrawable.HLINE);
        dividerDrawable.setSize(-1, t.dp(1));
        dividerDrawable.setColor(UiThemeTokens.CYAN);
        divLayout.setDividerDrawable(dividerDrawable);
        divLayout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        divLayout.setDividerPadding(t.dp(8));
        for (String txt : new String[]{"Item A", "Item B", "Item C"}) {
            TextView item = createText(divLayout, txt, UiThemeTokens.TEXT, 10, true);
            item.setPadding(t.dp(8), t.dp(4), t.dp(8), t.dp(4));
            divLayout.addView(item);
        }
        divLayout.setBackground(makeBackground(divLayout, UiThemeTokens.SECTION_BG, UiThemeTokens.DIVIDER, 6, 1));
        p.addView(divLayout, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 12. GradientDrawable =====================

    private static void buildGradientDrawableSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "12. GradientDrawable");
        LinearLayout p = panel(t);
        int boxH = t.dp(28);

        label(p, t, "Solid + rounded corners (r=6dp)");
        View solid = new View(t.getContext());
        GradientDrawable gSolid = new GradientDrawable();
        gSolid.setColor(UiThemeTokens.TAB_ACTIVE);
        gSolid.setCornerRadius(t.dp(6));
        solid.setBackground(gSolid);
        LinearLayout.LayoutParams sP = wp(t.dp(2));
        sP.height = boxH;
        p.addView(solid, sP);

        label(p, t, "Linear gradient (cyan → rose)");
        View grad = new View(t.getContext());
        GradientDrawable gGrad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{UiThemeTokens.CYAN, UiThemeTokens.ROSE});
        gGrad.setCornerRadius(t.dp(4));
        grad.setBackground(gGrad);
        LinearLayout.LayoutParams gP = wp(t.dp(2));
        gP.height = boxH;
        p.addView(grad, gP);

        label(p, t, "3-color gradient (emerald → amber → rose)");
        View tri = new View(t.getContext());
        GradientDrawable gTri = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{UiThemeTokens.EMERALD, UiThemeTokens.AMBER, UiThemeTokens.ROSE});
        gTri.setCornerRadius(t.dp(4));
        tri.setBackground(gTri);
        LinearLayout.LayoutParams tP = wp(t.dp(2));
        tP.height = boxH;
        p.addView(tri, tP);

        label(p, t, "Vertical gradient (top-down: blue → transparent)");
        View vGrad = new View(t.getContext());
        GradientDrawable gVert = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{UiThemeTokens.BLUE, 0x00000000});
        gVert.setCornerRadius(t.dp(4));
        vGrad.setBackground(gVert);
        LinearLayout.LayoutParams vP = wp(t.dp(2));
        vP.height = boxH;
        p.addView(vGrad, vP);

        label(p, t, "Border only (stroke + transparent)");
        View border = new View(t.getContext());
        GradientDrawable gBorder = new GradientDrawable();
        gBorder.setColor(0x00000000);
        gBorder.setStroke(t.dp(2), UiThemeTokens.CYAN);
        gBorder.setCornerRadius(t.dp(6));
        border.setBackground(gBorder);
        LinearLayout.LayoutParams bP = wp(t.dp(2));
        bP.height = boxH;
        p.addView(border, bP);

        label(p, t, "Pill shape (large radius) + gradient fill");
        View pillGrad = new View(t.getContext());
        GradientDrawable gPill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{UiThemeTokens.CYAN, UiThemeTokens.EMERALD});
        gPill.setCornerRadius(1000);
        pillGrad.setBackground(gPill);
        LinearLayout.LayoutParams pgP = wp(t.dp(2));
        pgP.height = t.dp(24);
        pgP.width = t.dp(160);
        p.addView(pillGrad, pgP);

        addSection(root, p, gap);
    }

    // ===================== 13. StateListDrawable =====================

    private static void buildStateListDrawableSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "13. StateListDrawable + ColorStateList");
        LinearLayout p = panel(t);

        label(p, t, "Hover/press these boxes to see state changes");
        String[] names = {"buttonBackgroundStateful", "cardBackgroundStateful",
                "rowBackgroundStateful (normal)", "rowBackgroundStateful (critical)",
                "tabBackgroundStateful (active)", "tabBackgroundStateful (inactive)"};

        for (int i = 0; i < names.length; i++) {
            LinearLayout box = new LinearLayout(t.getContext());
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(t.dp(10), t.dp(6), t.dp(10), t.dp(6));
            box.setClickable(true);
            box.setFocusable(true);

            StateListDrawable bg = switch (i) {
                case 0 -> buttonBackgroundStateful(box);
                case 1 -> cardBackgroundStateful(box);
                case 2 -> rowBackgroundStateful(box, false);
                case 3 -> rowBackgroundStateful(box, true);
                case 4 -> tabBackgroundStateful(box, true);
                default -> tabBackgroundStateful(box, false);
            };
            box.setBackground(bg);

            addText(box, names[i], UiThemeTokens.TEXT, 9, 0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);
            p.addView(box, wp(t.dp(3)));
        }

        // ColorStateList demo
        label(p, t, "TextView with ColorStateList — text color changes on press/hover");
        TextView cslTv = new TextView(t.getContext());
        cslTv.setText("Press me — color changes!");
        cslTv.setTextSize(11);
        cslTv.setPadding(t.dp(10), t.dp(6), t.dp(10), t.dp(6));
        cslTv.setClickable(true);
        cslTv.setFocusable(true);
        cslTv.setBackground(buttonBackgroundStateful(cslTv));
        ColorStateList textCsl = new ColorStateList(
                new int[][]{
                        new int[]{R.attr.state_pressed},
                        new int[]{R.attr.state_hovered},
                        StateSet.WILD_CARD
                },
                new int[]{UiThemeTokens.ROSE, UiThemeTokens.AMBER, UiThemeTokens.CYAN}
        );
        cslTv.setTextColor(textCsl);
        p.addView(cslTv, wp(t.dp(4)));

        addSection(root, p, gap);
    }

    // ===================== 14. Rich Text / Spannable =====================

    private static void buildRichTextSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "14. Rich Text / Spannable (from TestFragment)");
        LinearLayout p = panel(t);

        label(p, t, "SpannableString with multiple spans");

        String text = "Bold text, colored, larger, underlined, and strikethrough in one TextView.";
        Spannable span = new SpannableString(text);
        // "Bold text" → bold
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // "colored" → foreground color
        span.setSpan(new ForegroundColorSpan(UiThemeTokens.CYAN), 11, 18, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // "larger" → relative size
        span.setSpan(new RelativeSizeSpan(1.3f), 20, 26, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // "underlined" → underline
        span.setSpan(new UnderlineSpan(), 28, 38, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // "strikethrough" → strikethrough
        span.setSpan(new StrikethroughSpan(), 44, 57, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView richTv = new TextView(t.getContext());
        richTv.setText(span, TextView.BufferType.SPANNABLE);
        richTv.setTextSize(11);
        richTv.setTextColor(UiThemeTokens.TEXT);
        richTv.setTextIsSelectable(true);
        p.addView(richTv, wp(t.dp(4)));

        // Multi-color line
        label(p, t, "Multi-span colored line");
        String line = "RED GREEN BLUE AMBER";
        Spannable colorLine = new SpannableString(line);
        colorLine.setSpan(new ForegroundColorSpan(UiThemeTokens.ROSE), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        colorLine.setSpan(new ForegroundColorSpan(UiThemeTokens.EMERALD), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        colorLine.setSpan(new ForegroundColorSpan(UiThemeTokens.BLUE), 10, 14, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        colorLine.setSpan(new ForegroundColorSpan(UiThemeTokens.AMBER), 15, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        colorLine.setSpan(new StyleSpan(Typeface.BOLD), 0, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        colorLine.setSpan(new RelativeSizeSpan(1.4f), 0, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView colorTv = new TextView(t.getContext());
        colorTv.setText(colorLine, TextView.BufferType.SPANNABLE);
        colorTv.setTextSize(11);
        colorTv.setTextColor(UiThemeTokens.TEXT);
        p.addView(colorTv, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 15. Animation =====================

    private static void buildAnimationSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "15. ObjectAnimator (from TestFragment DView)");
        LinearLayout p = panel(t);

        label(p, t, "Click the box to play rotation + scale + fade animation");

        // Animated box (inspired by TestFragment DView)
        View animBox = new View(t.getContext());
        ShapeDrawable animBg = new ShapeDrawable();
        animBg.setCornerRadius(t.dp(8));
        animBg.setColor(0xFF1E3A5F);
        animBox.setBackground(animBg);
        animBox.setElevation(t.dp(6));
        LinearLayout.LayoutParams abP = wp(t.dp(4));
        abP.width = t.dp(80);
        abP.height = t.dp(80);
        abP.gravity = Gravity.CENTER_HORIZONTAL;
        p.addView(animBox, abP);

        // Animation on click
        PropertyValuesHolder pvhR = PropertyValuesHolder.ofFloat(View.ROTATION, 0, 360);
        PropertyValuesHolder pvhSx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1, 0.3f);
        PropertyValuesHolder pvhSy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1, 0.3f);
        PropertyValuesHolder pvhA = PropertyValuesHolder.ofFloat(View.ALPHA, 1, 0.2f);
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(animBox, pvhR, pvhSx, pvhSy, pvhA);
        anim.setDuration(1500);
        anim.setRepeatCount(1);
        anim.setRepeatMode(ObjectAnimator.REVERSE);
        anim.setInterpolator(TimeInterpolator.ACCELERATE_DECELERATE);

        animBox.setClickable(true);
        animBox.setFocusable(true);
        animBox.setOnClickListener(v -> {
            if (!anim.isRunning()) anim.start();
        });

        // Overshoot animation demo
        label(p, t, "Click for OVERSHOOT translation animation");
        View overshootBox = new View(t.getContext());
        ShapeDrawable ovBg = new ShapeDrawable();
        ovBg.setCornerRadius(1000);
        ovBg.setColor(UiThemeTokens.CYAN);
        overshootBox.setBackground(ovBg);
        LinearLayout.LayoutParams ovP = wp(t.dp(4));
        ovP.width = t.dp(40);
        ovP.height = t.dp(40);
        p.addView(overshootBox, ovP);

        ObjectAnimator ovAnim = ObjectAnimator.ofFloat(overshootBox, View.TRANSLATION_X, 0, t.dp(100));
        ovAnim.setDuration(600);
        ovAnim.setRepeatCount(1);
        ovAnim.setRepeatMode(ObjectAnimator.REVERSE);
        ovAnim.setInterpolator(TimeInterpolator.OVERSHOOT);

        overshootBox.setClickable(true);
        overshootBox.setFocusable(true);
        overshootBox.setOnClickListener(v -> {
            if (!ovAnim.isRunning()) ovAnim.start();
        });

        addSection(root, p, gap);
    }

    // ===================== 16. Toast =====================

    private static void buildToastSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "16. Toast");
        LinearLayout p = panel(t);

        label(p, t, "Click buttons to show Toast messages");

        LinearLayout row = hRow(t);
        Button shortToast = new Button(t.getContext(), null, R.attr.buttonOutlinedStyle);
        shortToast.setText("Short Toast");
        shortToast.setOnClickListener(v ->
                Toast.makeText(t.getContext(), "Short Toast!", Toast.LENGTH_SHORT).show());
        row.addView(shortToast, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View sp = new View(t.getContext());
        row.addView(sp, new LinearLayout.LayoutParams(t.dp(8), 0));

        Button longToast = new Button(t.getContext(), null, R.attr.buttonOutlinedStyle);
        longToast.setText("Long Toast");
        longToast.setOnClickListener(v ->
                Toast.makeText(t.getContext(), "This is a longer Toast message!", Toast.LENGTH_LONG).show());
        row.addView(longToast, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        p.addView(row, wp(t.dp(2)));

        addSection(root, p, gap);
    }

    // ===================== 17. PopupMenu =====================

    private static void buildPopupMenuSection(ResourceTerminalFragment t, LinearLayout root, int gap) {
        sectionTitle(root, t, "17. PopupMenu");
        LinearLayout p = panel(t);

        label(p, t, "Click button to show PopupMenu with items");

        Button popupBtn = new Button(t.getContext(), null, R.attr.buttonOutlinedStyle);
        popupBtn.setText("Show PopupMenu");
        popupBtn.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(t.getContext(), v);
            Menu menu = popup.getMenu();
            menu.add(0, 0, 0, "Iron Ingot — 64 stacks");
            menu.add(0, 1, 1, "Gold Ingot — 32 stacks");
            menu.add(0, 2, 2, "Diamond — 16 stacks");
            menu.add(0, 3, 3, "Netherite — 4 stacks");
            popup.setOnMenuItemClickListener(item -> {
                Toast.makeText(t.getContext(), "Selected: " + item.getTitle(), Toast.LENGTH_SHORT).show();
                return true;
            });
            popup.show();
        });
        p.addView(popupBtn, wp(t.dp(2)));

        addSection(root, p, gap);
    }
}
