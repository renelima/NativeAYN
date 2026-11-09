# Mapeamento de objetos NVN → Metal

Este documento lista em que cada tipo de objeto e comando NVN (a interface gráfica nativa do Switch) resulta em Metal na camada de gráficos do host. Para a estrutura geral da camada de gráficos veja [README.md](README.md).

Escopo: as funções NVN que os jogos chamam. As outras estão ligadas a um stub que reporta em vermelho e que, quando chamado, imprime `[RED] NVN unimplemented function called: <name>` no log.

Duas convenções que percorrem a tabela toda:

- **Endereço de GPU = endereço de CPU**. Os memory pools do NVN usam memória que o próprio jogo aloca, e a camada de gráficos cria buffers Metal zero-copy nessa mesma memória. Qualquer endereço de buffer que o jogo passa é buscado na tabela de intervalos dos memory pools e convertido em "qual `MTLBuffer` + offset" (`mtl_addr()`).
- **Gravar primeiro, reproduzir depois**. As chamadas de command buffer só gravam registros na memória de comando fornecida pelo jogo (para o formato veja `nvn/nvn_obj.h`); só no momento do submit o `nvn/nvn_mtl.m` os reproduz como comandos Metal.

## 1. Objetos

| objeto NVN | implementação em Metal | Pontos-chave |
|---|---|---|
| Device | `MTLDevice` (memória unificada necessária) | As propriedades do dispositivo devolvidas pelo `nvnDeviceGetInteger` ainda são dadas ao jogo com os valores do Tegra X1 (`g_devinfo` em `hle_nvn.c`) |
| MemoryPool | `newBufferWithBytesNoCopy` sobre memória do jogo | As páginas no Apple silicon são de 16 KiB e os pools não têm garantia de alinhamento a isso: o começo é arredondado para baixo e o fim para cima até os limites de página antes de o buffer ser criado, e os buffers de pools adjacentes podem se sobrepor (`mtl_pool_add()`; a abordagem é verificada por `metal/probe/p_nocopy.m`) |
| Endereço de buffer (`NVNbufferAddress`) | (`MTLBuffer`, offset) | Buscado na tabela de intervalos dos pools |
| Texture | `MTLTexture` | As texturas empacotadas do jogo estão em layout block-linear e são deswizzled no upload; o mapeamento de formato e o deswizzling estão em `tex/`. Depois que o jogo reescreve a memória do pool, a textura é enviada de novo, o que é detectado pelo rastreamento de páginas sujas por proteção contra escrita do `nvn/nvn_dirty.c`. `ReadTexels`: texturas empacotadas são deswizzled e lidas direto da memória do pool, e texturas escritas pela GPU são lidas de volta do `MTLTexture` |
| TextureView | `newTextureViewWithPixelFormat:textureType:levels:slices:` | — |
| Sampler | `MTLSamplerState` | Para a cor de borda o Metal só tem três presets (preto transparente, preto opaco, branco opaco); é escolhido o mais próximo da cor de borda que o NVN dá |
| TexturePool / SamplerPool | Uma tabela do lado do host: número do descritor → textura / sampler | O valor de um handle de textura separada é o número do descritor |
| Program | Um `MTLFunction` por estágio | Indexado pelo hash da seção de controle do shader mais a área de código e carregado do `<key>_b.metallib` traduzido offline; nada é traduzido em tempo de execução. Para a chain de tradução veja o README |
| Objetos de estado (Blend / Color / ChannelMask / Multisample / Polygon / DepthStencil / VertexAttrib / VertexStream) | Campos do descritor do render pipeline + `MTLDepthStencilState` + estado do encoder | O jogo copia essas estruturas pequenas por valor, e o lado do host as empacota no lugar com bit fields. Os render pipelines e os estados depth-stencil ficam em cache pelo hash do estado |
| CommandBuffer | Um stream de registros gravado na memória de comando do jogo | Reproduzido no momento do submit. O registro do host dentro do `NVNcommandBuffer` do jogo deixa o byte +0x10 em paz: o código NVN embutido do jogo o escreve direto. Quando uma gravação estoura a memória do jogo, o host empresta um segmento, liberado só quando o jogo re-concede a memória em que a gravação começou; o `GetCommandMemoryUsed` conta os bytes nos dois |
| Queue | Fila de comandos do Metal | `SubmitCommands` = replay e submit |
| Sync | O valor de fence de uma submission | O `FenceSync` registra o valor de fence da submission mais recente; o `SyncWait` deixa o chamador passar só depois que a GPU de fato concluiu até aquele valor (o valor concluído de um `MTLSharedEvent`). Esperar num objeto de sync que nunca foi cercado por fence retorna um timeout direto |
| Event | Um valor de 32 bits na memória do jogo | O valor é escrito depois que a GPU conclui |
| Window | As texturas de swapchain do próprio jogo ↔ `CAMetalLayer` | O `AcquireTexture` retorna o índice de uma textura de swapchain, e a espera por sincronismo vertical acontece aqui; o `QueuePresentTexture` só põe a apresentação numa fila e retorna de imediato, e a thread de apresentação desenha aquela textura no drawable usando a região de recorte dada pelo `WindowSetCrop`. Ele desenha os bytes armazenados como o scan-out do Switch os envia: uma textura de swapchain sRGB é amostrada por uma view não-sRGB (`present_raw_src`) |

## 2. Comandos

| comando NVN | Metal | Notas |
|---|---|---|
| SetRenderTargets | Encerra o render encoder atual e abre um novo render pass | Os clears antes do primeiro comando no pass são incorporados à ação de load do attachment (load = Clear). Uma view que muda o formato (uma view `RGBA8_SRGB` de uma textura `RGBA8`) é anexada como uma view Metal daquele formato, então as escritas são codificadas como no Switch |
| ClearColor / ClearDepthStencil | Igual ao de cima | Clears no meio de um pass, e clears de cor com máscara parcial ou scissor parcial, não estão implementados e reportam vermelho quando encontrados |
| ClearTexture / ClearTextureui | Limpa uma textura por conta própria | Algumas formas não estão implementadas e reportam vermelho quando encontradas |
| BindProgram e as várias chamadas de estado Bind* | Gravados no estado atual; o pipeline é obtido conforme necessário antes do próximo draw | — |
| BindUniformBuffer(s) / BindStorageBuffer(s) / BindSeparateTextures / BindSeparateSamplers / BindImages | Preenche o argument buffer de acordo com o json de reflection do shader | Para como os números de slot correspondem às seções de constante no shader, veja "Shader translation chain" no README |
| BindVertexBuffers | Vertex buffer = (`MTLBuffer`, offset) | — |
| SetViewports / SetScissor / SetDepthRange / Stencil* / SetBlendColor / SetPolygonOffsetClamp | O ajuste correspondente no encoder | — |
| DrawArrays / DrawElementsInstanced | Draw / draw indexado | O Metal não tem primitiva de quad nem de triangle-fan: quads e fans não indexados são desenhados como triângulos por meio de index buffers compartilhados (0,1,2,0,2,3 por quad; 0,i,i+1 por triângulo do fan) com first como o vértice base; quads / fans indexados e índices de 8 bits são expandidos na CPU numa lista de índices de 32 bits (16 bits para listas de 8 bits) na arena do frame |
| DrawArraysIndirect / DrawElementsIndirect | Draw indireto / draw indireto indexado | Os registros do NVN têm os layouts de `MTLDrawPrimitivesIndirectArguments` / `MTLDrawIndexedPrimitivesIndirectArguments` e são lidos pela GPU a partir do pool. Quads, fans e índices de 8 bits: um compute pass antes do render pass (`ixc_run`) converte o registro escrito pela GPU em argumentos indiretos indexados (e, para draws indexados, uma lista de índices de 32 bits de no máximo 49.152 índices) |
| DrawTexture | Um retângulo texturizado desenhado por um pipeline embutido | Um render pass próprio por alvo de cor vinculado, com o scissor e a máscara de canal; sem blending (`draw_tex`, [renderização](../../docs/reference/rendering.md#nvncommandbufferdrawtexture)) |
| DispatchCompute / DispatchComputeIndirect | Compute encoder, com o tamanho do thread group tirado do json de reflection | Os 4 compute shaders embutidos no programa principal usam as versões escritas à mão em `nvn/nvn_builtin.h`. Indireto: `dispatchThreadgroupsWithIndirectBuffer` sobre o registro; a GPU copia as contagens de grupos dele para o segmento c0 + 0x200 do driver que o shader lê |
| CopyBufferToBuffer / UpdateUniformBuffer | Cópias de buffer inseridas em ordem no stream de comandos | Os dados do `UpdateUniformBuffer` são copiados para a memória de comando no momento da gravação, porque o NVN permite que o chamador modifique os dados originais depois de gravar |
| CopyBufferToTexture / CopyTextureToBuffer / CopyTextureToTexture | Cópias de textura | Algumas formas de textura-para-textura não estão implementadas e reportam vermelho quando encontradas |
| ReportCounter / ResetCounter | O valor do contador é escrito de volta depois que a GPU conclui | O tipo "samples passed" é implementado com o buffer de visibility result do Metal |
| SignalEvent | O valor do evento é escrito, o que acontece depois que a GPU conclui | — |
| SetTexturePool / SetSamplerPool | Gravados no stream de comandos, anotando o pool de descritores em uso no momento | — |

As seguintes são implementações vazias (`g_nops` em `hle_nvn.c`); chamá-las não reporta vermelho nem tem efeito nenhum:

`SetPatchSize`, `MultiDrawArraysIndirectCount`, `MultiDrawElementsIndirectCount`, `SetDepthBounds`, `DownsampleTextureView`, `WaitSync`, `SetShaderScratchMemory`, `PushDebugGroupDynamic`, `PopDebugGroup`, `InsertDebugMarkerDynamic`, `WaitEvent`, e `nvnWindowFinalize`.

## 3. Bindings de shaders

Os shaders do Switch obtêm seus recursos por meio de algumas seções de constante mantidas pelo driver, e durante o replay a camada de gráficos preenche os mesmos conteúdos no argument buffer do Metal. Cada binding no json de reflection carrega um tipo, uma string de `nvn/dmsh_abi.h` (`DMSH_KIND_*`, a única definição que a build e o host compartilham, com o layout dos segmentos do driver) que o `nvn/nvn_refl.m` converte num código `NVN_RB_*` (`nvn/nvn_refl.h`):

| Tipo | Significado |
|---|---|
| `CBUF` | slot r de uniform buffer |
| `TEX` / `SAMP` / `SAMPT` | Slot de textura separada, slot de sampler separado; `SAMPT` significa que o sampler é tirado do handle combinado do slot de textura |
| `IMG` | Textura gravável (slot de image) |
| `TBUF` | Buffer tipado (buffer texture) |
| `SBUFP` | Ponteiro de dispositivo de um slot de storage buffer |
| `UBOP` | Ponteiro de dispositivo de um slot de uniform do estágio de compute |
| `C1` | A seção de constante embutida do shader |
| `DRV0` / `DRV2` | As seções de constante do driver c[0] (tabela de endereços de storage buffer) e c[2] (tabela de handles de textura e sampler), preenchidas na hora pela camada de replay de acordo com os bindings atuais |
| `SUPPORT` | Constantes auxiliares usadas pelo próprio tradutor |

Um slot de textura sem nada vinculado recebe uma textura dummy, cujo tipo e classe de pixel são tirados do reflection.

## 4. Adicionar uma função que ainda não está implementada

1. Em `nvn/hle_nvn.c`, escreva a implementação com a macro `NVN(name, return type, parameters…)`. A macro faz uma checagem de assinatura contra `nvn/nvn_protos.h` e registra a implementação na tabela de slots; se o nome não estiver em `nvn/names.txt`, o programa aborta na inicialização.
2. Se for um comando: adicione o formato do registro em `nvn/nvn_obj.h`, escreva a gravação em `hle_nvn.c`, e escreva o replay em `nvn/nvn_mtl.m`.
3. Adicione um teste em `tests/`, depois `make -C dm/gfx/tests run`.
