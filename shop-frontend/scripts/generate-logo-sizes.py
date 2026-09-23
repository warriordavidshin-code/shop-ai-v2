from PIL import Image
from pathlib import Path

src = Path(r"E:\work\workspace\shop-ai\shop-ai-v2\shop-frontend\public\logo-boutique-camel.png")
out_dir = Path(r"E:\work\workspace\shop-ai\shop-ai-v2\shop-frontend\public\brand")
out_dir.mkdir(parents=True, exist_ok=True)
img = Image.open(src).convert("RGBA")

# css display height -> export at 3x for retina
sizes = {
    "header": 36,
    "auth": 56,
    "footer": 40,
    "hero": 48,
    "admin": 32,
}

w0, h0 = img.size
for name, css_h in sizes.items():
    target_h = css_h * 3
    target_w = max(1, round(w0 * (target_h / h0)))
    resized = img.resize((target_w, target_h), Image.Resampling.LANCZOS)
    path = out_dir / f"logo-{name}@3x.png"
    resized.save(path, optimize=True)
    print(name, resized.size, path.name, path.stat().st_size)
