import sqlite3
import time

def monitor_bounties():
    db_path = r"U:\McServerData\moro_bounty.db"
    print("📡 Подключение к API MoroSMP...")
    
    while True:
        try:
            conn = sqlite3.connect(db_path)
            cursor = conn.cursor()
            # Get the most expensive bounty
            cursor.execute("SELECT uuid, amount FROM bounties ORDER BY amount DESC LIMIT 1")
            top = cursor.fetchone()
            
            if top:
                print(f"🔥 The most expensive bounty is currently: {top[0][:8]}... Price: ${top[1]:,.0f}")
            
            conn.close()
        except Exception as e:
            print(f"Ошибка связи: {e}")
        
        time.sleep(30) # Ask every 30 seconds

if __name__ == "__main__":
    monitor_bounties()