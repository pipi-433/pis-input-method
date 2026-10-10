Add-Type -AssemblyName System.Drawing
$Out='D:/py/to1/pis-fcitx/docs/designs/pis-feedback-b.png'
$Bmp=[Drawing.Bitmap]::new(1440,820)
$G=[Drawing.Graphics]::FromImage($Bmp)
$G.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
$G.TextRenderingHint=[Drawing.Text.TextRenderingHint]::AntiAliasGridFit
function MakeBrush($c){[Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($c))}
function MakePen($c,[float]$w=1){[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml($c),$w)}
function C([int[]]$a){-join ($a | ForEach-Object {[char]$_})}
function DrawText($s,[float]$x,[float]$y,[float]$size,$c='#26303A',$font='Microsoft JhengHei UI',[Drawing.FontStyle]$style=[Drawing.FontStyle]::Regular){$G.DrawString([string]$s,[Drawing.Font]::new($font,$size,$style),(MakeBrush $c),$x,$y)}
function DrawRounded($x,$y,$w,$h,$radius,$fill,$stroke='#D5D9DC'){$path=[Drawing.Drawing2D.GraphicsPath]::new();$d=$radius*2;$path.AddArc($x,$y,$d,$d,180,90);$path.AddArc($x+$w-$d,$y,$d,$d,270,90);$path.AddArc($x+$w-$d,$y+$h-$d,$d,$d,0,90);$path.AddArc($x,$y+$h-$d,$d,$d,90,90);$path.CloseFigure();$G.FillPath((MakeBrush $fill),$path);$G.DrawPath((MakePen $stroke 1),$path);$path.Dispose()}
$G.Clear([Drawing.ColorTranslator]::FromHtml('#F8F7F4'))
DrawText 'pis · feedback concept B' 55 28 20 '#7A8187' 'Segoe UI'
DrawText 'RIGHT FEEDBACK MODULE' 1110 32 13 '#8C9398' 'Segoe UI' ([Drawing.FontStyle]::Bold)
DrawRounded 50 90 1340 605 26 '#E8EAEC'
DrawRounded 50 90 1340 180 26 '#F2F3F4'
$G.FillRectangle((MakeBrush '#F2F3F4'),50,135,1340,135)
$good=(C @(0x597D,0x7684));$welldone=(C @(0x597D,0x5F97));$number=(C @(0x53F7,0x7684))
DrawText 'good' 105 110 15 '#707C86' 'Segoe UI';DrawText $good 105 134 28 '#202830'
DrawText 'well done' 310 110 15 '#707C86' 'Segoe UI';DrawText $welldone 310 134 28 '#202830'
DrawText $number 525 134 28 '#202830'
$G.DrawLine((MakePen '#D4D8DA' 1),1035,105,1035,160)
$dc=@('#E86A55','#E8B73F','#62B16A');for($i=0;$i -lt 3;$i++){$G.FillEllipse((MakeBrush $dc[$i]),1128+$i*22,108,11,11)}
DrawRounded 1110 129 190 30 8 '#E5E7E8' '#D1D5D7'
for($rr=0;$rr -lt 3;$rr++){for($cc=0;$cc -lt 16;$cc++){$col=if($rr -eq 1 -and $cc -ge 5 -and $cc -le 9){'#D6B785'}else{'#BEC4C8'};$G.FillEllipse((MakeBrush $col),1122+$cc*10,136+$rr*7,4,4)}}
DrawText '⌄' 1348 128 22 '#687177' 'Segoe UI'
$keys='QWERTYUIOP'.ToCharArray();for($i=0;$i -lt 10;$i++){$x=75+$i*128;DrawRounded $x 226 112 112 13 '#FFFFFF' '#D7DADD';DrawText ([string]$keys[$i]) ($x+34) 258 33 '#252D34' 'Segoe UI'}
$lower='ASDFGHJKL'.ToCharArray();for($i=0;$i -lt 9;$i++){$x=140+$i*128;DrawRounded $x 368 112 100 13 '#FFFFFF' '#D7DADD';DrawText ([string]$lower[$i]) ($x+38) 397 31 '#252D34' 'Segoe UI'}
DrawText (C @(0x6587,0x5B57,0x533A,0x56FA,0x5B9A,0x5BBD,0x5EA6,0x20,0xB7,0x20,0x70B9,0x9635,0x53EA,0x5728,0x53F3,0x4FA7,0x77ED,0x4FC3,0x53CD,0x9988,0x20,0xB7,0x20,0x95F2,0x7F6E,0x4E0D,0x5FAA,0x73AF)) 55 735 18 '#646E76'
DrawText 'B' 1352 737 30 '#A4AAAE' 'Segoe UI' ([Drawing.FontStyle]::Bold)
$G.Dispose();$Bmp.Save($Out,[Drawing.Imaging.ImageFormat]::Png);$Bmp.Dispose();$Out



