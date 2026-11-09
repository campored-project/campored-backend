<#
.SYNOPSIS
    Smoke test del Sprint 3 contra la API desplegada (Render + Supabase). Base de US-25 (AB#56).

.DESCRIPTION
    Pensado para Windows PowerShell 5.1 (no requiere PowerShell 7 ni módulos externos).
    Usa System.Net.Http.HttpClient para leer los 4xx sin excepciones, enviar OPTIONS y decodificar en UTF-8.

    Solo toca datos que crea: cuatro usuarios smoke.<rol>.<sufijo>@campored.test y sus ofertas, pedidos
    y contactos. Las contraseñas se generan en memoria y no se imprimen ni se escriben; los tokens tampoco.
    Al terminar marca como AGOTADA las ofertas smoke que sigan activas para que no queden en el catálogo
    público. La limpieza definitiva se hace con scripts/smoke/limpiar_datos_smoke.sql.

    Ante un 502, 503 o 504 cada petición se reintenta una vez tras 5 s; el chequeo evalúa la respuesta del
    reintento y lo marca con "reintento tras <código>" en la consola y en la tabla Markdown.

.PARAMETER BaseUrl
    URL base de la API. Por defecto https://campored-backend.onrender.com

.PARAMETER TablaMd
    Ruta opcional donde escribir la tabla de resultados en Markdown (sin secretos).

.PARAMETER ConservarOfertasActivas
    No marca como agotadas las ofertas smoke al final.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\smoke-sprint3.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\smoke-sprint3.ps1 -BaseUrl http://localhost:8080
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'https://campored-backend.onrender.com',
    [string]$TablaMd,
    [switch]$ConservarOfertasActivas
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.Web.Extensions
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
$BaseUrl = $BaseUrl.TrimEnd('/')

$ICONO_OK = [char]::ConvertFromUtf32(0x2705)
$ICONO_KO = [char]::ConvertFromUtf32(0x274C)
$ICONO_AVISO = [char]::ConvertFromUtf32(0x26A0)

# ---------------------------------------------------------------------------
# Mensajes exactos del contrato (docs/API_CONTRATO_SPRINT3.md)
# ---------------------------------------------------------------------------
$MSG = @{
    CamposInvalidos       = 'La solicitud contiene campos inválidos'
    RolNoPermitido        = 'El rol del usuario no permite esta operación'
    DireccionRequerida    = 'La dirección de entrega es obligatoria para continuar.'
    JornadaRequerida      = 'Debes seleccionar la jornada de entrega: Mañana o Tarde.'
    CantidadSupera        = 'La cantidad solicitada supera la disponible.'
    FechaAnteriorHoy      = 'La fecha preferida no puede ser anterior a hoy.'
    FechaAnteriorCosecha  = 'La fecha preferida no puede ser anterior a la fecha estimada de cosecha.'
    ModalidadRequerida    = 'Debes seleccionar la modalidad de entrega.'
    ModalidadNoHabilitada = 'La modalidad de entrega no está habilitada en esta oferta.'
    OfertaAgotada         = 'Esta oferta ya no está disponible para pedidos.'
    OfertaNoExiste        = 'La oferta no existe'
    PedidoNoExiste        = 'El pedido no existe'
    PedidoAjeno           = 'No tienes permiso para ver este pedido.'
    CanalNoHabilitado     = 'El productor no tiene habilitado este canal de contacto.'
    PedidoNoCorresponde   = 'El pedido no corresponde a esta oferta.'
    OfertaAjena           = 'No tienes permiso para modificar esta oferta.'
    OfertaConPedidos      = 'No puedes eliminar esta oferta porque tiene pedidos pendientes. Primero márcala como agotada.'
    OfertaConContactos    = 'No puedes eliminar esta oferta porque ya tiene contactos de compradores. Márcala como agotada.'
    WhatsappRequerido     = 'Debes ingresar un número de WhatsApp para habilitar este canal.'
    TelefonoRequerido     = 'Debes ingresar un número de teléfono para habilitar este canal.'
}

# ---------------------------------------------------------------------------
# Fechas con la zona de Colombia, no la del equipo
# ---------------------------------------------------------------------------
$ZonaColombia = $null
try { $ZonaColombia = [TimeZoneInfo]::FindSystemTimeZoneById('SA Pacific Standard Time') } catch { $ZonaColombia = $null }

function Get-AhoraColombia {
    if ($ZonaColombia) { return [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $ZonaColombia) }
    # Colombia no tiene horario de verano: UTC-5 fijo
    return [DateTime]::UtcNow.AddHours(-5)
}

function ConvertTo-HoraColombia([DateTime]$Utc) {
    if ($ZonaColombia) { return [TimeZoneInfo]::ConvertTimeFromUtc($Utc, $ZonaColombia) }
    return $Utc.AddHours(-5)
}

$HoyColombia = (Get-AhoraColombia).Date
function Fecha([int]$Dias) { return $HoyColombia.AddDays($Dias).ToString('yyyy-MM-dd') }

$Sufijo = (Get-AhoraColombia).ToString('yyyyMMddHHmmss')

# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$Http = New-Object System.Net.Http.HttpClient($handler)
$Http.Timeout = [TimeSpan]::FromSeconds(90)

$Respuestas4xx5xx = New-Object System.Collections.Generic.List[object]
$Resultados = New-Object System.Collections.Generic.List[object]
$CODIGOS_REINTENTO = @(502, 503, 504)

# Texto que se agrega al resultado de un chequeo cuya respuesta llegó en el reintento
function Get-AvisoReintento($Codigo) {
    if ($Codigo) { return " ($ICONO_AVISO reintento tras $Codigo)" }
    return ''
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Metodo,
        [Parameter(Mandatory)][string]$Ruta,
        $Cuerpo = $null,
        [string]$Token = $null,
        [hashtable]$Encabezados = @{},
        [System.Net.Http.HttpClient]$Cliente = $Http,
        [switch]$SinRegistrar
    )
    $json = $null
    if ($null -ne $Cuerpo) {
        $json = if ($Cuerpo -is [string]) { $Cuerpo } else { $Cuerpo | ConvertTo-Json -Depth 6 -Compress }
    }

    # Un 502, 503 o 504 suele ser el proxy de Render durante un reinicio: se reintenta una sola vez tras 5 s.
    # La respuesta que se devuelve y se evalúa es la del reintento; Reintento guarda el código que lo provocó
    $reintento = $null
    for ($intento = 1; $intento -le 2; $intento++) {
        # Un HttpRequestMessage no se puede enviar dos veces: se arma uno nuevo en cada intento
        $peticion = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Metodo), "$BaseUrl$Ruta")
        if ($Token) {
            $peticion.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
        }
        foreach ($clave in $Encabezados.Keys) { [void]$peticion.Headers.TryAddWithoutValidation($clave, [string]$Encabezados[$clave]) }
        if ($null -ne $json) {
            $peticion.Content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, 'application/json')
        }

        $inicioUtc = [DateTime]::UtcNow
        $status = 0
        $texto = ''
        $encabezadosResp = @{}
        try {
            $resp = $Cliente.SendAsync($peticion).GetAwaiter().GetResult()
            $status = [int]$resp.StatusCode
            $bytes = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $texto = [Text.Encoding]::UTF8.GetString($bytes)
            foreach ($h in $resp.Headers) { $encabezadosResp[$h.Key] = ($h.Value -join ', ') }
            foreach ($h in $resp.Content.Headers) { $encabezadosResp[$h.Key] = ($h.Value -join ', ') }
        } catch {
            $texto = "Error de red: $($_.Exception.Message)"
        }
        $finUtc = [DateTime]::UtcNow

        if ($intento -eq 1 -and $CODIGOS_REINTENTO -contains $status) {
            $reintento = $status
            Write-Host ("$ICONO_AVISO reintento tras {0}: {1} {2} (se espera 5 s)" -f $status, $Metodo, $Ruta)
            Start-Sleep -Seconds 5
            continue
        }
        break
    }
    if ($reintento) {
        Write-Host ("$ICONO_AVISO resultado del reintento de {0} {1}: HTTP {2}" -f $Metodo, $Ruta, $status)
    }

    $datos = $null
    if ($status -gt 0 -and $texto.Trim().Length -gt 0) {
        try { $datos = $texto | ConvertFrom-Json } catch { $datos = $null }
    }
    $r = [pscustomobject]@{
        Metodo    = $Metodo
        Ruta      = $Ruta
        Status    = $status
        Texto     = $texto
        Datos     = $datos
        Headers   = $encabezadosResp
        InicioUtc = $inicioUtc
        FinUtc    = $finUtc
        Reintento = $reintento
    }
    if (-not $SinRegistrar -and $status -ge 400) { $Respuestas4xx5xx.Add($r) }
    return $r
}

function Test-ListaVacia($r) { return ($r.Texto.Trim() -eq '[]') }

function Get-Propiedades($objeto) {
    if ($null -eq $objeto) { return @() }
    return @($objeto.PSObject.Properties | ForEach-Object { $_.Name })
}

# ---------------------------------------------------------------------------
# Chequeos
# ---------------------------------------------------------------------------
function Write-Resumen {
    $api = @($Resultados | Where-Object { $_.Grupo -eq 'API' })
    $cors = @($Resultados | Where-Object { $_.Grupo -eq 'CORS' })
    $fallidosApi = @($api | Where-Object { -not $_.Ok })
    $fallidosCors = @($cors | Where-Object { -not $_.Ok })
    Write-Host ''
    Write-Host '======================================================================'
    Write-Host " RESUMEN  sufijo $Sufijo  ·  $BaseUrl"
    Write-Host '======================================================================'
    if ($fallidosApi.Count -gt 0 -or $fallidosCors.Count -gt 0) {
        Write-Host ' Fallidos:'
        foreach ($f in @($fallidosApi + $fallidosCors)) {
            Write-Host ("   $ICONO_KO [{0}] {1} | esperado {2} · obtenido {3}" -f $f.Hu, $f.Escenario, $f.Esperado, $f.Obtenido)
        }
    }
    Write-Host (" API : {0} pasados · {1} fallidos (de {2})" -f ($api.Count - $fallidosApi.Count), $fallidosApi.Count, $api.Count)
    Write-Host (" CORS: {0} pasados · {1} fallidos (de {2})  (si falla, revisar CORS_ALLOWED_ORIGINS en Render)" -f ($cors.Count - $fallidosCors.Count), $fallidosCors.Count, $cors.Count)
    Write-Host '======================================================================'

    if ($TablaMd) {
        $lineas = New-Object System.Collections.Generic.List[string]
        $lineas.Add('| HU | Escenario | Esperado | Obtenido | Resultado |')
        $lineas.Add('| --- | --- | --- | --- | --- |')
        foreach ($x in $Resultados) {
            $icono = if ($x.Ok) { $ICONO_OK } else { $ICONO_KO }
            $esc = ($x.Escenario -replace '\|', '\|')
            $lineas.Add("| $($x.Hu) | $esc | $($x.Esperado) | $($x.Obtenido)$(Get-AvisoReintento $x.Reintento) | $icono |")
        }
        [IO.File]::WriteAllLines($TablaMd, $lineas, (New-Object Text.UTF8Encoding($false)))
    }
}

function Stop-Smoke([string]$Motivo) {
    Write-Host ''
    Write-Host "$ICONO_KO Paso bloqueante fallido: $Motivo. Se detiene la corrida."
    Write-Resumen
    exit 2
}

function Test-Check {
    param(
        [Parameter(Mandatory)][string]$Hu,
        [Parameter(Mandatory)][string]$Escenario,
        [Parameter(Mandatory)][string]$Esperado,
        $Resp,
        [scriptblock]$Validar,
        [switch]$Bloqueante,
        [string]$Grupo = 'API'
    )
    $fallos = New-Object System.Collections.Generic.List[string]
    $obtenido = if ($Resp) { $Resp.Status } else { 0 }
    $statusOk = if ($Esperado -eq '2xx') { $obtenido -ge 200 -and $obtenido -lt 300 } else { $obtenido -eq [int]$Esperado }
    if (-not $statusOk) {
        $fallos.Add("HTTP esperado $Esperado, obtenido $obtenido")
    } elseif ($Validar) {
        foreach ($f in @(& $Validar $Resp)) { if ($f) { $fallos.Add([string]$f) } }
    }
    $ok = ($fallos.Count -eq 0)
    $icono = if ($ok) { $ICONO_OK } else { $ICONO_KO }
    $reintento = if ($Resp) { $Resp.Reintento } else { $null }
    Write-Host ("{0} [{1}] {2} | esperado {3} · obtenido {4}{5}" -f $icono, $Hu, $Escenario, $Esperado, $obtenido, (Get-AvisoReintento $reintento))
    if ($Resp -and $Resp.Status -ge 400 -and $Resp.Datos) {
        if ($Resp.Datos.mensaje) { Write-Host "      mensaje: $($Resp.Datos.mensaje)" }
        if ($Resp.Datos.errores) { Write-Host "      errores: $($Resp.Datos.errores | ConvertTo-Json -Compress)" }
    }
    if (-not $ok) {
        foreach ($f in $fallos) { Write-Host "      -> $f" }
        if ($Resp -and $Resp.Status -eq 0) { Write-Host "      -> $($Resp.Texto)" }
    }
    $Resultados.Add([pscustomobject]@{ Hu = $Hu; Escenario = $Escenario; Esperado = $Esperado; Obtenido = $obtenido; Ok = $ok; Grupo = $Grupo; Reintento = $reintento })
    if ($Bloqueante -and -not $ok) { Stop-Smoke "[$Hu] $Escenario" }
    return $ok
}

function Assert-Mensaje($r, [string]$Esperado) {
    $obtenido = $r.Datos.mensaje
    if ($obtenido -cne $Esperado) { return "mensaje esperado '$Esperado', obtenido '$obtenido'" }
}

function Assert-ErrorCampo($r, [string]$Campo, [string]$Esperado) {
    $errores = $r.Datos.errores
    if ($null -eq $errores) { return "la respuesta no trae 'errores'" }
    $prop = $errores.PSObject.Properties[$Campo]
    if ($null -eq $prop) { return "falta errores.$Campo (llegaron: $((Get-Propiedades $errores) -join ', '))" }
    if ($Esperado -and $prop.Value -cne $Esperado) { return "errores.$Campo esperado '$Esperado', obtenido '$($prop.Value)'" }
}

function Write-Seccion([string]$Titulo) {
    Write-Host ''
    Write-Host "--- $Titulo ---"
}

# Contraseña aleatoria en memoria que cumple ^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$ y 8..64 caracteres
function New-Contrasena {
    $alfabeto = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
    $bytes = New-Object byte[] 20
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $cuerpo = -join ($bytes | ForEach-Object { $alfabeto[$_ % $alfabeto.Length] })
    return "Sm$($cuerpo)k7"
}

# ---------------------------------------------------------------------------
# Inicio
# ---------------------------------------------------------------------------
Write-Host '======================================================================'
Write-Host ' Smoke test Sprint 3 — CampoRed backend'
Write-Host " URL base : $BaseUrl"
Write-Host " Sufijo   : $Sufijo   (correos smoke.<rol>.$Sufijo@campored.test)"
Write-Host " Hoy (Colombia): $(Fecha 0)"
Write-Host '======================================================================'

# ===========================================================================
# A. Despliegue y esquema
# ===========================================================================
Write-Seccion 'A. Despliegue y esquema'

# Cold start de Render: la primera petición reintenta hasta 120 s
$clienteDespertar = New-Object System.Net.Http.HttpClient
$clienteDespertar.Timeout = [TimeSpan]::FromSeconds(30)
$limite = [DateTime]::UtcNow.AddSeconds(120)
$avisado = $false
$apiDocs = $null
while ($true) {
    $apiDocs = Invoke-Api -Metodo GET -Ruta '/v3/api-docs' -Cliente $clienteDespertar -SinRegistrar
    if ($apiDocs.Status -ge 200 -and $apiDocs.Status -lt 500) { break }
    if (-not $avisado) {
        Write-Host "$ICONO_AVISO El servidor está despertando (cold start de Render). Reintentando hasta 120 s..."
        $avisado = $true
    }
    if ([DateTime]::UtcNow -ge $limite) { break }
    Start-Sleep -Seconds 5
}

$RutasNuevas = @('POST /api/pedidos', 'GET /api/pedidos/{id}', 'GET /api/pedidos/recibidos',
                 'POST /api/contactos', 'GET /api/contactos/mios', 'DELETE /api/ofertas/{id}')
[void](Test-Check -Hu 'US-25' -Escenario 'A1 GET /v3/api-docs expone los endpoints del Sprint 3 (arrancó con ddl-auto=validate)' -Esperado 200 -Resp $apiDocs -Bloqueante -Validar {
    param($r)
    $serializador = New-Object System.Web.Script.Serialization.JavaScriptSerializer
    $serializador.MaxJsonLength = [int]::MaxValue
    $doc = $serializador.DeserializeObject($r.Texto)
    $paths = $doc['paths']
    foreach ($ruta in $RutasNuevas) {
        $metodo, $path = $ruta.Split(' ')
        if (-not $paths.ContainsKey($path) -or -not $paths[$path].ContainsKey($metodo.ToLower())) {
            "falta $ruta en el despliegue (Render puede seguir sirviendo la versión anterior)"
        }
    }
})

$catalogo = Invoke-Api -Metodo GET -Ruta '/api/ofertas'
[void](Test-Check -Hu 'US-11' -Escenario 'A2 GET /api/ofertas responde PageResponse' -Esperado 200 -Resp $catalogo -Validar {
    param($r)
    $props = Get-Propiedades $r.Datos
    foreach ($campo in 'content', 'number', 'size', 'totalElements', 'totalPages', 'first', 'last', 'empty') {
        if ($props -notcontains $campo) { "falta '$campo' en PageResponse" }
    }
})

$Origenes = @('https://campored.rollerandres.dev', 'https://campored-frontend.vercel.app')
foreach ($origen in $Origenes) {
    foreach ($ruta in '/api/contactos', '/api/pedidos') {
        $pre = Invoke-Api -Metodo OPTIONS -Ruta $ruta -SinRegistrar -Encabezados @{
            'Origin'                         = $origen
            'Access-Control-Request-Method'  = 'POST'
            'Access-Control-Request-Headers' = 'authorization,content-type'
        }
        [void](Test-Check -Grupo 'CORS' -Hu 'CORS' -Escenario "A3 preflight OPTIONS $ruta desde $origen" -Esperado '2xx' -Resp $pre -Validar {
            param($r)
            $allowOrigin = $r.Headers['Access-Control-Allow-Origin']
            if ($allowOrigin -ne $origen) { "Access-Control-Allow-Origin esperado '$origen', obtenido '$allowOrigin'" }
            $allowHeaders = [string]$r.Headers['Access-Control-Allow-Headers']
            foreach ($h in 'authorization', 'content-type') {
                if ($allowHeaders.ToLower() -notmatch [regex]::Escape($h)) { "Access-Control-Allow-Headers no incluye '$h' (obtenido '$allowHeaders')" }
            }
            $allowMethods = [string]$r.Headers['Access-Control-Allow-Methods']
            if ($allowMethods -notmatch 'POST') { "Access-Control-Allow-Methods no incluye POST (obtenido '$allowMethods')" }
        })
    }
}

# ===========================================================================
# B. Datos de prueba
# ===========================================================================
Write-Seccion 'B. Datos de prueba'

$Claves = @{}
$Tokens = @{}
$RespLogin = @{}
$Correos = @{
    ProductorA = "smoke.productora.$Sufijo@campored.test"
    ProductorB = "smoke.productorb.$Sufijo@campored.test"
    CompradorA = "smoke.compradora.$Sufijo@campored.test"
    CompradorB = "smoke.compradorb.$Sufijo@campored.test"
}
foreach ($k in @($Correos.Keys)) { $Claves[$k] = New-Contrasena }

$NombreProductorA = 'Smoke Productor A'
$FincaA = "Finca Smoke A $Sufijo"
$NombreCompradorA = 'Smoke Comprador A'

$registros = @(
    @{ Clave = 'ProductorA'; Hu = 'US-01'; Ruta = '/api/auth/registro/productor'; Esc = 'B4 registro productor A (Sonsón)'; Cuerpo = [ordered]@{
        correo = $Correos.ProductorA; contrasena = $Claves.ProductorA; nombre = $NombreProductorA
        nombreFinca = $FincaA; vereda = 'Vereda Smoke'; municipio = 'SONSON'; telefono = '3000000001' } },
    # Sin WhatsApp a propósito: US-17 Esc 2 prueba habilitar el canal sin número guardado
    @{ Clave = 'ProductorB'; Hu = 'US-01'; Ruta = '/api/auth/registro/productor'; Esc = 'B4 registro productor B (San Carlos)'; Cuerpo = [ordered]@{
        correo = $Correos.ProductorB; contrasena = $Claves.ProductorB; nombre = 'Smoke Productor B'
        nombreFinca = "Finca Smoke B $Sufijo"; vereda = 'Vereda Smoke'; municipio = 'SAN_CARLOS'; telefono = '3000000003' } },
    @{ Clave = 'CompradorA'; Hu = 'US-03'; Ruta = '/api/auth/registro/comprador'; Esc = 'B4 registro comprador A'; Cuerpo = [ordered]@{
        correo = $Correos.CompradorA; contrasena = $Claves.CompradorA; nombre = $NombreCompradorA
        nombreNegocio = "Restaurante Smoke A $Sufijo"; tipoNegocio = 'RESTAURANTE'; direccion = 'Calle Smoke 1 # 2-3'
        municipio = 'RIONEGRO'; horarioRecepcion = 'Lunes a viernes, 6 a 10 a. m.'; notasAcceso = 'Entrada de proveedores (smoke)'
        telefono = '3000000004'; whatsapp = '3000000005' } },
    @{ Clave = 'CompradorB'; Hu = 'US-03'; Ruta = '/api/auth/registro/comprador'; Esc = 'B4 registro comprador B'; Cuerpo = [ordered]@{
        correo = $Correos.CompradorB; contrasena = $Claves.CompradorB; nombre = 'Smoke Comprador B'
        nombreNegocio = "Tienda Smoke B $Sufijo"; tipoNegocio = 'TIENDA'; direccion = 'Carrera Smoke 4 # 5-6'
        municipio = 'LA_CEJA'; horarioRecepcion = 'Sábados, 7 a 11 a. m.'; notasAcceso = 'Timbre lateral (smoke)'
        telefono = '3000000006'; whatsapp = '3000000007' } }
)
foreach ($reg in $registros) {
    $r = Invoke-Api -Metodo POST -Ruta $reg.Ruta -Cuerpo $reg.Cuerpo
    [void](Test-Check -Hu $reg.Hu -Escenario $reg.Esc -Esperado 201 -Resp $r -Bloqueante)
}
foreach ($k in 'ProductorA', 'ProductorB', 'CompradorA', 'CompradorB') {
    $r = Invoke-Api -Metodo POST -Ruta '/api/auth/login' -Cuerpo ([ordered]@{ correo = $Correos[$k]; contrasena = $Claves[$k] })
    [void](Test-Check -Hu 'US-02' -Escenario "B4 login $k" -Esperado 200 -Resp $r -Bloqueante -Validar {
        param($x) if (-not $x.Datos.token) { 'la respuesta no trae token' }
    })
    $Tokens[$k] = $r.Datos.token
    $RespLogin[$k] = $r
}
$TPA = $Tokens.ProductorA; $TPB = $Tokens.ProductorB; $TCA = $Tokens.CompradorA; $TCB = $Tokens.CompradorB

$r = Invoke-Api -Metodo PATCH -Ruta '/api/usuarios/perfil/productor' -Token $TPA -Cuerpo ([ordered]@{
    telefono = '3000000001'; whatsapp = '3000000002'; canalWhatsappHabilitado = $true; canalLlamadaHabilitado = $true })
[void](Test-Check -Hu 'US-17' -Escenario 'B5 productor A habilita WhatsApp y llamada con números' -Esperado 200 -Resp $r -Validar {
    param($x)
    if ($x.Datos.canalWhatsappHabilitado -ne $true) { 'canalWhatsappHabilitado no quedó en true' }
    if ($x.Datos.canalLlamadaHabilitado -ne $true) { 'canalLlamadaHabilitado no quedó en true' }
})
$r = Invoke-Api -Metodo PATCH -Ruta '/api/usuarios/perfil/productor' -Token $TPB -Cuerpo ([ordered]@{
    canalWhatsappHabilitado = $false; canalLlamadaHabilitado = $false })
[void](Test-Check -Hu 'US-17' -Escenario 'B5 productor B apaga ambos canales' -Esperado 200 -Resp $r -Validar {
    param($x)
    if ($x.Datos.canalWhatsappHabilitado -ne $false -or $x.Datos.canalLlamadaHabilitado -ne $false) { 'algún canal quedó habilitado' }
})

$O = @{}
$Precio = @{}
$RespOferta = @{}
$ofertas = @(
    @{ Id = 'O1'; Token = $TPA; Desc = 'O1 DISPONIBLE, RECOGIDA_FINCA, 250 KILOGRAMO'; Cuerpo = [ordered]@{
        producto = 'TOMATE_CHONTO'; descripcion = "Smoke test $Sufijo - O1"; cantidadDisponible = 250; unidadMedida = 'KILOGRAMO'
        precioUnitario = 3500; estadoCultivo = 'DISPONIBLE'; modalidadLogistica = 'RECOGIDA_FINCA' } },
    @{ Id = 'O2'; Token = $TPA; Desc = 'O2 EN_CRECIMIENTO, AMBAS, cosecha hoy+10'; Cuerpo = [ordered]@{
        producto = 'MORA_CASTILLA'; descripcion = "Smoke test $Sufijo - O2"; cantidadDisponible = 100; unidadMedida = 'KILOGRAMO'
        precioUnitario = 4200; estadoCultivo = 'EN_CRECIMIENTO'; fechaEstimadaCosecha = (Fecha 10); modalidadLogistica = 'AMBAS' } },
    @{ Id = 'O3'; Token = $TPA; Desc = 'O3 DISPONIBLE (para eliminar sin pedidos)'; Cuerpo = [ordered]@{
        producto = 'CILANTRO'; descripcion = "Smoke test $Sufijo - O3"; cantidadDisponible = 50; unidadMedida = 'KILOGRAMO'
        precioUnitario = 2000; estadoCultivo = 'DISPONIBLE'; modalidadLogistica = 'DESPACHO_PRODUCTOR' } },
    @{ Id = 'O4'; Token = $TPA; Desc = 'O4 DISPONIBLE (para agotar)'; Cuerpo = [ordered]@{
        producto = 'AGUACATE'; descripcion = "Smoke test $Sufijo - O4"; cantidadDisponible = 80; unidadMedida = 'KILOGRAMO'
        precioUnitario = 5200; estadoCultivo = 'DISPONIBLE'; modalidadLogistica = 'RECOGIDA_FINCA' } },
    @{ Id = 'O5'; Token = $TPA; Desc = 'O5 DISPONIBLE (solo contactos)'; Cuerpo = [ordered]@{
        producto = 'PLATANO'; descripcion = "Smoke test $Sufijo - O5"; cantidadDisponible = 120; unidadMedida = 'KILOGRAMO'
        precioUnitario = 1800; estadoCultivo = 'DISPONIBLE'; modalidadLogistica = 'AMBAS' } },
    @{ Id = 'O6'; Token = $TPB; Desc = 'O6 DISPONIBLE del productor B'; Cuerpo = [ordered]@{
        producto = 'FRIJOL_CARGAMANTO'; descripcion = "Smoke test $Sufijo - O6"; cantidadDisponible = 60; unidadMedida = 'KILOGRAMO'
        precioUnitario = 9000; estadoCultivo = 'DISPONIBLE'; modalidadLogistica = 'RECOGIDA_FINCA' } }
)
foreach ($of in $ofertas) {
    $r = Invoke-Api -Metodo POST -Ruta '/api/ofertas' -Token $of.Token -Cuerpo $of.Cuerpo
    [void](Test-Check -Hu 'US-06' -Escenario "B6 crear $($of.Desc)" -Esperado 201 -Resp $r -Bloqueante -Validar {
        param($x) if (-not $x.Datos.id) { 'la respuesta no trae id' }
    })
    $O[$of.Id] = $r.Datos.id
    $RespOferta[$of.Id] = $r
    $Precio[$of.Id] = [decimal]$of.Cuerpo.precioUnitario
}

$r = Invoke-Api -Metodo GET -Ruta "/api/ofertas/$($O.O1)" -Token $TCA
[void](Test-Check -Hu 'US-14/15' -Escenario 'B7 detalle de O1 como comprador A trae whatsappContacto y telefonoContacto' -Esperado 200 -Resp $r -Validar {
    param($x)
    if (-not $x.Datos.productor.whatsappContacto) { 'whatsappContacto es null' }
    if (-not $x.Datos.productor.telefonoContacto) { 'telefonoContacto es null' }
})
$r = Invoke-Api -Metodo GET -Ruta "/api/ofertas/$($O.O1)"
[void](Test-Check -Hu 'US-14/15' -Escenario 'B7 detalle de O1 sin token trae los números en null' -Esperado 200 -Resp $r -Validar {
    param($x)
    if ($null -ne $x.Datos.productor.whatsappContacto) { 'whatsappContacto no es null para un visitante' }
    if ($null -ne $x.Datos.productor.telefonoContacto) { 'telefonoContacto no es null para un visitante' }
})

# ===========================================================================
# C. US-18 (AB#49)
# ===========================================================================
Write-Seccion 'C. US-18 Checkout y Pedido (AB#49)'

function New-Pedido([string]$OfertaId, [hashtable]$Cambios = @{}, [string[]]$Quitar = @()) {
    $cuerpo = [ordered]@{
        ofertaId = $OfertaId; cantidadSolicitada = 100; direccionEntrega = 'Calle Smoke 10 # 20-30'
        municipioEntrega = 'RIONEGRO'; fechaPreferida = (Fecha 0); jornada = 'MANANA'; notasAcceso = "Smoke test $Sufijo"
    }
    foreach ($k in $Cambios.Keys) { $cuerpo[$k] = $Cambios[$k] }
    foreach ($k in $Quitar) { $cuerpo.Remove($k) }
    return $cuerpo
}

$respP1 = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O1)
[void](Test-Check -Hu 'US-18' -Escenario 'Esc 1 pedido de 100 kg sobre O1, hoy, MANANA, sin modalidad' -Esperado 201 -Resp $respP1 -Bloqueante -Validar {
    param($x)
    $d = $x.Datos
    $loc = $x.Headers['Location']
    if (-not $loc -or $loc -notmatch "/api/pedidos/$($d.id)$") { "Location inválido: '$loc'" }
    if ($d.estado -ne 'SOLICITADO') { "estado '$($d.estado)', se esperaba SOLICITADO" }
    if ($d.modalidadEntrega -ne 'RECOGIDA_FINCA') { "modalidadEntrega '$($d.modalidadEntrega)', se esperaba RECOGIDA_FINCA" }
    if ([decimal]$d.precioUnitario -ne $Precio.O1) { "precioUnitario $($d.precioUnitario), se esperaba $($Precio.O1)" }
    if ([decimal]$d.precioTotalEstimado -ne (100 * $Precio.O1)) { "precioTotalEstimado $($d.precioTotalEstimado), se esperaba $(100 * $Precio.O1)" }
    if (-not $d.fechaCreacion) { 'fechaCreacion es null' }
})
$P1 = $respP1.Datos.id

$r = Invoke-Api -Metodo GET -Ruta "/api/ofertas/$($O.O1)"
[void](Test-Check -Hu 'US-18' -Escenario 'Esc 1 el pedido no descuenta inventario (O1 sigue en 250)' -Esperado 200 -Resp $r -Validar {
    param($x) if ([decimal]$x.Datos.cantidadDisponible -ne 250) { "cantidadDisponible $($x.Datos.cantidadDisponible), se esperaba 250" }
})

$respSinDireccion = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O1 -Quitar 'direccionEntrega')
[void](Test-Check -Hu 'US-18' -Escenario 'Esc 2 sin dirección de entrega' -Esperado 400 -Resp $respSinDireccion -Validar {
    param($x) Assert-Mensaje $x $MSG.CamposInvalidos; Assert-ErrorCampo $x 'direccionEntrega' $MSG.DireccionRequerida
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O1 -Quitar 'jornada')
[void](Test-Check -Hu 'US-18' -Escenario 'Esc 3 sin jornada' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'jornada' $MSG.JornadaRequerida
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O1 @{ cantidadSolicitada = 300 })
[void](Test-Check -Hu 'US-18' -Escenario 'Esc 5 cantidad 300 kg mayor a la disponible (250)' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'cantidadSolicitada' $MSG.CantidadSupera
})

$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O1 @{ fechaPreferida = (Fecha -1) })
[void](Test-Check -Hu 'US-18' -Escenario 'Regla fecha preferida ayer (Colombia)' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'fechaPreferida' $MSG.FechaAnteriorHoy
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O2 @{ fechaPreferida = (Fecha 2); modalidadEntrega = 'DESPACHO_PRODUCTOR' })
[void](Test-Check -Hu 'US-18' -Escenario 'Regla O2 con fecha hoy+2, antes de la cosecha (hoy+10)' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'fechaPreferida' $MSG.FechaAnteriorCosecha
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O2 @{ fechaPreferida = (Fecha 12) })
[void](Test-Check -Hu 'US-18' -Escenario 'Regla O2 (AMBAS) sin modalidad' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'modalidadEntrega' $MSG.ModalidadRequerida
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O2 @{ fechaPreferida = (Fecha 12); modalidadEntrega = 'AMBAS' })
[void](Test-Check -Hu 'US-18' -Escenario 'Regla O2 con modalidad AMBAS' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'modalidadEntrega' $MSG.ModalidadNoHabilitada
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O1 @{ modalidadEntrega = 'DESPACHO_PRODUCTOR' })
[void](Test-Check -Hu 'US-18' -Escenario 'Regla O1 (RECOGIDA_FINCA) con DESPACHO_PRODUCTOR' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'modalidadEntrega' $MSG.ModalidadNoHabilitada
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O2 @{ cantidadSolicitada = 300; fechaPreferida = (Fecha -1) })
[void](Test-Check -Hu 'US-18' -Escenario 'Regla varios errores de negocio juntos (cantidad, fecha, modalidad) sobre O2' -Esperado 400 -Resp $r -Validar {
    param($x)
    Assert-Mensaje $x $MSG.CamposInvalidos
    Assert-ErrorCampo $x 'cantidadSolicitada' $MSG.CantidadSupera
    Assert-ErrorCampo $x 'fechaPreferida' $MSG.FechaAnteriorHoy
    Assert-ErrorCampo $x 'modalidadEntrega' $MSG.ModalidadRequerida
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O1 -Quitar 'direccionEntrega', 'jornada')
[void](Test-Check -Hu 'US-18' -Escenario 'Regla varios errores de formato juntos (dirección y jornada)' -Esperado 400 -Resp $r -Validar {
    param($x)
    Assert-ErrorCampo $x 'direccionEntrega' $MSG.DireccionRequerida
    Assert-ErrorCampo $x 'jornada' $MSG.JornadaRequerida
})
$respP2 = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O2 @{ cantidadSolicitada = 40; fechaPreferida = (Fecha 12); modalidadEntrega = 'DESPACHO_PRODUCTOR' })
[void](Test-Check -Hu 'US-18' -Escenario 'Regla O2 con hoy+12 y DESPACHO_PRODUCTOR' -Esperado 201 -Resp $respP2 -Validar {
    param($x)
    if ($x.Datos.modalidadEntrega -ne 'DESPACHO_PRODUCTOR') { "modalidadEntrega '$($x.Datos.modalidadEntrega)'" }
    if ($x.Datos.fechaPreferida -ne (Fecha 12)) { "fechaPreferida '$($x.Datos.fechaPreferida)'" }
})
$P2 = $respP2.Datos.id

$r = Invoke-Api -Metodo PATCH -Ruta "/api/ofertas/$($O.O4)/estado" -Token $TPA -Cuerpo ([ordered]@{ estadoPublicacion = 'AGOTADA' })
[void](Test-Check -Hu 'US-09' -Escenario 'Agotar O4' -Esperado 200 -Resp $r -Validar {
    param($x) if ($x.Datos.estadoPublicacion -ne 'AGOTADA') { "estadoPublicacion '$($x.Datos.estadoPublicacion)'" }
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido $O.O4)
[void](Test-Check -Hu 'US-18' -Escenario 'Pedido sobre O4 agotada' -Esperado 422 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.OfertaAgotada
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TCA -Cuerpo (New-Pedido ([guid]::NewGuid().ToString()))
[void](Test-Check -Hu 'US-18' -Escenario 'Pedido sobre oferta inexistente' -Esperado 404 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.OfertaNoExiste
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Token $TPA -Cuerpo (New-Pedido $O.O1)
[void](Test-Check -Hu 'US-18' -Escenario 'Productor A creando un pedido' -Esperado 403 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.RolNoPermitido
})
$r = Invoke-Api -Metodo POST -Ruta '/api/pedidos' -Cuerpo (New-Pedido $O.O1)
[void](Test-Check -Hu 'US-18' -Escenario 'Pedido sin token' -Esperado 401 -Resp $r)

$r = Invoke-Api -Metodo GET -Ruta "/api/pedidos/$P1" -Token $TCA
[void](Test-Check -Hu 'US-18' -Escenario 'GET P1 como comprador A (dueño)' -Esperado 200 -Resp $r -Validar {
    param($x)
    foreach ($bloque in 'productor', 'comprador', 'oferta') { if ($null -eq $x.Datos.$bloque) { "falta '$bloque'" } }
    if ($x.Datos.oferta.id -ne $O.O1) { 'oferta.id no corresponde a O1' }
    if (-not $x.Datos.productor.whatsappContacto) { 'productor.whatsappContacto es null' }
})
$r = Invoke-Api -Metodo GET -Ruta "/api/pedidos/$P1" -Token $TPA
[void](Test-Check -Hu 'US-18' -Escenario 'GET P1 como productor A (dueño de la oferta)' -Esperado 200 -Resp $r -Validar {
    param($x) if ($x.Datos.comprador.nombre -ne $NombreCompradorA) { "comprador.nombre '$($x.Datos.comprador.nombre)'" }
})
$r = Invoke-Api -Metodo GET -Ruta "/api/pedidos/$P1" -Token $TCB
[void](Test-Check -Hu 'US-18' -Escenario 'GET P1 como comprador B (ajeno)' -Esperado 403 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.PedidoAjeno
})
$r = Invoke-Api -Metodo GET -Ruta "/api/pedidos/$P1" -Token $TPB
[void](Test-Check -Hu 'US-18' -Escenario 'GET P1 como productor B (ajeno)' -Esperado 403 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.PedidoAjeno
})
$r = Invoke-Api -Metodo GET -Ruta "/api/pedidos/$([guid]::NewGuid())" -Token $TCA
[void](Test-Check -Hu 'US-18' -Escenario 'GET pedido inexistente' -Esperado 404 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.PedidoNoExiste
})

# ===========================================================================
# D. US-19, US-14 y US-15 (AB#50, AB#45, AB#46)
# ===========================================================================
Write-Seccion 'D. US-19 / US-14 / US-15 Contactos (AB#50, AB#45, AB#46)'

$respC1 = Invoke-Api -Metodo POST -Ruta '/api/contactos' -Token $TCA -Cuerpo ([ordered]@{ ofertaId = $O.O1; canal = 'WHATSAPP'; pedidoId = $P1 })
[void](Test-Check -Hu 'US-14' -Escenario 'Esc 3 WhatsApp sobre O1 desde el pedido P1' -Esperado 201 -Resp $respC1 -Validar {
    param($x)
    if (-not $x.Datos.fechaContacto) { 'fechaContacto es null' }
    if ($x.Datos.pedidoId -ne $P1) { "pedidoId '$($x.Datos.pedidoId)', se esperaba P1" }
    if ($x.Datos.canal -ne 'WHATSAPP') { "canal '$($x.Datos.canal)'" }
})
$respC2 = Invoke-Api -Metodo POST -Ruta '/api/contactos' -Token $TCA -Cuerpo ([ordered]@{ ofertaId = $O.O5; canal = 'WHATSAPP' })
[void](Test-Check -Hu 'US-14' -Escenario 'Esc 4 WhatsApp sobre O5 sin pedido' -Esperado 201 -Resp $respC2 -Validar {
    param($x)
    if (-not $x.Datos.fechaContacto) { 'fechaContacto es null' }
    if ($null -ne $x.Datos.pedidoId) { "pedidoId debería ser null, llegó '$($x.Datos.pedidoId)'" }
})
$respC3 = Invoke-Api -Metodo POST -Ruta '/api/contactos' -Token $TCA -Cuerpo ([ordered]@{ ofertaId = $O.O1; canal = 'LLAMADA' })
[void](Test-Check -Hu 'US-15' -Escenario 'Esc 3 llamada sobre O1' -Esperado 201 -Resp $respC3 -Validar {
    param($x)
    if (-not $x.Datos.fechaContacto) { 'fechaContacto es null' }
    if ($x.Datos.canal -ne 'LLAMADA') { "canal '$($x.Datos.canal)'" }
})
$r = Invoke-Api -Metodo POST -Ruta '/api/contactos' -Token $TCA -Cuerpo ([ordered]@{ ofertaId = $O.O6; canal = 'WHATSAPP' })
[void](Test-Check -Hu 'US-14' -Escenario 'WhatsApp sobre O6 (productor B con canales apagados)' -Esperado 422 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.CanalNoHabilitado
})
$r = Invoke-Api -Metodo POST -Ruta '/api/contactos' -Token $TCB -Cuerpo ([ordered]@{ ofertaId = $O.O1; canal = 'WHATSAPP'; pedidoId = $P1 })
[void](Test-Check -Hu 'US-19' -Escenario 'Comprador B usando el pedido P1 de comprador A' -Esperado 422 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.PedidoNoCorresponde
})
$respC4 = Invoke-Api -Metodo POST -Ruta '/api/contactos' -Token $TCA -Cuerpo ([ordered]@{ ofertaId = $O.O4; canal = 'WHATSAPP' })
[void](Test-Check -Hu 'US-19' -Escenario 'Contacto sobre O4 agotada se registra igual' -Esperado 201 -Resp $respC4)

$ContactosEsperados = @($respC4.Datos.id, $respC3.Datos.id, $respC2.Datos.id, $respC1.Datos.id)
$r = Invoke-Api -Metodo GET -Ruta '/api/contactos/mios' -Token $TCA
[void](Test-Check -Hu 'US-19' -Escenario 'Esc 2 historial del comprador A en orden descendente, sin números' -Esperado 200 -Resp $r -Validar {
    param($x)
    $lista = @($x.Datos)
    $ids = @($lista | ForEach-Object { $_.id })
    if (($ids -join ',') -ne ($ContactosEsperados -join ',')) { "orden/ids inesperados: $($ids.Count) registros" }
    for ($i = 1; $i -lt $lista.Count; $i++) {
        if ([string]::CompareOrdinal([string]$lista[$i - 1].fechaContacto, [string]$lista[$i].fechaContacto) -lt 0) { "fechaContacto no descendente en la posición $i" }
    }
    foreach ($c in $lista) {
        if (-not $c.producto -or $c.producto -cmatch '^[A-Z_]+$') { "producto '$($c.producto)' no es texto para mostrar" }
        if ($c.productorNombre -ne $NombreProductorA) { "productorNombre '$($c.productorNombre)'" }
        if ($c.fincaNombre -ne $FincaA) { "fincaNombre '$($c.fincaNombre)'" }
        $numeros = @(Get-Propiedades $c | Where-Object { $_ -match '(?i)whatsapp|telefono' })
        if ($numeros.Count -gt 0) { "expone números: $($numeros -join ', ')" }
    }
})
$r = Invoke-Api -Metodo GET -Ruta '/api/contactos/mios' -Token $TCB
[void](Test-Check -Hu 'US-19' -Escenario 'Esc 3 comprador B sin contactos responde []' -Esperado 200 -Resp $r -Validar {
    param($x) if (-not (Test-ListaVacia $x)) { "se esperaba [], llegó: $($x.Texto)" }
})
$r = Invoke-Api -Metodo GET -Ruta '/api/contactos/mios' -Token $TPA
[void](Test-Check -Hu 'US-19' -Escenario 'Historial consultado por un productor' -Esperado 403 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.RolNoPermitido
})
$r = Invoke-Api -Metodo GET -Ruta '/api/contactos/mios'
[void](Test-Check -Hu 'US-19' -Escenario 'Historial sin token' -Esperado 401 -Resp $r)

# ===========================================================================
# E. US-23 (AB#54)
# ===========================================================================
Write-Seccion 'E. US-23 Pedidos recibidos (AB#54)'

$r = Invoke-Api -Metodo GET -Ruta '/api/pedidos/recibidos' -Token $TPA
[void](Test-Check -Hu 'US-23' -Escenario 'Esc 1 productor A ve sus pedidos en orden descendente con datos del comprador' -Esperado 200 -Resp $r -Validar {
    param($x)
    $lista = @($x.Datos)
    $ids = @($lista | ForEach-Object { $_.id })
    if (($ids -join ',') -ne (@($P2, $P1) -join ',')) { "se esperaban [P2, P1], llegaron $($ids.Count) pedidos" }
    for ($i = 1; $i -lt $lista.Count; $i++) {
        if ([string]::CompareOrdinal([string]$lista[$i - 1].fechaCreacion, [string]$lista[$i].fechaCreacion) -lt 0) { "fechaCreacion no descendente en la posición $i" }
    }
    foreach ($p in $lista) {
        if ($p.comprador.nombre -ne $NombreCompradorA) { "comprador.nombre '$($p.comprador.nombre)'" }
        if (-not $p.comprador.nombreNegocio) { 'comprador.nombreNegocio vacío' }
        if (-not $p.comprador.whatsapp -and -not $p.comprador.telefono) { 'sin datos de contacto del comprador' }
    }
})
$r = Invoke-Api -Metodo GET -Ruta '/api/pedidos/recibidos' -Token $TPB
[void](Test-Check -Hu 'US-23' -Escenario 'Esc 2/3 productor B no ve pedidos de A (aislamiento)' -Esperado 200 -Resp $r -Validar {
    param($x) if (-not (Test-ListaVacia $x)) { "se esperaba [], llegó: $($x.Texto)" }
})
$r = Invoke-Api -Metodo GET -Ruta '/api/pedidos/recibidos' -Token $TCA
[void](Test-Check -Hu 'US-23' -Escenario 'Pedidos recibidos consultados por un comprador' -Esperado 403 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.RolNoPermitido
})

# ===========================================================================
# F. US-10 (AB#38)
# ===========================================================================
Write-Seccion 'F. US-10 Eliminar oferta (AB#38)'

$r = Invoke-Api -Metodo DELETE -Ruta "/api/ofertas/$($O.O3)" -Token $TPA
[void](Test-Check -Hu 'US-10' -Escenario 'Esc 1 eliminar O3 sin pedidos ni contactos' -Esperado 204 -Resp $r)
$r = Invoke-Api -Metodo GET -Ruta "/api/ofertas/$($O.O3)"
[void](Test-Check -Hu 'US-10' -Escenario 'Esc 1 O3 ya no tiene detalle' -Esperado 404 -Resp $r)
$r = Invoke-Api -Metodo GET -Ruta '/api/ofertas/mis-ofertas' -Token $TPA
[void](Test-Check -Hu 'US-10' -Escenario 'Esc 1 O3 no aparece en mis-ofertas' -Esperado 200 -Resp $r -Validar {
    param($x)
    $ids = @(@($x.Datos) | ForEach-Object { $_.id })
    if ($ids -contains $O.O3) { 'O3 sigue en mis-ofertas' }
    if ($ids -notcontains $O.O1) { 'O1 no aparece en mis-ofertas (lista inesperada)' }
})
$r = Invoke-Api -Metodo DELETE -Ruta "/api/ofertas/$($O.O1)" -Token $TPA
[void](Test-Check -Hu 'US-10' -Escenario 'Esc 2 eliminar O1 con pedidos y contactos' -Esperado 422 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.OfertaConPedidos
})
$r = Invoke-Api -Metodo DELETE -Ruta "/api/ofertas/$($O.O5)" -Token $TPA
[void](Test-Check -Hu 'US-10' -Escenario 'Esc 3 eliminar O5 solo con contactos' -Esperado 422 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.OfertaConContactos
})
$r = Invoke-Api -Metodo DELETE -Ruta "/api/ofertas/$($O.O1)" -Token $TPB
[void](Test-Check -Hu 'US-10' -Escenario 'Productor B eliminando O1 (ajena)' -Esperado 403 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.OfertaAjena
})
$r = Invoke-Api -Metodo DELETE -Ruta "/api/ofertas/$($O.O1)" -Token $TCA
[void](Test-Check -Hu 'US-10' -Escenario 'Comprador eliminando una oferta' -Esperado 403 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.RolNoPermitido
})
$r = Invoke-Api -Metodo DELETE -Ruta "/api/ofertas/$([guid]::NewGuid())" -Token $TPA
[void](Test-Check -Hu 'US-10' -Escenario 'Eliminar oferta inexistente' -Esperado 404 -Resp $r -Validar {
    param($x) Assert-Mensaje $x $MSG.OfertaNoExiste
})

# ===========================================================================
# G. US-17 (AB#47)
# ===========================================================================
Write-Seccion 'G. US-17 Canales de contacto (AB#47)'

$r = Invoke-Api -Metodo PATCH -Ruta '/api/usuarios/perfil/productor' -Token $TPB -Cuerpo ([ordered]@{ canalWhatsappHabilitado = $true })
[void](Test-Check -Hu 'US-17' -Escenario 'Esc 2 productor B habilita WhatsApp sin número guardado' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'whatsapp' $MSG.WhatsappRequerido
})
$r = Invoke-Api -Metodo PATCH -Ruta '/api/usuarios/perfil/productor' -Token $TPB -Cuerpo ([ordered]@{ telefono = ''; canalLlamadaHabilitado = $true })
[void](Test-Check -Hu 'US-17' -Escenario 'Esc 2 productor B habilita llamada con teléfono vacío' -Esperado 400 -Resp $r -Validar {
    param($x) Assert-ErrorCampo $x 'telefono' $MSG.TelefonoRequerido
})
$r = Invoke-Api -Metodo PATCH -Ruta '/api/usuarios/perfil/productor' -Token $TPB -Cuerpo ([ordered]@{ canalWhatsappHabilitado = $false; canalLlamadaHabilitado = $false })
[void](Test-Check -Hu 'US-17' -Escenario 'Esc 3 ambos canales en false' -Esperado 200 -Resp $r -Validar {
    param($x)
    if ($x.Datos.canalWhatsappHabilitado -ne $false -or $x.Datos.canalLlamadaHabilitado -ne $false) { 'algún canal quedó habilitado' }
    if (-not $x.Datos.telefono) { 'el teléfono guardado se borró pese al 400 anterior' }
})

# ===========================================================================
# H. Formato de errores (US-22, AB#52)
# ===========================================================================
Write-Seccion 'H. Formato de errores (US-22, AB#52)'

$revisadas = @($Respuestas4xx5xx | Where-Object { $_.Status -ne 401 })
$problemas = New-Object System.Collections.Generic.List[string]
foreach ($e in $revisadas) {
    $props = Get-Propiedades $e.Datos
    $faltan = @('timestamp', 'status', 'error', 'mensaje', 'ruta' | Where-Object { $props -notcontains $_ })
    $etiqueta = "$($e.Metodo) $($e.Ruta) -> $($e.Status)"
    if ($null -eq $e.Datos) { $problemas.Add("$etiqueta sin cuerpo JSON"); continue }
    if ($faltan.Count -gt 0) { $problemas.Add("$etiqueta sin: $($faltan -join ', ')") }
    if ($props -contains 'status' -and [int]$e.Datos.status -ne $e.Status) { $problemas.Add("$etiqueta con status $($e.Datos.status) en el cuerpo") }
    $tieneErrores = $props -contains 'errores'
    if ($e.Status -eq 400 -and -not $tieneErrores) { $problemas.Add("$etiqueta es un 400 por campo sin 'errores'") }
    if ($e.Status -ne 400 -and $tieneErrores) { $problemas.Add("$etiqueta trae 'errores' sin ser un 400 por campo") }
}
$resp22 = [pscustomobject]@{ Status = 200; Datos = $null; Texto = ''; Headers = @{} }
[void](Test-Check -Hu 'US-22' -Escenario "Formato ErrorResponse en las $($revisadas.Count) respuestas de error (sin 401)" -Esperado 200 -Resp $resp22 -Validar {
    param($x) foreach ($p in $problemas) { $p }
})

# ===========================================================================
# I. Fechas con el offset de Colombia (US-22, AB#52)
# ===========================================================================
Write-Seccion 'I. Fechas con el offset de Colombia (US-22, AB#52)'

# La fecha debe llevar -05:00 y estar a ±5 minutos de la hora de Colombia en que se hizo la petición
function Test-FechaColombia([string]$Etiqueta, [string]$Esperado, $Resp, [scriptblock]$Valor) {
    $texto = if ($Resp) { [string](& $Valor $Resp) } else { '' }
    [void](Test-Check -Hu 'US-22' -Escenario "I $Etiqueta con offset -05:00 y hora de la corrida" -Esperado $Esperado -Resp $Resp -Validar {
        param($x)
        if (-not $texto) { return 'sin valor' }
        if ($texto -notmatch '-05:00$') { return 'no termina en -05:00' }
        $medioUtc = $x.InicioUtc.AddTicks([long](($x.FinUtc - $x.InicioUtc).Ticks / 2))
        $instanteUtc = [DateTimeOffset]::Parse($texto, [Globalization.CultureInfo]::InvariantCulture).UtcDateTime
        $diferencia = [math]::Abs(($instanteUtc - $medioUtc).TotalSeconds)
        if ($diferencia -gt 300) { "a $([math]::Round($diferencia)) s de la hora de la corrida (máximo 300 s)" }
    })
    if ($Resp) {
        $medioCol = ConvertTo-HoraColombia $Resp.InicioUtc.AddTicks([long](($Resp.FinUtc - $Resp.InicioUtc).Ticks / 2))
        Write-Host ("      valor: '{0}' · hora Colombia de la petición: {1:yyyy-MM-ddTHH:mm:ss}" -f $texto, $medioCol)
    }
}
Test-FechaColombia 'fechaCreacion del pedido P1' '201' $respP1 { param($x) $x.Datos.fechaCreacion }
Test-FechaColombia 'fechaContacto del contacto C1' '201' $respC1 { param($x) $x.Datos.fechaContacto }
Test-FechaColombia 'timestamp de ErrorResponse (400 sin dirección)' '400' $respSinDireccion { param($x) $x.Datos.timestamp }
Test-FechaColombia 'fechaCreacion de la oferta O1' '201' $RespOferta['O1'] { param($x) $x.Datos.fechaCreacion }
Test-FechaColombia 'usuario.fechaRegistro del login del comprador A' '200' $RespLogin['CompradorA'] { param($x) $x.Datos.usuario.fechaRegistro }

# ---------------------------------------------------------------------------
# Retirar del catálogo público las ofertas smoke que siguen activas
# ---------------------------------------------------------------------------
if (-not $ConservarOfertasActivas) {
    Write-Seccion 'Cierre: marcar como AGOTADA las ofertas smoke activas'
    foreach ($par in @(@('O1', $TPA), @('O2', $TPA), @('O5', $TPA), @('O6', $TPB))) {
        $x = Invoke-Api -Metodo PATCH -Ruta "/api/ofertas/$($O[$par[0]])/estado" -Token $par[1] -Cuerpo ([ordered]@{ estadoPublicacion = 'AGOTADA' }) -SinRegistrar
        $icono = if ($x.Status -eq 200) { $ICONO_OK } else { $ICONO_AVISO }
        Write-Host "$icono $($par[0]) -> HTTP $($x.Status)"
    }
}

Write-Resumen
$fallidos = @($Resultados | Where-Object { $_.Grupo -eq 'API' -and -not $_.Ok }).Count
if ($fallidos -gt 0) { exit 1 }
exit 0
