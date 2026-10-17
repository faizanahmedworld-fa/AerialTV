# -Res draws the Android icons and banner, -Windows draws icon.ico for the Windows app.
param([string]$Res, [string]$Windows)
Add-Type -AssemblyName System.Drawing

$navy = [System.Drawing.Color]::FromArgb(255, 14, 26, 58)
$amber = [System.Drawing.Color]::FromArgb(255, 255, 176, 32)
$blue = [System.Drawing.Color]::FromArgb(255, 47, 107, 255)
$white = [System.Drawing.Color]::White

function RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

# Draws the TV mark in the 108 unit grid used by the vector icon, scaled by $s and offset.
function DrawMark($g, [float]$s, [float]$ox, [float]$oy) {
    $pen = New-Object System.Drawing.Pen($amber, (3.5 * $s))
    $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
    $g.DrawLine($pen, $ox + 54 * $s, $oy + 40 * $s, $ox + 42 * $s, $oy + 27 * $s)
    $g.DrawLine($pen, $ox + 54 * $s, $oy + 40 * $s, $ox + 66 * $s, $oy + 27 * $s)
    $b = New-Object System.Drawing.SolidBrush($amber)
    $g.FillEllipse($b, $ox + 39 * $s, $oy + 24 * $s, 6 * $s, 6 * $s)
    $g.FillEllipse($b, $ox + 63 * $s, $oy + 24 * $s, 6 * $s, 6 * $s)
    $g.FillPath((New-Object System.Drawing.SolidBrush($white)), (RoundRect ($ox + 30 * $s) ($oy + 40 * $s) (48 * $s) (34 * $s) (7 * $s)))
    $g.FillPath((New-Object System.Drawing.SolidBrush($blue)), (RoundRect ($ox + 35 * $s) ($oy + 45 * $s) (38 * $s) (24 * $s) (4 * $s)))
    $tri = [System.Drawing.PointF[]]@(
        (New-Object System.Drawing.PointF(($ox + 50 * $s), ($oy + 51 * $s))),
        (New-Object System.Drawing.PointF(($ox + 60 * $s), ($oy + 57 * $s))),
        (New-Object System.Drawing.PointF(($ox + 50 * $s), ($oy + 63 * $s))))
    $g.FillPolygon((New-Object System.Drawing.SolidBrush($white)), $tri)
}

function NewCanvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::Transparent)
    return @($bmp, $g)
}

if ($Res) {
    $sizes = @{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }
    foreach ($k in $sizes.Keys) {
        $n = $sizes[$k]
        $bmp, $g = NewCanvas $n $n
        $g.FillPath((New-Object System.Drawing.SolidBrush($navy)), (RoundRect 0 0 ($n - 1) ($n - 1) ($n * 0.2)))
        # Legacy icons have no safe zone crop, so the mark is drawn larger than in the adaptive icon.
        $s = $n / 72.0
        DrawMark $g $s (-18 * $s) (-14 * $s)
        $dir = Join-Path $Res "mipmap-$k"
        New-Item -ItemType Directory -Force $dir | Out-Null
        $bmp.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
    }

    # Android TV home screen banner, 320x180 at xhdpi.
    $bmp, $g = NewCanvas 320 180
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0, 0)), (New-Object System.Drawing.Point(320, 180)),
        $navy, [System.Drawing.Color]::FromArgb(255, 7, 13, 31))
    $g.FillRectangle($grad, 0, 0, 320, 180)
    DrawMark $g 1.25 (-18) 2
    $font = New-Object System.Drawing.Font('Segoe UI', 30, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $small = New-Object System.Drawing.Font('Segoe UI', 15, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $g.DrawString('Aerial', $font, (New-Object System.Drawing.SolidBrush($white)), 112, 56)
    $g.DrawString('TV', $font, (New-Object System.Drawing.SolidBrush($amber)), 203, 56)
    $g.DrawString('Free TV, just switch on', $small, (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 184, 194, 220))), 114, 96)
    $dir = Join-Path $Res 'drawable-xhdpi'
    New-Item -ItemType Directory -Force $dir | Out-Null
    $bmp.Save((Join-Path $dir 'banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# Windows app icon: one .ico holding PNG images from 16 to 256 px.
if ($Windows) {
    New-Item -ItemType Directory -Force $Windows | Out-Null
    $pngs = @()
    foreach ($n in @(16, 24, 32, 48, 64, 128, 256)) {
        $bmp, $g = NewCanvas $n $n
        $g.FillPath((New-Object System.Drawing.SolidBrush($navy)), (RoundRect 0 0 ($n - 1) ($n - 1) ($n * 0.2)))
        $s = $n / 72.0
        DrawMark $g $s (-18 * $s) (-14 * $s)
        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $pngs += , @($n, $ms.ToArray())
        $g.Dispose(); $bmp.Dispose()
    }
    $out = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($out)
    $w.Write([UInt16]0); $w.Write([UInt16]1); $w.Write([UInt16]$pngs.Count)
    $offset = 6 + 16 * $pngs.Count
    foreach ($p in $pngs) {
        $n = $p[0]; $data = $p[1]
        $w.Write([byte]($n % 256)); $w.Write([byte]($n % 256)); $w.Write([byte]0); $w.Write([byte]0)
        $w.Write([UInt16]1); $w.Write([UInt16]32); $w.Write([UInt32]$data.Length); $w.Write([UInt32]$offset)
        $offset += $data.Length
    }
    foreach ($p in $pngs) { $w.Write([byte[]]$p[1]) }
    $w.Flush()
    [System.IO.File]::WriteAllBytes((Join-Path $Windows 'icon.ico'), $out.ToArray())
}

'done'

