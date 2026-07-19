import ctypes
import time

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

class DeltaSmoother:
    """
    Exponential Moving Average (EMA) filter specifically designed for relative deltas.
    Smooths out micro-jitter while moving, but resets instantly when touch stops
    to avoid any lag or sliding effect.
    """
    def __init__(self, alpha=0.3):
        self.alpha = alpha
        self.last_dx = 0.0
        self.last_dy = 0.0

    def filter(self, dx, dy):
        if dx == 0.0 and dy == 0.0:
            self.last_dx = 0.0
            self.last_dy = 0.0
            return 0.0, 0.0
            
        smoothed_dx = self.alpha * dx + (1.0 - self.alpha) * self.last_dx
        smoothed_dy = self.alpha * dy + (1.0 - self.alpha) * self.last_dy
        
        self.last_dx = smoothed_dx
        self.last_dy = smoothed_dy
        return smoothed_dx, smoothed_dy

class MouseController:
    """
    Simulates Windows mouse and keyboard inputs using Win32 API.
    Includes sub-pixel remainder accumulation for precision movements.
    """
    def __init__(self):
        self.smoother = DeltaSmoother(alpha=0.3)
        self.dx_remainder = 0.0
        self.dy_remainder = 0.0

    def reset_filters(self):
        self.smoother = DeltaSmoother(alpha=0.3)
        self.dx_remainder = 0.0
        self.dy_remainder = 0.0

    def _send_input(self, dx, dy, mouse_data, dw_flags):
        ctypes.windll.user32.mouse_event(dw_flags, int(dx), int(dy), int(mouse_data), 0)

    def _send_key(self, vk_code, up=False):
        flags = KEYEVENTF_KEYUP if up else 0
        ctypes.windll.user32.keybd_event(vk_code, 0, flags, 0)

    def move(self, dx, dy):
        # Smooth relative velocity deltas
        smooth_dx, smooth_dy = self.smoother.filter(dx, dy)
        
        self.dx_remainder += smooth_dx
        self.dy_remainder += smooth_dy
        
        move_x = int(self.dx_remainder)
        move_y = int(self.dy_remainder)
        
        self.dx_remainder -= move_x
        self.dy_remainder -= move_y
        
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
