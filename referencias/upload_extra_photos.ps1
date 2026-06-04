param(
    [string]$SourceDir = "C:\Users\FA506NC\Downloads\fotos_extra",
    [string]$ApiBase = "http://52.201.91.206:3000"
)

$token = (Invoke-RestMethod -Uri "$ApiBase/api/usuarios/login" -Method Post `
    -ContentType "application/json" -Body '{"nombre":"adminb","password":"clase1234"}').token
$headers = @{Authorization = "Bearer $token"}

# Scan folders named by barcode
$dirs = Get-ChildItem -Path $SourceDir -Directory
foreach ($dir in $dirs) {
    $barcode = $dir.Name
    $files = Get-ChildItem -Path $dir.FullName -File | Sort-Object Name
    $order = 1
    foreach ($file in $files) {
        try {
            $imgBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($file.FullName))
            $body = @{
                imagen = "data:image/jpeg;base64,$imgBase64"
                orden = $order
            } | ConvertTo-Json -Compress

            $r = Invoke-RestMethod -Uri "$ApiBase/api/admin/productos/EAN13/$barcode/fotos" `
                -Method Post -ContentType "application/json" -Headers $headers -Body $body
            Write-Host "  OK  $barcode orden=$order" -ForegroundColor Green
            $order++
        } catch {
            Write-Host "  ERR $barcode orden=$order : $_" -ForegroundColor Red
        }
    }
}
Write-Host "`nDone!" -ForegroundColor Cyan
