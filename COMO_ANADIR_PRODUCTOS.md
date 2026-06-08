# Cómo Añadir un Nuevo Producto al Sistema (Paso a Paso)

## Índice

1. [Estructura de un Producto](#1-estructura-de-un-producto)
2. [Requisitos Previos](#2-requisitos-previos)
3. [Preparar las Fotos del Producto](#3-preparar-las-fotos-del-producto)
4. [Crear el Producto en la API (con imagen principal)](#4-crear-el-producto-en-la-api-con-imagen-principal)
   - [Opción A: PowerShell (recomendada)](#opción-a-powershell-recomendada)
   - [Opción B: Python](#opción-b-python)
5. [Subir Fotos Extra](#5-subir-fotos-extra)
   - [Usando el script Python upload_extra_photos.py](#usando-el-script-python-upload_extra_photospy)
   - [Manual desde PowerShell](#manual-desde-powershell)
6. [Regenerar Embeddings de CLIP](#6-regenerar-embeddings-de-clip)
   - [Opción A: Ejecutar en local y subir el .pkl al EC2](#opción-a-ejecutar-en-local-y-subir-el-pkl-al-ec2)
   - [Opción B: Ejecutar directamente en EC2](#opción-b-ejecutar-directamente-en-ec2)
7. [Reiniciar el Contenedor Docker de CLIP](#7-reiniciar-el-contenedor-docker-de-clip)
8. [Verificar que Todo Funciona](#8-verificar-que-todo-funciona)
9. [Resumen Rápido (Checklist)](#9-resumen-rápido-checklist)
10. [Solución de Problemas](#10-solución-de-problemas)

---

## 1. Estructura de un Producto

Cada producto en la base de datos tiene esta estructura:

```json
{
  "tipo": "EAN13",
  "numeroBarras": 1234567890123,
  "nombre": "Nombre del producto",
  "emisionesReducibles": 0.5,
  "material": "PET",
  "imagen": "data:image/jpeg;base64,/9j/4AAQ..."
}
```

| Campo | Descripción | Ejemplo |
|-------|-------------|---------|
| `tipo` | Tipo de código de barras | Siempre `"EAN13"` |
| `numeroBarras` | Código de barras numérico (13 dígitos) | `8410376101246` |
| `nombre` | Nombre del producto | `"Lata Coca-Cola 33cl"` |
| `emisionesReducibles` | Kg de CO₂ que se ahorran al reciclar | `0.5` |
| `material` | Material del producto | `"PET"`, `"Aluminio"`, `"Cartón"`, `"Vidrio"` |
| `imagen` | Imagen principal en base64 con prefijo `data:image/jpeg;base64,` | (string muy largo) |

Además, cada producto puede tener **fotos extra** en la tabla `Fotos_Extra`, que también se usan para generar los embeddings de CLIP.

### Productos existentes en el sistema (para referencia)

Los productos actuales tienen estos nombres (obtenidos de la API):
- Bote champu 400ml Pantene
- Botella agua 1L Bezoya
- Botella agua 250ml Bezoya
- Botella cerveza 33cl Mahou
- Brick leche 1L Pascual
- Caja de carton
- Lata Coca-Cola 33cl
- Lata Fanta Naranja 33cl

---

## 2. Requisitos Previos

Antes de empezar, necesitas:

1. **Credenciales de administrador** de la API:
   - Usuario: `adminb`
   - Contraseña: `clase1234`

2. **Acceso SSH a EC2** (para regenerar embeddings y reiniciar Docker):
   - IP: `52.201.91.206`
   - Necesitas la clave `.pem` del servidor

3. **Python 3.8+** instalado en tu máquina local (para el script de embeddings si eliges la Opción A)

4. **PowerShell** o **Python** para hacer llamadas a la API

5. **Las fotos del producto** listas en tu ordenador:
   - Al menos 1 foto principal (para la imagen del producto)
   - Opcional: varias fotos extra desde distintos ángulos (mejoran el reconocimiento)

---

## 3. Preparar las Fotos del Producto

### 3.1 Foto principal

La foto principal debe ser:
- Formato: **JPEG** (`.jpg`)
- Tamaño recomendado: **640×480 píxeles** o similar (se redimensionará a 224×224 para CLIP)
- La imagen debe mostrar el producto **centrado**, con fondo simple si es posible
- El peso máximo recomendado: **~500KB** (la API acepta strings base64 grandes, pero es mejor no abusar)

### 3.2 Fotos extra (opcional pero muy recomendado)

Las fotos extra mejoran drásticamente la precisión de CLIP. Se recomienda:
- **4–5 fotos** por producto
- Diferentes ángulos: frontal, lateral, superior, ¾
- Diferentes condiciones de luz si es posible
- Misma resolución que la foto principal

### 3.3 Crear la carpeta local (opcional, para mantener registro)

Si quieres mantener una copia local organizada de las fotos extra (como ya existe), crea una carpeta en `fotos_extra/` con el nombre del producto:

```
fotos_extra/
└── Mi Nuevo Producto/
    ├── 1.jpg
    ├── 2.jpg
    ├── 3.jpg
    ├── 4.jpg
    └── .barcode        ← archivo con solo el código de barras (ej: "8410376101246")
```

El archivo `.barcode` es un archivo de texto plano sin extensión que contiene **únicamente** el código de barras de 13 dígitos. Sirve para que el script `upload_extra_photos.py` sepa a qué producto pertenecen las fotos.

---

## 4. Crear el Producto en la API (con imagen principal)

Primero necesitas convertir la foto a base64 con prefijo `data:image/jpeg;base64,`.

### Paso 0: Obtener token JWT

```
POST /api/usuarios/login
Body: {"nombre": "adminb", "password": "clase1234"}
Response: {"token": "eyJhbGciOiJIUzI1NiJ9..."}
```

**cURL** (en Windows usa `curl.exe` para evitar el alias de PowerShell):
```bash
curl.exe -s -X POST "http://52.201.91.206:3000/api/usuarios/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"nombre\":\"adminb\",\"password\":\"clase1234\"}"
```

**PowerShell:**
```powershell
$login = Invoke-RestMethod -Uri "http://52.201.91.206:3000/api/usuarios/login" -Method Post `
    -ContentType "application/json" `
    -Body '{"nombre":"adminb","password":"clase1234"}'
$token = $login.token
$headers = @{Authorization = "Bearer $token"}
```

**Python:**
```python
resp = requests.post("http://52.201.91.206:3000/api/usuarios/login",
                     json={"nombre": "adminb", "password": "clase1234"},
                     timeout=15)
token = resp.json()["token"]
headers = {"Authorization": f"Bearer {token}"}
```

### Opción A: PowerShell (recomendada)

Crea un archivo `crear_producto.ps1` con este contenido (MODIFICA los valores):

```powershell
param(
    [string]$ImagePath = "C:\ruta\a\tu\foto.jpg",
    [string]$Barcode = "1234567890123",      # ← CÓDIGO DE BARRAS DE 13 DÍGITOS
    [string]$Nombre = "Mi Nuevo Producto",   # ← NOMBRE DEL PRODUCTO
    [string]$Material = "PET",               # ← PET, Aluminio, Cartón, Vidrio, etc.
    [string]$Emisiones = "0.5",              # ← Kg de CO₂ ahorrados
    [string]$ApiBase = "http://52.201.91.206:3000"
)

# 1. LOGIN
Write-Host "Obteniendo token..." -ForegroundColor Cyan
$login = Invoke-RestMethod -Uri "$ApiBase/api/usuarios/login" -Method Post `
    -ContentType "application/json" `
    -Body '{"nombre":"adminb","password":"clase1234"}'
$token = $login.token
$headers = @{Authorization = "Bearer $token"}
Write-Host "  Token obtenido" -ForegroundColor Green

# 2. CONVERTIR IMAGEN A BASE64
Write-Host "Convirtiendo imagen a base64..." -ForegroundColor Cyan
$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $ImagePath))
$b64 = [System.Convert]::ToBase64String($bytes)
$imagenConPrefijo = "data:image/jpeg;base64,$b64"
Write-Host "  Imagen convertida ($($b64.Length) caracteres)" -ForegroundColor Green

# 3. CREAR PRODUCTO
Write-Host "Creando producto..." -ForegroundColor Cyan
$body = @{
    tipo = "EAN13"
    numeroBarras = [long]$Barcode
    nombre = $Nombre
    emisionesReducibles = [float]$Emisiones
    material = $Material
    imagen = $imagenConPrefijo
} | ConvertTo-Json -Compress

try {
    $response = Invoke-RestMethod -Uri "$ApiBase/api/admin/productos" -Method Post `
        -ContentType "application/json" -Headers $headers -Body $body
    Write-Host "  ✓ Producto creado exitosamente:" -ForegroundColor Green
    Write-Host "    Tipo: $($response.tipo)" -ForegroundColor White
    Write-Host "    Código: $($response.numeroBarras)" -ForegroundColor White
    Write-Host "    Nombre: $($response.nombre)" -ForegroundColor White
    Write-Host "    Material: $($response.material)" -ForegroundColor White
    Write-Host "    Tiene imagen: $($response.imagen.Length -gt 0)" -ForegroundColor White
} catch {
    $err = $_.ErrorDetails.Message
    if ($err -match "Ya existe") {
        Write-Host "  ✗ El producto YA EXISTE (tipo EAN13 + código $Barcode)" -ForegroundColor Red
        Write-Host "  Usa PUT para actualizarlo en lugar de POST" -ForegroundColor Yellow
    } else {
        Write-Host "  ✗ Error: $_" -ForegroundColor Red
        if ($err) { Write-Host "    Detalle: $err" -ForegroundColor Red }
    }
}
```

**Ejecútalo así** (en PowerShell desde la carpeta del proyecto):

```powershell
.\crear_producto.ps1 -ImagePath "C:\Users\FA506NC\Desktop\fotos\mi_producto.jpg" `
    -Barcode "8412345678901" `
    -Nombre "Mi Nuevo Producto" `
    -Material "PET" `
    -Emisiones "0.5"
```

### Opción B: Python

Crea un archivo `crear_producto.py`:

```python
import requests, base64, json

API_BASE = "http://52.201.91.206:3000"

# 1. LOGIN
print("Obteniendo token...")
resp = requests.post(f"{API_BASE}/api/usuarios/login",
                     json={"nombre": "adminb", "password": "clase1234"},
                     timeout=15)
token = resp.json()["token"]
headers = {"Authorization": f"Bearer {token}"}
print("  Token obtenido")

# 2. CONVERTIR IMAGEN A BASE64
ruta_imagen = r"C:\ruta\a\tu\foto.jpg"  # ← CAMBIA ESTO
with open(ruta_imagen, "rb") as f:
    b64 = base64.b64encode(f.read()).decode()
imagen = f"data:image/jpeg;base64,{b64}"
print(f"  Imagen convertida ({len(b64)} caracteres)")

# 3. CREAR PRODUCTO
body = {
    "tipo": "EAN13",
    "numeroBarras": 8412345678901,       # ← CAMBIA ESTO (13 dígitos, sin comillas)
    "nombre": "Mi Nuevo Producto",        # ← CAMBIA ESTO
    "emisionesReducibles": 0.5,           # ← CAMBIA ESTO
    "material": "PET",                    # ← CAMBIA ESTO
    "imagen": imagen
}

print("Creando producto...")
resp = requests.post(f"{API_BASE}/api/admin/productos",
                     json=body, headers=headers, timeout=30)

if resp.status_code == 201:
    p = resp.json()
    print(f"  ✓ Producto creado: {p['tipo']}_{p['numeroBarras']}_{p['nombre']}")
elif resp.status_code == 409:
    print("  ✗ El producto YA EXISTE (código de barras duplicado)")
    print("  Usa PUT para actualizar en lugar de POST")
else:
    print(f"  ✗ Error {resp.status_code}: {resp.text}")
```

**Ejecútalo:**

```bash
python crear_producto.py
```

**Respuesta esperada (código 201):**

```json
{
  "tipo": "EAN13",
  "numeroBarras": "8412345678901",
  "nombre": "Mi Nuevo Producto",
  "emisionesReducibles": 0.5,
  "material": "PET",
  "imagen": "data:image/jpeg;base64,...",
  "vecesReciclado": 0
}
```

### Si el producto ya existe y quieres actualizarlo

Usa `PUT` en lugar de `POST`:

```
PUT /api/admin/productos/EAN13/{codigo_barras}
```

En PowerShell:

```powershell
Invoke-RestMethod -Uri "$ApiBase/api/admin/productos/EAN13/$Barcode" -Method Put `
    -ContentType "application/json" -Headers $headers -Body $body
```

En Python:

```python
resp = requests.put(f"{API_BASE}/api/admin/productos/EAN13/{barcode}",
                    json=body, headers=headers, timeout=30)
```

> **IMPORTANTE**: Si solo quieres actualizar la imagen (sin cambiar nombre/material), el PUT sobreescribe TODOS los campos. Asegúrate de enviar los valores actuales de nombre, emisionesReducibles y material junto con la nueva imagen.

---

## 5. Subir Fotos Extra

Este paso es **opcional pero muy recomendado**. Las fotos extra mejoran la precisión de CLIP porque el embedding final es el **promedio** de todas las fotos (principal + extra).

### Usando el script Python upload_extra_photos.py

Ya existe un script preparado en `referencias/upload_extra_photos.py`. Así se usa:

**cURL** (Windows — usa `curl.exe`):
```bash
curl.exe -s -X POST "http://52.201.91.206:3000/api/usuarios/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"nombre\":\"adminb\",\"password\":\"clase1234\"}"
```

**PowerShell:**
```powershell
$login = Invoke-RestMethod -Uri "http://52.201.91.206:3000/api/usuarios/login" -Method Post `
    -ContentType "application/json" `
    -Body '{"nombre":"adminb","password":"clase1234"}'
$token = $login.token
$headers = @{Authorization = "Bearer $token"}
```

#### Paso 1: Crear la carpeta del producto en fotos_extra/

```
fotos_extra/
└── Mi Nuevo Producto/       ← El nombre DEBE coincidir exactamente con el nombre en la API
    ├── 1.jpg                ← Fotos desde distintos ángulos
    ├── 2.jpg
    ├── 3.jpg
    ├── 4.jpg
    └── .barcode             ← Archivo con el código de barras (solo el número, sin extensión)
```

El archivo `.barcode` es **obligatorio** si el nombre de la carpeta no coincide exactamente con el nombre del producto en la API. Contiene solo el código de barras:

```
8412345678901
```

Para crearlo en PowerShell:

```powershell
Set-Content -Path "fotos_extra\Mi Nuevo Producto\.barcode" -Value "8412345678901" -NoNewline
```

#### Paso 2: Ejecutar el script

```bash
python referencias/upload_extra_photos.py
```

El script:
1. Hace login con `adminb:clase1234`
2. Obtiene la lista de productos de la API
3. Para cada carpeta en `fotos_extra/`, lee el `.barcode` o busca por nombre
4. Consulta las fotos extra existentes para ese producto (para continuar desde el orden correcto)
5. Sube cada imagen que no se haya subido ya (usa `.uploaded.json` para llevar registro)
6. Marca cada imagen como subida en `.uploaded.json`

**Salida esperada:**

```
Logging in...
Fetching products...
  ✓ Mi Nuevo Producto  orden=1  (1.jpg)
  ✓ Mi Nuevo Producto  orden=2  (2.jpg)
  ✓ Mi Nuevo Producto  orden=3  (3.jpg)
  ✓ Mi Nuevo Producto  orden=4  (4.jpg)

Done! Now regenerate embeddings on EC2.
```

### Manual desde PowerShell

Si prefieres hacerlo manualmente (para un solo producto):

**cURL** (Windows — usa `curl.exe`):
```bash
:: Paso 1 — Login y guardar token en variable
for /f "tokens=*" %i in ('curl.exe -s -X POST "http://52.201.91.206:3000/api/usuarios/login" -H "Content-Type: application/json" -d "{\"nombre\":\"adminb\",\"password\":\"clase1234\"}" ^| python -c "import sys,json;print(json.load(sys.stdin)['token'])"') do set TOKEN=%i

:: Paso 2 — Subir cada foto (repite por cada archivo)
curl.exe -s -X POST "http://52.201.91.206:3000/api/admin/productos/EAN13/8412345678901/fotos" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"imagen\":\"data:image/jpeg;base64,...\",\"orden\":1}"
```

**PowerShell:**
```powershell
param(
    [string]$Barcode = "8412345678901",           # ← CÓDIGO DE BARRAS
    [string]$FotosDir = "C:\ruta\a\las\fotos",   # ← CARPETA CON LAS FOTOS
    [string]$ApiBase = "http://52.201.91.206:3000"
)

# Login
$login = Invoke-RestMethod -Uri "$ApiBase/api/usuarios/login" -Method Post `
    -ContentType "application/json" `
    -Body '{"nombre":"adminb","password":"clase1234"}'
$headers = @{Authorization = "Bearer $login.token"}

# Obtener fotos extra actuales (para saber el orden inicial)
try {
    $existing = Invoke-RestMethod -Uri "$ApiBase/api/admin/productos/EAN13/$Barcode/fotos" `
        -Headers $headers
    $order = $existing.Count + 1
} catch {
    $order = 1
}

# Subir cada foto
Get-ChildItem -Path $FotosDir -Include "*.jpg","*.jpeg","*.png" -Name | Sort-Object | ForEach-Object {
    $imgPath = Join-Path $FotosDir $_
    $bytes = [System.IO.File]::ReadAllBytes($imgPath)
    $b64 = [System.Convert]::ToBase64String($bytes)
    $body = @{
        imagen = "data:image/jpeg;base64,$b64"
        orden = $order
    } | ConvertTo-Json -Compress

    try {
        $r = Invoke-RestMethod -Uri "$ApiBase/api/admin/productos/EAN13/$Barcode/fotos" `
            -Method Post -ContentType "application/json" -Headers $headers -Body $body
        Write-Host "  ✓ $_ (orden=$order)" -ForegroundColor Green
        $order++
    } catch {
        Write-Host "  ✗ $_ : $_" -ForegroundColor Red
    }
}
```

---

## 6. Regenerar Embeddings de CLIP

**Este paso es OBLIGATORIO.** Sin regenerar los embeddings, CLIP no sabrá reconocer el nuevo producto.

Los embeddings se generan con `precompute_embeddings.py`, que:
1. Obtiene TODOS los productos de la API (`GET /api/admin/productos`)
2. Para cada producto, procesa la imagen principal + fotos extra
3. Calcula el embedding promedio de todas las imágenes
4. Calcula el embedding de texto (nombre del producto)
5. Guarda todo en `embeddings.pkl`

Tienes dos opciones para ejecutarlo:

### Opción A: Ejecutar en local y subir el .pkl al EC2

#### Requisitos locales:
- Python 3.8+
- PyTorch (CPU basta)
- CLIP (`pip install clip-by-openai`)
- Las dependencias de `requirements.txt`

#### Paso 1: Instalar dependencias (solo la primera vez)

```bash
cd referencias\clip-service
pip install -r requirements.txt
```

#### Paso 2: Configurar variables de entorno (opcional)

Por defecto el script ya apunta a `http://52.201.91.206:3000`. Si necesitas cambiarlo:

```powershell
$env:API_BASE = "http://52.201.91.206:3000"
```

#### Paso 3: Ejecutar el script

```bash
cd referencias\clip-service
python precompute_embeddings.py
```

**Salida esperada:**

```
Obteniendo productos desde http://52.201.91.206:3000...
Token no proporcionado, las requests serán sin autenticación
Productos obtenidos: 9  (← ahora son 9 en lugar de 8)
Procesando EAN13_8410376101246_Lata Coca-Cola 33cl...
  EXTRA Lata Coca-Cola 33cl (orden 1)
  EXTRA Lata Coca-Cola 33cl (orden 2)
  EXTRA Lata Coca-Cola 33cl (orden 3)
  EXTRA Lata Coca-Cola 33cl (orden 4)
  OK  EAN13_8410376101246_Lata Coca-Cola 33cl (5 photos)
...
Procesando EAN13_8412345678901_Mi Nuevo Producto...   (← EL NUEVO)
  OK  EAN13_8412345678901_Mi Nuevo Producto (1 photos)  (← si no tiene fotos extra)

Guardado embeddings.pkl con N embeddings
```

> **NOTA**: Si el script muestra `SKIP ... (no images)` para tu producto, significa que no tiene imagen principal ni fotos extra. Vuelve al paso 4 y asegúrate de incluir la imagen.

#### Paso 4: Subir embeddings.pkl al EC2

```powershell
scp -i "C:\ruta\a\tu\clave.pem" referencias\clip-service\embeddings.pkl ubuntu@52.201.91.206:/home/ubuntu/ClipAPi/clip-service/embeddings.pkl
```

### Opción B: Ejecutar directamente en EC2 (recomendada si tienes SSH)

Es más rápido porque PyTorch ya está instalado en el contenedor Docker.

#### Paso 1: Conectarte al EC2 por SSH

```powershell
ssh -i "C:\ruta\a\tu\clave.pem" ubuntu@52.201.91.206
```

#### Paso 2: Copiar el script actualizado dentro del contenedor

Desde dentro del EC2:

```bash
# Ver contenedor en ejecución
docker ps | grep clip

# Copiar el script al contenedor
docker cp /home/ubuntu/ClipAPi/clip-service/precompute_embeddings.py clip-container:/app/precompute_embeddings.py
```

O si el contenedor tiene otro nombre:

```bash
# Encontrar el nombre del contenedor clip
docker ps --format "{{.Names}}" | grep -i clip

# Copiar el script
docker cp precompute_embeddings.py <nombre_del_contenedor>:/app/precompute_embeddings.py
```

#### Paso 3: Ejecutar dentro del contenedor

```bash
docker exec -it <nombre_del_contenedor> python /app/precompute_embeddings.py
```

**Salida esperada** (similar a la Opción A).

#### Paso 4: Salir del EC2

```bash
exit
```

---

## 7. Reiniciar el Contenedor Docker de CLIP

Después de regenerar `embeddings.pkl`, el contenedor Docker de CLIP **debe reiniciarse** para que cargue el nuevo archivo.

### Si ejecutaste la Opción B (dentro del EC2):

```bash
# Desde el EC2, reiniciar el contenedor
docker restart <nombre_del_contenedor>

# Ver los logs para confirmar que cargó los nuevos embeddings
docker logs <nombre_del_contenedor> --tail 10
```

Deberías ver en los logs:

```
CLIP loading ViT-B/32 on cpu...
CLIP ViT-B/32 loaded.
Loaded 9 embeddings from /app/embeddings.pkl   (← ahora 9 en lugar de 8)
```

### Si ejecutaste la Opción A (local y subiste el .pkl):

Conéctate al EC2 y ejecuta:

```bash
ssh -i "C:\ruta\a\tu\clave.pem" ubuntu@52.201.91.206

# Reiniciar el contenedor
docker restart <nombre_del_contenedor>

# Verificar
docker logs <nombre_del_contenedor> --tail 5
```

### Si no sabes el nombre del contenedor:

```bash
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
```

Busca el que tenga la imagen de CLIP (normalmente `clip-service` o similar). Si solo hay un contenedor en ejecución, puedes usar:

```bash
docker restart $(docker ps -q)
```

---

## 8. Verificar que Todo Funciona

### 8.1 Health check del servicio CLIP

```bash
curl http://52.201.91.206:8080/health
```

**Respuesta esperada:**

```json
{"status": "ok", "embeddings": 9}   // ← 9 en lugar de 8
```

Si ves `9`, el nuevo producto se cargó correctamente.

### 8.2 Probar el matching con una foto del producto

Prepara una foto de prueba y ejecuta:

```python
import requests, base64

# Cargar imagen de prueba
with open("foto_prueba.jpg", "rb") as f:
    b64 = base64.b64encode(f.read()).decode()

# Llamar a CLIP
resp = requests.post("http://52.201.91.206:8080/match", json={
    "image": f"data:image/jpeg;base64,{b64}",
    "top_k": 5
}, timeout=30)

matches = resp.json()["matches"]
for m in matches:
    print(f"  {m['name']}: {m['score']}")
```

El nuevo producto debería aparecer en los resultados (idealmente en el top 1-3, dependiendo de la calidad de la foto).

### 8.3 Probar desde la app JavaFX

1. Ejecuta la app JavaFX (`gradlew run` en `trabajo/`)
2. Escanea el nuevo producto con la cámara
3. Envía la imagen
4. El producto debería aparecer en la tabla de resultados

### 8.4 Verificar producto en la API

**cURL** (Windows — usa `curl.exe`):
```bash
:: Guardar token en variable (PowerShell)
for /f "tokens=*" %i in ('curl.exe -s -X POST "http://52.201.91.206:3000/api/usuarios/login" -H "Content-Type: application/json" -d "{\"nombre\":\"adminb\",\"password\":\"clase1234\"}" ^| python -c "import sys,json;print(json.load(sys.stdin)['token'])"') do set TOKEN=%i

:: Listar productos y filtrar por código de barras
curl.exe -s -H "Authorization: Bearer %TOKEN%" "http://52.201.91.206:3000/api/admin/productos" | python -c "import sys,json;prods=json.load(sys.stdin);[print(p) for p in prods if p['numeroBarras']=='8412345678901']"
```

**PowerShell:**
```powershell
# Login
$login = Invoke-RestMethod -Uri "http://52.201.91.206:3000/api/usuarios/login" -Method Post `
    -ContentType "application/json" `
    -Body '{"nombre":"adminb","password":"clase1234"}'
$token = $login.token
$headers = @{Authorization = "Bearer $token"}

# Buscar el producto
$prod = Invoke-RestMethod -Uri "http://52.201.91.206:3000/api/admin/productos" -Headers $headers
$prod | Where-Object { $_.numeroBarras -eq "8412345678901" } | Format-List
```

**Python:**
```python
resp = requests.post("http://52.201.91.206:3000/api/usuarios/login",
                     json={"nombre": "adminb", "password": "clase1234"},
                     timeout=15)
headers = {"Authorization": f"Bearer {resp.json()['token']}"}
prods = requests.get("http://52.201.91.206:3000/api/admin/productos",
                     headers=headers, timeout=15).json()
for p in prods:
    if p["numeroBarras"] == "8412345678901":
        print(p)
```

---

## 9. Resumen Rápido (Checklist)

Una vez que entiendes el proceso, estos son los pasos resumidos para tu próximo producto:

- [ ] **1. Preparar fotos** — 1 foto principal + 4–5 fotos extra del producto desde distintos ángulos
- [ ] **2. Crear producto en la API** — `POST /api/admin/productos` con la foto principal en base64
- [ ] **3. Crear carpeta en fotos_extra/** — con `.barcode` y las fotos extra (opcional pero muy recomendado)
- [ ] **4. Subir fotos extra** — Ejecutar `python referencias/upload_extra_photos.py`
- [ ] **5. Regenerar embeddings** — Ejecutar `precompute_embeddings.py` (local o en EC2)
- [ ] **6. Subir `embeddings.pkl` a EC2** — `scp` si ejecutaste local
- [ ] **7. Reiniciar contenedor Docker** — `docker restart <clip-container>`
- [ ] **8. Verificar** — `curl http://52.201.91.206:8080/health` debe mostrar `embeddings: N+1`

---

## 10. Solución de Problemas

### "Ya existe un producto con tipo=EAN13 y barras=..."

El código de barras ya está registrado. Usa:
- `PUT` para actualizar el producto existente
- O elige otro código de barras si es un producto diferente

### "Token no proporcionado" en precompute_embeddings.py

El script intenta obtener productos sin autenticación. Si la API requiere token, edita `precompute_embeddings.py` y añade el login:

```python
# Añadir después de la línea 14:
if not TOKEN:
    resp = requests.post(f"{API_BASE}/api/usuarios/login",
                        json={"nombre": "adminb", "password": "clase1234"},
                        timeout=15)
    TOKEN = resp.json()["token"]
    headers = {"Authorization": f"Bearer {TOKEN}"}
```

### SKIP ... (no images) para mi producto

El producto no tiene imagen principal ni fotos extra. Vuelve al paso 4 y asegúrate de incluir el campo `imagen` con el base64 de la foto.

### El contenedor Docker no se reinicia

```bash
# Ver logs del contenedor
docker logs <nombre_del_contenedor>

# Si hay error, reconstruir la imagen
docker build -t clip-service /home/ubuntu/ClipAPi/clip-service/
docker run -d -p 8080:8080 --name clip-service clip-service
```

### La app JavaFX no encuentra el producto

1. Verifica el health check de CLIP (`/health`) — debe mostrar el número correcto de embeddings
2. Prueba el matching directamente contra CLIP (`POST /match`)
3. Verifica que la API esté funcionando (`GET /api/health`)
4. Si todo lo anterior funciona pero la app da error, revisa los logs de la app

### Error de conexión al EC2

```bash
# Verificar que el EC2 está accesible
ping 52.201.91.206

# Verificar que el puerto 8080 está abierto
Test-NetConnection -ComputerName 52.201.91.206 -Port 8080

# Verificar que el puerto 3000 está abierto
Test-NetConnection -ComputerName 52.201.91.206 -Port 3000
```

### Las fotos extra no se suben (upload_extra_photos.py)

```
✗ Mi Nuevo Producto  orden=1  (404)
```

El endpoint `/api/admin/productos/EAN13/{barcode}/fotos` devuelve 404. Esto puede significar:
- El producto no existe (verifica el código de barras en `.barcode`)
- El endpoint de fotos extra no está implementado en la versión de la API desplegada (revisa el `AdminController.java` de `sourceApi`, que sí lo tiene)

Solución alternativa: usar el script de PowerShell `upload_extra_photos.ps1` que tiene mejor manejo de errores.
