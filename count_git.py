import subprocess
import os

def count_all_tracked_files():
    # Use git ls-files to safely get only tracked files (bypassing node_modules, build, etc.)
    result = subprocess.run(['git', 'ls-files'], stdout=subprocess.PIPE, text=True)
    files = result.stdout.splitlines()
    
    counts = {}
    
    # Files to ignore
    ignore_exts = {'.png', '.jpg', '.jpeg', '.gif', '.zip', '.jar', '.webp', '.ico', '.so', '.ttf', '.woff', '.woff2', '.jks', '.keystore', '.dex', '.apk', '.svg', '.mp4'}
    
    for file in files:
        if not os.path.exists(file):
            continue
            
        ext = os.path.splitext(file)[1].lower()
        if not ext:
            if file.endswith('gradlew'):
                ext = 'Shell/Bash'
            else:
                ext = 'Other/Config'
                
        if ext in ignore_exts:
            continue
            
        try:
            with open(file, 'r', encoding='utf-8', errors='ignore') as f:
                lines = sum(1 for _ in f)
                
                # Group common extensions nicely
                name = ext
                if ext == '.kt': name = 'Kotlin'
                elif ext == '.xml': name = 'XML'
                elif ext == '.ts': name = 'TypeScript'
                elif ext == '.tsx': name = 'React (TSX)'
                elif ext == '.css': name = 'CSS'
                elif ext == '.js': name = 'JavaScript'
                elif ext == '.json': name = 'JSON'
                elif ext == '.py': name = 'Python'
                elif ext == '.md': name = 'Markdown'
                elif ext == '.kts': name = 'Kotlin Script (Gradle)'
                elif ext == '.bat': name = 'Batch Script'
                elif ext == '.pro': name = 'ProGuard Rules'
                elif ext == '.properties': name = 'Properties Config'
                elif ext == '.html': name = 'HTML'
                elif ext == '.yaml' or ext == '.yml': name = 'YAML'
                elif ext == '.lock': name = 'Lockfiles'
                
                if name not in counts:
                    counts[name] = 0
                counts[name] += lines
        except Exception:
            pass
            
    sorted_counts = sorted(counts.items(), key=lambda x: x[1], reverse=True)
    for k, v in sorted_counts:
        print(f"{k}: {v} lines")

count_all_tracked_files()
