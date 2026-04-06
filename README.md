# 🎓 BMU Attendance Tracker

An  attendance widget for **BML Munjal University** students.

## 📋 Overview

This project is a widget that lets you check your attendance straight from you desktop instead of going through the hassle of opening the browser and logging into the portal.

---

## 🛠️ Installation & Setup

### 1. Global Prerequisites
You must have the following installed on your system before running any version:

* **Python 3.7+**
* **Google Chrome** browser (standard installation).
* **Required Python Libraries:**
    ```bash
    pip install selenium webdriver-manager colorama prettytable requests
    ```

### 2. Configure Credentials
Open the script file you intend to use (e.g., `attendance.py` or `attendance.1h.py`) in a text editor and update the configuration section at the top:

```python
# --- CONFIGURATION ---
USERNAME = "your.email@bmu.edu.in"  # Your Maitri Username
PASSWORD = "YourPassword123"        # Your Maitri Password
# ---------------------
````

-----

## 🖥️ Platform Guides

### 💻 A. CLI Version (Terminal)

#### Setup

1.  Navigate to the project directory.
2.  Run the script directly:
    ```bash
    python attendance.py
    ```

#### UNIX Users (macOS, Linux)

Instead of navigating to the folder every time, set up a global command.

**For macOS/Linux (zsh or bash):**

1.  Open your shell configuration file:
    ```bash
    nano ~/.zshrc   # or ~/.bashrc for Linux/older Mac
    ```
2.  Add this line to the bottom (replace path with your actual file path):
    ```bash
    alias attendance="python3 /Users/YOUR_NAME/Projects/Attendance/attendance.py"
    ```
3.  Save and reload:
    ```bash
    source ~/.zshrc
    ```
4.  **Usage:** Just type `attendance` in any terminal window.

#### Windows Users

1.  **Create a Batch File:**
    Open Notepad and paste the following code:
    ```batch
    @echo off
    REM Ensure 'python' is in your system's PATH, or use the full path to python.exe
    python "C:\Users\YourName\Path\To\Attendance\attendance.py" %*
    ```
    *(Replace `C:\Users\YourName\Path\To\Attendance\attendance.py` with the actual, full path to your `attendance.py` script).*
2.  **Save the File:**
    Save the file as `attendance.bat` in a new, dedicated folder (e.g., `C:\Scripts`).
3.  **Add to System PATH:**
    *   Search for "Edit the system environment variables" in the Start Menu and open it.
    *   Click the "Environment Variables..." button.
    *   Under "User variables for [Your Username]", select the `Path` variable and click "Edit...".
    *   Click "New" and add the path to your script folder (e.g., `C:\Scripts`).
    *   Click "OK" on all open windows to save the changes.
4.  **Usage:**
    Open a **new** Command Prompt or PowerShell window and simply type `attendance`.

###  B. macOS Menu Bar (SwiftBar)

#### Prerequisites

  * Download and install **[SwiftBar](https://swiftbar.app)** (Open Source).
  * or else ``` brew install swiftbar```

#### Setup Guide

1.  **Locate the Plugin Folder:**
    Open SwiftBar, click the "Open Plugin Folder..." option (or create a folder where you want your plugins to live).

2.  **Install the Script:**
    Copy the `attendance.1h.py` file into that Plugin Folder.

      * *Note:* The `.1h.` in the filename tells SwiftBar to refresh this script every **1 hour**. You can change it to `.30m.` for 30 minutes if preferred.

3.  **Permissions (Important):**
    Ensure the script is executable:

    ```bash
    chmod +x /path/to/your/SwiftBarPlugins/attendance.1h.py
    ```

4.  **Python Path Fix (If it crashes):**
    SwiftBar sometimes uses the system Python instead of your installed version. If the script fails, add the full path to your Python interpreter at the very top of the script (Shebang line):

    ```python
    #!/usr/local/bin/python3
    # OR
    #!/Users/yourname/.pyenv/shims/python
    ```

    *(Type `which python3` in your terminal to find your specific path).*

#### Visual Preview

The menu bar will show your **Overall %**. Clicking it reveals the dropdown:

![Menu Bar Preview](MacOs/image.png)

*(Example: Attendance breakdown with 'Red' alerts for low attendance subjects)*

-----



**Author:** [Kota Ravindra Reddy](https://github.com/yourusername)
