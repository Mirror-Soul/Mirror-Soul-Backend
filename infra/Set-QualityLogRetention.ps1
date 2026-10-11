[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$LogGroupName,
    [ValidateNotNullOrEmpty()][string]$Region = 'ap-northeast-2',
    [ValidateSet(7,14,30,60,90,180,365)][int]$RetentionDays = 30
)
$ErrorActionPreference = 'Stop'
if ($PSCmdlet.ShouldProcess($LogGroupName, "Set CloudWatch log retention to $RetentionDays days")) {
    aws logs put-retention-policy --log-group-name $LogGroupName --retention-in-days $RetentionDays --region $Region
    if ($LASTEXITCODE -ne 0) { throw 'CloudWatch log retention update failed.' }
}
