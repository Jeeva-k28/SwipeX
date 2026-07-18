import subprocess
import sys

def build():
    print("Building SwipeX Desktop Standalone Executable...")
    # Compile options:
    # --onefile: pack into a single standalone SwipeX.exe
    # --noconsole: run silently as a window application (no CMD popup)
    # --collect-all customtkinter: bundles customtkinter themes, assets, and metadata
    cmd = [
        "python", "-m", "PyInstaller",
        "--noconsole",
        "--onefile",
        "--collect-all", "customtkinter",
        "--name=SwipeX",
        "app.py"
    ]
    
    try:
        subprocess.run(cmd, check=True)
        print("\n" + "=" * 50)
        print("BUILD SUCCESSFUL!")
        print("Standalone executable created: SwipeX Desktop/dist/SwipeX.exe")
        print("=" * 50)
    except subprocess.CalledProcessError as e:
        print(f"Error during compilation: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    build()
