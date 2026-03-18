import os
import subprocess

# Твоя главная папка с плагинами
BASE_DIR = r"C:\Users\bycor\Projects\MoroPlugins"

print("🚀 Запускаю конвейер массовой сборки...")

# Перебираем все элементы в папке
for folder_name in os.listdir(BASE_DIR):
    folder_path = os.path.join(BASE_DIR, folder_name)
    
    # Нас интересуют только папки
    if os.path.isdir(folder_path):
        pom_path = os.path.join(folder_path, "pom.xml")
        
        # Если в папке есть pom.xml, значит это Maven-проект
        if os.path.exists(pom_path):
            print(f"\n{'='*40}")
            print(f"📦 Собираю проект: {folder_name}")
            print(f"{'='*40}")
            
            try:
                # Вызываем Maven в контексте нужной папки
                result = subprocess.run(
                    ["mvn", "clean", "package"], 
                    cwd=folder_path, 
                    shell=True
                )
                
                if result.returncode == 0:
                    print(f"✅ {folder_name} собран идеально!")
                else:
                    print(f"❌ Ошибка компиляции в {folder_name}!")
            except Exception as e:
                print(f"⚠️ Критическая ошибка при запуске Maven: {e}")
        else:
            # Игнорируем папки без кода
            pass

print("\n🎉 Все проекты обработаны! Можешь забирать .jar файлы из папок target/")