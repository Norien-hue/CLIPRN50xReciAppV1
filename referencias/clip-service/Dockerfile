FROM python:3.11-slim

WORKDIR /app

# Install torch CPU-only first (separate from other deps to avoid index conflicts)
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Install git for pip's git+https support, then CLIP from OpenAI's GitHub
# (clip-by-openai on PyPI is outdated and requires torch==1.7.1)
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir git+https://github.com/openai/CLIP.git

COPY app.py .
COPY precompute_embeddings.py .

EXPOSE 8080
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080"]
