# Estado de jogo

O estado dos próprios jogos: saves, estado roteirizado e comportamentos que parecem bug do host mas não são. Cada entrada: sintoma, causa, correção, os jogos onde foi encontrada. O [índice de sintomas](../agents/symptoms.md) lista todos os sintomas.

## Comportamento do jogo que não é bug do host

### A maioria dos draws está em command buffers reproduzidos depois

- **Sintoma:** "travado" numa tela com um draw por frame.
- **Causa:** geralmente não está travado: a maioria dos draws está em command buffers gravados e reproduzidos depois.
- **Correção:** despeje os passes (`DM_NVN_PASSDUMP`, [04, Switches de diagnóstico](../agents/04-graphics.md#7-switches-de-diagnostico-host)) antes de assumir que a lógica do jogo está bloqueada.

### Botões mapeados por posição: B confirma

- **Sintoma:** os menus ignoram A.
- **Causa:** não é falha do host: o jogo mapeia seus botões por posição, B confirma e A volta (suas mensagens dizem isso).
- **Encontrado em:** DARK SOULS: REMASTERED (SDK 4.5.0). O primeiro boot mostra dois avisos antes do menu do título (sem conexão com a rede, e depois o jogo vai começar no modo offline), e um novo jogo primeiro percorre as configurações de Brilho, Opções e Botões.

### Qualquer erro de Lua encerra um jogo LÖVE

- **Sintoma:** um crash em `main+0x339c00` um ou dois segundos após o título, no teardown do módulo de vídeo do love-nx (`love::video::theora::Video` → `~Worker`; sua thread liberou o worker que o módulo fechando também apaga).
- **Causa:** o estado Lua estava fechando: no Switch, o `love.errhand` do Balatro retorna de imediato, então qualquer erro de Lua encerra. Um `print` no topo do `love.errhand` numa cópia do RomFS (`DM_ROMFS=<copy>`) deu o erro: `Shader uniform 'distortion_fac' does not exist` (a execução de captura não tinha biblioteca de shaders, então todo programa linkava sem uniforms), depois `bad argument #2 to 'send' (number expected, got boolean)` (uma uniform `bool` reportada como `GL_UNSIGNED_INT`).
- **Correção:** procure primeiro o erro de Lua; os dois erros aqui eram falhas do host nas shaders e uniforms da camada GL (veja [Shaders GL montados em tempo de execução e capturados](rendering.md#gl-shaders-put-together-at-run-time-and-captured) e [Uniforms, pipelines e atributos GL](rendering.md#gl-uniforms-pipelines-and-attributes)).
- **Encontrado em:** Balatro (SDK 18.3.2, LÖVE 11.4; um boot).
- **Checagem:** sondar os destrutores (`DM_PROBE`) e registrar todo objeto que o GC do Lua liberou (`DM_PROBE=main:0x18b920@x0+0`: o módulo faz proxy de todos de uma vez) mostrou que era um shutdown, não uma coleta prematura.

### A janela de escuta atrasada e marcada pela batida da calibração de latência de TV

- **Sintoma:** a calibração de latência de TV ("pressione A quando ouvir a quarta batida") ignora A.
- **Causa (não é bug do host):** na dock, o primeiro boot a executa (script de cena `main+0x4c0820`). Ela começa a escutar só `0x3c0` frames (16 s) depois do aviso, através do controle ativo (`main+0x22b0` id `0x3ed`, botão virtual 4 = A), e passa quando cerca de dez pressão mantêm um offset estável entre −5 e +13 frames do cue (avaliação `main+0x2948c0`; offset = ticks do cue em `+0x34` / cerca de 17 ticks por frame, cerca de 480 por batida).
- **Testando:** pressões roteirizadas em segundos fixos saem do ritmo da batida; use `DM_KEYS` com tempo em frames no período da barra (117,12 frames nos seus 123 BPM) e leia o offset de cada pressão com `DM_PROBE=main:2951f0@34`. Uma calibração aprovada é salva por identidade do painel de TV (incluída a toda-zero do host, [Identificador de painel de exibição padrão](services.md#default-display-panel-identifier)) e não é pedida de novo.
- **Profile:** ele joga no portátil com Boost (`op_mode handheld`, `perf_mode boost`), que pula a configuração de TV e mantém o tamanho de draw em 1080p.
- **Encontrado em:** Rhythm Heaven Groove (SDK 21.4.0).

## Estado roteirizado

### O teclado virtual em execuções roteirizadas (`DM_KBD`)

- **Sintoma:** numa execução roteirizada, uma tela volta sozinha logo depois de uma confirmação; o log mostra `swkbd: … → no window, treated as cancel`.
- **Causa:** a confirmação abriu o teclado do sistema (`swkbd`, entrada de nome). Sem janela o host responde "cancelar" por projeto.
- **Correção:** passe `DM_KBD=<texto>` para respondê-lo.
- **Encontrado em:** Fire Emblem Engage (a escolha de aparência).

### Telas de entrada de player (`hid::StartLrAssignmentMode`)

- **Sintoma:** uma tela de entrada de player que ignora A (uma seleção de personagem com quatro espaços "L + R para entrar" depois do título).
- **Causa:** o jogo chama `hid::StartLrAssignmentMode` e espera L e R pressionados juntos na própria tela de entrada, depois uma escolha de personagem com A.
- **Correção:** em `DM_KEYS` isso é `L+R~0.5` enquanto a tela está aberta (pressões no título antes dela são perdidas).
- **Encontrado em:** Super Mario 3D World + Bowser's Fury.
- **Nota:** na seleção de jogo, segurar o analógico para a direita por um segundo (`7:LRight~1`) muda para Bowser's Fury, que também usa `nvnTextureGetSwizzle` e `nvnTextureGetStorageClass`. O movimento no mapa e no nível lê só o analógico esquerdo: o `DM_KEYS` aceita `LLeft` / `LUp` / `LRight` / `LDown`. Uma rota funcional até World 1-1 num save novo: `DM_KEYS=6:A,10:A,14:L+R~0.5,17:A*3/2,24:Plus*6/3,45:A*6/3,66:LUp~1,70:A,74:LRight~1,78:A,82:LLeft~1,86:A` (World 1-1 por volta do frame ~6000).
