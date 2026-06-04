import os, requests

API_BASE = "http://52.201.91.206:3000"
OUTPUT = r"C:\Users\FA506NC\Desktop\Subida de nota\Interfaces_PI\fotos_extra"

print("Logging in...")
resp = requests.post(f"{API_BASE}/api/usuarios/login",
                     json={"nombre": "adminb", "password": "clase1234"},
                     timeout=15)
token = resp.json()["token"]
headers = {"Authorization": f"Bearer {token}"}

print("Fetching products...")
prods = requests.get(f"{API_BASE}/api/admin/productos", headers=headers, timeout=15).json()

os.makedirs(OUTPUT, exist_ok=True)

for p in prods:
    if not p.get("imagen"):
        continue
    name = p["nombre"]
    barcode = str(p["numeroBarras"])
    folder = os.path.join(OUTPUT, name)
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, ".barcode"), "w") as f:
        f.write(barcode)
    print(f"  [OK] {name}  ({barcode})")

print(f"\nDone! Put your photos in each folder, then run upload_extra_photos.py")
