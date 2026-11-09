# 03 — Bring-up do host

## O loop

```bash
python3 dm/tools/patch_nso.py $W/data/v0/ExeFS/main $W/build/patched_v0 main   # and sdk
make -C dm/host GAME=<title ID> dmhost
$W/run/boot.sh <log name> [seconds] [VAR=value ...]
```

O `boot.sh` executa `dmhost <patched dir>` com `DM_ROMFS`, `DM_SAVE`, `DM_TEMP`, `DM_MSL_DIR` definidos, sob um `alarm` do perl (uma execução que atinge o limite de tempo termina com `CRASH sig=14` — isso é o alarm, não um crash). Os logs vão para `run/<name>.log`. Logs podem conter bytes não-UTF-8: use `grep -a` / `LC_ALL=C` ao filtrar.

## Lendo o log

| Marcador | Significado |
|---|---|
| `>>> nnosInitialize`, `>>> nnMain` | Estágios do boot atingidos |
| `[RED] …` | Algo não implementado ou errado, impresso uma vez por tipo. Colete-os com `grep -aF "[RED]" log \| sort \| uniq -c` depois de cada execução |
| `UNIMPL <name>` | Um stub forçado de NVN / SDK foi chamado |
| `[nvn] frame N: … \| name=count …` | Contagens de chamadas NVN por frame (chamadas feitas na thread do frame; command buffers gravados em outro lugar são reproduzidos depois) |
| `[longframe]` | Um frame longo, com a divisão por submit / GPU / pipeline / upload |
| `memory up to frame N: resident … GPU allocated …` | Relatório de memória (RSS, tamanho alocado no Metal). Impresso quando a memória se move; crescimento constante aqui é um vazamento. Um watchdog encerra aos 24 GB. Também: os memory pools e quantos estão residentes (os pools entram no conjunto de residência no primeiro uso pela GPU), buffers de pools finalizados ainda vivos, a tabela de texturas com armazenamento compartilhado, e no iOS `headroom to the system memory limit` (`os_proc_available_memory`, o quão perto o app está de ser morto por memória) |
| `CRASH sig=… pc=… lr=…` + registradores + `bt:` + `stack scan` + despejo de threads | Relatório de crash: veja [05](05-debugging.md) |
| `thread 0x10xx 'name' … waits:<state> addr=…` | Despejo de threads: no que cada thread convidada espera (`address` = address arbiter, `mutex` = mutex com detentor, `condvar`, `sync object`, `running/not waiting` = executando); uma thread esperando num SVC também ganha uma linha `guest stack:`, as palavras na sua pilha convidada que apontam para dentro de um módulo (seus chamadores quando o código não mantém frame pointers) e x19–x28 |

Switches úteis: `DM_FS=1` (aberturas de arquivo, e sondas de não encontrado), `DM_SVC=1` (SVCs de memória e outras syscalls), `DM_THR=1` (SVCs de thread), `DM_WATCH=<s>` (despeja todas as threads depois de s segundos), `DM_WATCH_STALL=<s>` (despeja quando o loop de frames para de progredir).

## Catálogo de falhas

As falhas encontradas até agora são arquivadas por subsistema, uma entrada por mecanismo (sintoma, causa, correção, os jogos onde foi encontrada): [reescritor](../reference/rewriter.md), [memória e espaço de endereço](../reference/memory.md), [threads](../reference/threads.md), [serviços e chamadas de SDK](../reference/services.md), [vídeos](../reference/movies.md), [áudio](../reference/audio.md), [frame rate](../reference/frame-rate.md), [objetos NVN](../reference/nvn-objects.md) e [estado de jogo](../reference/game-state.md); gráficos em [renderização](../reference/rendering.md) e [shaders](../reference/shaders.md). A maioria das falhas de um título novo é uma delas numa forma nova, então procure no [índice de sintomas](symptoms.md) pela asserção, pelo texto `[RED]`, pelo ponto do abort ou pelo resultado visível primeiro, depois leia a entrada e as notas dos jogos que ela nomeia.

Uma descoberta nova vai para um só lugar: um mecanismo geral como entrada na sua referência (com uma linha no índice de sintomas), um fato sobre um jogo na nota desse jogo (`dm/games/<id>/README.md`).
