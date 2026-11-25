import random
import re
import requests

EDUCATIONAL_TOPICS = [
    # Hóa học & Vật liệu
    "hóa học hữu cơ là gì", "hóa học vô cơ là gì", "phản ứng oxy hóa khử là gì", "liên kết cộng hóa trị là gì",
    "liên kết ion là gì", "bảng tuần hoàn các nguyên tố hóa học", "axit và bazơ là gì", "dung dịch đệm là gì",
    "chất xúc tác là gì", "polyme là gì", "este là gì", "gluxit là gì", "protein là gì", "lipit là gì",
    "hóa học phân tích là gì", "nhiệt động lực học hóa học là gì", "đồng phân là gì", "ancol là gì", "andehit là gì",
    "kim loại kiềm là gì", "kim loại kiềm thổ là gì", "nguyên tố đất hiếm là gì", "phóng xạ hạt nhân là gì",
    "chu kỳ bán rã là gì", "chất điện ly là gì", "hóa học nano là gì", "phản ứng trùng hợp là gì",
    "axit amin là gì", "dung dịch bão hòa là gì", "độ ph là gì", "hóa trị của nguyên tố là gì",
    "liên kết hydro là gì", "chất chống oxy hóa là gì", "hóa keo là gì", "phản ứng este hóa là gì",
    "chất bán dẫn là gì", "vật liệu siêu nhẹ aerogel", "vật liệu graphene là gì", "hợp kim titan là gì",
    "vật liệu composite là gì",

    # Sinh học & Y học
    "công nghệ sinh học là gì", "quang hợp ở thực vật là gì", "hô hấp tế bào là gì", "tế bào gốc là gì",
    "cấu trúc dna và rna là gì", "đột biến gen là gì", "hệ tuần hoàn của con người", "hệ miễn dịch hoạt động như thế nào",
    "kháng sinh là gì", "vắc xin hoạt động như thế nào", "vi sinh vật học là gì", "vi khuẩn và virus khác nhau thế nào",
    "hệ tiêu hóa ở người hoạt động ra sao", "enzym là gì", "chu trình creb là gì", "di truyền học menđen là gì",
    "chọn lọc tự nhiên là gì", "thuyết tiến hóa đác-uyn", "đa dạng sinh học là gì", "hệ sinh thái là gì",
    "chuỗi thức ăn trong tự nhiên", "dự án giải mã bộ gen người", "hệ nhóm máu abo", "miễn dịch cộng đồng là gì",
    "bệnh tự miễn là gì", "liệu pháp gen là gì", "vi khuẩn có lợi đường ruột", "tế bào lympho t là gì",
    "kháng thể là gì", "quá trình nguyên phân là gì", "quá trình giảm phân là gì", "nhiễm sắc thể là gì",
    "clon sinh học là gì", "sinh thái học là gì", "hormone là gì", "chất dẫn truyền thần kinh là gì",
    "tế bào thần kinh nơron là gì", "quá trình lão hóa của tế bào", "thụ tinh nhân tạo là gì", "công nghệ crispr là gì",

    # Vật lý & Thiên văn
    "vật lý lượng tử là gì", "thuyết tương đối hẹp là gì", "thuyết tương đối tổng quát", "hố đen vũ trụ là gì",
    "vận tốc ánh sáng là bao nhiêu", "sóng điện từ là gì", "lực hấp dẫn là gì", "vật chất tối là gì",
    "năng lượng tối là gì", "vụ nổ big bang là gì", "cơ học cổ điển newton", "định luật bảo toàn năng lượng",
    "nguyên lý bất định heisenberg", "hiệu ứng doppler là gì", "sóng hấp dẫn là gì", "hạt higgs là gì",
    "quang phổ ánh sáng là gì", "từ trường trái đất là gì", "các hành tinh trong hệ mặt trời", "sao băng là gì",
    "nguyệt thực và nhật thực khác nhau thế nào", "nhiệt độ không tuyệt đối là gì", "hiện tượng khúc xạ ánh sáng",
    "hiện tượng giao thoa ánh sáng", "động năng và thế năng là gì", "nhiệt động lực học là gì",
    "hiệu ứng quang điện là gì", "chuyển động brown là gì", "hiện tượng siêu dẫn là gì", "hạt sơ cấp là gì",
    "thuyết dây trong vật lý là gì", "bức xạ hawking là gì", "năm ánh sáng là gì", "kính thiên văn james webb",
    "ngôi sao neutron là gì", "sao lùn trắng là gì", "vận tốc vũ trụ cấp 1 là gì", "trọng lực là gì",
    "quang học lượng tử là gì", "phản vật chất là gì",

    # Công nghệ thông tin & AI
    "trí tuệ nhân tạo là gì", "học máy machine learning là gì", "học sâu deep learning là gì",
    "mô hình ngôn ngữ lớn llm là gì", "xử lý ngôn ngữ tự nhiên nlp là gì", "thị giác máy tính là gì",
    "điện toán đám mây là gì", "chuỗi khối blockchain là gì", "hợp đồng thông minh smart contract là gì",
    "an ninh mạng là gì", "tường lửa firewall là gì", "mã hóa bất đối xứng là gì", "mạng di động 5g là gì",
    "mạng 6g phát triển như thế nào", "internet vạn vật iot là gì", "dữ liệu lớn big data là gì",
    "khoa học dữ liệu data science là gì", "lập trình hướng đối tượng oop là gì", "cơ sở dữ liệu quan hệ sql là gì",
    "hệ điều hành linux là gì", "thuật toán tìm kiếm nhị phân", "cấu trúc dữ liệu mảng và danh sách",
    "điện toán lượng tử là gì", "máy chủ proxy là gì", "giao thức mạng https là gì", "mạng riêng ảo vpn là gì",
    "trí tuệ nhân tạo tạo sinh generative ai", "hệ thống gợi ý recommendation system", "tự động hóa rpa là gì",
    "điện toán biên edge computing là gì", "chữ ký số là gì", "phần mềm mã nguồn mở là gì",
    "kiến trúc microservices là gì", "giao diện lập trình api là gì", "công nghệ thực tế ảo vr là gì",
    "thực tế tăng cường ar là gì", "người máy robot hình người", "phương tiện tự hành xe tự lái",
    "mô hình mạng nơron nhân tạo", "chuyển đổi số doanh nghiệp là gì", "giao thức tcp ip là gì",
    "tên miền dns hoạt động thế nào", "bộ nhớ đệm cache là gì", "tấn công từ chối dịch vụ ddos là gì",
    "tấn công giả mạo phishing là gì",

    # Môi trường, Địa lý & Địa chất
    "biến đổi khí hậu toàn cầu là gì", "hiệu ứng nhà kính là gì", "năng lượng tái tạo là gì",
    "năng lượng mặt trời hoạt động ra sao", "năng lượng gió là gì", "tầng ozon có vai trò gì",
    "ô nhiễm vi nhựa là gì", "hiện tượng el nino là gì", "hiện tượng la nina là gì",
    "sóng thần hình thành như thế nào", "núi lửa hoạt động như thế nào", "động đất xảy ra do đâu",
    "thuyết kiến tạo mảng là gì", "các tầng khí quyển của trái đất", "rừng ngập mặn có tác dụng gì",
    "phát triển bền vững là gì", "dấu chân carbon là gì", "kinh tế tuần hoàn là gì", "xâm nhập mặn là gì",
    "vòng tuần hoàn của nước là gì", "bão nhiệt đới hình thành thế nào", "thủng tầng ozon do nguyên nhân nào",
    "nước ngầm là gì", "sa mạc hóa là gì", "băng tan ở hai cực ảnh hưởng gì", "chu trình nitơ trong tự nhiên",
    "chu trình cacbon là gì", "hải lưu trong đại dương là gì", "hiện tượng thủy triều đỏ là gì",
    "mưa axit hình thành như thế nào", "độ ph của đất trồng là gì", "tài nguyên khoáng sản là gì",
    "rạn san hô có vai trò gì", "vườn quốc gia là gì", "bảo tồn động vật hoang dã",

    # Kinh tế, Tài chính & Quản trị
    "kinh tế vi mô là gì", "kinh tế vĩ mô là gì", "lạm phát là gì", "giảm phát là gì", "chỉ số gdp là gì",
    "chính sách tiền tệ là gì", "chính sách tài khóa là gì", "lãi suất cơ bản là gì",
    "thị trường chứng khoán hoạt động thế nào", "trái phiếu chính phủ là gì", "cổ phiếu và cổ tức là gì",
    "quỹ đầu tư mạo hiểm là gì", "ngân hàng trung ương có vai trò gì", "tỷ giá hối đoái là gì",
    "cán cân thương mại là gì", "chu kỳ kinh tế là gì", "thuế giá trị gia tăng vat là gì",
    "thuế thu nhập cá nhân là gì", "đòn bẩy tài chính là gì", "tính thanh khoản của tài sản là gì",
    "quản trị rủi ro tài chính là gì", "thương mại điện tử là gì", "chuỗi cung ứng toàn cầu là gì",
    "tự do hóa thương mại là gì", "tổ chức thương mại thế giới wto", "cung và cầu quyết định giá cả thế nào",
    "chi phí cơ hội là gì", "lợi thế so sánh trong thương mại", "độc quyền kinh tế là gì",
    "doanh nghiệp khởi nghiệp startup là gì", "thị trường ngoại hối forex là gì", "chỉ số cpi là gì",
    "sáp nhập và mua lại ma là gì", "tài sản vô hình là gì", "dòng tiền thông minh là gì",

    # Khoa học Xã hội, Tâm lý & Triết học
    "tâm lý học hành vi là gì", "tư duy phản biện là gì", "trí tuệ cảm xúc eq là gì", "phương pháp luận là gì",
    "chủ nghĩa duy vật biện chứng là gì", "chủ nghĩa duy tâm là gì", "quy luật mâu thuẫn là gì",
    "xã hội học là gì", "nhân học là gì", "ngôn ngữ học là gì", "khảo cổ học là gì",
    "văn hóa phi vật thể là gì", "toàn cầu hóa văn hóa là gì", "hiến pháp là gì", "quyền con người là gì",
    "luật quốc tế là gì", "tâm lý học nhận thức là gì", "hội chứng tâm lý fomo là gì",
    "hiệu ứng dunning kruger là gì", "tháp nhu cầu maslow là gì", "triết học hiện sinh là gì",
    "đạo đức học là gì", "xã hội công dân là gì", "nhà nước pháp quyền là gì", "bình đẳng giới là gì",
    "cấu trúc xã hội là gì", "dư luận xã hội là gì", "giao tiếp phi ngôn ngữ là gì",
    "trực giác con người là gì", "tiềm thức và vô thức là gì", "chứng trầm cảm là gì",
    "lo âu xã hội là gì", "tâm lý đám đông là gì", "nghệ thuật tranh biện là gì", "thuyết định kiến xã hội",

    # Toán học, Thống kê & Logic
    "xác suất thống kê là gì", "giải tích toán học là gì", "đại số tuyến tính là gì", "hình học phi euclid là gì",
    "số phức là gì", "số nguyên tố là gì", "dãy số fibonacci là gì", "tỷ lệ vàng trong nghệ thuật và tự nhiên",
    "ma trận trong toán học là gì", "không gian nhiều chiều là gì", "đạo hàm và tích phân là gì",
    "phương trình vi phân là gì", "định lý pythagore là gì", "định lý fermat lớn là gì", "toán rời rạc là gì",
    "lý thuyết đồ thị là gì", "lý thuyết trò chơi game theory là gì", "phân phối chuẩn hình chuông là gì",
    "độ lệch chuẩn là gì", "hồi quy tuyến tính là gì", "mật mã học ứng dụng toán học",
    "thuật toán rsa là gì", "logic mệnh đề là gì", "nguyên lý dirichlet là gì", "tập hợp fractal là gì",
    "nguyên lý chuồng bồ câu là gì", "hình học topo là gì", "tối ưu hóa lồi là gì", "chuỗi fourier là gì", "phương sai là gì",

    # Lịch sử, Văn hóa & Đời sống
    "cách mạng công nghiệp lần thứ tư là gì", "văn minh lưỡng hà là gì", "kim tự tháp ai cập xây dựng thế nào",
    "con đường tơ lụa cổ đại là gì", "thời kỳ phục hưng là gì", "thời kỳ khai sáng ở châu âu",
    "cách mạng công nghiệp lần thứ nhất", "chiến tranh thế giới thứ nhất và thứ hai", "tổ chức liên hợp quốc un là gì",
    "di sản thế giới unesco là gì", "trống đồng đông sơn là gì", "văn minh sông hồng là gì",
    "phong trào văn hóa đổi mới", "kiến trúc gothic là gì", "kiến trúc phục hưng là gì",
    "nông nghiệp hữu cơ là gì", "thủy canh là gì", "khí sinh học biogas là gì",
    "chế độ ăn địa trung hải là gì", "chất chống dính teflon là gì", "pin lithium ion hoạt động thế nào",
    "động cơ đốt trong hoạt động ra sao", "sợi cáp quang truyền dữ liệu như thế nào", "màn hình oled và amoled khác nhau thế nào",
    "vệ tinh địa tĩnh là gì", "đài quan sát thiên văn là gì", "tên lửa đẩy vũ trụ hoạt động thế nào",
    "trạm vũ trụ quốc tế iss là gì", "bản quyền sở hữu trí tuệ là gì", "sáng chế độc quyền là gì",
    "thương hiệu là gì", "chuỗi giá trị là gì", "chuẩn mực kế toán là gì", "kiểm toán độc lập là gì",
    "trọng tài thương mại quốc tế là gì"
]

class KeywordProvider:
    def __init__(self):
        self.used_keywords = set()
        self.pool = list(EDUCATIONAL_TOPICS)
        random.shuffle(self.pool)
        self.trending_pool = []
        self._fetch_google_trends()

    def _fetch_google_trends(self):
        try:
            resp = requests.get(
                "https://trends.google.com/trending/rss?geo=VN",
                headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
                timeout=4
            )
            if resp.status_code == 200:
                titles = re.findall(r'<title>(?!Daily Search Trends)([^<]+)</title>', resp.text)
                for t in titles:
                    clean = t.strip()
                    if clean and "Google" not in clean:
                        self.trending_pool.append(f"{clean} là gì")
        except Exception:
            pass

    def get_keyword(self) -> str:
        if self.trending_pool and random.random() < 0.25:
            candidate = self.trending_pool.pop(random.randint(0, len(self.trending_pool) - 1))
            if candidate not in self.used_keywords:
                self.used_keywords.add(candidate)
                return candidate

        if not self.pool:
            self.pool = list(EDUCATIONAL_TOPICS)
            random.shuffle(self.pool)

        kw = self.pool.pop(0)
        self.used_keywords.add(kw)
        return kw
