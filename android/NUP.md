# Suporte a NUP (branch android-nup)

Jogar e preparar um jogo direto de um arquivo `.nup` (github.com/a55uka/nup, SPEC.md no commit 92089de3, formato 1.0-draft),
sem desempacotar. O `.nup` é só lido: é o dump do usuário e nunca é modificado. O código reescrito entra no pack
como entra no de uma pasta.

## O formato em um parágrafo

Superbloco (0x80) em 0, depois o TOC (pacotes, diretórios, arquivos, filhos, pool de strings; SHA-256 no superbloco), depois a
região de blobs. Um pacote é `base` (tipo 0), `update` (1, um por versão) ou `addon` (2, title ID próprio); cada um tem um
`exefs_dir` e um `romfs_dir` (0xFFFFFFFF = nenhum). Cada arquivo é um extent, alinhado a 16 bytes, codec 0 (raw) ou 1 (um frame zstd
para o arquivo inteiro, tamanho do conteúdo no cabeçalho do frame). Arquivos iguais podem compartilhar um extent. Um pacote de update guarda um
RomFS completo (como `updates/<version>/romfs` faz num dump de pasta): não precisa de camadas.

## Nomeando um pacote: `<file>.nup:<selector>`

Em todo lugar que um caminho para um RomFS (ou um ExeFS) é dado, um pacote `.nup` é nomeado como o caminho do arquivo, dois pontos, e:

| seletor | pacote |
|---|---|
| `base` | o pacote base |
| `update/<decimal version>` | o update dessa versão NCM, por exemplo `update/458752` |
| `addon/<16 hex digits>` | o add-on desse title ID (minúsculas), por exemplo `addon/040074a01bf13001` |

O caminho é dividido no último `.nup:`. Sem um seletor (`game.nup`), o RomFS é o do update de versão mais alta, ou o
do base quando não há nenhum. Um seletor que não nomeia nenhum pacote é um erro.

Um pacote ocupa o lugar onde uma pasta de pacote de um dump de pasta estaria (`base/`, `updates/<v>/`, `dlc/<tid>/<n>/`), e um caminho dentro
dele pode seguir o seletor, como dentro daquela pasta: `game.nup:update/458752/romfs`, `game.nup:update/458752/exefs/main`,
`game.nup:addon/040074a01bf13001/romfs`. A forma do próprio seletor é fixa (`base`, `update/<digits>`, `addon/<16 hex>`), então
o resto depois dele não é ambíguo. As ferramentas aceitam isso em todo lugar onde aceitam o caminho de pasta correspondente. O `DM_ROMFS` do host aceita
`<file>.nup:<selector>/romfs` (também `<file>.nup:<selector>` e `<file>.nup` puro, ambos significando o RomFS daquele pacote), e o
`pack.tsv` escreve a forma completa `/romfs`.

## Host (dm/host/hle_fs.c, dm/host/nup_image.h)

- O `DM_ROMFS` pode ser `<file>.nup` ou `<file>.nup:<selector>`. A montagem lê o TOC, o confere, e mapeia a subárvore
  `romfs_dir` do pacote escolhido sobre o mesmo índice em memória que uma imagem de RomFS (DMROMFS1) usa, então buscas, listagens e
  tamanhos são código compartilhado.
- Leituras: um arquivo raw é lido com pread no seu extent. Um arquivo zstd é descomprimido inteiro na memória quando o jogo o
  abre, segurado até ser fechado, depois mantido num cache least-recently-used de `DM_NUP_CACHE_MB` (padrão 128). Sem cache em
  disco: em Donkey Kong Bananza os arquivos comprimidos são 5,6 GiB que o zstd reduz em só 13 %, então um cache em disco devolveria
  o que a compressão economizou; o maior tem 53 MiB e só 8 passam de 16 MiB (um descomprime em dezenas de ms).
- zstd: o `DM_NUP_ZSTD` (android/host/CMakeLists.txt, os testes de unidade) vincula dm/third_party/zstd. Os hosts de Mac e iOS são
  compilados sem ele: arquivos raw são lidos, um arquivo comprimido falha ao abrir com uma linha vermelha.
- Testes: `make -C dm/host/tests GAME=<id> run` (t_fs "NUP container": um .nup escrito em C a partir da spec). Contra um
  dump real: `build/t_nup_dump <file>.nup:<selector>/romfs <that package's RomFS folder>` lê cada arquivo através do hle_fs.c e
  o compara (DKB update 458752: 77.342 arquivos, 8,55 GiB, todos iguais; base e DLC também).
- `DM_AOC_DIR/<index>.nup` (um arquivo ou um link simbólico para o `.nup`): o conteúdo de add-on `<index>` é o pacote addon cujo
  title ID tem os 12 bits baixos iguais a `<index>` (o índice do pack_android.py é `tid - app - 0x1000`, o mesmo número). Conferido
  antes de `<index>.img` e `<index>/`.

## Ferramentas (Python: dm/tools/nup.py e seus usuários)

- `dm/tools/nup.py`: leitor. Confere um `.nup` conforme SPEC §6.1 (TOC SHA-256 antes de qualquer valor do TOC ser usado, limites, sem
  extents sobrepostos mas com compartilhados, diretórios acíclicos ordenados; codec, tipo e flags desconhecidos recusados), lista pacotes, resolve seletores,
  e dá walk / list / stat / read e streams seekable. zstd: em memória até 64 MiB, em stream acima disso, um arquivo temporário em
  `$DM_TEMP` só quando um chamador precisa de um descritor para mmap. CLI: `packages`, `ls`, `cat`, `verify`.
- O `dm/tools/romfs.py` (`Path`, `open`, `os`, `mmap` para caminhos dentro de imagens) é a camada de acesso: um caminho com `.nup:` vai para
  uma view NUP, então `shader_split.py`, `romfs_image.py` e `pack_dump.py` (scan, pack_version, pack_dlc) aceitam caminhos `.nup` sem
  código próprio. `<file>.nup:` com um seletor vazio é a raiz do contêiner (pastas `base`, `update/<v>`,
  `addon/<tid>`); um caminho que nomeia um pacote ausente não é encontrado.
- Módulo zstd: `compression.zstd` (Python 3.14: nativeayn-env no Mac, o CPython 3.14.6 do celular), senão
  `backports.zstd`; importado no primeiro arquivo comprimido, então um `.nup` só raw não precisa de nenhum.
- O reescritor lê arquivos simples: o `pack_android.py` copia um módulo NUP para o `.nup-src/<module>/` do pack para o
  `patch_nso.py` e o apaga depois (`rewriter_src`). O `nso.py` e os outros arquivos `REWRITER` ficam inalterados, então a rewrite
  key é a mesma para uma pasta e para um `.nup` dela, e os packs feitos antes deste branch mantêm seus módulos.
- O `pack.tsv` escrito para um jogo `.nup`: `romfs` = `<name>.nup:update/<version>/romfs` (ou `:base/romfs`), e o caminho de cada linha
  `aoc` = `<name>.nup:addon/<tid>/romfs`, relativo à pasta que guarda o `.nup`. O pack vai para
  `<title ID>-work/android-pack` ao lado do `.nup`, como ao lado de uma pasta de jogo.
- Um `.nup` não tem arquivos `.listing` (SPEC §6.3), então a sua contagem de arquivos difere a de um dump de pasta (DKB update: 77.342 contra
  80.273); a saída do shader split é a mesma.
- `dm/tools/nup_pack.py`: um pequeno gravador clean-room, para fixtures de teste (um dump de pasta → `.nup`), não para usuários.
- Testes: `python -m unittest dm/tools/tests/test_nup.py` (nativeayn-env). No DKB, o `pack_android.py` a partir da pasta e a partir
  do `.nup` (`--in-process`, o caminho do celular) dão módulos, tabelas e chaves byte-idênticos; o `shader_split.py` dá
  o mesmo pacote e as mesmas tabelas (22.852 shaders).

## App (android/app)

- A extensão é em minúsculas: ferramentas e host dividem em `.nup:`; o app recusa `.NUP` com "renomeie para .nup".
- Adicionar jogo pergunta "uma pasta" ou "um arquivo .nup" (seletor de arquivos do sistema, convertido no seu caminho /storage; entradas
  do Recent e do MediaStore não têm nenhum e são recusadas com uma frase). Uma pasta escolhida que contém arquivos `.nup` (ou subpastas de dump) adiciona
  todos eles (`PackFind.gamesIn`; um title sem profile neste APK é nomeado no resumo). adb: `--es game <file.nup>`, ou
  `--es add_game <file.nup or folder>` para o caminho próprio do Adicionar jogo. O pack de um `.nup` é procurado ao lado dele e em `<title ID>-work/<any name>`
  da pasta dele e até três pastas acima (`PackFind`).
- O `Nup.java` lê o superbloco e o TOC (SHA-256 conferido, em cache por caminho, tamanho e mtime), o `Zstd.java` é um decodificador
  de frame zstd em Java puro (RFC 8878, sem dicionários) para main.npdm, build IDs de NSO e a checagem da rewrite key: o processo do
  launcher não carrega código nativo.
- GameActivity: `DM_ROMFS` = o `.nup` do próprio jogo + o seletor do pack.tsv + `/romfs` (só o seletor é pego, então um
  `.nup` renomeado ainda joga; um pack feito de uma pasta recorre a `update/<pack version>` ou `base`). `DM_AOC_DIR/<index>.nup`
  aponta para o `.nup` do próprio jogo quando ele guarda aquele add-on, senão para o `.nup` que a linha do aoc nomeia, senão para o do próprio jogo.
- Preparar no celular (PackService) passa `<N>` (o `.nup`) para o pack_android.py e `<N>:update/<v>/romfs`,
  `<N>:update/<v>/exefs/main` (`<N>:base/…` para a versão 0) para o shader_split.py. Ele aceita qualquer um de três layouts (`DumpLayout`): o base sozinho com seu ExeFS e RomFS (um jogo cujo build é o seu base), um update sozinho que carrega o jogo inteiro, ou ambos; o ExeFS e o RomFS de uma versão são os dela, senão os do base, e a versão é a cujos módulos são um build do profile, achada pelo build ID onde quer que esteja. Um `.nup` de outro build é recusado com o que ele guarda e o build que este app suporta. O DumpCheck confere o hash do TOC,
  a main / main.npdm / RomFS da versão, o build ID de cada módulo e as linhas `romfs_check` do profile (`RomfsCheck`: o RomFS são os dados daquela versão).
- Prontidão: uma linha "Arquivos do jogo" por jogo (um `.nup` tem que abrir e passar no hash do TOC).
- Testes: `NupJvmTest` (javac + java, sem runner do Gradle ainda; veja o cabeçalho dele): 293 checagens num `.nup` sintético, 30 no
  DKB real.

## Dados de teste

Testado com um dump de `040074A01BF12000` (base, update 458752, add-on `040074a01bf13001`) como um `.nup` oficial e como uma pasta,
e um dump de `0400C3F00006E000` 1.8.0 (só base) como um `.nup` escrito pelo `dm/tools/nup_pack.py` (2026-11-05, no celular: ambos
chegam ao seu nível a partir do `.nup` sem Preparar). Pastas de dump e arquivos `.nup` de teste pertencem ao drive de dump, não ao
repositório; um `.nup` é uma cópia de tamanho completo de um dump, então cuidado com o espaço livre antes de gravar um.
