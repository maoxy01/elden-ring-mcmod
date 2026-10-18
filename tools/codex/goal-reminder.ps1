$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

try {
    $eventData = [Console]::In.ReadToEnd() | ConvertFrom-Json
    $eventName = [string]$eventData.hook_event_name
    if ($eventName -notin @('SessionStart', 'UserPromptSubmit')) {
        throw 'Unsupported event for project goal reminder.'
    }

    $projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
    $focusPath = Join-Path $projectRoot 'docs/PROJECT-FOCUS.md'
    $context = [IO.File]::ReadAllText($focusPath, [Text.Encoding]::UTF8)

    # Context only: never block, rewrite a tool call, or start another turn.
    $result = @{
        hookSpecificOutput = @{
            hookEventName = $eventName
            additionalContext = $context
        }
    }
    [Console]::Out.WriteLine(($result | ConvertTo-Json -Depth 4 -Compress))
    exit 0
}
catch {
    [Console]::Error.WriteLine('Project goal reminder failed: ' + $_.Exception.Message)
    exit 1
}
