from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
core_p = CORE / "ImperiumGameV6.java"
art_p = CORE / "ImperiumGameV7.java"

s = core_p.read_text(encoding="utf-8")
if 'private static final String VERSION = "v0.18.0-alpha";' not in s:
    raise RuntimeError('v19 core version anchor missing')
s = s.replace('private static final String VERSION = "v0.18.0-alpha";',
              'private static final String VERSION = "v0.19.0-alpha";', 1)
core_p.write_text(s, encoding='utf-8')

v = art_p.read_text(encoding='utf-8')

# Lazy procedural material textures: higher material richness at tiny APK cost.
field_anchor = '    private float waterTime;\n'
fields = '''    private Texture cityFacadeTexture;
    private Texture cityRoofTexture;
    private Texture cityPaverTexture;
'''
if fields not in v:
    if field_anchor not in v:
        raise RuntimeError('v19 art field anchor missing')
    v = v.replace(field_anchor, field_anchor + fields, 1)

helper_anchor = '''    private Material alphaMat(float r, float g, float b, float a) {'''
helpers = '''    private Texture proceduralCityTexture(int style) {
        final int size = 256;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int h = x * 73856093 ^ y * 19349663 ^ style * 83492791;
                h ^= h << 13; h ^= h >>> 17; h ^= h << 5;
                float n = ((h & 1023) / 1023f - 0.5f);
                float r, g, b;
                if (style == 0) {
                    // Warm plaster with timber-frame lines and subtle age variation.
                    r = 0.66f + n * 0.055f; g = 0.59f + n * 0.050f; b = 0.47f + n * 0.045f;
                    boolean timber = (x % 62 < 5) || (y % 72 < 5) || ((x + y) % 126 < 4);
                    if (timber) { r = 0.22f + n * 0.025f; g = 0.115f + n * 0.018f; b = 0.048f + n * 0.012f; }
                } else if (style == 1) {
                    // Overlapping medieval roof tiles, deliberately low-contrast at RTS distance.
                    r = 0.43f + n * 0.055f; g = 0.245f + n * 0.035f; b = 0.105f + n * 0.025f;
                    int row = y / 18;
                    int xo = (row & 1) * 10;
                    boolean mortar = (y % 18 < 2) || ((x + xo) % 20 < 2);
                    if (mortar) { r *= 0.68f; g *= 0.66f; b *= 0.64f; }
                } else {
                    // Small irregular stone pavers.
                    r = 0.47f + n * 0.060f; g = 0.455f + n * 0.055f; b = 0.405f + n * 0.050f;
                    int row = y / 16;
                    int xo = (row & 1) * 9;
                    boolean joint = (y % 16 < 2) || ((x + xo) % 18 < 2);
                    if (joint) { r *= 0.61f; g *= 0.61f; b *= 0.60f; }
                }
                p.setColor(clamp(r), clamp(g), clamp(b), 1f);
                p.drawPixel(x, y);
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        p.dispose();
        textures.add(t);
        return t;
    }

    private Texture facadeTex() {
        if (cityFacadeTexture == null) cityFacadeTexture = proceduralCityTexture(0);
        return cityFacadeTexture;
    }

    private Texture roofTex() {
        if (cityRoofTexture == null) cityRoofTexture = proceduralCityTexture(1);
        return cityRoofTexture;
    }

    private Texture paverTex() {
        if (cityPaverTexture == null) cityPaverTexture = proceduralCityTexture(2);
        return cityPaverTexture;
    }

'''
if helpers not in v:
    if helper_anchor not in v:
        raise RuntimeError('v19 material helper anchor missing')
    v = v.replace(helper_anchor, helpers + helper_anchor, 1)

# Streets switch from flat diffuse colors to visible pavers.
v = v.replace('Material roadMat = mat(0.46f, 0.455f, 0.42f);',
              'Material roadMat = tex(paverTex(), 0.96f, 0.96f, 0.94f);')
v = v.replace('Material curbMat = mat(0.61f, 0.60f, 0.55f);',
              'Material curbMat = tex(paverTex(), 1.08f, 1.06f, 1.02f);')
v = v.replace('Model avenue = own(mb.createBox(24f, 0.045f, 4.2f, roadMat, ATTR));',
              'Model avenue = own(mb.createBox(24f, 0.045f, 4.2f, roadMat, ATTR_TEX));')
v = v.replace('Model cross = own(mb.createBox(4.2f, 0.047f, 22f, roadMat, ATTR));',
              'Model cross = own(mb.createBox(4.2f, 0.047f, 22f, roadMat, ATTR_TEX));')
v = v.replace('Model curb = own(mb.createBox(22f, 0.09f, 0.18f, curbMat, ATTR));',
              'Model curb = own(mb.createBox(22f, 0.09f, 0.18f, curbMat, ATTR_TEX));')

# Houses gain actual facade/roof material patterns.
v = v.replace('Material plaster = mat(0.68f, 0.62f, 0.52f);',
              'Material plaster = tex(facadeTex(), 1.00f, 1.00f, 0.98f);')
v = v.replace('Material plaster2 = mat(0.59f, 0.54f, 0.46f);',
              'Material plaster2 = tex(facadeTex(), 0.90f, 0.91f, 0.89f);')
v = v.replace('Material roofMat = mat(0.48f, 0.34f, 0.17f);',
              'Material roofMat = tex(roofTex(), 1.00f, 1.00f, 1.00f);')
v = v.replace('Model lower = own(mb.createBox(3.65f, 2.35f, 3.15f, plaster, ATTR));',
              'Model lower = own(mb.createBox(3.65f, 2.35f, 3.15f, plaster, ATTR_TEX));')
v = v.replace('Model upper = own(mb.createBox(3.25f, 1.05f, 3.05f, plaster2, ATTR));',
              'Model upper = own(mb.createBox(3.25f, 1.05f, 3.05f, plaster2, ATTR_TEX));')

# The custom gabled roof needs UVs for roof tiles.
v = v.replace('MeshPartBuilder part = builder.part("city-roof", GL20.GL_TRIANGLES, ATTR, material);',
              'MeshPartBuilder part = builder.part("city-roof", GL20.GL_TRIANGLES, ATTR_TEX, material);')

# Keep upper mass receives stone-paver texture as a cheap masonry treatment.
v = v.replace('Material stone = mat(0.57f, 0.565f, 0.53f);',
              'Material stone = tex(paverTex(), 0.88f, 0.88f, 0.86f);')
v = v.replace('Model upper = own(mb.createBox(4.95f, 2.15f, 4.45f, stone, ATTR));',
              'Model upper = own(mb.createBox(4.95f, 2.15f, 4.45f, stone, ATTR_TEX));')
v = v.replace('Model merlon = own(mb.createBox(0.62f, 0.72f, 0.68f, stone, ATTR));',
              'Model merlon = own(mb.createBox(0.62f, 0.72f, 0.68f, stone, ATTR_TEX));')

art_p.write_text(v, encoding='utf-8')
print('Applied v0.19 textured facades, roof tiles and stone-paved urban materials')
