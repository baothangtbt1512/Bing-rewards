/**
 * ============================================================================
 * BING REWARDS MOBILE AUTO (AutoX.js / Auto.js Script)
 * Chạy độc lập trực tiếp trên điện thoại Android (Hỗ trợ Android 8.0+)
 * ============================================================================
 * Tính năng:
 *  1. Tự động Tìm kiếm (Mobile Search):
 *     - Tự lấy từ khóa xu hướng mới nhất trong ngày từ Google Trends RSS + Kho từ khóa tự nhiên
 *     - Nhận diện chính xác thanh tìm kiếm của App Bing (com.microsoft.bing)
 *     - Giả lập hành vi người dùng thật: vuốt xem kết quả, thời gian chờ ngẫu nhiên
 *     - Hỗ trợ Cooldown thông minh (nghỉ 15 phút sau mỗi 3-4 lượt tìm kiếm để tránh khóa điểm)
 *  2. Tự động Đọc báo (Read to Earn - 30 điểm / 10 bài):
 *     - Tự cuộn danh sách tin tức trên trang chủ Bing / mục Rewards
 *     - Mở từng bài báo, vuốt đọc tự nhiên 12-16 giây cho nhảy xu (+3 điểm) rồi quay lại
 *  3. Bảng điều khiển nổi (Floating Widget) ngay trên màn hình điện thoại:
 *     - Bấm chạy / tạm dừng / cài đặt thông số bất kỳ lúc nào.
 * ============================================================================
 */

"auto";

// ================= CẤU HÌNH MẶC ĐỊNH =================
var storage = storages.create("bing_rewards_auto_config");

var CONFIG = {
    bingPackage: "com.microsoft.bing",
    totalSearches: storage.get("totalSearches", 20),          // Tổng số lượt tìm kiếm (20 lượt = 60 điểm Mobile)
    batchSize: storage.get("batchSize", 4),                   // Số lượt tìm mỗi đợt trước khi nghỉ Cooldown (0 = tắt Cooldown)
    cooldownMinutes: storage.get("cooldownMinutes", 15),      // Số phút nghỉ Cooldown giữa các đợt (mặc định 15 phút)
    minSearchDelaySec: storage.get("minSearchDelaySec", 8),   // Thời gian đọc kết quả tối thiểu (giây)
    maxSearchDelaySec: storage.get("maxSearchDelaySec", 14),  // Thời gian đọc kết quả tối đa (giây)
    totalArticles: storage.get("totalArticles", 10),          // Số bài báo cần đọc (10 bài = 30 điểm)
    articleReadSec: storage.get("articleReadSec", 14)         // Thời gian vuốt đọc mỗi bài báo (giây)
};

// Trạng thái chạy
var isRunning = false;
var workerThread = null;
var usedKeywords = {};

// ================= KHO TỪ KHÓA TỰ NHIÊN PHONG PHÚ =================
var TOPICS_PREFIX = [
    "cách làm", "hướng dẫn", "kinh nghiệm", "đánh giá", "so sánh",
    "tin tức mới nhất về", "lịch sử", " lợi ích của", "top 10", "địa điểm",
    "giá cả", "xu hướng", "tổng hợp", "bí quyết", "công thức"
];

var TOPICS_CORE = [
    "phở bò Hà Nội", "cà phê sữa đá Sài Gòn", "bún chả truyền thống", "bánh mì Việt Nam",
    "du lịch Đà Lạt tự túc", "kinh nghiệm đi Phú Quốc", "vịnh Hạ Long Quảng Ninh", "phố cổ Hội An",
    "điện thoại thông minh pin trâu", "máy tính xách tay văn phòng", "tai nghe chống ồn tốt nhất",
    "trí tuệ nhân tạo AI", "lập trình Python cơ bản", "công nghệ bán dẫn", "xe điện thông minh",
    "tập thể dục buổi sáng", "chế độ ăn lành mạnh", "cách ngủ ngon giấc", "chăm sóc da mùa hè",
    "uống nước đúng cách", "chạy bộ giảm cân", "yoga tại nhà cho người mới",
    "trồng cây cảnh trong nhà", "chăm sóc mèo con", "nuôi chó cảnh chung cư",
    "sách hay nên đọc", "phim khoa học viễn tưởng", "nhạc không lời thư giãn",
    "quản lý tài chính cá nhân", "tiết kiệm tiền hiệu quả", "học tiếng Anh giao tiếp",
    "thời tiết hôm nay", "lịch thi đấu bóng đá", "công thức nấu canh chua",
    "gà nướng mật ong", "sườn xào chua ngọt", "bánh bông lan trứng muối", "trà đào cam sả",
    "du lịch Sapa mùa đông", "biển Nha Trang", "tháp Chàm Ninh Thuận", "miền Tây mùa nước nổi"
];

var TOPICS_SUFFIX = [
    "năm nay", "chi tiết nhất", "dễ hiểu", "tại nhà", "cho người mới bắt đầu",
    "tiết kiệm chi phí", "hiệu quả cao", "mới cập nhật", "được yêu thích nhất", ""
];

/**
 * Lấy thêm từ khóa xu hướng thực tế từ Google Trends RSS Việt Nam
 */
function fetchTrendingKeywords() {
    var trends = [];
    try {
        var res = http.get("https://trends.google.com/trending/rss?geo=VN", {
            headers: { "User-Agent": "Mozilla/5.0 (Linux; Android 10)" }
        });
        if (res && res.statusCode === 200) {
            var xml = res.body.string();
            var matches = xml.match(/<title>(?!Daily Search Trends)([^<]+)<\/title>/g);
            if (matches) {
                for (var i = 0; i < matches.length; i++) {
                    var clean = matches[i].replace(/<\/?title>/g, "").trim();
                    if (clean && clean.length > 2 && clean.indexOf("Google") === -1) {
                        trends.push(clean);
                        trends.push("tin tức " + clean);
                    }
                }
            }
        }
    } catch (e) {
        // Nếu mạng chậm hoặc lỗi RSS thì dùng kho từ khóa nội bộ
    }
    return trends;
}

var dynamicTrends = [];

function generateUniqueKeyword() {
    // Thử lấy từ Google Trends trước
    while (dynamicTrends.length > 0) {
        var idx = random(0, dynamicTrends.length - 1);
        var kw = dynamicTrends.splice(idx, 1)[0];
        if (!usedKeywords[kw]) {
            usedKeywords[kw] = true;
            return kw;
        }
    }

    // Tạo từ khóa ghép ngẫu nhiên tự nhiên
    for (var attempt = 0; attempt < 30; attempt++) {
        var p = TOPICS_PREFIX[random(0, TOPICS_PREFIX.length - 1)];
        var c = TOPICS_CORE[random(0, TOPICS_CORE.length - 1)];
        var s = TOPICS_SUFFIX[random(0, TOPICS_SUFFIX.length - 1)];
        var phrase = (p + " " + c + (s ? " " + s : "")).trim();
        if (!usedKeywords[phrase]) {
            usedKeywords[phrase] = true;
            return phrase;
        }
    }
    return TOPICS_CORE[random(0, TOPICS_CORE.length - 1)] + " " + random(10, 999);
}

// ================= HÀM TIỆN ÍCH GIAO DIỆN & THAO TÁC =================
var W = device.width || 1080;
var H = device.height || 1920;

function updateStatus(msg) {
    ui.run(function () {
        if (window && window.txtStatus) {
            window.txtStatus.setText(msg);
        }
    });
}

function sleepWithCountdown(totalSec, prefixText) {
    for (var s = totalSec; s > 0; s--) {
        if (!isRunning) return false;
        var min = Math.floor(s / 60);
        var sec = s % 60;
        var timeStr = min > 0
            ? (min + "p " + (sec < 10 ? "0" + sec : sec) + "s")
            : (sec + "s");
        updateStatus(prefixText + " (" + timeStr + ")");
        sleep(1000);
    }
    return isRunning;
}

/**
 * Vuốt màn hình tự nhiên giống thao tác ngón tay người thật
 */
function naturalSwipeUp() {
    var x1 = Math.floor(W * (0.42 + Math.random() * 0.16));
    var y1 = Math.floor(H * (0.70 + Math.random() * 0.08));
    var x2 = Math.floor(W * (0.44 + Math.random() * 0.14));
    var y2 = Math.floor(H * (0.28 + Math.random() * 0.08));
    var duration = random(450, 800);
    swipe(x1, y1, x2, y2, duration);
}

function naturalSwipeDown() {
    var x1 = Math.floor(W * (0.45 + Math.random() * 0.12));
    var y1 = Math.floor(H * (0.30 + Math.random() * 0.08));
    var x2 = Math.floor(W * (0.45 + Math.random() * 0.12));
    var y2 = Math.floor(H * (0.72 + Math.random() * 0.08));
    var duration = random(450, 750);
    swipe(x1, y1, x2, y2, duration);
}

/**
 * Đóng các popup quảng cáo / thông báo nếu xuất hiện trong App Bing
 */
function dismissPopupsIfAny() {
    var dismissTexts = ["Bỏ qua", "Đóng", "Để sau", "Không, cảm ơn", "Dismiss", "Not now", "Skip", "Close", "Got it"];
    for (var i = 0; i < dismissTexts.length; i++) {
        var btn = text(dismissTexts[i]).findOne(50);
        if (btn && btn.clickable()) {
            btn.click();
            sleep(500);
        }
    }
}

/**
 * Mở ứng dụng Bing và đảm bảo đang ở bên trong App
 */
function ensureBingOpen() {
    if (currentPackage() !== CONFIG.bingPackage) {
        updateStatus("Đang mở ứng dụng Bing...");
        var launched = app.launchPackage(CONFIG.bingPackage);
        if (!launched) {
            app.launchApp("Bing");
        }
        sleep(4000);
    }
    dismissPopupsIfAny();
}

/**
 * Quay về trang chủ của App Bing
 */
function goToBingHome() {
    ensureBingOpen();
    // Tìm tab Trang chủ / Home ở thanh điều hướng dưới cùng
    var homeTab = textMatches(/(Trang chủ|Home)/i).findOne(800) ||
                  descMatches(/(Trang chủ|Home)/i).findOne(800);
    if (homeTab) {
        var b = homeTab.bounds();
        click(b.centerX(), b.centerY());
        sleep(1200);
        return;
    }
    // Nếu đang ở sâu trong trang kết quả hoặc bài báo, bấm Back vài lần để về Home
    for (var i = 0; i < 3; i++) {
        if (idMatches(/.*(sa_hp_header_search_box|sa_hp_search_container|sa_search_box).*/).exists()) {
            break;
        }
        back();
        sleep(1000);
    }
}

// ================= 1. NHIỆM VỤ TÌM KIẾM (MOBILE SEARCH) =================
/**
 * Tìm và nhấn vào ô tìm kiếm của App Bing
 * Sử dụng chính xác các Resource ID trích xuất từ APK Bing + cơ chế dự phòng
 */
function focusBingSearchBar() {
    ensureBingOpen();

    // 1. Tìm theo Resource ID chính thức của Bing APK
    var searchBoxNode = idMatches(/.*(sa_hp_header_search_box|sa_hp_search_container|sa_search_box|iab_address_bar_text|url_bar|search_src_text).*/).findOne(1200);
    if (searchBoxNode) {
        var b = searchBoxNode.bounds();
        if (b.width() > 50 && b.height() > 20 && b.centerY() > 0 && b.centerY() < H * 0.6) {
            click(b.centerX(), b.centerY());
            sleep(1200);
        }
    }

    // 2. Nếu chưa thấy EditText, thử tìm theo chữ gợi ý trong thanh tìm kiếm
    if (!className("android.widget.EditText").exists()) {
        var hintNode = textMatches(/(Tìm kiếm|Search|Hỏi tôi bất cứ điều gì|Ask me anything|Nhập nội dung tìm kiếm).*/i).findOne(1000) ||
                       descMatches(/(Tìm kiếm|Search).*/i).findOne(1000);
        if (hintNode) {
            var hb = hintNode.bounds();
            click(hb.centerX(), hb.centerY());
            sleep(1200);
        }
    }

    // 3. Nếu đang ở trang kết quả tìm kiếm mà thanh địa chỉ bị ẩn khi cuộn, cuộn nhẹ xuống rồi bấm vùng thanh tìm kiếm phía trên
    if (!className("android.widget.EditText").exists()) {
        naturalSwipeDown();
        sleep(800);
        // Nhấn vào vùng thanh tìm kiếm trên cùng (tọa độ chuẩn trên trang chủ & trang kết quả Bing)
        click(Math.floor(W * 0.45), Math.floor(H * 0.14));
        sleep(1200);
    }

    return className("android.widget.EditText").findOne(2000);
}

/**
 * Thực hiện 1 lượt tìm kiếm từ khóa trên App Bing
 */
function performSingleSearch(keyword, currentIdx, totalCount) {
    updateStatus("🔍 [" + currentIdx + "/" + totalCount + "] Tìm: " + keyword);
    ensureBingOpen();

    // Luôn đưa về Trang Chủ sạch sẽ trước khi tìm kiếm
    goToBingHome();
    sleep(500);

    var editBox = focusBingSearchBar();
    if (!editBox) {
        goToBingHome();
        sleep(800);
        editBox = focusBingSearchBar();
    }

    if (editBox) {
        // Xóa sạch chữ cũ trước khi gõ từ khóa mới
        editBox.click();
        sleep(300);
        setText("");
        sleep(200);
        setText(keyword);
        sleep(700);
    } else {
        // Dự phòng: gọi trực tiếp màn hình tìm kiếm của Bing bằng Intent URL
        app.startActivity({
            action: "android.intent.action.VIEW",
            data: "https://www.bing.com/search?q=" + encodeURIComponent(keyword),
            packageName: CONFIG.bingPackage
        });
        sleep(3000);
    }

    // Nhấn nút Enter / Search trên bàn phím hoặc gợi ý đầu tiên
    var searchBtn = idMatches(/.*(iab_action_search|sa_hp_header_search_icon|search_go_btn).*/).findOne(600) ||
                    textMatches(/^(Tìm kiếm|Search|Đi|Go)$/i).findOne(600) ||
                    descMatches(/^(Tìm kiếm|Search)$/i).findOne(600);

    if (searchBtn) {
        var sb = searchBtn.bounds();
        click(sb.centerX(), sb.centerY());
    } else {
        // Nhấn phím Enter/Search (KeyCode 66) hoặc nhấn vào dòng gợi ý tìm kiếm đầu tiên
        KeyCode(66);
        sleep(600);
        // Nếu vẫn còn đang ở màn hình nhập chữ (bàn phím/EditText còn focus), nhấn vào dòng gợi ý ngay dưới thanh search
        var firstSuggestion = className("android.widget.TextView").textContains(keyword.split(" ")[0]).findOne(800);
        if (firstSuggestion && firstSuggestion.bounds().centerY() > H * 0.15 && firstSuggestion.bounds().centerY() < H * 0.45) {
            click(firstSuggestion.bounds().centerX(), firstSuggestion.bounds().centerY());
        }
    }

    // Chờ trang kết quả tải và cuộn đọc như người thật
    var waitSec = random(CONFIG.minSearchDelaySec, CONFIG.maxSearchDelaySec);
    var halfWait = Math.max(3, Math.floor(waitSec / 2));

    if (!sleepWithCountdown(halfWait, "🔍 [" + currentIdx + "/" + totalCount + "] Đang xem kết quả")) return false;
    naturalSwipeUp();
    sleep(800);
    if (Math.random() > 0.4) {
        naturalSwipeUp();
    }
    if (!sleepWithCountdown(waitSec - halfWait, "🔍 [" + currentIdx + "/" + totalCount + "] Chờ hoàn tất")) return false;

    // Thoát về lại Trang Chủ ngay sau khi đọc xong để chuẩn bị cho lượt sau
    goToBingHome();
    sleep(500);

    return true;
}

function runAllSearches() {
    updateStatus("Đang lấy từ khóa xu hướng mới nhất...");
    dynamicTrends = fetchTrendingKeywords();

    var total = CONFIG.totalSearches;
    var batch = CONFIG.batchSize;
    var cooldownSec = CONFIG.cooldownMinutes * 60;

    for (var i = 1; i <= total; i++) {
        if (!isRunning) break;

        var kw = generateUniqueKeyword();
        var ok = performSingleSearch(kw, i, total);
        if (!ok || !isRunning) break;

        // Kiểm tra nghỉ Cooldown sau mỗi đợt (nếu bật batchSize > 0 và chưa phải lượt cuối)
        if (batch > 0 && i < total && (i % batch === 0)) {
            if (!sleepWithCountdown(cooldownSec, "⏳ Nghỉ Cooldown tránh giới hạn (" + i + "/" + total + ")")) {
                break;
            }
        }
    }
}

// ================= 2. NHIỆM VỤ ĐỌC BÁO (READ TO EARN) =================
/**
 * Tự động mở từng bài báo trong danh sách tin tức của App Bing,
 * vuốt đọc 12-16 giây để nhận +3 điểm/bài rồi quay lại cuộn sang bài mới.
 */
function runReadToEarn() {
    var totalArticles = CONFIG.totalArticles;
    var readSec = CONFIG.articleReadSec;

    updateStatus("📰 Chuẩn bị đọc báo (" + totalArticles + " bài)...");
    goToBingHome();
    sleep(1500);

    // Cuộn nhẹ từ trang chủ xuống khu vực danh sách tin tức (News Feed)
    naturalSwipeUp();
    sleep(1200);

    for (var a = 1; a <= totalArticles; a++) {
        if (!isRunning) break;

        ensureBingOpen();
        updateStatus("📰 [" + a + "/" + totalArticles + "] Đang chọn bài báo...");

        // Cuộn danh sách tin tức để bài báo mới hiện vào giữa màn hình
        naturalSwipeUp();
        sleep(1200);

        // Nhấn vào bài báo ở vùng trung tâm màn hình (tránh thanh công cụ trên/dưới)
        var clickX = Math.floor(W * (0.35 + Math.random() * 0.3));
        var clickY = Math.floor(H * (0.48 + Math.random() * 0.18));
        click(clickX, clickY);
        sleep(2500);

        // Vuốt đọc nội dung bài báo nhiều lần trong khoảng thời gian cấu hình
        var steps = Math.max(3, Math.floor(readSec / 3));
        for (var step = 1; step <= steps; step++) {
            if (!isRunning) break;
            updateStatus("📰 [" + a + "/" + totalArticles + "] Đang đọc báo (" + (step * 3) + "/" + readSec + "s)");
            if (step % 3 === 0) {
                naturalSwipeDown();
            } else {
                naturalSwipeUp();
            }
            sleep(random(2600, 3400));
        }

        if (!isRunning) break;

        // Nhấn Back để quay lại danh sách tin tức
        updateStatus("📰 [" + a + "/" + totalArticles + "] Hoàn thành bài " + a + ", quay lại...");
        back();
        sleep(1800);

        // Nếu lỡ thoát khỏi Bing thì mở lại trang chủ
        if (currentPackage() !== CONFIG.bingPackage) {
            goToBingHome();
            naturalSwipeUp();
            sleep(1000);
        }
    }
}

// ================= HỘP THOẠI CÀI ĐẶT THÔNG SỐ =================
function openSettingsDialog() {
    dialogs.build({
        title: "⚙️ Cài đặt Bing Rewards Auto",
        content: "Hiện tại:\n" +
                 "• Tổng lượt Search: " + CONFIG.totalSearches + " lượt\n" +
                 "• Nghỉ Cooldown sau mỗi: " + (CONFIG.batchSize > 0 ? CONFIG.batchSize + " lượt (" + CONFIG.cooldownMinutes + " phút)" : "Tắt (chạy liên tục)") + "\n" +
                 "• Số bài báo cần đọc: " + CONFIG.totalArticles + " bài (" + CONFIG.articleReadSec + "s/bài)",
        positive: "Đổi Cài Đặt",
        negative: "Đóng",
        neutral: "Mặt định (An toàn)"
    }).on("positive", function () {
        dialogs.rawInput("1. Tổng số lượt tìm kiếm (Mobile Search):", String(CONFIG.totalSearches), function (val1) {
            if (val1 !== null && !isNaN(parseInt(val1))) {
                CONFIG.totalSearches = Math.max(1, parseInt(val1));
                storage.put("totalSearches", CONFIG.totalSearches);
            }
            dialogs.rawInput("2. Số lượt tìm mỗi đợt trước khi nghỉ Cooldown (Nhập 0 nếu muốn chạy liên tục không nghỉ 15p):", String(CONFIG.batchSize), function (val2) {
                if (val2 !== null && !isNaN(parseInt(val2))) {
                    CONFIG.batchSize = Math.max(0, parseInt(val2));
                    storage.put("batchSize", CONFIG.batchSize);
                }
                dialogs.rawInput("3. Số bài báo cần đọc (Read to Earn - thường là 10 bài):", String(CONFIG.totalArticles), function (val3) {
                    if (val3 !== null && !isNaN(parseInt(val3))) {
                        CONFIG.totalArticles = Math.max(1, parseInt(val3));
                        storage.put("totalArticles", CONFIG.totalArticles);
                    }
                    toast("Đã lưu cài đặt!");
                    updateStatus("Đã lưu: " + CONFIG.totalSearches + " search, " + CONFIG.totalArticles + " báo");
                });
            });
        });
    }).on("neutral", function () {
        CONFIG.totalSearches = 20;
        CONFIG.batchSize = 4;
        CONFIG.cooldownMinutes = 15;
        CONFIG.totalArticles = 10;
        storage.put("totalSearches", 20);
        storage.put("batchSize", 4);
        storage.put("cooldownMinutes", 15);
        storage.put("totalArticles", 10);
        toast("Đã khôi phục cài đặt an toàn mặc định!");
    }).show();
}

// ================= BẢNG ĐIỀU KHIỂN NỔI (FLOATING WINDOW) =================
var window = floaty.window(
    <card cardCornerRadius="12dp" cardElevation="6dp" bg="#EE1E293B">
        <vertical padding="10 8" w="230dp">
            <horizontal gravity="center_vertical">
                <text id="txtTitle" text="🏆 Bing Rewards Auto" textColor="#38BDF8" textStyle="bold" textSize="13sp" layout_weight="1" />
                <text id="btnMin" text=" Thu gọn " textColor="#CBD5E1" bg="#334155" textSize="10sp" padding="4 2" marginRight="4" />
                <text id="btnClose" text=" ✕ " textColor="#FCA5A5" bg="#7F1D1D" textSize="11sp" padding="5 2" />
            </horizontal>

            <text id="txtStatus" text="Sẵn sàng! Bấm nút bên dưới để chạy" textColor="#E2E8F0" textSize="11sp" marginTop="6" maxLines="2" ellipsize="end" />

            <vertical id="panelButtons" marginTop="6">
                <horizontal>
                    <button id="btnSearch" text="🔍 Search" style="Widget.AppCompat.Button.Colored" textSize="11sp" layout_weight="1" h="38dp" />
                    <button id="btnRead" text="📰 Đọc báo" style="Widget.AppCompat.Button.Colored" textSize="11sp" layout_weight="1" h="38dp" />
                </horizontal>
                <horizontal marginTop="2">
                    <button id="btnCombo" text="🚀 Chạy Tất Cả" textSize="11sp" textColor="#FFFFFF" bg="#059669" layout_weight="1" h="34dp" marginRight="4" />
                    <button id="btnSetting" text="⚙️ Cài đặt" textSize="11sp" textColor="#FFFFFF" bg="#475569" w="82dp" h="34dp" />
                </horizontal>
                <button id="btnStop" text="⏹ DỪNG LẠI" textSize="11sp" textColor="#FFFFFF" bg="#DC2626" w="*" h="34dp" marginTop="4" visibility="gone" />
            </vertical>
        </vertical>
    </card>
);

window.setPosition(30, Math.floor(H * 0.18));

// Hỗ trợ kéo thả di chuyển bảng nổi trên màn hình
var touchX = 0, touchY = 0, winX = 0, winY = 0;
window.txtTitle.setOnTouchListener(function (view, event) {
    switch (event.getAction()) {
        case event.ACTION_DOWN:
            touchX = event.getRawX();
            touchY = event.getRawY();
            winX = window.getX();
            winY = window.getY();
            return true;
        case event.ACTION_MOVE:
            window.setPosition(winX + (event.getRawX() - touchX), winY + (event.getRawY() - touchY));
            return true;
    }
    return true;
});

var isMinimized = false;
window.btnMin.click(function () {
    ui.run(function () {
        isMinimized = !isMinimized;
        window.panelButtons.setVisibility(isMinimized ? 8 : 0); // 8 = GONE, 0 = VISIBLE
        window.btnMin.setText(isMinimized ? " Mở rộng " : " Thu gọn ");
    });
});

window.btnClose.click(function () {
    isRunning = false;
    if (workerThread && workerThread.isAlive()) {
        workerThread.interrupt();
    }
    window.close();
    exit();
});

function setRunningUI(running) {
    ui.run(function () {
        window.btnSearch.setVisibility(running ? 8 : 0);
        window.btnRead.setVisibility(running ? 8 : 0);
        window.btnCombo.setVisibility(running ? 8 : 0);
        window.btnSetting.setVisibility(running ? 8 : 0);
        window.btnStop.setVisibility(running ? 0 : 8);
    });
}

function startTask(mode) {
    if (isRunning) {
        toast("Đang chạy nhiệm vụ rồi!");
        return;
    }
    isRunning = true;
    setRunningUI(true);

    workerThread = threads.start(function () {
        try {
            if (mode === "search") {
                runAllSearches();
            } else if (mode === "read") {
                runReadToEarn();
            } else if (mode === "combo") {
                runReadToEarn();
                if (isRunning) {
                    sleep(2000);
                    runAllSearches();
                }
            }
            if (isRunning) {
                updateStatus("✅ Đã hoàn thành nhiệm vụ!");
                device.vibrate(400);
                toast("🎉 Hoàn thành nhiệm vụ Bing Rewards!");
            }
        } catch (err) {
            if (isRunning) {
                updateStatus("⚠️ Dừng/Lỗi: " + err);
            }
        } finally {
            isRunning = false;
            setRunningUI(false);
        }
    });
}

window.btnSearch.click(function () {
    startTask("search");
});

window.btnRead.click(function () {
    startTask("read");
});

window.btnCombo.click(function () {
    startTask("combo");
});

window.btnSetting.click(function () {
    openSettingsDialog();
});

window.btnStop.click(function () {
    isRunning = false;
    if (workerThread && workerThread.isAlive()) {
        workerThread.interrupt();
    }
    setRunningUI(false);
    updateStatus("⏹ Đã dừng theo yêu cầu.");
    toast("Đã dừng nhiệm vụ!");
});

// Giữ script chạy nền cùng bảng nổi
setInterval(function () {}, 3000);
