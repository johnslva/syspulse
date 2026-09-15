from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
core_p = CORE / "ImperiumGameV6.java"
art_p = CORE / "ImperiumGameV7.java"

s = core_p.read_text(encoding="utf-8")


def rep_core(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v18 core missing target: " + old[:150])
    s = s.replace(old, new, n)

rep_core('private static final String VERSION = "v0.17.0-alpha";',
         'private static final String VERSION = "v0.18.0-alpha";')

# Compact HUD: expose substantially more world while retaining touch hit targets.
rep_core('''        float top = Math.max(58f, h * 0.092f), bottom = Math.max(82f, h * 0.142f);''',
         '''        float top = Math.max(52f, h * 0.078f), bottom = Math.max(66f, h * 0.104f);''')
rep_core('''        float bw = Math.min(176f, w * 0.162f), bh = bottom * 0.70f, gap = 9f;''',
         '''        float bw = Math.min(142f, w * 0.125f), bh = bottom * 0.72f, gap = 7f;''')
rep_core('''        float mapW = Math.min(210f, w * 0.185f), mapH = bottom * 0.76f, mapY = (bottom - mapH) * 0.5f;''',
         '''        float mapW = Math.min(165f, w * 0.135f), mapH = bottom * 0.78f, mapY = (bottom - mapH) * 0.5f;''')

core_p.write_text(s, encoding="utf-8")

v = art_p.read_text(encoding="utf-8")

# Muted, layered woodland palette instead of toy-like saturated green blobs.
v = v.replace('Model broadA = own(mb.createSphere(2.8f, 2.25f, 2.7f, 11, 8, mat(0.10f, 0.30f, 0.075f), ATTR));',
              'Model broadA = own(mb.createSphere(2.8f, 2.25f, 2.7f, 11, 8, mat(0.105f, 0.245f, 0.070f), ATTR));')
v = v.replace('Model broadB = own(mb.createSphere(2.2f, 1.85f, 2.1f, 10, 7, mat(0.16f, 0.38f, 0.10f), ATTR));',
              'Model broadB = own(mb.createSphere(2.2f, 1.85f, 2.1f, 10, 7, mat(0.155f, 0.315f, 0.095f), ATTR));')
v = v.replace('Model broadC = own(mb.createSphere(1.75f, 1.55f, 1.70f, 10, 7, mat(0.22f, 0.43f, 0.13f), ATTR));',
              'Model broadC = own(mb.createSphere(1.75f, 1.55f, 1.70f, 10, 7, mat(0.205f, 0.365f, 0.120f), ATTR));')

# Add architectural layers to the v0.16/v0.17 modular houses.
old_models = '''        Model chimney = own(mb.createBox(0.44f, 1.24f, 0.44f, stone, ATTR));
        Model banner = own(mb.createBox(0.38f, 1.20f, 0.07f, team, ATTR));'''
new_models = '''        Model chimney = own(mb.createBox(0.44f, 1.24f, 0.44f, stone, ATTR));
        Model plinth = own(mb.createBox(3.78f, 0.46f, 3.25f, stone, ATTR));
        Model ridge = own(mb.createBox(0.18f, 0.18f, 3.55f, timber, ATTR));
        Model shutter = own(mb.createBox(0.15f, 0.62f, 0.09f, timber, ATTR));
        Model banner = own(mb.createBox(0.38f, 1.20f, 0.07f, team, ATTR));'''
if old_models not in v:
    raise RuntimeError('v18 art missing urban model anchor')
v = v.replace(old_models, new_models, 1)

old_instances = '''                add(lower, x, 1.18f * scale, z, yaw, scale, scale, scale);
                add(upper, x, 2.78f * scale, z, yaw, scale, scale, scale);
                add(roof, x, 3.88f * scale, z, yaw, scale, scale, scale);'''
new_instances = '''                add(plinth, x, 0.23f * scale, z, yaw, scale, scale, scale);
                add(lower, x, 1.28f * scale, z, yaw, scale, scale, scale);
                add(upper, x, 2.84f * scale, z, yaw, scale, scale, scale);
                add(roof, x, 3.94f * scale, z, yaw, scale, scale, scale);
                add(ridge, x, 4.58f * scale, z, yaw, scale, scale, scale);'''
if old_instances not in v:
    raise RuntimeError('v18 art missing urban instance anchor')
v = v.replace(old_instances, new_instances, 1)

# Window shutters add strong readable facade rhythm at RTS zoom.
old_windows = '''                add(window, x - 0.72f * scale, 2.90f * scale, z - 1.62f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(window, x + 0.72f * scale, 2.90f * scale, z - 1.62f * (blue ? 1f : -1f), yaw, scale, scale, scale);'''
new_windows = '''                add(window, x - 0.72f * scale, 2.90f * scale, z - 1.62f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(window, x + 0.72f * scale, 2.90f * scale, z - 1.62f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(shutter, x - 1.02f * scale, 2.90f * scale, z - 1.635f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(shutter, x - 0.43f * scale, 2.90f * scale, z - 1.635f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(shutter, x + 0.43f * scale, 2.90f * scale, z - 1.635f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(shutter, x + 1.02f * scale, 2.90f * scale, z - 1.635f * (blue ? 1f : -1f), yaw, scale, scale, scale);'''
if old_windows not in v:
    raise RuntimeError('v18 art missing window anchor')
v = v.replace(old_windows, new_windows, 1)

# Reduce oversized broadleaf crowns a little to stop vegetation obscuring formations.
v = v.replace('float s = 0.72f + rng.nextFloat() * 0.55f;',
              'float s = 0.62f + rng.nextFloat() * 0.46f;')

art_p.write_text(v, encoding="utf-8")
print("Applied v0.18 compact HUD, natural woodland and richer modular facades")
