import os
import subprocess

# Main plugins directory
BASE_DIR = r"C:\Users\bycor\Projects\MoroPlugins"

print("🚀 Starting bulk build pipeline...")

# Iterate through all items in the directory
for folder_name in os.listdir(BASE_DIR):
    folder_path = os.path.join(BASE_DIR, folder_name)
    
    # We only care about directories
    if os.path.isdir(folder_path):
        pom_path = os.path.join(folder_path, "pom.xml")
        
        # If pom.xml exists, this is a Maven project
        if os.path.exists(pom_path):
            print(f"\n{'='*40}")
            print(f"📦 Building project: {folder_name}")
            print(f"{'='*40}")
            
            try:
                # Run Maven in the target project directory
                result = subprocess.run(
                    ["mvn", "clean", "package"], 
                    cwd=folder_path, 
                    shell=True
                )
                
                if result.returncode == 0:
                    print(f"✅ {folder_name} built successfully!")
                else:
                    print(f"❌ Compilation failed in {folder_name}!")
            except Exception as e:
                print(f"⚠️ Critical error while running Maven: {e}")
        else:
            # Ignore folders without code
            pass

print("\n🎉 All projects processed! You can find .jar files in target/ folders.")