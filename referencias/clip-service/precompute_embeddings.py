import os, sys, base64, io, pickle, json
import torch
import clip
import requests
import numpy as np
from PIL import Image

API_BASE = os.environ.get("API_BASE", "http://52.201.91.206:3000")
TOKEN = os.environ.get("API_TOKEN", "")

device = "cuda" if torch.cuda.is_available() else "cpu"
model, preprocess = clip.load("ViT-B/32", device=device)

headers = {"Authorization": f"Bearer {TOKEN}"} if TOKEN else {}
resp = requests.get(f"{API_BASE}/api/admin/productos", headers=headers)
productos = resp.json()

def decode_and_embed(raw_b64):
    if "base64," in raw_b64:
        raw_b64 = raw_b64.split("base64,")[1]
    pil_img = Image.open(io.BytesIO(base64.b64decode(raw_b64)))
    img_input = preprocess(pil_img).unsqueeze(0).to(device)
    with torch.no_grad():
        emb = model.encode_image(img_input).cpu().numpy()
    return emb[0]

embeddings = {}
for p in productos:
    key = f"{p['tipo']}_{p['numeroBarras']}_{p['nombre']}"
    img_embs = []

    # Main product image
    main_raw = p.get("imagen")
    if main_raw:
        try:
            img_embs.append(decode_and_embed(main_raw))
        except Exception as e:
            print(f"  ERR main img {key}: {e}")

    # Extra photos from DB
    tipo = p['tipo']
    barras = p['numeroBarras']
    try:
        fe_resp = requests.get(f"{API_BASE}/api/admin/productos/{tipo}/{barras}/fotos", headers=headers)
        if fe_resp.status_code == 200:
            fotos = fe_resp.json()
            for fe in fotos:
                fe_raw = fe.get("imagen")
                if fe_raw:
                    try:
                        img_embs.append(decode_and_embed(fe_raw))
                        print(f"  EXTRA {key} (orden {fe.get('orden',0)})")
                    except Exception as e:
                        print(f"  ERR extra img {key}: {e}")
    except Exception as e:
        print(f"  ERR fetching fotos for {key}: {e}")

    # Skip if no images at all
    if not img_embs:
        print(f"  SKIP {key} (no images)")
        continue

    # Average all image embeddings
    avg_img_emb = np.mean(img_embs, axis=0)

    # Text embedding from product name
    name = p.get("nombre", "")
    text = clip.tokenize([f"{name}"]).to(device)
    with torch.no_grad():
        txt_emb = model.encode_text(text).cpu().numpy()

    embeddings[key] = {"img": avg_img_emb, "txt": txt_emb[0]}
    count = len(img_embs)
    print(f"  OK  {key} ({count} photo{'s' if count>1 else ''})")

pickle.dump(embeddings, open("embeddings.pkl", "wb"))
print(f"\nSaved {len(embeddings)} embeddings to embeddings.pkl")
