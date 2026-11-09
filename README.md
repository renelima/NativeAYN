<p align="center"><img src="android/images/logo.png" alt="Logo do NativeAYN" width="140"></p>

<h1 align="center">NativeAYN</h1>

<p align="center"><b>Seus jogos de Switch 2 rodando de forma nativa no seu Android portátil.</b></p>

<p align="center">
<a href="#como-funciona">Como funciona</a> ·
<a href="#recursos">Recursos</a> ·
<a href="#jogos">Jogos</a> ·
<a href="#instalar">Instalar</a> ·
<a href="#compilar">Compilar</a> ·
<a href="#aviso-legal">Aviso legal</a>
</p>

---

O **NativeAYN** é um recompilador nativo para Android: em vez de interpretar o código do jogo instrução por instrução, ele executa o **código ARM64 original do seu próprio jogo** de Switch 2 direto no processador do aparelho — o que o Android não entende é reescrito uma única vez, e os gráficos são reconstruídos em Vulkan. O jogo roda como ele mesmo, e o NativeAYN responde às chamadas de sistema que ele espera encontrar.

Projetado desde o primeiro dia para **portáteis Android** (AYN Odin e semelhantes): tela sempre ativa durante o jogo, controles físicos configuráveis por interface e escolha de driver GPU por jogo.

## Como funciona

Três peças trabalham juntas:

1. **O jogo** — o executável do seu dump é reescrito uma única vez (o "pack"), trocando chamadas incompatíveis por equivalentes do Android. Depois disso, é o código da Nintendo/terceiros que roda na sua CPU.
2. **O host** — uma camada fina que atende as chamadas de sistema do jogo (memória, threads, filesystem, áudio) e traduz os gráficos NVN do Switch 2 para Vulkan, incluindo task shaders e mesh shaders.
3. **O launcher** — onde você adiciona o jogo, escolhe driver, mapeia o controle e acompanha o log de cada execução.

Os shaders do jogo são traduzidos uma vez, antes de jogar, e ficam no pack — não há compilação surpresa em plena gameplay além do cache normal do driver.

## Recursos

- **🎮 Mapeamento de controles nativo** — seção *Controller* no painel lateral: toque no botão do jogo, aperte o botão do seu aparelho, gravado. Por controle conectado, com deadzone ajustável e "aprender pressionando". É a primeira vez que isso existe em um projeto deste tipo.
- **🔌 Driver GPU por jogo** — instale drivers Turnip (`.zip`/`.adpkg`) e escolha qual cada jogo usa; cache de pipelines por driver para a segunda partida começar quente.
- **🔒 Tela sempre ativa** — o jogo nunca bloqueia a tela do portátil no meio de uma sessão.
- **📜 Log por execução** — cada partida gera um log nomeado; o seletor de problemas de versão de build fica no próprio app.
- **🧩 Perfis por jogo** — constantes e correções específicas de cada título vivem em `dm/games/<title-id>/`, não no código.

## Jogos

| Jogo | Title ID | Situação no NativeAYN |
|---|---|---|
| **The Legend of Zelda: Ocarina of Time** (Switch 2) | `0400486024DEC000` | **Jogável (experimental)** — principal alvo de desenvolvimento; medido em AYN Odin 3 (Adreno 830, Android 15) |
| Donkey Kong Bananza | `040074A01BF12000` | Perfil presente; não revalidado nesta variante |
| Mario Kart World | `0400C3F00006E000` | Perfil presente; não revalidado nesta variante |

O APK é montado por jogo: cada build carrega o perfil dos títulos selecionados (veja [Compilar](#compilar)).

## Instalar

1. Baixe o APK em [Releases](https://github.com/renelima/NativeAYN/releases) (quando publicado) e instale no aparelho (Android 13+).
2. Abra o app, conceda o acesso a arquivos e toque em **Add game**, apontando para a pasta do **seu dump** (a que tem `base/` e `updates/`) ou para o arquivo `.nup` do seu jogo.
3. Em aparelhos Adreno, instale um driver Turnip em *Settings › GPU driver* e selecione-o no jogo.

## Compilar

Ambiente: WSL2 (ou Linux) com Android SDK/NDK 28 e JDK 17. Do diretório `android/app`:

```bash
gradle assembleDebug \
  -PnxGame=0400486024DEC000 -PnxGames=0400486024DEC000 \
  -PnxAppId=dev.nativeayn.android -PnxPackEngine=fake
```

O APK resultante (`build/outputs/apk/debug/`) embute o host do jogo escolhido. Testes do hospedeiro C: `make -C dm/host/tests` (inclui os testes de mapeamento de controle).

## Contribuindo

Relatos de compatibilidade vão em [Discussions](https://github.com/renelima/NativeAYN/discussions); bugs reproduzíveis, em [Issues](https://github.com/renelima/NativeAYN/issues). PRs são bem-vindos — abra uma issue antes para mudanças grandes. Docs técnicas do hospedeiro estão em `docs/` e `AGENTS.md` (inglês).

## Aviso legal

Use **apenas dumps próprios e legalmente adquiridos** dos seus jogos. Este repositório e o app não contêm nem distribuem código de jogos, dados, chaves, firmwares ou bibliotecas de shaders. O NativeAYN não tem afiliação com Nintendo ou com qualquer publicadora; problemas devem ser reportados aqui, nunca aos detentores dos jogos.
