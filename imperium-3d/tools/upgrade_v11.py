from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "core/src/main/java/com/imperiumvale/core"
p = CORE / "ImperiumGameV6.java"
s = p.read_text(encoding="utf-8")


def rep(old, new, n=1):
    global s
    if s.count(old) < n:
        raise RuntimeError("v11 missing target: " + old[:110])
    s = s.replace(old, new, n)


rep('private static final String VERSION = "v0.10.0-alpha";', 'private static final String VERSION = "v0.11.0-alpha";')
rep('private enum BuildingType { HOUSE, BARRACKS, TOWER }', 'private enum BuildingType { HOUSE, BARRACKS, TOWER, WALL }')
rep('''                case TOWER -> maxHp = 780f;''', '''                case TOWER -> maxHp = 780f;
                case WALL -> maxHp = 1100f;''')

rep('''    private Model towerBodyModel;
    private Model towerRoofModel;
    private Model towerDetailModel;''', '''    private Model towerBodyModel;
    private Model towerRoofModel;
    private Model towerDetailModel;
    private Model wallBodyModel;
    private Model wallRoofModel;
    private Model wallDetailModel;''')
rep('''    private final Rectangle towerButton = new Rectangle();''', '''    private final Rectangle towerButton = new Rectangle();
    private final Rectangle wallButton = new Rectangle();''')

rep('''        towerBodyModel = own(mb.createCylinder(2.75f, 6.2f, 2.75f, 14, textured(stoneTexture, 0.88f, 0.87f, 0.82f), ATTR_TEX));
        towerRoofModel = own(mb.createCone(3.35f, 2.55f, 3.35f, 14, textured(roofTexture, 0.78f, 0.80f, 0.82f), ATTR_TEX));
        towerDetailModel = own(mb.createBox(0.52f, 1.05f, 0.16f, material(0.08f, 0.09f, 0.09f), ATTR));''', '''        towerBodyModel = own(mb.createCylinder(2.75f, 6.2f, 2.75f, 14, textured(stoneTexture, 0.88f, 0.87f, 0.82f), ATTR_TEX));
        towerRoofModel = own(mb.createCone(3.35f, 2.55f, 3.35f, 14, textured(roofTexture, 0.78f, 0.80f, 0.82f), ATTR_TEX));
        towerDetailModel = own(mb.createBox(0.52f, 1.05f, 0.16f, material(0.08f, 0.09f, 0.09f), ATTR));
        wallBodyModel = own(mb.createBox(4.4f, 2.25f, 0.82f, textured(stoneTexture, 0.90f, 0.89f, 0.84f), ATTR_TEX));
        wallRoofModel = own(mb.createBox(4.6f, 0.30f, 1.02f, textured(stoneTexture, 0.98f, 0.97f, 0.92f), ATTR_TEX));
        wallDetailModel = own(mb.createBox(0.48f, 2.45f, 1.02f, textured(stoneTexture, 0.74f, 0.74f, 0.71f), ATTR_TEX));''')

# Make the initial medieval village use true gabled roofs rather than pyramids.
old_house_roof = '''        Model roof = own(mb.createCone(4.75f, 2.35f, 4.05f, 4, textured(roofTexture,
            team == PLAYER ? 0.58f : 0.92f, team == PLAYER ? 0.72f : 0.68f, team == PLAYER ? 1.0f : 0.65f), ATTR_TEX));'''
new_house_roof = '''        Model roof = own(buildGableRoof(4.75f, 3.95f, 1.55f, textured(roofTexture,
            team == PLAYER ? 0.58f : 0.92f, team == PLAYER ? 0.72f : 0.68f, team == PLAYER ? 1.0f : 0.65f)));'''
rep(old_house_roof, new_house_roof)
rep('addStatic(roof, sx, 3.35f, sz, 45f, 1f, 1f, 1f, false);',
    'addStatic(roof, sx, 3.12f, sz, 0f, 1f, 1f, 1f, false);')
rep('''        Model barracksRoof = own(mb.createCone(6.2f, 2.4f, 4.8f, 4, new Material(ColorAttribute.createDiffuse(teamColor.cpy().mul(0.72f))), ATTR));''',
    '''        Model barracksRoof = own(buildGableRoof(6.2f, 4.7f, 1.70f, new Material(ColorAttribute.createDiffuse(teamColor.cpy().mul(0.72f)))));''')
rep('addStatic(barracksRoof, x - side * 6.5f, 3.58f, z + side * 4.9f, 45f, 1f, 1f, 1f, false);',
    'addStatic(barracksRoof, x - side * 6.5f, 3.30f, z + side * 4.9f, 0f, 1f, 1f, 1f, false);')

# Remove toy-like team-color cone roofs from fortress towers; keep banners for team identity.
rep('''        Model cone = own(mb.createCone(3.0f, 2.6f, 3.0f, 14, new Material(ColorAttribute.createDiffuse(teamColor)), ATTR));''',
    '''        Model cone = own(mb.createCylinder(2.85f, 0.68f, 2.85f, 14, textured(stoneTexture, 0.88f, 0.87f, 0.82f), ATTR_TEX));''')
rep('addStatic(cone, x + o[0], 7.9f, z + o[1], 0f, 1f, 1f, 1f, false);',
    'addStatic(cone, x + o[0], 6.78f, z + o[1], 0f, 1f, 1f, 1f, false);')

# V5/V7 already adds broadleaf trees; reduce the old conifer monoculture.
rep('for (int i = 0; i < 82; i++) {', 'for (int i = 0; i < 46; i++) {')

# Dynamic construction supports stone walls.
rep('''        } else {
            b.body = new ModelInstance(towerBodyModel); b.roof = new ModelInstance(towerRoofModel); b.detail = new ModelInstance(towerDetailModel);
        }''', '''        } else if (type == BuildingType.WALL) {
            b.body = new ModelInstance(wallBodyModel); b.roof = new ModelInstance(wallRoofModel); b.detail = new ModelInstance(wallDetailModel);
        } else {
            b.body = new ModelInstance(towerBodyModel); b.roof = new ModelInstance(towerRoofModel); b.detail = new ModelInstance(towerDetailModel);
        }''')
rep('''        } else {
            b.body.transform.setToTranslation(b.pos.x, 3.10f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 7.48f, b.pos.z);
            b.detail.transform.setToTranslation(b.pos.x, 3.20f, b.pos.z - 1.46f);
        }
    }''', '''        } else if (b.type == BuildingType.WALL) {
            b.body.transform.setToTranslation(b.pos.x, 1.13f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 2.40f, b.pos.z);
            b.detail.transform.setToTranslation(b.pos.x - 1.55f, 1.23f, b.pos.z);
        } else {
            b.body.transform.setToTranslation(b.pos.x, 3.10f, b.pos.z);
            b.roof.transform.setToTranslation(b.pos.x, 7.48f, b.pos.z);
            b.detail.transform.setToTranslation(b.pos.x, 3.20f, b.pos.z - 1.46f);
        }
    }''')

# Four compact build buttons.
old_build = '''        float bw = Math.min(150f, w * 0.13f), bh = Math.max(48f, h * 0.07f), x = 16f, y = bottom + 18f;
        houseButton.set(x, y, bw, bh); barracksButton.set(x + bw + 8f, y, bw, bh); towerButton.set(x + (bw + 8f) * 2f, y, bw, bh);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonFill(houseButton, pendingBuild == BuildingType.HOUSE); drawButtonFill(barracksButton, pendingBuild == BuildingType.BARRACKS);
        drawButtonFill(towerButton, pendingBuild == BuildingType.TOWER); shapes.end();
        sprites.setProjectionMatrix(uiMatrix); sprites.begin(); font.setColor(0.90f, 0.84f, 0.70f, 1f);
        font.draw(sprites, "CASA 100M", houseButton.x, houseButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "QUARTEL 150M", barracksButton.x, barracksButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "TORRE 60M 160P", towerButton.x, towerButton.y + bh * 0.60f, bw, Align.center, false); sprites.end();'''
new_build = '''        float bw = Math.min(138f, w * 0.115f), bh = Math.max(48f, h * 0.07f), x = 16f, y = bottom + 18f;
        houseButton.set(x, y, bw, bh); barracksButton.set(x + bw + 8f, y, bw, bh);
        towerButton.set(x + (bw + 8f) * 2f, y, bw, bh); wallButton.set(x + (bw + 8f) * 3f, y, bw, bh);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonFill(houseButton, pendingBuild == BuildingType.HOUSE); drawButtonFill(barracksButton, pendingBuild == BuildingType.BARRACKS);
        drawButtonFill(towerButton, pendingBuild == BuildingType.TOWER); drawButtonFill(wallButton, pendingBuild == BuildingType.WALL); shapes.end();
        sprites.setProjectionMatrix(uiMatrix); sprites.begin(); font.setColor(0.90f, 0.84f, 0.70f, 1f);
        font.draw(sprites, "CASA 100M", houseButton.x, houseButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "QUARTEL 150M", barracksButton.x, barracksButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "TORRE 60M 160P", towerButton.x, towerButton.y + bh * 0.60f, bw, Align.center, false);
        font.draw(sprites, "MURO 35M 90P", wallButton.x, wallButton.y + bh * 0.60f, bw, Align.center, false); sprites.end();'''
rep(old_build, new_build)

rep('''        } else {
            if (wood < 60f || stone < 160f) return false; wood -= 60f; stone -= 160f;
        }''', '''        } else if (pendingBuild == BuildingType.WALL) {
            if (wood < 35f || stone < 90f) return false; wood -= 35f; stone -= 90f;
        } else {
            if (wood < 60f || stone < 160f) return false; wood -= 60f; stone -= 160f;
        }''')
rep('''            if (inside(towerButton, x, uy)) { selectBuild(BuildingType.TOWER); return; }
        }''', '''            if (inside(towerButton, x, uy)) { selectBuild(BuildingType.TOWER); return; }
            if (inside(wallButton, x, uy)) { selectBuild(BuildingType.WALL); return; }
        }''')

# AI spends its stone on real defensive towers rather than hoarding it.
insert_ai = '''        int enemyCount = countTeam(ENEMY);'''
replace_ai = '''        int desiredTowers = 1 + difficulty;
        if (aiStone >= 160f && aiWood >= 60f && countBuildings(ENEMY, BuildingType.TOWER) < desiredTowers) {
            aiStone -= 160f; aiWood -= 60f;
            createAiTower(23f + rng.nextFloat() * 8f - 4f, 18f + rng.nextFloat() * 8f - 4f);
        }

        int enemyCount = countTeam(ENEMY);'''
rep(insert_ai, replace_ai)

rep('''    private boolean aiCanTrain(UnitType type) {''', '''    private Building createAiTower(float x, float z) {
        Building b = new Building(nextBuildingId++, ENEMY, BuildingType.TOWER, x, z);
        b.body = new ModelInstance(towerBodyModel);
        b.roof = new ModelInstance(towerRoofModel);
        b.detail = new ModelInstance(towerDetailModel);
        updateBuildingTransform(b);
        buildings.add(b);
        return b;
    }

    private int countBuildings(int team, BuildingType type) {
        int n = 0;
        for (Building b : buildings) if (b.team == team && b.type == type && b.hp > 0f) n++;
        return n;
    }

    private boolean aiCanTrain(UnitType type) {''')

# Slightly closer, lower gameplay camera to showcase unit and architecture detail.
rep('cameraDistance = 34f; cameraYaw = 43f; cameraPitch = 52f;',
    'cameraDistance = 30.5f; cameraYaw = 43f; cameraPitch = 48f;')

p.write_text(s, encoding="utf-8")

# Art overlay: remove flat oval patches and keep the continuous textured ground + mixed woodland.
v7p = CORE / "ImperiumGameV7.java"
v = v7p.read_text(encoding="utf-8")
v = v.replace('for (int i = 0; i < 24; i++) {', 'for (int i = 0; i < 0; i++) {')
v7p.write_text(v, encoding="utf-8")
print("Applied v0.11 architecture, walls and AI defense upgrade")
