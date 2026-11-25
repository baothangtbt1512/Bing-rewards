import os
import sys
import time
import random
from typing import Dict, Any, Tuple

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

from playwright.sync_api import sync_playwright, BrowserContext, Page
from keywords import KeywordProvider

# User-Agent Edge trên nền Chromium để nhận cả điểm Edge Bonus (20đ) ngay trên Google Chrome
EDGE_ON_CHROME_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
)

class ChromeRewardBot:
    def __init__(self, account: Dict[str, Any], base_dir: str, headless: bool = False):
        self.account = account
        self.account_id = account["id"]
        self.account_name = account["name"]
        self.profile_path = os.path.join(base_dir, account["profile_dir"])
        os.makedirs(self.profile_path, exist_ok=True)
        self.headless = headless
        self.keyword_provider = KeywordProvider()

    def _create_context(self, p) -> BrowserContext:
        proxy_config = None
        if self.account.get("proxy"):
            proxy_config = {"server": self.account["proxy"]}

        context = p.chromium.launch_persistent_context(
            user_data_dir=self.profile_path,
            channel="chrome",
            headless=self.headless,
            proxy=proxy_config,
            user_agent=EDGE_ON_CHROME_UA,
            viewport={"width": 1280, "height": 820},
            args=[
                "--disable-blink-features=AutomationControlled",
                "--no-first-run",
                "--no-default-browser-check",
            ],
            ignore_default_args=["--enable-automation"],
        )
        return context

    def open_for_manual_login(self):
        """Mở trình duyệt Chrome cho người dùng đăng nhập tài khoản lần đầu"""
        print(f"\n=======================================================")
        print(f"👉 Đang mở Chrome cho: [{self.account_name}]")
        print(f"Vui lòng đăng nhập tài khoản Microsoft trên cửa sổ Chrome vừa hiện.")
        print(f"Sau khi đăng nhập xong và thấy trang Rewards, bấm ENTER tại đây để lưu lại session.")
        print(f"=======================================================\n")

        with sync_playwright() as p:
            context = self._create_context(p)
            page = context.pages[0] if context.pages else context.new_page()
            page.goto("https://rewards.bing.com", wait_until="domcontentloaded")
            input(">>> Nhấn [ENTER] khi bạn đã đăng nhập thành công vào tài khoản này... ")
            time.sleep(2)
            context.close()
        print(f"✅ Đã lưu phiên đăng nhập cho [{self.account_name}]!\n")

    def run_account_tasks(self, search_count: int = 30, do_daily_sets: bool = True) -> Tuple[int, int]:
        """Chạy nhiệm vụ cho tài khoản: Daily Set (Quiz/Poll) + PC Search"""
        points_start = 0
        points_end = 0

        print(f"\n=======================================================")
        print(f"🚀 BẮT ĐẦU CHẠY: {self.account_name}")
        print(f"=======================================================")

        with sync_playwright() as p:
            context = self._create_context(p)
            page = context.pages[0] if context.pages else context.new_page()

            # 1. Kiểm tra trạng thái đăng nhập
            page.goto("https://rewards.bing.com", wait_until="domcontentloaded")
            time.sleep(4)
            points_start = self._extract_points(page)
            print(f"💰 Điểm ban đầu: {points_start} điểm")

            # 2. Làm nhiệm vụ Daily Sets (Quiz, Polls, Thẻ +10đ)
            if do_daily_sets:
                self._solve_daily_sets(page)

            # 3. Làm nhiệm vụ Tìm kiếm PC (PC Search)
            self._perform_searches(page, search_count)

            # 4. Kiểm tra điểm sau khi hoàn thành
            page.goto("https://rewards.bing.com", wait_until="domcontentloaded")
            time.sleep(3)
            points_end = self._extract_points(page)
            earned = points_end - points_start
            print(f"\n🎉 {self.account_name} HOÀN TẤT! Điểm mới: {points_end} (+{earned} điểm)")

            time.sleep(2)
            context.close()

        return points_start, points_end

    def _extract_points(self, page: Page) -> int:
        try:
            # Thử nhiều selector lấy tổng điểm trên trang rewards.bing.com
            selectors = [
                "#id_rc",
                "span[mee-binding='points']",
                ".points-container",
                "#userPointsConfirm",
                "mee-rewards-points"
            ]
            for sel in selectors:
                el = page.query_selector(sel)
                if el:
                    txt = el.inner_text().strip().replace(",", "").replace(".", "")
                    nums = [int(s) for s in txt.split() if s.isdigit()]
                    if nums:
                        return nums[0]
                    if txt.isdigit():
                        return int(txt)
        except Exception:
            pass
        return 0

    def _solve_daily_sets(self, page: Page):
        print("\n📋 Đang kiểm tra nhiệm vụ hàng ngày (Daily Sets & More Activities)...")
        try:
            page.goto("https://rewards.bing.com", wait_until="domcontentloaded")
            time.sleep(3)

            # Tìm các thẻ hoạt động chưa hoàn thành (thẻ không có icon tick xanh)
            cards = page.query_selector_all("mee-rewards-daily-set-item-ineligible, .c-card, .promotional-item")
            print(f"👉 Tìm thấy {len(cards)} thẻ nhiệm vụ có thể làm.")

            for idx, card in enumerate(cards[:6], 1):
                try:
                    # Mở thẻ nhiệm vụ trong tab mới
                    with page.expect_popup(timeout=5000) as popup_info:
                        card.click()
                    new_tab = popup_info.value
                    new_tab.wait_for_load_state("domcontentloaded")
                    print(f"  • Mở thẻ {idx}: {new_tab.title()[:45]}...")
                    time.sleep(random.randint(4, 7))

                    # Xử lý nếu là Polls / Câu đố khảo sát (bấm vào lựa chọn đầu tiên)
                    poll_options = new_tab.query_selector_all(".btOption, .p-poll-option, input[type='radio']")
                    if poll_options:
                        poll_options[0].click()
                        time.sleep(3)

                    # Cuộn nhẹ trang để hệ thống tính lượt xem
                    new_tab.mouse.wheel(0, 300)
                    time.sleep(random.randint(3, 5))
                    new_tab.close()
                except Exception:
                    # Thẻ đã hoàn thành hoặc mở cùng tab
                    pass
            print("✅ Đã hoàn tất kiểm tra Daily Sets!")
        except Exception as e:
            print(f"⚠️ Bỏ qua Daily Sets: {e}")

    def _perform_searches(self, page: Page, total_searches: int):
        print(f"\n🔍 Bắt đầu cày {total_searches} lượt tìm kiếm PC...")
        page.goto("https://www.bing.com", wait_until="domcontentloaded")
        time.sleep(2)

        for i in range(1, total_searches + 1):
            kw = self.keyword_provider.get_keyword()
            print(f"  [{i}/{total_searches}] Tìm: '{kw}'", end="", flush=True)

            try:
                # Tìm thanh tìm kiếm của Bing
                search_box = page.wait_for_selector("#sb_form_q", timeout=6000)
                if not search_box:
                    page.goto("https://www.bing.com", wait_until="domcontentloaded")
                    search_box = page.wait_for_selector("#sb_form_q", timeout=6000)

                search_box.fill("")
                time.sleep(0.2)
                # Gõ từng phím với độ trễ người thật (human typing)
                search_box.type(kw, delay=random.randint(50, 110))
                time.sleep(0.4)
                search_box.press("Enter")

                page.wait_for_load_state("domcontentloaded", timeout=8000)
            except Exception:
                # Dự phòng: mở trực tiếp URL
                page.goto(f"https://www.bing.com/search?q={kw}", wait_until="domcontentloaded")

            # Cuộn xem kết quả ngẫu nhiên như người thật
            time.sleep(random.randint(2, 4))
            page.mouse.wheel(0, random.randint(300, 600))
            time.sleep(random.randint(2, 4))
            page.mouse.wheel(0, -random.randint(100, 300))

            # Nghỉ ngẫu nhiên giữa các lần tìm kiếm (8 - 13 giây)
            delay = random.randint(8, 13)
            print(f" -> Chờ {delay}s...")
            time.sleep(delay)

            # Nghỉ Cooldown 15 phút sau mỗi 4 lượt (nếu bật chế độ an toàn)
            # Ở tài khoản bình thường không bị giới hạn thì không cần nghỉ lâu
            if i < total_searches and i % 4 == 0:
                print(f"  ⏳ Tạm nghỉ 10 giây giữa đợt...")
                time.sleep(10)
