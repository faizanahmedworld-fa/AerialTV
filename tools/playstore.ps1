# Draws the Play Store graphics into docs/playstore: the 512 icon, the 1024x500 feature graphic
# and the 1280x720 TV banner, from the same shapes as the launcher icon.
param([string]$Out = (Join-Path $PSScriptRoot '..\docs\playstore'))

# Borrow the colours, RoundRect, DrawMark and NewCanvas helpers. Called with no switches,
# icons.ps1 only defines them.
. (Join-Path $PSScriptRoot 'icons.ps1') | Out-Null

New-Item -ItemType Directory -Force $Out | Out-Null
$muted = [System.Drawing.Color]::FromArgb(255, 184, 194, 220)

function Title($g, [float]$x, [float]$y, [float]$px) {
    $font = New-Object System.Drawing.Font('Segoe UI', $px, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $g.DrawString('Aerial', $font, (New-Object System.Drawing.SolidBrush($white)), $x, $y)
    $w = $g.MeasureString('Aerial ', $font).Width
    $g.DrawString('TV', $font, (New-Object System.Drawing.SolidBrush($amber)), $x + $w - ($px * 0.25), $y)
}

# Icon 512 x 512, no transparency (Play rejects alpha here).
$bmp, $g = NewCanvas 512 512
$g.Clear($navy)
DrawMark $g (512 / 72.0) (-18 * 512 / 72.0) (-14 * 512 / 72.0)
$bmp.Save((Join-Path $Out 'Icon 512.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# Feature graphic 1024 x 500.
$bmp, $g = NewCanvas 1024 500
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)), (New-Object System.Drawing.Point(1024, 500)),
    $navy, [System.Drawing.Color]::FromArgb(255, 7, 13, 31))
$g.FillRectangle($grad, 0, 0, 1024, 500)
DrawMark $g 3.6 (-20) 60
Title $g 380 150 88
$small = New-Object System.Drawing.Font('Segoe UI', 30, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString('Free live TV from 189 countries', $small, (New-Object System.Drawing.SolidBrush($muted)), 386, 262)
$g.DrawString('No account. No subscription. Just switch on.', $small, (New-Object System.Drawing.SolidBrush($muted)), 386, 306)
$bmp.Save((Join-Path $Out 'Feature graphic.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# TV banner 1280 x 720 for the Android TV section of the listing.
$bmp, $g = NewCanvas 1280 720
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)), (New-Object System.Drawing.Point(1280, 720)),
    $navy, [System.Drawing.Color]::FromArgb(255, 7, 13, 31))
$g.FillRectangle($grad, 0, 0, 1280, 720)
DrawMark $g 5.0 (40) 110
Title $g 520 250 120
$small = New-Object System.Drawing.Font('Segoe UI', 36, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString('Free live TV, just switch on', $small, (New-Object System.Drawing.SolidBrush($muted)), 528, 400)
$bmp.Save((Join-Path $Out 'TV banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

'play store graphics written to ' + $Out
