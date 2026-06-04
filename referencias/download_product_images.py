import os, requests, time
from icrawler.builtin import BingImageCrawler

API_BASE = "http://52.201.91.206:3000"
OUTPUT = r"C:\Users\FA506NC\Downloads\fotos_extra"

# Login
print("Logging in...")
resp = requests.post(f"{API_BASE}/api/usuarios/login",
                     json={"nombre": "adminb", "password": "clase1234"})
token = resp.json()["token"]
headers = {"Authorization": f"Bearer {token}"}

# Get products
print("Fetching products...")
prods = requests.get(f"{API_BASE}/api/admin/productos", headers=headers).json()

for p in prods:
    if not p.get("imagen"):
        continue
    name = p["nombre"]
    barcode = str(p["numeroBarras"])
    dest = os.path.join(OUTPUT, barcode)
    os.makedirs(dest, exist_ok=True)

    existing = [f for f in os.listdir(dest) if f.lower().endswith(('.jpg','.jpeg','.png'))]
    if len(existing) >= 5:
        print(f"  SKIP {name} ({barcode}) — already has {len(existing)} images")
        continue

    print(f"  Searching: {name} ({barcode})")
    crawler = BingImageCrawler(storage={"root_dir": dest})
    crawler.crawl(keyword=name, max_num=5)
    print(f"  OK  {name} ({barcode})")
    time.sleep(2)

print("\nDone! Check C:\\Users\\FA506NC\\Downloads\\fotos_extra\\")
