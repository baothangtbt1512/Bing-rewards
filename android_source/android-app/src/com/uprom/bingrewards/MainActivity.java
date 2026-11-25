package com.uprom.bingrewards;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final int ID_BTN_APP_INFO = 1001;
    private static final int ID_BTN_ACCESS = 1002;
    private static final int ID_BTN_LAUNCH_PANEL = 1003;
    private static final int ID_BTN_RUN_SEARCH = 1004;
    private static final int ID_BTN_RUN_READ = 1005;
    private static final int ID_BTN_RUN_COMBO = 1006;

    private Button btnAccessPerm;
    private EditText edtTotalSearches;
    private EditText edtBatchSize;
    private EditText edtCooldownMin;
    private EditText edtTotalArticles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences(BingAutoService.PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("cooldown_off_v2", false)) {
            prefs.edit().putInt("batchSize", 0).putBoolean("cooldown_off_v2", true).apply();
        }
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private GradientDrawable roundedBg(int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private void buildUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0F172A"));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(22), dp(18), dp(30));

        TextView title = new TextView(this);
        title.setText("🏆 Bing Rewards Auto");
        title.setTextColor(Color.parseColor("#38BDF8"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Phiên bản mới: Tự hiện Cửa Sổ Nổi thông qua Trợ Năng (Không cần bật quyền Cửa Sổ Nổi riêng!)");
        subtitle.setTextColor(Color.parseColor("#4ADE80"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(16);
        container.addView(subtitle, subLp);

        // Card 1: Bật Quyền Trợ Năng duy nhất
        LinearLayout permCard = createCard();
        permCard.addView(createSectionTitle("Bước 1: Chỉ cần bật quyền Trợ Năng"));

        TextView noteRestricted = new TextView(this);
        noteRestricted.setText("💡 Mẹo (Android 13/14): Nếu điện thoại báo 'Cài đặt bị hạn chế' không cho bật, hãy bấm nút màu tím bên dưới -> Bấm dấu 3 chấm (⋮) góc trên phải -> Chọn 'Cho phép cài đặt bị hạn chế'.");
        noteRestricted.setTextColor(Color.parseColor("#CBD5E1"));
        noteRestricted.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(6);
        permCard.addView(noteRestricted, noteLp);

        Button btnAppInfo = createButton("🔓 Mở Thông Tin App (Bỏ chặn 'Cài đặt bị hạn chế')", "#6D28D9");
        btnAppInfo.setId(ID_BTN_APP_INFO);
        btnAppInfo.setOnClickListener(this);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        infoLp.topMargin = dp(10);
        permCard.addView(btnAppInfo, infoLp);

        btnAccessPerm = createButton("Bật quyền Trợ Năng (Bing Rewards Auto)", "#D97706");
        btnAccessPerm.setId(ID_BTN_ACCESS);
        btnAccessPerm.setOnClickListener(this);
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        b2Lp.topMargin = dp(10);
        permCard.addView(btnAccessPerm, b2Lp);

        container.addView(permCard);

        // Card 2: Cấu hình
        SharedPreferences prefs = getSharedPreferences(BingAutoService.PREFS_NAME, MODE_PRIVATE);

        LinearLayout configCard = createCard();
        LinearLayout.LayoutParams card2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card2Lp.topMargin = dp(14);

        configCard.addView(createSectionTitle("Bước 2: Cấu hình nhiệm vụ"));

        edtTotalSearches = addSettingField(configCard,
                "🔍 Tổng số lượt Tìm kiếm Mobile (20 lượt = 60 điểm):",
                String.valueOf(prefs.getInt("totalSearches", 20)));

        edtTotalArticles = addSettingField(configCard,
                "📰 Số bài báo cần đọc (10 bài = 30 điểm):",
                String.valueOf(prefs.getInt("totalArticles", 10)));

        edtBatchSize = addSettingField(configCard,
                "⏳ Nghỉ 15 phút giữa các đợt (Mặc định 0 = TẮT, chạy 1 mạch không chờ):",
                String.valueOf(prefs.getInt("batchSize", 0)));

        edtCooldownMin = addSettingField(configCard,
                "Thời gian nghỉ Cooldown (phút) nếu bật tính năng trên:",
                String.valueOf(prefs.getInt("cooldownMinutes", 15)));

        container.addView(configCard, card2Lp);

        // Card 3: Khởi chạy trực tiếp
        LinearLayout runCard = createCard();
        LinearLayout.LayoutParams card3Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card3Lp.topMargin = dp(14);

        runCard.addView(createSectionTitle("Bước 3: Bấm chạy nhiệm vụ"));

        Button btnLaunchPanel = createButton("🪟 Hiện Bảng Điều Khiển Nổi & Mở App Bing", "#0284C7");
        btnLaunchPanel.setId(ID_BTN_LAUNCH_PANEL);
        btnLaunchPanel.setOnClickListener(this);
        LinearLayout.LayoutParams lpPanel = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        lpPanel.topMargin = dp(10);
        runCard.addView(btnLaunchPanel, lpPanel);

        LinearLayout rowDirect = new LinearLayout(this);
        rowDirect.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);

        Button btnRunSearch = createButton("🔍 Chạy Search Ngay", "#0369A1");
        btnRunSearch.setId(ID_BTN_RUN_SEARCH);
        btnRunSearch.setOnClickListener(this);
        LinearLayout.LayoutParams rsLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        rsLp.rightMargin = dp(4);
        rowDirect.addView(btnRunSearch, rsLp);

        Button btnRunRead = createButton("📰 Chạy Đọc Báo Ngay", "#7C3AED");
        btnRunRead.setId(ID_BTN_RUN_READ);
        btnRunRead.setOnClickListener(this);
        LinearLayout.LayoutParams rrLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        rrLp.leftMargin = dp(4);
        rowDirect.addView(btnRunRead, rrLp);

        runCard.addView(rowDirect, rowLp);

        Button btnRunCombo = createButton("🚀 CHẠY TẤT CẢ NGAY (Đọc Báo + Search)", "#059669");
        btnRunCombo.setId(ID_BTN_RUN_COMBO);
        btnRunCombo.setOnClickListener(this);
        btnRunCombo.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        rcLp.topMargin = dp(8);
        runCard.addView(btnRunCombo, rcLp);

        container.addView(runCard, card3Lp);

        scrollView.addView(container);
        setContentView(scrollView);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == ID_BTN_APP_INFO) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this,
                    "Bấm dấu 3 chấm (⋮) ở góc trên bên phải -> Chọn 'Cho phép cài đặt bị hạn chế'",
                    Toast.LENGTH_LONG).show();
        } else if (id == ID_BTN_ACCESS) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this,
                    "Hãy tìm và bật mục 'Bing Rewards Auto' trong danh sách!",
                    Toast.LENGTH_LONG).show();
        } else if (id == ID_BTN_LAUNCH_PANEL) {
            startWithMode(null);
        } else if (id == ID_BTN_RUN_SEARCH) {
            startWithMode("search");
        } else if (id == ID_BTN_RUN_READ) {
            startWithMode("read");
        } else if (id == ID_BTN_RUN_COMBO) {
            startWithMode("combo");
        }
    }

    private void startWithMode(String taskMode) {
        saveSettings();
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this,
                    "Vui lòng bật quyền 'Trợ Năng (Bing Rewards Auto)' ở Bước 1 trước!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (BingAutoService.instance != null) {
            BingAutoService.instance.showFloatingPanel();
            if (taskMode != null) {
                BingAutoService.instance.startTask(taskMode);
            }
        }

        Intent launchBing = getPackageManager().getLaunchIntentForPackage(BingAutoService.BING_PACKAGE);
        if (launchBing != null) {
            launchBing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchBing);
        }
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBg(Color.parseColor("#1E293B"), 12));
        return card;
    }

    private TextView createSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#F8FAFC"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private Button createButton(String text, String colorHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setBackground(roundedBg(Color.parseColor(colorHex), 8));
        return b;
    }

    private EditText addSettingField(LinearLayout parent, String label, String defaultValue) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = dp(10);
        parent.addView(tv, tvLp);

        EditText edt = new EditText(this);
        edt.setText(defaultValue);
        edt.setTextColor(Color.WHITE);
        edt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        edt.setInputType(InputType.TYPE_CLASS_NUMBER);
        edt.setPadding(dp(12), dp(6), dp(12), dp(6));
        edt.setBackground(roundedBg(Color.parseColor("#0F172A"), 8));
        LinearLayout.LayoutParams edtLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        edtLp.topMargin = dp(4);
        parent.addView(edt, edtLp);
        return edt;
    }

    private void saveSettings() {
        int totalSearches = parseIntSafe(edtTotalSearches.getText().toString(), 20, 1);
        int totalArticles = parseIntSafe(edtTotalArticles.getText().toString(), 10, 1);
        int batchSize = parseIntSafe(edtBatchSize.getText().toString(), 0, 0);
        int cooldownMin = parseIntSafe(edtCooldownMin.getText().toString(), 15, 1);

        SharedPreferences prefs = getSharedPreferences(BingAutoService.PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putInt("totalSearches", totalSearches)
                .putInt("totalArticles", totalArticles)
                .putInt("batchSize", batchSize)
                .putInt("cooldownMinutes", cooldownMin)
                .apply();
    }

    private int parseIntSafe(String val, int fallback, int min) {
        try {
            return Math.max(min, Integer.parseInt(val.trim()));
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String expectedComponentName = getPackageName() + "/" + BingAutoService.class.getCanonicalName();
        String enabledServicesSetting = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServicesSetting == null) return false;
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            String componentName = colonSplitter.next();
            if (componentName.equalsIgnoreCase(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }

    private void refreshPermissionStatus() {
        boolean accessOk = isAccessibilityServiceEnabled();
        btnAccessPerm.setText(accessOk
                ? "✅ Quyền Trợ Năng (Bing Auto): ĐÃ BẬT"
                : "❌ Bấm vào đây để Bật Quyền Trợ Năng");
        btnAccessPerm.setBackground(roundedBg(Color.parseColor(accessOk ? "#059669" : "#D97706"), 8));

        if (accessOk && BingAutoService.instance != null) {
            BingAutoService.instance.showFloatingPanel();
        }
    }
}
