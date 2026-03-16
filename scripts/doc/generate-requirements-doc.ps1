param(
    [string]$SourcePath = "D:\javacode\smart-exam-cloud\docs\需求分析阶段文档.md",
    [string]$OutputPath = "D:\javacode\smart-exam-cloud\docs\智能考试云平台需求分析阶段文档.docx",
    [string]$DesktopOutputPath = "C:\Users\fan\Desktop\智能考试云平台需求分析阶段文档.docx"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Add-Paragraph {
    param(
        $Selection,
        $Document,
        [string]$StyleName,
        [string]$Text,
        [int]$Alignment = 0,
        [int]$SpaceAfter = 0,
        [double]$FirstLineIndent = [double]::NaN
    )

    $Selection.Style = $Document.Styles.Item($StyleName)
    $Selection.ParagraphFormat.Alignment = $Alignment
    $Selection.ParagraphFormat.SpaceAfter = $SpaceAfter
    if (-not [double]::IsNaN($FirstLineIndent)) {
        $Selection.ParagraphFormat.FirstLineIndent = $FirstLineIndent
    }
    $Selection.TypeText($Text)
    $Selection.TypeParagraph()
}

function Add-Image {
    param(
        $Selection,
        $Document,
        [string]$ImagePath,
        [string]$Caption
    )

    if (-not (Test-Path $ImagePath)) {
        throw "Image file not found: $ImagePath"
    }

    $Selection.Style = $Document.Styles.Item("正文")
    $Selection.ParagraphFormat.Alignment = 1
    $Selection.ParagraphFormat.SpaceAfter = 6
    $Selection.ParagraphFormat.FirstLineIndent = 0
    $shape = $Selection.InlineShapes.AddPicture($ImagePath)
    $shape.LockAspectRatio = -1
    $maxWidth = $Document.PageSetup.PageWidth - $Document.PageSetup.LeftMargin - $Document.PageSetup.RightMargin - 18
    if ($shape.Width -gt $maxWidth) {
        $shape.Width = $maxWidth
    }
    $Selection.TypeParagraph()

    Add-Paragraph -Selection $Selection -Document $Document -StyleName "正文" -Text $Caption -Alignment 1 -SpaceAfter 6 -FirstLineIndent 0
}

function Add-MarkdownLines {
    param(
        [string[]]$Lines,
        $Selection,
        $Document,
        [string]$BaseDirectory
    )

    foreach ($line in $Lines) {
        if ($line -match "^#\s+(.+)$") {
            Add-Paragraph -Selection $Selection -Document $Document -StyleName "标题 1" -Text $Matches[1] -SpaceAfter 6
            continue
        }

        if ($line -match "^##\s+(.+)$") {
            Add-Paragraph -Selection $Selection -Document $Document -StyleName "标题 2" -Text $Matches[1] -SpaceAfter 3
            continue
        }

        if ($line -match "^###\s+(.+)$") {
            Add-Paragraph -Selection $Selection -Document $Document -StyleName "标题 3" -Text $Matches[1] -SpaceAfter 0
            continue
        }

        if ($line -match "^!\[(.*)\]\((.+)\)$") {
            $caption = $Matches[1]
            $imageRef = $Matches[2]
            $imagePath = if ([System.IO.Path]::IsPathRooted($imageRef)) {
                $imageRef
            } else {
                Join-Path $BaseDirectory $imageRef
            }
            Add-Image -Selection $Selection -Document $Document -ImagePath ([System.IO.Path]::GetFullPath($imagePath)) -Caption $caption
            continue
        }

        if ($line -match "^- (.+)$") {
            Add-Paragraph -Selection $Selection -Document $Document -StyleName "正文" -Text ("• " + $Matches[1])
            continue
        }

        if ([string]::IsNullOrWhiteSpace($line)) {
            $Selection.TypeParagraph()
            continue
        }

        Add-Paragraph -Selection $Selection -Document $Document -StyleName "正文" -Text $line
    }
}

function Get-Sections {
    param([string[]]$Lines)

    $sections = @()
    $current = $null

    foreach ($line in $Lines) {
        if ($line -match "^#\s+") {
            if ($null -ne $current) {
                $sections += ,$current
            }

            $current = [pscustomobject]@{
                Heading = $line
                Lines   = @($line)
            }
            continue
        }

        if ($null -ne $current) {
            $current.Lines += $line
        }
    }

    if ($null -ne $current) {
        $sections += ,$current
    }

    return $sections
}

function Set-DocumentStyles {
    param($Word, $Document)

    $Document.PageSetup.PaperSize = 7
    $Document.PageSetup.TopMargin = $Word.CentimetersToPoints(2.54)
    $Document.PageSetup.BottomMargin = $Word.CentimetersToPoints(2.54)
    $Document.PageSetup.LeftMargin = $Word.CentimetersToPoints(3.17)
    $Document.PageSetup.RightMargin = $Word.CentimetersToPoints(3.17)

    $body = $Document.Styles.Item("正文")
    $body.Font.NameFarEast = "宋体"
    $body.Font.Name = "Times New Roman"
    $body.Font.Size = 12
    $body.ParagraphFormat.LineSpacingRule = 1
    $body.ParagraphFormat.LineSpacing = 18
    $body.ParagraphFormat.FirstLineIndent = $Word.CentimetersToPoints(0.74)

    $title = $Document.Styles.Item("标题")
    $title.Font.NameFarEast = "黑体"
    $title.Font.Size = 22

    $subtitle = $Document.Styles.Item("副标题")
    $subtitle.Font.NameFarEast = "宋体"
    $subtitle.Font.Size = 14

    $heading1 = $Document.Styles.Item("标题 1")
    $heading1.Font.NameFarEast = "黑体"
    $heading1.Font.Size = 16

    $heading2 = $Document.Styles.Item("标题 2")
    $heading2.Font.NameFarEast = "黑体"
    $heading2.Font.Size = 14

    $heading3 = $Document.Styles.Item("标题 3")
    $heading3.Font.NameFarEast = "黑体"
    $heading3.Font.Size = 12
}

$source = [System.IO.Path]::GetFullPath($SourcePath)
$output = [System.IO.Path]::GetFullPath($OutputPath)
$desktopOutput = [System.IO.Path]::GetFullPath($DesktopOutputPath)
$sourceDir = Split-Path -Parent $source

if (-not (Test-Path $source)) {
    throw "Source file not found: $source"
}

$sourceLines = Get-Content -Path $source -Encoding UTF8
$sections = Get-Sections -Lines $sourceLines

if ($sections.Count -lt 3) {
    throw "The source document must contain at least 摘要、ABSTRACT and the main body."
}

$prefaceSections = @($sections[0], $sections[1])
$bodySections = @()
if ($sections.Count -gt 2) {
    $bodySections = $sections[2..($sections.Count - 1)]
}

$word = $null
$document = $null

try {
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $output) -Force
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $desktopOutput) -Force

    if (Test-Path $output) {
        Remove-Item -Path $output -Force
    }

    if (Test-Path $desktopOutput) {
        Remove-Item -Path $desktopOutput -Force
    }

    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0

    $document = $word.Documents.Add()
    Set-DocumentStyles -Word $word -Document $document

    $selection = $word.Selection

    Add-Paragraph -Selection $selection -Document $document -StyleName "标题" -Text "智能考试云平台需求分析阶段文档" -Alignment 1 -SpaceAfter 12
    Add-Paragraph -Selection $selection -Document $document -StyleName "副标题" -Text "项目名称：smart-exam-cloud" -Alignment 1 -SpaceAfter 6
    Add-Paragraph -Selection $selection -Document $document -StyleName "正文" -Text ("生成时间：" + (Get-Date -Format "yyyy年MM月dd日")) -Alignment 1
    $selection.InsertBreak(7)

    foreach ($section in $prefaceSections) {
        Add-MarkdownLines -Lines $section.Lines -Selection $selection -Document $document -BaseDirectory $sourceDir
    }

    $selection.InsertBreak(7)
    Add-Paragraph -Selection $selection -Document $document -StyleName "TOC 标题" -Text "目录" -Alignment 1 -SpaceAfter 6
    $tocStart = $selection.Range.Start
    $selection.InsertBreak(7)

    foreach ($section in $bodySections) {
        Add-MarkdownLines -Lines $section.Lines -Selection $selection -Document $document -BaseDirectory $sourceDir
    }

    $tocRange = $document.Range($tocStart, $tocStart)
    $null = $document.TablesOfContents.Add($tocRange, $true, 1, 3)

    foreach ($toc in $document.TablesOfContents) {
        $toc.Update()
    }

    $document.Fields.Update() | Out-Null
    $document.SaveAs2($output, 16)

    if ($output -ne $desktopOutput) {
        Copy-Item -Path $output -Destination $desktopOutput -Force
    }
}
finally {
    if ($null -ne $document) {
        $document.Close([ref]0)
    }

    if ($null -ne $word) {
        $word.Quit()
    }

    if ($null -ne $document) {
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($document)
    }

    if ($null -ne $word) {
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($word)
    }

    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

Write-Output "Generated: $output"
if ($output -ne $desktopOutput) {
    Write-Output "Copied: $desktopOutput"
}



