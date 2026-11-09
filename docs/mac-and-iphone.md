# Os builds para Mac e iPhone

Este repositório também guarda os builds para Mac, iPhone e iPad do host de que o projeto partiu (MIT). Eles não fazem parte do NativeAYN e não foram alterados nem testados para ele; o README original deles segue abaixo, inalterado exceto pelos links e sem a sua galeria de screenshots (screenshots de jogos não ficam neste repositório). Créditos: [NOTICE.md](../NOTICE.md).

## nxnative

Jogue seus próprios jogos de Nintendo Switch nativamente no iPhone e no iPad. Traga seu jogo (seus arquivos NSP) e seus `prod.keys`; um script só os transforma em um IPA que você instala por sideloading.

Não é um emulador: o código do próprio jogo roda direto no chip da Apple, com os gráficos do Switch reconstruídos em Metal. Nada de nenhum jogo está neste repositório, e o projeto não tem afiliação com a Nintendo nem com nenhuma publicadora de jogos.

Os jogos de Switch 2 também começaram: [Donkey Kong Bananza](../dm/games/040074A01BF12000/README.md) roda no Mac, feito a partir de um dump do jogo; iPhone e iPad não foram testados.

## Agradecimentos

Este projeto é construído sobre o trabalho de outras pessoas ([licenças](../dm/third_party/README.md)):

- **[d2r-mobile](https://github.com/jhaizhou-ops/d2r-mobile)** (MIT) desenvolveu essa abordagem de execução nativa, para Diablo II: Resurrected. O nxnative começou como uma cópia de suas partes genéricas: o rewriter de NSO, o host que faz as vezes do Horizon, a camada NVN-sobre-Metal e o shell do app iOS. As correções fluem nos dois sentidos.
- **Ryujinx** (MIT): seu tradutor de shaders (`Ryujinx.Graphics.Shader`, `Ryujinx.Common` e `Spv.Generator` 1.3.2, em `dm/third_party/ryujinx` com algumas correções listadas em [seu README](../dm/third_party/ryujinx/README.md)) converte os shaders dos jogos em SPIR-V; seu suporte a tasks / mesh de Switch 2 vem depois do [switch2mac](https://github.com/azmasm/switch2mac) (azmasm), com um arquivo GPL-3.0-or-later do [Eglantine](https://github.com/djihads80/eglantine) (Eden Emulator Project), o que torna o tradutor compilado GPL-3.0-or-later como um todo ([seu README](../dm/third_party/ryujinx/README.md)). Sua emulação do kernel e dos serviços de sistema também serve de referência quando o comportamento do host está em dúvida.
- **[Atmosphère](https://github.com/Atmosphere-NX/Atmosphere)**: seu kernel, o mesosphère, é a referência para a semântica de sincronização do Horizon (address arbiter, condition variables).
- **[LibHac](https://github.com/Thealexbarney/LibHac)**: lê NSPs e sobrepõe updates ao jogo base em `dm/tools/nx_extract`.
- **[SPIRV-Cross](https://github.com/KhronosGroup/SPIRV-Cross)**: SPIR-V para Metal Shading Language.
- **[glslang](https://github.com/KhronosGroup/glslang)** compila o GLSL dos jogos de OpenGL para SPIR-V para ele.
- **[MacNativeGames](https://github.com/ly05010419/MacNativeGames)** (MIT): o alargamento de GPU de índices de 8 bits e clears in-pass em `dm/gfx/nvn/nvn_mtl.m`, e o lowering de contadores de visibilidade no tradutor de shaders.

## Como jogar

Jogos de Switch 2 são compilados a partir de uma pasta com seus arquivos, não de NSPs, e testados só no Mac: veja [Jogos de Switch 2](#jogos-de-switch-2).

### Com um app para Mac

Projetos da comunidade constroem sobre o nxnative com apps para Mac que transformam seus jogos em apps para seu iPhone, iPad ou Mac, sem linha de comando, e os guardam em uma biblioteca. São projetos separados: dúvidas e bugs sobre eles vão para suas próprias páginas.

- [NX2Apple](https://github.com/LNPANDY/NX2Apple)
- [NX Play](https://github.com/varyform/nx-native-wrapper)
- [NxNative Launcher](https://github.com/rennan101/nxnative_Launcher)
- [LumaNX](https://github.com/Xc1Ym/LumaNX)

### Compilar pela linha de comando

Você precisa de um Mac com macOS 26.6 ou mais recente, [Xcode](https://developer.apple.com/download/all/?q=Xcode%2027.2) 27.2, [Homebrew](https://brew.sh) e Python 3.12 ou mais novo (um arm64, não o de um Homebrew Intel). Um build para iPhone também precisa da plataforma iOS do Xcode (Xcode → Settings → Components).

Os shaders são compilados com o compilador Metal do Xcode 27.2. Selecione-o antes do passo 1: `sudo xcode-select -s /Applications/Xcode.app`. Com várias versões do Xcode instaladas, nomeie esta como `Xcode-27.2.app` e selecione-a com `sudo xcode-select -s /Applications/Xcode-27.2.app`.

**1. Instale as ferramentas** (uma só vez), no Terminal:

```
xcodebuild -downloadComponent MetalToolchain
git clone https://github.com/seedds/nxnative.git && cd nxnative
brew install xcodegen zstd glslang
brew install --cask dotnet-sdk
python3 -m pip install lz4 "capstone>=5,<6" fonttools nsz zbic "backports.zstd; python_version < '3.14'"
```

**2. Coloque seu jogo em uma pasta**: o jogo, mais sua atualização e DLC se você os tiver (`.nsp`, `.xci`, `.nsz` ou `.xcz`):

```
~/Games/MarioWonder/
  Super Mario Bros. Wonder [010015100B514000][v0].nsp
  Super Mario Bros. Wonder [010015100B514800][v327680].nsp
```

**3. Compile**, com a pasta do seu jogo e seus `prod.keys`:

```
python3 scripts/build_ipa_from_nsp.py ~/Games/MarioWonder -k ~/keys/prod.keys
```

O IPA aparece na pasta do jogo. O primeiro build leva 30–60 minutos e precisa de espaço livre em disco de cerca de duas vezes o tamanho do jogo mais 10 GB; compilar de novo é muito mais rápido.

Para jogar no Mac, em vez disso, adicione `--platform mac-app`: você ganha um app do jogo para Mac para abrir pelo Finder. Todas as opções: [O script de packing em detalhe](../docs/development.md#the-packing-script-in-detail).

### Instalação

Instale o IPA no seu iPhone ou iPad com uma ferramenta de sideloading como AltStore, Sideloadly ou LiveContainer. Se sua ferramenta oferecer **Increased Memory Limit**, ative: os jogos maiores precisam disso.

Jogo grande demais para instalar? Compile de novo com `--romfs separate` e execute uma vez o comando que o script imprime para copiar os arquivos do jogo para o seu telefone ([Reinstalações rápidas](../docs/development.md#quick-reinstalls)).

Controles, saves e configurações: [Jogando](../docs/playing.md).

### Jogos de Switch 2

Um jogo de Switch 2 é compilado a partir de uma pasta com seus arquivos, não de NSPs, e não precisa de `prod.keys`. O script espera esta pasta (`updates` e `dlc` são opcionais):

```
~/Games/DonkeyKongBananza/
  base/exefs/                     main, main.npdm, sdk, rtld
  base/romfs/
  updates/458752/exefs/           the update's version, as a decimal number
  updates/458752/romfs/           the patched RomFS, not a patch
  dlc/040074a01bf13001/0/romfs/   the DLC's title ID
```

```
python3 scripts/build_ipa_from_nsp.py ~/Games/DonkeyKongBananza --platform mac-app
```

Você ganha um app para Mac, sem ícone porque a pasta não tem nenhum. iPhone e iPad não foram testados.

## Jogos

Jogos testados no Mac ou no iPhone. Veja as [notas por jogo](../dm/games/README.md#jogos) para plataformas testadas, progresso e problemas conhecidos:

Requer iOS / iPadOS 26 ou mais recente em um iPhone 15 Pro ou mais novo (chip A17 Pro ou posterior) ou um iPad com chip M1 ou posterior: as texturas dos jogos são comprimidas em BC, e iPhones antigos não leem esse formato. Os outros jogos ainda não são suportados. Para eles o script escreve um survey e ainda tenta ao máximo fazer o pack de um IPA, deduzindo as configurações do jogo; um IPA assim pode não iniciar.


### Para desenvolvedores

[Como funciona](../docs/development.md) · [Notas por jogo](../dm/games/README.md#jogos) · [Bring-up e depuração](../docs/agents/README.md) · [Frontends](../docs/frontends.md) · [Contribuindo](../CONTRIBUTING.md)

### Licença

MIT ([LICENSE](../LICENSE)). O código de terceiros mantém a própria licença ([dm/third_party](../dm/third_party/README.md)); duas partes são GPL-3.0-or-later, e também é o que é construído com elas: o tradutor de shaders ([dm/third_party/ryujinx](../dm/third_party/ryujinx/README.md)) e a geração de frames do Android ([android/NOTICE.md](../android/NOTICE.md)).
