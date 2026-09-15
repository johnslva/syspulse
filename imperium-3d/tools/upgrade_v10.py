from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
p = CORE / "ImperiumGameV6.java"
s = p.read_text(encoding="utf-8")


def rep(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v10 missing target: " + old[:100])
    s = s.replace(old, new, n)


rep('private static final String VERSION = "v0.9.0-alpha";', 'private static final String VERSION = "v0.10.0-alpha";')
rep('import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;',
    'import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;\nimport com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;')

rep('''    private Model own(Model m) {
        ownedModels.add(m);
        return m;
    }

    private void buildSharedModels() {''', '''    private Model own(Model m) {
        ownedModels.add(m);
        return m;
    }

    private Model buildHumanoidBody(float r, float g, float b, boolean mounted) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("body", GL20.GL_TRIANGLES, ATTR, material(r, g, b));
        Matrix4 tr = new Matrix4();
        float legY = mounted ? -0.34f : -0.55f;
        float legH = mounted ? 0.48f : 0.72f;
        float torsoY = mounted ? 0.16f : 0.10f;
        part.setVertexTransform(tr.setToTranslation(0f, torsoY, 0f));
        part.box(0.72f, 0.86f, 0.38f);
        part.setVertexTransform(tr.setToTranslation(-0.20f, legY, 0f).rotate(Vector3.Z, mounted ? -18f : 2f));
        part.box(0.24f, legH, 0.27f);
        part.setVertexTransform(tr.setToTranslation(0.20f, legY, 0f).rotate(Vector3.Z, mounted ? 18f : -2f));
        part.box(0.24f, legH, 0.27f);
        part.setVertexTransform(tr.setToTranslation(-0.46f, 0.08f, 0f).rotate(Vector3.Z, -9f));
        part.box(0.20f, 0.72f, 0.22f);
        part.setVertexTransform(tr.setToTranslation(0.46f, 0.08f, 0f).rotate(Vector3.Z, 9f));
        part.box(0.20f, 0.72f, 0.22f);
        part.setVertexTransformationEnabled(false);
        return builder.end();
    }

    private Model buildHorseBody() {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("horse", GL20.GL_TRIANGLES, ATTR, material(0.29f, 0.16f, 0.07f));
        Matrix4 tr = new Matrix4();
        part.setVertexTransform(tr.idt());
        part.sphere(1.75f, 0.92f, 0.82f, 12, 8);
        float[] xs = {-0.55f, 0.55f};
        float[] zs = {-0.28f, 0.28f};
        for (float x : xs) for (float z : zs) {
            part.setVertexTransform(tr.setToTranslation(x, -0.63f, z));
            part.box(0.18f, 0.92f, 0.18f);
        }
        part.setVertexTransform(tr.setToTranslation(0f, 0.38f, 0.52f).rotate(Vector3.X, -24f));
        part.box(0.48f, 0.95f, 0.44f);
        part.setVertexTransform(tr.setToTranslation(0f, 0.10f, -0.62f).rotate(Vector3.X, 38f));
        part.cone(0.28f, 0.86f, 0.28f, 7);
        part.setVertexTransformationEnabled(false);
        return builder.end();
    }

    private Model buildHorseHead() {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("head", GL20.GL_TRIANGLES, ATTR, material(0.31f, 0.17f, 0.075f));
        Matrix4 tr = new Matrix4();
        part.setVertexTransform(tr.setToTranslation(0f, 0f, 0.08f));
        part.sphere(0.55f, 0.64f, 0.78f, 10, 7);
        part.setVertexTransform(tr.setToTranslation(-0.18f, 0.36f, 0.02f));
        part.cone(0.16f, 0.32f, 0.16f, 6);
        part.setVertexTransform(tr.setToTranslation(0.18f, 0.36f, 0.02f));
        part.cone(0.16f, 0.32f, 0.16f, 6);
        part.setVertexTransformationEnabled(false);
        return builder.end();
    }

    private Model buildGableRoof(float width, float depth, float rise, Material mat) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("roof", GL20.GL_TRIANGLES, ATTR, mat);
        Matrix4 tr = new Matrix4();
        float half = width * 0.5f;
        float slope = (float)Math.sqrt(half * half + rise * rise);
        float angle = (float)Math.toDegrees(Math.atan2(rise, half));
        part.setVertexTransform(tr.setToTranslation(-width * 0.25f, 0f, 0f).rotate(Vector3.Z, -angle));
        part.box(slope, 0.24f, depth);
        part.setVertexTransform(tr.setToTranslation(width * 0.25f, 0f, 0f).rotate(Vector3.Z, angle));
        part.box(slope, 0.24f, depth);
        part.setVertexTransformationEnabled(false);
        return builder.end();
    }

    private void buildSharedModels() {''')

rep('''        playerBody = own(mb.createCapsule(0.39f, 1.44f, 12, material(0.08f, 0.30f, 0.72f), ATTR));
        enemyBody = own(mb.createCapsule(0.39f, 1.44f, 12, material(0.70f, 0.10f, 0.065f), ATTR));
        playerCavalryBody = own(mb.createCapsule(0.39f, 1.48f, 12, material(0.07f, 0.27f, 0.67f), ATTR));
        enemyCavalryBody = own(mb.createCapsule(0.39f, 1.48f, 12, material(0.65f, 0.085f, 0.055f), ATTR));''', '''        playerBody = own(buildHumanoidBody(0.08f, 0.30f, 0.72f, false));
        enemyBody = own(buildHumanoidBody(0.70f, 0.10f, 0.065f, false));
        playerCavalryBody = own(buildHumanoidBody(0.07f, 0.27f, 0.67f, true));
        enemyCavalryBody = own(buildHumanoidBody(0.65f, 0.085f, 0.055f, true));''')
rep('''        horseModel = own(mb.createBox(1.72f, 0.82f, 0.72f, material(0.28f, 0.15f, 0.065f), ATTR));
        horseHeadModel = own(mb.createBox(0.55f, 0.70f, 0.52f, material(0.30f, 0.16f, 0.07f), ATTR));''', '''        horseModel = own(buildHorseBody());
        horseHeadModel = own(buildHorseHead());''')
rep('''        houseRoofModel = own(mb.createCone(4.85f, 2.45f, 4.15f, 4, textured(roofTexture, 0.80f, 0.82f, 0.82f), ATTR_TEX));''',
    '''        houseRoofModel = own(buildGableRoof(4.75f, 3.95f, 1.55f, textured(roofTexture, 0.80f, 0.82f, 0.82f)));''')
rep('''        barracksRoofModel = own(mb.createCone(6.55f, 2.55f, 5.05f, 4, textured(roofTexture, 0.78f, 0.78f, 0.78f), ATTR_TEX));''',
    '''        barracksRoofModel = own(buildGableRoof(6.45f, 4.85f, 1.75f, textured(roofTexture, 0.78f, 0.78f, 0.78f)));''')

# Lower roof instances because gable geometry is centered differently than the old pyramids.
rep('b.roof.transform.setToTranslation(b.pos.x, 3.46f, b.pos.z).rotate(Vector3.Y, 45f);',
    'b.roof.transform.setToTranslation(b.pos.x, 3.12f, b.pos.z);')
rep('b.roof.transform.setToTranslation(b.pos.x, 3.83f, b.pos.z).rotate(Vector3.Y, 45f);',
    'b.roof.transform.setToTranslation(b.pos.x, 3.48f, b.pos.z);')

# Formation: durable melee up front, archers behind, villagers last.
rep('''        group.sort(Comparator.comparingInt(a -> a.id));
        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(group.size()))); float spacing = 1.38f;''', '''        group.sort(Comparator.comparingInt(a -> formationRank(a.type) * 10000 + a.id));
        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(group.size()))); float spacing = 1.48f;''')
rep('''    private void issueMove(float x, float z) {''', '''    private int formationRank(UnitType type) {
        return switch (type) {
            case CAVALRY -> 0;
            case SWORD -> 1;
            case ARCHER -> 2;
            case VILLAGER -> 3;
        };
    }

    private void issueMove(float x, float z) {''')

# Lightweight navigation: armies cross the river via the bridge instead of walking through water.
rep('''    private void moveToward(Unit u, float x, float z, float dt) {
        float dx = x - u.pos.x, dz = z - u.pos.z;''', '''    private void moveToward(Unit u, float x, float z, float dt) {
        float navX = x, navZ = z;
        boolean crossingEast = u.pos.x < -0.6f && x > 9.0f;
        boolean crossingWest = u.pos.x > 9.0f && x < -0.6f;
        if ((crossingEast || crossingWest) && Math.abs(u.pos.z - 0.3f) > 3.0f) {
            navX = crossingEast ? 0.2f : 8.2f;
            navZ = 0.3f;
        }
        float dx = navX - u.pos.x, dz = navZ - u.pos.z;''')
rep('''        faceToward(u, x, z);''', '''        faceToward(u, navX, navZ);''', 1)

p.write_text(s, encoding="utf-8")

# Force HD during the initial visual-validation window even on SwiftShader.
v7p = CORE / "ImperiumGameV7.java"
v = v7p.read_text(encoding="utf-8")
v = v.replace('private Field lowQualityField;', 'private Field lowQualityField;\n    private Field lowFpsTimerField;')
v = v.replace('''            lowQualityField = c.getDeclaredField("lowQuality");
            lowQualityField.setAccessible(true);''', '''            lowQualityField = c.getDeclaredField("lowQuality");
            lowQualityField.setAccessible(true);
            lowFpsTimerField = c.getDeclaredField("lowFpsTimer");
            lowFpsTimerField.setAccessible(true);''')
v = v.replace('if ("GAME".equals(mode)) hdGrace = 7.0f;', 'if ("GAME".equals(mode)) hdGrace = 10.0f;')
v = v.replace('''                hdGrace -= dt;
                lowQualityField.setBoolean(game, false);''', '''                hdGrace -= dt;
                lowQualityField.setBoolean(game, false);
                lowFpsTimerField.setFloat(game, -20f);''')
# More natural grass balance and warmer daylight.
v = v.replace('float r = 0.17f + fine * 0.045f + broad * 0.018f;', 'float r = 0.22f + fine * 0.040f + broad * 0.018f;')
v = v.replace('float g = 0.34f + fine * 0.070f + broad * 0.030f;', 'float g = 0.37f + fine * 0.060f + broad * 0.025f;')
v = v.replace('float b = 0.105f + fine * 0.035f + broad * 0.012f;', 'float b = 0.15f + fine * 0.030f + broad * 0.012f;')
v7p.write_text(v, encoding="utf-8")
print("Applied v0.10 composite-model and navigation upgrade")
