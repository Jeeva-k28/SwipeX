import os
import threading
import win32api
import win32con
import win32gui

class TrayIcon:
    """
    Native Win32 system tray icon implementation.
    Runs in its own background thread to prevent GUI freezing.
    """
    def __init__(self, title, on_show_window, on_exit):
        self.title = title
        self.on_show_window = on_show_window
        self.on_exit = on_exit
        self.hwnd = None
        self.thread = None
        self.nid = None

    def start(self):
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self):
        # Register window class for message processing
        wc = win32gui.WNDCLASS()
        wc.hInstance = win32gui.GetModuleHandle(None)
        wc.lpszClassName = "SwipeXTrayWindow"
        wc.style = win32con.CS_VREDRAW | win32con.CS_HREDRAW
        wc.lpfnWndProc = self._wnd_proc
        
        try:
            class_atom = win32gui.RegisterClass(wc)
        except Exception:
            class_atom = "SwipeXTrayWindow"

        # Create hidden window
        self.hwnd = win32gui.CreateWindow(
            class_atom,
            "SwipeXTrayWindow",
            win32con.WS_OVERLAPPED | win32con.WS_SYSMENU,
            0, 0, 100, 100,
            0, 0, wc.hInstance, None
        )
        win32gui.UpdateWindow(self.hwnd)

        # Set up tray icon
        self._add_icon()

        # Run windows message loop
        win32gui.PumpMessages()

    def _add_icon(self):
        # Extract a mouse icon from shell32.dll (index 18)
        hicon = None
        try:
            shell_dll = os.path.join(win32api.GetSystemDirectory(), "shell32.dll")
            hicon = win32gui.ExtractIconEx(shell_dll, 18, 1)[0][0]
        except Exception:
            hicon = win32gui.LoadIcon(0, win32con.IDI_APPLICATION)

        flags = win32gui.NIF_ICON | win32gui.NIF_MESSAGE | win32gui.NIF_TIP
        self.nid = (self.hwnd, 0, flags, win32con.WM_USER + 20, hicon, self.title)
        win32gui.Shell_NotifyIcon(win32gui.NIM_ADD, self.nid)

    def _wnd_proc(self, hwnd, msg, wparam, lparam):
        if msg == win32con.WM_DESTROY:
            if self.nid:
                win32gui.Shell_NotifyIcon(win32gui.NIM_DELETE, self.nid)
            win32gui.PostQuitMessage(0)
            return True
            
        elif msg == win32con.WM_USER + 20: # Tray notification event
            if lparam == win32con.WM_RBUTTONUP:
                self._show_menu()
            elif lparam == win32con.WM_LBUTTONDBLCLK:
                self.on_show_window()
            return True
            
        elif msg == win32con.WM_COMMAND:
            cmd_id = win32api.LOWORD(wparam)
            if cmd_id == 1001: # Open UI
                self.on_show_window()
            elif cmd_id == 1002: # Exit
                win32gui.DestroyWindow(self.hwnd)
                self.on_exit()
            return True
            
        return win32gui.DefWindowProc(hwnd, msg, wparam, lparam)

    def _show_menu(self):
        menu = win32gui.CreatePopupMenu()
        win32gui.AppendMenu(menu, win32con.MF_STRING, 1001, "Open SwipeX")
        win32gui.AppendMenu(menu, win32con.MF_SEPARATOR, 0, "")
        win32gui.AppendMenu(menu, win32con.MF_STRING, 1002, "Exit")
        
        pos = win32gui.GetCursorPos()
        win32gui.SetForegroundWindow(self.hwnd)
        win32gui.TrackPopupMenu(menu, win32con.TPM_LEFTALIGN, pos[0], pos[1], 0, self.hwnd, None)
        win32gui.PostMessage(self.hwnd, win32con.WM_NULL, 0, 0)

    def stop(self):
        if self.hwnd:
            win32gui.PostMessage(self.hwnd, win32con.WM_CLOSE, 0, 0)
