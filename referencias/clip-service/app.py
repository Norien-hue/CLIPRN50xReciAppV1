import os, sys, base64, io, pickle, json
import numpy as np
import torch
import clip
from PIL import Image
from fastapi import FastAPI, Request
from sklearn.preprocessing import normalize

app = FastAPI()
device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"CLIP loading ViT-B/32 on {device}...", flush=True)
model, preprocess = clip.load("ViT-B/32", device=device)
print("CLIP ViT-B/32 loaded.", flush=True)

EMB_PATH = os.environ.get("EMBEDDINGS_PATH", "/app/embeddings.pkl")
if os.path.exists(EMB_PATH):
    with open(EMB_PATH, "rb") as f:
        data = pickle.load(f)
    db_names = list(data.keys())
    db_embs = normalize(np.array(list(data.values())))
    print(f"Loaded {len(db_names)} embeddings from {EMB_PATH}", flush=True)
else:
    print(f"WARNING: embeddings file not found at {EMB_PATH}", flush=True)
    db_names, db_embs = [], np.array([])

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
    sims = db_embs @ query_emb.T
    top_k = body.get("top_k", 5)
    idxs = np.argsort(sims.flatten())[-top_k:][::-1]
    return [{"name": db_names[i], "score": round(float(sims[i]), 4)} for i in idxs]

@app.get("/health")
def health():
    return {"status": "ok", "embeddings": len(db_names)}
