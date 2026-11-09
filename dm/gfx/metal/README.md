# dm/gfx/metal — exemplo mínimo offscreen de Metal

Um módulo pequeno e autônomo: ele envolve o Metal numa interface pura em C, renderiza a imagem para uma textura offscreen e a lê de volta para a CPU, sem pôr nada na tela. Não é compilado no host (o `dm/host/Makefile` não inclui este diretório); a camada de replay do Metal que o jogo realmente usa está em `../nvn/nvn_mtl.m`. Isto é mantido como referência que compila sozinha e pode ser entendida em poucas dezenas de linhas, e como uma pequena sonda para verificar o comportamento do Metal.

## Interface (`mtl_backend.h`)

| Interface | Efeito |
|---|---|
| `mtlb_init()` / `mtlb_shutdown(ctx)` | Cria / libera o contexto: dispositivo, fila de comandos, command buffer do quadro atual, cache de pipelines |
| `mtlb_target(ctx, w, h, fmt)` / `mtlb_target_free(rt)` | Cria / libera um render target offscreen, com formato `MTLB_FMT_BGRA8` ou `MTLB_FMT_RGBA8` |
| `mtlb_clear(ctx, rt, r, g, b, a)` | Registra no quadro atual um render pass que limpa o target para a cor dada |
| `mtlb_triangle(ctx, rt)` | Registra no quadro atual outro render pass (mantendo o conteúdo existente) que desenha um triângulo tricolor |
| `mtlb_readback(ctx, rt, buf, size)` | Submete o quadro atual, espera o GPU terminar e lê os pixels de volta para `buf` compactados por linha (linha 0 é o topo da imagem) |

As funções retornam 0 para sucesso e -1 para falha, e o motivo da falha é impresso no erro padrão. A implementação está em `mtl_backend.m` (Objective-C, ARC).

Pontos de projeto:

- **Um command buffer por quadro**: ele é criado apenas quando um pass é registrado pela primeira vez; as limpezas e o triângulo seguintes são acrescentados ao mesmo command buffer, e ele só é submetido no readback.
- **Render targets não são variáveis globais**: o contexto e cada target são ponteiros opacos.
- **Modo de armazenamento é Shared**: em dispositivos de memória unificada uma textura Shared pode ser usada diretamente como anexo de render, e depois que o GPU termina a CPU a lê diretamente, sem buffer intermediário. Em dispositivos de memória não unificada `mtlb_init` reporta erro logo no início.
- O shader do triângulo é MSL inline, compilado em tempo de execução.

## Compilação e execução

```sh
make -C dm/gfx/metal        # produz test_offscreen
make -C dm/gfx/metal run    # roda o autoteste; código de saída 0 significa que tudo passou
make -C dm/gfx/metal clean
```

Só são necessárias as ferramentas de linha de comando do Xcode; nenhum arquivo de jogo é necessário. Os produtos da compilação já estão listados no `.gitignore` deste diretório.

## Autoteste (`test_offscreen.c`)

Todos os valores esperados são calculados de forma independente no teste, sem ler dados internos do backend:

- os vértices do triângulo são copiados separadamente no teste;
- coordenadas normalizadas são convertidas em pixels: `px = (x + 1) / 2 · W`, `py = (1 − y) / 2 · H`;
- a cor no centro de um pixel é a interpolação linear das três cores de vértice por coordenadas baricêntricas.

Os dois testes são executados uma vez para cada formato, BGRA8 e RGBA8:

1. Limpar para uma cor sólida e comparar pixel a pixel.
2. Limpar a tela e depois desenhar o triângulo: pixels dentro do triângulo (a mais de 1 pixel de uma borda) são comparados com o valor interpolado, com a tolerância dada por `TOL` no código-fonte; pixels fora são comparados com a cor de limpeza; a faixa de 1 pixel exatamente sobre as bordas é pulada, pois as regras de borda da rasterização estão fora do escopo deste teste.

Antes do readback o buffer é primeiro preenchido com 0x5a, para evitar "passar mesmo que o backend não tenha escrito nada".

## Sonda (`probe/p_nocopy.m`)

Um programa de arquivo único, sem alvo de build; compile-o manualmente com clang quando precisar. Ele verifica uma coisa da qual a abordagem de memory pools da camada de replay depende: num pedaço de memória não alinhado a página, depois de estender seu início e seu fim para fora até os limites de página, um `MTLBuffer` zero-copy pode ser criado; dois desses buffers podem se sobrepor; e o que o GPU escreve pode ser lido pela CPU através do ponteiro original.

## Limitações

- Os vértices do triângulo são fixos no código de `mtl_backend.m`; não há estado configurável como vertex buffers, shaders ou viewports.
- `mtlb_readback` espera sincronicamente o quadro inteiro terminar e bloqueia a thread chamadora.
- Só um único anexo de cor e dois formatos de 8 bits são suportados; não há depth / stencil, multiamostragem nem múltiplos anexos.
- Não é thread-safe; apenas dispositivos de memória unificada são suportados.
