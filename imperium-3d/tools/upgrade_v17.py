from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
core_p = CORE / "ImperiumGameV6.java"
art_p = CORE / "ImperiumGameV7.java"

s = core_p.read_text(encoding="utf-8")


def rep_core(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v17 core missing target: " + old[:140])
    s = s.replace(old, new, n)

rep_core('private static final String VERSION = "v0.16.0-alpha";',
         'private static final String VERSION = "v0.17.0-alpha";')
# Pull the camera back to a more RTS-like framing so formations and architecture read together.
s = s.replace('cameraDistance = 30.5f; cameraYaw = 43f; cameraPitch = 48f;',
              'cameraDistance = 35.5f; cameraYaw = 43f; cameraPitch = 52f;')
core_p.write_text(s, encoding="utf-8")

v = art_p.read_text(encoding="utf-8")

# Less neon terrain, closer to the muted natural palette in the supplied references.
v = v.replace('float r = 0.22f + fine * 0.040f + broad * 0.018f;',
              'float r = 0.19f + fine * 0.035f + broad * 0.016f;')
v = v.replace('float g = 0.37f + fine * 0.060f + broad * 0.025f;',
              'float g = 0.31f + fine * 0.050f + broad * 0.022f;')
v = v.replace('float b = 0.15f + fine * 0.030f + broad * 0.012f;',
              'float b = 0.12f + fine * 0.025f + broad * 0.010f;')

# Refine urban material palette and scale after v0.16 visual review.
v = v.replace('Material plaster = mat(0.74f, 0.68f, 0.57f);',
              'Material plaster = mat(0.68f, 0.62f, 0.52f);')
v = v.replace('Material plaster2 = mat(0.64f, 0.58f, 0.49f);',
              'Material plaster2 = mat(0.59f, 0.54f, 0.46f);')
v = v.replace('Material roofMat = mat(0.72f, 0.60f, 0.34f);',
              'Material roofMat = mat(0.48f, 0.34f, 0.17f);')
v = v.replace('float baseX = cx + side * 12.4f;',
              'float baseX = cx + side * 14.2f;')
v = v.replace('for (int row = -2; row <= 2; row++) {\n            for (int col = 0; col < 3; col++) {',
              'for (int row = -2; row <= 2; row++) {\n            for (int col = 0; col < 2; col++) {')
v = v.replace('float x = baseX + side * (col * 4.8f);',
              'float x = baseX + side * (col * 5.3f);')
v = v.replace('float z = cz + row * 4.05f;',
              'float z = cz + row * 4.55f;')
v = v.replace('float scale = 0.88f + (index % 4) * 0.035f;',
              'float scale = 0.72f + (index % 4) * 0.035f;')

# Narrower/lower roof silhouette so buildings no longer obscure the battlefield.
v = v.replace('Model roof = cityGableRoof(4.25f, 3.65f, 1.36f, roofMat);',
              'Model roof = cityGableRoof(3.95f, 3.45f, 1.12f, roofMat);')
v = v.replace('add(roof, x, 4.15f * scale, z, yaw, scale, scale, scale);',
              'add(roof, x, 3.88f * scale, z, yaw, scale, scale, scale);')

# Roads/plazas should frame districts rather than disappear underneath large roofs.
v = v.replace('Model avenue = own(mb.createBox(22f, 0.045f, 3.6f, roadMat, ATTR));',
              'Model avenue = own(mb.createBox(24f, 0.045f, 4.2f, roadMat, ATTR));')
v = v.replace('Model cross = own(mb.createBox(3.6f, 0.047f, 20f, roadMat, ATTR));',
              'Model cross = own(mb.createBox(4.2f, 0.047f, 22f, roadMat, ATTR));')
v = v.replace('float ux = cx + side * 13.2f;',
              'float ux = cx + side * 14.8f;')

# Keep upgrade should add presence without filling the whole camera.
v = v.replace('Model upper = own(mb.createBox(5.35f, 2.45f, 4.85f, stone, ATTR));',
              'Model upper = own(mb.createBox(4.95f, 2.15f, 4.45f, stone, ATTR));')
v = v.replace('add(upper, x, 5.25f, z, 0f, 1f, 1f, 1f);',
              'add(upper, x, 5.05f, z, 0f, 1f, 1f, 1f);')

art_p.write_text(v, encoding="utf-8")
print("Applied v0.17 composition cleanup: RTS camera, smaller city, muted palette and clearer streets")
