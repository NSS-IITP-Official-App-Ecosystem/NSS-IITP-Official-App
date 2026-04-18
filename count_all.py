import os
import sys

def count_all_extensions(paths):
    counts = {}
    
    # Extensions to ignore completely (binaries, images, etc)
    ignore_exts = {'.png', '.jpg', '.jpeg', '.gif', '.zip', '.jar', '.webp', '.ico', '.so', '.ttf', '.woff', '.woff2', '.jks', '.keystore', '.dex', '.apk'}
    
    for root, dirs, files in os.walk(paths):
        # Ignore dependency and build directories
        if any(ignored in root for ignored in ['.git', 'node_modules', 'build', '.gradle', '.idea', '__pycache__']):
            continue
            
        for file in files:
            ext = os.path.splitext(file)[1].lower()
            if not ext:
                ext = 'No Extension'
                
            if ext in ignore_exts:
                continue
                
            path = os.path.join(root, file)
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = sum(1 for _ in f)
                    if ext not in counts:
                        counts[ext] = 0
                    counts[ext] += lines
            except Exception as e:
                pass
                
    # Sort by line count descending
    sorted_counts = sorted(counts.items(), key=lambda x: x[1], reverse=True)
    for k, v in sorted_counts:
        print(f"{k}: {v} lines")

count_all_extensions('.')
