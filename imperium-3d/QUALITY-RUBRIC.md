# Imperium Vale — Quality Rubric

Meta final: 9,9/10 no Galaxy S25 FE.

A nota nao e apenas subjetiva. Cada build candidata precisa ser avaliada nos cinco eixos abaixo.

| Eixo | Peso | 9,5+ exige |
| --- | ---: | --- |
| Visual | 35% | Cena 3D coerente, terreno organico, arquitetura detalhada, tropas legiveis, boa iluminacao e sem aparencia de placeholder |
| Performance | 20% | Meta de 60 FPS no S25 FE em partida normal, sem stutter perceptivel; LOD/culling/adaptacao funcionando |
| Jogabilidade | 20% | Economia, coleta, producao, construcao, combate, IA em 3 niveis e objetivo de vitoria completos |
| Touch / UX | 15% | Selecao, movimento, camera, zoom, construcao e HUD confortaveis em tela pequena |
| Estabilidade | 10% | 10/10 obrigatorio: zero crash/ANR no smoke test, cold start, resume e stress touch |

## Formula

`nota = visual*0,35 + performance*0,20 + jogabilidade*0,20 + touch*0,15 + estabilidade*0,10`

## Gate para declarar 9,9

- Nota ponderada >= 9,9.
- Nenhum eixo abaixo de 9,5.
- Estabilidade = 10,0.
- APK instala e abre normalmente.
- Smoke test Android completo passa.
- Captura real da build e inspecionada antes de promover a versao.
- Teste final no Galaxy S25 FE do usuario confirma fluidez e controles.

## Baseline v0.6.0-alpha

Avaliacao provisoria baseada na captura e nos testes automatizados:

- Visual: 3,5/10.
- Performance: 6,0/10 (emulador usa SwiftShader, portanto nao representa a GPU do S25 FE).
- Jogabilidade: 6,0/10.
- Touch / UX: 6,5/10.
- Estabilidade: 9,0/10.
- Nota ponderada aproximada: 5,5/10.

O principal gargalo e visual. As proximas iteracoes devem melhorar arte/render sem regredir estabilidade ou performance.
