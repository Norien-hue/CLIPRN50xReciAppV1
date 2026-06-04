import os, requests, base64, json

API_BASE = "http://52.201.91.206:3000"
SOURCE = r"C:\Users\FA506NC\Desktop\Subida de nota\Interfaces_PI\fotos_extra"

print("Logging in...")
resp = requests.post(f"{API_BASE}/api/usuarios/login",
                     json={"nombre": "adminb", "password": "clase1234"},
                     timeout=15)
token = resp.json()["token"]
headers = {"Authorization": f"Bearer {token}"}

# Build product name → barcode map from API
print("Fetching products...")
prods = requests.get(f"{API_BASE}/api/admin/productos", headers=headers, timeout=15).json()
name_to_barcode = {p["nombre"]: str(p["numeroBarras"]) for p in prods if p.get("imagen")}

valid_ext = (".jpg", ".jpeg", ".png", ".bmp", ".webp")

for folder_name in os.listdir(SOURCE):
    folder_path = os.path.join(SOURCE, folder_name)
    if not os.path.isdir(folder_path):
        continue

    # Try to get barcode from .barcode file first, then match by name
    barcode = None
    barcode_file = os.path.join(folder_path, ".barcode")
    if os.path.exists(barcode_file):
        with open(barcode_file) as f:
            barcode = f.read().strip()
    elif folder_name in name_to_barcode:
        barcode = name_to_barcode[folder_name]

    if not barcode:
        print(f"  ?  {folder_name} — could not determine barcode, skipping")
        continue

    # Load previously uploaded records for this folder
    uploaded_file = os.path.join(folder_path, ".uploaded.json")
    uploaded = {}
    if os.path.exists(uploaded_file):
        try:
            with open(uploaded_file) as f:
                uploaded = json.load(f)
        except:
            uploaded = {}

    # Get current extra photo count for this product
    try:
        existing = requests.get(
            f"{API_BASE}/api/admin/productos/EAN13/{barcode}/fotos",
            headers=headers, timeout=15
        ).json()
        order = len(existing) + 1
    except:
        order = 1

    # Upload each image in the folder
    files = sorted([f for f in os.listdir(folder_path)
                    if f.lower().endswith(valid_ext)])
    if not files:
        print(f"  -  {folder_name} ({barcode}) — no images found")
        continue

    for filename in files:
        if filename in uploaded:
            print(f"  ~  {folder_name}  ({filename}) already uploaded, skipping")
            continue
        filepath = os.path.join(folder_path, filename)
        try:
            with open(filepath, "rb") as f:
                b64 = base64.b64encode(f.read()).decode()
            body = {
                "imagen": f"data:image/jpeg;base64,{b64}",
                "orden": order
            }
            r = requests.post(
                f"{API_BASE}/api/admin/productos/EAN13/{barcode}/fotos",
                json=body, headers=headers, timeout=60
            )
            if r.status_code == 201:
                print(f"  ✓ {folder_name}  orden={order}  ({filename})")
                uploaded[filename] = True
                with open(uploaded_file, "w") as f:
                    json.dump(uploaded, f)
                order += 1
            else:
                print(f"  ✗ {folder_name}  orden={order}  ({r.status_code})")
        except Exception as e:
            print(f"  ✗ {folder_name}  {filename}: {e}")

print("\nDone! Now regenerate embeddings on EC2.")
