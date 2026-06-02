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
BLEND_W = float(os.environ.get("BLEND_WEIGHT", "0.5"))

db_names, db_img_embs, db_txt_embs = [], np.array([]), np.array([])
if os.path.exists(EMB_PATH):
    with open(EMB_PATH, "rb") as f:
        data = pickle.load(f)
    db_names = list(data.keys())
    db_img_embs = normalize(np.array([v["img"] for v in data.values()]))
    db_txt_embs = normalize(np.array([v["txt"] for v in data.values()]))
    print(f"Loaded {len(db_names)} embeddings from {EMB_PATH}", flush=True)
else:
    print(f"WARNING: embeddings file not found at {EMB_PATH}", flush=True)

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

    if len(db_names) == 0:
        return {"matches": [], "debug": {"error": "no embeddings loaded"}}

    sims_img = db_img_embs @ query_emb.T
    sims_txt = db_txt_embs @ query_emb.T
    sims = BLEND_W * sims_img + (1 - BLEND_W) * sims_txt

    sims_flat = sims.flatten()
    top_k = body.get("top_k", 5)
    idxs = np.argsort(sims_flat)[-top_k:][::-1]

    # all_scores sorted descending
    all_order = np.argsort(sims_flat)[::-1]

    query_emb_list = query_emb.flatten().tolist()
    query_mean = float(np.mean(query_emb_list))
    query_std = float(np.std(query_emb_list))

    return {
        "matches": [
            {"name": db_names[i], "score": round(float(sims_flat[i]), 4)}
            for i in idxs
        ],
        "debug": {
            "query_embedding_first_10": [round(v, 6) for v in query_emb_list[:10]],
            "query_embedding_mean": round(query_mean, 6),
            "query_embedding_std": round(query_std, 6),
            "all_scores": [
                {"name": db_names[i], "score": round(float(sims_flat[i]), 4)}
                for i in all_order
            ]
        }
    }

@app.get("/health")
def health():
    return {"status": "ok", "embeddings": len(db_names)}
