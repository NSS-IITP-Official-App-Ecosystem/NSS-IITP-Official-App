# Auto ADB Reverse Daemon
# Keeps adb reverse tcp:5001 tcp:5001 alive for all connected devices/emulators

Write-Host "Starting Auto ADB Reverse Daemon..."
while ($true) {
    try {
        $devices = adb devices
        foreach ($line in $devices) {
            $trimmed = $line.Replace("`r", "").Trim()
            if ($trimmed -match '\s+device$') {
                $serial = ($trimmed -split "\s+")[0]
                if ($serial) {
                    # Check if port 5001 is already reversed
                    $rules = adb -s $serial reverse --list 2>&1
                    $alreadyReversed = $false
                    foreach ($rule in $rules) {
                        $ruleStr = $rule.ToString()
                        if ($ruleStr.Replace("`r", "") -match 'tcp:5001\s+tcp:5001') {
                            $alreadyReversed = $true
                            break
                        }
                    }

                    if (-not $alreadyReversed) {
                        Write-Host "Setting up adb reverse tcp:5001 tcp:5001 for device: $serial"
                        adb -s $serial reverse tcp:5001 tcp:5001
                    }
                }
            }
        }
    } catch {
        Write-Host "Error in loop: $_"
    }
    Start-Sleep -Seconds 5
}
