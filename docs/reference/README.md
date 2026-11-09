# Referências por subsistema

Como o host atende cada parte de um jogo de Switch, e todas as falhas encontradas até hoje, uma entrada por mecanismo: sintoma, causa, correção (arquivo e função), os jogos em que foi encontrada (com link para as notas em `dm/games/<id>/README.md`). Comece pelo [índice de sintomas](../agents/symptoms.md) quando tiver uma linha de log ou um resultado visível; os fluxos de trabalho que levam até aqui estão em [docs/agents](../agents/README.md).

| Arquivo | Cobre |
|---|---|
| [packing.md](packing.md) | Script do NSP, entradas comprimidas e artefatos de build |
| [rewriter.md](rewriter.md) | O rewriter de NSO `patch_nso.py`: jump tables, janelas exclusivas, alcance da área de patches, cuts e observation gates (e os truques carregados entre builds com o mesmo matcher), funções sem entrada de unwind, pilha e registradores de sistema, realocações |
| [memory.md](memory.md) | Layout do convidado, heap e application pool, memória de endereço virtual e a região alias (a área de nós do SDK), aloca­dores de engine, SVCs de mapeamento, ficar sem memória |
| [threads.md](threads.md) | Criação de threads e núcleos, arbitração de endereços, yields e spin waits, eventos |
| [services.md](services.md) | Chamadas do SDK e serviços de sistema: conexões sm, nomes que mudaram entre SDKs, filesystem e saves, add-on content, input, teclado e applets, display e modo de operação, contas e rede, configurações e fontes, biblioteca C e tempo, módulos |
| [movies.md](movies.md) | A biblioteca de filmes do SDK: ciclo de vida do decodificador, slots de saída, saída em textura nativa, demuxers e codecs, tocadores de filme dos jogos |
| [audio.md](audio.md) | O audio renderer e suas command lists por SDK, AudioOut e dispositivos, codecs, temporização |
| [frame-rate.md](frame-rate.md) | O frame step do host, limitadores de frame de engines, intervalos de present e taxas de display, custo de CPU |
| [nvn-objects.md](nvn-objects.md) | Tamanhos de objetos NVN, pools de memória, memória de comando, filas e sincronizações, texturas escritas pela CPU, entry points do NVN |
| [rendering.md](rendering.md) | O replay sobre Metal: texturas, passes e clears, blend / depth / stencil, entrada de vértices, uniforms e ordenação, renderização condicional; as camadas Vulkan e OpenGL |
| [shaders.md](shaders.md) | Contêineres de shaders por engine, seções de controle e código, tradução, bindings e handles, shaders compilados em tempo de execução, pipelines e caches |
| [cheats.md](cheats.md) | Como os cheats funcionam num Switch com Atmosphère (dmnt, o arquivo, a VM, seu comportamento em falha) e onde este host difere |
| [game-state.md](game-state.md) | Saves, estado roteirizado e comportamento de jogo que parece bug do host mas não é |

Uma nova descoberta geral entra no arquivo e na seção a que pertence, no mesmo formato, com uma linha no índice de sintomas; um fato sobre um jogo entra na nota daquele jogo.
