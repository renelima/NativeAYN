# dm/gfx — camada gráfica (NVN → Metal)

A versão de Switch do jogo desenha com NVN (a interface gráfica nativa do Switch). A camada gráfica reimplementa essa interface sobre o Metal da Apple: toda chamada NVN que o jogo faz chega aqui, é registrada como um comando e é reproduzida como comandos Metal no momento da submissão. Os shaders do jogo não são traduzidos em tempo de execução; eles são traduzidos offline, uma vez, num computador, para bibliotecas de shader Metal (quando o computador não tem o compilador Metal da Apple, o passo final de compilação se muda para o celular; ver abaixo).

Para as etapas de build do projeto inteiro veja [README.md](../../README.md) e [docs/development.md](../../docs/development.md) (as ferramentas de que precisam: Xcode, xcodegen, .NET SDK, zstd, e os pacotes Python lz4 / capstone / fonttools; spirv-cross é o build no repositório `tools/spirv_cross_bin`); esta página cobre apenas os assuntos próprios da camada gráfica.

## Como funciona

### Assumindo a interface NVN

- O programa principal do jogo importa apenas um símbolo NVN, `nvnBootstrapLoader`; todas as outras funções NVN são obtidas por nome através dele. O host toma esse import à força (`nvn/hle_nvn.c`), e nenhuma instrução do driver gráfico original é executada.
- O jogo busca os endereços de 523 nomes no total (`nvn/names.txt`). Cada nome tem um thunk (`nvn/nvn_thunks.S`, um arquivo gerado): ele primeiro incrementa a contagem de chamadas daquela função e depois salta para a implementação.
- As funções que os jogos chamam têm implementações, conforme a semântica do NVN, ou vazias (`g_nops` em `hle_nvn.c`); as demais estão ligadas a um stub de reporte vermelho que, quando chamado, imprime `[RED] NVN unimplemented function called: <name>` no log.
- Cada implementação passa por uma checagem de assinatura em tempo de compilação contra `nvn/nvn_protos.h` (um arquivo gerado). As convenções de chamada do arm64 da Apple e do Switch diferem para parâmetros inteiros mais estreitos que 32 bits, então no lado do host os parâmetros `NVNboolean` são sempre recebidos como `uint32_t` e só os 8 bits baixos são examinados.

### Duas camadas: gravação e replay

- **Camada de interface `nvn/hle_nvn.c`**: os objetos do lado do host ficam armazenados no próprio lugar da memória que o jogo aloca para os objetos NVN; os layouts estão em `nvn/nvn_obj.h`, e cada um tem uma asserção estática garantindo que não excede o tamanho reservado do NVN. Os comandos são gravados na memória de comandos fornecida pelo jogo, no formato de registro de `nvn_obj.h`.
- **Camada de replay `nvn/nvn_mtl.m`**: na submissão, reproduz o fluxo de registros como comandos Metal.
  - Os memory pools são criados como `MTLBuffer`s zero-copy, com o endereço de GPU igual ao endereço de CPU, por isso só dispositivos de memória unificada são suportados.
  - Os efeitos visíveis de "GPU completion" (sync objects ficando prontos, eventos escrevendo seu valor, contadores) acontecem só depois que o command buffer do Metal realmente terminou.
  - Os pipeline states e depth-stencil states são cacheados por hash de estado; na inicialização, shaders e pipelines são pré-construídos conforme as manifestos de warm-up (a semente incluída `pso_manifest.tsv`, depois a `pso_user.tsv` do dispositivo; ver abaixo): com janela, em todos os núcleos antes do primeiro quadro do jogo atrás de uma tela "Preparing shaders" (docs/development.md, "The prepare step"), senão em segundo plano. Um pipeline que falha ao ser construído é cacheado como falho e reportado uma vez com suas chaves de shader.
  - Os render pipelines são feitos pelo `MTL4Compiler` a partir de uma base cujo estado de cor fica não especializado, uma base por pipeline menos seu estado de cor, especializada por estado de cor; as bases ficam no arquivo de pipelines por jogo (`pso_archive/` ao lado de `pso_user.tsv`) e são retomadas dele na próxima inicialização (`pso_build`; ver docs/development.md, "Pipeline bases and the pipeline archive"). Um desenho cujo pipeline ainda não está construído desenha com o pipeline de pull do par de shaders dele (sem vertex descriptor) enquanto o seu próprio é construído em segundo plano (docs/development.md, "Hybrid pipelines").
  - As texturas empacotadas do jogo estão em layout block-linear de Tegra e são deswizzled antes do upload (veja `tex/README.md`).
  - Apresentação: a textura de swapchain do jogo é desenhada para um `CAMetalLayer`, e o ritmo da apresentação é agendado pelo pacer em `nvn/nvn_pace.h`.

Para saber em que objeto Metal cada objeto e comando NVN cai, veja [nvn_metal_mapping.md](nvn_metal_mapping.md).

### Cadeia de tradução de shaders

Os shaders do jogo são código de máquina de GPU para o Tegra X1 (o conjunto de instruções Maxwell). A tradução desse código de máquina para Metal Shading Language não é feita no celular; tudo acontece offline:

```
Pacote de shader original de Switch
  → tools/dmsh_xlate        produz SPIR-V com a biblioteca de tradução de shaders do Ryujinx (uma ferramenta em C#)
  → spirv-cross             converte SPIR-V para Metal Shading Language (MSL); o build no repositório tools/spirv_cross_bin
  → xcrun metal / metallib  compila para bibliotecas de shader Metal
```

A cadeia inteira é conduzida por `tools/dmsh_build.py`; `scripts/build_ipa_from_nsp.py` a executa como parte da montagem de um IPA. Pontos-chave:

- **Chave**: cada shader é indexado pelos 16 primeiros dígitos hexadecimais do sha256 da sua control section (os primeiros 0x880 bytes, ou o comprimento próprio da seção em control +0x20 quando esse é menor, como nas seções de 0x850 bytes do SDK 10) seguido pela sua área de código (muitos shaders de Mario compartilham uma control section e diferem no código). Em tempo de execução o jogo entrega ambos a `nvnProgramSetShaders`, e a camada de replay calcula a mesma chave para achar a biblioteca.
- **Saídas do build**: `<key>_b.metallib` (a biblioteca de shader), `<key>_b.json` (reflection usada pelo replay: o tipo de cada binding, o número do slot, o índice dentro do argument buffer e assim por diante), `<key>_b.metal` (o código MSL). Alguns fragment shaders têm adicionalmente `<key>_bf.metallib`, uma versão "readable tile" usada para fusão de passes (veja o cabeçalho do arquivo `tools/dmsh_fetch.py`). O `_b_build.json` no diretório registra o sucesso ou a falha de cada chave nesta execução.
- **A ABI da biblioteca**: `nvn/dmsh_abi.h` é a definição única do que as bibliotecas e o replay devem concordar: o layout dos segmentos de driver NVN (as tabelas de handles `c2`, as tabelas de storage e uniform buffers `c0` e as palavras que o host preenche) e os tipos de binding do `<key>_b.json`. O host o inclui; `tools/dmsh_abi.py` o lê para o `dmsh_build.py` e para em qualquer linha que não consiga ler. Ambos fazem parte da versão da cadeia, e `dm/tools/tests/test_dmsh_abi.py` os protege (o `Program.cs` do tradutor mantém sua própria cópia do layout `c2`, que o teste compara).
- **`dm/games/<id>/shader_map.tsv`**: o mapa de shaders, uma linha por variante de shader. Em tempo de execução só se usa seu "chave → estágio (vertex / fragment / compute)".
- **`dm/games/<id>/shader_facts.tsv`**: a tabela de respostas para consultas da tradução. O tradutor precisa saber fatos que não podem ser lidos do código de máquina (o tipo de dimensão de uma textura, formatos de imagem, os tipos de componente de atributos de vértice e saídas de fragment, o tamanho do thread group e a contagem de bytes de memória compartilhada de um compute shader), e esta tabela dá as respostas por chave. A tabela acompanha o repositório, e a build das bibliotecas de shader só a lê; a tabela é mantida por `tools/dmsh_facts.py`, cujo cabeçalho traz os detalhes.
- **Versão da cadeia de tradução e o diretório `b-<V>/`**: a versão V é uma string de 12 dígitos hexadecimais, obtida hashando juntos os conteúdos dos arquivos-fonte envolvidos na cadeia de tradução, as opções de tradução e as impressões digitais das ferramentas (`tools/dmsh_chain.lock`); `tools/dmsh_chain.py` a calcula. As bibliotecas de shader são escritas em `<diretório de saída>/b-<V>/`, e todo json de reflection também registra V. Quando o host é compilado, ele pega V de `nvn/dmsh_chain.h`, uma versão para todo jogo, carrega só de `b-<V>/`, e confere a versão em cada json um por um; numa divergência ele reporta vermelho e não carrega. Portanto, depois de mudar qualquer arquivo da cadeia, ou mudar uma ferramenta, as bibliotecas devem ser reconstruídas e o host, recompilado. As entradas de um perfil (suas tabelas de shader e chaves `shader_*` / `nvn_*`) não fazem parte de V: elas estão na chave de cache de cada shader, então uma mudança reconstrói os shaders que afeta, e um build completo carimba o diretório com o jogo e o digest delas (`b-<V>/inputs.tsv`, `dmsh_chain.py inputs`). Um build completo parte de nenhuma saída de shader, e um parcial de nenhuma das chaves que constrói, então um diretório contém só o que seu último build produziu; ele recusa um diretório carimbado para outro jogo, por isso cada jogo precisa do seu próprio diretório de saída.
  - `dmsh_chain.py`: imprime V.
  - `tool_lock.py --check-tools`: verifica se as ferramentas locais coincidem com o arquivo de lock.
  - `tool_lock.py --update-lock`: reescreve o arquivo de lock conforme as ferramentas locais. O lock faz parte de V; essas checagens não fazem (não decidem nenhuma saída), então uma mudança nelas não altera V nem reconstrói shader algum.
  - `dmsh_chain.py --header`: reescreve `nvn/dmsh_chain.h` (commite-o junto com a mudança da cadeia; `dm/tools/tests/test_nsp_chain.py` falha num desatualizado).
  - `dmsh_chain.py --inputs`: imprime o digest das entradas do perfil atual (`DM_GAME`), como um build completo o carimba.
  - O conversor é `tools/spirv_cross_bin/spirv-cross`, construído por `tools/spirv_cross_bin/build.sh` a partir de um commit upstream fixado. `SPIRV_CROSS=` seleciona outro executável, que deve coincidir com o lock para builds NVN. Mudar o pin exige reconstruí-lo, rodar `tool_lock.py --update-lock` e `dmsh_chain.py --header`. Migrar para outro Xcode também exige atualizar a versão testada documentada.
  - `tools/own_compilers.py <argumentos do dmsh_build.py>` exige que o tradutor, o SPIRV-Cross e ambos os compiladores Metal coincidam com o lock. Outro compilador Metal para a build com instruções de instalação; `DM_ALLOW_OTHER_METAL=1` o permite e imprime a versão real. A issue #242 reportou corrupção com um compilador não testado, mas o papel do compilador permanece não confirmado. O override muda chaves de cache sem alterar o lock nem a versão da cadeia.
- **`dm/games/<id>/c1_full.tsv`**: a lista de índices para a seção de constantes embutida (c1) dos shaders, gerada pelo `tools/c1_guard.py` varrendo o MSL traduzido; lista as chaves de shader cujo "índice em tempo de execução não pôde ser provado limitado". Um índice conta como provado só quando aritmética de intervalos sobre a expressão inteira (cada variável seguida até sua única definição; literais, `& K`, `% K`, shifts, `min`, `max`, `clamp` e casts o limitam) o mantém não negativo e dentro do intervalo que um banco compartilhado dá àquele shader: seus dados de c1 arredondados para cima até 256 bytes mais a lacuna de zeros de 256 bytes. Todo estágio conta (`vp_`, `fp_`, `cp_` …). Sua primeira linha registra o `b-<V>` e o carimbo de entradas da biblioteca de que foi feita. Para os shaders da lista, a camada de replay aloca um buffer inteiro separado de 64 KiB para o c1; quando a lista falta ou não nomeia a versão e o carimbo da biblioteca carregada, ela reporta vermelho e trata todos os shaders assim, conservadoramente. A lista não é commiteada (o git a ignora): cada checkout a faz a partir das saídas Mac do seu próprio build (um build completo do `dmsh_build.py` o faz).
- **`dm/games/<id>/cb_extent.tsv`**: até onde em cada slot uniform (binding de cbuf) cada shader pode ler, gerado pelo `tools/cb_extent.py` a partir do MSL traduzido com a aritmética de intervalos do `c1_guard.py`: por chave de shader, `reg:bytes` para cada slot cujas leituras são todas provadas dentro dos seus primeiros bytes, e `s<reg>` para cada slot storage que o shader nunca escreve (o SPIRV-Cross tipa todo storage buffer que o Ryujinx emite como gravável, então o host conta os demais como escritos). Quando o jogo atualiza parte de um uniform block vinculado dentro de um render pass, a camada de replay copia só essa parte do binding para dar aos desenhos após a atualização os seus dados sem encerrar o pass (`nvn_mtl.m` `ubp_get`; Breath of the Wild vincula um bloco de 64 KiB e seus vertex shaders leem no máximo 544 bytes dele). Sua primeira linha registra o `b-<V>` e o carimbo de entradas, como a do `c1_full.tsv`; quando falta ou está desatualizada, os bindings são copiados inteiros (até 64 KiB). Como o `c1_full.tsv`, não é commiteado, e um build completo do `dmsh_build.py` o produz.
- **`dm/games/<id>/shader_pairs.tsv` e `pso_pairs.tsv`**: os (chave de vertex, chave de pixel) de cada variante BNSH (Unity: de cada programa) e de variante de programa agl BAHS, escritos pelo `tools/shader_split.py`. O Metal só constrói um pipeline quando toda entrada de pixel shader é uma saída de vertex shader, enquanto o Maxwell dá a um atributo não escrito um valor padrão; o `dmsh_build.py` portanto acrescenta as saídas que os parceiros de um vertex shader leem mas ele não escreve, ajustadas em (0, 0, 0, 1) (`vs_pad`). As tabelas do perfil fazem parte do seu digest de entradas e das chaves de cache por shader. Um perfil sem `pso_pairs.tsv` usa a tabela gerada ao lado do pacote de shaders.
- **Várias versões de jogo**: o `shader_split.py` aceita listas separadas por vírgula de RomFS e de `main` (o jogo base primeiro) e escreve a união; as tabelas map, facts e pairs hoje cobrem Mario v0 e 1.2.1.
- **`dm/games/<id>/pso_manifest.tsv`**: o manifesto de semente de warm-up incluído no app: shaders (`S`), render pipelines (`R`, o hex do `pso_skey` em `nvn/nvn_pso_skey.h`) e compute pipelines (`C`) a pré-construir na inicialização. A ordem das linhas é a ordem de pré-construção (primeiro uso); não a ordene. Sua companheira é a `pso_user.tsv`, que o host acrescenta ao lado do diretório de saves sempre que precisa construir algo na hora, e que é pré-construída depois da semente na próxima abertura. Ambas nomeiam shaders por chave e descrevem só as configurações do pipeline, então uma lista gravada num dispositivo funciona em qualquer outro; linhas que um build não pode usar são puladas. Linhas gravadas podem ser acrescentadas à semente abaixo do seu cabeçalho (atualmente vazio).
- **Shaders embutidos no próprio programa**: o programa principal contém adicionalmente 4 compute shaders que não estão no pacote de shaders; foram escritos à mão em MSL a partir do desmontamento e estão em `nvn/nvn_builtin.h`.

Convenções de binding seguidas na tradução (o layout do driver NVN, o mesmo em todo jogo: `tools/dmsh_build.py`, preenchido em tempo de execução pelo `nvn/nvn_mtl.m`):

- o uniform buffer r é a seção de constantes `c[3 + r]` no código de máquina;
- o handle da textura combinada r (`nvnCommandBufferBindTexture`) está em `c[2][0x20 + 8r]`, o da imagem r em `c[2][0x120 + 8r]`, o da textura separada r em `c[2][0x168 + 8r]` (o slot de textura 128 + r do host) e o do sampler separado r em `c[2][0x568 + 8r]`;
- o endereço do storage buffer r está em `c[0][base + 16r]`, com base: vertex 0x110, compute 0x310, fragment 0x510;
- a origem da janela é no canto superior esquerdo e a faixa de profundidade vai de 0 a 1, igual ao Metal, por isso nada é espelhado na tradução.

## Estrutura de diretórios

| Local | Conteúdo |
|---|---|
| `nvn/hle_nvn.c` | Camada de interface: tomada do NVN, implementações de objetos, gravação de comandos, ritmo da apresentação |
| `nvn/nvn_mtl.m`, `nvn/nvn_mtl.h` | Camada de replay: dispositivo Metal, memory pools, texturas, carregamento de shaders, pipelines, desenho, apresentação |
| `nvn/nvn_obj.h` | Layouts de objetos do lado do host e formato de registro de comando, compartilhados pelas duas camadas |
| `nvn/nvn_entry.S`, `nvn/nvn_thunks.S` | Entradas do stub não implementado e do wrapper de medição, thunks por função |
| `nvn/nvn_tex.c`, `nvn/nvn_tex.h` | Tamanho de armazenamento, alinhamento e offsets por nível de texturas block-linear (portado do deko3d) |
| `nvn/nvn_dirty.c`, `nvn/nvn_dirty.h` | Rastreia páginas sujas da memória do jogo com proteção de escrita, para decidir se o conteúdo de texturas mudou e precisa de re-upload |
| `nvn/nvn_refl.m`, `nvn/nvn_refl.h` | Converte o json de reflection do shader em arrays C de tamanho fixo, para que o desenho só percorra arrays |
| `nvn/nvn_hmap.h`, `nvn/nvn_cover.h`, `nvn/nvn_pace.h`, `nvn/nvn_pso_skey.h`, `nvn/nvn_primitive.h` | Tabela hash, prova geométrica de desenhos de cobertura total, pacer de apresentação, chave de manifesto de pipeline, triangle fans e quads indexados como triangle lists (primitive restart mantido) |
| `nvn/nvn_builtin.h` | MSL escrito à mão para os 4 compute shaders embutidos no programa principal |
| `nvn/include/nvn.h` | Declarações da interface NVN (de terceiros; para a licença veja o cabeçalho do arquivo) |
| `nvn/names.txt` | A lista de nomes da API NVN. As tabelas por jogo descritas acima ficam no perfil do jogo `dm/games/<id>/` (nomeado pelo title ID do jogo base; as ferramentas o escolhem com `DM_GAME`, o make com `GAME`; não há jogo padrão) |
| `tex/` | Mapeamento de formatos de textura, deswizzling, descompressão BC, veja [tex/README.md](tex/README.md) |
| `vk/hle_vk.m` | Vulkan sobre Metal, para jogos que renderizam com Vulkan (Hades, Super Mario Galaxy); seus shaders SPIR-V são traduzidos pelo `tools/vk_shaders.py` |
| `gl/hle_gl.m` | OpenGL via EGL sobre Metal, para jogos que renderizam com o driver GL da NVIDIA (Digimon Story Cyber Sleuth: Complete Edition; Balatro, LÖVE nos seus caminhos de core profile); seus códigos GLSL são traduzidos pelo `tools/gl_shaders.py` (glslang, depois SPIRV-Cross) |
| `metal/` | Um exemplo offscreen mínimo autônomo de Metal, não compilado no host, veja [metal/README.md](metal/README.md) |
| `tools/` | Ferramentas offline, veja abaixo |
| `tests/` | Testes de unidade |
| `hle_vi.c`, `hle_nv.c` | Camada de cola para o serviço de display e a inicialização do driver gráfico: retorna objetos estáticos do host ou não faz nada |

Os scripts em `tools/`; o uso está escrito no cabeçalho de cada arquivo:

| Script | Finalidade |
|---|---|
| `dmsh_xlate/` | A ferramenta de tradução de shaders (C#), que referencia a biblioteca de tradução em `dm/third_party/ryujinx` |
| `dmsh_build.py` | Conduz a cadeia de tradução inteira para produzir as bibliotecas de shader; quando a variável de ambiente `IOSOUT=diretório` está definida, também produz a versão iOS |
| `dmsh_chain.py`, `dmsh_chain.lock` | Versão da cadeia de tradução e impressões digitais das ferramentas |
| `dmsh_facts.py` | Lê a tabela de respostas `shader_facts.tsv` (que o `shader_split.py` escreve) |
| `dmsh_fetch.py` | Gera as versões "readable tile" de fragment shaders |
| `c1_guard.py` | Gera `c1_full.tsv` |
| `cb_extent.py` | Gera `cb_extent.tsv` |
| `dmsh_dryrun.py` | Conversão de ensaio da biblioteca inteira, medindo só "ela pode ser convertida" |
| `gen_protos.py` | Gera `nvn_protos.h` / os thunks |
| `tbdr_budget.py`, `tbdr_load_dead.py`, `tbdr_store_dead.py`, `tbdr_pass_merge.py` | Leem o log `DM_NVN_PASSDUMP` e analisam o volume de load / store dos anexos de cada render pass por quadro |
| `passgpu_top.py` | Lê a saída `DM_NVN_PASSGPU` e lista o tempo de GPU por pass |

## Compilação

A camada gráfica não tem biblioteca separada. O `dm/host/Makefile` compila `dm/gfx/*.c` e os arquivos-fonte sob `dm/gfx/nvn/` e `dm/gfx/tex/` no host `dmhost` por wildcard; o App iOS é montado pelo `dm/ios/tools/build_ipa.sh`, que também copia o map, `pso_manifest.tsv`, `c1_full.tsv` e as bibliotecas de shader `b-<V>/` para o App. `tests/` e `metal/` não vão para o host.

As bibliotecas de shader são geradas pelo `tools/dmsh_build.py` (o script do NSP o executa; para compilar à mão veja [docs/development.md](../../docs/development.md)):

- a versão Mac fica em `$DM_ROOT/build/cache/metal_shader_cache/b-<V>/`;
- a versão iOS fica em `$DM_ROOT/build/ios/dmsh_b/b-<V>/`.

Depois de mudar um arquivo da cadeia de tradução, a ordem para reconstruir manualmente é: `dmsh_chain.py --header` (uma vez, para todo jogo) → `dmsh_build.py` (um build completo também produz `c1_full.tsv` e `cb_extent.tsv`, tabelas locais, não commiteadas) → recompilar o host.

A chave de cache por shader no `dmsh_build.py` (`shader_key`) cobre as linhas `shader_*` do perfil e os pares do shader, e a chave de um vertex shader pareado também leva as chaves dos seus pixel shaders (`pair_keyed`), já que o `vs_pad` lê o MSL deles. Antes, isso só estava na versão da cadeia, então um build depois de mudar apenas isso acertava as entradas antigas por shader e as arquivava sob a nova versão; agora a versão da cadeia as deixa de fora por completo. `$DM_ROOT` tem valor padrão `~/dm-work`.

## Testes

```sh
make -C dm/gfx/tests run        # testes de unidade da camada gráfica
make -C dm/gfx/tex/tests run    # testes de unidade do submódulo de texturas; os itens com dados reais precisam de amostras de textura das versões Switch e PC, veja tex/README.md
make -C dm/gfx/metal run        # autoteste do exemplo offscreen, see metal/README.md
```

Os testes em `dm/gfx/tests` compilam o código de produção diretamente e precisam de um dispositivo Metal na máquina local; alguns deles rodam sob `MTL_DEBUG_LAYER=1` (a camada de validação do Metal). A maioria dos testes não lê o workspace; as exceções são `t_refl` e `t_rt_range_ro`, que leem o json de reflection de shaders traduzidos sob `$DM_ROOT/build/cache/metal_shader_cache`, e `test_tbdr_budget.py`, que cria um diretório temporário sob `$DM_ROOT/run/gfx`. Principais conteúdos:

| Teste | O que verifica |
|---|---|
| `t_nvn` | Camada de interface: busca endereços pelo `nvnBootstrapLoader` como o jogo faz, até chegar à apresentação |
| `t_builtin` | Os 4 compute shaders embutidos |
| `t_refl`, `t_hmap`, `t_cover`, `t_pace`, `t_pso_skey`, `t_dirty_slots` | Análise do reflection, tabela hash, prova de cobertura total, pacer, chave de manifesto de pipeline, rastreamento de páginas sujas |
| `t_pso_warm` | Normalização e deduplicação do manifesto histórico, reuso de cache no momento do desenho e pixels de GPU para passes normais e mesclados, e uma preparação por chave de shader entre tarefas de warm-up concorrentes com chaves não relacionadas deixadas livres, através do MTLDevice e do Metal 4 |
| `t_deswz_gpu` | Deswizzling na GPU |
| `t_present_crop` | Recorte da apresentação |
| `t_mip_extent` | Tamanho efetivo ao renderizar num nível de mip diferente do nível 0 |
| `t_fuse_check`, `t_reorder_chain`, `t_defer_chain`, `t_zskip`, `t_xv`, `t_ub`, `t_pfv`, `t_rt_range`, `t_rt_range_shape`, `t_rt_range_ro`, `t_rt_depth_ro` | Compilam a camada de replay `nvn_mtl.m` diretamente e verificam as várias lógicas de otimização e diagnóstico dentro da camada de replay |
| `test_tbdr_budget.py` | A análise do `tools/tbdr_budget.py` |

## Chaves de execução comuns

Todas são variáveis de ambiente. O App iOS define `DM_NVN_SHMAP` e `DM_MSL_DIR` sozinho na inicialização.

Locais de recursos:

| Chave | Efeito |
|---|---|
| `DM_NVN_SHMAP=path` | O mapa de shaders. O padrão é `../games/<id>/shader_map.tsv` relativo ao diretório do executável; `c1_full.tsv` e `pso_manifest.tsv` vêm do mesmo diretório |
| `DM_MSL_DIR=diretório` | O diretório pai das bibliotecas de shader; o host carrega de `b-<V>/` abaixo dele. Ao rodar o host num Mac, defina-o para `$DM_ROOT/build/cache/metal_shader_cache` |
| `DM_PSO_MANIFEST=path` ou `off` | Usa um manifesto de warm-up diferente, ou desliga o warm-up |
| `DM_PSO_PREPARE=0` ou `1` | `0`: o warm-up roda em segundo plano enquanto o jogo começa, como sem janela; `1`: o jogo espera por ele antes do seu primeiro quadro também sem janela (`DM_PSO_PREPARE_THREADS=<n>` trabalhadores; docs/development.md, "The prepare step") |
| `DM_PSO_RECORD=path` | Acrescenta a um arquivo os shaders e pipelines usados nesta execução, para regravar o manifesto de warm-up |
| `DM_PSO_USER=path` | Onde guardar a lista por dispositivo de shaders e pipelines construídos na hora (padrão: `pso_user.tsv` no pai de `DM_SAVE`) |
| `DM_PSO_ARCHIVE=pasta` ou `off` | Onde guardar o arquivo de pipelines por jogo das bases de pipeline (padrão: `pso_archive/` ao lado de `pso_user.tsv`), ou nenhum arquivo |
| `DM_PSO_MTL4=0` / `=1` | `0`: constrói render pipelines pelo `MTLDevice` como antes, sem bases não especializadas e sem arquivo (o padrão do Mac é ligado). `1`: liga-os no iOS, onde são desligados por padrão |
| `DM_PSO_HYBRID=0` | Um desenho cujo pipeline ainda não está construído espera por ele, em vez de desenhar com o pipeline de pull do par enquanto isso (docs/development.md, "Hybrid pipelines") |
| `DM_PSO_PREBUILD=0` ou `tool` | Nenhum prebuild das bases de pull no arquivo (no passo de prepare, ou em segundo plano sem um); `tool`: roda só o prebuild e sai (`DM_PSO_PREBUILD_DEPTH=zero`, `DM_PSO_PREBUILD_THREADS=<n>`) |
| `DM_PSO_PAIRS=path` | A segunda tabela de pares do prebuild (padrão: `pso_pairs.tsv` ao lado do mapa de shaders) |
| `DM_PSO_BUNDLE=pasta` ou `off` | O arquivo de pipelines feito offline (`dm/gfx/tools/pso_offline.py`): `<arquitetura de GPU>.mtl4a` e `prebuilt.tsv`, só leitura; quando cobre os pares de shaders o dispositivo não constrói bases de prebuild. O app iOS o define para sua pasta `pso/` quando tem uma (docs/development.md, "The pipeline archive made offline") |
| `DM_PSO_WAITLOG=1` | Registra no log cada desenho que esperou mais de 20 ms pelo seu pipeline, com o motivo de nenhum substituto tê-lo atendido |
| `DM_PSO_PULL_ALL=1` | Diagnóstico: todo desenho que tem um pipeline de pull desenha com ele |

Logging e captura:

| Chave | Efeito |
|---|---|
| `DM_NVN_LOG=1` | Registra a criação de objetos NVN |
| `DM_NVN_FRAMES=N` | Imprime a tabela de chamadas quadro a quadro pelos primeiros N quadros (padrão 3) |
| `DM_NVN_TSV=path` | Escreve a tabela de chamadas e os tempos por quadro como TSV; acrescentar `DM_NVN_TSV_CALLS=0` escreve só as linhas de tempo |
| `DM_SLOWFRAME=1` | Escreve o detalhamento de tempos no log só para os quadros que caíram |
| `DM_NVN_SHOT=frame[,frame…]:path` | Salva a textura de swapchain como imagem nos frames especificados; `%d` no caminho é trocado pelo número do quadro |
| `DM_NVN_SHOT_OUT=LxA` | Faz a captura de tela acima pegar a imagem depois do upscaling para esta resolução, em vez disso |
| `DM_NVN_TEXDUMP=frame:path` | Salva cada render target de cor usado naquele quadro como imagem |
| `DM_NVN_PASSDUMP=frame` ou `inicio-fim` | Escreve os anexos, amostras e apresentação de cada render pass desses quadros no erro padrão entrada por entrada, para análise pelos `tools/tbdr_*.py`; definir também `DM_NVN_PASSDUMP_ALL` registra todo desenho |
| `DM_NVN_PASSGPU=arquivo` | Tempo de GPU por pass (exige suporte do dispositivo a amostragem de contadores em fronteiras de estágio) |
| `DM_NVN_PROF=1` | Envolve as funções NVN implementadas num wrapper de medição e coleta o tempo que cada função leva durante a gravação |

Comparação e teste:

| Chave | Efeito |
|---|---|
| `DM_NVN_TBDR=1` | Liga as otimizações de pass da camada de replay (elisão de store / load, fusão de passes). Estão desligadas por padrão: elas inferem quais texturas um desenho lê a partir dos seus slots vinculados, o que texturas bindless derrotam ([docs/agents/04-graphics.md](../../docs/agents/04-graphics.md) seção 4) |
| `DM_NVN_UNWRITTEN_DC=0` | Com `DM_NVN_TBDR=1`, desliga o único item "anexos não escritos neste pass não são armazenados" |
| `DM_NVN_FUSE_CHECK=1` | Checagem de fusão de passes no mesmo quadro; registra o resultado da comparação todas as vezes |
| `DM_NVN_CS_HOIST=0` | Um dispatch de compute entre os desenhos de um pass o encerra (por padrão ele roda adiantado em relação ao pass quando isso não muda nada que os desenhos antes dele leram: [referência](../../docs/reference/rendering.md#compute-dispatches-between-the-draws-of-a-pass-run-ahead-of-it)) |
| `DM_NVN_SLICE_MERGE=0` | Passes em outras camadas de um nível de textura array ou cube ficam separados (por padrão rodam como um único pass em camadas: [referência](../../docs/reference/rendering.md#passes-into-other-layers-of-one-texture-level-run-as-one-layered-pass)) |
| `DM_FPS=30` ou `60` | Fixa a taxa de quadros-alvo |
| `DM_NVN_BARRIER=game`, `DM_NVN_CUT` | Ordem de passes e submissões de segmentos: veja [docs/agents/04-graphics.md](../../docs/agents/04-graphics.md) seção 7 |
| `DM_PRESENT_HZ=N` | A frequência do ritmo de apresentação (padrão 60); 0 significa sem limite de quadros e é só para perfis de desempenho |
| `DM_PRESENT_UPSCALE=metalfx` ou `off` | Como a apresentação escala uma imagem menor que seu retângulo na tela: `metalfx` pelo escalador espacial do MetalFX, `off` (o padrão) bilinearmente. Uma opção em ambas as plataformas (o Upscaling do launcher iOS; `env.txt` no Mac); renderizar na resolução da tela continua sendo a referência de nitidez ([referência](../../docs/reference/rendering.md#the-present-upscales-through-metalfx)) |

## Limitações conhecidas

Os caminhos a seguir não estão implementados e reportam vermelho no log quando alcançados (`RED_ONCE` em `nvn/nvn_mtl.m`):

- limpezas no meio de um render pass, e limpezas de cor com máscara parcial ou scissor parcial;
- primitivos de quad indexados (índices de 8 bits são alargados para 16 bits no replay);
- algumas formas de cópia de textura para textura e limpeza de textura.

Funções listadas em `g_nops`, como `MultiDraw*IndirectCount` e `SetDepthBounds`, são implementações vazias: chamá-las não reporta vermelho nem tem efeito algum. `Downsample` / `DownsampleTextureView` são gravados como cópias de textura com uma flag do host (`DM_COPY_RESOLVE`) e reproduzidos como uma resolução de multiamostragem do Metal.
