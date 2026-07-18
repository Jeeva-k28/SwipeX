import ctypes
import math
import time

# Win32 SendInput Structures
LONG = ctypes.c_long
DWORD = ctypes.c_ulong
ULONG_PTR = ctypes.c_ulong

class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", LONG),
        ("dy", LONG),
        ("mouseData", DWORD),
        ("dwFlags", DWORD),
        ("time", DWORD),
        ("dwExtraInfo", ULONG_PTR)
    ]

class INPUT_UNION(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT)]

class INPUT(ctypes.Structure):
    _fields_ = [
        ("type", DWORD),
        ("union", INPUT_UNION)
    ]

# dwFlags Constants
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_WHEEL = 0x0800

class OneEuroFilter:
    """
    Adaptive low-pass filter to smooth coordinates and eliminate jitter
    without introducing lag at higher velocities.
    """
    def __init__(self, mincutoff=1.0, beta=0.015, dcutoff=1.0):
        self.mincutoff = mincutoff
        self.beta = beta
        self.dcutoff = dcutoff
        self.x_prev = None
        self.dx_prev = 0.0
        self.t_prev = None

    def reset(self):
        self.x_prev = None
        self.dx_prev = 0.0
        self.t_prev = None

    def filter(self, t, x):
        if self.x_prev is None:
            self.x_prev = x
            self.t_prev = t
            return x

        dt = t - self.t_prev
        if dt <= 0:
            return self.x_prev

        # Calculate raw speed
        dx = (x - self.x_prev) / dt
        
        # Filter speed
        tau_d = 1.0 / (2 * math.pi * self.dcutoff)
        alpha_d = 1.0 / (1.0 + tau_d / dt)
        dx_hat = alpha_d * dx + (1.0 - alpha_d) * self.dx_prev
        
        # Determine adaptive cutoff frequency
        cutoff = self.mincutoff + self.beta * abs(dx_hat)
        
        # Filter coordinate using adaptive cutoff
        tau = 1.0 / (2 * math.pi * cutoff)
        alpha = 1.0 / (1.0 + tau / dt)
        x_hat = alpha * x + (1.0 - alpha) * self.x_prev
        
        self.x_prev = x_hat
        self.dx_prev = dx_hat
        self.t_prev = t
        
        return x_hat

class MouseController:
    """
    Simulates Windows mouse events using native Win32 SendInput.
    Includes sub-pixel remainder accumulation to guarantee no movements are lost.
    """
    def __init__(self):
        self.filter_x = OneEuroFilter(mincutoff=0.8, beta=0.02, dcutoff=1.0)
        self.filter_y = OneEuroFilter(mincutoff=0.8, beta=0.02, dcutoff=1.0)
        self.dx_remainder = 0.0
        self.dy_remainder = 0.0

    def reset_filters(self):
        self.filter_x.reset()
        self.filter_y.reset()
        self.dx_remainder = 0.0
        self.dy_remainder = 0.0

    def _send_input(self, dx, dy, mouse_data, dw_flags):
        extra = ctypes.c_ulong(0)
        mi = MOUSEINPUT(dx, dy, mouse_data, dw_flags, 0, ctypes.addressof(extra))
        union = INPUT_UNION(mi=mi)
        inp = INPUT(type=0, union=union) # type=0 is INPUT_MOUSE
        ctypes.windll.user32.SendInput(1, ctypes.pointer(inp), ctypes.sizeof(inp))

    def move(self, dx, dy):
        t = time.perf_counter()
        # Smooth delta velocity
        smooth_dx = self.filter_x.filter(t, dx)
        smooth_dy = self.filter_y.filter(t, dy)
        
        # Accumulate float remainders to support high precision slow movement
        self.dx_remainder += smooth_dx
        self.dy_remainder += smooth_dy
        
        move_x = int(self.dx_remainder)
        move_y = int(self.dy_remainder)
        
        self.dx_remainder -= move_x
        self.dy_remainder -= move_y
        
        if move_x != 0 or move_y != 0:
            self._send_input(move_x, move_y, 0, MOUSEEVENTF_MOVE)

    def scroll(self, dy):
        # Windows scroll event (negative moves cursor down/scrolls out, positive scrolls in)
        # Scale scroll delta by standard factors
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
