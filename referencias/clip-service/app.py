import os, sys, base64, io, pickle, json
import numpy as np
import torch
import clip
from PIL import Image
from fastapi import FastAPI, Request
from sklearn.preprocessing import normalize

app = FastAPI()
device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"CLIP loading RN50 on {device}...", flush=True)
model, preprocess = clip.load("RN50", device=device)
print("CLIP RN50 loaded.", flush=True)

EMB_PATH = os.environ.get("EMBEDDINGS_PATH", "/app/embeddings.pkl")
META_PATH = os.environ.get("META_PATH", "/app/products_meta.pkl")

if os.path.exists(EMB_PATH):
    with open(EMB_PATH, "rb") as f:
        data = pickle.load(f)
    db_names = list(data.keys())
    db_embs = normalize(np.array(list(data.values())))
    print(f"Loaded {len(db_names)} image embeddings from {EMB_PATH}", flush=True)
else:
    print(f"WARNING: embeddings file not found at {EMB_PATH}", flush=True)
    db_names, db_embs = [], np.array([])

metas = []
if os.path.exists(META_PATH):
    with open(META_PATH, "rb") as f:
        meta_data = pickle.load(f)
    metas = [meta_data.get(name, {}) for name in db_names]
    print(f"Loaded {len(metas)} metadata entries", flush=True)

# Precompute text embeddings for all product names
text_embs = None
if db_names and metas:
    text_inputs = clip.tokenize([m.get("nombre", name) for name, m in zip(db_names, metas)]).to(device)
    with torch.no_grad():
        text_embs_raw = model.encode_text(text_inputs).cpu().numpy()
    text_embs = normalize(text_embs_raw)
    print("Text embeddings precomputed.", flush=True)

@app.post("/match")
async def match(request: Request):
    body = await request.json()
    raw = body.get("image", "")
    if "base64," in raw:
        raw = raw.split("base64,")[1]
    img_bytes = base64.b64decode(raw)
    img = preprocess(Image.open(io.BytesIO(img_bytes))).unsqueeze(0).to(device)
    with torch.no_grad():
        query_emb = normalize(model.encode_image(img).cpu().numpy())

    sims_img = db_embs @ query_emb.T

    if text_embs is not None and len(text_embs) == len(db_embs):
        sims_text = text_embs @ query_emb.T
        sims = 0.3 * sims_img + 0.7 * sims_text
    else:
        sims = sims_img

    top_k = body.get("top_k", 5)
    idxs = np.argsort(sims.flatten())[-top_k:][::-1]
    return [{"name": db_names[i], "score": round(float(sims[i]), 4)} for i in idxs]

@app.get("/health")
def health():
    return {"status": "ok", "embeddings": len(db_names), "text_enabled": text_embs is not None}
