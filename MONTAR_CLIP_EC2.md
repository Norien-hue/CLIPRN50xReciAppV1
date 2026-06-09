# Montar el Servicio CLIP en otro EC2

## Estructura

La app JavaFX llama a la API Spring Boot (`/api/clip/match`), y la API **proxyza** la petición al contenedor Docker de CLIP. Solo necesitas mover el contenedor.

```
App JavaFX → API Spring Boot (:3000) → CLIP Docker (:8080)
                └── app.clip.url=http://NUEVA_IP:8080
```

## Paso 1 — Copiar el directorio clip-service al nuevo EC2

```bash
scp -i "tu-clave.pem" -r referencias/clip-service ubuntu@NUEVA_IP:/home/ubuntu/ClipAPi/clip-service
```

## Paso 2 — Conectarse al nuevo EC2

```bash
ssh -i "tu-clave.pem" ubuntu@NUEVA_IP
```

## Paso 3 — Instalar Docker (si no lo tiene)

```bash
sudo apt update
sudo apt install -y docker.io
sudo usermod -aG docker $ubuntu
```

Cierra sesión y vuelve a entrar para que el grupo surta efecto.

## Paso 4 — Construir la imagen Docker

```bash
cd /home/ubuntu/ClipAPi/clip-service
docker build -t clip-service .
```

Esto:
1. Descarga `python:3.11-slim`
2. Instala PyTorch CPU + torchvision + clip + fastapi + uvicorn
3. Descarga el modelo ViT-B/32 (~600MB)
4. La imagen final pesa ~2.5GB

## Paso 5 — Ejecutar el contenedor

```bash
docker run -d --name clip-service -p 8080:8080 clip-service
```

El contenedor escucha en `0.0.0.0:8080` dentro del contenedor, mapeado al puerto `8080` del host.

Verificar que arranca:

```bash
docker logs clip-service --tail 10
```

Deberías ver:
```
CLIP loading ViT-B/32 on cpu...
CLIP ViT-B/32 loaded.
WARNING: embeddings file not found at /app/embeddings.pkl
```

## Paso 6 — Generar los embeddings

El contenedor necesita `embeddings.pkl` para poder reconocer productos.

### Opción A: Desde el EC2 de la API (recomendada)

Si el EC2 de la API tiene acceso a la base de datos MySQL, puedes ejecutar `precompute_embeddings.py` dentro del contenedor:

```bash
# Obtener token JWT (necesitas las credenciales de la API)
TOKEN=$(curl -s -X POST "http://IP_API_ORIGINAL:3000/api/usuarios/login" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"adminb","password":"clase1234"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# Ejecutar precompute dentro del contenedor
docker exec -e API_BASE="http://IP_API_ORIGINAL:3000" -e API_TOKEN="$TOKEN" clip-service python /app/precompute_embeddings.py

# El embeddings.pkl se genera dentro del contenedor en /app/embeddings.pkl
```

Si `precompute_embeddings.py` no tiene soporte para `API_TOKEN`, edítalo para incluir login o simplemente:

```bash
docker exec clip-service python /app/precompute_embeddings.py
```

Pero esto fallará si la API requiere autenticación. En ese caso, edita `precompute_embeddings.py` para que haga login:

```python
# Añadir al principio (después de las importaciones)
if not TOKEN:
    resp = requests.post(f"{API_BASE}/api/usuarios/login",
                        json={"nombre": "adminb", "password": "clase1234"},
                        timeout=15)
    TOKEN = resp.json()["token"]
    headers = {"Authorization": f"Bearer {TOKEN}"}
```

Luego copia el script actualizado al contenedor:

```bash
docker cp precompute_embeddings.py clip-service:/app/precompute_embeddings.py
docker exec -e API_BASE="http://IP_API_ORIGINAL:3000" clip-service python /app/precompute_embeddings.py
```

### Opción B: Precompute en local y subir el .pkl

Si no tienes acceso a la base de datos desde el EC2 de CLIP:

```bash
# En tu máquina local
cd referencias\clip-service
python precompute_embeddings.py

# Subir el embeddings.pkl al nuevo EC2
scp -i "tu-clave.pem" embeddings.pkl ubuntu@NUEVA_IP:/home/ubuntu/ClipAPi/clip-service/embeddings.pkl

# Copiarlo dentro del contenedor
docker cp embeddings.pkl clip-service:/app/embeddings.pkl
```

## Paso 7 — Reiniciar el contenedor

```bash
docker restart clip-service
docker logs clip-service --tail 5
```

Ahora debe mostrar:

```
Loaded 8 embeddings from /app/embeddings.pkl
```

## Paso 8 — Actualizar la API para que apunte al nuevo CLIP

En el EC2 donde corre la API Spring Boot, edita `application.properties` (o la variable de entorno):

```bash
# Buscar el properties
sudo find / -name "application.properties" -path "*/reciapp*" 2>/dev/null
```

Cambia la línea:

```properties
app.clip.url=http://localhost:8080
```

por la IP del nuevo EC2:

```properties
app.clip.url=http://NUEVA_IP:8080
```

O usa una variable de entorno al arrancar la API:

```bash
java -jar api.jar --app.clip.url=http://NUEVA_IP:8080
```

O si la API soporta variables de entorno:

```bash
export APP_CLIP_URL=http://NUEVA_IP:8080
java -jar api.jar
```

## Paso 9 — Verificar

### Health check del CLIP

```bash
curl http://NUEVA_IP:8080/health
```

Respuesta esperada:
```json
{"status":"ok","embeddings":8}
```

### Health check de la API

```bash
curl http://IP_API:3000/api/health
```

### Probar matching

```bash
# Desde cualquier máquina con curl
curl -X POST "http://IP_API:3000/api/clip/match" \
  -H "Content-Type: application/json" \
  -d '{"image":"data:image/jpeg;base64,/9j/..."}'
```

## Resumen

```bash
# En el NUEVO EC2
scp -r clip-service ubuntu@NUEVA_IP:~/ClipAPi/
ssh ubuntu@NUEVA_IP
cd ~/ClipAPi/clip-service
docker build -t clip-service .
docker run -d --name clip-service -p 8080:8080 clip-service
# generar/subir embeddings.pkl
docker restart clip-service

# En el EC2 de la API
# cambiar app.clip.url=http://NUEVA_IP:8080
# reiniciar API
```
