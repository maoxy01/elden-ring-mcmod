[CmdletBinding()]
param([ValidateSet('build','client','server')][string]$Mode = 'build')
$ErrorActionPreference = 'Stop'
# GlobalMemoryStatusEx reports the remaining system commit budget, not disk free space.
if (-not ('PackMemoryStatus' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class PackMemoryStatus {
    [StructLayout(LayoutKind.Sequential)]
    public class Status {
        public uint length = (uint)Marshal.SizeOf(typeof(Status));
        public uint load;
        public ulong totalPhysical, availablePhysical, totalCommit, availableCommit;
        public ulong totalVirtual, availableVirtual, availableExtendedVirtual;
    }
    [DllImport("kernel32.dll", SetLastError=true)]
    public static extern bool GlobalMemoryStatusEx([In, Out] Status status);
}
'@
}
$memory = [PackMemoryStatus+Status]::new()
if (![PackMemoryStatus]::GlobalMemoryStatusEx($memory)) { throw 'Cannot read system memory budget; test not started' }
$minimumCommit = @{build=8GB; client=12GB; server=10GB}[$Mode]
$minimumPhysical = @{build=2GB; client=4GB; server=3GB}[$Mode]
$java = @(Get-Process java,javaw -ErrorAction SilentlyContinue)
if ($java.Count) { throw "Java is already running (PID $($java.Id -join ',')); close the previous test before starting another" }
Write-Host ('BUDGET mode={0} commitFreeGiB={1:N2} physicalFreeGiB={2:N2}' -f $Mode,($memory.availableCommit/1GB),($memory.availablePhysical/1GB))
if ($memory.availableCommit -lt $minimumCommit -or $memory.availablePhysical -lt $minimumPhysical) {
    throw "Insufficient headroom for $Mode; need at least $($minimumCommit/1GB) GiB commit and $($minimumPhysical/1GB) GiB available physical memory. Test not started."
}
Write-Host 'BUDGET_PASS: startup check only; monitor memory during the run as well.'
