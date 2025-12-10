# <bitbar.title>BMU Attendance</bitbar.title>
# <bitbar.version>v1.3</bitbar.version>
# <bitbar.author>Ravindra Reddy</bitbar.author>
# <bitbar.desc>Shows college attendance with abbreviations and preserved hyphens</bitbar.desc>

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
import time
import re

# --- CONFIGURATION ---
USERNAME = ""
PASSWORD = ""

LOGIN_URL = "https://maitri.bmu.edu.in/loginPage.htm"
ATTENDANCE_URL = "https://maitri.bmu.edu.in/studentCourseFileNew.htm?shwA=%2700A%27"
# ---------------------

def get_abbreviation(course_name):
    """
    Generates an abbreviation:
    - Preserves hyphens in words like "Course-II" -> "C-II".
    - Checks for Roman Numerals (I to V) and keeps them.
    - Otherwise takes the first letter of capitalized words.
    """
    if not course_name:
        return ""

    words = course_name.split()
    abbr = ""
    
    # Regex for Roman Numerals (I, II, III, IV, V) - Case insensitive
    roman_pattern = re.compile(r"^(I|II|III|IV|V)$", re.IGNORECASE)

    for word in words:
        # Check if the word itself has a hyphen (e.g., "Course-II")
        if '-' in word:
            parts = word.split('-')
            processed_parts = []
            for part in parts:
                clean_part = part.strip()
                # Process each part of the hyphenated word
                if roman_pattern.match(clean_part):
                    processed_parts.append(clean_part.upper())
                elif clean_part and clean_part[0].isupper():
                    processed_parts.append(clean_part[0])
            
            # Join them back with the hyphen
            if processed_parts:
                abbr += "-".join(processed_parts)
                
        else:
            # Standard processing for non-hyphenated words
            clean_word = word.strip()
            if roman_pattern.match(clean_word):
                abbr += clean_word.upper()
            elif clean_word and clean_word[0].isupper():
                abbr += clean_word[0]
            
    return abbr

def main():
    options = webdriver.ChromeOptions()
    options.add_argument("--headless")
    options.add_argument("--disable-gpu")
    options.add_argument("--no-sandbox")
    options.add_argument("--log-level=3")

    driver = webdriver.Chrome(options=options)
    
    subjects = []
    total_attended = 0
    total_delivered = 0
    error_msg = None

    try:
        driver.get(LOGIN_URL)
        time.sleep(1)
        
        driver.find_element(By.NAME, "j_username").send_keys(USERNAME)
        p = driver.find_element(By.NAME, "j_password")
        p.send_keys(PASSWORD)
        p.send_keys(Keys.RETURN)
        
        time.sleep(2)

        driver.get(ATTENDANCE_URL)
        time.sleep(2)

        rows = driver.find_elements(By.XPATH, "//div[@id='attendanceDiv']//table/tbody/tr[td]")

        for row in rows:
            cols = row.find_elements(By.TAG_NAME, "td")
            if len(cols) >= 4:
                name = cols[1].text.strip()
                count_str = cols[2].text.strip()
                percent_str = cols[3].text.strip()

                abbr_name = get_abbreviation(name)

                try:
                    p_val = float(percent_str.replace("%", ""))
                except:
                    p_val = 0.0

                try:
                    if "/" in count_str:
                        attended, delivered = map(int, count_str.split("/"))
                        total_attended += attended
                        total_delivered += delivered
                except:
                    pass 

                subjects.append({
                    "abbr": abbr_name,
                    "full_name": name,
                    "count": count_str,
                    "percent_str": percent_str,
                    "p_val": p_val
                })

    except Exception as e:
        error_msg = str(e)
    finally:
        driver.quit()

    # --- OUTPUT SECTION ---
    
    if error_msg:
        print("⚠ Error | color=red")
        print("---")
        print(f"Details: {error_msg}")
        return

    overall_percent = 0
    if total_delivered > 0:
        overall_percent = (total_attended / total_delivered) * 100
    
    # Main Menu Bar
    print(f"Attendance: {overall_percent:.1f}%")
    
    # Dropdown
    print("---")
    print("Refresh... | refresh=true")
    print("---")
    
    for sub in subjects:
        text_display = f"{sub['abbr']}: {sub['percent_str']} ({sub['count']})"
        
        if sub['p_val'] < 70:
            print(f"{text_display} | color=red font=Menlo")
        else:
            print(f"{text_display} | font=Menlo")
        
        print(f"-- {sub['full_name']} | color=gray size=10")

if __name__ == "__main__":
    main()