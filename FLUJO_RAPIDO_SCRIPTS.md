# Flujo Rápido: Añadir Productos Usando los Scripts de Python

## Los Scripts (ya están en `referencias/`)

| Script | Función |
|--------|---------|
| `create_product_folders.py` | Crea carpetas en `fotos_extra/` con `.barcode` para cada producto existente en la API |
| `download_product_images.py` | Descarga automáticamente fotos de Bing para cada producto |
| `upload_extra_photos.py` | Sube las fotos de `fotos_extra/` a la API como fotos extra |
| `precompute_embeddings.py` | Regenera `embeddings.pkl` con todos los productos + sus fotos |
| `reciclaje_terminal.py` | Terminal interactiva para registrar reciclajes por código de barras + TAP |

---

## Flujo 1: Si el producto ya existe en la API (solo faltan fotos extra y embeddings)

Usa los scripts cuando el producto **ya está creado** en la API (tiene código de barras e imagen principal) y solo quieres añadirle fotos extra + regenerar embeddings.

### Paso 1 — Crear las carpetas locales

```bash
python referencias/create_product_folders.py
```

Esto crea en `fotos_extra/` una carpeta por cada producto que tenga imagen en la API, con un archivo `.barcode` dentro con su código de barras.

```
fotos_extra/
├── Bote champu 400ml Pantente/
│   └── .barcode  (contiene: "8410654012345")
├── Lata Coca-Cola 33cl/
│   └── .barcode  (contiene: "8410376101246")
├── Mi Nuevo Producto/
│   └── .barcode  (contiene: "8412345678901")
└── ...
```

Si tu producto ya está en la API, ya tendrá su carpeta. Pon las fotos ahí:

```
fotos_extra/Mi Nuevo Producto/
├── .barcode
├── 1.jpg
├── 2.jpg
├── 3.jpg
├── 4.jpg
└── 5.jpg
```

### Paso 2 — Subir las fotos extra a la API

```bash
python referencias/upload_extra_photos.py
```

El script:
1. Hace login automáticamente con `adminb:clase1234`
2. Para cada carpeta en `fotos_extra/`, lee el `.barcode` y sube las fotos que faltan
3. Marca las subidas en `.uploaded.json` (no se suben dos veces)

Salida esperada:
```
Logging in...
Fetching products...
  ✓ Mi Nuevo Producto  orden=1  (1.jpg)
  ✓ Mi Nuevo Producto  orden=2  (2.jpg)
  ✓ Mi Nuevo Producto  orden=3  (3.jpg)
  ✓ Mi Nuevo Producto  orden=4  (4.jpg)

Done! Now regenerate embeddings on EC2.
```

### Paso 3 — Regenerar embeddings

```bash
cd referencias\clip-service
python precompute_embeddings.py
```

Este script obtiene todos los productos de la API (con sus fotos extra ya subidas), calcula los embeddings de CLIP (imagen + texto) y guarda `embeddings.pkl`.

### Paso 4 — Subir embeddings.pkl al EC2 y reiniciar Docker

```bash
scp -i "C:\ruta\a\tu\clave.pem" referencias\clip-service\embeddings.pkl ubuntu@52.201.91.206:/home/ubuntu/ClipAPi/clip-service/embeddings.pkl

ssh -i "C:\ruta\a\tu\clave.pem" ubuntu@52.201.91.206
docker restart $(docker ps -q)
docker logs $(docker ps -q) --tail 5
exit
```

Verifica:
```bash
curl http://52.201.91.206:8080/health
# → {"status":"ok","embeddings":9}
```

---

## Flujo 2: El producto NO existe — hay que crearlo primero

Los scripts actuales **solo trabajan con productos que ya existen en la API**. Si el producto es nuevo, primero créalo.

### Paso 1 — Crear el producto en la API

```bash
curl.exe -s -X POST "http://52.201.91.206:3000/api/usuarios/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"nombre\":\"adminb\",\"password\":\"clase1234\"}"
```

Copia el token y luego:

```bash
curl.exe -s -X POST "http://52.201.91.206:3000/api/admin/productos" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer TOKEN_COPIADO" ^
  -d "{\"tipo\":\"EAN13\",\"numeroBarras\":8412345678901,\"nombre\":\"Mi Nuevo Producto\",\"emisionesReducibles\":0.5,\"material\":\"PET\",\"imagen\":\"data:image/jpeg;base64,...\"}"
```

### Paso 2 — Ahora sí, ejecuta los scripts

```bash
:: Crea la carpeta en fotos_extra/
python referencias/create_product_folders.py

:: Mete las fotos extra en fotos_extra/Mi Nuevo Producto/

:: Súbelas a la API
python referencias/upload_extra_photos.py

:: Regenera embeddings
cd referencias\clip-service
python precompute_embeddings.py
```

### Paso 3 — Subir y reiniciar

```bash
scp -i "C:\ruta\a\tu\clave.pem" referencias\clip-service\embeddings.pkl ubuntu@52.201.91.206:/home/ubuntu/ClipAPi/clip-service/embeddings.pkl
ssh -i "C:\ruta\a\tu\clave.pem" ubuntu@52.201.91.206 "docker restart \$(docker ps -q)"
```

---

## Flujo 3: Descargar fotos automáticamente de Internet

Si no tienes fotos del producto, el script `download_product_images.py` busca y descarga imágenes de Bing automáticamente.

```bash
pip install icrawler
python referencias/download_product_images.py
```

Esto descarga hasta 5 fotos por producto en `C:\Users\FA506NC\Downloads\fotos_extra\` (carpeta por código de barras).

Luego puedes moverlas a la carpeta correspondiente en `fotos_extra/` y ejecutar `upload_extra_photos.py`.

---

## Flujo 4: Terminal interactivo para registrar reciclajes

El script `reciclaje_terminal.py` es un terminal interactivo para registrar reciclajes desde línea de comandos (útil para probar productos sin abrir la app JavaFX).

```bash
python referencias/reciclaje_terminal.py
```

Hace:
1. Login automático
2. Pide código de barras → busca producto
3. Pide TAP → busca usuario
4. Confirma y registra el reciclaje

Útil para **verificar que un producto nuevo funciona** antes de probar la app.

---

## Resumen: El workflow completo en 3 comandos

Después de crear el producto en la API y poner fotos en su carpeta:

```bash
python referencias/upload_extra_photos.py
cd referencias\clip-service && python precompute_embeddings.py
scp embeddings.pkl ubuntu@52.201.91.206:~/ClipAPi/clip-service/ && ssh ubuntu@52.201.91.206 "docker restart \$(docker ps -q)"
```

---

## Dónde están los scripts

```
referencias/
├── create_product_folders.py      ← Crea carpetas en fotos_extra/
├── upload_extra_photos.py         ← Sube fotos extra a la API
├── download_product_images.py     ← Descarga fotos de Bing
├── reciclaje_terminal.py          ← Terminal de reciclaje interactivo
└── clip-service/
    └── precompute_embeddings.py   ← Regenera embeddings.pkl
```
