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

embeddings = {}
for p in productos:
    raw = p.get("imagen")
    if not raw:
        continue
    if "base64," in raw:
        raw = raw.split("base64,")[1]
    try:
        # Image embedding
        pil_img = Image.open(io.BytesIO(base64.b64decode(raw)))
        img_input = preprocess(pil_img).unsqueeze(0).to(device)
        with torch.no_grad():
            img_emb = model.encode_image(img_input).cpu().numpy()

        # Text embedding from product name
        name = p.get("nombre", "")
        text = clip.tokenize([f"{name}"]).to(device)
        with torch.no_grad():
            txt_emb = model.encode_text(text).cpu().numpy()

        key = f"{p['tipo']}_{p['numeroBarras']}_{p['nombre']}"
        embeddings[key] = {"img": img_emb[0], "txt": txt_emb[0]}
        print(f"  OK  {key}")
    except Exception as e:
        print(f"  ERR {p.get('nombre','?')}: {e}")

pickle.dump(embeddings, open("embeddings.pkl", "wb"))
print(f"\nSaved {len(embeddings)} embeddings to embeddings.pkl")
