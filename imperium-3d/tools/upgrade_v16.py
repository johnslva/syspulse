from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
core_p = CORE / "ImperiumGameV6.java"
art_p = CORE / "ImperiumGameV7.java"

s = core_p.read_text(encoding="utf-8")


def rep_core(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v16 core missing target: " + old[:140])
    s = s.replace(old, new, n)


# Version + denser army presentation. The higher cap is still bounded enough for mobile,
# while the tighter spacing makes formations read as armies instead of scattered agents.
rep_core('private static final String VERSION = "v0.15.0-alpha";',
         'private static final String VERSION = "v0.16.0-alpha";')
s = s.replace('private int populationCap = 40;', 'private int populationCap = 72;')
s = s.replace('populationCap = 40;', 'populationCap = 72;')
rep_core('''        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(group.size()))); float spacing = 1.48f;''',
         '''        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(group.size() * 1.35f))); float spacing = 1.12f;''')

core_p.write_text(s, encoding="utf-8")

v = art_p.read_text(encoding="utf-8")

# Composite architectural models for a denser, more premium medieval city silhouette.
if 'import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;' not in v:
    v = v.replace('import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;',
                  'import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;\nimport com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;')
if 'import com.badlogic.gdx.math.Matrix4;' not in v:
    v = v.replace('import com.badlogic.gdx.math.Vector3;',
                  'import com.badlogic.gdx.math.Vector3;\nimport com.badlogic.gdx.math.Matrix4;')

# Slightly warmer/high-key daylight inspired by the supplied large-battle references.
v = v.replace('environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.275f, 0.295f, 0.27f, 1f));',
              'environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.305f, 0.315f, 0.285f, 1f));')

anchor = '''    private Model own(Model m) {\n        models.add(m);\n        return m;\n    }\n'''
helper = '''    private Model cityGableRoof(float width, float depth, float rise, Material material) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("city-roof", GL20.GL_TRIANGLES, ATTR, material);
        Matrix4 tr = new Matrix4();
        float half = width * 0.5f;
        float slope = (float)Math.sqrt(half * half + rise * rise);
        float angle = (float)Math.toDegrees(Math.atan2(rise, half));
        part.setVertexTransform(tr.setToTranslation(-width * 0.25f, 0f, 0f).rotate(Vector3.Z, -angle));
        part.box(slope, 0.22f, depth);
        part.setVertexTransform(tr.setToTranslation(width * 0.25f, 0f, 0f).rotate(Vector3.Z, angle));
        part.box(slope, 0.22f, depth);
        part.setVertexTransformationEnabled(false);
        return own(builder.end());
    }

'''
if helper not in v:
    if anchor not in v:
        raise RuntimeError('v16 art missing own(Model) anchor')
    v = v.replace(anchor, anchor + '\n' + helper, 1)

# Add a real urban composition pass to both factions.
old_calls = '''        buildMixedTrees(mb);\n        buildMicroVegetation(mb);\n        buildWaterShimmers(mb);'''
new_calls = '''        buildPavedDistrict(mb, -25f, -18f, true);
        buildPavedDistrict(mb, 25f, 18f, false);
        buildUrbanDistrict(mb, -25f, -18f, true);
        buildUrbanDistrict(mb, 25f, 18f, false);
        buildKeepUpgrade(mb, -25f, -18f, true);
        buildKeepUpgrade(mb, 25f, 18f, false);
        buildMixedTrees(mb);
        buildMicroVegetation(mb);
        buildWaterShimmers(mb);'''
if old_calls not in v:
    raise RuntimeError('v16 art missing scenery call block')
v = v.replace(old_calls, new_calls, 1)

anchor2 = '''    private void buildMixedTrees(ModelBuilder mb) {'''
methods = '''    private void buildPavedDistrict(ModelBuilder mb, float cx, float cz, boolean blue) {
        Material roadMat = mat(0.46f, 0.455f, 0.42f);
        Material curbMat = mat(0.61f, 0.60f, 0.55f);
        Model avenue = own(mb.createBox(22f, 0.045f, 3.6f, roadMat, ATTR));
        Model cross = own(mb.createBox(3.6f, 0.047f, 20f, roadMat, ATTR));
        Model curb = own(mb.createBox(22f, 0.09f, 0.18f, curbMat, ATTR));
        float side = blue ? 1f : -1f;
        float ux = cx + side * 13.2f;
        add(avenue, ux, 0.055f, cz - 5.1f, 0f, 1f, 1f, 1f);
        add(avenue, ux, 0.056f, cz + 5.1f, 0f, 1f, 1f, 1f);
        add(cross, ux - side * 4.6f, 0.057f, cz, 0f, 1f, 1f, 1f);
        add(cross, ux + side * 4.6f, 0.057f, cz, 0f, 1f, 1f, 1f);
        add(curb, ux, 0.105f, cz - 7.0f, 0f, 1f, 1f, 1f);
        add(curb, ux, 0.105f, cz + 7.0f, 0f, 1f, 1f, 1f);
    }

    private void buildUrbanDistrict(ModelBuilder mb, float cx, float cz, boolean blue) {
        Material plaster = mat(0.74f, 0.68f, 0.57f);
        Material plaster2 = mat(0.64f, 0.58f, 0.49f);
        Material timber = mat(0.23f, 0.115f, 0.045f);
        Material roofMat = mat(0.72f, 0.60f, 0.34f);
        Material stone = mat(0.52f, 0.51f, 0.48f);
        Material dark = mat(0.055f, 0.065f, 0.07f);
        Material team = blue ? mat(0.055f, 0.27f, 0.78f) : mat(0.72f, 0.09f, 0.055f);

        Model lower = own(mb.createBox(3.65f, 2.35f, 3.15f, plaster, ATTR));
        Model upper = own(mb.createBox(3.25f, 1.05f, 3.05f, plaster2, ATTR));
        Model roof = cityGableRoof(4.25f, 3.65f, 1.36f, roofMat);
        Model beamV = own(mb.createBox(0.16f, 2.55f, 0.13f, timber, ATTR));
        Model beamH = own(mb.createBox(3.05f, 0.16f, 0.13f, timber, ATTR));
        Model window = own(mb.createBox(0.42f, 0.58f, 0.09f, dark, ATTR));
        Model door = own(mb.createBox(0.72f, 1.28f, 0.11f, timber, ATTR));
        Model chimney = own(mb.createBox(0.44f, 1.24f, 0.44f, stone, ATTR));
        Model banner = own(mb.createBox(0.38f, 1.20f, 0.07f, team, ATTR));
        Model target = own(mb.createCylinder(0.78f, 0.10f, 0.78f, 16, mat(0.80f, 0.77f, 0.67f), ATTR));
        Model targetDot = own(mb.createCylinder(0.28f, 0.115f, 0.28f, 16, mat(0.60f, 0.08f, 0.055f), ATTR));

        float side = blue ? 1f : -1f;
        float baseX = cx + side * 12.4f;
        int index = 0;
        for (int row = -2; row <= 2; row++) {
            for (int col = 0; col < 3; col++) {
                if ((row == 0 && col == 0) || (row == -2 && col == 2)) continue;
                float x = baseX + side * (col * 4.8f);
                float z = cz + row * 4.05f;
                float yaw = blue ? 0f : 180f;
                float scale = 0.88f + (index % 4) * 0.035f;
                add(lower, x, 1.18f * scale, z, yaw, scale, scale, scale);
                add(upper, x, 2.78f * scale, z, yaw, scale, scale, scale);
                add(roof, x, 4.15f * scale, z, yaw, scale, scale, scale);
                add(beamV, x - 1.38f * side * scale, 1.45f * scale, z - 1.59f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(beamV, x + 1.38f * side * scale, 1.45f * scale, z - 1.59f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(beamH, x, 2.15f * scale, z - 1.60f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(window, x - 0.72f * scale, 2.90f * scale, z - 1.62f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(window, x + 0.72f * scale, 2.90f * scale, z - 1.62f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                add(door, x, 0.78f * scale, z - 1.64f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                if ((index & 1) == 0) add(chimney, x + side * 0.90f * scale, 4.35f * scale, z + 0.55f, yaw, scale, scale, scale);
                add(banner, x + side * 1.72f * scale, 2.25f * scale, z - 1.64f * (blue ? 1f : -1f), yaw, scale, scale, scale);
                if ((index % 3) == 0) {
                    float tx = x + side * 1.95f;
                    float tz = z + 1.70f;
                    add(target, tx, 0.72f, tz, 90f, 1f, 1f, 1f);
                    add(targetDot, tx, 0.74f, tz, 90f, 1f, 1f, 1f);
                }
                index++;
            }
        }
    }

    private void buildKeepUpgrade(ModelBuilder mb, float x, float z, boolean blue) {
        Material stone = mat(0.57f, 0.565f, 0.53f);
        Material stoneDark = mat(0.40f, 0.405f, 0.39f);
        Material dark = mat(0.045f, 0.052f, 0.055f);
        Material team = blue ? mat(0.045f, 0.25f, 0.76f) : mat(0.68f, 0.075f, 0.05f);
        Model upper = own(mb.createBox(5.35f, 2.45f, 4.85f, stone, ATTR));
        Model buttress = own(mb.createBox(0.70f, 3.75f, 0.90f, stoneDark, ATTR));
        Model merlon = own(mb.createBox(0.62f, 0.72f, 0.68f, stone, ATTR));
        Model slit = own(mb.createBox(0.20f, 0.72f, 0.09f, dark, ATTR));
        Model banner = own(mb.createBox(0.56f, 1.65f, 0.075f, team, ATTR));
        Model gateLintel = own(mb.createBox(3.0f, 0.58f, 0.55f, stoneDark, ATTR));

        add(upper, x, 5.25f, z, 0f, 1f, 1f, 1f);
        float[][] bs = {{-3.55f,-2.75f},{3.55f,-2.75f},{-3.55f,2.75f},{3.55f,2.75f}};
        for (float[] b : bs) add(buttress, x + b[0], 2.0f, z + b[1], 0f, 1f, 1f, 1f);
        for (int i = -3; i <= 3; i++) {
            add(merlon, x + i * 0.78f, 6.78f, z - 2.18f, 0f, 1f, 1f, 1f);
            add(merlon, x + i * 0.78f, 6.78f, z + 2.18f, 0f, 1f, 1f, 1f);
        }
        add(slit, x - 1.45f, 5.20f, z - 2.46f, 0f, 1f, 1f, 1f);
        add(slit, x + 1.45f, 5.20f, z - 2.46f, 0f, 1f, 1f, 1f);
        add(banner, x, 5.30f, z - 2.49f, 0f, 1f, 1f, 1f);
        add(gateLintel, x, 2.95f, z - 3.72f, 0f, 1f, 1f, 1f);
    }

'''
if anchor2 not in v:
    raise RuntimeError('v16 art missing mixed trees anchor')
v = v.replace(anchor2, methods + anchor2, 1)

art_p.write_text(v, encoding="utf-8")
print("Applied v0.16 dense city, paved districts, keep upgrade and tighter army formations")
