# Modern UI (icyllis.modernui) 3.12.0 — API 权威参考手册

> **适用版本**: Modern UI 3.12.0 · NeoForge 1.21.1 · Minecraft Mod 开发
> **用途**: 供 AI Agent 构建 Minecraft Mod UI 时作为唯一可信 API 参考
> **所有 API 签名均已通过 javap 验证**，标记 ✅ 表示已确认可用，⚠️ 表示有已知问题

---

## Part 1: 框架概述

### 1.1 Maven 依赖配置

```groovy
// build.gradle (NeoForge 1.21.1)
repositories {
    maven { url 'https://maven.icyllis.dev/releases' }
}
dependencies {
    implementation 'icyllis.modernui:ModernUI-Core:3.12.0'
    implementation 'icyllis.modernui:ModernUI-Markdown:3.12.0' // 可选
}
```

### 1.2 架构说明

Modern UI **遵循 Android View 系统**的设计思想，**不是** JavaFX，**不是** Swing。
- 所有控件在 `icyllis.modernui.widget.*` 包下
- 视图系统在 `icyllis.modernui.view.*` 包下
- 绘图系统在 `icyllis.modernui.graphics.drawable.*` 包下
- 动画系统在 `icyllis.modernui.animation.*` 包下
- 文本系统在 `icyllis.modernui.text.*` 包下
- 工具类在 `icyllis.modernui.util.*` 包下

### 1.3 关键差异：Modern UI vs Android

| 概念 | Modern UI | Android |
|------|-----------|---------|
| R 类 | `icyllis.modernui.R` | `android.R` |
| Context | `icyllis.modernui.core.Context` | `android.content.Context` |
| 保存状态 | `DataSet` | `Bundle` |
| ColorStateList | `icyllis.modernui.util.ColorStateList` | `android.content.res.ColorStateList` |
| StateSet | `icyllis.modernui.util.StateSet` | `android.util.StateSet` |
| 布局方式 | **纯代码**（无 XML） | XML + 代码 |
| dp 转换 | `view.dp(value)` 直接调用 | `TypedValue.applyDimension(...)` |
| 右键菜单 | `setOnContextClickListener` **不可用** ⚠️ | 可用 |

### 1.4 主题解析模式 ✅

```java
import icyllis.modernui.R;
import icyllis.modernui.resources.TypedValue;

TypedValue value = new TypedValue();
// R.ns 是命名空间常量（int），必须作为第一个参数
context.getTheme().resolveAttribute(R.ns, R.attr.colorPrimary, value, true);
int colorPrimary = value.data;
```

### 1.5 布局系统

全部通过代码构建，使用 `LinearLayout`、`FrameLayout`、`ScrollView`。

```java
import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

// MATCH_PARENT = -1, WRAP_CONTENT = -2
LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
lp.topMargin = view.dp(8);

// 加权布局：宽度设为 0，指定 weight
LinearLayout.LayoutParams weighted = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
```

---

## Part 2: 基础控件 (Widgets)

### 2.1 TextView ✅

**类路径**: `icyllis.modernui.widget.TextView`

**构造方法**:
- `new TextView(Context ctx)` ✅

**关键方法**:
- `setText(CharSequence text)` ✅
- `setTextColor(int color)` / `setTextColor(ColorStateList colors)` ✅
- `setTextSize(float sp)` ✅
- `setTextAlignment(int alignment)` ✅ — `View.TEXT_ALIGNMENT_GRAVITY`, `TEXT_ALIGNMENT_VIEW_START`, `TEXT_ALIGNMENT_VIEW_END`
- `setGravity(int gravity)` ✅
- `setTextIsSelectable(boolean selectable)` ✅
- `setText(CharSequence, TextView.BufferType)` ✅ — `BufferType.SPANNABLE` 用于富文本
- `setSingleLine()` ✅
- `setEllipsize(TextUtils.TruncateAt.END)` ✅
- `setLinksClickable(boolean clickable)` ✅
- `setIncludeFontPadding(boolean include)` ✅
- `setLineBreakWordStyle(int style)` ✅

**TestFragment 使用** (行 383-426):
```java
import icyllis.modernui.widget.TextView;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;

TextView tv = new TextView(getContext());
tv.setLayoutParams(new LayoutParams(tv.dp(640), WRAP_CONTENT));
tv.setLinksClickable(true);
tv.setTextIsSelectable(true);
tv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

// 富文本方式设置
Spannable spannable = new SpannableString("Hello World");
tv.setText(spannable, TextView.BufferType.SPANNABLE);
```

### 2.2 Button ✅

**类路径**: `icyllis.modernui.widget.Button`

**构造方法（3 种样式）**:
- `new Button(ctx, null)` ✅ — **Filled**（默认 Material3 填充按钮）— TestFragment i=8,9
- `new Button(ctx, null, R.attr.buttonOutlinedStyle)` ✅ — **Outlined**（轮廓按钮）— TestFragment i=1
- 自定义样式按钮：使用 `TextView` + `setBackground(StateListDrawable)` + `setClickable(true)`

**关键方法**:
- `setText(CharSequence text)` ✅
- `setOnClickListener(View.OnClickListener listener)` ✅
- `setEnabled(boolean enabled)` ✅
- `setTooltipText(CharSequence text)` ✅

**完整示例** (DevComponentsPageBuilder 行 134-144):
```java
import icyllis.modernui.R;
import icyllis.modernui.widget.Button;

// Filled 按钮（默认）
Button filled = new Button(context, null);
filled.setText("Filled Button");
filled.setTooltipText("Default filled button style");
filled.setOnClickListener(v ->
    Toast.makeText(context, "Clicked!", Toast.LENGTH_SHORT).show());

// Outlined 按钮
Button outlined = new Button(context, null, R.attr.buttonOutlinedStyle);
outlined.setText("Outlined Button");

// 禁用按钮
Button disabled = new Button(context, null);
disabled.setText("Disabled");
disabled.setEnabled(false);
disabled.setTooltipText("This button is intentionally disabled");
```

### 2.3 ToggleButton ✅

**类路径**: `icyllis.modernui.widget.ToggleButton`

**构造方法**:
- `new ToggleButton(ctx)` ✅
- `new ToggleButton(ctx, null, R.attr.buttonElevatedStyle)` ✅ — **Elevated 样式**，带阴影 — TestFragment i=4

**关键方法**:
- `setText(CharSequence text)` ✅
- `setTextOn(CharSequence text)` ✅
- `setTextOff(CharSequence text)` ✅
- `setEnabled(boolean enabled)` ✅

**带图标示例** (TestFragment 行 688-696):
```java
import icyllis.modernui.widget.ToggleButton;
import icyllis.modernui.graphics.drawable.BuiltinIconDrawable;

ToggleButton elevated = new ToggleButton(context, null, R.attr.buttonElevatedStyle);
// 添加 CompoundDrawable 图标
var icon = new BuiltinIconDrawable(context.getResources(), BuiltinIconDrawable.CHECK, 18);
icon.setTintList(elevated.getTextColors());
elevated.setCompoundDrawablePadding(view.dp(8));
elevated.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
elevated.setText("Elevated button");
```

### 2.4 EditText ✅

**类路径**: `icyllis.modernui.widget.EditText`

**构造方法**:
- `new EditText(ctx)` ✅ — 基础文本输入
- `new EditText(ctx, null, R.attr.editTextFilledStyle)` ✅ — **Filled 样式**（带下划线）— TestFragment i=3

**关键方法**:
- `setHint(CharSequence hint)` ✅
- `setTextAppearance(int resId)` ✅ — 如 `R.attr.textAppearanceLabelLarge`
- `setSingleLine(boolean singleLine)` ✅ / `setSingleLine()` ✅
- `setMinLines(int lines)` ✅
- `setMaxLines(int lines)` ✅
- `setText(CharSequence text)` ✅
- `setTextAlignment(int alignment)` ✅
- `setTextColor(int color)` ✅
- `setHintTextColor(int color)` ✅
- `setId(int id)` ✅ — 如 `R.id.input`

**完整示例** (TestFragment 行 563-599):
```java
import icyllis.modernui.R;
import icyllis.modernui.widget.EditText;

// Material3 Filled 样式
EditText filledField = new EditText(context, null, R.attr.editTextFilledStyle);
filledField.setTextAppearance(R.attr.textAppearanceLabelLarge);
filledField.setHint("Your Name");
filledField.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

// 内联标签 + 输入框
LinearLayout row = new LinearLayout(context);
row.setOrientation(LinearLayout.HORIZONTAL);

TextView label = new TextView(context);
label.setText("Title");
label.setTextSize(14);
row.addView(label, new LayoutParams(0, WRAP_CONTENT, 1));

EditText input = new EditText(context);
input.setId(R.id.input);
input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
input.setTextSize(14);
input.setPadding(dp3, 0, dp3, 0);
input.setText("Value");
row.addView(input, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

// 多行输入
EditText multiLine = new EditText(context);
multiLine.setHint("Multi-line input...");
multiLine.setMinLines(3);
multiLine.setMaxLines(5);
```

### 2.5 CheckBox ✅

**类路径**: `icyllis.modernui.widget.CheckBox`

**构造方法**:
- `new CheckBox(ctx)` ✅ — TestFragment i=6

**关键方法（支持三态）**:
- `setChecked(boolean checked)` ✅
- `setCheckedState(int state)` ✅ — 三态支持
  - `CheckBox.STATE_CHECKED` ✅
  - `CheckBox.STATE_UNCHECKED` ✅
  - `CheckBox.STATE_INDETERMINATE` ✅ — 不确定态
- `setText(CharSequence text)` ✅
- `setOnCheckedStateChangeListener(listener)` ✅

**完整示例** (TestFragment 行 637-643):
```java
import icyllis.modernui.widget.CheckBox;

// 三态 checkbox
CheckBox triState = new CheckBox(context);
triState.setCheckedState(CheckBox.STATE_INDETERMINATE);
triState.setText("Checkbox 0");
triState.setTooltipText("Hello, this is a tooltip.");

// 普通 checkbox
CheckBox normal = new CheckBox(context);
normal.setChecked(true);
normal.setText("Enable notifications");
normal.setTextColor(0xFFE0E0E0);

// 禁用态
CheckBox disabled = new CheckBox(context);
disabled.setChecked(true);
disabled.setText("Locked option");
disabled.setEnabled(false);
```

### 2.6 RadioButton + RadioGroup ✅

**类路径**: `icyllis.modernui.widget.RadioButton`, `icyllis.modernui.widget.RadioGroup`

**构造方法**:
- `new RadioButton(ctx)` ✅ — 标准样式
- `new RadioButton(ctx, null, null, null)` ✅ — 4参数无样式版本（用于导航）
- `new RadioGroup(ctx)` ✅ — 排他选择组

**关键方法**:
- RadioButton: `setText()`, `setId(int)`, `setTextColor(int)` ✅
- RadioGroup: `setOnCheckedChangeListener((group, checkedId) -> {})` ✅
- RadioGroup: `check(int id)` ✅ — 程序化选中
- RadioGroup: `setOrientation(LinearLayout.HORIZONTAL/VERTICAL)` ✅

**嵌套 RadioGroup (3×3 网格)** (TestFragment 行 607-635):
```java
import icyllis.modernui.widget.RadioButton;
import icyllis.modernui.widget.RadioGroup;
import icyllis.modernui.widget.Toast;

RadioGroup outerGroup = new RadioGroup(context);
Iterator<String> options = List.of(
    "English", "Chinese", "Spanish", "Hindi",
    "Arabic", "French", "Bengali", "Portuguese", "Russian"
).iterator();

for (int k = 0; k < 3; k++) {
    RadioGroup innerGroup = new RadioGroup(context);
    innerGroup.setOrientation(LinearLayout.HORIZONTAL);
    for (int j = 0; j < 3; j++) {
        RadioButton button = new RadioButton(context);
        button.setText(options.next());
        button.setId(1 + k * 3 + j);
        innerGroup.addView(button);
    }
    outerGroup.addView(innerGroup);
    if (k == 0) {
        // 额外按钮直接加到外层
        RadioButton extra = new RadioButton(context);
        extra.setText("Esperanto");
        extra.setId(99);
        outerGroup.addView(extra);
    }
}
outerGroup.setOnCheckedChangeListener((__, checkedId) ->
    Toast.makeText(context, "You checked " + checkedId, Toast.LENGTH_SHORT).show());
```

### 2.7 Switch ✅

**类路径**: `icyllis.modernui.widget.Switch`

**构造方法**:
- `new Switch(ctx)` ✅ — TestFragment i=0

**关键方法**:
- `setText(CharSequence text)` ✅
- `setChecked(boolean checked)` ✅
- `setPadding(left, top, right, bottom)` ✅
- `setOnCheckedChangeListener((button, checked) -> {})` ✅

**完整示例** (TestFragment 行 532-558):
```java
import icyllis.modernui.widget.Switch;

Switch sw = new Switch(context);
sw.setText("Show text block");
sw.setPadding(view.dp(12), 0, view.dp(12), 0);
sw.setOnCheckedChangeListener((button, checked) -> {
    if (checked) {
        button.post(() -> layout.addView(targetView, 2));
    } else {
        button.post(() -> layout.removeView(targetView));
    }
});
// 推荐设置尺寸
var swParams = new LayoutParams(view.dp(300), view.dp(40));
```

### 2.8 SwitchButton ✅

**类路径**: `icyllis.modernui.widget.SwitchButton`

**构造方法**:
- `new SwitchButton(ctx)` ✅ — 紧凑型开关

**关键方法**:
- `setChecked(boolean checked)` ✅
- `setOnCheckedChangeListener((btn, checked) -> {})` ✅
- `setCheckedColor(int color)` ✅
- `setUncheckedColor(int color)` ✅
- `setBorderWidth(float width)` ✅

**完整示例** (DevComponentsPageBuilder 行 399-407):
```java
import icyllis.modernui.widget.SwitchButton;

SwitchButton sb = new SwitchButton(context);
sb.setChecked(false);
sb.setOnCheckedChangeListener((btn, checked) -> {
    label.setText(checked ? "ON" : "OFF");
});
```

### 2.9 SeekBar ✅

**类路径**: `icyllis.modernui.widget.SeekBar`

**构造方法**:
- `new SeekBar(ctx)` ✅ — 标准滑块
- `new SeekBar(ctx, null, null, R.style.Widget_Material3_SeekBar_Discrete_Slider)` ✅ — **离散滑块**（带刻度）— TestFragment i=11

**关键方法**:
- `setMax(int max)` ✅
- `setProgress(int progress)` ✅
- `setUserAnimationEnabled(boolean enabled)` ✅
- `setOnSeekBarChangeListener(OnSeekBarChangeListener listener)` ✅

**完整示例** (TestFragment 行 654-660, DevComponentsPageBuilder 行 427-456):
```java
import icyllis.modernui.R;
import icyllis.modernui.widget.SeekBar;

// 离散滑块（带刻度）
SeekBar discrete = new SeekBar(context, null, null,
    R.style.Widget_Material3_SeekBar_Discrete_Slider);
discrete.setMax(10);
discrete.setProgress(5);
discrete.setUserAnimationEnabled(true);

// 标准滑块 + 数值显示
SeekBar seek = new SeekBar(context);
seek.setMax(100);
seek.setProgress(50);
seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
    @Override
    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
        valueLabel.setText(String.valueOf(progress));
    }
    @Override public void onStartTrackingTouch(SeekBar sb) {}
    @Override public void onStopTrackingTouch(SeekBar sb) {}
});
```

### 2.10 ProgressBar ✅

**类路径**: `icyllis.modernui.widget.ProgressBar`

**构造方法**:
- `new ProgressBar(ctx)` ✅ — **圆形**旋转指示器 — TestFragment i=13
- `new ProgressBar(ctx, null, R.attr.progressBarStyleHorizontal)` ✅ — **水平**进度条 — TestFragment i=12

**关键方法**:
- `setIndeterminate(boolean indeterminate)` ✅
- `setMax(int max)` ✅
- `setProgress(int progress)` ✅
- `isIndeterminate()` ✅

**完整示例** (TestFragment 行 661-672):
```java
import icyllis.modernui.R;
import icyllis.modernui.widget.ProgressBar;

// 水平不确定进度条（滚动动画）
ProgressBar hBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
hBar.setIndeterminate(true);

// 圆形不确定进度条
ProgressBar circular = new ProgressBar(context);
circular.setIndeterminate(true);

// 确定进度的水平条
ProgressBar determinate = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
determinate.setMax(100);
determinate.setProgress(60);
determinate.setIndeterminate(false);
```

### 2.11 Spinner ✅

**类路径**: `icyllis.modernui.widget.Spinner`

**构造方法**:
- `new Spinner(ctx)` ✅ — TestFragment i=7

**关键方法**:
- `setAdapter(ArrayAdapter<?> adapter)` ✅
- `setMinimumWidth(int px)` ✅

**完整示例** (TestFragment 行 644-653):
```java
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.ArrayAdapter;

Spinner spinner = new Spinner(context);
ArrayAdapter<?> adapter = new ArrayAdapter<>(context,
    FontFamily.getSystemFontMap().keySet().toArray());
adapter.sort(null);  // 自然排序
spinner.setAdapter(adapter);
spinner.setMinimumWidth(view.dp(240));
```

### 2.12 ArrayAdapter ✅

**类路径**: `icyllis.modernui.widget.ArrayAdapter`

**构造方法**:
- `new ArrayAdapter<>(ctx, List<T> list)` ✅
- `new ArrayAdapter<>(ctx, T[] array)` ✅

**关键方法**:
- `sort(Comparator<? super T> comparator)` ✅ — 传 `null` 使用自然排序
- `add(T item)` ✅
- `remove(T item)` ✅
- `clear()` ✅

**示例**:
```java
import icyllis.modernui.widget.ArrayAdapter;
import java.util.Arrays;
import java.util.List;

List<String> items = Arrays.asList("Iron", "Gold", "Diamond", "Emerald");
ArrayAdapter<String> adapter = new ArrayAdapter<>(context, items);
adapter.sort(null);  // 按字母排序
```

### 2.13 Toast ✅

**类路径**: `icyllis.modernui.widget.Toast`

**静态方法**:
- `Toast.makeText(Context ctx, CharSequence text, int duration)` ✅

**时长常量**:
- `Toast.LENGTH_SHORT` ✅
- `Toast.LENGTH_LONG` ✅

**示例** (TestFragment 行 631):
```java
import icyllis.modernui.widget.Toast;

Toast.makeText(context, "Short message!", Toast.LENGTH_SHORT).show();
Toast.makeText(context, "Longer message stays!", Toast.LENGTH_LONG).show();
```

### 2.14 CheckableImageButton ✅

**类路径**: `icyllis.modernui.widget.CheckableImageButton`

**构造方法**:
- `new CheckableImageButton(ctx, null, R.attr.iconButtonFilledStyle)` ✅ — TestFragment i=14

**关键方法**:
- `setImageDrawable(Drawable drawable)` ✅
- `setTooltipTextOn(CharSequence text)` ✅
- `setTooltipTextOff(CharSequence text)` ✅

**完整示例** (TestFragment 行 674-685):
```java
import icyllis.modernui.R;
import icyllis.modernui.widget.CheckableImageButton;
import icyllis.modernui.graphics.drawable.BuiltinIconDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.util.StateSet;

CheckableImageButton button = new CheckableImageButton(context,
    null, R.attr.iconButtonFilledStyle);

// 根据选中状态切换图标
var icon = new StateListDrawable();
icon.addState(new int[]{R.attr.state_checked},
    new BuiltinIconDrawable(context.getResources(), BuiltinIconDrawable.KEYBOARD_ARROW_UP));
icon.addState(StateSet.WILD_CARD,
    new BuiltinIconDrawable(context.getResources(), BuiltinIconDrawable.KEYBOARD_ARROW_DOWN));
button.setImageDrawable(icon);

// 不同状态的提示文字
button.setTooltipTextOn("Collapse this card");
button.setTooltipTextOff("Expand this card");
```

---

## Part 3: 布局容器 (Layout Containers)

### 3.1 LinearLayout ✅

**类路径**: `icyllis.modernui.widget.LinearLayout`

**方向常量**: `LinearLayout.HORIZONTAL`, `LinearLayout.VERTICAL`

**关键方法**:
- `setOrientation(int orientation)` ✅
- `setGravity(int gravity)` ✅ — `Gravity.CENTER`, `Gravity.CENTER_VERTICAL`, `Gravity.START` 等
- `setDividerDrawable(Drawable divider)` ✅
- `setShowDividers(int showDividers)` ✅ — `SHOW_DIVIDER_MIDDLE`, `SHOW_DIVIDER_END`, `SHOW_DIVIDER_BEGINNING`
- `setDividerPadding(int padding)` ✅
- `setLayoutTransition(LayoutTransition transition)` ✅
- `setPadding(left, top, right, bottom)` ✅

**完整示例** (TestFragment 行 326-348):
```java
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.animation.LayoutTransition;

LinearLayout layout = new LinearLayout(context);
layout.setOrientation(LinearLayout.VERTICAL);
layout.setGravity(Gravity.CENTER);
layout.setPadding(dp(12), dp(12), dp(12), dp(12));

// 分割线
ShapeDrawable divider = new ShapeDrawable();
divider.setShape(ShapeDrawable.HLINE);
divider.setSize(-1, dp(1));
divider.setColor(dividerColor);
layout.setDividerDrawable(divider);
layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE | LinearLayout.SHOW_DIVIDER_END);
layout.setDividerPadding(dp(8));

// 子视图添加/移除动画
layout.setLayoutTransition(new LayoutTransition());
```

### 3.2 FrameLayout ✅

**类路径**: `icyllis.modernui.widget.FrameLayout`

**LayoutParams**:
```java
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.view.Gravity;

FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
    view.dp(960), view.dp(540));
params.gravity = Gravity.CENTER;
view.setLayoutParams(params);
```

### 3.3 ScrollView ✅

**类路径**: `icyllis.modernui.widget.ScrollView`

**用法**: 包装单个子视图使其可滚动。

```java
import icyllis.modernui.widget.ScrollView;

ScrollView scroll = new ScrollView(context);
scroll.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

LinearLayout content = new LinearLayout(context);
content.setOrientation(LinearLayout.VERTICAL);
scroll.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
```

### 3.4 RadioGroup ✅

继承自 `LinearLayout`，提供排他选择行为。详见 2.6 节。

---

## Part 4: Drawable 系统

### 4.1 ShapeDrawable ✅

**类路径**: `icyllis.modernui.graphics.drawable.ShapeDrawable`

**形状常量**:
- `ShapeDrawable.RECTANGLE` ✅ — 默认矩形
- `ShapeDrawable.HLINE` ✅ — 水平线
- `ShapeDrawable.VLINE` ✅ — 垂直线
- `ShapeDrawable.CIRCLE` ✅ — 圆形
- `ShapeDrawable.RING` ✅ — 环形

**关键方法**:
- `setShape(int shape)` ✅
- `setColor(int color)` ✅
- `setCornerRadius(float radius)` ✅ — 设为 `1000` 可得到**胶囊形**
- `setStroke(float width, int color)` ✅
- `setSize(int width, int height)` ✅

**示例** (TestFragment 行 332-338, DevComponentsPageBuilder 行 601-633):
```java
import icyllis.modernui.graphics.drawable.ShapeDrawable;

// 胶囊形背景
ShapeDrawable pill = new ShapeDrawable();
pill.setCornerRadius(1000);  // 大圆角 = 胶囊形
pill.setColor(0xFF1A3B5C);
view.setBackground(pill);

// 圆角矩形
ShapeDrawable roundRect = new ShapeDrawable();
roundRect.setCornerRadius(view.dp(8));
roundRect.setColor(0xFF1E3A5F);
view.setBackground(roundRect);

// 水平分割线（HLINE）
ShapeDrawable hline = new ShapeDrawable();
hline.setShape(ShapeDrawable.HLINE);
hline.setSize(-1, view.dp(1));  // -1 宽度表示自适应
hline.setColor(0xFF2A3A4A);
dividerView.setBackground(hline);

// 带边框的矩形
ShapeDrawable bordered = new ShapeDrawable();
bordered.setColor(fillColor);
bordered.setCornerRadius(view.dp(4));
bordered.setStroke(view.dp(1), borderColor);
view.setBackground(bordered);
```

### 4.2 GradientDrawable ✅

**类路径**: `icyllis.modernui.graphics.drawable.GradientDrawable`
**继承自**: `ShapeDrawable`

**构造方法**:
- `new GradientDrawable()` ✅ — 纯色，可后续设置
- `new GradientDrawable(Orientation orientation, int[] colors)` ✅ — 渐变

**方向常量** (`GradientDrawable.Orientation`):
- `LEFT_RIGHT`, `TOP_BOTTOM`, `RIGHT_LEFT`, `BOTTOM_TOP` 等

**关键方法**:
- `setColor(int color)` ✅ — 纯色
- `setColors(int[] colors)` ✅ — 重设渐变色
- `setOrientation(Orientation orientation)` ✅
- `setCornerRadius(float radius)` ✅
- `setStroke(float width, int color)` ✅

**完整示例** (DevComponentsPageBuilder 行 666-728):
```java
import icyllis.modernui.graphics.drawable.GradientDrawable;

// 双色线性渐变
GradientDrawable grad = new GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    new int[]{0xFF00D4AA, 0xFFFF6B9D});
grad.setCornerRadius(view.dp(4));
view.setBackground(grad);

// 三色渐变
GradientDrawable tri = new GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    new int[]{0xFF10B981, 0xFFF59E0B, 0xFFF43F5E});
tri.setCornerRadius(view.dp(4));

// 仅边框（透明填充）
GradientDrawable border = new GradientDrawable();
border.setColor(0x00000000);
border.setStroke(view.dp(2), 0xFF00D4AA);
border.setCornerRadius(view.dp(6));

// 胶囊形渐变
GradientDrawable pillGrad = new GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    new int[]{0xFF00D4AA, 0xFF10B981});
pillGrad.setCornerRadius(1000);
```

### 4.3 StateListDrawable ✅

**类路径**: `icyllis.modernui.graphics.drawable.StateListDrawable`

**关键方法**:
- `addState(int[] stateSpec, Drawable drawable)` ✅

**状态常量** (来自 `R.attr`):
- `R.attr.state_pressed` ✅ — 按下
- `R.attr.state_hovered` ✅ — 悬停
- `R.attr.state_checked` ✅ — 选中
- `R.attr.state_focused` ✅ — 聚焦
- `R.attr.state_enabled` ✅ — 启用
- `StateSet.WILD_CARD` ✅ — 匹配任意状态（**必须放最后**）

**⚠️ 重要**: 状态匹配是**先匹配先使用**，`WILD_CARD` 必须最后添加。

**完整示例** (ModernUiTheme 行 88-101):
```java
import icyllis.modernui.R;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.util.StateSet;

StateListDrawable bg = new StateListDrawable();

// pressed 状态（最先检查）
ShapeDrawable pressed = new ShapeDrawable();
pressed.setColor(pressedColor);
pressed.setCornerRadius(radius);
bg.addState(new int[]{R.attr.state_pressed}, pressed);

// hovered 状态
ShapeDrawable hovered = new ShapeDrawable();
hovered.setColor(hoveredColor);
hovered.setCornerRadius(radius);
bg.addState(new int[]{R.attr.state_hovered}, hovered);

// 默认状态（WILD_CARD 必须最后）
ShapeDrawable normal = new ShapeDrawable();
normal.setColor(normalColor);
normal.setCornerRadius(radius);
bg.addState(StateSet.WILD_CARD, normal);

view.setBackground(bg);
```

### 4.4 ColorDrawable ✅

**类路径**: `icyllis.modernui.graphics.drawable.ColorDrawable`

```java
import icyllis.modernui.graphics.drawable.ColorDrawable;

view.setBackground(new ColorDrawable(0xFF1A1A2E));
```

### 4.5 RippleDrawable ✅

**类路径**: `icyllis.modernui.graphics.drawable.RippleDrawable`

**构造方法**:
- `new RippleDrawable(ColorStateList rippleColor, Drawable content, Drawable mask)` ✅

```java
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.util.ColorStateList;

RippleDrawable ripple = new RippleDrawable(
    ColorStateList.valueOf(rippleColor),  // 波纹颜色
    contentDrawable,                       // 内容 Drawable
    maskDrawable                           // 遮罩 Drawable（可为 null）
);
```

### 4.6 ImageDrawable ✅

**类路径**: `icyllis.modernui.graphics.drawable.ImageDrawable`

**构造方法**:
- `new ImageDrawable(Resources resources, Image image)` ✅

**关键方法**:
- `setSrcRect(int left, int top, int right, int bottom)` ✅
- `setTintList(ColorStateList tint)` ✅
- `setGravity(int gravity)` ✅

### 4.7 BuiltinIconDrawable ✅

**类路径**: `icyllis.modernui.graphics.drawable.BuiltinIconDrawable`

**构造方法**:
- `new BuiltinIconDrawable(Resources resources, int iconConstant)` ✅
- `new BuiltinIconDrawable(Resources resources, int iconConstant, int sizeSp)` ✅

**图标常量**:
- `BuiltinIconDrawable.CHECK` ✅ — 对勾
- `BuiltinIconDrawable.KEYBOARD_ARROW_UP` ✅ — 上箭头
- `BuiltinIconDrawable.KEYBOARD_ARROW_DOWN` ✅ — 下箭头
- `BuiltinIconDrawable.RADIO_SMALL` ✅ — 小圆点

**示例** (TestFragment 行 690-691, 1006-1007):
```java
import icyllis.modernui.graphics.drawable.BuiltinIconDrawable;

var icon = new BuiltinIconDrawable(context.getResources(), BuiltinIconDrawable.CHECK, 18);
icon.setTintList(button.getTextColors());

var radioIcon = new BuiltinIconDrawable(context.getResources(), BuiltinIconDrawable.RADIO_SMALL);
view.setForeground(radioIcon);
```

---

## Part 5: ColorStateList 系统

### 5.1 基本用法 ✅

**类路径**: `icyllis.modernui.util.ColorStateList`

**构造方法**:
- `new ColorStateList(int[][] stateSpecs, int[] colors)` ✅

**⚠️ 重要**: 匹配顺序=数组顺序，第一个匹配的生效。`StateSet.WILD_CARD` 必须放最后。

```java
import icyllis.modernui.R;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;

ColorStateList textColor = new ColorStateList(
    new int[][]{
        new int[]{R.attr.state_pressed},   // 按下时
        new int[]{R.attr.state_hovered},   // 悬停时
        StateSet.WILD_CARD                  // 默认（必须最后）
    },
    new int[]{
        0xFFF43F5E,   // pressed → 红色
        0xFFF59E0B,   // hovered → 琥珀色
        0xFF00D4AA    // default → 青色
    }
);
textView.setTextColor(textColor);
```

### 5.2 用于导航（CenterFragment2 模式）

```java
// 选中/未选中两态
ColorStateList navColor = new ColorStateList(
    new int[][]{
        new int[]{R.attr.state_checked},   // 选中态
        StateSet.WILD_CARD                  // 默认态
    },
    new int[]{selectedColor, defaultColor}
);
```

### 5.3 工具方法

- `ColorStateList.valueOf(int color)` ✅ — 创建单色 CSL
- `ColorStateList.modulateColor(int baseColor, float alphaFraction)` ✅ — 按比例调整透明度

---

## Part 6: 菜单系统 (Menus)

### 6.1 ContextMenu（浮动右键菜单）✅

**触发方式**: 右键点击 / 长按

**⚠️ 关键前提**: 视图**必须** `setLongClickable(true)` 才能响应右键触发 `showContextMenu`

**完整示例** (TestFragment 行 748-765):
```java
import icyllis.modernui.view.Menu;
import icyllis.modernui.view.MenuItem;
import icyllis.modernui.view.SubMenu;

// 1. 必须设置 longClickable
view.setClickable(true);
view.setFocusable(true);
view.setLongClickable(true);  // ⚠️ 必须！否则右键无反应

// 2. 设置菜单创建监听器
view.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
    menu.setQwertyMode(true);
    menu.setGroupDividerEnabled(true);

    // 添加带快捷键的菜单项（groupId=2 用于单选组）
    MenuItem item;
    item = menu.add(2, Menu.NONE, Menu.NONE, "Align start");
    item.setAlphabeticShortcut('s');
    item.setChecked(true);
    item = menu.add(2, Menu.NONE, Menu.NONE, "Align center");
    item.setAlphabeticShortcut('d');
    item = menu.add(2, Menu.NONE, Menu.NONE, "Align end");
    item.setAlphabeticShortcut('f');
    // 设置组为排他单选
    menu.setGroupCheckable(2, true, true);

    // 子菜单
    SubMenu subMenu = menu.addSubMenu("New");
    subMenu.add("Document");
    subMenu.add("Image");

    // 其他组的菜单项
    menu.add(1, Menu.NONE, Menu.NONE, "Delete");
});
```

**⚠️ 不可用 API**:
- `setOnContextClickListener` — `View.onGenericMotionEvent()` 在 Modern UI 3.12.0 中直接返回 `false`，**无法触发**。始终使用 `setOnCreateContextMenuListener` 代替。

### 6.2 PopupMenu（锚定弹出菜单）✅

**类路径**: `icyllis.modernui.widget.PopupMenu`

**构造方法**:
- `new PopupMenu(Context ctx, View anchorView)` ✅

**关键方法**:
- `getMenu()` → `Menu` ✅
- `setOnMenuItemClickListener(item -> { return true; })` ✅
- `show()` ✅

**完整示例** (DevComponentsPageBuilder 行 943-954):
```java
import icyllis.modernui.widget.PopupMenu;

Button popupBtn = new Button(context, null, R.attr.buttonOutlinedStyle);
popupBtn.setText("Show PopupMenu");
popupBtn.setOnClickListener(v -> {
    PopupMenu popup = new PopupMenu(context, v);
    Menu menu = popup.getMenu();
    menu.add(0, 0, 0, "Iron Ingot — 64 stacks");
    menu.add(0, 1, 1, "Gold Ingot — 32 stacks");
    menu.add(0, 2, 2, "Diamond — 16 stacks");
    popup.setOnMenuItemClickListener(item -> {
        Toast.makeText(context, "Selected: " + item.getTitle(),
            Toast.LENGTH_SHORT).show();
        return true;
    });
    popup.show();
});
```

### 6.3 SubMenu ✅

**创建方式**:
- `menu.addSubMenu(CharSequence title)` ✅
- `menu.addSubMenu(int groupId, int itemId, int order, CharSequence title)` ✅

返回 `SubMenu` 对象，可继续调用 `add()` 添加子菜单项。

---

## Part 7: 动画系统 (Animation)

### 7.1 ObjectAnimator ✅

**类路径**: `icyllis.modernui.animation.ObjectAnimator`

**创建方法**:
- `ObjectAnimator.ofPropertyValuesHolder(Object target, PropertyValuesHolder... holders)` ✅
- `ObjectAnimator.ofFloat(Object target, Property property, float... values)` ✅

**关键方法**:
- `setDuration(long millis)` ✅
- `setRepeatCount(int count)` ✅ — `ValueAnimator.INFINITE` 为无限
- `setRepeatMode(int mode)` ✅ — `ObjectAnimator.REVERSE`
- `setInterpolator(TimeInterpolator interpolator)` ✅
- `start()` ✅
- `isRunning()` ✅
- `cancel()` ✅
- `addUpdateListener(a -> view.invalidate())` ✅
- `addListener(AnimatorListener)` ✅

**View 属性常量**:
- `View.ROTATION` ✅
- `View.ROTATION_X` ✅
- `View.ROTATION_Y` ✅
- `View.SCALE_X` ✅
- `View.SCALE_Y` ✅
- `View.TRANSLATION_X` ✅
- `View.TRANSLATION_Y` ✅
- `View.ALPHA` ✅
- `View.X`, `View.Y`, `View.Z` ✅

**完整示例** (TestFragment 行 432-449, 995-1004):
```java
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.view.View;

// 多属性组合动画
PropertyValuesHolder pvh1 = PropertyValuesHolder.ofFloat(View.ROTATION, 0, 360);
PropertyValuesHolder pvh2 = PropertyValuesHolder.ofFloat(View.SCALE_X, 1, 0.2f);
PropertyValuesHolder pvh3 = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1, 0.2f);
PropertyValuesHolder pvh4 = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0, 60);
PropertyValuesHolder pvh5 = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0, -180);
PropertyValuesHolder pvh6 = PropertyValuesHolder.ofFloat(View.ALPHA, 1, 0);

ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view, pvh1, pvh2, pvh3, pvh4, pvh5, pvh6);
anim.setRepeatCount(1);
anim.setRepeatMode(ObjectAnimator.REVERSE);
anim.setDuration(3000);

// 点击时播放
view.setClickable(true);
view.setOnClickListener(v -> {
    if (!anim.isRunning()) anim.start();
});
```

**Overshoot 弹跳动画** (DevComponentsPageBuilder 行 888-898):
```java
ObjectAnimator ovAnim = ObjectAnimator.ofFloat(box, View.TRANSLATION_X, 0, view.dp(100));
ovAnim.setDuration(600);
ovAnim.setRepeatCount(1);
ovAnim.setRepeatMode(ObjectAnimator.REVERSE);
ovAnim.setInterpolator(TimeInterpolator.OVERSHOOT);
```

### 7.2 PropertyValuesHolder ✅

**类路径**: `icyllis.modernui.animation.PropertyValuesHolder`

```java
PropertyValuesHolder pvh = PropertyValuesHolder.ofFloat(View.ROTATION, 0, 2880);
```

### 7.3 TimeInterpolator 常量 ✅

**类路径**: `icyllis.modernui.animation.TimeInterpolator`

| 常量 | 效果 |
|------|------|
| `LINEAR` | 匀速 |
| `ACCELERATE` | 加速 |
| `DECELERATE` | 减速 |
| `ACCELERATE_DECELERATE` | 先加速后减速 |
| `OVERSHOOT` | 超出后回弹 |
| `ANTICIPATE` | 先后退再前进 |
| `BOUNCE` | 弹跳 |
| `VISCOUS_FLUID` | 粘性流体 |
| `SINE` | 正弦 |

### 7.4 LayoutTransition ✅

**类路径**: `icyllis.modernui.animation.LayoutTransition`

给容器添加子视图增删时的过渡动画：

```java
import icyllis.modernui.animation.LayoutTransition;

layout.setLayoutTransition(new LayoutTransition());
```

### 7.5 自定义 FloatProperty ✅

**类路径**: `icyllis.modernui.util.FloatProperty`

用于 `ObjectAnimator` 驱动自定义属性 (TestFragment 行 861-884):

```java
import icyllis.modernui.util.FloatProperty;

private static final FloatProperty<MyView> sMyProp = new FloatProperty<>("myProp") {
    @Override
    public void setValue(MyView obj, float value) {
        obj.myField = value;
    }

    @Override
    public Float get(MyView obj) {
        return obj.myField;
    }
};

// 使用
ObjectAnimator anim = ObjectAnimator.ofFloat(view, sMyProp, 0, 80);
anim.setDuration(400);
anim.setInterpolator(TimeInterpolator.OVERSHOOT);
anim.addUpdateListener(a -> view.invalidate());  // 每帧重绘
anim.start();
```

---

## Part 8: 富文本系统 (Rich Text / Spannable)

### 8.1 SpannableString ✅

**类路径**: `icyllis.modernui.text.SpannableString`

**用法**: 创建可标记区间样式的文本。

```java
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spannable;
import icyllis.modernui.text.Spanned;

Spannable spannable = new SpannableString("Hello World Bold");
spannable.setSpan(spanObject, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
```

### 8.2 可用 Span 类型

| Span 类 | 包路径 | 效果 |
|---------|--------|------|
| `ForegroundColorSpan(int color)` ✅ | `icyllis.modernui.text.style` | 文字颜色 |
| `RelativeSizeSpan(float proportion)` ✅ | `icyllis.modernui.text.style` | 相对字体大小 |
| `StyleSpan(int style)` ✅ | `icyllis.modernui.text.style` | 粗体/斜体 (`Typeface.BOLD`) |
| `UnderlineSpan()` ✅ | `icyllis.modernui.text.style` | 下划线 |
| `StrikethroughSpan()` ✅ | `icyllis.modernui.text.style` | 删除线 |
| `SuperscriptSpan()` ✅ | `icyllis.modernui.text.style` | 上标 |
| `URLSpan(String url)` ✅ | `icyllis.modernui.text.style` | 可点击链接 |

### 8.3 完整示例 (TestFragment 行 387-424, DevComponentsPageBuilder 行 797-816)

```java
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spannable;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.style.*;
import icyllis.modernui.widget.TextView;

String text = "Bold text, colored, larger, underlined, and strikethrough.";
Spannable span = new SpannableString(text);

// 粗体: "Bold text"
span.setSpan(new StyleSpan(Typeface.BOLD), 0, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
// 前景色: "colored"
span.setSpan(new ForegroundColorSpan(0xFF00D4AA), 11, 18, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
// 放大: "larger"
span.setSpan(new RelativeSizeSpan(1.3f), 20, 26, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
// 下划线: "underlined"
span.setSpan(new UnderlineSpan(), 28, 38, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
// 删除线: "strikethrough"
span.setSpan(new StrikethroughSpan(), 44, 57, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

// 可点击链接（需要 setLinksClickable）
// span.setSpan(new URLSpan("https://example.com"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

TextView tv = new TextView(context);
tv.setText(span, TextView.BufferType.SPANNABLE);
tv.setTextSize(11);
tv.setLinksClickable(true);      // 链接可点击
tv.setTextIsSelectable(true);    // 文本可选
```

### 8.4 异步预计算文本 ✅

用于大段文本，避免阻塞 UI 线程 (TestFragment 行 404-410):

```java
import icyllis.modernui.text.PrecomputedText;
import java.util.concurrent.CompletableFuture;

CompletableFuture.runAsync(() -> {
    var precomputed = PrecomputedText.create(spannable, tv.getTextMetricsParams());
    view.post(() -> tv.setText(precomputed, TextView.BufferType.SPANNABLE));
});
```

---

## Part 9: 主题与样式 (Theme & Styling)

### 9.1 主题颜色解析 ✅

```java
import icyllis.modernui.R;
import icyllis.modernui.resources.TypedValue;

TypedValue value = new TypedValue();

// 获取主色
context.getTheme().resolveAttribute(R.ns, R.attr.colorPrimary, value, true);
int colorPrimary = value.data;

// 获取表面色
context.getTheme().resolveAttribute(R.ns, R.attr.colorSurface, value, true);
int colorSurface = value.data;

// 获取次级容器色
context.getTheme().resolveAttribute(R.ns, R.attr.colorSecondaryContainer, value, true);
int colorSecondaryContainer = value.data;
```

### 9.2 可用 R.attr 颜色常量 ✅

| 常量 | 用途 |
|------|------|
| `colorPrimary` | 主题主色 |
| `colorSecondary` | 次要色 |
| `colorSurface` | 表面背景色 |
| `colorSurfaceContainer` | 容器背景色 |
| `colorOnPrimaryContainer` | 主色容器上的文字色 |
| `colorOnSecondaryContainer` | 次色容器上的文字色 |
| `colorOnSurfaceVariant` | 表面变体上的文字色 |
| `colorSecondaryContainer` | 次色容器背景 |
| `colorPrimaryContainer` | 主色容器背景 |
| `colorOutlineVariant` | 轮廓变体色 |

### 9.3 视图属性 ✅

```java
// 阴影 / 高度
view.setElevation(view.dp(10));
view.setOutlineAmbientShadowColor(0xFF66CCFF);
view.setOutlineSpotShadowColor(0xFF66CCFF);

// 工具提示
view.setTooltipText("Hover info text");
```

### 9.4 样式应用

```java
// 应用暗色主题覆盖
// app.getTheme().applyStyle(R.style.ThemeOverlay_Material3_Dark_Rust, true);
```

---

## Part 10: 导航系统 (Navigation with Fragments)

### 10.1 Fragment 基础 ✅

**类路径**: `icyllis.modernui.fragment.Fragment`

```java
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.util.DataSet;

public class MyFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             DataSet savedInstanceState) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return root;
    }
}
```

### 10.2 Fragment 切换（导航）✅

```java
import icyllis.modernui.fragment.FragmentTransaction;

// 基于 CenterFragment2 模式
getChildFragmentManager().beginTransaction()
    .replace(containerId, new TargetFragment(), "tag")
    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
    .setReorderingAllowed(true)
    .commit();
```

### 10.3 导航栏模式（CenterFragment2）

使用 `RadioGroup` 作为导航栏，`RadioButton` 作为标签按钮：

```java
import icyllis.modernui.widget.RadioGroup;
import icyllis.modernui.widget.RadioButton;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.fragment.FragmentContainerView;

// 导航栏
RadioGroup navBar = new RadioGroup(context);
navBar.setOrientation(LinearLayout.HORIZONTAL);

// 导航按钮（4参数无样式版本用于自定义外观）
RadioButton tab = new RadioButton(context, null, null, null);
tab.setId(tabId);
// 自定义外观：ShapeDrawable pill + RippleDrawable + ImageDrawable 图标
// + ColorStateList 控制文字/图标/波纹/指示器颜色
navBar.addView(tab);

// 内容区域
FragmentContainerView container = new FragmentContainerView(context);
container.setId(containerId);

// 监听标签切换
navBar.setOnCheckedChangeListener((group, checkedId) -> {
    Fragment target = getFragmentForTab(checkedId);
    getChildFragmentManager().beginTransaction()
        .replace(containerId, target)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .setReorderingAllowed(true)
        .commit();
});
```

---

## Part 11: 右键菜单触发机制

### ⚠️ 关键章节 — AI Agent 必读

Modern UI 的右键菜单触发链路：

1. **触发入口**: `View.performButtonActionOnTouchDown()` 检查：
   ```
   isLongClickable() && (buttonState & BUTTON_SECONDARY) != 0
   ```
   → 调用 `showContextMenu(x, y)`

2. **传播**: `showContextMenu()` → `getParent().showContextMenuForChild()` → 创建浮动 ContextMenu → 调用 `onCreateContextMenuListener` 填充菜单

3. **必要条件**: 视图**必须**设为 `setLongClickable(true)`（直接设置，或通过 `setOnLongClickListener` 隐式设置）

### 子视图拦截问题 ⚠️

如果子视图是可点击的（如带 `setOnClickListener` 的按钮），它会**消费所有触摸事件**包括右键。

**解决方案**: 给子视图添加 `OnTouchListener` 转发右键到父行：

```java
import icyllis.modernui.view.MotionEvent;

child.setOnTouchListener((v, event) -> {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN
            && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
        parentRow.showContextMenu(event.getX(), event.getY());
        return true;
    }
    return false;
});
```

### ⚠️ 不可用 API

`setOnContextClickListener` 在 Modern UI 3.12.0 中**不工作** — `View.onGenericMotionEvent()` 直接返回 `false`。**永远使用** `setOnCreateContextMenuListener`。

---

## Part 12: 常用辅助模式 (Common Patterns)

### 12.1 三态交互背景 (hover/press feedback)

```java
import icyllis.modernui.R;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.util.StateSet;

StateListDrawable bg = new StateListDrawable();

ShapeDrawable pressed = new ShapeDrawable();
pressed.setColor(pressedColor);
pressed.setCornerRadius(r);
bg.addState(new int[]{R.attr.state_pressed}, pressed);

ShapeDrawable hovered = new ShapeDrawable();
hovered.setColor(hoveredColor);
hovered.setCornerRadius(r);
bg.addState(new int[]{R.attr.state_hovered}, hovered);

ShapeDrawable normal = new ShapeDrawable();
normal.setColor(normalColor);
normal.setCornerRadius(r);
bg.addState(StateSet.WILD_CARD, normal);

view.setBackground(bg);
```

### 12.2 dp 转换辅助 ✅

`view.dp(value)` 在所有 View 上可直接调用，将 dp 值转为像素：

```java
int px8 = view.dp(8);   // 8dp → 像素
int px12 = view.dp(12); // 12dp → 像素
```

### 12.3 国际化 (i18n) ✅

使用 Minecraft 的翻译系统：

```java
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;

// 方式 1: Component
String text = Component.translatable("gui.mymod.title").getString();

// 方式 2: I18n
String text2 = I18n.get("gui.mymod.title");

// 带参数
String formatted = Component.translatable("gui.mymod.count", 42).getString();
```

### 12.4 可点击视图设置 ✅

创建交互式元素时，**必须同时设置**：

```java
view.setClickable(true);
view.setFocusable(true);
// 如果需要右键菜单:
view.setLongClickable(true);
// 如果需要键盘焦点:
// view.setFocusableInTouchMode(true);
```

### 12.5 UI 重建模式

Modern UI 视图通常在数据变更时**完全重建**（移除所有子视图后重新添加）：

```java
container.removeAllViews();
// 重新构建所有子视图
for (Item item : dataList) {
    container.addView(buildItemView(item));
}
```

### 12.6 自定义小按钮模式 (ModernUiTheme)

```java
// 方式: 用 TextView 模拟按钮
TextView button = new TextView(context);
button.setText("Action");
button.setTextSize(10);
button.setIncludeFontPadding(false);
button.setSingleLine();
button.setTextColor(textColor);
button.setGravity(Gravity.CENTER);
button.setPadding(view.dp(8), view.dp(4), view.dp(8), view.dp(4));
button.setBackground(statefulBackgroundDrawable);
button.setClickable(true);
button.setFocusable(true);
button.setOnClickListener(v -> { /* action */ });
```

---

## Part 13: R 常量速查表

### R.attr — 控件样式

| 常量 | 用途 |
|------|------|
| `buttonOutlinedStyle` ✅ | 轮廓按钮样式 |
| `buttonElevatedStyle` ✅ | 带阴影按钮样式 |
| `editTextFilledStyle` ✅ | 填充文本输入样式 |
| `iconButtonFilledStyle` ✅ | 填充图标按钮样式 |
| `textAppearanceLabelLarge` ✅ | 大标签文本样式 |
| `textAppearanceTitleMedium` ✅ | 中号标题文本样式 |
| `progressBarStyleHorizontal` ✅ | 水平进度条样式 |

### R.attr — 状态

| 常量 | 触发条件 |
|------|----------|
| `state_pressed` ✅ | 鼠标/手指按下 |
| `state_hovered` ✅ | 鼠标悬停 |
| `state_checked` ✅ | 开关/选项选中 |
| `state_focused` ✅ | 拥有键盘焦点 |
| `state_enabled` ✅ | 非禁用态 |

### R.attr — 主题颜色

| 常量 | 说明 |
|------|------|
| `colorPrimary` ✅ | 主色 |
| `colorSecondary` ✅ | 次色 |
| `colorSurface` ✅ | 表面色 |
| `colorSurfaceContainer` ✅ | 容器表面色 |
| `colorOnPrimaryContainer` ✅ | 主容器文字色 |
| `colorOnSecondaryContainer` ✅ | 次容器文字色 |
| `colorOnSurfaceVariant` ✅ | 表面变体文字色 |
| `colorSecondaryContainer` ✅ | 次色容器 |
| `colorPrimaryContainer` ✅ | 主色容器 |
| `colorOutlineVariant` ✅ | 轮廓变体 |

### R.style

| 常量 | 用途 |
|------|------|
| `Widget_Material3_SeekBar_Discrete_Slider` ✅ | 离散滑块（有刻度） |
| `Widget_Material3_SeekBar` ✅ | 标准滑块 |
| `Widget_Material3_SeekBar_Slider` ✅ | 滑块变体 |

### R.ns ✅

`R.ns` 是**命名空间常量**（`int` 类型），作为 `resolveAttribute()` 的第一个参数。

### R.id

| 常量 | 用途 |
|------|------|
| `R.id.input` ✅ | 标准输入字段 ID |

---

## 附录：与 Android 的差异速查

| 特性 | Modern UI (icyllis.modernui) | Android |
|------|------------------------------|---------|
| 包前缀 | `icyllis.modernui` | `android` |
| R 类 | `icyllis.modernui.R` | `android.R` |
| Context | `icyllis.modernui.core.Context` | `android.content.Context` |
| 状态保存 | `DataSet` | `Bundle` |
| 布局方式 | 纯代码（无 XML） | XML + 代码 |
| dp 转换 | `view.dp(value)` | `TypedValue.applyDimension(...)` |
| ColorStateList | `icyllis.modernui.util.ColorStateList` | `android.content.res.ColorStateList` |
| StateSet | `icyllis.modernui.util.StateSet` | `android.util.StateSet` |
| ContextClick | ⚠️ `setOnContextClickListener` 不可用 | 正常可用 |
| 主题解析 | `resolveAttribute(R.ns, R.attr.xxx, value, true)` | `resolveAttribute(R.attr.xxx, value, true)` |

---

## 附录：完整 import 速查

```java
// 核心
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.util.DataSet;

// 视图
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.Menu;
import icyllis.modernui.view.MenuItem;
import icyllis.modernui.view.SubMenu;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.KeyEvent;
import static icyllis.modernui.view.ViewGroup.LayoutParams.*;

// 控件
import icyllis.modernui.widget.*;

// Drawable
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.BuiltinIconDrawable;

// 工具
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.util.FloatProperty;

// 文本
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spannable;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.PrecomputedText;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.text.style.RelativeSizeSpan;
import icyllis.modernui.text.style.StyleSpan;
import icyllis.modernui.text.style.UnderlineSpan;
import icyllis.modernui.text.style.StrikethroughSpan;
import icyllis.modernui.text.style.SuperscriptSpan;
import icyllis.modernui.text.style.URLSpan;

// 动画
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
```

---

> **文档版本**: v1.0 · 基于 Modern UI 3.12.0 · 所有 API 经 javap 验证
> **源码参考**: TestFragment.java (1127行), DevComponentsPageBuilder.java (960行), ModernUiTheme.java (275行)
