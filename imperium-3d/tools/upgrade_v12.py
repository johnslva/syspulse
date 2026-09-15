from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
p = CORE / "ImperiumGameV6.java"
s = p.read_text(encoding="utf-8")


def rep(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v12 missing target: " + old[:120])
    s = s.replace(old, new, n)


rep('private static final String VERSION = "v0.11.0-alpha";', 'private static final String VERSION = "v0.12.0-alpha";')

# Separate limbs from the torso so units can actually walk/attack instead of bobbing as rigid statues.
old_body = '''    private Model buildHumanoidBody(float r, float g, float b, boolean mounted) {
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
    }'''
new_body = '''    private Model buildHumanoidBody(float r, float g, float b, boolean mounted) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder cloth = builder.part("cloth", GL20.GL_TRIANGLES, ATTR, material(r, g, b));
        Matrix4 tr = new Matrix4();
        cloth.setVertexTransform(tr.setToTranslation(0f, mounted ? 0.12f : 0.08f, 0f));
        cloth.box(0.74f, mounted ? 0.80f : 0.90f, 0.40f);
        cloth.setVertexTransform(tr.setToTranslation(0f, -0.34f, 0f));
        cloth.cone(0.88f, 0.46f, 0.66f, 8);
        MeshPartBuilder belt = builder.part("belt", GL20.GL_TRIANGLES, ATTR, material(0.20f, 0.12f, 0.055f));
        belt.setVertexTransform(tr.setToTranslation(0f, -0.18f, 0f));
        belt.box(0.80f, 0.10f, 0.44f);
        cloth.setVertexTransformationEnabled(false);
        belt.setVertexTransformationEnabled(false);
        return builder.end();
    }'''
rep(old_body, new_body)

# Unit state + shared limb meshes.
rep('''        ModelInstance shadow;

        Unit(int id, int team, UnitType type, float x, float z) {''', '''        ModelInstance shadow;
        ModelInstance leftArm;
        ModelInstance rightArm;
        ModelInstance leftLeg;
        ModelInstance rightLeg;

        Unit(int id, int team, UnitType type, float x, float z) {''')

rep('''    private Model shadowModel;
    private Model projectileModel;''', '''    private Model shadowModel;
    private Model projectileModel;
    private Model playerArmModel;
    private Model enemyArmModel;
    private Model playerLegModel;
    private Model enemyLegModel;''')

rep('''        projectileModel = own(mb.createBox(0.07f, 0.07f, 0.62f, material(0.27f, 0.16f, 0.06f), ATTR));''', '''        projectileModel = own(mb.createBox(0.07f, 0.07f, 0.62f, material(0.27f, 0.16f, 0.06f), ATTR));
        playerArmModel = own(mb.createBox(0.20f, 0.72f, 0.22f, material(0.075f, 0.275f, 0.67f), ATTR));
        enemyArmModel = own(mb.createBox(0.20f, 0.72f, 0.22f, material(0.66f, 0.085f, 0.055f), ATTR));
        playerLegModel = own(mb.createBox(0.25f, 0.74f, 0.28f, material(0.10f, 0.19f, 0.28f), ATTR));
        enemyLegModel = own(mb.createBox(0.25f, 0.74f, 0.28f, material(0.25f, 0.12f, 0.085f), ATTR));''')

rep('''        u.shadow = new ModelInstance(shadowModel);
        switch (type) {''', '''        u.shadow = new ModelInstance(shadowModel);
        u.leftArm = new ModelInstance(team == PLAYER ? playerArmModel : enemyArmModel);
        u.rightArm = new ModelInstance(team == PLAYER ? playerArmModel : enemyArmModel);
        u.leftLeg = new ModelInstance(team == PLAYER ? playerLegModel : enemyLegModel);
        u.rightLeg = new ModelInstance(team == PLAYER ? playerLegModel : enemyLegModel);
        switch (type) {''')

# Animate four limbs independently. Weapon motion is layered on top of the arm gait.
rep('''        float swing = u.cooldown > u.attackPeriod * 0.55f ? 38f : 0f;
        float yaw = u.facingYaw;

        if (u.type == UnitType.CAVALRY) {''', '''        float swing = u.cooldown > u.attackPeriod * 0.55f ? 38f : 0f;
        float yaw = u.facingYaw;
        float gait = u.moving ? (float)Math.sin(u.anim) : 0f;
        float rx = (float)Math.cos(yaw * DEG);
        float rz = -(float)Math.sin(yaw * DEG);

        if (u.type == UnitType.CAVALRY) {''')

rep('''            u.body.transform.setToTranslation(u.pos.x, 1.74f + bob, u.pos.z).rotate(Vector3.Y, yaw);
            u.head.transform.setToTranslation(u.pos.x, 2.66f + bob, u.pos.z);''', '''            u.body.transform.setToTranslation(u.pos.x, 1.74f + bob, u.pos.z).rotate(Vector3.Y, yaw);
            u.leftArm.transform.setToTranslation(u.pos.x - rx * 0.46f, 1.82f + bob, u.pos.z - rz * 0.46f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 20f + gait * 10f);
            u.rightArm.transform.setToTranslation(u.pos.x + rx * 0.46f, 1.82f + bob, u.pos.z + rz * 0.46f).rotate(Vector3.Y, yaw).rotate(Vector3.X, -18f - gait * 10f - swing * 0.35f);
            u.leftLeg.transform.setToTranslation(u.pos.x - rx * 0.22f, 1.25f + bob, u.pos.z - rz * 0.22f).rotate(Vector3.Y, yaw).rotate(Vector3.X, -28f);
            u.rightLeg.transform.setToTranslation(u.pos.x + rx * 0.22f, 1.25f + bob, u.pos.z + rz * 0.22f).rotate(Vector3.Y, yaw).rotate(Vector3.X, 28f);
            u.head.transform.setToTranslation(u.pos.x, 2.66f + bob, u.pos.z);''')

rep('''        u.body.transform.setToTranslation(u.pos.x, 0.97f + bob, u.pos.z).rotate(Vector3.Y, yaw);
        u.head.transform.setToTranslation(u.pos.x, 1.88f + bob, u.pos.z);''', '''        u.body.transform.setToTranslation(u.pos.x, 0.97f + bob, u.pos.z).rotate(Vector3.Y, yaw);
        float legSwing = gait * 27f;
        float armSwing = gait * 24f;
        u.leftLeg.transform.setToTranslation(u.pos.x - rx * 0.20f, 0.46f + bob, u.pos.z - rz * 0.20f).rotate(Vector3.Y, yaw).rotate(Vector3.X, legSwing);
        u.rightLeg.transform.setToTranslation(u.pos.x + rx * 0.20f, 0.46f + bob, u.pos.z + rz * 0.20f).rotate(Vector3.Y, yaw).rotate(Vector3.X, -legSwing);
        u.leftArm.transform.setToTranslation(u.pos.x - rx * 0.46f, 1.12f + bob, u.pos.z - rz * 0.46f).rotate(Vector3.Y, yaw).rotate(Vector3.X, -armSwing);
        u.rightArm.transform.setToTranslation(u.pos.x + rx * 0.46f, 1.12f + bob, u.pos.z + rz * 0.46f).rotate(Vector3.Y, yaw).rotate(Vector3.X, armSwing - swing * 0.55f);
        u.head.transform.setToTranslation(u.pos.x, 1.88f + bob, u.pos.z);''')

# Render limbs only in detailed LOD; distance rendering stays cheap.
rep('''            modelBatch.render(u.head, environment);
            modelBatch.render(u.helmet, environment);
            modelBatch.render(u.weapon, environment);''', '''            modelBatch.render(u.leftLeg, environment);
            modelBatch.render(u.rightLeg, environment);
            modelBatch.render(u.leftArm, environment);
            modelBatch.render(u.rightArm, environment);
            modelBatch.render(u.head, environment);
            modelBatch.render(u.helmet, environment);
            modelBatch.render(u.weapon, environment);''')

# Richer single-draw-call facades and crenellations for player-built structures.
insert_before = '''    private void buildSharedModels() {'''
helpers = '''    private Model buildHouseFacadeDetail() {
        ModelBuilder b = new ModelBuilder(); b.begin(); Matrix4 tr = new Matrix4();
        MeshPartBuilder wood = b.part("wood", GL20.GL_TRIANGLES, ATTR, material(0.20f, 0.105f, 0.04f));
        for (float x : new float[]{-1.35f, 0f, 1.35f}) { wood.setVertexTransform(tr.setToTranslation(x, 0.08f, 0f)); wood.box(0.18f, 2.15f, 0.14f); }
        wood.setVertexTransform(tr.setToTranslation(0f, 0.70f, 0f)); wood.box(2.85f, 0.16f, 0.14f);
        MeshPartBuilder dark = b.part("dark", GL20.GL_TRIANGLES, ATTR, material(0.055f, 0.075f, 0.080f));
        dark.setVertexTransform(tr.setToTranslation(-0.70f, 0.35f, -0.03f)); dark.box(0.48f, 0.58f, 0.10f);
        dark.setVertexTransform(tr.setToTranslation(0.70f, 0.35f, -0.03f)); dark.box(0.48f, 0.58f, 0.10f);
        MeshPartBuilder door = b.part("door", GL20.GL_TRIANGLES, ATTR, material(0.30f, 0.16f, 0.055f));
        door.setVertexTransform(tr.setToTranslation(0f, -0.38f, -0.04f)); door.box(0.64f, 1.18f, 0.12f);
        wood.setVertexTransformationEnabled(false); dark.setVertexTransformationEnabled(false); door.setVertexTransformationEnabled(false);
        return b.end();
    }

    private Model buildBarracksFacadeDetail() {
        ModelBuilder b = new ModelBuilder(); b.begin(); Matrix4 tr = new Matrix4();
        MeshPartBuilder wood = b.part("frame", GL20.GL_TRIANGLES, ATTR, material(0.25f, 0.13f, 0.045f));
        for (float x : new float[]{-2.1f,-0.7f,0.7f,2.1f}) { wood.setVertexTransform(tr.setToTranslation(x, 0.15f, 0f)); wood.box(0.20f, 2.30f, 0.16f); }
        wood.setVertexTransform(tr.setToTranslation(0f, 0.78f, 0f)); wood.box(4.55f, 0.20f, 0.16f);
        MeshPartBuilder gate = b.part("gate", GL20.GL_TRIANGLES, ATTR, material(0.19f, 0.10f, 0.035f));
        gate.setVertexTransform(tr.setToTranslation(0f, -0.20f, -0.04f)); gate.box(1.50f, 1.72f, 0.14f);
        MeshPartBuilder iron = b.part("iron", GL20.GL_TRIANGLES, ATTR, material(0.18f, 0.19f, 0.18f));
        for (int i=-2;i<=2;i++){ iron.setVertexTransform(tr.setToTranslation(i*0.28f,-0.20f,-0.13f)); iron.box(0.055f,1.64f,0.055f); }
        wood.setVertexTransformationEnabled(false); gate.setVertexTransformationEnabled(false); iron.setVertexTransformationEnabled(false);
        return b.end();
    }

    private Model buildCrenellatedCap(float radius) {
        ModelBuilder b = new ModelBuilder(); b.begin(); Matrix4 tr = new Matrix4();
        MeshPartBuilder stone = b.part("stone", GL20.GL_TRIANGLES, ATTR, textured(stoneTexture, 0.92f, 0.91f, 0.86f));
        stone.setVertexTransform(tr.idt()); stone.cylinder(radius*2f, 0.42f, radius*2f, 14);
        for (int i=0;i<10;i++) {
            float a=(float)(i*Math.PI*2.0/10.0), x=(float)Math.sin(a)*radius*0.78f, z=(float)Math.cos(a)*radius*0.78f;
            stone.setVertexTransform(tr.setToTranslation(x,0.46f,z).rotate(Vector3.Y,(float)Math.toDegrees(a)));
            stone.box(0.62f,0.70f,0.76f);
        }
        stone.setVertexTransformationEnabled(false); return b.end();
    }

    private Model buildWallCrenellations() {
        ModelBuilder b = new ModelBuilder(); b.begin(); Matrix4 tr = new Matrix4();
        MeshPartBuilder stone = b.part("stone", GL20.GL_TRIANGLES, ATTR, textured(stoneTexture, 0.96f,0.95f,0.90f));
        stone.setVertexTransform(tr.setToTranslation(0f,-0.10f,0f)); stone.box(4.55f,0.22f,0.96f);
        for(int i=-4;i<=4;i+=2){ stone.setVertexTransform(tr.setToTranslation(i*0.48f,0.30f,0f)); stone.box(0.46f,0.62f,0.90f); }
        stone.setVertexTransformationEnabled(false); return b.end();
    }

'''
rep(insert_before, helpers + insert_before)

rep('''        houseDetailModel = own(mb.createBox(0.58f, 1.15f, 0.16f, material(0.14f, 0.09f, 0.04f), ATTR));''', '        houseDetailModel = own(buildHouseFacadeDetail());')
rep('''        barracksDetailModel = own(mb.createBox(1.45f, 2.05f, 0.18f, textured(timberTexture, 0.65f, 0.58f, 0.48f), ATTR_TEX));''', '        barracksDetailModel = own(buildBarracksFacadeDetail());')
rep('''        towerRoofModel = own(mb.createCone(3.35f, 2.55f, 3.35f, 14, textured(roofTexture, 0.78f, 0.80f, 0.82f), ATTR_TEX));''', '        towerRoofModel = own(buildCrenellatedCap(1.68f));')
rep('''        wallRoofModel = own(mb.createBox(4.6f, 0.30f, 1.02f, textured(stoneTexture, 0.98f, 0.97f, 0.92f), ATTR_TEX));''', '        wallRoofModel = own(buildWallCrenellations());')

# Reposition richer detail meshes.
rep('''            b.detail.transform.setToTranslation(b.pos.x, 1.15f, b.pos.z - 1.78f);''', '            b.detail.transform.setToTranslation(b.pos.x, 1.20f, b.pos.z - 1.75f);')
rep('''            b.detail.transform.setToTranslation(b.pos.x, 1.15f, b.pos.z - 2.25f);''', '            b.detail.transform.setToTranslation(b.pos.x, 1.35f, b.pos.z - 2.26f);')
rep('''            b.roof.transform.setToTranslation(b.pos.x, 7.48f, b.pos.z);''', '            b.roof.transform.setToTranslation(b.pos.x, 6.45f, b.pos.z);')
rep('''            b.roof.transform.setToTranslation(b.pos.x, 2.40f, b.pos.z);''', '            b.roof.transform.setToTranslation(b.pos.x, 2.42f, b.pos.z);')

# More cinematic but still readable mobile camera.
rep('cameraDistance = 30.5f; cameraYaw = 43f; cameraPitch = 48f;', 'cameraDistance = 29.0f; cameraYaw = 43f; cameraPitch = 46.5f;')

p.write_text(s, encoding="utf-8")

# Art overlay: richer close-range vegetation and less flat lighting.
v7p = CORE / "ImperiumGameV7.java"
v = v7p.read_text(encoding="utf-8")
v = v.replace('environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.32f, 0.35f, 0.32f, 1f));',
              'environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.24f, 0.27f, 0.24f, 1f));')
v = v.replace('environment.add(new DirectionalLight().set(0.24f, 0.29f, 0.34f, 0.35f, -0.45f, 0.55f));',
              'environment.add(new DirectionalLight().set(0.34f, 0.38f, 0.40f, 0.30f, -0.62f, 0.42f));')
v = v.replace('''        buildMixedTrees(mb);
        buildVillageAccents(mb, -25f, -18f, true);''', '''        buildMixedTrees(mb);
        buildMicroVegetation(mb);
        buildVillageAccents(mb, -25f, -18f, true);''')
anchor = '''    private void buildMixedTrees(ModelBuilder mb) {'''
method = '''    private void buildMicroVegetation(ModelBuilder mb) {
        Model grass = own(mb.createCone(0.34f, 0.72f, 0.34f, 5, mat(0.19f, 0.42f, 0.11f), ATTR));
        Model grassDry = own(mb.createCone(0.30f, 0.62f, 0.30f, 5, mat(0.42f, 0.45f, 0.17f), ATTR));
        Model flower = own(mb.createSphere(0.12f, 0.12f, 0.12f, 6, 4, mat(0.72f, 0.66f, 0.30f), ATTR));
        for (int i = 0; i < 150; i++) {
            float x = rng.nextFloat() * 76f - 38f;
            float z = rng.nextFloat() * 76f - 38f;
            if (nearRoad(x, z) || Math.abs(x - 4.2f) < 5.4f || nearCastle(x, z)) continue;
            float s = 0.58f + rng.nextFloat() * 0.72f;
            add((i % 5 == 0) ? grassDry : grass, x, 0.30f * s, z, rng.nextFloat() * 180f, s, s, s);
            if (i % 13 == 0) add(flower, x + 0.22f, 0.52f * s, z - 0.12f, 0f, s, s, s);
        }
    }

'''
if anchor not in v:
    raise RuntimeError('v12 missing V7 vegetation anchor')
v = v.replace(anchor, method + anchor, 1)
v7p.write_text(v, encoding="utf-8")
print("Applied v0.12 animated units, richer architecture and microvegetation")
