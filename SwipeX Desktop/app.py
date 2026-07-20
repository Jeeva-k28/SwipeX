import os
import sys
import io

# Redirect stdout/stderr if None (PyInstaller --noconsole execution fix)
if sys.stdout is None:
    sys.stdout = io.StringIO()
if sys.stderr is None:
    sys.stderr = io.StringIO()
import time
import socket
import threading
import asyncio
import qrcode
import customtkinter as ctk
from PIL import Image, ImageTk
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import uvicorn

# Set customtkinter appearance
ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")

# Resolve the logo path (works both in source and PyInstaller --onefile mode)
def _get_resource_path(relative_name: str) -> str:
    """Return absolute path to a bundled resource, works for PyInstaller and dev mode."""
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_name)
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), relative_name)

LOGO_PATH = _get_resource_path("swipex_logo.png")
ICO_PATH  = _get_resource_path("swipex_icon.ico")

# Import native controller and tray
from mouse_controller import MouseController
from tray_icon import TrayIcon

# FastAPI application configuration
fastapi_app = FastAPI()
mouse_controller = MouseController()

# Connection states
is_connected = False
client_host = "Unknown"
active_websocket = None
app_instance = None  # Reference to the main GUI app

# Connection ID converter helper
def ip_to_id(ip):
    try:
        parts = [int(p) for p in ip.split('.')]
        if len(parts) == 4:
            return f"{parts[0]:02X}{parts[1]:02X}{parts[2]:02X}{parts[3]:02X}"
    except Exception:
        pass
    return ""

# Retrieve local IP addresses
def get_local_ips():
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        primary_ip = s.getsockname()[0]
        ips.append(primary_ip)
        s.close()
    except Exception:
        pass

    try:
        host_name = socket.gethostname()
        _, _, ip_list = socket.gethostbyname_ex(host_name)
        for ip in ip_list:
            if not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except Exception:
        pass

    def ip_priority(ip):
        if ip.startswith("192.168."):
            return 0
        elif ip.startswith("10."):
            return 1
        elif ip.startswith("172.") and not (ip.startswith("172.17.") or ip.startswith("172.18.") or ip.startswith("172.19.") or ip.startswith("172.2") or ip.startswith("172.30.") or ip.startswith("172.31.")):
            return 2
        else:
            return 3

    try:
        ips.sort(key=ip_priority)
    except Exception:
        pass

    if not ips:
        ips.append("127.0.0.1")
    return ips

@fastapi_app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    global is_connected, client_host, active_websocket
    
    if active_websocket:
        try:
            await active_websocket.close(code=1000, reason="New connection accepted")
        except Exception:
            pass
            
    await websocket.accept()
    
    is_connected = True
    active_websocket = websocket
    client_host = websocket.client.host
    mouse_controller.reset_filters()
    
    if app_instance:
        app_instance.update_connection_status(True, client_host)
 
    try:
        while True:
            data = await websocket.receive_text()
            parts = data.strip().split(",")
            if not parts:
                continue
                
            cmd = parts[0]
            if cmd == "m" and len(parts) == 3:
                try:
                    dx = float(parts[1])
                    dy = float(parts[2])
                    mouse_controller.move(dx, dy)
                except ValueError:
                    pass
            elif cmd == "c" and len(parts) == 3:
                mouse_controller.click(parts[1], parts[2])
            elif cmd == "s" and len(parts) == 2:
                try:
                    dy = float(parts[1])
                    mouse_controller.scroll(dy)
                except ValueError:
                    pass
            elif cmd == "h" and len(parts) == 2:
                try:
                    dx = float(parts[1])
                    mouse_controller.horizontal_scroll(dx)
                except ValueError:
                    pass
            elif cmd == "z" and len(parts) == 2:
                mouse_controller.zoom(parts[1])
            elif cmd == "g" and len(parts) == 2:
                mouse_controller.gesture(parts[1])
    except WebSocketDisconnect:
        pass
    finally:
        is_connected = False
        active_websocket = None
        if app_instance:
            app_instance.update_connection_status(False)

# Uvicorn WebSocket Server Thread
class WebSocketServerThread(threading.Thread):
    def __init__(self, app, port=18888):
        super().__init__(daemon=True)
        self.port = port
        self.config = uvicorn.Config(app, host="0.0.0.0", port=port, log_level="warning", log_config=None)
        self.server = uvicorn.Server(self.config)
        self.loop = None
 
    def run(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self.server.serve())
 
    def stop(self):
        if self.server:
            self.server.should_exit = True
            if self.loop and self.loop.is_running():
                self.loop.stop()

# UDP Beacon Broadcaster Thread
class UdpBeaconThread(threading.Thread):
    def __init__(self, port=18889):
        super().__init__(daemon=True)
        self.port = port
        self.running = True

    def run(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        while self.running:
            try:
                msg = b"SWIPEX_BEACON_18888"
                sock.sendto(msg, ('<broadcast>', self.port))
            except Exception:
                pass
            time.sleep(2.0)

    def stop(self):
        self.running = False

# Main CustomTkinter Application Class (Matching Image 2 Reference UI)
class SwipeXApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        global app_instance
        app_instance = self

        self.title("SwipeX Server")
        self.geometry("640x510")
        self.resizable(False, False)
        
        # Center window on screen
        screen_width = self.winfo_screenwidth()
        screen_height = self.winfo_screenheight()
        x = (screen_width - 640) // 2
        y = (screen_height - 510) // 2
        self.geometry(f"640x510+{x}+{y}")

        # Set window icon (taskbar + titlebar)
        try:
            if os.path.exists(ICO_PATH):
                self.wm_iconbitmap(ICO_PATH)
            elif os.path.exists(LOGO_PATH):
                icon_img = Image.open(LOGO_PATH).resize((32, 32), Image.Resampling.LANCZOS)
                self._icon_photo = ImageTk.PhotoImage(icon_img)
                self.wm_iconphoto(True, self._icon_photo)
        except Exception:
            pass

        self.protocol("WM_DELETE_WINDOW", self.hide_window)

        self.ip_addresses = get_local_ips()
        self.selected_ip = self.ip_addresses[0]

        self.build_ui()
        self.start_backend_servers()
        self.start_tray_icon()

    def build_ui(self):
        self.configure(fg_color="#0D0E11")

        # Main Container (2 Columns: Left Sidebar, Right Content Card)
        self.grid_columnconfigure(0, weight=0)
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        # ----------------------------------------------------
        # Left Sidebar Panel (Matching Image 2)
        # ----------------------------------------------------
        self.sidebar_frame = ctk.CTkFrame(self, fg_color="#0D0E11", width=190, corner_radius=0)
        self.sidebar_frame.grid(row=0, column=0, sticky="nsew", padx=16, pady=16)

        # Logo Image + Title Row
        logo_row = ctk.CTkFrame(self.sidebar_frame, fg_color="transparent")
        logo_row.pack(anchor="w", padx=4, pady=(4, 0))

        # Attempt to load and show logo
        try:
            if os.path.exists(LOGO_PATH):
                _pil_logo = Image.open(LOGO_PATH).resize((36, 36), Image.Resampling.LANCZOS)
                self._ctk_logo = ctk.CTkImage(light_image=_pil_logo, dark_image=_pil_logo, size=(36, 36))
                logo_img_lbl = ctk.CTkLabel(logo_row, image=self._ctk_logo, text="")
                logo_img_lbl.pack(side="left", padx=(0, 8))
        except Exception:
            pass

        # App Title Header
        self.title_label = ctk.CTkLabel(
            logo_row,
            text="SwipeX",
            font=ctk.CTkFont(size=22, weight="bold"),
            text_color="#FFFFFF"
        )
        self.title_label.pack(side="left", anchor="w")

        self.subtitle_label = ctk.CTkLabel(
            self.sidebar_frame,
            text="Server",
            font=ctk.CTkFont(size=14, weight="normal"),
            text_color="#8A8D9B"
        )
        self.subtitle_label.pack(anchor="w", padx=8, pady=(2, 24))

        # Nav Items List
        self.nav_items = [
            ("✔  Status", True)
        ]

        for text, active in self.nav_items:
            bg_col = "#1E2028" if active else "transparent"
            txt_col = "#FFFFFF" if active else "#8A8D9B"
            btn = ctk.CTkButton(
                self.sidebar_frame,
                text=text,
                anchor="w",
                fg_color=bg_col,
                text_color=txt_col,
                hover_color="#1E2028",
                height=38,
                corner_radius=10,
                font=ctk.CTkFont(size=14, weight="bold" if active else "normal")
            )
            btn.pack(fill="x", pady=3)

        # Bottom Action: Stop Server Button
        self.stop_button = ctk.CTkButton(
            self.sidebar_frame,
            text="🔴 Stop Server",
            anchor="w",
            fg_color="transparent",
            text_color="#FF453A",
            hover_color="#2A1517",
            height=38,
            corner_radius=10,
            font=ctk.CTkFont(size=14, weight="bold"),
            command=self.quit_app
        )
        self.stop_button.pack(side="bottom", fill="x", pady=8)

        # ----------------------------------------------------
        # Right Status Content Card (Matching Image 2)
        # ----------------------------------------------------
        self.content_card = ctk.CTkFrame(self, fg_color="#16181D", corner_radius=22)
        self.content_card.grid(row=0, column=1, sticky="nsew", padx=(0, 16), pady=16)

        # Status Header Row
        self.card_header = ctk.CTkFrame(self.content_card, fg_color="transparent")
        self.card_header.pack(fill="x", padx=24, pady=(20, 12))

        self.status_dot_text = ctk.CTkLabel(
            self.card_header,
            text="🟢  Server Active",
            font=ctk.CTkFont(size=18, weight="bold"),
            text_color="#30D158"
        )
        self.status_dot_text.pack(anchor="w")

        self.address_label = ctk.CTkLabel(
            self.card_header,
            text=f"{self.selected_ip}:18888",
            font=ctk.CTkFont(size=13),
            text_color="#8A8D9B"
        )
        self.address_label.pack(anchor="w", pady=(2, 0))

        # QR Code Display Frame (Centered Rounded Frame)
        self.qr_border_frame = ctk.CTkFrame(
            self.content_card,
            fg_color="#0D0E11",
            corner_radius=16,
            border_width=1,
            border_color="#2A2D35"
        )
        self.qr_border_frame.pack(padx=24, pady=8)

        self.qr_image_label = ctk.CTkLabel(self.qr_border_frame, text="")
        self.qr_image_label.pack(padx=16, pady=16)

        # Parameter Table List
        self.table_frame = ctk.CTkFrame(self.content_card, fg_color="transparent")
        self.table_frame.pack(fill="x", padx=28, pady=(12, 16))

        # Row 1: IP Address
        self.r1_left = ctk.CTkLabel(self.table_frame, text="IP Address", font=ctk.CTkFont(size=13), text_color="#8A8D9B")
        self.r1_left.grid(row=0, column=0, sticky="w", pady=4)
        
        self.ip_dropdown = ctk.CTkOptionMenu(
            self.table_frame,
            values=self.ip_addresses,
            command=self.on_ip_changed,
            font=ctk.CTkFont(size=13, weight="bold"),
            fg_color="#1E2028",
            button_color="#2A2D35",
            width=140,
            height=26
        )
        self.ip_dropdown.grid(row=0, column=1, sticky="e", pady=4)
        self.ip_dropdown.set(self.selected_ip)

        self.table_frame.grid_columnconfigure(0, weight=1)
        self.table_frame.grid_columnconfigure(1, weight=1)

        # Row 2: Port
        self.r2_left = ctk.CTkLabel(self.table_frame, text="Port", font=ctk.CTkFont(size=13), text_color="#8A8D9B")
        self.r2_left.grid(row=1, column=0, sticky="w", pady=4)
        self.r2_right = ctk.CTkLabel(self.table_frame, text="18888", font=ctk.CTkFont(size=13, weight="bold"), text_color="#FFFFFF")
        self.r2_right.grid(row=1, column=1, sticky="e", pady=4)

        # Row 3: Status
        self.r3_left = ctk.CTkLabel(self.table_frame, text="Status", font=ctk.CTkFont(size=13), text_color="#8A8D9B")
        self.r3_left.grid(row=2, column=0, sticky="w", pady=4)
        self.r3_right = ctk.CTkLabel(self.table_frame, text="Active", font=ctk.CTkFont(size=13, weight="bold"), text_color="#30D158")
        self.r3_right.grid(row=2, column=1, sticky="e", pady=4)

        # Row 4: Clients
        self.r4_left = ctk.CTkLabel(self.table_frame, text="Clients", font=ctk.CTkFont(size=13), text_color="#8A8D9B")
        self.r4_left.grid(row=3, column=0, sticky="w", pady=4)
        self.r4_right = ctk.CTkLabel(self.table_frame, text="0", font=ctk.CTkFont(size=13, weight="bold"), text_color="#FFFFFF")
        self.r4_right.grid(row=3, column=1, sticky="e", pady=4)

        self.generate_qr_code()

    def generate_qr_code(self):
        payload = f"{self.selected_ip}:18888"
        qr = qrcode.QRCode(version=1, box_size=4, border=1)
        qr.add_data(payload)
        qr.make(fit=True)
        
        qr_img = qr.make_image(fill_color="white", back_color="#0D0E11").convert("RGB")
        qr_img = qr_img.resize((150, 150), Image.Resampling.LANCZOS)
        self.qr_image_tk = ctk.CTkImage(
            light_image=qr_img, 
            dark_image=qr_img, 
            size=(150, 150)
        )
        self.qr_image_label.configure(image=self.qr_image_tk)
        self.address_label.configure(text=f"{self.selected_ip}:18888")

    def on_ip_changed(self, choice):
        self.selected_ip = choice
        self.generate_qr_code()

    def update_connection_status(self, connected, client_ip=None):
        if connected:
            self.status_dot_text.configure(text=f"🟢  Connected to {client_ip}", text_color="#30D158")
            self.r3_right.configure(text="Connected", text_color="#30D158")
            self.r4_right.configure(text="1", text_color="#FFFFFF")
        else:
            self.status_dot_text.configure(text="🟢  Server Active", text_color="#30D158")
            self.r3_right.configure(text="Active", text_color="#30D158")
            self.r4_right.configure(text="0", text_color="#FFFFFF")

    def start_backend_servers(self):
        self.websocket_thread = WebSocketServerThread(fastapi_app, port=18888)
        self.websocket_thread.start()

        self.udp_thread = UdpBeaconThread(port=18889)
        self.udp_thread.start()

    def start_tray_icon(self):
        try:
            self.tray = TrayIcon("SwipeX Server", self.show_window, self.quit_app)
            self.tray.start()
        except Exception as e:
            print(f"Failed to start tray icon: {e}")

    def hide_window(self):
        self.withdraw()

    def show_window(self):
        self.deiconify()
        self.focus_force()

    def quit_app(self):
        if hasattr(self, "udp_thread"):
            self.udp_thread.stop()
        if hasattr(self, "websocket_thread"):
            self.websocket_thread.stop()
        if hasattr(self, "tray"):
            self.tray.stop()
        self.destroy()
        sys.exit(0)

if __name__ == "__main__":
    app = SwipeXApp()
    app.mainloop()
