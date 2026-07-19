import os
import sys
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
    # Primary interface discovery trick
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        primary_ip = s.getsockname()[0]
        ips.append(primary_ip)
        s.close()
    except Exception:
        pass

    # Generic discovery of other interfaces
    try:
        host_name = socket.gethostname()
        _, _, ip_list = socket.gethostbyname_ex(host_name)
        for ip in ip_list:
            if not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except Exception:
        pass

    # Prioritize typical local subnets (192.168.x.x and 10.x.x.x) over virtual adapters (e.g. WSL/Docker)
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
    
    # Close any existing active connection cleanly to avoid leaks
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
    
    # Update GUI status in main thread
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
    except Exception as e:
        print(f"WebSocket processing error: {e}")
    finally:
        if active_websocket == websocket:
            is_connected = False
            active_websocket = None
            client_host = "Unknown"
            mouse_controller.reset_filters()
            if app_instance:
                app_instance.update_connection_status(False)

# Uvicorn WebSocket Server Thread
class WebSocketServerThread(threading.Thread):
    def __init__(self, app, port=18888):
        super().__init__(daemon=True)
        self.port = port
        self.config = uvicorn.Config(app, host="0.0.0.0", port=port, log_level="info")
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
            if not is_connected:
                # Broadcast local IPs so the phone can find us
                local_ips = get_local_ips()
                for ip in local_ips:
                    message = f"SwipeXServer:{ip}:18888"
                    try:
                        sock.sendto(message.encode(), ("255.255.255.255", self.port))
                    except Exception:
                        pass
            time.sleep(2.0)
        sock.close()

    def stop(self):
        self.running = False

# customtkinter App Window
class SwipeXApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        global app_instance
        app_instance = self

        self.title("SwipeX Desktop Server")
        self.geometry("450x570")
        self.resizable(False, False)
        
        # Center the window
        screen_width = self.winfo_screenwidth()
        screen_height = self.winfo_screenheight()
        x = (screen_width - 450) // 2
        y = (screen_height - 570) // 2
        self.geometry(f"450x570+{x}+{y}")

        # Intercept window close
        self.protocol("WM_DELETE_WINDOW", self.hide_window)

        # Retrieve network interfaces
        self.ip_addresses = get_local_ips()
        self.selected_ip = self.ip_addresses[0]

        self.build_ui()
        self.start_backend_servers()
        self.start_tray_icon()

    def build_ui(self):
        # Header Label
        self.header_label = ctk.CTkLabel(
            self, 
            text="SwipeX Touchpad Server", 
            font=ctk.CTkFont(size=22, weight="bold")
        )
        self.header_label.pack(pady=(20, 10))

        # Connection Status Container
        self.status_frame = ctk.CTkFrame(self, fg_color="#1E1E24", corner_radius=16)
        self.status_frame.pack(fill="x", padx=30, pady=10)

        self.status_title = ctk.CTkLabel(
            self.status_frame, 
            text="STATUS", 
            font=ctk.CTkFont(size=10, weight="bold")
        )
        self.status_title.pack(anchor="w", padx=16, pady=(10, 2))

        self.status_text_label = ctk.CTkLabel(
            self.status_frame, 
            text="Disconnected", 
            text_color="#FF3B30", 
            font=ctk.CTkFont(size=16, weight="bold")
        )
        self.status_text_label.pack(anchor="w", padx=16, pady=(0, 10))

        # QR Code Display Box
        self.qr_frame = ctk.CTkFrame(self, fg_color="#1E1E24", corner_radius=16)
        self.qr_frame.pack(fill="both", expand=True, padx=30, pady=10)

        self.qr_title = ctk.CTkLabel(
            self.qr_frame, 
            text="SCAN QR TO PAIR", 
            font=ctk.CTkFont(size=10, weight="bold")
        )
        self.qr_title.pack(anchor="w", padx=16, pady=(10, 2))

        self.qr_label = ctk.CTkLabel(self.qr_frame, text="")
        self.qr_label.pack(pady=10)

        # Network interface dropdown selection
        self.dropdown_frame = ctk.CTkFrame(self.qr_frame, fg_color="transparent")
        self.dropdown_frame.pack(fill="x", padx=16, pady=(0, 15))

        self.ip_title = ctk.CTkLabel(
            self.dropdown_frame, 
            text="IP Address:", 
            font=ctk.CTkFont(size=14, weight="bold")
        )
        self.ip_title.pack(side="left", padx=5)

        self.ip_dropdown = ctk.CTkOptionMenu(
            self.dropdown_frame,
            values=self.ip_addresses,
            command=self.on_ip_changed,
            font=ctk.CTkFont(size=14, weight="bold")
        )
        self.ip_dropdown.pack(side="right", fill="x", expand=True, padx=5)
        self.ip_dropdown.set(self.selected_ip)

        # Bottom Actions Layout
        self.actions_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.actions_frame.pack(fill="x", padx=30, pady=(10, 20))

        self.tray_button = ctk.CTkButton(
            self.actions_frame, 
            text="Minimize to Tray", 
            command=self.hide_window
        )
        self.tray_button.pack(side="left", fill="x", expand=True, padx=(0, 5))

        self.quit_button = ctk.CTkButton(
            self.actions_frame, 
            text="Exit Server", 
            fg_color="#FF3B30", 
            hover_color="#D32F2F", 
            command=self.quit_app
        )
        self.quit_button.pack(side="right", fill="x", expand=True, padx=(5, 0))

        # Load first QR code
        self.generate_qr_code()

    def generate_qr_code(self):
        # Generate connection payload containing ws host
        payload = f"{self.selected_ip}:18888"
        qr = qrcode.QRCode(version=1, box_size=4, border=1)
        qr.add_data(payload)
        qr.make(fit=True)
        
        # Style QR code for UI
        qr_img = qr.make_image(fill_color="white", back_color="#1E1E24").convert("RGB")
        # Resize image for display
        qr_img = qr_img.resize((180, 180), Image.Resampling.LANCZOS)
        self.qr_image_tk = ctk.CTkImage(
            light_image=qr_img, 
            dark_image=qr_img, 
            size=(180, 180)
        )
        self.qr_label.configure(image=self.qr_image_tk)

    def on_ip_changed(self, choice):
        self.selected_ip = choice
        self.generate_qr_code()

    def update_connection_status(self, connected, client_ip=None):
        if connected:
            self.status_text_label.configure(
                text=f"Connected to {client_ip}", 
                text_color="#34C759"
            )
        else:
            self.status_text_label.configure(
                text="Disconnected (Waiting...)", 
                text_color="#FF3B30"
            )

    def start_backend_servers(self):
        # Start WebSocket server
        self.websocket_thread = WebSocketServerThread(fastapi_app, port=18888)
        self.websocket_thread.start()

        # Start UDP Discovery Broadcaster
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
        # Stop background threads
        if hasattr(self, "udp_thread"):
            self.udp_thread.stop()
        if hasattr(self, "websocket_thread"):
            self.websocket_thread.stop()
        if hasattr(self, "tray"):
            self.tray.stop()
        
        # Shutdown Tkinter
        self.destroy()
        sys.exit(0)

if __name__ == "__main__":
    app = SwipeXApp()
    app.mainloop()
