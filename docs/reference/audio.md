# Áudio

O renderizador de áudio (`dm/host/hle_audren.c`), AudioOut, dispositivos de áudio e codecs. Cada entrada: sintoma, causa, correção, os jogos em que foi encontrada. O [índice de sintomas](../agents/symptoms.md) lista todos os sintomas.

## Renderizador de áudio

### Rodando o renderizador da própria SDK na CPU

Confira se a SDK traz o renderizador: liste as exports do módulo `sdk` para `nn::audio::server::` e `nn::audio::dsp::CommandListProcessor`. Se estiverem lá (Super Mario Bros. Wonder, SDK 16), não escreva um mixer:

- troque uma cópia de `AudioRendererParameter` para rendering device = CPU (+0x2c = 1) e execution mode = manual (+0x30 = 1) em `GetAudioRendererWorkBufferSize` e em ambas as overloads de `OpenAudioRenderer`;
- chame os originais da SDK (`guest_sym` devolve o código do próprio módulo mesmo para um símbolo forçado);
- conduza `ExecuteAudioRendererRendering` a partir de uma thread do convidado (`dm_guest_thread_spawn`).

A abertura então passa por `CreateAudioRendererManagerByDfc`; sem IPC.
Para um renderizador DSP substituído, a consulta de tamanho mantém o contrato de work buffer do serviço; uma representação local de CPU maior é um custo do backend. Veja [O heap de um sound engine pequeno demais para seu renderizador e players](#o-heap-de-um-sound-engine-pequeno-demais-para-seu-renderizador-e-players).

### O `AudioRendererParameter` mais longo da SDK 8

- **Sintoma:** abort com Result 0x499 em `audio::GetAudioRendererWorkBufferSize`.
- **Causa:** o `AudioRendererParameter` da SDK 8 é uma palavra mais longo que o da SDK 16: sua revisão fica em +0x38. O host copia o parâmetro do jogo antes de trocá-lo para renderização na CPU com execução manual, e copiava só 0x38 bytes, então `CheckValidRevision` lia lixo.
- **Correção:** agora copia 0x40.
- **Encontrado em:** The Legend of Zelda: Link's Awakening (SDK 8.2.0).

### Um renderizador aberto sem evento

- **Sintoma:** o jogo fica mudo; só os updates do jogo renderizam (catch-up frames que nada reproduz). O log tem `audren: OpenAudioRenderer (no event)` e nenhum `render thread started`.
- **Causa:** o jogo abre o renderizador com `OpenAudioRenderer(handle, parameter, work, size)`, a forma sem evento de sistema, e cadencia seus próprios updates, mas o renderizador ainda deve rodar a cada 5 ms. O host costumava iniciar sua render thread só para a forma com um evento.
- **Correção:** as duas formas passam por uma única abertura que inicia a thread (`hle_audren.c` `ar_open`).
- **Encontrado em:** Balatro (SDK 18.3.2, OpenAL Soft do LÖVE; um boot).

### A abertura da SDK com evento chama a abertura sem evento (`t_in_open`)

- **Sintoma:** `[RED] audren: a second renderer` logo depois da primeira abertura, com o mesmo work buffer e "original device 1 mode 1".
- **Causa:** não é um segundo renderizador: o `OpenAudioRenderer` com evento da SDK 17.5.3 abre o renderizador chamando a forma sem um através do PLT (`sdk+0x1e489c`), que o loader vincula ao hook do host já que aquela forma tem um ([Um renderizador aberto sem evento](#um-renderizador-aberto-sem-evento)), então `ar_open` rodou duas vezes para um só renderizador. A chamada interna iniciou a render thread antes de a externa ter criado o evento, e a chamada externa tomou o renderizador como um segundo.
- **Correção:** uma flag thread-local (`t_in_open`) entrega a chamada interna direto à SDK com a cópia CPU / manual do parâmetro feita pelo host, e só a chamada externa inicia a thread (`hle_audren.c`).
- **Encontrado em:** ROMANCE OF THE THREE KINGDOMS 8 REMAKE (SDK 17.5.3).

### O evento do renderizador como o serviço o dá: um evento de kernel

- **Sintoma:** o loop de frames para de vez enquanto um nível carrega e o áudio continua renderizando. A thread main espera em `os::WaitSystemEvent` sob `CAudioMemoryPool::Shutdown`, e um thread de som de prioridade mais alta espera na mesma variável de condição.
- **Causa:** com o serviço DSP (device 0, o que o jogo pede) `OpenAudioRenderer` anexa o evento do renderizador do serviço, um evento readable de kernel (`AttachReadableHandleToSystemEvent`, auto-clear). Com renderização na CPU e execução manual, para o que o host troca ([Rodando o renderizador da própria SDK na CPU](#rodando-o-renderizador-da-propria-sdk-na-cpu)), ele cria um local em vez disso (`CreateSystemEvent(event, AutoClear, false)`, SDK 4: `sdk+0x190b6c`). Sinalizar um evento auto-clear local acorda um waiters, o de prioridade mais alta. Um evento de kernel acorda todos os waiters, e cada um tenta `ResetSignal`. Donkey Kong Country: Tropical Freeze espera no evento de duas threads: seu thread de som (prioridade 29, `main+0x971e8`) a cada frame, e a thread main enquanto desanexa um pool de memória de áudio (`main+0x6fa8c`: `RequestDetachMemoryPool`, depois espera e atualiza até `IsMemoryPoolAttached` ser false). O thread de som pegava todas as wakes, e a thread main nunca mais acordava.
- **Correção:** quando o jogo abriu o renderizador para o serviço, o host substitui o evento local da SDK por um auto-clear interprocess (`DestroySystemEvent`, depois `hle_bind_system_event(event, 1)` em `ar_open`), e o evento do jogo espera numa cópia de sua ponta de leitura. Aquele evento é o do próprio renderizador, não o do jogo ([O evento do renderizador pertence ao renderizador, não ao jogo](#o-evento-do-renderizador-pertence-ao-renderizador-nao-ao-jogo)). O renderizador da SDK sinaliza através de `os::SignalSystemEvent`, que aceita qualquer tipo. Um jogo que abre ele mesmo um renderizador de CPU mantém o evento local da SDK.
- **Encontrado em:** Donkey Kong Country: Tropical Freeze (SDK 4.4.0): o carregamento do primeiro nível depois da cinematográfica de introdução.

### O evento do renderizador pertence ao renderizador, não ao jogo

- **Sintoma:** o jogo sai com 42 em seu primeiro frame de áudio renderizado: `Break#1 reason=0` em `os::SignalSystemEvent`, chamado a partir do público `ExecuteAudioRendererRendering` na render thread do host (`render_main`).
- **Causa:** com o serviço DSP (o que o jogo pede), nenhum `OpenAudioRenderer` de SDK mantém um ponteiro para o `SystemEventType` do jogo. Ele guarda NULL no slot de evento do handle e anexa ao objeto o handle readable do serviço, então o jogo pode destruir o objeto ou reutilizar sua memória assim que a abertura retorna. O slot é `handle[1]` nas SDKs 3, 5 e 12 (SDK 3 `sdk+0x17e004`, SDK 12 `sdk+0x4d2b4`) e `*handle[1]` na 18. A abertura CPU / manual para a qual o host troca ([Rodando o renderizador da própria SDK na CPU](#rodando-o-renderizador-da-propria-sdk-na-cpu)) guarda o objeto naquele slot em vez disso (SDK 3 `sdk+0x17dffc`, SDK 12 `sdk+0x4d274`), e `ExecuteAudioRendererRendering` o sinaliza depois de cada render. LEGO Star Wars: The Skywalker Saga passa um evento em sua pilha, e Little Nightmares reutiliza a memória do objeto. O primeiro render achou um byte de estado (+0x28) que já não era o do evento, e a SDK abortou.
- **Correção:** `ar_open` dá à abertura da SDK um evento do próprio renderizador (`g_rev` em `hle_audren.c`, vivo até `CloseAudioRenderer`, que o destrói), seja qual for o slot em que a SDK o guarde. O objeto do jogo ganha uma cópia da ponta de leitura daquele evento de kernel (`hle_dup_readable_handle`), anexada como gerenciada e auto-clear, como é o handle copiado do serviço. O renderizador nunca mais toca no objeto do jogo. O binding do host também não fica mais no endereço do jogo, onde um evento de serviço posterior no mesmo armazenamento o teria encontrado (`hle_bind_system_event` retorna cedo para um endereço que já mantém). Testes: `t_audren_work` (o evento do jogo é sobrescrito depois da abertura, incluindo o slot das SDKs 3 a 12) e `t_system_event` (a ponta de leitura copiada).
- **Encontrado em:** LEGO Star Wars: The Skywalker Saga (`010042D00D900000`, 1.10.0, SDK 12.3.6; issue #632), Little Nightmares (`01002FC00412C000`, 1.0.4, SDK 3.5.1; PR #804), ambos de profiles provisórios. DRAGON BALL FighterZ (`0100A250097F0000`, SDK 4.5.0; issue #771) aborta no mesmo lugar, e a abertura e o `ExecuteAudioRendererRendering` da SDK 5.4 são o mesmo código que os da SDK 3 e da 12. Nenhum dos três está aqui: a correção aguarda as execuções dos relatores.

### O lock de update do renderizador (`update_lock`)

- **Sintoma:** nenhum próprio; a geração de comandos corre com o `Update` do jogo em SDKs cujo lock não está no offset da SDK 16.
- **Causa:** `AudioRenderSystem::Update` segura um mutex em `AudioRenderSystem`; `GenerateCommand` não o toma, então o host o toma (a thread de sistema do modo auto faz o mesmo em `SendCommandToDsp`). Qual mutex depende da SDK: `nn::os::LockMutex(MutexType *)` em +0x1d0 na SDK 4.4 e em +0x1d8 na 6.4–8.2, `SdkMutexType::Lock` em +0x1d8 na 10.4–11.4, +0x1f0 na 13.3–16.2 e +0x1f8 na 17.5 (os builds `sdk` de 24 jogos à mão), inlined na 19 e na 20 (+0x2d0, o layout Span: [O layout `AudioRenderSystem` da SDK 20](#o-layout-audiorendersystem-da-sdk-20)). O host tomava o da SDK 16 para toda SDK antes da 20, o que escrevia uma palavra de lock em outro campo e não excluía nada.
- **Correção:** `update_lock` em `hle_audren.c` o lê do próprio `Update` da SDK: o registrador em que o prólogo soma o offset e move para x0 antes de seu primeiro `bl`, cujo slot do PLT deve conter `LockMutex` ou `SdkMutexType::Lock`; qualquer outra coisa mantém a da SDK 16.
- **Encontrado em:** Mario Kart 8 Deluxe (4.4), o primeiro jogo em que estava francamente errado.
- **Checagem:** conferido com som em Big Brain Academy: Brain vs. Brain (11.4, +0x1d8), DRAGON QUEST XI S (7.3, `MutexType` +0x1d8) e Super Mario Odyssey (7.3, que fica mudo no primeiro minuto com qualquer um dos locks).

### Sem `SdkMutexType` na SDK 3

- **Sintoma:** `FATAL audren: missing SDK symbol _ZN2nn2os12SdkMutexType4LockEv` logo depois que a render thread do renderizador inicia.
- **Causa:** o caminho de render procurava `SdkMutexType::Lock` / `Unlock` antes de ler qual lock o `Update` da SDK toma ([O lock de update do renderizador](#o-lock-de-update-do-renderizador-update_lock)); o `sdk` da 3.5.1 não tem `SdkMutexType` e toma `nn::os::LockMutex` em +0x1f8.
- **Correção:** `render_once` procura as funções `SdkMutexType` só quando `update_lock` não acha `LockMutex` (`hle_audren.c`).
- **Encontrado em:** OCTOPATH TRAVELER (SDK 3.5.1), o primeiro jogo de SDK 3 pelo renderizador de áudio (Bayonetta 2, na 3.6.0, toca através de `AudioOut`).

### Onde o `AudioRenderSystem` fica em `AudioRendererImpl`

- **Sintoma:** o som fica picotado: cerca de dois de cada três blocos de 5 ms são silenciosos (um `DM_AUDIO_DUMP`), sem underruns. A linha `catch-up renders so far` do log cresce de um para cada frame renderizado (2000 de cada 2000), e todo update registra `no frame rendered before update N`.
- **Causa:** a tomada de `AudioRendererImpl::ExecuteAudioRendererRendering` pelo host achava o `AudioRenderSystem` num `+0x60` fixo, onde as SDKs 16 e 20 o mantêm. A SDK 3.4 o mantém em `+0x50` (`add x19, x0, #0x50` no prólogo, `sdk+0x188a10`). Com o ponteiro errado, o modo de execução e o dispositivo de renderização vinham de outros campos, então cada render na render thread de 5 ms falhava com 0x40299 e tocava um bloco silencioso. Só os updates do jogo renderizavam: um catch-up frame cada, a 60 Hz em vez de 200.
- **Correção:** `impl_sys_off` em `hle_audren.c` lê o offset da própria função da SDK: o primeiro `add xd, x0, #imm` antes da primeira branch. Se não achar nenhum, mantém `+0x60`. Todo outro build `sdk` à mão (18 jogos, SDKs 4.5 a 21) lê `+0x60`.
- **Encontrado em:** SteamWorld Dig e SteamWorld Dig 2 (ambos trazem o mesmo build `sdk` da SDK 3.4.0).
- **Checagem:** depois da correção, o log tem `audren: AudioRenderSystem at +0x50 in AudioRendererImpl`, algumas dezenas de catch-up renders por 2000 frames, e um dump sem blocos silenciosos quando a música começa.

### O caminho da render thread pelo público `ExecuteAudioRendererRendering` (SDK 20)

- **Sintoma:** `CommandListProcessor::Process` derruba de vez em quando (SIGSEGV sob `render_main`, uma vez aos 60 s e outra aos 134 s no santuário); todo `RequestUpdate` renderiza um catch-up frame.
- **Causa:** o público `ExecuteAudioRendererRendering` (`sdk+0x221e90`) chega a `AudioRendererImpl::ExecuteAudioRendererRendering` (`sdk+0x22ca88`, `x0 += 0x60` e um jump pelo PLT) através da vtable, então a tomada da função `Impl` pelo host nunca era chamada. O `AudioRenderSystem::ExecuteAudioRendererRendering` da própria SDK rodava em vez disso (sem lock de update), todo `RequestUpdate` renderizava um catch-up frame porque o contador de render do host nunca se movia, e as duas threads escreviam no mesmo command buffer.
- **Correção:** o host também toma `AudioRenderSystem::ExecuteAudioRendererRendering`, o alvo do PLT (`hle_audren.c`); os catch-ups caíram de um por update para 3 por execução.
- **Encontrado em:** The Legend of Zelda: Breath of the Wild (SDK 20).
- **Nota:** o outro layout de `AudioRenderSystem` da mesma SDK está em [O layout `AudioRenderSystem` da SDK 20](#o-layout-audiorendersystem-da-sdk-20).

### Um renderizador abaixo de 48 kHz sem upsampler na CPU

- **Sintoma:** SIGSEGV no endereço 0x18 em `UpsamplerManager::Allocate` na render thread.
- **Causa:** um DeviceSink abaixo de 48 kHz é amostrado para cima pelo upsampler da SDK, e `AudioRenderSystem::SetUpRenderResourceSet` aloca o `UpsamplerManager` só para o DSP (`sdk+0x21eb6c`: taxa 0x7d00 e rendering device +0x48 == 0); com a renderização na CPU do host o ponteiro do manager (`AudioRenderSystem+0x150`, o +0x20 do contexto de render) é nulo.
- **Correção:** o host responde `Allocate` sobre um manager nulo sem upsampler, então o generator pula o comando de upsample (`sdk+0x225b40`), e reamostra ele mesmo o downmix estéreo do DeviceSink 3:2 (um sinc janelado de 16 taps em três fases, `up_3_2` em `hle_audren.c`), 240 amostras por frame de 5 ms para a saída de 48 kHz.
- **Encontrado em:** DRAGON QUEST VII Reimagined: taxa 32000, 160 amostras por frame, `GenerateSinkCommands` `sdk+0x225a80`.
- **Nota:** até então o código de saída assumia um buffer com upsample a 32 kHz que nenhum jogo tinha produzido.

### O heap de um sound engine pequeno demais para seu renderizador e players

- **Sintoma:** a thread main gira na inicialização numa espera de recurso (um loop `YieldThread`, sem crash), logo depois de `OpenAudioRenderer`.
- **Causa:** o serviço DSP e o servidor local da SDK de uma aplicação usam representações de comando/estado diferentes. O host encaminhava o requisito de work buffer do servidor CPU local da SDK como o requisito do serviço DSP do convidado. Estruturas de comando locais maiores podiam então esgotar um heap de som fixo que cabe no orçamento do serviço. Mudar só os campos de dispositivo / execução na rotina de dimensionamento local não revela essa diferença: ela ainda dimensiona as mesmas estruturas compiladas. A nota do jogo registra a comparação contra o código de dimensionamento real do Horizon e a falha de alocação resultante.
- **Correção:** `ar_service_work_size` em `hle_audren.c` calcula o orçamento do serviço Horizon até a revisão 15 do renderizador, com gates de revisão para armazenamento de graph/splitter, estados de efeito, frames de performance e armazenamento de comandos. Um chamador DSP recebe esse tamanho. Na abertura o host pergunta independentemente à SDK seu requisito CPU/manual e fornece um buffer de backend alinhado a página e zerado se ele excede a capacidade do chamador; um buffer abaixo do requisito de serviço declarado é recusado. Ambas as formas de abertura compartilham a alocação, incluindo uma abertura no-evento aninhada. Aberturas que falham a liberam; o close junta a render thread, fecha o renderizador da SDK e depois a libera. Renderizadores de CPU explícitos mantêm seu contrato de SDK. Um buffer suficiente do chamador é reusado; revisões além do modelo de serviço verificado mantêm o tamanho da SDK. Nenhum firmware é carregado ou distribuído em execução, e nenhuma constante de heap de jogo muda.
- **Encontrado em:** Hyrule Warriors: Age of Calamity.
- **Checagem:** `make -C dm/host/tests GAME=<id> audren-work` confere fixtures de dimensionamento do Horizon obtidas independentemente, ambos os layouts públicos de parâmetro, padding não inicializado, revisões não suportadas, aberturas aninhadas, buffers menores que o necessário, limpeza em falha, armazenamento de propriedade do chamador e tempo de vida do close. A implementação de dimensionamento também bateu com 360 conjuntos de parâmetro com seed executados pelo código de máquina original do Horizon nas REV1–REV15. A nota do jogo registra a reprodução com o heap original e a checagem de áudio.

## Listas de comandos por SDK

### Listas de partida incompletas não devem selecionar o layout de comandos

- **Sintoma:** nenhum som depois do boot; `audren: no DeviceSink (ID 0x13) in the command list` e um erro de comando malformado persistem mesmo quando as listas de comandos posteriores são válidas.
- **Causa:** a render thread do host pode gerar comandos antes do primeiro `RequestUpdate` do jogo. A primeira lista de Mario Kart 8 Deluxe terminava num terceiro comando zerado, então nenhum dos layouts de cabeçalho suportados conseguia percorrê-la. `capture` ainda assim travava o layout padrão e consumia a varredura única de sink. Cabeçalhos de SDK 6 posteriores eram lidos como cabeçalhos de SDK 8–20, e o DeviceSink da SDK 4 nunca era achado. Repetir só a detecção de cabeçalho ainda deixa a varredura de sink consumida.
- **Correção:** selecione o cabeçalho só quando a lista inteira é percorrida num layout suportado. Até lá, retorne um frame silencioso antes da varredura de sink; a primeira lista válida pode selecionar tanto o cabeçalho quanto seu sink. `t_audren_layout` reproduz a falha e confere PCM estéreo para as SDKs 4, 6, 16 e 21, com listas de partida válida primeiro, vazia e incompleta, e a primeira lista curta da SDK 21.
- **Encontrado em:** Mario Kart 8 Deluxe (SDK 4.4.0; [PR #331](https://github.com/seedds/nxnative/pull/331)).

### Listas de comandos posteriores malformadas e logging de histograma limitado

- **Sintoma:** o ASan aponta um stack-buffer-overflow em `capture` ao registrar o primeiro histograma completo de comandos; uma lista malformada também pode consumir a descoberta de sink ou retornar PCM antes de sua cauda inválida ser checada.
- **Causa:** uma lista válida curta selecionava o layout de cabeçalho sem rodar o histograma. Uma lista malformada posterior entrava na varredura do histograma, que parava no primeiro comando inválido mas ainda registrava uma string não inicializada ou adotava um sink do prefixo válido. Separadamente, o valor de retorno não truncado de `snprintf` acumulado avançava a próxima escrita além da string de 512 bytes.
- **Correção:** valide a lista de comandos inteira em toda captura antes de histograma, descoberta de sink ou saída de PCM. Mantenha a descoberta pendente para listas recusadas, inicialize a string do histograma e pare de acrescentar na última entrada completa que cabe.
- **Checagem:** `t_audren_layout` cobre comandos primeiro, do meio e de cauda malformados depois da seleção de layout, a recuperação para PCM estéreo válido nas SDKs 4/5/6/16/21, e 64.000 comandos sintéticos cujo histograma excede o buffer do log. O ASan reproduzia tanto a leitura da string não inicializada quanto a escrita de acréscimo truncado antes da correção.
- **Encontrado em:** listas de comandos sintéticas da [auditoria de análise estática da issue #313](https://github.com/seedds/nxnative/issues/313#issuecomment-6543080671); nenhuma falha de jogabilidade foi atribuída a esses defeitos de logging.

### O mix final é tirado dos buffers de mix do DeviceSink

- **Sintoma:** o processador de CPU descarta o mix final.
- **Causa:** `dsp::SendCommandBuffer`, `dsp::Wait` e `dsp::Signal` são stubs no build da aplicação, e DeviceSink (comando 0x13) é um no-op em `CommandListProcessor::Process`.
- **Correção:** pegue a saída depois de `Process` nos buffers de mix que o comando DeviceSink nomeia (layout em `hle_audren.c`).

### SDK 4: DeviceSink é o comando 0xf de 0x130 bytes

- **Sintoma:** nenhum som: `[RED] audren: no DeviceSink (ID 0x10) in the command list, 0 commands of 0x148 bytes`.
- **Causa:** a lista de comandos da SDK 4 tem o cabeçalho da SDK 6, mas seu DeviceSink (`CommandBuffer::GenerateDeviceSinkCommand`, `sdk+0x19e690`) é o ID 0xf com 0x130 bytes: nome do dispositivo +0x14, buffer de upsample +0x118, contagem de inputs +0x120, índices de input como bytes a partir de +0x124.
- **Correção:** `capture` pega o único comando de 0x130 bytes com 1 a 6 inputs quando não há um de 0x148 bytes (`g_sink4` em `hle_audren.c`).
- **Encontrado em:** Mario Kart 8 Deluxe (4.0.0, SDK 4.4.0; um boot).
- **Nota:** seu `AudioRenderSystem::Update` (`sdk+0x19b77c`) trava um `nn::os::MutexType` em +0x1d0, não o `SdkMutexType` da SDK 16 em +0x1f0; veja [O lock de update do renderizador (`update_lock`)](#o-lock-de-update-do-renderizador-update_lock).

### SDK 6: o layout de cabeçalho de comando DSP

- **Sintoma:** nenhum som; vermelho `audren: command 1 in the command list is malformed (magic 0 size 0)`.
- **Causa:** o cabeçalho de comando DSP da SDK 6 é +0x04 tamanho (s16), +0x06 ID do comando (s16), +0x10 habilitado, onde as SDKs 8–20 têm +0x04 habilitado, +0x05 ID, +0x06 tamanho (`CommandListProcessor::Process`, `sdk+0xaaa00`); seu DeviceSink é o ID 0x10.
- **Correção:** `capture` em `hle_audren.c` pega o layout cujo percurso acha o magic 0xcafebabe em todo comando da primeira lista válida; [listas de partida incompletas](#listas-de-partida-incompletas-nao-devem-selecionar-o-layout-de-comandos) deixam a detecção aberta.
- **Encontrado em:** Fire Emblem: Three Houses (SDK 6.5.0).

### SDK 5: uma lista de partida que percorre em dois layouts, e um DeviceSink de 0x138 bytes

- **Sintoma:** nenhum som: `[RED] audren: no DeviceSink (ID 0x13) in the command list, 0 commands of 0x148 bytes` e `[RED] audren: command 2 in the command list is malformed (offset 0x3e, …)`, com `first-frame commands 13, by command ID:  0×2`.
- **Causa:** a lista de comandos da SDK 5.4.0 tem o cabeçalho da SDK 6. Sua primeira lista tem dois comandos, o primeiro com ID 0x14 e 0x14 bytes, então também é percorrida no layout das SDKs 8–20 (o ID lido como o tamanho), e esse layout era tomado porque é tentado primeiro. Seu DeviceSink (`CommandBuffer::GenerateDeviceSinkCommand`, `sdk+0x233890`) é o da SDK 4 (ID 0xf, nome do dispositivo +0x14, buffer de upsample +0x118, contagem de inputs +0x120) mas com 0x138 bytes, com os índices de input como s16 a partir de +0x124.
- **Correção:** `capture` em `hle_audren.c` toma um layout de cabeçalho só de uma lista que percorre em exatamente um; uma lista que percorre em nenhum ou em vários é um frame silencioso. A varredura de sink pega o único comando de 0x130 bytes (índices s8) ou 0x138 bytes (índices s16, `g_sink5`) com 1 a 6 inputs. `t_audren_layout` cobre a SDK 5 e a primeira lista de Bastion.
- **Encontrado em:** Bastion (1.0.0, SDK 5.4.0).

### Cabeçalhos de comando desalinhados durante a sondagem de layout

- **Sintoma:** o UBSan aponta uma load de `uint32_t` desalinhada em `cmd_ok` enquanto `t_audren_layout` sonda listas das SDKs 4/6 (issue #409).
- **Causa:** interpretar o ID do comando como o tamanho de outro layout pode avançar o ponteiro candidato para fora do alinhamento de palavra. As loads de cabeçalho tipadas têm então comportamento indefinido em C embora o arm64 suporte a load de máquina. Um tamanho errado também podia levar o próximo cabeçalho para fora da lista gerada.
- **Correção:** `cmd_size`, `cmd_id` e a checagem de magic decodificam campos de fio multibyte com `memcpy`. `cmd_ok` verifica que o cabeçalho completo e o comando cabem antes de consumi-los; os diagnósticos de comando malformado não dereferenciam um cabeçalho recusado. Todos os layouts de SDK suportados usam os mesmos leitores, sem gate de jogo.
- **Checagem:** `t_audren_layout` confere cabeçalhos deliberadamente desalinhados, todos os comprimentos de cabeçalho/corpo truncados, tamanhos assinados inválidos e PCM estéreo para as SDKs 4/6/16/21, incluindo a recuperação de listas de partida incompletas. O processador embarcado da SDK 6 lê o byte habilitado em +0x10 antes de despachar qualquer comando, confirmando o mínimo de 0x11 bytes usado pelo parser. A supressão de alinhamento do UBSan dele foi removida.
- **Encontrado em:** as fixtures das SDKs 4/6 da auditoria de sanitizers, representando Mario Kart 8 Deluxe e Fire Emblem: Three Houses; nenhum crash de jogo foi atribuído a essa load indefinida.

### SDK 8: IDs de comando numerados de outro jeito

- **Sintoma:** nenhuma música nem efeitos sonoros, só o som dos vídeos (o log repete `audren: first-frame commands …` a cada frame e nunca imprime `DeviceSink inputs`).
- **Causa:** a SDK 8 numera os comandos DSP de outro jeito: sua lista de comandos vai 4×6, 7×24, 11×2, 16×1, 21×1 onde a da SDK 20 vai 7×6, 11×24, 15×2, 20×1, 25×1, e o comando de saída (DeviceSink, o mesmo layout de 0x148 bytes com nome de dispositivo `MainAudioOut`, seis inputs) é o 0x10, não o 0x13 da SDK 16. O host nunca o achava e tocava silêncio.
- **Correção:** quando o ID esperado falta na primeira lista de comandos completa, `capture` em `hle_audren.c` agora pega o único comando de 0x148 bytes com 1 a 6 inputs como o sink e registra o ID que adotou.
- **Encontrado em:** The Legend of Zelda: Link's Awakening (SDK 8.2.0).

### O layout `AudioRenderSystem` da SDK 20

- **Causa:** na SDK 20 os getters que o host usava (`GetExecutionMode`, `GetRenderingDevice`, `GetCommandBufferWorkBufferSize`) são inlined, `GenerateCommand` toma e devolve um `Span<std::byte>` (a lista gerada volta em x0 / x1), o dispositivo e o modo são palavras em +0x48 / +0x4c, o command buffer está em +0xf8 / +0x100, e `UpdateImpl` trava o mutex em +0x2d0 em vez de +0x1f0. O comando DeviceSink virou o ID 0x14 (a SDK 20 inseriu um comando antes dele) com o mesmo layout.
- **Correção:** `render_once` escolhe o layout pelo existir ou não a versão Span de `GenerateCommand` (`hle_audren.c`).
- **Encontrado em:** The Legend of Zelda: Breath of the Wild.
- **Nota:** a chamada da render thread toma outro caminho nesta SDK também: veja [O caminho da render thread pelo público `ExecuteAudioRendererRendering` (SDK 20)](#o-caminho-da-render-thread-pelo-publico-executeaudiorendererrendering-sdk-20); o lock de update em +0x2d0 está em [O lock de update do renderizador (`update_lock`)](#o-lock-de-update-do-renderizador-update_lock).

### Cabeçalho de comando DSP da SDK 21 sem magic

- **Sintoma:** nenhum som; vermelho `audren: command 0 in the command list is malformed (first word 0x181901 size 0)`.
- **Causa:** o cabeçalho de comando DSP da SDK 21 não tem magic: +0x00 habilitado (bit 0), +0x01 ID do comando, +0x02 tamanho (s16), +0x04 tempo estimado, +0x08 ID do nó (`CommandListProcessor::Process`, `sdk+0x24fd50`), e todo corpo de comando se moveu 8 bytes para frente. DeviceSink ainda é o ID 0x14 mas com 0x140 bytes: contagem de inputs +0x128, índices de input +0x12c, buffer de upsample +0x120 (escrito em `sdk+0x23a7b0`). `AudioRenderSystem` mantém os offsets da SDK 20 (lock de update +0x2d0).
- **Correção:** `capture` (`hle_audren.c`) tenta este layout quando nenhum percurso de magic funciona, limitado pelo comprimento da lista gerada. Suas primeiras listas têm 4 comandos ou menos, então o layout agora é escolhido na primeira lista não vazia.
- **Encontrado em:** Rhythm Heaven Groove (SDK 21.4.0, um boot).

## AudioOut e dispositivos

### Diagnósticos periódicos fora do callback do AudioUnit

- **Sintoma:** o RealtimeSanitizer aponta locking de stdio em `au_render` na sua fronteira de diagnóstico de 2.000 callbacks. Um escritor de log ocupado pode bloquear esse callback mesmo quando a fila de PCM dele está cheia.
- **Causa:** `LOG` pega o lock de stream de `stderr` e formata a mensagem na thread chamadora. O callback antes a chamava diretamente para suas estatísticas de contagem de frames, profundidade da fila e underruns.
- **Correção:** o callback publica um snapshot limitado para o watchdog de áudio existente, que copia relatórios prontos sob o lock do registro de saídas e os escreve depois de destravar. As cópias locais dele possuem todo argumento de log; um escritor lento não segura nem um ponteiro de saída nem o lock do registro. A caixa de correio de slot único usa posse release/acquire; um escritor ocupado deixa o callback acumulando as janelas seguintes em vez de esperar ou sobrescrever dados não lidos. Os relatórios mantêm o ID da saída, o total de callbacks, a faixa de frames, a profundidade média da fila e as contagens de underrun/zero-padding, com o comprimento real da janela quando várias janelas se fundem. O notificador de evento de liberação permanece independente dos diagnósticos, então um jogo dirigido por eventos pode continuar reenchendo o PCM enquanto o logging está bloqueado. O close para o produtor e pega o snapshot restante sob o mesmo lock do registro antes de liberar a saída.
- **Encontrado em:** a [auditoria sintética de áudio em tempo real da issue #313](https://github.com/seedds/nxnative/issues/313#issuecomment-6523656102). Nenhuma falha audível de jogo específico é atribuída a esse achado.
- **Checagem:** `t_audio_stats` chama o callback real com PCM sintético e intercepta o stdio da thread do callback, incluindo seu lock de stream. Com o escritor do watchdog deliberadamente bloqueado, cada buffer de PCM ainda dispara seu evento de liberação do convidado e é reenchido corretamente; relatórios na fila e fundidos preservam seus campos. O close drena um segundo snapshot pendente enquanto o watchdog segura só uma cópia local, que ele registra depois que a saída foi liberada. Estatísticas sozinhas não causam nenhum evento do convidado. A fixture não precisa de dispositivo de som nem de arquivos de jogo.

### A notificação de liberação do AudioOut pode dormir com trabalho pendente

- **Sintoma:** o PCM é consumido corretamente e seu buffer chega à fila de liberados, mas o evento de liberação do convidado nunca chega. O notificador está dormindo com `notify_pending=1` e `notify_armed=0`.
- **Causa:** o notificador limpava `notify_armed` com uma store somente-release antes de checar `notify_pending`. Um produtor podia publicar trabalho pendente e ver o valor armed antigo, deixando o notificador se acordar sozinho; sem adquirir a exchange daquele produtor, o notificador podia ler um estado pending obsoleto e dormir. O código gerado para Apple silicon produzia uma store-release seguida de acquire-PC loads, que permitem essa ordenação.
- **Correção:** desarme com uma exchange acquire-release. Se a exchange armed do produtor a precede, o notificador adquire a publicação pending. Caso contrário o produtor vê zero e publica, ou encontra o notificador já rearmado com seu próprio post do semaphore. Nenhuma mudança de timeout, polling, logging de callback ou cadência de áudio é necessária.
- **Encontrado em:** a fixture ASan/UBSan `t_audio_stats` da CI compartilhada, reproduzida localmente sem dispositivo de som nem jogo. Este é um mecanismo geral do AudioOut; nenhuma reprodução em jogabilidade é alegada.
- **Checagem:** os casos existentes de diagnósticos bloqueados permanecem intactos. A fixture também renderiza e recupera individualmente 100.000 buffers de PCM dirigidos por eventos, então liberações não podem ser escondidas pelo coalescing em bloco de notificações. O notificador antigo naturalmente encalha uma liberação neste caso; os diagnósticos distinguem a entrega de eventos, o conteúdo do PCM e a identidade do buffer devolvido sem retentar a espera que falhou.

### Parar um AudioOut libera seus buffers pendentes

- **Sintoma:** o FMOD espera pela liberação de buffers depois de parar um AudioOut, travando o jogo durante uma transição de cena.
- **Causa:** o host parava seu backend mas deixava na fila pendente buffers não tocados e tocados parcialmente, então nenhum callback podia liberá-los.
- **Correção:** depois de parar um backend iniciado, `au_release_pending` move os buffers pendentes para a fila de liberados em ordem, reseta a posição do buffer parcial e sinaliza o evento de liberação do cliente e o waiters do host. Chamar Stop numa saída já parada preserva os buffers enfileirados para seu próximo Start.
- **Encontrado em:** Pokémon Brilliant Diamond: reportado na 1.0.0 no [PR #190](https://github.com/seedds/nxnative/pull/190); a bring-up da 1.3.0 passou aquela transição antes desta correção.
- **Checagem:** `t_audio_spsc` cobre um buffer parcialmente consumido, o wraparound da fila, a notificação de liberação, Stop repetido e buffers enfileirados antes do Start, depois roda seu teste de estresse produtor/consumidor de 100.000 buffers.

### A duração padrão do buffer de E/S do RemoteIO

- **Sintoma:** estalos só no iOS.
- **Causa:** o RemoteIO pede tantos frames por callback quanto a duração do buffer de E/S da sessão de áudio, cerca de 21 ms por padrão (1024 frames a 48 kHz), enquanto o renderizador então mantinha 4 blocos × 5 ms = 20 ms em voo (12 agora: [Blocos em voo para a saída](#blocos-em-voo-para-a-saida-dm_audio_blocks)). Cada callback chegava 1,3 ms curto e era preenchido com zeros, cerca de 47 lacunas por segundo. Reproduzido no Mac forçando callbacks de 1024 frames (`DM_DBG_AU_FRAMES=1024` com `DM_AUDIO=1`: 1999 underruns em 2000 callbacks).
- **Correção:** `AVAudioSession` `setPreferredIOBufferDuration:0.005` (e 48 kHz) antes de o jogo abrir áudio e de novo ao voltar ao primeiro plano (`front_ios_audio_session` em `front_ios.m`); o dispositivo então faz callback com 240 frames e nada sofre underrun.
- **Checagem:** ao conferir um dispositivo novo, leia a linha `audio #N device callbacks` do host, normalmente reportada a cada 2000 callbacks: frames por callback, blocos na fila, underruns. Um escritor de diagnósticos ocupado pode fundir janelas; a linha declara a contagem real de callbacks delas.

### O buffer de E/S padrão do Mac

- **Sintoma:** cliques e dropouts no Mac, ouvidos como clipping; as linhas `audio #N device callbacks` do log mostram centenas de underruns por 2000 callbacks. Fechar uma saída também pode registrar `[RED] audio device buffer: ... (status 560947818)` (`!obj`).
- **Causa:** a saída padrão do Mac pede 512 frames de dispositivo por callback (10,7 ms a 48 kHz; 557 frames do stream de 48 kHz quando o dispositivo roda a 44,1 kHz), mais do que um jogo que mantém só alguns blocos pequenos na fila tem pronto toda vez. Unicorn Overlord nos alto-falantes de um MacBook Pro: 250–290 underruns em cada 2000 callbacks a 48 kHz (0,1–0,2 s de zeros), cerca de 1.000 a 44,1 kHz. Se um jogo sofre underrun depende de quanto ele enfileira: 13 Sentinels (2,8 blocos na fila, como os 3,0 de Unicorn Overlord, mas maiores) não tinha nenhum com 512 frames.
- **Correção:** como o buffer de sessão do iOS, o Mac define o buffer de dispositivo da sua unidade de saída para cerca de 5 ms de frames de dispositivo (`kAudioDevicePropertyBufferFrameSize`, 240 a 48 kHz, 221 a 44,1 kHz), dentro da faixa suportada pelo dispositivo (`au_buffer` em `hle_audio.c`). Ele reaplica isso quando a saída padrão muda e quando a taxa de amostragem de saída do mesmo dispositivo muda; só `CurrentDevice` não notifica uma mudança de taxa. Um buffer inalterado não é escrito de novo, já que uma escrita pode ela mesma notificar o listener de formato. Ambos os listeners são removidos antes de desinicializar ou descartar a unidade, incluindo uma abertura que falhou: o descarte de outro modo chama o listener depois que o dispositivo se foi (`!obj`). O log lê de volta a contagem real de frames. `DM_DBG_AU_FRAMES=N` pede N frames em vez disso.
- **Encontrado em:** Unicorn Overlord (lançamento asiático 1.05 no Mac).
- **Checagem:** o [PR #262](https://github.com/seedds/nxnative/pull/262) reporta Unicorn Overlord a 240 frames: 0 underruns em 10.000 callbacks; 13 Sentinels fica em 0. Uma sonda Core Audio silenciosa num Mac mini reproduziu o erro de descarte e o buffer obsoleto depois de uma mudança de taxa, depois verificou as correções: a saída padrão alternou entre USB a 48 kHz e alto-falantes a 44,1 kHz com os callbacks continuando, e os mesmos alto-falantes a 44,1 / 96 / 48 kHz usaram 221 / 480 / 240 frames. As taxas originais de saída padrão e de dispositivo foram restauradas. `make -C dm/host/tests GAME=<id> audio-device` confere a configuração, as notificações de dispositivo e de taxa, a filtragem de escopo, os limites de buffer, as notificações síncronas, o override de diagnóstico, dispositivos ausentes, o close e a limpeza de listeners na abertura que falha sem hardware de áudio.
- **Em aberto:** a saída HDMI listada não foi retida como padrão do sistema durante a sonda, então mudar para ela continua não validado. A sonda verifica a reprodução silenciosa do host; a escuta em jogabilidade depois dessas mudanças de listeners não foi repetida.

### A chave ring / silent silenciava o jogo no iOS

- **Sintoma:** um jogo fica mudo num iPhone cuja chave ring / silent está no silêncio, embora o volume esteja alto; um app de jogo ou vídeo toca apesar da chave.
- **Causa:** a categoria da sessão de áudio era `AVAudioSessionCategorySoloAmbient`, que o iOS silencia com a chave.
- **Correção:** `front_ios_audio_session` em `front_ios.m` define `AVAudioSessionCategoryPlayback`: a chave não silencia mais o jogo, e ele ainda interrompe o áudio de outros apps, como `SoloAmbient` fazia. O host ainda suspende sua saída quando o app vai para segundo plano (nenhum modo de áudio em segundo plano é declarado).
- **Encontrado em:** The Legend of Zelda: Breath of the Wild (iPhone: reportado pelo jogador); também a [issue #155](https://github.com/seedds/nxnative/issues/155), cujo comentarista tem de alternar a chave para ouvir qualquer jogo.
- **Checagem:** questão em aberto, não executado num dispositivo: com a chave no silêncio, o som do jogo deve tocar; a linha `audio session:` do log mostra que a sessão subiu.

### Interrupções que param os callbacks do RemoteIO

- **Sintoma:** o som continua mudo depois de Siri, um alarme, uma ligação ou o áudio de outro app; um jogo cujo thread de áudio espera seus buffers de volta (a saída só-poll do Wwise, o FMOD) então espera para sempre.
- **Causa:** o iOS para de chamar a unidade RemoteIO de volta sem pará-la, então o host ainda acha que a saída toca.
- **Correção:** três camadas cuidam disso:
  - `front_ios.m` observa `AVAudioSessionInterruptionNotification` (began → o sink silencioso entra em ação, ended → sessão reerguida e retomada; ficar ativo também encerra uma interrupção cujo "ended" nunca chegou);
  - `front_ios.m` também observa `AVAudioSessionMediaServicesWereResetNotification` (`dm_audio_reset` recria toda AudioUnit);
  - um watchdog em `hle_audio.c` troca qualquer saída iniciada cujo dispositivo não faz callback há 200 ms em primeiro plano pelo sink silencioso, retentando a AudioUnit depois de 1, 2, 4, 8 s. Um start que falha (sessão ainda retida por uma ligação) recorre do mesmo jeito em vez de abortar.
- **Checagem:** o log mostra `audio session: interrupted`, `the device has not called back for N ms` e `AudioUnit restarted`. O cenário 3 de `tests/t_audio.c` (em `dm/host`) para uma AudioUnit tocando pelas costas do host.

### Construção da AudioUnit antes da publicação da saída

- **Sintoma:** o TSan aponta `au_create` escrevendo `a->au` enquanto `au_watchdog` o lê sob `g_aos_lock` quando uma segunda saída real abre (issue #410).
- **Causa:** `au_open_at` punha a saída no registro de tempo de vida/watchdog antes de juntar seu helper de construção da AudioUnit. O lock do registro não ordenava as escritas do helper.
- **Correção:** construa e junte enquanto a saída é privada, depois publique sob `g_aos_lock`. Suspend, resume, reset e leitores do watchdog só descobrem a saída concluída. A construção continua fora daquele lock porque juntar o helper pode ceder o core do convidado.
- **Checagem:** `t_audio_device` afirma de dentro da criação da AudioUnit que a saída pendente está ausente do registro; falhava antes da correção. `t_audio` exercita tempos de vida de saídas reais e a recuperação do watchdog sob TSan com a supressão de `au_create` removida.
- **Encontrado em:** testes de unidade do host; nenhuma falha de jogo específico foi atribuída a essa corrida.

### Áudio através de uma ida e volta ao segundo plano no Mac

`DM_AUDIO=1 DM_AUDIO_MUTE=1` pega o caminho real do Core Audio com a saída silenciada, e `DM_OE_SCRIPT="bg=1@60,bg=0@66"` roda a mesma sequência que os callbacks de ciclo de vida do iOS (foco, tomada de áudio, congelamento do relógio do convidado, suspensão gráfica). Envie `SIGSTOP` / `SIGCONT` a `dmhost` entre os dois para imitar o iOS suspendendo o processo. O sink silencioso que entra em ação enquanto em segundo plano não repõe o tempo em que o processo ficou suspenso (ele costumava puxar segundos de uma vez e registrar centenas de underruns).

### Várias saídas ao mesmo tempo (`audio #N`)

- **Sintoma:** números combinados enganam: uma saída totalmente alimentada mais uma alimentada por ninguém parecem "cada callback alternado sofre underrun, 2,0 blocos na fila".
- **Causa:** um jogo pode abrir várias saídas. Engage abre um AudioOut do Wwise (`main+0x177d924`, sem evento de liberação, o jogo faz polling); no celular ele abria um segundo mais tarde, e as execuções no Mac nunca abriam. O FMOD da Unity também tem uma saída (`main+0x4aec0c` / `0x4aec4c`).
- **Correção:** o host registra cada saída como `audio #N`: a abertura com seu call site, Start / Stop / Close (os primeiros 32 por saída), e as estatísticas de callback normalmente a cada 2000 callbacks daquela saída, através do [watchdog](#diagnosticos-periodicos-fora-do-callback-do-audiounit).
- **Encontrado em:** Fire Emblem Engage.

### Conferir áudio sem escutar

`DM_AUDIO_DUMP=file` grava o PCM que o host reproduz (16-bit, 48 kHz, estéreo). RMS por segundo, percentual de clipping, planicidade espectral (música ≈ 0,1–0,5, ruído branco ≈ 0,85) e os picos de FFT mais fortes (eles devem cair em notas musicais) distinguem música real de ruído ou silêncio.

### Volume do AudioOut mantido por saída

- **Sintoma:** uma leitura nula em `sdk+0x1f2314` quando o primeiro vídeo começa (o fundo do menu do título, logo depois da primeira pressão), em `nn::audio::SetAudioOutVolume`, chamada de `main+0x1c4fbc`.
- **Causa:** a chamada é no AudioOut que o jogo abre para o som AAC do vídeo (`OpenDefaultAudioOut` sem evento, `main+0x1c48c0`). A versão da SDK chama o método virtual da sessão audout, e o host nunca criou a sessão. O jogo define o volume a cada frame enquanto um vídeo toca.
- **Correção:** o host mantém o volume por saída e escala o PCM16 que reproduz, com saturação; `GetAudioOutVolume` o lê de volta (`hle_audio.c`).
- **Encontrado em:** STEINS;GATE RE:BOOT (SDK 20.5.17, engine ADV da M2; a única chamada de que precisava).

### Contagem de buffers do AudioOut pela fila do host

- **Sintoma:** uma leitura nula em `audio::GetAudioOutBufferCount` na thread de wakeup do sink do Wwise (`sdk+0x1e7ed0`, de `main+0x30faae8`).
- **Causa:** depois de todo evento de liberação o Wwise pergunta quantos buffers ainda estão na fila e marca o sink como faminto em 0; a versão da SDK lia a sessão audout que o host nunca criou.
- **Correção:** ele responde pela fila do host: acrescentados e ainda não tocados até o fim (`hle_audio.c`).
- **Encontrado em:** Pokémon Legends: Z-A (19.3.11; um boot).

### Volume e nomes de saída do dispositivo de áudio

- **Sintoma:** um abort em `sm::Initialize` sob `audio::GetAudioDeviceOutputVolume` (o thread do jogo, `main+0xee8edc`).
- **Correção:** o volume que `SetAudioDeviceOutputVolume` define agora é mantido e lido de volta (`hle_audren.c`). `audio::GetAudioOutName` também é hookado.
- **Encontrado em:** DRAGON QUEST VII Reimagined (SDK 19.3.1, Unreal Engine 4.27: `GetAudioDeviceOutputVolume`); 13 Sentinels: Aegis Rim (SDK 11: `GetAudioOutName`).
- **Nota:** SDKs antigas nomeiam algumas chamadas de dispositivo de outro jeito (`ListAudioDeviceName` / `GetActiveAudioDeviceName`, `OpenDefaultAudioOut` na SDK 12; [Nomes de SDK antigas para as mesmas chamadas](services.md#older-sdk-names-for-the-same-calls)), e Super Mario Odyssey (SDK 7) abortou em `audio::AcquireAudioDeviceSwitchNotification` ([Chamadas de SDK que abrem um serviço de sistema através do sm](services.md#sdk-calls-that-open-a-system-service-through-sm)).

### Sem dispositivos de entrada de áudio

- **Sintoma:** o driver de áudio do SDL3 lista os dispositivos de entrada (AudioIn) quando seu áudio inicia. `[RED] sf: the game connects to service audren:u itself`, depois `nn::diag abort: Result 0xe15` sob `audio::AcquireAudioDeviceNotificationForInput` (`sdk+0x5633c`).
- **Causa:** o host não tem microfone. A notificação de mudança de dispositivo de entrada passa pelo serviço de dispositivos de áudio, como a de saída que o host responde.
- **Correção:** `ListAudioIns` não reporta nenhum, ambas as aberturas falham, e as chamadas que precisam de um AudioIn aberto respondem sem tocar no objeto do jogo (`hle_audio.c`). `AcquireAudioDeviceNotificationForInput` vincula um evento que nunca é sinalizado e retorna 0x499 quando adquirido de novo, como a SDK faz com uma flag sua (SDK 13.3.0 `sdk+0x56300`: `+0x851`, o `+0x852` da saída; `hle_audren.c`).
- **Encontrado em:** Absolum (SDK 18.3.2, FNA em .NET NativeAOT com SDL3; um boot cada); Portal 2 (SDK 13.3.0: seu thread de áudio no início, `main+0x19f37c`).

### Dispositivos de entrada virtuais listados por `auddmg:u` (SDK 22)

- **Sintoma:** um jogo de Switch 2 aborta no boot com `game called nn::diag abort: Result 0xa99` em `sdk+0x1f61ac` (sdk `1643a838`), sob `nn::audio::ListVirtualInputDevices`, seja qual for o ajuste de microfone do jogo.
- **Causa:** a API de stream de entrada da SDK 22 (a substituta da Switch 2 para o AudioIn) lista seus dispositivos por um gerenciador de dispositivos que o próprio `ListVirtualInputDevices` da SDK inicializa primeiro; aquilo conecta a `auddmg:u`, que o host não atende, e a SDK aborta na conexão que falha. O gerenciador de microfone de um jogo pode listar os dispositivos na construção, antes de qualquer abertura.
- **Correção:** `hle_audio.c` hooka `ListVirtualInputDevices` (`h_ai_ListVirtual`): 0 dispositivos, como `ListAudioIns` ([Sem dispositivos de entrada de áudio](#sem-dispositivos-de-entrada-de-audio)), registrado uma vez com seu call site. 0 também é o que a SDK retorna para uma lista de comprimento zero, e um jogo confere a contagem antes de `OpenInputStream`. `GetTrueRawAudioInputDevice` não precisa de hook: ele copia um nome constante.
- **Encontrado em:** [The Legend of Zelda: Ocarina of Time](../../dm/games/0400486024DEC000/README.md) (SDK 22.2.8, Android): seu gerenciador de microfone (construtor `main+0xed1440`, abertura `main+0xf4860c`, lista `main+0xf48128`) pula a abertura sem dispositivo (`main+0xf4812c`); o boot continua (conferido no celular).

### `OpenAudioOut` sem evento de liberação (SDK 22)

- **Sintoma:** `game called nn::diag abort: Result 0xe15, call site sdk+0x1f8704`, isto é, dentro de `nn::audio::CreateAudioOutManagerByHipc+0xb4`, antes de qualquer dispositivo de áudio aberto.
- **Causa:** a SDK 22 chama **`nn::audio::OpenAudioOut(AudioOut*, const char*, const AudioOutParameter&)`** (`sdk+0x1f7914`, o caminho de falha de `nn::audio::OpenAudioOut(AudioOut*, const char*, const AudioOutParameter&)`) — a overload sem o `SystemEvent` que o host implementava (`_ZN2nn5audio12OpenAudioOutEPNS0_8AudioOutEPKcRKNS0_17AudioOutParameterEPNS_2os11SystemEventE`). Sem hook, o caminho de IPC da própria SDK rodava e o serviço audout não está lá.
- **Correção:** `hle_audio.c` ganhou o hook de 4 argumentos, que abre pelo mesmo `au_open_at` e deixa o evento de liberação NULL (`au_open_at(g, NULL, name, param, __builtin_return_address(0))`), então a saída faz polling dos buffers liberados. O log então lê `OpenAudioOut … , no release event (the game polls)`.
- **Encontrado em:** Well Dweller (SDK 22.2.0); o primeiro `AudioOut` do jogo é a forma "DeviceOut", o segundo é sua saída de música de 48 kHz.

## Codecs

### Decodificadores Opus de hardware construídos sobre os decodificadores de software da SDK

- **Sintoma:** um abort em `sm::Initialize` sob a interface C do decodificador Opus de hardware; para o multistream, sob `nn::codec::GetHardwareOpusMultiStreamDecoderWorkBufferSize(int, int, int, int, int)` (em `nnMain`, streams Opus da CRI). Um abort em `sf::hipc::InitializeHipcServiceResolution` sob `codec::GetHardwareOpusDecoderWorkBufferSize(int, int)` (`main+0x31f8e8`, no início de `nnMain`). Um jogo que constrói `HardwareOpusMultiStreamDecoder` com o construtor importado e depois o chama pela vtable.
- **Causa:** os decodificadores de hardware são um serviço. Os 12 slots de vtable da classe `HardwareOpusMultiStreamDecoder` batem com o `OpusMultiStreamDecoder` de software da SDK um a um. A SDK 11 tem uma forma de tamanho de work buffer sem o argumento de opção; o host só tinha a de três argumentos.
- **Correção** (`hle_codec.c`):
  - as classes: o hook do construtor constrói o decodificador de software no lugar (0x38 dos 0x48 bytes), como para o `HardwareOpusDecoder` de stream único; `GetHardwareOpusMultiStreamDecoderWorkBufferSize` devolve o tamanho de software. Os dois destrutores são um `ret` puro.
  - a interface C de stream único (`GetHardwareOpusDecoderWorkBufferSize`, `InitializeHardwareOpusDecoder`, `DecodeOpusInterleavedWithHardware`, …) é encaminhada à interface C do `OpusDecoder` de software do `sdk` com um estado de decodificador mantido pelo host por objeto do jogo.
  - a interface C multistream, incluindo a overload da SDK 12+ com um argumento de opção, vai à interface C do `OpusMultiStreamDecoder` de software do `sdk`, com um estado mantido pelo host por objeto do jogo como no decodificador de stream único. `GetHardwareOpusMultiStreamDecoderProcessingTime` reporta 0.
  - o `GetHardwareOpusDecoderWorkBufferSize` de dois argumentos é hookado ao lado do de três.
- **Encontrado em:** Hyrule Warriors: Age of Calamity (SDK 9.6: só a classe `HardwareOpusMultiStreamDecoder` e o tamanho de quatro argumentos); The Legend of Zelda: Tears of the Kingdom (1.4.3, a interface C de stream único); Big Brain Academy: Brain vs. Brain (SDK 11.4.3, o tamanho de dois argumentos); Unicorn Overlord (SDK 16.2.4, a interface C multistream; um boot).

## Timing e catch-up

### Blocos em voo para a saída (`DM_AUDIO_BLOCKS`)

- **Sintoma:** dropouts curtos na jogabilidade com saída real; as linhas `audio #N device callbacks` da saída do renderizador (`AudioRenderer`) mostram underruns, e os próprios renders da render thread levam muito menos que 5 ms em média.
- **Causa:** a render thread renderiza um bloco só quando a saída devolve um, então os blocos em voo são toda a folga contra uma render thread que chega atrasada. Com 4 (20 ms) e o buffer de 384 frames do AAudio, uma render thread 20–33 ms atrasada (um thread parado ou preemptido, um page fault, um catch-up render segurando o lock de render) deixava o callback do dispositivo sem nada para tocar.
- **Correção:** 12 blocos (60 ms) por padrão; `DM_AUDIO_BLOCKS=N` mantém 2 a 32. Cada bloco acrescenta 5 ms de latência. A render thread renderiza os blocos livres na partida e depois de se atrasar; desde a entrada abaixo, cada um daqueles frames de backlog primeiro espera brevemente pelo update do jogo. A fila de catch-up (4 frames) é separada e inalterada.
- **Encontrado em:** [The Legend of Zelda: Ocarina of Time](../../dm/games/0400486024DEC000/README.md) (Switch 2 Edition) num POCO F8 Pro (Android): underruns do host com a render thread 20–33 ms atrasada na jogabilidade. O padrão de 12 blocos e o relatório abaixo ainda não foram conferidos no celular.
- **Checagem:** a render thread reporta a cada 2000 blocos que entrega à saída (10 s): `audren: last 2000 blocks (N in flight): render thread … renders, mean / max …, lock wait max …; catch-up … renders, mean / max …, lock wait max …; a released block waited mean / max … for the render thread (max … after a sleep); render thread … major / … minor faults, … involuntary switches`. Os tempos de render contam desde antes do lock de render, separados por chamador: a render thread, e o catch-up frame que o update de um jogo renderiza na própria thread dele. A espera vai do momento em que a saída liberou um bloco (carimbado no callback dele, que continua sem lock) até a render thread pegá-lo; depois de um sono é o atraso de despertada do thread, caso contrário o thread ainda estava ocupado. Faults e switches são do próprio thread de render na janela (`getrusage(RUSAGE_THREAD)`, Linux e Android; -1 alhures). Uma espera próxima de blocos em voo × 5 ms significa que a folga acabou; major faults ou muitos switches involuntários apontam para pressão de memória ou a prioridade do thread, não o custo de render.

### O som falha enquanto os contadores da saída estão limpos

- **Sintoma:** o som congela ou falha dentro e fora na jogabilidade com saída real, enquanto cada linha `audio #N device callbacks` da saída do renderizador mostra 0 underruns, 0 ms de zero-padding e 0 underruns do stream do dispositivo, e as linhas `audren: last 2000 blocks` mostram renders curtos.
- **Causa (em aberto):** aqueles contadores só mostram que os frames chegaram ao alto-falante. Dentro de um frame, uma voz que o jogo não reenchia a tempo toca silêncio. Dois mecanismos do host podem causar isso. (1) Depois de uma despertada atrasada a render thread renderizava seu backlog sem pausa: até 9 frames (46 ms em Ocarina of Time) num mesmo estado do jogo, sem RequestUpdate no meio, enquanto o DSP de áudio renderiza um frame a cada 5 ms e o jogo atualiza no meio. (2) A render thread e os threads de áudio do jogo rodam no mesmo valor nice que os outros 60 threads do jogo. Os alimentadores do próprio jogo também podem passar fome (leituras de stream durante tempestades de page fault, `aal::LowPrioWorkerThread` na prioridade 45).
- **Correção:** (`hle_audren.c`) um frame de backlog espera até `DM_AUDREN_GATE_MS` (padrão 2 ms; `0` é o comportamento antigo) pelo update do jogo depois do frame anterior, enquanto 3 ou mais blocos ainda estão na fila. A render thread roda em nice `DM_AUDREN_NICE` (padrão -16, Linux / Android). `DM_AUDREN_UPDATE_NICE=<n>` (desligado por padrão) define o valor nice do thread que chama RequestUpdate.
- **Encontrado em:** [The Legend of Zelda: Ocarina of Time](../../dm/games/0400486024DEC000/README.md) (Switch 2 Edition) num POCO F8 Pro; ainda não conferido no celular.
- **Checagem:** cada relatório acrescenta três linhas. `audren game updates:` dá o intervalo de updates e o atraso do sinal até o update. Seu histograma de frames renderizados entre dois updates tem um máximo que são ms de áudio sem update do jogo. Ele também mostra esperas da fila de catch-up e frames de catch-up descartados (5 ms pulados cada), renders de backlog e acertos / timeouts do gate, e a espera do lock de update da SDK. `audren output signal:` dá frames zerados, lacunas de zeros curtas, quedas de 12 dB e comandos de fonte de dados (canais de voz) por frame. Suas quedas curtas são divididas por o último update do jogo ter mais de 20 ms. `audren threads over the window:` dá o tempo de execução, a espera na fila de execução e os major faults do thread de RequestUpdate e dos threads `DM_AUDREN_WATCH` (Android).

### Ao menos um frame renderizado entre dois updates

- **Sintoma:** o light limiter derrubava num ponteiro de estado nulo (`ApplyLightLimiterEffect`, `sdk+0x228494`).
- **Causa:** o estado de parâmetro "needs init" de um efeito é definido só no update em que o jogo o marca novo; o update seguinte copia o valor do jogo por cima. Mario atualiza duas vezes seguidas na inicialização; com um dispositivo de áudio real o primeiro frame veio depois de ambas.
- **Correção:** o hook de `AudioRenderSystem::Update` renderiza um catch-up frame primeiro quando nada foi renderizado desde o último update.
- **Encontrado em:** Super Mario Bros. Wonder.
- **Checagem:** `DM_AUDREN_TRACE=1` registra cada update com o número de frames renderizados desde o anterior.

### Um driver de som que nunca espera no evento do renderizador

- **Sintoma:** o som roda cerca de 50 vezes mais rápido que a saída; o loop roda 11.000 updates por segundo num core.
- **Causa:** o `SQEX::Sd::Driver::Core::CoreAudioOut` da Square Enix (seu loop em `main+0x442c5c0`) acrescenta um buffer de wave, mixa o próximo bloco na mesma thread, chama `RequestUpdate` e pega o próximo buffer liberado. Ele espera no evento do renderizador só quando nenhum buffer é liberado. Todo update ali acha que nenhum frame foi renderizado desde o anterior e renderiza um catch-up frame, que libera o buffer recém-acrescentado, então o loop nunca espera. Quando uma fila de catch-up cheia descartava o frame, o loop corria sem freio.
- **Correção:** um update com a fila cheia espera (no máximo 50 ms) a render thread tocar um frame na fila, então os catch-up frames mantêm o tempo real (`hle_audren.c`). A render thread então só toca frames na fila, e os updates do driver renderizam todo frame. Um frame na fila não sinaliza o evento do renderizador (a SDK o sinaliza em cada render): sinalizado, um jogo comum acordava, atualizava sem nada renderizado e renderizava o próximo catch-up frame, então todo frame virava um (Big Brain Academy: Brain vs. Brain, cerca de 38.000 catch-up renders em vez de 37).
- **Encontrado em:** DRAGON QUEST XI S: Echoes of an Elusive Age – Definitive Edition.
- **Checagem:** `audren: rendered 12000 frames, N of them not silent` mostra se o som de um jogo chega à saída.
