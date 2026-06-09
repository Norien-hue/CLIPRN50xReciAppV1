import os, sys, requests, base64, json

API_BASE = os.environ.get("API_BASE", "http://34.225.184.57:3000")
FOTOS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "fotos_extra")
valid_ext = (".jpg", ".jpeg", ".png", ".bmp", ".webp")

print(f"API: {API_BASE}")
print(f"Fotos: {FOTOS_DIR}")

# Login
print("\nLogging in...")
resp = requests.post(f"{API_BASE}/api/usuarios/login",
                     json={"nombre": "adminb", "password": "clase1234"},
                     timeout=15)
if resp.status_code != 200:
    print(f"ERROR: Login failed ({resp.status_code}) {resp.text[:200]}")
    sys.exit(1)
token = resp.json().get("token")
if not token:
    print("ERROR: No token in response")
    sys.exit(1)
headers = {"Authorization": f"Bearer {token}"}
print("OK")

# Fetch all products
print("\nFetching products from API...")
prods = requests.get(f"{API_BASE}/api/admin/productos", headers=headers, timeout=15).json()
name_to_prod = {p["nombre"]: p for p in prods}
print(f"  Found {len(prods)} products in DB")

# For each folder in fotos_extra, match by name and upload
for folder_name in sorted(os.listdir(FOTOS_DIR)):
    folder_path = os.path.join(FOTOS_DIR, folder_name)
    if not os.path.isdir(folder_path):
        continue

    if folder_name not in name_to_prod:
        print(f"\n  ?  {folder_name} -- no matching product in DB, skipping")
        continue

    prod = name_to_prod[folder_name]
    barcode = str(prod["numeroBarras"])
    print(f"\n  === {folder_name} (barcode: {barcode}) ===")

    # Load previously uploaded records
    uploaded_file = os.path.join(folder_path, ".uploaded.json")
    uploaded = {}
    if os.path.exists(uploaded_file):
        try:
            with open(uploaded_file) as f:
                uploaded = json.load(f)
        except:
            uploaded = {}

    # Get sorted image files
    files = sorted([f for f in os.listdir(folder_path) if f.lower().endswith(valid_ext)])
    if not files:
        print(f"      No images found in folder, skipping")
        continue

    # Step 1: Upload first image as main product image (if not already done)
    first_img = files[0]
    main_uploaded = uploaded.get("_main", False)

    if main_uploaded:
        print(f"    [SKIP] Main image already uploaded: {first_img}")
    else:
        first_path = os.path.join(folder_path, first_img)
        try:
            with open(first_path, "rb") as f:
                b64 = base64.b64encode(f.read()).decode()
            main_body = {
                "tipo": "EAN13",
                "numeroBarras": prod["numeroBarras"],
                "nombre": prod["nombre"],
                "emisionesReducibles": prod["emisionesReducibles"],
                "material": prod["material"],
                "imagen": f"data:image/jpeg;base64,{b64}"
            }
            r = requests.put(
                f"{API_BASE}/api/admin/productos/EAN13/{barcode}",
                json=main_body, headers=headers, timeout=60
            )
            if r.status_code == 200:
                print(f"    [OK] Main image set: {first_img}")
                uploaded["_main"] = True
                with open(uploaded_file, "w") as f:
                    json.dump(uploaded, f)
            else:
                print(f"    [FAIL] Main image: {r.status_code} {r.text[:200]}")
                continue
        except Exception as e:
            print(f"    [ERROR] Main image: {e}")
            continue

    # Step 2: Upload remaining images as extra photos
    remaining = files[1:]
    if not remaining:
        print(f"      No extra photos to upload")
        continue

    # Get current extra photo count
    try:
        existing = requests.get(
            f"{API_BASE}/api/admin/productos/EAN13/{barcode}/fotos",
            headers=headers, timeout=15
        ).json()
        order = len(existing) + 1
    except:
        order = 1

    for filename in remaining:
        if filename in uploaded:
            print(f"    [SKIP] Extra photo already uploaded: {filename}")
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
                print(f"    [OK] Extra photo order={order}: {filename}")
                uploaded[filename] = True
                with open(uploaded_file, "w") as f:
                    json.dump(uploaded, f)
                order += 1
            else:
                print(f"    [FAIL] Extra photo order={order}: {r.status_code} {r.text[:200]}")
        except Exception as e:
            print(f"    [ERROR] Extra photo: {filename}: {e}")

print("\nAll done!")
