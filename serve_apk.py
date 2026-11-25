import os
import sys
import io
import base64
import socket
import webbrowser
from http.server import HTTPServer, BaseHTTPRequestHandler
import qrcode

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

PORT = 8888
APK_PATH = r"D:\Projects\uprom\bing-rewards\BingRewardsAuto.apk"
if not os.path.exists(APK_PATH):
    APK_PATH = r"D:\Projects\uprom\BingRewardsAuto.apk"

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

LOCAL_IP = get_local_ip()
DOWNLOAD_URL = f"http://{LOCAL_IP}:{PORT}/BingRewardsAuto.apk"

def generate_qr_base64(data):
    qr = qrcode.QRCode(box_size=8, border=2)
    qr.add_data(data)
    qr.make(fit=True)
    img = qr.make_image(fill_color="#0F172A", back_color="#FFFFFF")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode("utf-8")

QR_BASE64 = generate_qr_base64(DOWNLOAD_URL)
FILE_SIZE_KB = round(os.path.getsize(APK_PATH) / 1024, 1) if os.path.exists(APK_PATH) else 37.3

HTML_PAGE = f"""<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tải Bing Rewards Auto APK</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            background: linear-gradient(135deg, #0F172A 0%, #1E293B 100%);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            color: #F8FAFC;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }}
        .card {{
            background: #1E293B;
            border: 1px solid #334155;
            border-radius: 20px;
            padding: 36px 32px;
            max-width: 440px;
            width: 100%;
            text-align: center;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
        }}
        .badge {{
            display: inline-block;
            background: rgba(56, 189, 248, 0.15);
            color: #38BDF8;
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 16px;
        }}
        h1 {{
            font-size: 24px;
            font-weight: 700;
            color: #FFFFFF;
            margin-bottom: 8px;
        }}
        p.subtitle {{
            color: #94A3B8;
            font-size: 14px;
            margin-bottom: 24px;
        }}
        .qr-wrapper {{
            background: #FFFFFF;
            padding: 16px;
            border-radius: 16px;
            display: inline-block;
            margin-bottom: 20px;
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
        }}
        .qr-wrapper img {{
            display: block;
            width: 240px;
            height: 240px;
        }}
        .instruction {{
            background: #0F172A;
            border: 1px solid #334155;
            border-radius: 12px;
            padding: 14px;
            font-size: 13px;
            color: #CBD5E1;
            margin-bottom: 24px;
            line-height: 1.6;
        }}
        .instruction strong {{
            color: #4ADE80;
        }}
        .btn {{
            display: block;
            width: 100%;
            background: #0284C7;
            color: #FFFFFF;
            text-decoration: none;
            padding: 14px 20px;
            border-radius: 12px;
            font-weight: 600;
            font-size: 15px;
            transition: all 0.2s;
            border: none;
            cursor: pointer;
        }}
        .btn:hover {{
            background: #0369A1;
            transform: translateY(-1px);
        }}
        .url-box {{
            margin-top: 16px;
            font-family: monospace;
            font-size: 12px;
            color: #64748B;
            word-break: break-all;
        }}
    </style>
</head>
<body>
    <div class="card">
        <div class="badge">🚀 Phiên bản mới đã cập nhật</div>
        <h1>Bing Rewards Auto</h1>
        <p class="subtitle">Dung lượng: <strong>{FILE_SIZE_KB} KB</strong> &bull; Không cần root</p>
        
        <div class="qr-wrapper">
            <img src="data:image/png;base64,{QR_BASE64}" alt="Quét mã QR để tải APK">
        </div>

        <div class="instruction">
            📱 <strong>Cách tải:</strong> Mở <strong>Camera điện thoại</strong>, <strong>Zalo</strong> hoặc <strong>Google Lens</strong> quét mã QR phía trên (yêu cầu điện thoại và máy tính chung mạng Wi-Fi).
        </div>

        <a href="/BingRewardsAuto.apk" class="btn" download>📥 Bấm để Tải APK trực tiếp</a>

        <div class="url-box">
            Đường dẫn tải: {DOWNLOAD_URL}
        </div>
    </div>
</body>
</html>
"""

class ApkHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(HTML_PAGE.encode("utf-8"))
        elif self.path == "/BingRewardsAuto.apk":
            if os.path.exists(APK_PATH):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="BingRewardsAuto.apk"')
                self.send_header("Content-Length", str(os.path.getsize(APK_PATH)))
                self.end_headers()
                with open(APK_PATH, "rb") as f:
                    self.wfile.write(f.read())
                print(f"[+] Client {self.client_address[0]} downloaded BingRewardsAuto.apk successfully!")
            else:
                self.send_error(404, "APK file not found")
        else:
            self.send_error(404, "Not Found")

    def log_message(self, format, *args):
        # Concise logging
        print(f"[{self.log_date_time_string()}] {self.client_address[0]} - {args[0]}")

def run_server():
    server = HTTPServer(("0.0.0.0", PORT), ApkHandler)
    print("=" * 60)
    print(f"🚀 SERVER TẢI APK ĐANG CHẠY!")
    print(f"👉 Local URL (Máy tính):  http://localhost:{PORT}")
    print(f"👉 Mạng LAN (Điện thoại): http://{LOCAL_IP}:{PORT}")
    print(f"👉 Link tải trực tiếp:    {DOWNLOAD_URL}")
    print("=" * 60)

    # Tự động mở trình duyệt trên máy tính
    try:
        webbrowser.open(f"http://localhost:{PORT}")
    except Exception:
        pass

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nĐã dừng server.")
        server.server_close()

if __name__ == "__main__":
    run_server()
