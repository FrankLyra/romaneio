$ErrorActionPreference = 'Stop'

$jarPath = Join-Path $PSScriptRoot 'build\libs\romaneio-1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "JAR do backend não encontrado: $jarPath"
}

$apiKey = $env:ROMANEIO_GOOGLE_MAPS_API_KEY
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $secureKey = Read-Host 'Chave Google Maps (oculta; Enter inicia sem cálculo de distância)' -AsSecureString
    if ($secureKey.Length -gt 0) {
        $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
        try {
            $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
        }
        finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
        }
    }
}

$tempDir = 'C:\t'
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

$previousTemp = $env:TEMP
$previousTmp = $env:TMP
$previousApiKey = $env:ROMANEIO_GOOGLE_MAPS_API_KEY
try {
    $env:TEMP = $tempDir
    $env:TMP = $tempDir
    if ([string]::IsNullOrWhiteSpace($apiKey)) {
        Remove-Item Env:ROMANEIO_GOOGLE_MAPS_API_KEY -ErrorAction SilentlyContinue
        Write-Host 'Backend iniciando sem cálculo viário. Você ainda pode importar e classificar rotas.'
    }
    else {
        $env:ROMANEIO_GOOGLE_MAPS_API_KEY = $apiKey
    }

    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        & $javaCommand.Source '-Djava.io.tmpdir=C:\t' '-jar' $jarPath
    }
    elseif ($env:JAVA_HOME) {
        & (Join-Path $env:JAVA_HOME 'bin\java.exe') '-Djava.io.tmpdir=C:\t' '-jar' $jarPath
    }
    else {
        throw 'Java não encontrado no PATH nem em JAVA_HOME. Instale/configure o Java 21.'
    }
}
finally {
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
    $env:ROMANEIO_GOOGLE_MAPS_API_KEY = $previousApiKey
    $apiKey = $null
    $secureKey = $null
}
