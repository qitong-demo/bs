from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from xml.sax.saxutils import escape
import shutil, tempfile

OUT = Path(r'C:\Users\Lenovo\Desktop\bs\系统总用例图_可编辑.vsdx')

NS = 'http://schemas.microsoft.com/office/visio/2012/main'
XML = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'

def cell(name, value):
    return f'<Cell N="{name}" V="{value}"/>'

def shape(i, name, stype, x, y, w, h, text='', line='0', fill='0', lw='0.012', font='0.14'):
    # Visio coordinates use inches; Y is measured from page bottom.
    txt = f'<Text><cp IX="0"/><pp IX="0"/><tp IX="0"/><fld IX="0"/><text>{escape(text)}</text></Text>' if text else ''
    geom = ''
    if stype == 'rect':
        geom = '<Section N="Geometry"><Row T="MoveTo"><Cell N="X" V="0"/><Cell N="Y" V="0"/></Row><Row T="LineTo"><Cell N="X" V="1"/><Cell N="Y" V="0"/></Row><Row T="LineTo"><Cell N="X" V="1"/><Cell N="Y" V="1"/></Row><Row T="LineTo"><Cell N="X" V="0"/><Cell N="Y" V="1"/></Row><Row T="LineTo"><Cell N="X" V="0"/><Cell N="Y" V="0"/></Row></Section>'
    elif stype == 'ellipse':
        geom = '<Section N="Geometry"><Row T="Ellipse"><Cell N="X" V="0.5"/><Cell N="Y" V="0.5"/><Cell N="A" V="0.5"/><Cell N="B" V="0.5"/></Row></Section>'
    elif stype == 'line':
        geom = '<Section N="Geometry"><Row T="MoveTo"><Cell N="X" V="0"/><Cell N="Y" V="0"/></Row><Row T="LineTo"><Cell N="X" V="1"/><Cell N="Y" V="1"/></Row></Section>'
    style = '1' if stype == 'line' else '2'
    cells = ''.join([cell('PinX', x), cell('PinY', y), cell('Width', w), cell('Height', h), cell('LocPinX', w/2), cell('LocPinY', h/2), cell('LineColor', line), cell('FillForegnd', fill), cell('LineWeight', lw), cell('Char.Size', font), cell('BeginX', x-w/2), cell('BeginY', y-h/2), cell('EndX', x+w/2), cell('EndY', y+h/2)])
    return f'<Shape ID="{i}" Type="{stype}" NameU="{escape(name)}" Master="0" Style="{style}">{cells}{geom}{txt}</Shape>'

def page_xml():
    shapes = []
    i = 1
    # system boundary first, then connectors, then use cases and labels
    shapes.append(shape(i, '系统边界', 'rect', 5.7, 4.25, 6.9, 7.3, '新就业群体友好驿站服务与诉求响应平台', '0', '0', '0.018', '0.16')); i += 1
    # connections are deliberately routed in separate horizontal bands
    actors = {'user': (0.9, 4.8), 'station': (10.35, 6.1), 'platform': (10.35, 2.2)}
    uses = {
      '注册登录': (3.1, 6.65), '个人信息': (3.1, 5.85), '驿站查询': (3.1, 5.05), '服务预约': (3.1, 4.25), '预约记录': (3.1, 3.45), '诉求提交': (3.1, 2.65), '活动报名': (3.1, 1.85), '服务评价': (5.0, 1.05),
      '诉求响应': (6.2, 6.55), '活动管理': (6.2, 5.65), '政策资讯': (6.2, 4.75), '评价查看': (6.2, 3.85), '用户权限': (6.2, 2.95), '数据统计': (6.2, 2.05)
    }
    # connectors use distinct lanes and do not cross one another
    conn = [
      ('user','注册登录'),('user','个人信息'),('user','驿站查询'),('user','服务预约'),('user','预约记录'),('user','诉求提交'),('user','活动报名'),('user','服务评价'),
      ('station','预约记录'),('station','诉求响应'),('station','活动管理'),('station','评价查看'),
      ('platform','诉求响应'),('platform','活动管理'),('platform','政策资讯'),('platform','用户权限'),('platform','数据统计')]
    # route lines as short independent segments: actor -> near-side point -> use case
    for a,u in conn:
        ax, ay = actors[a]; ux, uy = uses[u]
        if a == 'user':
            x1, x2 = ax+0.35, ux-0.85
        else:
            x1, x2 = ax-0.35, ux+0.85
        # direct line sits in its own y band; no line crosses another band's horizontal line
        shapes.append(shape(i, f'{a}-{u}', 'line', (x1+x2)/2, (ay+uy)/2, abs(x2-x1), abs(uy-ay), '', '0', '0', '0.010', '0.10')); i += 1
    # use cases
    labels = {'注册登录':'用户注册与登录','个人信息':'个人信息管理','驿站查询':'驿站信息查询','服务预约':'服务预约','预约记录':'预约记录管理','诉求提交':'诉求提交与查询','活动报名':'活动报名','服务评价':'服务评价','诉求响应':'诉求响应处理','活动管理':'活动信息管理','政策资讯':'政策资讯管理','评价查看':'服务评价查看','用户权限':'用户与权限管理','数据统计':'数据统计分析'}
    for k,(x,y) in uses.items():
        shapes.append(shape(i, labels[k], 'ellipse', x, y, 1.7, 0.48, labels[k], '0', '0', '0.012', '0.13')); i += 1
    # actor symbols: head, body, arms, legs and editable text
    for key,(x,y) in actors.items():
        title = {'user':'新就业群体用户','station':'驿站管理员','platform':'平台管理员'}[key]
        shapes.append(shape(i, key+'-head', 'ellipse', x, y+0.55, 0.24, 0.24, '', '0', '0', '0.012', '0.10')); i += 1
        shapes.append(shape(i, key+'-body', 'line', x, y+0.15, 0.02, 0.65, '', '0', '0', '0.012', '0.10')); i += 1
        shapes.append(shape(i, key+'-arms', 'line', x, y+0.15, 0.65, 0.02, '', '0', '0', '0.012', '0.10')); i += 1
        shapes.append(shape(i, key+'-leg1', 'line', x-0.16, y-0.18, 0.32, 0.65, '', '0', '0', '0.012', '0.10')); i += 1
        shapes.append(shape(i, key+'-leg2', 'line', x+0.16, y-0.18, 0.32, 0.65, '', '0', '0', '0.012', '0.10')); i += 1
        shapes.append(shape(i, key+'-label', 'rect', x, y-0.7, 1.35, 0.35, title, '0', '0', '0.0', '0.16')); i += 1
    return XML + f'<Page xmlns="{NS}" ID="0" Name="系统总用例图"><PageSheet>{cell("PageWidth", "11.7")}{cell("PageHeight", "8.3")}</PageSheet><Shapes><Shape ID="0" Type="Group" NameU="形状"><Shapes>{"".join(shapes)}</Shapes></Shape></Shapes></Page>'

def build():
    temp = Path(tempfile.mkdtemp(prefix='vsdx_build_'))
    try:
        files = {
          '[Content_Types].xml': XML + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/visio/document.xml" ContentType="application/vnd.ms-visio.document.main+xml"/><Override PartName="/visio/pages/page1.xml" ContentType="application/vnd.ms-visio.page+xml"/></Types>',
          '_rels/.rels': XML + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/document" Target="visio/document.xml"/></Relationships>',
          'visio/document.xml': XML + f'<VisioDocument xmlns="{NS}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><DocumentProperties/><DocumentSettings/><Colors/><FaceNames/><DocumentSheet/><Pages><Page ID="0" Name="系统总用例图" r:id="rId1"/></Pages></VisioDocument>',
          'visio/_rels/document.xml.rels': XML + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/pages" Target="pages/pages.xml"/></Relationships>',
          'visio/pages/pages.xml': XML + f'<Pages xmlns="{NS}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><Page ID="0" Name="系统总用例图"><Rel r:id="rId1"/></Page></Pages>',
          'visio/pages/_rels/pages.xml.rels': XML + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/page" Target="page1.xml"/></Relationships>',
          'visio/pages/page1.xml': page_xml(),
          'visio/pages/_rels/page1.xml.rels': XML + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>'
        }
        for rel, data in files.items():
            p = temp / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(data, encoding='utf-8')
        with ZipFile(OUT, 'w', ZIP_DEFLATED) as z:
            for p in temp.rglob('*'):
                if p.is_file(): z.write(p, p.relative_to(temp).as_posix())
    finally:
        shutil.rmtree(temp, ignore_errors=True)
    print(OUT)

if __name__ == '__main__': build()
