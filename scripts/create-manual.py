"""Rebuild the unsigned signing copy from docs/MANUAL.md. Requires reportlab.
Run from the repository root: python scripts/create-manual.py
Never run this over a personally signed PDF; signed copies belong outside this path.
"""
from pathlib import Path
from xml.sax.saxutils import escape
import re
import reportlab
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, Preformatted, KeepTogether

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/task12/group-5-signing-copy.pdf'
font_dir = Path(reportlab.__file__).resolve().parent / 'fonts'
pdfmetrics.registerFont(TTFont('Vera', str(font_dir / 'Vera.ttf')))
pdfmetrics.registerFont(TTFont('Vera-Bold', str(font_dir / 'VeraBd.ttf')))
pdfmetrics.registerFontFamily('Vera', normal='Vera', bold='Vera-Bold', italic='Vera', boldItalic='Vera-Bold')
NAVY = colors.HexColor('#16304b')
BLUE = colors.HexColor('#296687')
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name='CopyBody', fontName='Vera', fontSize=9.5, leading=13, spaceAfter=7, textColor=NAVY))
styles.add(ParagraphStyle(name='CopyTitle', fontName='Vera-Bold', fontSize=23, leading=28, textColor=NAVY, spaceAfter=15))
styles.add(ParagraphStyle(name='CopyHeading', fontName='Vera-Bold', fontSize=14, leading=18, textColor=BLUE, spaceBefore=14, spaceAfter=8, keepWithNext=True))
styles.add(ParagraphStyle(name='CopyCode', fontName='Courier', fontSize=8, leading=11, leftIndent=8, spaceBefore=5, spaceAfter=9))
styles.add(ParagraphStyle(name='CopySmall', fontName='Vera', fontSize=8.5, leading=12, textColor=NAVY, spaceAfter=6))


def inline(text):
    text = escape(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    text = re.sub(r'`(.+?)`', r'<font name="Courier">\1</font>', text)
    return text


def para(text, style='CopyBody'):
    return Paragraph(inline(text), styles[style])


def markdown(lines):
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith('```'):
            code = []
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                # Long commands wrap on clear whitespace; preserve copied command in Markdown source.
                import textwrap
                code.extend(textwrap.wrap(lines[i], 94, subsequent_indent='  ', break_long_words=False, break_on_hyphens=False) or [''])
                i += 1
            result.append(Preformatted('\n'.join(code), styles['CopyCode']))
            i += 1
            continue
        if line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                fields = [x.strip() for x in lines[i].strip('|').split('|')]
                if not all(re.fullmatch(r'[:\-]+', x) for x in fields):
                    rows.append([para(x, 'CopySmall') for x in fields])
                i += 1
            widths = [125, 374] if len(rows[0]) == 2 else None
            table = Table(rows, colWidths=widths, repeatRows=1, hAlign='LEFT')
            table.setStyle(TableStyle([('BACKGROUND', (0,0), (-1,0), colors.HexColor('#e5eff5')), ('VALIGN',(0,0),(-1,-1),'TOP'), ('LINEBELOW',(0,0),(-1,0),0.7,BLUE), ('LINEBELOW',(0,1),(-1,-1),0.25,colors.HexColor('#d5dfe5')), ('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),4)]))
            result += [table, Spacer(1,8)]
            continue
        if line.startswith('# '):
            result.append(para(line[2:], 'CopyTitle'))
            i += 1
            continue
        if line.startswith('## '):
            result.append(para(line[3:], 'CopyHeading'))
            i += 1
            continue
        chunk = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and not lines[i].startswith(('#', '|', '```', '- ')):
            chunk.append(lines[i]); i += 1
        text = ' '.join(chunk)
        if text.startswith('- '):
            text = '- ' + text[2:]
        result.append(para(text))
    return result


def signing_blocks():
    result = []
    for n in range(1, 5):
        result.append(KeepTogether([
            para(f'Member {n}', 'CopyHeading'),
            para('Full name: ___________________________________________________', 'CopySmall'),
            para('Student number: ______________________    Date: __________________', 'CopySmall'),
            Spacer(1, 18),
            para('Personal signature: ______________________________________________', 'CopySmall'),
            Spacer(1, 9),
        ]))
    return result


def footer(canvas, doc):
    canvas.saveState()
    w, h = A4
    canvas.setStrokeColor(colors.HexColor('#d5dfe5'))
    canvas.line(48, 44, w-48, 44)
    canvas.setFont('Vera', 8)
    canvas.setFillColor(NAVY)
    canvas.drawString(48, 30, 'COS341 | Group 5 | 1st Class | SPL Compiler')
    canvas.drawRightString(w-48, 30, str(doc.page))
    canvas.restoreState()


# Developer-only build instructions stay in Markdown, outside the assessment manual.
manual = (ROOT / 'docs/MANUAL.md').read_text().split('## Development only')[0]
story = markdown(manual.splitlines())
story += [PageBreak(), para('Declaration 1', 'CopyTitle'), para('Submission category', 'CopyHeading'),
    para('We, the undersigned members of Group 5, agree that our COS341 Part 2 submission is made in the **1st Class** category, for a maximum of **6/6 points**. Our submission includes Parts **2a, 2b, 3a and 3b**: naming and scope analysis, type analysis, algorithm code generation, and function/call translation by inlining, including generation of human-readable executable C++ target source.'),
    para('Each member below personally confirms agreement with this category by signing this declaration.'),
    *signing_blocks(),
    PageBreak(), para('Declaration 2', 'CopyTitle'), para('Awareness and agreement with the submission', 'CopyHeading'),
    para('We, the undersigned members of Group 5, confirm that **each of us is fully aware of the whole contents of group-5.zip and agrees with the submission of the ZIP file in this form**. The ZIP contains our runnable compiler, group-5.jar, and this user manual, group-5.pdf, including both personally signed declarations.'),
    para('Each member personally confirms that they have reviewed the submission contents and agree that our group speaker may submit this package in the **1st Class** submission slot.'),
    *signing_blocks()]
OUT.parent.mkdir(parents=True, exist_ok=True)
doc = SimpleDocTemplate(str(OUT), pagesize=A4, rightMargin=48, leftMargin=48, topMargin=48, bottomMargin=60, title='Group 5 - COS341 SPL Compiler User Manual', author='COS341 Group 5')
doc.build(story, onFirstPage=footer, onLaterPages=footer)
print(OUT)
