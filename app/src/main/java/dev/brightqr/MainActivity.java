package dev.brightqr;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String MOVIE_SEAT_SAMPLE =
            "BRIGHT CINEMA\n" +
            "The Last Horizon\n" +
            "Saturday · 7:30 PM\n" +
            "Auditorium 6\n" +
            "Row J · Seats 12–13\n" +
            "Booking BQR-8X42";

    private HdrQrSurfaceView qrView;
    private DisplayDiagnostics diagnostics;
    private TextView renderingMode;
    private TextView diagnosticText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applySystemBarAppearance();
        // Deliberately do not modify screenBrightness. The surrounding UI follows
        // the user's SDR brightness while only the QR SurfaceView requests HDR.

        DisplayManager displayManager =
                (DisplayManager) getSystemService(DISPLAY_SERVICE);
        diagnostics = new DisplayDiagnostics(
                this,
                displayManager == null
                        ? null
                        : displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY),
                this::showDiagnostics
        );
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        qrView.onResume();
        diagnostics.start(this::runOnUiThread);
    }

    @Override
    protected void onPause() {
        diagnostics.stop();
        qrView.onPause();
        super.onPause();
    }

    private View buildContent() {
        int pagePadding = dp(20);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(getColor(R.color.background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(getColor(R.color.background));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        applySafeInsets(scrollView, root, pagePadding);

        TextView title = text("Bright QR", 26, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = text(
                "Maximum localized brightness, within Android's current limit",
                14,
                R.color.text_secondary
        );
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        subtitleParams.bottomMargin = dp(12);
        root.addView(subtitle, subtitleParams);

        EditText payload = new EditText(this);
        payload.setSingleLine(false);
        payload.setMinLines(3);
        payload.setMaxLines(6);
        payload.setGravity(Gravity.TOP | Gravity.START);
        payload.setText(MOVIE_SEAT_SAMPLE);
        payload.setSelectAllOnFocus(true);
        payload.setHint("Text or URL");
        payload.setTextColor(getColor(R.color.text_primary));
        payload.setHintTextColor(getColor(R.color.text_secondary));
        payload.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        payload.setImeOptions(EditorInfo.IME_ACTION_DONE);
        payload.setBackgroundColor(getColor(R.color.surface));
        payload.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(payload, matchWrap());

        Button update = new Button(this);
        update.setText(R.string.update_qr);
        update.setAllCaps(false);
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(8);
        root.addView(update, buttonParams);

        renderingMode = text("Starting renderer…", 13, R.color.text_secondary);
        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.topMargin = dp(6);
        root.addView(renderingMode, modeParams);

        TextView developerTitle = text("Developer HDR headroom", 15, R.color.text_primary);
        developerTitle.setGravity(Gravity.START);
        developerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams developerTitleParams = matchWrap();
        developerTitleParams.topMargin = dp(18);
        root.addView(developerTitle, developerTitleParams);

        TextView dialValue = text("Requested: MAX (10,000×)", 14, R.color.primary);
        dialValue.setGravity(Gravity.START);
        LinearLayout.LayoutParams dialValueParams = matchWrap();
        dialValueParams.topMargin = dp(3);
        root.addView(dialValue, dialValueParams);

        SeekBar headroomDial = new SeekBar(this);
        headroomDial.setMax(HdrPolicy.DEV_DIAL_MAX_PROGRESS);
        headroomDial.setProgress(HdrPolicy.DEV_DIAL_MAX_PROGRESS);
        headroomDial.setContentDescription("Requested HDR headroom");
        root.addView(headroomDial, matchWrap());

        TextView dialHelp = text(
                "1.0×–10.9× in 0.1 steps · final position is uncapped MAX",
                12,
                R.color.text_secondary
        );
        dialHelp.setGravity(Gravity.START);
        LinearLayout.LayoutParams dialHelpParams = matchWrap();
        dialHelpParams.bottomMargin = dp(12);
        root.addView(dialHelp, dialHelpParams);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setWeightSum(4f);
        Button presetSdr = presetButton(R.string.preset_sdr_1x);
        Button preset2x = presetButton(R.string.preset_2x);
        Button preset4x = presetButton(R.string.preset_4x);
        Button presetMax = presetButton(R.string.preset_max);
        presetRow.addView(presetSdr, weightedButtonParams());
        presetRow.addView(preset2x, weightedButtonParams());
        presetRow.addView(preset4x, weightedButtonParams());
        presetRow.addView(presetMax, weightedButtonParams());
        LinearLayout.LayoutParams presetParams = matchWrap();
        presetParams.bottomMargin = dp(12);
        root.addView(presetRow, presetParams);

        TextView comparisonTitle = text("Brightness comparison", 15, R.color.text_primary);
        comparisonTitle.setGravity(Gravity.START);
        comparisonTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams comparisonTitleParams = matchWrap();
        comparisonTitleParams.bottomMargin = dp(6);
        root.addView(comparisonTitle, comparisonTitleParams);

        LinearLayout referenceRow = new LinearLayout(this);
        referenceRow.setOrientation(LinearLayout.HORIZONTAL);
        referenceRow.setGravity(Gravity.CENTER_VERTICAL);
        referenceRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        referenceRow.setBackgroundColor(getColor(R.color.surface));
        View sdrWhite = new View(this);
        sdrWhite.setContentDescription("Ordinary SDR white reference");
        GradientDrawable swatchBackground = new GradientDrawable();
        swatchBackground.setColor(Color.WHITE);
        swatchBackground.setCornerRadius(dp(4));
        swatchBackground.setStroke(dp(1), getColor(R.color.text_secondary));
        sdrWhite.setBackground(swatchBackground);
        referenceRow.addView(sdrWhite, new LinearLayout.LayoutParams(dp(76), dp(54)));

        TextView referenceLabel = text(
                "SDR reference white\nOrdinary Android View\nCompare with HDR QR white below ↓",
                13,
                R.color.text_secondary
        );
        referenceLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams referenceLabelParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        referenceLabelParams.setMarginStart(dp(12));
        referenceRow.addView(referenceLabel, referenceLabelParams);
        LinearLayout.LayoutParams referenceRowParams = matchWrap();
        referenceRowParams.bottomMargin = dp(10);
        root.addView(referenceRow, referenceRowParams);

        FrameLayout qrFrame = new FrameLayout(this);
        qrFrame.setForegroundGravity(Gravity.CENTER);
        int qrSize = Math.min(
                getResources().getDisplayMetrics().widthPixels - pagePadding * 2,
                dp(420)
        );
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                qrSize
        );
        root.addView(qrFrame, frameParams);

        qrView = new HdrQrSurfaceView(this, (hdrActive, detail) -> {
            renderingMode.setText(
                    hdrActive ? "HDR renderer active · " + detail : "SDR fallback · " + detail);
            if (diagnostics != null) {
                diagnostics.setRendererState(hdrActive, detail);
            }
        });
        FrameLayout.LayoutParams qrParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        qrFrame.addView(qrView, qrParams);

        TextView conditionsTitle = text("Device conditions", 15, R.color.text_primary);
        conditionsTitle.setGravity(Gravity.START);
        conditionsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams conditionsTitleParams = matchWrap();
        conditionsTitleParams.topMargin = dp(18);
        conditionsTitleParams.bottomMargin = dp(6);
        root.addView(conditionsTitle, conditionsTitleParams);

        diagnosticText = text("Reading display capabilities…", 12, R.color.text_secondary);
        diagnosticText.setTypeface(Typeface.MONOSPACE);
        diagnosticText.setGravity(Gravity.START);
        diagnosticText.setBackgroundColor(getColor(R.color.surface));
        diagnosticText.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams diagnosticsParams = matchWrap();
        root.addView(diagnosticText, diagnosticsParams);

        Button copyReport = new Button(this);
        copyReport.setText(R.string.copy_report);
        copyReport.setAllCaps(false);
        LinearLayout.LayoutParams copyParams = matchWrap();
        copyParams.topMargin = dp(6);
        root.addView(copyReport, copyParams);

        TextView sampleTitle = text("SDR comparison copy", 15, R.color.text_primary);
        sampleTitle.setGravity(Gravity.START);
        sampleTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams sampleTitleParams = matchWrap();
        sampleTitleParams.topMargin = dp(22);
        sampleTitleParams.bottomMargin = dp(6);
        root.addView(sampleTitle, sampleTitleParams);

        TextView sample = text(MOVIE_SEAT_SAMPLE, 16, R.color.text_primary);
        sample.setGravity(Gravity.START);
        sample.setTypeface(Typeface.MONOSPACE);
        sample.setBackgroundColor(getColor(R.color.surface));
        sample.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(sample, matchWrap());

        Runnable updateQr = () -> {
            try {
                qrView.setPayload(payload.getText().toString());
            } catch (WriterException | IllegalArgumentException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        update.setOnClickListener(ignored -> updateQr.run());
        payload.setOnEditorActionListener((ignored, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                updateQr.run();
                return true;
            }
            return false;
        });

        if (Build.VERSION.SDK_INT >= 35) {
            headroomDial.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float headroom = HdrPolicy.headroomForDialProgress(progress);
                    qrView.applyRequestedHeadroom(headroom);
                    dialValue.setText(formatRequestedHeadroom(headroom));
                    if (diagnostics != null) {
                        diagnostics.setRequestedHeadroom(headroom);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            presetSdr.setOnClickListener(ignored -> headroomDial.setProgress(0));
            preset2x.setOnClickListener(ignored -> headroomDial.setProgress(10));
            preset4x.setOnClickListener(ignored -> headroomDial.setProgress(30));
            presetMax.setOnClickListener(
                    ignored -> headroomDial.setProgress(HdrPolicy.DEV_DIAL_MAX_PROGRESS));
        } else {
            headroomDial.setEnabled(false);
            presetSdr.setEnabled(false);
            preset2x.setEnabled(false);
            preset4x.setEnabled(false);
            presetMax.setEnabled(false);
            dialValue.setText(R.string.headroom_requires_android_15);
        }

        copyReport.setOnClickListener(ignored -> copyDiagnosticsReport());

        updateQr.run();
        return scrollView;
    }

    private void applySafeInsets(ScrollView scrollView, LinearLayout content, int pagePadding) {
        int verticalPadding = dp(16);
        scrollView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = windowInsets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            }
            content.setPadding(
                    pagePadding + left,
                    verticalPadding + top,
                    pagePadding + right,
                    verticalPadding + bottom
            );
            return windowInsets;
        });
        scrollView.requestApplyInsets();
    }

    private void applySystemBarAppearance() {
        boolean lightTheme = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightTheme ? mask : 0, mask);
            }
            return;
        }

        int visibility = decor.getSystemUiVisibility();
        visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 27) {
            visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        if (lightTheme) {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 27) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(visibility);
    }

    private static String formatRequestedHeadroom(float headroom) {
        return HdrPolicy.isMaximumRequest(headroom)
                ? "Requested: MAX (10,000×)"
                : String.format(Locale.US, "Requested: %.1f×", headroom);
    }

    private void showDiagnostics(String value) {
        if (diagnosticText != null) {
            diagnosticText.setText(value);
        }
    }

    private void copyDiagnosticsReport() {
        if (diagnostics == null) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.report_clip_label),
                diagnostics.getReport()
        ));
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value, int sp, int colorResource) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(getColor(colorResource));
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        return view;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private Button presetButton(int textResource) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setAllCaps(false);
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private static LinearLayout.LayoutParams weightedButtonParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }
}
