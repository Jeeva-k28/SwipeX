import ctypes
import time

# dwFlags Constants
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_WHEEL = 0x0800

class MouseController:
    """
    Simulates Windows mouse events using native Win32 mouse_event.
    Includes sub-pixel remainder accumulation to guarantee no movements are lost.
    """
    def __init__(self):
        self.dx_remainder = 0.0
        self.dy_remainder = 0.0

    def reset_filters(self):
        self.dx_remainder = 0.0
        self.dy_remainder = 0.0

    def _send_input(self, dx, dy, mouse_data, dw_flags):
        # Using standard mouse_event API to bypass 64-bit ctypes structure alignment issues
        ctypes.windll.user32.mouse_event(dw_flags, int(dx), int(dy), int(mouse_data), 0)

    def move(self, dx, dy):
        # Accumulate float remainders to support high precision slow movement
        self.dx_remainder += dx
        self.dy_remainder += dy
        
        move_x = int(self.dx_remainder)
        move_y = int(self.dy_remainder)
        
        self.dx_remainder -= move_x
        self.dy_remainder -= move_y
        
        if move_x != 0 or move_y != 0:
            self._send_input(move_x, move_y, 0, MOUSEEVENTF_MOVE)

    def scroll(self, dy):
        # Windows scroll event
        scroll_amount = int(round(dy * 1.5))
        if scroll_amount != 0:
            self._send_input(0, 0, scroll_amount, MOUSEEVENTF_WHEEL)

    def click(self, button, action):
        if button == "l":
            if action == "d":
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTDOWN)
            elif action == "u":
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTUP)
            elif action == "t":
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTDOWN)
                time.sleep(0.01)
                self._send_input(0, 0, 0, MOUSEEVENTF_LEFTUP)
        elif button == "r":
            if action == "d":
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTDOWN)
            elif action == "u":
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTUP)
            elif action == "t":
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTDOWN)
                time.sleep(0.01)
                self._send_input(0, 0, 0, MOUSEEVENTF_RIGHTUP)
