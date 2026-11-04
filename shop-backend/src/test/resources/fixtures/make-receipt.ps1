Add-Type -AssemblyName System.Drawing
$out = Join-Path $PSScriptRoot 'receipt-sample.png'
$bmp = New-Object System.Drawing.Bitmap 1000, 760
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$title = New-Object System.Drawing.Font 'Malgun Gothic', 34, ([System.Drawing.FontStyle]::Bold)
$font = New-Object System.Drawing.Font 'Malgun Gothic', 24
$b = [System.Drawing.Brushes]::Black
$g.DrawString('거래명세서', $title, $b, 380, 30)
$g.DrawString('등록번호 124-81-00998', $font, $b, 40, 120)
$g.DrawString('상호 테스트상사', $font, $b, 40, 170)
$g.DrawString('거래일자 2026-11-03', $font, $b, 40, 220)
$g.DrawString('품목', $font, $b, 40, 300); $g.DrawString('수량', $font, $b, 430, 300)
$g.DrawString('단가', $font, $b, 600, 300); $g.DrawString('금액', $font, $b, 800, 300)
$g.DrawString('니트 블랙 FREE', $font, $b, 40, 360); $g.DrawString('10', $font, $b, 440, 360)
$g.DrawString('15,000', $font, $b, 590, 360); $g.DrawString('150,000', $font, $b, 790, 360)
$g.DrawString('슬랙스 베이지 M', $font, $b, 40, 420); $g.DrawString('5', $font, $b, 450, 420)
$g.DrawString('20,000', $font, $b, 590, 420); $g.DrawString('100,000', $font, $b, 790, 420)
$g.DrawString('합계금액', $font, $b, 40, 540); $g.DrawString('250,000', $font, $b, 790, 540)
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
$out
