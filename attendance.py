import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
from colorama import Fore, Style, init
from prettytable import PrettyTable
import time
import sys

# Initialize colorama
init(autoreset=True)

# Configuration
USERNAME = ""
PASSWORD = ""
LOGIN_URL = "https://maitri.bmu.edu.in/loginPage.htm"
ATTENDANCE_URL = "https://maitri.bmu.edu.in/studentCourseFileNew.htm?shwA=%2700A%27"

def setup_driver():
    """Setup optimized Chrome driver"""
    options = webdriver.ChromeOptions()
    
    # Performance optimizations
    options.add_argument("--headless")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--disable-extensions")
    options.add_argument("--disable-images")  # Don't load images
    options.add_argument("--disable-javascript")  # If JS isn't needed for scraping
    options.add_argument("--disable-plugins")
    options.add_argument("--disable-web-security")
    options.add_argument("--window-size=1920,1080")
    
    # Use webdriver-manager for automatic driver management
    service = Service(ChromeDriverManager().install())
    return webdriver.Chrome(service=service, options=options)

def login_and_scrape():
    """Main scraping function with better error handling"""
    driver = setup_driver()
    wait = WebDriverWait(driver, 10)
    
    try:
        print(f"{Fore.YELLOW}🔐 Logging in...{Style.RESET_ALL}")
        
        # Step 1: Login
        driver.get(LOGIN_URL)
        
        # Use explicit waits instead of time.sleep
        user_input = wait.until(EC.presence_of_element_located((By.NAME, "j_username")))
        pass_input = driver.find_element(By.NAME, "j_password")
        
        user_input.send_keys(USERNAME)
        pass_input.send_keys(PASSWORD)
        pass_input.submit()
        
        print(f"{Fore.YELLOW}📊 Fetching attendance data...{Style.RESET_ALL}")
        
        # Step 2: Navigate to attendance
        driver.get(ATTENDANCE_URL)
        
        # Wait for attendance table to load
        wait.until(EC.presence_of_element_located((By.ID, "attendanceDiv")))
        
        # Step 3: Scrape data
        rows = driver.find_elements(By.XPATH, "//div[@id='attendanceDiv']//table/tbody/tr[td]")
        
        if not rows:
            print(f"{Fore.RED}❌ No attendance data found{Style.RESET_ALL}")
            return []
        
        subjects = []
        for row in rows:
            cols = row.find_elements(By.TAG_NAME, "td")
            if len(cols) >= 4:
                code = cols[0].text.strip()
                name = cols[1].text.strip()
                count = cols[2].text.strip()
                percent = cols[3].text.strip()
                subjects.append((code, name, count, percent))
        
        return subjects
        
    except Exception as e:
        print(f"{Fore.RED}❌ Error: {e}{Style.RESET_ALL}")
        return []
    finally:
        driver.quit()

def display_attendance(subjects):
    """Display attendance in a formatted table"""
    if not subjects:
        print(f"{Fore.RED}No data to display{Style.RESET_ALL}")
        return
    
    table = PrettyTable()
    table.field_names = ["Code", "Subject", "Count", "Percentage"]
    
    for code, name, count, percent in subjects:
        try:
            perc_value = float(percent.replace("%", "").strip())
        except ValueError:
            perc_value = 0
        
        # Color coding
        if perc_value >= 90:
            percent_colored = f"{Fore.GREEN}{Style.BRIGHT}{percent}{Style.RESET_ALL}"
            status = "🟢"
        elif perc_value >= 80:
            percent_colored = f"{Fore.YELLOW}{Style.BRIGHT}{percent}{Style.RESET_ALL}"
            status = "🟡"
        else:
            percent_colored = f"{Fore.RED}{Style.BRIGHT}{percent}{Style.RESET_ALL}"
            status = "🔴"
        
        table.add_row([
            f"{Fore.MAGENTA}{code}{Style.RESET_ALL}",
            f"{Fore.CYAN}{name[:30]}...{Style.RESET_ALL}" if len(name) > 30 else f"{Fore.CYAN}{name}{Style.RESET_ALL}",
            f"{Fore.BLUE}{count}{Style.RESET_ALL}",
            f"{status} {percent_colored}"
        ])
    
    print(f"\n{Fore.CYAN}{Style.BRIGHT}📊 Per-Subject Attendance:\n{Style.RESET_ALL}")
    print(table)
    
    # Summary stats
    percentages = []
    for _, _, _, percent in subjects:
        try:
            percentages.append(float(percent.replace("%", "").strip()))
        except ValueError:
            continue
    
    if percentages:
        avg_attendance = sum(percentages) / len(percentages)
        print(f"\n{Fore.CYAN}📈 Average Attendance: {avg_attendance:.1f}%{Style.RESET_ALL}")

if __name__ == "__main__":
    subjects = login_and_scrape()
    display_attendance(subjects)