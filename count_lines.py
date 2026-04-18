import os
import sys

def count_lines(patterns, paths):
    counts = {ext: 0 for ext in patterns}
    
    for root, dirs, files in os.walk(paths):
        if '.git' in root or 'node_modules' in root or 'build' in root or '.gradle' in root or '.idea' in root:
            continue
        for file in files:
            ext = os.path.splitext(file)[1]
            if ext in counts:
                path = os.path.join(root, file)
                try:
                    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                        lines = sum(1 for _ in f)
                        counts[ext] += lines
                except Exception as e:
                    pass
    for k, v in counts.items():
        print(f"{k}: {v}")

count_lines(['.kt', '.xml', '.ts', '.tsx', '.css', '.md'], '.')
