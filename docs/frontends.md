# Frontends

Um app gráfico que faz o pack de jogos, mantém uma biblioteca deles ou os inicia é um projeto próprio, construído sobre o nxnative. [NX2Apple](https://github.com/LNPANDY/NX2Apple), [NX Play](https://github.com/varyform/nx-native-wrapper) e [NxNative Launcher](https://github.com/rennan101/nxnative_Launcher) são apps assim. Esta página lista no que um app assim pode se apoiar: como ele executa um build e o acompanha, o que um app compilado diz sobre as configurações do seu jogo, e como ele inicia um jogo com elas. Nada disso exige mudar o nxnative; quando um frontend precisa de mais, a peça que ele precisa é uma issue ou um PR pequeno aqui.

- [Compilação: o script de NSP](#compilacao-o-script-de-nsp)
- [A lista de configurações](#a-lista-de-configuracoes)
- [Iniciar um jogo](#iniciar-um-jogo)
- [O que um frontend ainda não pode ver](#o-que-um-frontend-ainda-nao-pode-ver)

## Compilação: o script de NSP

`scripts/build_ipa_from_nsp.py <NSP folder>` compila um jogo: um IPA por padrão, um app para Mac com `--platform mac-app`, no caminho que `-o` nomeia. Suas opções estão em [O script de packing em detalhe](development.md#the-packing-script-in-detail) e em `--help`.

Com `--progress json` ele não pergunta nada e escreve um objeto JSON por linha no stdout. Todo objeto tem `ev` (o evento) e `t` (tempo Unix, segundos):

| `ev` | Campos | Quando |
|---|---|---|
| `stage` | `k`, `n` (estágio k de n), `name`, `step` | um estágio começa; `n` pode crescer durante o build (o heap check de um profile provisório) |
| `step` | `k`, `name`, `step` | um novo passo dentro do estágio; sua contagem recomeça |
| `progress` | `k`, `name`, `step`, `done`, `total`, `unit` (`''`, `'B'` para bytes, `'files'`, …), `est` (o total é uma estimativa), `note`, `frac` (de 0 a 0.99, ou `null` quando o trabalho não pode ser contado), `eta` (segundos ou `null`), `elapsed` | a contagem muda; no máximo a cada 0,5 s enquanto só a contagem se move |
| `stage_end` | `k`, `name`, `ok`, `summary`, `elapsed` | um estágio termina |
| `shaders_stale` | `reason` (`'chain'`: bibliotecas de outra translation chain `DMSH_CHAIN`; `'inputs'`: os inputs de shaders do profile diferem do stamp da biblioteca `inputs.tsv`), `old` (as pastas da chain `b-<V>` ou o digest do stamp), `new` (`b-<V>` atual ou o digest atual), `count` (bibliotecas base agendadas: uma por shader key atual e alvo selecionado, incluindo restores de cache) | uma vez por passo de shaders, antes de recompilar após uma mudança de chain ou de profile; recompilas de chain interrompidas ainda o reportam até os alvos selecionados terem bibliotecas com stamp. Um passo que reutiliza suas bibliotecas, ou que não tem nenhuma em cache para seus alvos, não envia. Um `say` com a mesma novidade vem em seguida |
| `say` | `msg` | uma mensagem; uma que começa com `[RED] ` é um erro |

O código de saída é 0 para um build concluído, 1 para um build que parou por um erro (seu último `say` diz por quê, começando com `[RED] `; um erro inesperado também imprime seu traceback no stderr), 2 para argumentos inválidos de linha de comando, e 130 quando interrompido. A análise dos argumentos acontece antes de o modo JSON começar: um erro de argumento escreve seu usage e o erro no stderr e não emite eventos JSON. Um frontend deve checar o status de saída do processo e capturar o stderr além do stream JSON. A saída completa de cada ferramenta vai para `<NSP folder>/<title ID>_build/logs/`, não para o stdout. A pasta de build `<title ID>_build/` guarda o que o próximo build pode reutilizar, então um segundo build após uma mudança leva uma fração do primeiro. Um frontend que guarda o build de cada jogo em outro lugar passa `--work-dir <pasta>`: essa pasta é a pasta de build, usada como dada (o script não acrescenta `<title ID>_build`), então o frontend a nomeia por jogo; os logs ficam em sua `logs/`, e o profile provisório de um jogo que não tem um fica em `profile/`, de modo que um frontend que reutiliza a pasta para um jogo mantém seu profile (um de uma execução anterior em `<NSP folder>/<title ID>_build/profile` é copiado para lá uma vez, e fica lá). Sem `-o`, o IPA ou o app para Mac vai para a pasta que o contém, então use `-o` para colocá-lo em outro lugar.

## A lista de configurações

Um app compilado carrega as configurações que seu jogo suporta, como a tela de configurações do app de iPhone as mostra ([Configurações](settings.md)): `dm/launcher.json` no app dentro de um IPA, `Contents/Resources/dm/launcher.json` em um app para Mac. A lista do app para Mac deixa de fora as configurações que só o app de iPhone lê (o contador de frame rate, o feedback tátil). `dm/games/settings.py` a escreve a partir de `dm/games/settings.tsv`, cujo cabeçalho descreve as linhas. A lista de Breath of the Wild para Mac, resumida:

```json
{"schema": 1, "game": "01007EF00011E000", "settings": [
 {"env": "DM_FPS_TARGET", "kind": "choice", "section": "Game", "label": "Frame rate", "note": "…", "default": "60",
  "options": [["30", "30 fps"], ["60", "60 fps"]], "level": "basic"},
 {"env": "DM_RENDER_SCALE", "kind": "range", "section": "Game", "label": "Render scale", "note": "…", "default": "1.0", "min": 0.5, "max": 1.0, "step": 0.05},
 {"env": "DM_FPS_HOLD", "kind": "toggle", "section": "Performance", "label": "Hold frames when late", "note": "…", "default": "on", "on": "on", "off": "off"}
]}
```

- `env`: a switch de ambiente que a configuração define. `kind`: `choice` (uma de `options`, cada uma `[value, label]`), `range` (um número de `min` a `max` em passos de `step`) ou `toggle` (`on` ou `off`, os valores que a switch aceita).
- `section`: o cabeçalho sob o qual a linha fica, hoje `Game`, `Controls` ou `Performance`. As seções vêm na ordem de suas primeiras linhas, e as linhas, na ordem em que devem ser mostradas. Uma seção nova pode aparecer no schema 1: um leitor desenha uma seção que não conhece como as outras, sob seu nome, como faz o app de iPhone.
- `default`: o valor para a plataforma deste app ([Defaults testados](settings.md#defaults-testados)), com os defaults do profile resolvidos para o seu jogo. Um frontend que guarda apenas as escolhas que diferem dele deixa os tested defaults de um build mais novo chegarem ao player, como faz o app de iPhone.
- `base` (opcional, só render scale): `{"docked": [w, h], "handheld": [w, h]}`, o tamanho das visões 3D que a escala multiplica, para um frontend poder mostrar 0.75 como 1200×675 no docked. Vem do profile do jogo (`render_base`), e só alguns jogos o têm.
- `level` (opcional): `basic` marca uma configuração para mostrar primeiro, para um frontend que mostra uma lista curta e põe o resto atrás de um segundo passo. Sem ele, o frontend decide.
- `needs` (opcional): `<VAR>=<value>` faz da linha uma sub-configuração da linha que define `<VAR>`, desenhada só enquanto essa linha tem aquele valor (Rumble strength só enquanto o rumble está ligado). Uma linha que nomeia uma variável que a lista não tem continua desenhada.
- Um app sem o arquivo não tem configurações a oferecer: inicie-o como está.

**Compatibilidade.** [launcher.schema.json](launcher.schema.json) descreve o arquivo como um JSON Schema, para um frontend poder checá-lo e gerar seus tipos a partir dele. `schema` é 1, e o schema 1 cresce só por campos opcionais: um leitor ignora campos que não conhece, desenha seções que não conhece, e um campo nunca muda seu significado. Um `kind` novo seria schema 2, já que um leitor não pode desenhar um kind que não conhece. Uma lista escrita antes de o campo existir não tem `schema` e é 1. Em `settings.tsv`, os campos opcionais vêm da última coluna, `meta` (pares `key=value` separados por `;`); uma chave nova é adicionada no `META` de `settings.py`, aqui e no schema, juntas, e `test_launcher_settings.py` falha quando os três divergem.

## Iniciar um jogo

**Um app para Mac** é iniciado como qualquer app, ou executando seu `Contents/MacOS/nxnative` com as configurações escolhidas em seu ambiente (`DM_FPS_TARGET=30`, …). O launcher ali executa o host como filho, reencaminha SIGTERM, SIGINT e SIGHUP para ele e sai com seu código de saída. Uma variável já no ambiente vence; depois vêm `~/Library/Application Support/nxnative/<title ID>/env.txt` e o `dm/env.txt` do bundle, um `KEY=VALUE` por linha ([Um app para Mac](development.md#a-mac-app)). O save fica em `save/` naquela pasta Application Support; o log é `~/Library/Logs/nxnative/<title ID>.log`, e o da execução anterior, `<title ID>.prev.log`. O title ID é `DMTitleID` no `Info.plist` do app.

Um app para Mac mostra sua própria janela de configurações antes do jogo, desenhada a partir de seu `launcher.json` e guardando suas escolhas no mesmo store ([Escolhas salvas](#escolhas-salvas), [A janela de configurações no Mac](settings.md#a-janela-de-configuracoes-no-mac)). Ele mostra como fixa uma variável que o ambiente de execução ou um `env.txt` define. Um frontend que desenha as configurações por conta própria inicia o app com `DM_LAUNCHER=0` em seu ambiente (ou `--no-launcher` na linha de comando) para pular a janela. As escolhas salvas continuam valendo, então um frontend que as escreve obtém o mesmo jogo que a janela daria.

Configurações que um player escolheu em um frontend e quer mantidas, inclusive para uma inicialização pelo Finder ou pelo Dock, vão nas escolhas salvas do app ([Escolhas salvas](#escolhas-salvas)), não em seu ambiente.

**Um IPA** é instalado pelo player (sideloading); sua própria tela de configurações inicia o jogo. Linhas no `Documents/env.txt` do app vencem aquela tela ([Configurações em arquivo](settings.md#configuracoes-em-arquivo)). O save e o `log.txt` ficam na pasta Documents do app.

## Escolhas salvas

Cada app guarda as escolhas do player em seu próprio domain de preferências, seu bundle ID (`CFBundleIdentifier` no `Info.plist`, `org.nxnative.<title ID>` a menos que o app tenha sido empacotado com outro): uma string por configuração, sob a chave `launcher.<env>` (`launcher.DM_FPS_TARGET` = `"30"`). A tela de configurações do app de iPhone as guarda ali, e o launcher de um app para Mac (`dm/mac/launcher.c`) as lê a cada inicialização, então uma inicialização pelo Finder ou pelo Dock também as tem. Um frontend escreve as mesmas chaves (`CFPreferencesSetAppValue` com o bundle ID do app, ou `defaults write org.nxnative.<title ID> launcher.DM_FPS_TARGET -string 30`), e uma janela de configurações dentro do app também o faria: há um único conjunto de escolhas, e toda visão dele concorda.

- **Só escolhas que diferem do default testado são guardadas.** Uma configuração que o player deixou em seu default segue os defaults de um build posterior. Remova a chave quando o player escolher o default de novo. Para "Reset to tested defaults", remova as chaves das configurações que o player pode mudar; a própria tela do app faz isso quando Play aplica os defaults, e mantém a escolha salva de uma configuração fixada para esta inicialização.
- **Checadas na leitura.** O launcher usa um valor salvo apenas quando este build ainda o oferece: uma das opções de um `choice`, o `on` ou `off` de um `toggle`, ou um número do `min` ao `max` de um `range`. Ele confere contra a própria lista do app, que o packer copia para o `Info.plist` como `DMSettings`. Qualquer outro valor, um valor que não é string, ou uma chave que nenhuma configuração tem é ignorado; ele fica salvo até a tela de configurações substituí-lo pela escolha do player ao tocar Play. Assim, um rebuild que deixa de oferecer um valor recorre ao seu default testado.
- **A ordem**, o primeiro que define uma variável vence:
  1. o ambiente de execução (overrides de execução de um frontend, um terminal);
  2. o `env.txt` do player (`~/Library/Application Support/nxnative/<title ID>/env.txt`; no iPhone, `Documents/env.txt`);
  3. o `dm/env.txt` do bundle;
  4. as escolhas salvas;
  5. os defaults testados (os do próprio host, do profile do jogo).
- **Overrides de execução são temporários.** Um frontend que inicia o jogo com uma variável em seu ambiente sobrepõe a escolha salva apenas para aquela inicialização. Ele escreve no store apenas quando o player quer manter a escolha.
- **`DM_SAVED_CHOICES=0`** (no ambiente ou em um `env.txt`) inicia com os defaults testados e deixa as escolhas salvas como estão. **`DM_LAUNCHER=0`** apenas pula a tela de configurações (a do iPhone, ou a janela de um app para Mac): as escolhas salvas continuam valendo.
- **Um frontend em sandbox** não pode escrever o domain de preferências de outro app; este store é para frontends fora do App Sandbox, como é o NX2Apple.

### A pasta dos cloud saves

A pasta através da qual o save de um app para Mac é sincronizado ([Saves na nuvem](settings.md#saves-na-nuvem)) não é uma linha da lista de configurações, já que uma pasta não é nenhum dos kinds dela. Ela tem uma chave própria no mesmo store: `cloudSaves.folder`, uma string com o caminho absoluto da pasta (`defaults write org.nxnative.<title ID> cloudSaves.folder "$HOME/Library/Mobile Documents/com~apple~CloudDocs/My Saves"`). Remova a chave, ou escreva uma string vazia, para desligar os cloud saves. A janela de configurações do app mostra e muda a mesma chave, então o player vê a pasta que um frontend escolheu.

- **Lida a cada inicialização** por `dm/mac/launcher.c`, com ou sem a janela, e repassada ao host como `DM_SAVE_SYNC`. Uma mudança vale na próxima inicialização do jogo.
- **A ordem** é a das escolhas salvas: `DM_SAVE_SYNC` no ambiente de execução vence (uma pasta só para esta inicialização, ou `0` para iniciar sem cloud saves), depois um `env.txt`, depois esta chave. `DM_SAVE_SYNC=icloud` é iCloud Drive › nxnative Saves.
- **Uma pasta serve todos os jogos.** Cada jogo guarda seu próprio `<title ID>/save.dmsync` nela, então um frontend pode escrever o mesmo caminho no domain de cada app para dar a toda a sua biblioteca uma única pasta na nuvem. Um caminho que já termina no title ID do jogo é tomado como a pasta própria daquele jogo.
- **O app de iPhone** guarda sua pasta como um security-scoped bookmark que só seu próprio seletor de pastas pode criar, então ela é escolhida ali.

## Cheats

Cheats são arquivos, não configurações: o host (`dm/host/hle_cheats.c`) lê arquivos de cheats no formato do Atmosphère para o build do jogo em execução e guarda os toggles do player ao lado deles. Um frontend que quer instalar cheats ou escolher quais executam, antes do jogo começar, escreve os mesmos arquivos; o menu e o painel Cheats da janela do jogo no Mac são uma visão deles. O motor em si e o formato de uma linha de cheat estão descritos na [referência de cheats](reference/cheats.md); esta seção é o contrato dos arquivos.

- **A pasta.** `DM_CHEATS=<folder>`, senão `<DM_ROOT>/cheats`, que para um app para Mac é `~/Library/Application Support/nxnative/<title ID>/cheats` (o launcher define `DM_ROOT` como essa pasta; quando um `dataFolder` muda os arquivos do app de lugar, a pasta de cheats vai junto, como tudo sob `DM_ROOT`). Dois layouts são lidos: plano, `<folder>/<build ID>.txt`, e a árvore de um cartão SD de Switch sob ela, `<folder>/atmosphere/contents/<title ID>/cheats/<build ID>.txt` (ou sem `atmosphere/`). O build ID são os primeiros 16 dígitos hex do build ID do NSO principal, em maiúsculas, como os sites de cheats nomeiam seus arquivos; a nota do jogo de um profile lista seus builds. Sem arquivo para o build em execução, nada muda no jogo, e a pasta nunca é criada pelo host.
- **Os arquivos ao lado de `<build ID>.txt`**, todos na mesma pasta, todos texto puro, escritos pelo host e legíveis por qualquer um:
  - `<build ID>.enabled`: os cheats que estão ligados, um nome por linha, na grafia do próprio arquivo (`Max HP`); entradas posteriores de um nome duplicado são `Name #2`, `Name #3`, …. Masters (entradas `{Name}`) estão sempre ligados e não são listados; labels e cheats one-pass nunca são. O host reescreve o arquivo a cada toggle (um arquivo temporário renomeado por cima, então nunca fica meio escrito) e o lê quando o arquivo de cheats é carregado. Um frontend que instala um arquivo e quer alguns cheats ligados desde a primeira inicialização escreve este arquivo antes da inicialização.
  - `<build ID>.meta.tsv`: opcional, escrito por `scripts/cheat_anchor.py` ao lado de um arquivo que ele compilou: uma linha por entrada (colunas por nome de cabeçalho: `name`, `kind`, `section`, `type`, `action`, `ask`, `value_words`, `once`, `pair`, `resolved`, `note`, `address`), que dá ao painel as seções, notas, pares on / off, valores digitados e cheats run-once. Um arquivo feito à mão ou baixado não tem nenhum, e não precisa.
  - `<build ID>.values`: os valores digitados dos cheats `ask`, `name<TAB>value` por linha, escritos quando o player define um.
  - `.cheats-off-<title ID>`: o master switch. Enquanto este arquivo existe, nenhum cheat do jogo roda, e os toggles são mantidos. `.cheats-running-<title ID>` é o sinalizador de safe mode do próprio host (só Mac): existe enquanto cheats rodam e desaparece com uma saída limpa; encontrado na inicialização seguinte, significa que a última execução travou com cheats ligados, e o jogo inicia com todos os cheats desligados até o player os restaurar. Um frontend não mexe nele.
- **O que um frontend pode escrever com o jogo rodando.** Nada que o host releia por conta própria: o host lê `<build ID>.enabled` e o arquivo de cheats quando os carrega, na inicialização e no "Reload cheats" do player (⌘R na janela do Mac, que relê o mesmo arquivo e os toggles junto), e nunca observa a pasta. Então um frontend escreve antes da inicialização, ou pede ao player para recarregar. O refresh do painel enquanto ele está aberto lê o estado do motor, não os arquivos. Para uma inicialização, `DM_CHEATS_ON=<name>,<name>,…` no ambiente de execução liga os cheats nomeados sem tocar em `.enabled` (sem diferenciar maiúsculas, por nome); `DM_CHEATS_ANY_BUILD=1` carrega o arquivo único da pasta de outro build quando o build em execução não tem nenhum (seus endereços podem não servir: para conferir um arquivo carregado), e `DM_CHEATS_DEBUG=1` registra cada escrita. Os três são diagnósticos, não configurações: não estão no `launcher.json`.
- **O iPhone.** O motor também é compilado no host iOS (sem safe mode), com a mesma regra de pasta, mas o app iOS não define nem `DM_CHEATS` nem `DM_ROOT`, então cheats estão desligados ali a menos que `Documents/env.txt` nomeie uma pasta com `DM_CHEATS`; mesmo assim eles só escrevem dados, nunca código (as páginas de código do app são assinadas). Dar ao app iOS um default, `Documents/cheats`, é o próximo passo natural quando um frontend quiser gerenciar cheats em um dispositivo; isso não está nesta mudança.
- **Carregar um arquivo para outro build.** `scripts/cheat_anchor.py` importa um arquivo de cheats para uma fonte independente de versão e o compila para qualquer build do jogo ([a referência do rewriter](reference/rewriter.md#cheats-carried-across-game-updates)). Um frontend não precisa saber disso: o `<build ID>.txt` compilado é um arquivo de cheats comum, e seu `.meta.tsv` deixa o painel mais rico.

## O que um frontend ainda não pode ver

Com um jogo rodando, o host diz o que acontece apenas em seu log, como texto feito para pessoas: um controle que conecta ou desaparece, o preparo dos shaders antes do primeiro frame, uma linha vermelha. Um frontend que lesse o log para mostrar isso quebraria quando a redação de uma linha mudasse. Eventos legíveis por programa (um objeto JSON por linha, como os do script de NSP) seriam a próxima peça desta interface; até lá, trate linhas de log como diagnósticos, não como interface.
