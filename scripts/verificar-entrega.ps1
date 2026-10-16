# Revisión local de candidatos a Git; no hace HTTP, git add, push ni despliegues.
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$candidatos = @(& git -C $repoPath ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar el índice local de Git.' }
$hallazgos = [System.Collections.Generic.List[string]]::new()
$reglas = @{
    'identificador AWS' = '(?:AKIA|ASIA)[A-Z0-9]{16}'
    'posible JWT literal' = 'eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}'
    'clave privada' = '-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----'
    'posible token GitHub' = '(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})'
    'URL con usuario y contraseña' = '(?i)\b(?:https?|postgres(?:ql)?|mysql|mongodb(?:\+srv)?|redis)://[^\s/:@]+:[^\s/@]+@'
}
foreach ($relativo in ($candidatos | Sort-Object -Unique)) {
    if ($relativo -match '(^|/)\.env($|\.)' -and $relativo -notmatch '\.env\.example$') {
        $hallazgos.Add("Archivo privado candidato: $relativo")
    }
    if ($relativo -match '(?i)\.tfstate(?:\.|$)|\.tfvars(?:\.json)?$|\.tfplan$|(^|/)(\.terraform|\.aws)/|(^|/)credentials$|\.(pem|key|p12|pfx)$') {
        $hallazgos.Add("Archivo privado candidato: $relativo")
    }
    $absoluto = Join-Path $repoPath $relativo
    if (!(Test-Path -LiteralPath $absoluto -PathType Leaf)) { continue }
    if ($relativo -match '(?i)\.(png|jpg|jpeg|gif|pdf|zip|jar|class|ico|woff2?)$') { continue }
    $numero = 0
    foreach ($linea in [System.IO.File]::ReadLines($absoluto)) {
        $numero++
        foreach ($nombre in $reglas.Keys) {
            if ([regex]::IsMatch($linea, $reglas[$nombre])) {
                # Mostrar solo ubicación y regla; nunca el valor encontrado.
                $hallazgos.Add("${relativo}:${numero}: $nombre")
            }
        }
    }
}
if ($hallazgos.Count -gt 0) {
    $hallazgos | ForEach-Object { Write-Output $_ }
    throw 'Revisar los candidatos indicados antes de publicar. No se muestran valores.'
}
Write-Output "Revisión local completada: $($candidatos.Count) candidatos, sin señales detectadas por las reglas."
Write-Output 'Los patrones no detectan todos los secretos posibles; revisar manualmente antes de publicar.'
