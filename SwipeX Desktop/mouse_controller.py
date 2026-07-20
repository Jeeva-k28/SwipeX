import ctypes
import time
import threading

# dwFlags Constants
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x01000 # Horizontal scroll wheel!

# Virtual Key Codes
VK_LWIN = 0x5B
VK_TAB = 0x09
VK_CONTROL = 0x11
VK_LEFT = 0x25
VK_RIGHT = 0x27
VK_D = 0x44

KEYEVENTF_KEYUP = 0x0002

class DynamicDeltaSmoother:
    """
    Speed-sensitive relative delta smoother.
    Adjusts alpha dynamically: slow movements get high smoothing (small alpha, e.g. 0.40)
    to kill micro-jitter/hand tremors. Fast movements get low smoothing (high alpha, e.g. 0.95)
    to guarantee zero latency.
    """
    def __init__(self, min_alpha=0.40, max_alpha=0.95, speed_scale=0.08):
        self.min_alpha = min_alpha
        self.max_alpha = max_alpha
        self.speed_scale = speed_scale
        self.last_dx = 0.0
        self.last_dy = 0.0

    def filter(self, dx, dy):
        if dx == 0.0 and dy == 0.0:
            self.last_dx = 0.0
            self.last_dy = 0.0
            return 0.0, 0.0
            
        speed = (dx * dx + dy * dy) ** 0.5
        alpha = self.min_alpha + (self.max_alpha - self.min_alpha) * (1.0 - 2.718281828459045 ** (-speed * self.speed_scale))
        
        smoothed_dx = alpha * dx + (1.0 - alpha) * self.last_dx
        smoothed_dy = alpha * dy + (1.0 - alpha) * self.last_dy
        
        self.last_dx = smoothed_dx
        self.last_dy = smoothed_dy
        return smoothed_dx, smoothed_dy

class MouseController:
    """
    Simulates Windows mouse and keyboard inputs using Win32 API.
    Drains accumulated movement targets over a high-frequency background thread (500Hz)
    to interpolate the movement between discrete network packets, resulting in butter-smooth gliding.
    """
    def __init__(self):
        self.smoother = DynamicDeltaSmoother()
        
        # Thread-safe glide accumulation buffers
        self.target_dx = 0.0
        self.target_dy = 0.0
        self.lock = threading.Lock()
        
        # Start background glide worker
        self.running = True
        self.worker = threading.Thread(target=self._glide_worker, daemon=True)
        self.worker.start()

    def reset_filters(self):
        self.smoother = DynamicDeltaSmoother()
        with self.lock:
            self.target_dx = 0.0
            self.target_dy = 0.0

    def _send_input(self, dx, dy, mouse_data, dw_flags):
        ctypes.windll.user32.mouse_event(dw_flags, int(dx), int(dy), int(mouse_data), 0)

    def _send_key(self, vk_code, up=False):
        flags = KEYEVENTF_KEYUP if up else 0
        ctypes.windll.user32.keybd_event(vk_code, 0, flags, 0)

    def move(self, dx, dy):
        # Smooth relative velocity deltas
        smooth_dx, smooth_dy = self.smoother.filter(dx, dy)
        
        # Add filtered deltas to the targets for the glide worker to consume
        with self.lock:
            self.target_dx += smooth_dx
            self.target_dy += smooth_dy

    def _glide_worker(self):
        """
        High-frequency loop (every 2ms) that drains targets using fractional steps.
        This fills in the gaps between packets, producing smooth cursor gliding.
        """
        remainder_x = 0.0
        remainder_y = 0.0
        
        while self.running:
            time.sleep(0.002) # ~500Hz update frequency
            
            with self.lock:
                current_x = self.target_dx
                current_y = self.target_dy
                
            if current_x == 0.0 and current_y == 0.0:
                continue
                
            # Drain a fraction of the remaining target (e.g. 55%)
            factor = 0.55
            step_x = current_x * factor
            step_y = current_y * factor
            
            # Clamp down small residual movements to prevent lingering glide drift
            if abs(current_x) < 0.15:
                step_x = current_x
            if abs(current_y) < 0.15:
                step_y = current_y
                
            with self.lock:
                self.target_dx -= step_x
                self.target_dy -= step_y
                
            # Handle fractional Win32 pixel steps
            remainder_x += step_x
            remainder_y += step_y
            
            move_x = int(remainder_x)
            move_y = int(remainder_y)
            
            remainder_x -= move_x
            remainder_y -= move_y
            
            if move_x != 0 or move_y != 0:
                self._send_input(move_x, move_y, 0, MOUSEEVENTF_MOVE)

    def scroll(self, dy):
        # Vertical scroll (scroll amount is relative, positive is up, negative is down)
        scroll_amount = int(round(dy * 40.0))
        if scroll_amount != 0:
            self._send_input(0, 0, scroll_amount, MOUSEEVENTF_WHEEL)

    def horizontal_scroll(self, dx):
        # Horizontal scroll (MOUSEEVENTF_HWHEEL)
        scroll_amount = int(round(dx * 40.0))
        if scroll_amount != 0:
            self._send_input(0, 0, scroll_amount, MOUSEEVENTF_HWHEEL)

    def zoom(self, zoom_type):
        # Press Ctrl, scroll, release Ctrl
        self._send_key(VK_CONTROL, up=False)
        time.sleep(0.01)
        scroll_dir = 120 if zoom_type == "in" else -120
        self._send_input(0, 0, scroll_dir, MOUSEEVENTF_WHEEL)
        time.sleep(0.01)
        self._send_key(VK_CONTROL, up=True)

    def gesture(self, name):
        if name == "taskview":
            # Win + Tab
            self._send_key(VK_LWIN, up=False)
            self._send_key(VK_TAB, up=False)
            time.sleep(0.01)
            self._send_key(VK_TAB, up=True)
            self._send_key(VK_LWIN, up=True)
        elif name == "desktop":
            # Win + D
            self._send_key(VK_LWIN, up=False)
            self._send_key(VK_D, up=False)
            time.sleep(0.01)
            self._send_key(VK_D, up=True)
            self._send_key(VK_LWIN, up=True)
        elif name == "prevdesktop":
            # Ctrl + Win + Left
            self._send_key(VK_CONTROL, up=False)
            self._send_key(VK_LWIN, up=False)
            self._send_key(VK_LEFT, up=False)
            time.sleep(0.01)
            self._send_key(VK_LEFT, up=True)
            self._send_key(VK_LWIN, up=True)
            self._send_key(VK_CONTROL, up=True)
        elif name == "nextdesktop":
            # Ctrl + Win + Right
            self._send_key(VK_CONTROL, up=False)
            self._send_key(VK_LWIN, up=False)
            self._send_key(VK_RIGHT, up=False)
            time.sleep(0.01)
            self._send_key(VK_RIGHT, up=True)
            self._send_key(VK_LWIN, up=True)
            self._send_key(VK_CONTROL, up=True)

    def click(self, button, action):
        if button == "l":
            if action == "d":
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTDOWN)
            elif action == "u":
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTUP)
            elif action == "t":
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTDOWN)
                time.sleep(0.005)
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTUP)
            elif action == "db":
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTDOWN)
                time.sleep(0.005)
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTUP)
                time.sleep(0.05)
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTDOWN)
                time.sleep(0.005)
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTUP)
        elif button == "r":
            if action == "d":
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTDOWN)
            elif action == "u":
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTUP)
            elif action == "t":
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTDOWN)
                time.sleep(0.005)
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTUP)
