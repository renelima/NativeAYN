# NativeAYN: créditos e licenças

O NativeAYN é construído sobre o trabalho de outras pessoas. Este arquivo é onde elas recebem crédito; seus copyrights e licenças são mantidos.
O NativeAYN é baseado no projeto Anka Native, de renelima.

## Os projetos nos quais se baseia

- **[nxnative](https://github.com/seedds/nxnative)**, de seedds (MIT). O host que executa nativamente o próprio código ARM64 de um jogo do Switch, o
  rewriter de NSO, os profiles de jogo, as ferramentas de shader e os builds de Mac / iPhone vêm todos dele. Sua licença MIT e sua linha de copyright estão
  mantidas em [LICENSE](LICENSE). Correções upstream posteriores portadas para o NativeAYN citam, no código e nos docs, o commit do nxnative de onde vêm
  (por exemplo, o frame step de Ocarina of Time, `7d8dcdf8`, e o record step de 8 bytes do `shader_split.py`, `b1d7c707`).
- **[Banana Native](https://github.com/moceure/BananaNative)**, de moceure (MIT, com as partes GPL citadas abaixo), incluindo o trabalho no fork Raphix91:
  o port para Android do nxnative que o NativeAYN dá continuidade: o launcher de Android (`android/app`), o replay Vulkan de NVN (`dm/gfx/vk_android`), o
  packer no celular, a geração de frames no Android, o carregador de driver de GPU customizado e os trabalhos de Donkey Kong Bananza e Mario Kart World no
  Android. Seu próprio aviso é mantido em [android/NOTICE.md](android/NOTICE.md).
- **[d2r-mobile](https://github.com/jhaizhou-ops/d2r-mobile)** (MIT): a abordagem de execução nativa da qual o nxnative partiu.

## Código e descobertas de outros projetos

- **Ryujinx** (MIT): o tradutor de shaders (`Ryujinx.Graphics.Shader`, `Ryujinx.Common`, `Spv.Generator` 1.3.2) em `dm/third_party/ryujinx`, com
  alterações locais listadas no seu README.
- **[Eglantine](https://github.com/djihads80/eglantine)** via **[switch2mac](https://github.com/azmasm/switch2mac)**, de azmasm: o lowering de task /
  mesh shader do Switch 2 (`InstEmitAmpereMesh.cs`, GPL-3.0-or-later); os entry points de mesh em SPIR-V do switch2mac (MIT fora de `nx2_lower`).
- **[kartnative](https://github.com/pahorema/kartnative)**, de pahorema (MIT): descobertas sobre texturas esparsas escritas pela GPU e o header por
  primitiva dos programas de mesh (seguidas sem copiar código).
- **[Eden](https://git.eden-emu.dev/eden-emu/eden)** e **[lsfg-vk](https://github.com/PancakeTAS/lsfg-vk)**: geração de frames do Lossless Scaling,
  portada para C em `dm/gfx/vk_android/lsfg` (GPL-3.0-or-later). O próprio `Lossless.dll` pertence ao Lossless Scaling, é vendido separadamente e nunca é
  incluído.
- **[libadrenotools](https://github.com/bylaws/libadrenotools)** (com liblinkernsbypass), como mantido no Eden: carregamento de um driver de GPU Adreno
  customizado.
- **O driver Vulkan Turnip da Mesa** (MIT): o driver Adreno aberto no qual o celular roda os jogos. Ele não está neste repositório nem no APK; o usuário
  instala um build de driver, como o build Anka Turnip ou os builds da comunidade que a lista Download... do launcher mostra (cada um mantém sua própria
  licença; os binários do driver da própria Qualcomm são proprietários).
- **Lichii** (emulador para Android): descobertas de Ocarina of Time. O cut de 4 bytes do jogo em `main+0x805bd0` usa o mesmo offset e os mesmos bytes do
  patch embutido do Lichii, e a divisão de frame rate que o frame step substitui foi encontrada lá primeiro (como o nxnative credita). Apenas ideias: nenhum
  código do Lichii está neste repositório.
- **[Atmosphere](https://github.com/Atmosphere-NX/Atmosphere)** (a referência para a sincronização do Horizon), **[LibHac](https://github.com/Thealexbarney/LibHac)**,
  **[SPIRV-Cross](https://github.com/KhronosGroup/SPIRV-Cross)**, **[glslang](https://github.com/KhronosGroup/glslang)**,
  **[MacNativeGames](https://github.com/ly05010419/MacNativeGames)** (MIT) e deko3d (zlib): usados pelos builds de Mac e iPhone e pelas ferramentas
  compartilhadas, como listado em [docs/mac-and-iphone.md](docs/mac-and-iphone.md#agradecimentos).
- Código de terceiros incluído em `dm/third_party` (AMD FidelityFX FSR 1, NVIDIA FLIP, libadrenotools, libvpx, stb_vorbis, Ryujinx, Vulkan-Headers, zstd e
  as partes adaptadas do MacNativeGames): cada um mantém seu arquivo de licença em seu próprio diretório, listado em
  [dm/third_party/README.md](dm/third_party/README.md).
- Ferramentas precompiladas commitadas para a shader chain de Mac: `dm/gfx/tools/spirv_cross_bin/spirv-cross` (SPIRV-Cross, Apache-2.0, textos de licença
  ao lado dele) e o tradutor `dm/gfx/tools/dmsh_xlate_bin` (um trabalho combinado GPL-3.0-or-later, veja acima). A pasta do tradutor também traz os
  pacotes .NET com os quais foi compilado, inalterados vindos do NuGet: Gommon 2.8.1.2 (MIT), Humanizer 3.0.10 com seus recursos de idioma (MIT),
  MsgPack.Cli 1.0.1 (Apache-2.0), Microsoft.IO.RecyclableMemoryStream 3.0.1 (MIT) e System.CodeDom 4.4.0 (MIT, .NET Foundation); a licença de cada pacote
  está no seu pacote NuGet. Ao lado, `dmsh_xlate-<linux|win>-<x64|arm64>` são hosts de aplicação do .NET (MIT) que iniciam o mesmo tradutor em Linux e
  Windows.

- O **packer no celular** do APK (`android/ondevice/build.sh` o compila, inalterado a partir da release de cada projeto): **[CPython](https://www.python.org)**
  3.14.6 para Android (PSF License), **[Capstone](https://github.com/capstone-engine/capstone)** 5.0.7 (BSD-3-Clause),
  **[python-lz4](https://github.com/python-lz4/python-lz4)** 4.4.5 com LZ4 (BSD-3-Clause, BSD-2-Clause),
  **[zbic](https://github.com/kinnay/zbic)** 1.0.1.post1, de Yannik Marchand (GPLv2; ele traz o Zstandard, BSD-3-Clause ou GPLv2), para o código
  comprimido em zstd dos jogos mais recentes, **[SPIRV-Tools](https://github.com/KhronosGroup/SPIRV-Tools)** (Apache-2.0) e o runtime do
  **[.NET](https://github.com/dotnet/runtime)** com o qual o tradutor é compilado (NativeAOT, MIT).

## Licenças

- O código próprio do repositório é MIT ([LICENSE](LICENSE): o copyright do seedds e o do renelima), exceto os arquivos marcados GPL-3.0-or-later: o
  port da geração de frames (`dm/gfx/vk_android/lsfg`), o `InstEmitAmpereMesh.cs` do tradutor de shaders e o script da marca Anka
  (`android/tools/anka_logo.py`).
- **O app Android é GPL-3.0-or-later como um todo.** O APK vincula o port da geração de frames (`dm/gfx/vk_android/lsfg`, GPL-3.0-or-later), a menos que
  seja compilado com `-PnxLsfg=false`, e um APK que traz o packer no celular também traz o tradutor de shaders GPL. Quem repassar um APK assim deve
  oferecer seu código-fonte completo: este repositório no commit indicado pela tag de build do app. Detalhes: [android/NOTICE.md](android/NOTICE.md).
- A marca do NativeAYN (o "N sobre um infinito" em laranja e azul) é um desenho próprio de renelima, usada no app, no launcher e no repositório. Seu script
  `android/tools/anka_logo.py` é Copyright 2026 renelima, GPL-3.0-or-later (o texto da licença: [dm/gfx/vk_android/lsfg/COPYING](dm/gfx/vk_android/lsfg/COPYING)).
  Os ícones que ele gera (`android/images/logo.png` e os arquivos `anka_*.png` e `ic_launcher*.png` em `android/app/src/main/res`) são arte de renelima e
  também são licenciados GPL-3.0-or-later. O `android/tools/anka_icons.py`, que os escreve a partir do script, é MIT.
- Os profiles de jogo (`dm/games/<title ID>`) mantidos neste repositório são Ocarina of Time (`0400486024DEC000`), Donkey Kong Bananza
  (`040074A01BF12000`) e Mario Kart World (`0400C3F00006E000`); os profiles do nxnative para outros jogos não foram trazidos.
- Nada de nenhum jogo está neste repositório ou no app: nenhum código de jogo, nenhum dado de jogo, nenhuma chave, nenhum firmware e nenhuma biblioteca
  de shaders feita a partir de shaders de jogo. Os profiles de jogo guardam apenas identificadores, hashes, offsets e pequenas palavras de instrução
  usadas para conferir o lugar de um patch.

Sem afiliação com a Nintendo, nenhuma publicadora de jogos ou qualquer dos projetos acima.
