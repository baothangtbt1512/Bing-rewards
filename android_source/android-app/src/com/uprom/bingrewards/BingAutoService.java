package com.uprom.bingrewards;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Random;

public class BingAutoService extends AccessibilityService
        implements View.OnClickListener, View.OnTouchListener, Handler.Callback, Runnable {

    public static final String BING_PACKAGE = "com.microsoft.bing";
    public static final String PREFS_NAME = "bing_rewards_prefs";

    private static final int ID_BTN_MIN = 2001;
    private static final int ID_BTN_CLOSE = 2002;
    private static final int ID_BTN_SEARCH = 2003;
    private static final int ID_BTN_READ = 2004;
    private static final int ID_BTN_COMBO = 2005;
    private static final int ID_BTN_STOP = 2006;
    private static final int ID_BTN_SEARCH_DEC = 2007;
    private static final int ID_BTN_SEARCH_INC = 2008;
    private static final int ID_BTN_READ_DEC = 2009;
    private static final int ID_BTN_READ_INC = 2010;
    private static final int ID_BTN_COOLDOWN_TOGGLE = 2011;

    private static final int MSG_SHOW_PANEL = 1;
    private static final int MSG_REMOVE_PANEL = 2;
    private static final int MSG_SET_RUNNING_UI = 3;
    private static final int MSG_UPDATE_STATUS = 4;

    public static BingAutoService instance;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;

    private TextView txtStatus;
    private TextView btnMinimize;
    private LinearLayout panelButtons;
    private LinearLayout panelSettings;
    private TextView txtSearchCount;
    private TextView txtReadCount;
    private TextView btnCooldownToggle;
    private LinearLayout rowStartButtons;
    private Button btnCombo;
    private Button btnStop;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    private boolean isMinimized = false;
    private volatile boolean isRunning = false;
    private volatile String currentTaskMode = "search";
    private Thread workerThread;
    private Handler mainHandler;
    private final Random random = new Random();
    private final KeywordGenerator keywordGenerator = new KeywordGenerator();

    private int screenWidth = 1080;
    private int screenHeight = 1920;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper(), this);
        updateScreenMetrics();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("cooldown_off_v2", false)) {
            prefs.edit().putInt("batchSize", 0).putBoolean("cooldown_off_v2", true).apply();
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper(), this);
        }
        updateScreenMetrics();
        if (canDrawOverlays()) {
            showFloatingPanel();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        // KHÔNG ngắt luồng ở đây! Android OS tự động gọi onInterrupt khi đổi cửa sổ hoặc chạm màn hình.
    }

    @Override
    public void onDestroy() {
        stopCurrentTask();
        removeFloatingPanel();
        instance = null;
        super.onDestroy();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SHOW_PANEL:
                if (floatingView == null && canDrawOverlays()) {
                    buildFloatingPanel();
                }
                return true;
            case MSG_REMOVE_PANEL:
                if (floatingView != null && windowManager != null) {
                    try {
                        windowManager.removeView(floatingView);
                    } catch (Exception ignored) {
                    }
                    floatingView = null;
                }
                return true;
            case MSG_SET_RUNNING_UI:
                if (floatingView != null) {
                    boolean running = msg.arg1 == 1;
                    if (panelSettings != null) {
                        panelSettings.setVisibility(running ? View.GONE : View.VISIBLE);
                    }
                    rowStartButtons.setVisibility(running ? View.GONE : View.VISIBLE);
                    btnCombo.setVisibility(running ? View.GONE : View.VISIBLE);
                    btnStop.setVisibility(running ? View.VISIBLE : View.GONE);
                }
                return true;
            case MSG_UPDATE_STATUS:
                if (txtStatus != null && msg.obj != null) {
                    txtStatus.setText(String.valueOf(msg.obj));
                }
                return true;
        }
        return false;
    }

    private void updateScreenMetrics() {
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    screenWidth = dm.widthPixels;
                    screenHeight = dm.heightPixels;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean canDrawOverlays() {
        return true;
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

    public void showFloatingPanel() {
        if (mainHandler != null) {
            mainHandler.sendEmptyMessage(MSG_SHOW_PANEL);
        }
    }

    public void removeFloatingPanel() {
        if (mainHandler != null) {
            mainHandler.sendEmptyMessage(MSG_REMOVE_PANEL);
        }
    }

    private void setRunningUI(boolean running) {
        if (mainHandler != null) {
            Message m = mainHandler.obtainMessage(MSG_SET_RUNNING_UI, running ? 1 : 0, 0);
            mainHandler.sendMessage(m);
        }
    }

    private void updateStatus(String message) {
        if (mainHandler != null) {
            Message m = mainHandler.obtainMessage(MSG_UPDATE_STATUS, message);
            mainHandler.sendMessage(m);
        }
    }

    private void buildFloatingPanel() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable rootBg = roundedBg(Color.parseColor("#EE0F172A"), 14);
        rootBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        root.setBackground(rootBg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOnTouchListener(this);

        TextView txtTitle = new TextView(this);
        txtTitle.setText("🏆 Bing Rewards Auto");
        txtTitle.setTextColor(Color.parseColor("#38BDF8"));
        txtTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        txtTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(txtTitle, titleLp);

        btnMinimize = new TextView(this);
        btnMinimize.setId(ID_BTN_MIN);
        btnMinimize.setText(" Thu gọn ");
        btnMinimize.setTextColor(Color.parseColor("#E2E8F0"));
        btnMinimize.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btnMinimize.setPadding(dp(6), dp(3), dp(6), dp(3));
        btnMinimize.setBackground(roundedBg(Color.parseColor("#334155"), 6));
        btnMinimize.setOnClickListener(this);
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        minLp.rightMargin = dp(6);
        header.addView(btnMinimize, minLp);

        TextView btnClose = new TextView(this);
        btnClose.setId(ID_BTN_CLOSE);
        btnClose.setText(" ✕ ");
        btnClose.setTextColor(Color.parseColor("#FCA5A5"));
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnClose.setPadding(dp(6), dp(2), dp(6), dp(2));
        btnClose.setBackground(roundedBg(Color.parseColor("#7F1D1D"), 6));
        btnClose.setOnClickListener(this);
        header.addView(btnClose);

        root.addView(header, new LinearLayout.LayoutParams(dp(235), LinearLayout.LayoutParams.WRAP_CONTENT));

        txtStatus = new TextView(this);
        txtStatus.setText("Sẵn sàng! Bấm nút bên dưới để chạy");
        txtStatus.setTextColor(Color.parseColor("#F1F5F9"));
        txtStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        txtStatus.setMaxLines(2);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(6);
        root.addView(txtStatus, statusLp);

        panelButtons = new LinearLayout(this);
        panelButtons.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panelLp.topMargin = dp(8);

        // Bảng cài đặt nhanh số lần chạy ngay trên cửa sổ nổi
        panelSettings = new LinearLayout(this);
        panelSettings.setOrientation(LinearLayout.VERTICAL);
        panelSettings.setBackground(roundedBg(Color.parseColor("#1E293B"), 8));
        panelSettings.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        settingsLp.bottomMargin = dp(6);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int curSearches = prefs.getInt("totalSearches", 20);
        int curArticles = prefs.getInt("totalArticles", 10);
        int curBatch = prefs.getInt("batchSize", 0);

        LinearLayout rowCounters = new LinearLayout(this);
        rowCounters.setOrientation(LinearLayout.HORIZONTAL);
        rowCounters.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout colSearch = createCounterCol("🔍", ID_BTN_SEARCH_DEC, ID_BTN_SEARCH_INC, curSearches);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        csLp.rightMargin = dp(4);
        rowCounters.addView(colSearch, csLp);

        LinearLayout colRead = createCounterCol("📰", ID_BTN_READ_DEC, ID_BTN_READ_INC, curArticles);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        crLp.leftMargin = dp(4);
        rowCounters.addView(colRead, crLp);

        panelSettings.addView(rowCounters);

        btnCooldownToggle = new TextView(this);
        btnCooldownToggle.setId(ID_BTN_COOLDOWN_TOGGLE);
        updateCooldownButtonText(curBatch);
        btnCooldownToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btnCooldownToggle.setPadding(dp(4), dp(3), dp(4), dp(3));
        btnCooldownToggle.setGravity(Gravity.CENTER);
        btnCooldownToggle.setBackground(roundedBg(Color.parseColor("#334155"), 6));
        btnCooldownToggle.setOnClickListener(this);
        LinearLayout.LayoutParams cdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cdLp.topMargin = dp(5);
        panelSettings.addView(btnCooldownToggle, cdLp);

        panelButtons.addView(panelSettings, settingsLp);

        rowStartButtons = new LinearLayout(this);
        rowStartButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSearch = createActionButton("🔍 Search", "#0284C7", ID_BTN_SEARCH);
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        b1Lp.rightMargin = dp(4);
        rowStartButtons.addView(btnSearch, b1Lp);

        Button btnRead = createActionButton("📰 Đọc báo", "#7C3AED", ID_BTN_READ);
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        b2Lp.leftMargin = dp(4);
        rowStartButtons.addView(btnRead, b2Lp);

        panelButtons.addView(rowStartButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnCombo = createActionButton("🚀 Chạy Tất Cả (Báo + Search)", "#059669", ID_BTN_COMBO);
        LinearLayout.LayoutParams comboLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        comboLp.topMargin = dp(6);
        panelButtons.addView(btnCombo, comboLp);

        btnStop = createActionButton("⏹ DỪNG LẠI NGAY", "#DC2626", ID_BTN_STOP);
        btnStop.setVisibility(View.GONE);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        stopLp.topMargin = dp(6);
        panelButtons.addView(btnStop, stopLp);

        root.addView(panelButtons, panelLp);

        int overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = dp(12);
        layoutParams.y = (int) (screenHeight * 0.16f);

        floatingView = root;
        try {
            windowManager.addView(floatingView, layoutParams);
        } catch (Exception e) {
            floatingView = null;
        }
    }

    private LinearLayout createCounterCol(String icon, int decId, int incId, int initialVal) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.HORIZONTAL);
        col.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextColor(Color.WHITE);
        tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        col.addView(tvIcon);

        TextView btnDec = createMiniButton(" - ", decId);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dLp.leftMargin = dp(3);
        col.addView(btnDec, dLp);

        TextView tvVal = new TextView(this);
        tvVal.setText(String.valueOf(initialVal));
        tvVal.setTextColor(Color.parseColor("#38BDF8"));
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setPadding(dp(4), 0, dp(4), 0);
        col.addView(tvVal);
        if (decId == ID_BTN_SEARCH_DEC) {
            txtSearchCount = tvVal;
        } else {
            txtReadCount = tvVal;
        }

        TextView btnInc = createMiniButton(" + ", incId);
        col.addView(btnInc);

        return col;
    }

    private TextView createMiniButton(String text, int id) {
        TextView tv = new TextView(this);
        tv.setId(id);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(5), dp(2), dp(5), dp(2));
        tv.setBackground(roundedBg(Color.parseColor("#475569"), 4));
        tv.setOnClickListener(this);
        return tv;
    }

    private void updateCooldownButtonText(int batchSize) {
        if (btnCooldownToggle == null) return;
        if (batchSize == 0) {
            btnCooldownToggle.setText("⏳ Nghỉ 15p (Cooldown): TẮT");
            btnCooldownToggle.setTextColor(Color.parseColor("#4ADE80"));
        } else {
            btnCooldownToggle.setText("⏳ Nghỉ 15p: BẬT (mỗi " + batchSize + " lượt)");
            btnCooldownToggle.setTextColor(Color.parseColor("#FBBF24"));
        }
    }

    private Button createActionButton(String text, String hexColor, int id) {
        Button btn = new Button(this);
        btn.setId(id);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setPadding(dp(6), 0, dp(6), 0);
        btn.setBackground(roundedBg(Color.parseColor(hexColor), 8));
        btn.setOnClickListener(this);
        return btn;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = layoutParams.x;
                initialY = layoutParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                if (floatingView != null && windowManager != null) {
                    windowManager.updateViewLayout(floatingView, layoutParams);
                }
                return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == ID_BTN_MIN) {
            isMinimized = !isMinimized;
            panelButtons.setVisibility(isMinimized ? View.GONE : View.VISIBLE);
            btnMinimize.setText(isMinimized ? " Mở rộng " : " Thu gọn ");
        } else if (id == ID_BTN_CLOSE) {
            stopCurrentTask();
            removeFloatingPanel();
        } else if (id == ID_BTN_SEARCH) {
            startTask("search");
        } else if (id == ID_BTN_READ) {
            startTask("read");
        } else if (id == ID_BTN_COMBO) {
            startTask("combo");
        } else if (id == ID_BTN_STOP) {
            stopCurrentTask();
            updateStatus("⏹ Đã dừng theo yêu cầu.");
        } else if (id == ID_BTN_SEARCH_DEC) {
            SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int vVal = Math.max(1, p.getInt("totalSearches", 20) - 5);
            p.edit().putInt("totalSearches", vVal).apply();
            if (txtSearchCount != null) txtSearchCount.setText(String.valueOf(vVal));
            Toast.makeText(this, "🔍 Tìm kiếm: " + vVal + " lượt", Toast.LENGTH_SHORT).show();
        } else if (id == ID_BTN_SEARCH_INC) {
            SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int vVal = Math.min(60, p.getInt("totalSearches", 20) + 5);
            p.edit().putInt("totalSearches", vVal).apply();
            if (txtSearchCount != null) txtSearchCount.setText(String.valueOf(vVal));
            Toast.makeText(this, "🔍 Tìm kiếm: " + vVal + " lượt", Toast.LENGTH_SHORT).show();
        } else if (id == ID_BTN_READ_DEC) {
            SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int vVal = Math.max(1, p.getInt("totalArticles", 10) - 2);
            p.edit().putInt("totalArticles", vVal).apply();
            if (txtReadCount != null) txtReadCount.setText(String.valueOf(vVal));
            Toast.makeText(this, "📰 Đọc báo: " + vVal + " bài", Toast.LENGTH_SHORT).show();
        } else if (id == ID_BTN_READ_INC) {
            SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int vVal = Math.min(30, p.getInt("totalArticles", 10) + 2);
            p.edit().putInt("totalArticles", vVal).apply();
            if (txtReadCount != null) txtReadCount.setText(String.valueOf(vVal));
            Toast.makeText(this, "📰 Đọc báo: " + vVal + " bài", Toast.LENGTH_SHORT).show();
        } else if (id == ID_BTN_COOLDOWN_TOGGLE) {
            SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int cur = p.getInt("batchSize", 0);
            int next = (cur == 0) ? 4 : 0;
            p.edit().putInt("batchSize", next).apply();
            updateCooldownButtonText(next);
            Toast.makeText(this, next == 0 ? "Đã TẮT nghỉ 15 phút (Chạy liên tục)" : "Đã BẬT nghỉ 15 phút mỗi 4 lượt", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopCurrentTask() {
        isRunning = false;
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }
        setRunningUI(false);
    }

    public void startTask(String mode) {
        if (isRunning) {
            Toast.makeText(this, "Đang chạy nhiệm vụ rồi!", Toast.LENGTH_SHORT).show();
            return;
        }
        updateScreenMetrics();
        currentTaskMode = mode;
        isRunning = true;
        setRunningUI(true);

        workerThread = new Thread(this);
        workerThread.start();
    }

    @Override
    public void run() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int totalSearches = prefs.getInt("totalSearches", 20);
            int batchSize = prefs.getInt("batchSize", 0); // MẶC ĐỊNH 0 = TẮT NGHỈ 15 PHÚT!
            int cooldownMinutes = prefs.getInt("cooldownMinutes", 15);
            int totalArticles = prefs.getInt("totalArticles", 10);
            int articleReadSec = prefs.getInt("articleReadSec", 10);

            if ("search".equals(currentTaskMode)) {
                restartBingApp();
                runSearchLoop(totalSearches, batchSize, cooldownMinutes);
                if (isRunning) {
                    closeBingApp();
                }
            } else if ("read".equals(currentTaskMode)) {
                restartBingApp();
                runReadToEarnLoop(totalArticles, articleReadSec);
                if (isRunning) {
                    closeBingApp();
                }
            } else if ("combo".equals(currentTaskMode)) {
                // Tách biệt hoàn toàn: Bước 1 đọc báo
                restartBingApp();
                runReadToEarnLoop(totalArticles, articleReadSec);

                if (isRunning) {
                    updateStatus("✅ Đã xong Đọc Báo! Nghỉ 3s rồi đóng app Bing...");
                    sleepMs(3000);

                    // Xóa app Bing đi rồi vào lại app trước khi Search
                    restartBingApp();
                    sleepMs(2000);

                    // Bước 2: Tìm kiếm
                    runSearchLoop(totalSearches, batchSize, cooldownMinutes);

                    if (isRunning) {
                        closeBingApp();
                    }
                }
            }

            if (isRunning) {
                updateStatus("✅ Đã hoàn thành toàn bộ nhiệm vụ!");
                vibratePhone();
            }
        } catch (Exception e) {
            if (isRunning) {
                updateStatus("⚠️ Tạm dừng: " + e.getMessage());
            }
        } finally {
            isRunning = false;
            setRunningUI(false);
        }
    }

    // ================= CÁC HÀM TƯƠNG TÁC MÀN HÌNH & BING =================

    private boolean sleepMs(long ms) {
        try {
            Thread.sleep(ms);
            return isRunning;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private boolean sleepWithCountdown(int seconds, String prefix) {
        for (int s = seconds; s > 0; s--) {
            if (!isRunning) return false;
            int min = s / 60;
            int sec = s % 60;
            String t = min > 0 ? (min + "p " + (sec < 10 ? "0" + sec : sec) + "s") : (sec + "s");
            updateStatus(prefix + " (" + t + ")");
            if (!sleepMs(1000)) return false;
        }
        return isRunning;
    }

    private void vibratePhone() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(400);
            }
        } catch (Exception ignored) {
        }
    }

    private void performTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 80));
        dispatchGesture(builder.build(), null, null);
    }

    private void performSwipe(float x1, float y1, float x2, float y2, long durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }

    private void naturalSwipeUp() {
        float x1 = screenWidth * (0.45f + random.nextFloat() * 0.10f);
        float y1 = screenHeight * (0.75f + random.nextFloat() * 0.05f);
        float x2 = screenWidth * (0.45f + random.nextFloat() * 0.10f);
        float y2 = screenHeight * (0.48f + random.nextFloat() * 0.05f);
        performSwipe(x1, y1, x2, y2, 450 + random.nextInt(250));
    }

    private void naturalSwipeDown() {
        float x1 = screenWidth * (0.45f + random.nextFloat() * 0.12f);
        float y1 = screenHeight * (0.30f + random.nextFloat() * 0.06f);
        float x2 = screenWidth * (0.45f + random.nextFloat() * 0.12f);
        float y2 = screenHeight * (0.72f + random.nextFloat() * 0.06f);
        performSwipe(x1, y1, x2, y2, 480 + random.nextInt(250));
    }

    private boolean isBingOrContentInForeground() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            sleepMs(200);
            root = getRootInActiveWindow();
        }
        if (root == null) return true; // Đang chuyển cảnh giao diện mượt, không can thiệp
        CharSequence pkgSeq = root.getPackageName();
        if (pkgSeq == null) return true;
        String pkg = pkgSeq.toString();
        // Cho phép Bing, In-app WebView/Chrome Tabs đọc báo, bàn phím ảo và giao diện hệ thống
        return BING_PACKAGE.equals(pkg) || pkg.contains("chrome") || pkg.contains("inputmethod") || pkg.contains("systemui");
    }

    private void ensureBingOpen() {
        if (!isBingOrContentInForeground()) {
            updateStatus("Đang mở ứng dụng Bing...");
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(BING_PACKAGE);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // KHÔNG dùng CLEAR_TOP để tránh reset ứng dụng
                startActivity(launchIntent);
            } else {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com"));
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(viewIntent);
            }
            sleepMs(3000);
        }
    }

    private void closeBingApp() {
        updateStatus("Đang đóng và dọn dẹp app Bing...");
        // 1. Mở màn hình Đa nhiệm (Recent Apps)
        performGlobalAction(GLOBAL_ACTION_RECENTS);
        sleepMs(1200);

        // 2. Thử tìm nút 'Xóa tất cả' / 'Clear all' trong Đa nhiệm
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean cleared = false;
        if (root != null) {
            String[] clearWords = {"Xóa tất cả", "Clear all", "Đóng tất cả", "Close all", "Xóa", "Dọn dẹp", "Clear"};
            for (String w : clearWords) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(w);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo n : nodes) {
                        tapNodeCenter(n);
                        cleared = true;
                        break;
                    }
                }
                if (cleared) break;
            }
        }

        // 3. Nếu không thấy nút bấm, vuốt app Bing lên trên để tắt khỏi đa nhiệm
        if (!cleared) {
            performSwipe(screenWidth * 0.5f, screenHeight * 0.55f, screenWidth * 0.5f, screenHeight * 0.12f, 260);
            sleepMs(800);
        }

        // 4. Quay về màn hình chính (Home)
        performGlobalAction(GLOBAL_ACTION_HOME);
        sleepMs(1000);
    }

    private void restartBingApp() {
        closeBingApp();
        updateStatus("Đang mở lại ứng dụng Bing mới...");
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(BING_PACKAGE);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launchIntent);
        }
        sleepMs(4500);
        dismissPopupsIfAny();
    }

    private void dismissPopupsIfAny() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        String[] dismissWords = {"Bỏ qua", "Đóng", "Để sau", "Không, cảm ơn", "Dismiss", "Not now", "Skip", "Got it"};
        for (String w : dismissWords) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(w);
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isClickable()) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        sleepMs(400);
                        return;
                    }
                }
            }
        }
    }

    private AccessibilityNodeInfo findNodeByViewIdContains(AccessibilityNodeInfo node, String[] substrings) {
        if (node == null) return null;
        String id = node.getViewIdResourceName();
        if (id != null) {
            String lower = id.toLowerCase();
            for (String s : substrings) {
                if (lower.contains(s)) {
                    return node;
                }
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo found = findNodeByViewIdContains(node.getChild(i), substrings);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findFirstEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence cls = node.getClassName();
        if (cls != null && cls.toString().contains("EditText")) {
            return node;
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo found = findFirstEditText(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private void tapNodeCenter(AccessibilityNodeInfo node) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.width() > 10 && r.height() > 10) {
            performTap(r.centerX(), r.centerY());
        } else {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    private boolean isAtBingHome() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        String[] homeHints = {"Hỏi bất kỳ điều gì", "Ask me anything", "Tạo bằng AI", "Rewards"};
        for (String h : homeHints) {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(h);
            if (list != null && !list.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void resetToBingHome() {
        ensureBingOpen();
        if (isAtBingHome()) return;

        // 1. Thử nhấn nút Back (hoặc icon Back của Bing) tối đa 3 lần để thoát trang kết quả
        for (int i = 0; i < 3; i++) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String[] backBtnIds = {"sa_action_bar_header_back", "sa_amp_header_action_back", "btn_back", "iab_action_back"};
            AccessibilityNodeInfo backBtn = findNodeByViewIdContains(root, backBtnIds);
            if (backBtn != null) {
                tapNodeCenter(backBtn);
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
            sleepMs(800);
            if (isAtBingHome()) return;
        }

        // 2. Chạm vào tab Trang chủ (Home) ở góc dưới bên trái màn hình (Ảnh 1 & 4)
        performTap(screenWidth * 0.11f, screenHeight * 0.96f);
        sleepMs(1000);
    }

    // ================= 1. LOGIC AUTO SEARCH (MOBILE SEARCH) =================

    private void runSearchLoop(int totalSearches, int batchSize, int cooldownMinutes) {
        updateStatus("Đang nạp kho từ khóa bách khoa toàn thư...");
        keywordGenerator.refreshTrendingKeywords();

        for (int i = 1; i <= totalSearches; i++) {
            if (!isRunning) break;

            String keyword = keywordGenerator.nextKeyword();
            try {
                performSingleSearch(keyword, i, totalSearches);
            } catch (Exception e) {
                sleepMs(1200);
            }

            if (!isRunning) break;

            if (batchSize > 0 && i < totalSearches && (i % batchSize == 0)) {
                int cooldownSec = cooldownMinutes * 60;
                if (!sleepWithCountdown(cooldownSec, "⏳ Cooldown (" + i + "/" + totalSearches + ")")) {
                    break;
                }
            }
        }
    }

    private boolean performSingleSearch(String keyword, int currentIdx, int totalCount) {
        updateStatus("🔍 [" + currentIdx + "/" + totalCount + "] Tìm: " + keyword);
        ensureBingOpen();

        // 1. Chạm vào ô tìm kiếm để mở màn hình nhập (Ảnh 2 / Ảnh 3)
        // Nếu là lần đầu tiên (hoặc đang ở màn hình Trang chủ Ảnh 1):
        // -> Chạm vào ô tìm kiếm ở giữa màn hình (Ảnh 1: x ≈ 50%, y ≈ 50.5%)
        // Nếu là các lần tiếp theo (đang ở màn hình kết quả Ảnh 4):
        // -> Chạm lại vào phần tìm kiếm ở đỉnh màn hình (Ảnh 4: x ≈ 45%, y ≈ 8.8%)
        boolean atHome = isAtBingHome();
        if (currentIdx == 1 || atHome) {
            boolean tappedNode = false;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                String[] homeSearchTexts = {"Hỏi bất kỳ điều gì", "Ask me anything", "Tìm kiếm"};
                for (String txt : homeSearchTexts) {
                    List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(txt);
                    if (list != null) {
                        for (AccessibilityNodeInfo n : list) {
                            Rect r = new Rect();
                            n.getBoundsInScreen(r);
                            if (r.centerY() > screenHeight * 0.40f && r.centerY() < screenHeight * 0.65f) {
                                tapNodeCenter(n);
                                tappedNode = true;
                                break;
                            }
                        }
                    }
                    if (tappedNode) break;
                }
            }
            if (!tappedNode) {
                // Chạm trực tiếp vào giữa ô tìm kiếm Ảnh 1 (x ≈ 50%, y ≈ 50.5%)
                performTap(screenWidth * 0.50f, screenHeight * 0.505f);
            }
            sleepMs(1200);
        } else {
            // Ảnh 4: Chạm lại vào phần tìm kiếm ở đỉnh màn hình (x ≈ 45%, y ≈ 8.8%)
            performTap(screenWidth * 0.45f, screenHeight * 0.088f);
            sleepMs(1000);
        }

        if (!isRunning) return false;

        // 2. Màn hình nhập tìm kiếm (Ảnh 2 / Ảnh 3):
        // QUAN TRỌNG: Ở LẦN TÌM KIẾM ĐẦU TIÊN (currentIdx == 1), KHÔNG ĐƯỢC ẤN DẤU X!
        // Vì lần đầu tiên ô tìm kiếm đang trống, vị trí x=89%, y=8.8% chính là nút GHI ÂM (Microphone).
        // Chỉ ấn nút xóa (X) từ lần tìm kiếm thứ 2 trở đi (currentIdx > 1) khi đã có từ khóa cũ hiển thị!
        if (currentIdx > 1) {
            performTap(screenWidth * 0.890f, screenHeight * 0.088f);
            sleepMs(200);
        }

        AccessibilityNodeInfo inputRoot = getRootInActiveWindow();
        AccessibilityNodeInfo editBox = findFirstEditText(inputRoot);
        if (editBox == null) {
            // Chạm nhẹ vào ô nhập để kích hoạt focus
            performTap(screenWidth * 0.45f, screenHeight * 0.088f);
            sleepMs(400);
            inputRoot = getRootInActiveWindow();
            editBox = findFirstEditText(inputRoot);
        }

        if (editBox != null) {
            // Xóa sạch chữ cũ trong ô
            Bundle emptyArgs = new Bundle();
            emptyArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            editBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            editBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, emptyArgs);
            sleepMs(150);

            // Gõ từ khóa tìm kiếm như Ảnh 3
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword);
            boolean setSuccess = editBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

            // Dự phòng: Paste qua Clipboard nếu máy chặn set_text
            if (!setSuccess || editBox.getText() == null || editBox.getText().length() == 0) {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("kw", keyword);
                        clipboard.setPrimaryClip(clip);
                        editBox.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    }
                } catch (Exception ignored) {
                }
            }
        } else {
            // Dự phòng nhập qua Clipboard
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("kw", keyword);
                    clipboard.setPrimaryClip(clip);
                }
            } catch (Exception ignored) {
            }
        }

        // Chờ từ khóa hiện và danh sách gợi ý xổ xuống như Ảnh 3
        sleepMs(650);
        if (!isRunning) return false;

        // 3. Ấn tìm kiếm để sang Ảnh 4:
        // Chạm dòng gợi ý đầu tiên ngay dưới thanh tìm kiếm (x ≈ 40%, y ≈ 17.0%)
        performTap(screenWidth * 0.40f, screenHeight * 0.170f);
        sleepMs(350);

        // Đồng thời chạm nút Tìm kiếm màu xanh ở góc dưới phải bàn phím ảo (Ảnh 3: x ≈ 91.6%, y ≈ 90.6%)
        performTap(screenWidth * 0.916f, screenHeight * 0.906f);

        // Chờ trang kết quả tải xong (Ảnh 4)
        sleepMs(2500);
        if (!isRunning) return false;

        // 4. Ở màn hình kết quả Ảnh 4:
        // Cuộn xem kết quả tự nhiên như người thật trong khoảng thời gian đã cài đặt (7 - 10 giây)
        int waitSec = 7 + random.nextInt(4); // 7, 8, 9, 10s
        int halfSec = Math.max(3, waitSec / 2);

        if (!sleepWithCountdown(halfSec, "🔍 [" + currentIdx + "/" + totalCount + "] Đang xem kết quả")) {
            return false;
        }
        naturalSwipeUp();
        sleepMs(800);
        if (random.nextBoolean()) {
            naturalSwipeUp();
        }
        if (!sleepWithCountdown(waitSec - halfSec, "🔍 [" + currentIdx + "/" + totalCount + "] Đang xem kết quả")) {
            return false;
        }

        // GIỮ NGUYÊN MÀN HÌNH KẾT QUẢ (ẢNH 4) CHO LẦN CHẠY TIẾP THEO
        // Ở lần sau (currentIdx + 1), hàm sẽ ấn lại vào thanh tìm kiếm ở đỉnh màn hình để tiếp tục!
        sleepMs(300);
        return true;
    }

    // ================= 2. LOGIC ĐỌC BÁO (READ TO EARN - 30 ĐIỂM) =================

    private void runReadToEarnLoop(int totalArticles, int articleReadSec) {
        updateStatus("📰 Chuẩn bị đọc báo (" + totalArticles + " bài)...");
        resetToBingHome();
        sleepMs(1500);

        naturalSwipeUp();
        sleepMs(1200);

        for (int a = 1; a <= totalArticles; a++) {
            if (!isRunning) break;

            ensureBingOpen();
            updateStatus("📰 [" + a + "/" + totalArticles + "] Đang mở bài báo...");

            // Bấm vào bài báo trên trang tin (y ≈ 50% - 62%)
            float tapX = screenWidth * (0.35f + random.nextFloat() * 0.30f);
            float tapY = screenHeight * (0.50f + random.nextFloat() * 0.12f);
            performTap(tapX, tapY);
            sleepMs(2800);

            // Thời gian đọc bài ngẫu nhiên từ 7 đến 10 giây theo yêu cầu
            int readSec = 7 + random.nextInt(4); // 7, 8, 9, 10 giây
            int halfRead = Math.max(3, readSec / 2);

            if (!sleepWithCountdown(halfRead, "📰 [" + a + "/" + totalArticles + "] Đang đọc bài")) {
                break;
            }
            naturalSwipeUp();
            sleepMs(800);
            if (!sleepWithCountdown(readSec - halfRead, "📰 [" + a + "/" + totalArticles + "] Đang đọc bài")) {
                break;
            }

            if (!isRunning) break;

            updateStatus("📰 [" + a + "/" + totalArticles + "] Xong bài, quay lại...");
            performGlobalAction(GLOBAL_ACTION_BACK);
            sleepMs(1500);

            // Vuốt trang để cuộn sang bài báo mới tiếp theo bên dưới
            naturalSwipeUp();
            sleepMs(1000);
        }
    }
}
