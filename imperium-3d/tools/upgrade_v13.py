from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
p = CORE / "ImperiumGameV6.java"
s = p.read_text(encoding="utf-8")


def rep(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v13 missing target: " + old[:120])
    s = s.replace(old, new, n)


rep('private static final String VERSION = "v0.12.0-alpha";', 'private static final String VERSION = "v0.13.0-alpha";')
rep('import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;',
    'import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;\nimport com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;')
rep('import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;',
    'import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;\nimport com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;')

# Short-lived impact VFX, rendered as lightweight geometry.
rep('''    private static final class StaticPiece {''', '''    private static final class Impact {
        final Vector3 pos = new Vector3();
        final ModelInstance flash;
        final ModelInstance ring;
        float life = 0.30f;

        Impact(Model flashModel, Model ringModel, float x, float y, float z) {
            pos.set(x, y, z);
            flash = new ModelInstance(flashModel);
            ring = new ModelInstance(ringModel);
        }
    }

    private static final class StaticPiece {''')

rep('''    private ModelBatch modelBatch;
    private ShapeRenderer shapes;''', '''    private ModelBatch modelBatch;
    private ModelBatch shadowBatch;
    private DirectionalShadowLight shadowLight;
    private ShapeRenderer shapes;''')
rep('''    private final List<Projectile> projectiles = new ArrayList<>();''', '''    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Impact> impacts = new ArrayList<>();''')
rep('''    private Model projectileModel;
    private Model playerArmModel;''', '''    private Model projectileModel;
    private Model impactFlashModel;
    private Model impactRingModel;
    private Model playerArmModel;''')

# Use the shadow light as the main sunlight so the shader has only two directional lights total.
rep('''        modelBatch = new ModelBatch();
        shapes = new ShapeRenderer();''', '''        modelBatch = new ModelBatch();
        shadowBatch = new ModelBatch(new DepthShaderProvider());
        shapes = new ShapeRenderer();''')
rep('''        environment.add(new DirectionalLight().set(1.10f, 0.95f, 0.76f, -0.58f, -0.82f, -0.33f));
        environment.add(new DirectionalLight().set(0.16f, 0.25f, 0.36f, 0.40f, -0.38f, 0.48f));''', '''        shadowLight = new DirectionalShadowLight(768, 768, 68f, 68f, 1f, 105f);
        shadowLight.set(1.10f, 0.95f, 0.76f, -0.58f, -0.82f, -0.33f);
        environment.add(shadowLight);
        environment.shadowMap = shadowLight;
        environment.add(new DirectionalLight().set(0.16f, 0.25f, 0.36f, 0.40f, -0.38f, 0.48f));''')

rep('''        projectileModel = own(mb.createBox(0.07f, 0.07f, 0.62f, material(0.27f, 0.16f, 0.06f), ATTR));''', '''        projectileModel = own(mb.createBox(0.07f, 0.07f, 0.62f, material(0.27f, 0.16f, 0.06f), ATTR));
        impactFlashModel = own(mb.createSphere(0.42f, 0.42f, 0.42f, 8, 6, material(1.0f, 0.62f, 0.16f), ATTR));
        impactRingModel = own(mb.createCylinder(0.92f, 0.035f, 0.92f, 12, alphaMaterial(1.0f, 0.42f, 0.08f, 0.58f), ATTR));''')

# Shadow pass before the lit scene. Low-quality mode falls back to the cheap contact blobs.
rep('''        ScreenUtils.clear(0.54f, 0.63f, 0.61f, 1f, true);''', '''        if (!lowQuality) {
            environment.shadowMap = shadowLight;
            renderShadowMap();
        } else {
            environment.shadowMap = null;
        }

        ScreenUtils.clear(0.54f, 0.63f, 0.61f, 1f, true);''')

anchor = '''    private void renderVisibleChunks() {'''
shadow_method = '''    private void renderShadowMap() {
        shadowLight.begin(camera);
        shadowBatch.begin(shadowLight.getCamera());
        for (Chunk c : chunks) if (c.cache != null) shadowBatch.render(c.cache);
        for (Building b : buildings) {
            if (b.hp <= 0f) continue;
            shadowBatch.render(b.body); shadowBatch.render(b.roof);
        }
        for (ResourceNode n : resources) if (n.amount > 0f) shadowBatch.render(n.primary);
        for (Unit u : units) {
            if (u.hp <= 0f) continue;
            if (u.mount != null) { shadowBatch.render(u.mount); shadowBatch.render(u.mountHead); }
            shadowBatch.render(u.body);
            shadowBatch.render(u.leftLeg); shadowBatch.render(u.rightLeg);
            shadowBatch.render(u.leftArm); shadowBatch.render(u.rightArm);
            shadowBatch.render(u.head); shadowBatch.render(u.helmet);
            if (u.weapon != null) shadowBatch.render(u.weapon);
            if (u.offhand != null) shadowBatch.render(u.offhand);
        }
        shadowBatch.end();
        shadowLight.end();
    }

'''
rep(anchor, shadow_method + anchor)

rep('''        if (detail && d2 < 1200f) modelBatch.render(u.shadow, environment);''',
    '''        if (lowQuality && d2 < 1200f) modelBatch.render(u.shadow, environment);''')
rep('''        for (Projectile p : projectiles) modelBatch.render(p.model, environment);''', '''        for (Projectile p : projectiles) modelBatch.render(p.model, environment);
        for (Impact i : impacts) { modelBatch.render(i.flash, environment); modelBatch.render(i.ring, environment); }''')

# Update impact animation together with projectiles.
rep('''    private void updateProjectiles(float dt) {
        Iterator<Projectile> it = projectiles.iterator();''', '''    private void updateProjectiles(float dt) {
        Iterator<Impact> impactIt = impacts.iterator();
        while (impactIt.hasNext()) {
            Impact i = impactIt.next();
            i.life -= dt;
            if (i.life <= 0f) { impactIt.remove(); continue; }
            float t = 1f - i.life / 0.30f;
            float pulse = 0.45f + t * 1.35f;
            i.flash.transform.setToTranslation(i.pos).scale(Math.max(0.10f, 1f - t) * 0.75f, Math.max(0.10f, 1f - t) * 0.75f, Math.max(0.10f, 1f - t) * 0.75f);
            i.ring.transform.setToTranslation(i.pos.x, 0.09f, i.pos.z).scale(pulse, 1f, pulse);
        }

        Iterator<Projectile> it = projectiles.iterator();''')

rep('''    private void spawnProjectile(float sx, float sy, float sz, float ex, float ey, float ez) {''', '''    private void spawnImpact(float x, float y, float z) {
        if (impacts.size() >= (lowQuality ? 5 : 18)) return;
        Impact i = new Impact(impactFlashModel, impactRingModel, x, y, z);
        i.flash.transform.setToTranslation(x, y, z);
        i.ring.transform.setToTranslation(x, 0.09f, z);
        impacts.add(i);
    }

    private void spawnProjectile(float sx, float sy, float sz, float ex, float ey, float ez) {''')

# Impact feedback for melee/ranged hits and defensive fire.
rep('''            target.hp -= 18f;
            base.cooldown = 1.32f;''', '''            target.hp -= 18f;
            spawnImpact(target.pos.x, 1.15f, target.pos.z);
            base.cooldown = 1.32f;''')
rep('''                target.hp -= 16f;
                b.cooldown = 1.15f;''', '''                target.hp -= 16f;
                spawnImpact(target.pos.x, 1.15f, target.pos.z);
                b.cooldown = 1.15f;''')
rep('''                enemy.hp -= u.damage; u.cooldown = u.attackPeriod;''', '''                enemy.hp -= u.damage; u.cooldown = u.attackPeriod;
                spawnImpact(enemy.pos.x, enemy.type == UnitType.CAVALRY ? 1.45f : 1.05f, enemy.pos.z);''')
rep('''                base.hp = Math.max(0f, base.hp - u.damage * 0.72f);
                u.cooldown = u.attackPeriod;''', '''                base.hp = Math.max(0f, base.hp - u.damage * 0.72f);
                spawnImpact(base.pos.x + (rng.nextFloat() - 0.5f) * 3.5f, 2.2f, base.pos.z + (rng.nextFloat() - 0.5f) * 3.5f);
                u.cooldown = u.attackPeriod;''')

# Clear VFX between games and dispose the shadow resources.
rep('''        units.clear(); resources.clear(); buildings.clear(); trainQueue.clear(); projectiles.clear(); selected.clear();''',
    '''        units.clear(); resources.clear(); buildings.clear(); trainQueue.clear(); projectiles.clear(); impacts.clear(); selected.clear();''', 1)
rep('''        if (modelBatch != null) modelBatch.dispose();''', '''        if (modelBatch != null) modelBatch.dispose();
        if (shadowBatch != null) shadowBatch.dispose();
        if (shadowLight != null) shadowLight.dispose();''')

p.write_text(s, encoding="utf-8")
print("Applied v0.13 dynamic shadows and combat impact VFX")
