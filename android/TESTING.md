# Testando o launcher em um celular

O primeiro teste: instalar o launcher, colocar a pasta do jogo Donkey Kong Bananza e o pack dela no celular, iniciar, e mandar o log de volta. Esta build reproduz os clears e presents do jogo em Vulkan mas não os seus draws, então uma boa execução inicializa o lado de CPU do jogo e registra as chamadas NVN dele com uma tela quase toda preta e as linhas de log mais novas por cima. Faça o [Teste 2](#teste-2-autoteste-grafico) primeiro: ele não precisa de jogo. O [README.md](README.md) explica as peças; o [PORTING.md](PORTING.md), o que ainda é um stub.

No Mac, com o celular no USB e a depuração USB ligada. `D` é a pasta de dump e `W` a pasta de trabalho do pack:

```sh
D=~/switch-dumps
W=$D/040074A01BF12000-work
```

## 1. Compilar o que vai para o celular

```sh
$D/.venv-nxnative/bin/python scripts/pack_android.py $D/DonkeyKongBananza   # → $W/android-pack (about 60 MB); not needed when the phone prepares it (below)
android/ondevice/build.sh $W/packer                                             # once: the app's packer (stage)
cd android/app && ./gradlew -PnxPackStage=$W/packer/stage apk && cd ../..      # → android/app/out/nativeayn-test.apk
```

Sem um pack do Mac, o app prepara um a partir do dump no celular: copie só o dump (passo 3 sem `android-pack`), então no passo 5 **Adicionar jogo** não acha pack e oferece **Preparar** (cerca de 30-45 minutos em segundo plano, ONDEVICE.md). Mate-o (`adb shell am force-stop dev.ankanative.android.test`) ou reinicie o celular no meio: **Retomar** na tela principal continua do shader em que tinha chegado.

## 2. Instalar o APK

```sh
adb install -r android/app/out/nativeayn-test.apk
```

Ou copie o APK para o celular e abra-o pelo Arquivos (permita instalações daquele app quando for pedido).

## 3. Copiar a pasta do jogo e o pack

O celular precisa do mesmo layout do drive de dump: a pasta do jogo e, ao lado dela, `040074A01BF12000-work/android-pack`. Só as partes que o host lê são necessárias: `base/exefs` (o launcher lê o title ID de `main.npdm`), o update e o DLC. O RomFS base (8,7 GB) pode ficar no Mac. Cerca de 9,8 GB no total:

```sh
adb shell mkdir -p /sdcard/SW2/DonkeyKongBananza/base /sdcard/SW2/DonkeyKongBananza/updates /sdcard/SW2/040074A01BF12000-work
adb push $D/DonkeyKongBananza/base/exefs           /sdcard/SW2/DonkeyKongBananza/base/
adb push $D/DonkeyKongBananza/updates/458752       /sdcard/SW2/DonkeyKongBananza/updates/
adb push $D/DonkeyKongBananza/dlc                  /sdcard/SW2/DonkeyKongBananza/
adb push $W/android-pack                           /sdcard/SW2/040074A01BF12000-work/
adb shell ls /sdcard/SW2/DonkeyKongBananza/updates/458752/romfs /sdcard/SW2/040074A01BF12000-work/android-pack/pack.tsv
```

Sem adb: copie as mesmas pastas por transferência de arquivos USB (MTP) para uma pasta `SW2` no topo do armazenamento do celular. Depois de um pack novo, copie `android-pack` de novo; a pasta do jogo fica.

## 4. Acesso a todos os arquivos

Abra o nxnative e toque em **Conceder acesso a todos os arquivos**, depois ligue "Allow access to manage all files" para o nxnative e volte. Com adb:

```sh
adb shell appops set --uid dev.ankanative.android.test MANAGE_EXTERNAL_STORAGE allow
```

## 5. Adicionar o jogo

Toque em **Adicionar jogo**, abra `SW2`, depois `DonkeyKongBananza`, e toque em **Use this folder** (permita o acesso quando for pedido). O launcher acha o pack em `SW2/040074A01BF12000-work/android-pack` (ou qualquer `<title ID>-work/<name>/` com um `pack.tsv` na pasta do jogo ou até três pastas acima, `PackFind`) e lista "Donkey Kong Bananza (040074A01BF12000)" sem perguntar quando o pack serve (o build do jogo do dump, a biblioteca de shaders deste APK, ou uma biblioteca mais antiga que a atualização automática de shaders traz ao dia). Sem uma, ele oferece **Preparar** (acima), **Usar um pack existente…** ou **Importar pack…**. Escolher o `SW2` em si adiciona todo jogo nele (cada `.nup` e subpasta de dump) e diz numa única mensagem o que foi adicionado, o que este app não consegue executar e o que precisa de Preparar.

Com adb, adicionar e iniciar num só passo:

```sh
adb shell am start -n dev.ankanative.android.test/dev.nxnative.android.MainActivity --es game /sdcard/SW2/DonkeyKongBananza --ez run true
# any app (files/saves/<id> by default): --es save <folder> names the save folder, as Folders does, so a test run never opens the user's save
adb shell am start -n dev.ankanative.android.test/dev.nxnative.android.MainActivity --es add_game /sdcard/SW2   # Add game's own path for a picked folder or .nup: all its games, one summary
```

## 6. Iniciar e acompanhar o log

Inicie o log no Mac primeiro, depois toque no jogo:

```sh
adb logcat -c && adb logcat -v time nxnative:V DEBUG:V libc:V '*:S' | tee nxnative-logcat.txt
```

A tela fica preta com as últimas linhas de log embaixo. Dê um minuto a ela: os estágios abaixo ocorrem a segundos de distância um do outro no Mac. O Voltar pergunta se quer parar o jogo; parar encerra o processo do jogo e o launcher fica.

**Controles na tela.** Sem nenhum gamepad conectado, um Pro Controller semitransparente é desenhado por cima do jogo: o analógico esquerdo em cima e o D-pad embaixo à esquerda, A / B / X / Y em cima e o analógico direito (a câmera) embaixo à direita, ZL / L e R / ZR nos cantos de cima com − e + ao lado deles, e pequenos L3 / R3 (cliques do analógico) ao lado dos analógicos. Eles ficam nas barras pretas ao lado da imagem 16:9 na medida em que cabem. Vários dedos funcionam ao mesmo tempo; cada pressão dá um tick curto (quando a vibração de toque do celular está ligada). Um toque que começa num controle aciona só o controle; os toques em outros lugares da imagem são a tela de toque do jogo. Eles se escondem sozinhos quando um gamepad conecta e voltam quando o último vai; **Controles na tela** no painel lateral (Voltar) os desliga e liga, com opacidade, tamanho e o tick guardados para a próxima execução. O log diz `[nxlaunch] on-screen controls: 15 in a 2420x1080 view …` e `[dm] input: on-screen controls: 15, the launcher's layout`.

## 7. Mandar o log de volta

Toque em **Compartilhar log** na lista de jogos (ele manda o log da execução mais nova), ou puxe todos os logs:

```sh
adb pull /sdcard/Android/data/dev.ankanative.android.test/files/logs/
```

Se o jogo derrubou, mande também `nxnative-logcat.txt` (ele tem o relatório de crash próprio do celular, um backtrace `DEBUG`) ou `adb logcat -d -b crash`.

## O que o log diz em cada estágio

Cada linha abaixo começa com `[nxlaunch]` (o launcher) ou `[dm]` (o host). O primeiro estágio cuja linha falta é onde a execução parou; as últimas linhas antes disso dizem por quê.

| Estágio | Linha | Significa |
|---|---|---|
| 1 Launcher | `[nxlaunch] env DM_ROMFS=/storage/emulated/0/SW2/DonkeyKongBananza/updates/458752/romfs` e as outras linhas `env` / `arg`; `hooks: window yes, input yes, lifecycle yes` | o processo do jogo iniciou e passou as pastas |
| 2 Host iniciado | `[nxlaunch] nxhost_main starts on its own thread (8 MiB stack)`, depois `[dm] build: commit …, shader chain …, profile 040074A01BF12000` | o `libnxhost_<title ID>.so` carregou e os handlers de crash dele estão no lugar, atrás dos do ART |
| 3 Thread slots | `[dm] thread slots: tpidr_el0 + 0x10 → K18 +0 KTLS +8 KTLR +0x10 KGATE +0x18` | o bloco de slots do host fica no `TLS_SLOT_APP` do Bionic; `FATAL … TLS_SLOT_APP … another library uses the slot` ou `thread slot layout mismatch` param aqui |
| 4 Loader feito | `[dm] main: gates … keys 0/1/2/3`, `[dm] sdk: gates …`, `[dm] main: base 0x… size …`, `reloc …`, `force: … entries hit`, `[dm] sdk: NintendoSDK nnSdk-…`, `[dm] stubs=…` | os dois módulos mapeados, realocados e o código deles tornado executável; `FATAL open …` nomeia um arquivo que falta no pack |
| 5 Primeiro código convidado e SVCs | `[dm] >>> nnosInitialize`, depois `[dm] guest AS 0x4000000000 +0x270000000  heap … alias … stack …` | o código próprio do jogo roda e o primeiro SVC de memória dele reservou o bloco convidado de 9,75 GiB no endereço esperado (`guest block: … was taken, reserved at …` está certo; `[RED] guest block` não) |
| 6 Inicialização do SDK | `[dm] >>> nninitInitializeSdkModule`, `[dm] >>> nninitStartup`, `[dm] >>> nnMain` | o SDK inicializou e o `nnMain` do jogo roda: as threads convidadas, o heap e as leituras de arquivo do RomFS funcionam |
| 7 Primeira chamada NVN | `[dm] [nvn] device API …` | o jogo pediu NVN e inicializa o dispositivo dele |
| 8 NVN até o primeiro frame | `[dm] [nvn] init 0: … functions … calls \| …`, depois mais linhas `[dm] [nvn] frame …` (cada frame inicial, depois cada 300º) | as chamadas NVN do jogo até o primeiro present dele, e o loop de frames roda (os presents chegam aos stubs) |

Esperados no caminho, não falhas: as linhas `[RED] vk: draw not yet on Vulkan` e as outras `[RED] vk:` para o que o replay ainda não faz ([PORTING.md](PORTING.md#stubs-nesta-build)), as linhas `[RED] … not yet on Android` para os stubs restantes, e as linhas `UNIMPL …` para chamadas de SDK que o host stubs, que também aparecem no Mac.

Um crash imprime `[dm] CRASH sig=… pc=… lr=…` com offsets de módulo (`main+0x…`), os registradores, um backtrace, uma varredura de pilha e um despejo de toda thread, e depois o relatório próprio do celular no logcat (`F libc : Fatal signal …`, `F DEBUG : backtrace:`). `FATAL …` é o host parando de propósito com o motivo dele.

## Saves

O save de um jogo fica no armazenamento próprio do app (`AppStorage`, como o Eden guarda a pasta de usuário): `/sdcard/Android/data/<app>/files/saves/<title ID>/`, com o save de dispositivo dele `saves/<title ID>_device/` ao lado. Cada app (teste `dev.ankanative.android.test`, jogo `dev.ankanative.android.play`, lançamento `dev.ankanative.android`) tem o seu. Uma pasta de save escolhida no ⋯ › Pastas do jogo é usada como está, em vez disso. O Android apaga o armazenamento do app junto com o app; o `hasFragileUserData` do manifesto faz a desinstalação perguntar se deve mantê-lo. **Fazer backup dos saves** escreve no armazenamento compartilhado (`/sdcard/Switch2/save-backups/<title ID>/`, abaixo), que uma desinstalação nunca toca.

Até o 0.1.0 o padrão ficava no armazenamento compartilhado, ao lado do jogo (a pasta que contém a pasta do jogo ou o `.nup`, `/sdcard/Switch2`):

| App | Pasta de save padrão antiga |
|---|---|
| jogo e lançamento | `/sdcard/Switch2/saves/<title ID>/` |
| teste | `/sdcard/Switch2/saves-test/<title ID>/` |

**A cópia única** (`SaveMigrate`, `GameActivity.defaultSave`): no início de um jogo, quando nenhuma pasta de save foi escolhida, o save do app não tem arquivo e o padrão antigo tem um save, o save antigo (e o `_device` dele) é copiado para o save do app sob as travas dos dois saves: primeiro numa pasta de staging `saves/.<title ID>.migrate/`, conferido arquivo por arquivo (caminho, tamanho, SHA-256) contra o save antigo, o save antigo varrido de novo, depois renomeado para o lugar (o save de dispositivo primeiro, o save principal por último: o save principal aparecendo é o commit). O app registra por title (`save_migrated@<title ID>`), mostra um toast, e o log diz `[nxlaunch] save: <app save> (existing); copied once from <old folder> (n files, size, checked); that folder is kept as it was`. A pasta antiga nunca é escrita, movida nem apagada (só a `<folder>.lock` ao lado dela é tomada, como antes). Uma cópia nunca é feita sobre um save do app que tem um arquivo. Quando a cópia falha (ou o save antigo tem um commit inacabado), nada pela metade é jogado: aquele início usa a pasta antiga, como o 0.1.0 fazia, com um toast e `save: … not copied into the app's storage (<why>)`; o próximo início tenta de novo. Até a cópia ser feita, ⋯ › Pastas, **Fazer backup** e **Restaurar** mostram e usam a pasta antiga; depois, Pastas diz onde o save está e de onde veio.

Depois disso, um save do app vazio é preenchido a partir do save privado das primeiras versões (`files/save/<title ID>`, guardado), senão da semente `/sdcard/Switch2/save-seed/<title ID>/`, um save que chega ao primeiro nível. A linha do log `[nxlaunch] save: <path> (migrated|seeded|existing|new)` diz qual. Se a pasta não pode ser criada, a pasta privada é usada e o log diz isso.

Cada jogo tem a própria pasta de save, `DM_TEMP` (`files/temp/<title ID>`: cache de pipeline, lista de prewarm), links de DLC (`files/aoc/<title ID>`) e logs (`files/logs/<title ID>-<time>.log`, 10 guardados por jogo); o `env.txt` vale para todo jogo, o `files/env-<title ID>.txt` (gravado e com `chmod 666` como o `env.txt`) para um só.

**Agentes de teste** começam da semente: dê à execução a própria pasta de save dela (uma cópia da semente: `--es save <folder>` no intent do launcher, ou `DM_SAVE=` em `env-<title ID>.txt`, abaixo) em vez de apagar qualquer coisa; nunca apague nem sobrescreva as pastas de save do usuário (`Switch2/saves*`, `save-backups`, os `files/saves` dos apps). Para testar a cópia única, copie a semente para `saves-test/<title ID>` ao lado de uma pasta de jogo ou `.nup` seu (uma pasta nova, digamos `Switch2/apptest/`; um `.nup` copia no celular em segundos) e adicione aquele jogo com `--es game`. Atualize a semente com `adb push <save folder> /sdcard/Switch2/save-seed/` (a pasta é nomeada pelo title ID). Saves são dados privados: nunca os comite.

### Packs no armazenamento do app

Preparar escreve um pack novo no `files/packs/<title ID>/` do app (os arquivos de trabalho do job ficam em `<dump>/../<title ID>-work/ondevice`). O pack de um jogo é procurado na pasta escolhida em Pastas, depois em `files/packs/<title ID>`, depois ao lado do jogo (`<title ID>-work/android-pack`, `Pack.find`), depois em `<title ID>-work/<any name>` e `android-pack` da pasta do jogo e até três pastas acima (`PackFind`; o primeiro que serve, senão o primeiro que uma atualização automática de shaders traz ao dia; nunca um de outro build do jogo): packs de versões anteriores ou de um Mac continuam funcionando e são atualizados no lugar (restamp, atualizações de shaders), nunca copiados. Testes: `python3 dm/tools/tests/test_android_packfind.py` (o localizador e a varredura de pastas).

A folha ⋯ Gerenciar de um jogo tem **Exportar pack…** e **Importar pack…** (`PackUi`, `PackZip`). Exportar escreve o pack como um único zip stored, `anka-pack-<title ID>-<version>.zip`, numa pasta escolhida (`<name>.partial`, lido de volta entry por entry contra os arquivos do pack, depois renomeado; um nome já tomado ganha `-2`). Importar aceita um zip assim (pack.tsv na raiz ou na única pasta de topo) ou uma pasta de pack: o title dele tem que ter um profile neste APK e ser o jogo da folha; ele é descompactado em `files/packs/<title ID>.partial` (uma entry nomeada fora dela, `..`, um caminho absoluto ou uma barra invertida recusam o zip inteiro antes de qualquer coisa ser escrita), conferido (`Pack.read`, código terminado, uma biblioteca de shaders de alguma versão, `PackCheck.blocking` contra a pasta do jogo), e só então trocado: o pack que estava lá é guardado como `<title ID>.replaced` até o novo ter o nome dele (uma parada entre as renomeações é refeita por `PackZip.recover`). O card então o usa; uma pasta de pack escolhida é liberada (mantida no armazenamento). Uma biblioteca mais antiga passa pelo usual restamp ou atualização de shaders. Ambos recusam enquanto o jogo roda.

Sem os seletores (só o app de teste): `adb shell am start -n dev.ankanative.android.test/dev.nxnative.android.MainActivity --es pack_title <title ID> [--es pack_game <game folder or .nup>] --es pack_export <folder>` (ou `--es pack_import <zip or pack folder>`) roda o mesmo para o jogo listado daquele caminho (senão o primeiro daquele title). Testes: `python3 dm/tools/tests/test_android_packzip.py` (ida e volta, zip slip, titles errados, packs danificados, a troca).

### Backups de save

A folha ⋯ Gerenciar de um jogo tem **Fazer backup dos saves**, **Restaurar saves…** e **Importar save…** (`SaveUi`, `SaveStore`). O save é `DM_SAVE` e, para um jogo cujo main importa tanto `MountSaveData` quanto `MountDeviceSaveData`, o save de dispositivo `<DM_SAVE>_device` ao lado (`hle_fs.c` `h_fs_MountDeviceSave`; dos jogos de Switch 2 aqui, nem o main do Donkey Kong Bananza nem o do Mario Kart World 1.8.0 importam `MountDeviceSaveData`, então ambos têm só o save principal). Um backup guarda ambos: `/sdcard/Switch2/save-backups/<title ID>/<yyyyMMdd-HHmmss>/main/` e `device/`, com um manifesto `<name>.manifest.tsv` ao lado (o layout, a pasta de origem, depois uma linha por arquivo: SHA-256, tamanho, caminho). Os apps de jogo e de teste compartilham aquela pasta; a lista mostra de qual pasta de save um backup veio. O app nunca apaga um backup terminado (apague os antigos com um gerenciador de arquivos ou `adb shell rm -r`).

- **A trava**: todo backup, restauração e recuperação segura `<save>.lock` (`FileChannel.lock`). Um início de jogo a toma antes do save ser usado e a mantém enquanto o processo vive (o kernel a libera quando o processo termina, também depois de um crash), então **Fazer backup** / **Restaurar** dizem "is running" exatamente enquanto aquele jogo roda, e um início que falha (sem host para o jogo, um save em uso, qualquer erro de início) encerra o processo `:game` dele de uma vez em vez de deixá-lo ocioso com um host carregado. Jogar em outro jogo enquanto um roda é respondido pelo jogo que roda ("stop it first"), um jogo por vez.
- Um backup sem save de dispositivo não é restaurado num save que tem um (as duas partes não pertenceriam juntas); um backup do app sempre guarda ambos.
- **Fazer backup**: o save (as duas partes) é copiado para `.partial-<name>/`, cada arquivo sincronizado; a cópia é comparada com o save arquivo por arquivo (caminho, tamanho, SHA-256) e o save é varrido de novo (um save que mudou durante a cópia falha); o manifesto é gravado, depois a pasta ganha o nome. Mais antigo que um dia, no próximo backup: uma pasta `.partial-*` que bate com o manifesto ao lado dela (parada logo antes da renomeação dela) ganha o nome; outras pastas `.partial-*` e manifestos sem a pasta deles são removidos (nunca um backup terminado). O save é só lido.
- **Restaurar / Importar**: recusado a partir do próprio save, de dentro dele, de uma pasta que o contém, e das pastas de trabalho dele (`<save>.restore`, `.new`, `.re`, `.work`, `.old`), comparados por caminhos canônicos. A origem é conferida contra o manifesto dela (um backup cujos arquivos não batem mais é recusado como danificado; uma pasta importada não tem nenhum, e o `<folder>_device` ao lado dela, se houver, é o save de dispositivo dela); um save atual com qualquer coisa nele é primeiro copiado como backup em `<time>-before-restore` (conferido; quando aquilo falha, nada mais acontece); cada parte é copiada para `<part>.restore` ao lado do alvo dela e conferida; então o arquivo de conjunto `<save>.restore-set` nomeia os pares, cada parte ganha `<part>.pend1` (nomeando a cópia, fsynced), o arquivo de conjunto sai, e cada parte é trocada para dentro sob o diário de avanço do host (`plat_android.c` `plat_rename_swap`: o FUSE não tem `RENAME_EXCHANGE` nem xattrs): o estágio 1 apaga o que a parte tem e a cópia não tem, `.pend2` substitui `.pend1`, o estágio 2 renomeia os arquivos da cópia sobre os da parte (cada renomeação atômica), os marcadores saem. Um kill antes do arquivo de conjunto deixa o save como estava; depois dele, o próximo backup, restauração ou início de jogo o termina (`SaveStore.recover`), então ambas as partes são a versão antiga ou a restaurada. A checagem final compara nomes sem diferenciar maiúsculas/minúsculas (o armazenamento compartilhado não diferencia caixa).
- **As regras do diário** são as mesmas no launcher (`SaveStore.recover`) e no host (`plat_swap_recover`, em cada montagem): um `<save>.old` legado volta quando o save está ausente e sai quando ele está lá; um marcador vazio é descartado e o próximo é olhado; um marcador que nomeia algo além de `<save>.new`, `<save>.re` ou `<save>.restore` (mesma pasta, pelo caminho real) é recusado, e também um `.pend1` cuja pasta sumiu (o estágio 1 esvaziaria o save): o save e o marcador ficam como estão, o host registra `[RED] save swap journal …`, e o launcher recusa o início com o motivo (afaste o marcador para usar o save). O estágio 2 roda só depois que `.pend1` é apagado e a pasta sincronizada (também quando a recuperação acha ambos os marcadores), então uma segunda parada nunca pode deixar `.pend1` ao lado de uma cópia esvaziada; tal `.pend1` de builds mais antigas (a pasta dele não tem arquivos enquanto o save tem) é descartado com a pasta vazia, o save ficando como está.
- Testes: `python3 dm/tools/tests/test_android_saves.py` roda `SaveStore`, `SaveMigrate` e `Profile` na JVM do Mac (`android/app/src/jvmtest`): a cópia única (copiada e conferida, nunca sobre um save do app, a pasta antiga inalterada, uma cópia que falha não deixando save para jogar e sendo copiada inteira no próximo início, um save antigo com um diário ou em uso deixado em paz), backups (principal e dispositivo), restaurações com o backup automático delas, um backup danificado recusado, trocas paradas depois do arquivo de conjunto, depois do `.pend1` e dentro do estágio 2, cada regra do diário, a trava segura por outro processo, origens recusadas, sobras obsoletas, caminhos canônicos e a checagem sem caixa; e ele compila o código do diário do host de `plat_android.c` para o Mac e recupera os mesmos estados parados com ambos, que têm que deixar os mesmos arquivos.
- No celular: faça backup, jogue um pouco e salve, restaure o backup e confira que o jogo carrega o save mais antigo; `ls /sdcard/Switch2/save-backups/<title ID>/` mostra ambos os backups.

## Pausa e energia em segundo plano

Pressione Home com o jogo rodando, espere, abra-o de novo: o log tem `[lifecycle] background: … since the last pause or resume …` e, ao voltar, `[lifecycle] foreground: … in <s>` com o CPU das threads ao longo de toda a permanência em segundo plano (deveria ser algumas dezenas de milissegundos por minuto; qualquer thread nomeada ali com tempo real é um busy loop para olhar), depois `[lifecycle] foreground, paused: the game waits for a tap`. A tela mostra "Pausado"; um toque registra `[lifecycle] continued: …`. O **Pausar o jogo** do painel dentro do jogo faz o mesmo sem sair. Com `DM_WATCH_STALL` definido, uma pausa longa não deve terminar em `[RED] watchdog`.

## Switches de diagnóstico

Um arquivo `env.txt` na pasta do app acrescenta linhas `KEY=VALUE` ao ambiente do host para a próxima execução, como o `Documents/env.txt` faz no iPhone. Por exemplo:

```sh
adb shell 'f=/sdcard/Android/data/dev.ankanative.android.test/files/env.txt; printf "DM_SVC=1\nDM_THR=1\nDM_WATCH_STALL=20\n" > $f; chmod 666 $f'
adb shell rm /sdcard/Android/data/dev.ankanative.android.test/files/env.txt     # back to a normal run
```

**Os caminhos continuam sendo do launcher.** Linhas do `env.txt`, do `env-<title ID>.txt` ou das linhas próprias de um jogo (Configurações do jogo) que definam `DM_SAVE`, `DM_ROMFS`, `DM_TEMP`, `DM_ROOT`, `DM_AOC_DIR` ou `HOME` não são aceitas: o jogo mostra uma mensagem e o log tem `[nxlaunch] [RED] env: not taken: …`. Uma exceção, **só no app de teste**: `DM_SAVE=<folder>` em `env-<title ID>.txt` (nunca em `env.txt`, que todo jogo lê) roda aquele jogo naquela pasta (uma semente copiada, para execuções de teste que não devem tocar o save próprio do app), travada e recuperada como qualquer save, nunca migrada para dentro nem semeada; o log diz `[nxlaunch] test app: DM_SAVE from env-<title ID>.txt: <folder>`. O app de jogo a ignora com a mesma linha `[RED]`.

O `chmod` é necessário: um arquivo que o `adb shell` escreve ali pertence ao usuário do shell, legível só pelo grupo dele, do qual o app não faz parte; sem ele a execução continua sem switches e o log diz `[nxlaunch] [RED] env.txt: … exists but cannot be read`. O log lista cada switch que o host recebeu como `[nxlaunch] env KEY=VALUE`.

`DM_SVC=1` registra os SVCs de memória, `DM_THR=1` os SVCs de thread, `DM_FS=1` as aberturas de arquivo, `DM_NVN_LOG=1` os objetos NVN, `DM_WATCH_STALL=<s>` despeja toda thread quando o jogo para de progredir por tanto tempo ([03-bringup.md](../docs/agents/03-bringup.md)).

## Captura de frame e replay no celular

Um teste gráfico que inicializa o jogo e joga até o primeiro nível leva 1–3 minutos. Em vez disso: capture um frame de nível uma vez, depois o reproduza no celular sem o jogo, quantas vezes precisar, em cerca de meio minuto por execução (um décimo de segundo disso é o próprio frame). O replay roda o replay NVN → Vulkan da build atual (`dm/gfx/vk_android`) sobre os comandos gravados, a memória de pools e as texturas do frame, e reporta o tempo de gravação em CPU, o tempo de GPU e a imagem.

- **Captura** (no jogo, `vka_capture.c`): `DM_NVN_CAPTURE=@<seconds>:1:<path>` no `env.txt` captura o primeiro frame que começa depois de tantos segundos de execução (`<frame>:1:<path>` um número de frame, como no Mac: [docs/development.md](../docs/development.md#frame-capture-and-replay)). Ele escreve o arquivo (cerca de 3,2 GB para o túnel: 2,65 GB de memória de pools, 490 MB de render targets e outras imagens só de GPU, as 4 submissions do frame) e `<path>.live.png`, a imagem apresentada; a execução continua depois. Capturas são dados de jogo: ficam em `/sdcard/Switch2/040074A01BF12000-work/captures/` (ou a pasta de trabalho no Mac), nunca no repositório.
- **Replay** (`nxreplay`, `vka_replay.c`): um executável rodado do `adb shell` como o usuário do shell, com o `libnxhost.so` ao lado em `/data/local/tmp/nxreplay` (sem instalação de APK; o app não é tocado). Ele lê a captura e a biblioteca de shaders do pack de `/sdcard`, mantém o próprio cache de pipeline ao lado (semeado uma vez a partir do do app), e escreve PNGs ao lado da captura.

Segure o lock do celular (`android/HANDOFF.md`) para ambos: a captura roda o jogo, o replay usa a GPU e 6 GB de memória.

### Capturando um frame de nível

Uma cópia nova do save-semente (um save no primeiro nível: [Saves](#saves)), dada à execução como a própria pasta de save dela, chega ao túnel de mineração com A pressionado a cada 2 s; aos 100 s a câmera está no túnel:

```sh
a() { adb "$@"; }
F=/sdcard/Android/data/dev.ankanative.android.test/files/env.txt
S=/sdcard/Switch2/saves-run-$(date +%m%d%H%M); a shell cp -r /sdcard/Switch2/save-seed/040074A01BF12000 $S   # a fresh level-1 save of its own
K=$(python3 -c "print(','.join('%d:A'%t for t in range(15,90,2)))")
a shell "printf 'DM_WATCH_STALL=25\nDM_KEYS=$K\nDM_NVN_CAPTURE=@100:1:/storage/emulated/0/Switch2/040074A01BF12000-work/captures/tunnel.nvncap\n' > $F"
a shell am start --user 0 -n dev.ankanative.android.test/dev.nxnative.android.MainActivity --es game /storage/emulated/0/Switch2/DonkeyKongBananza --es save $S --ez run true
# about 2 minutes; the game log (files/logs/, newest) ends the capture with
#   [dm] DM_NVN_CAPTURE: frame 2673: 4 submits, 1052 segments, 2147 regions, … 3170.6 MB in 15969 ms → …/tunnel.nvncap; live digest 0640c5… (2880x1620 VkFormat 37)
a shell am force-stop --user 0 dev.ankanative.android.test
a shell "printf 'DM_WATCH_STALL=25\n' > $F"                          # restore env.txt
```

A captura leva cerca de 16 s dentro do frame (cada passo espera a GPU e escreve os bytes dos pools); o jogo então continua. As linhas `[RED] DM_NVN_CAPTURE` dizem o que não foi capturado. Texturas registradas cujo armazenamento não está em nenhum pool vivo (133 no túnel: os pools delas foram finalizados enquanto elas continuaram registradas) são escritas sem armazenamento; o conteúdo das imagens delas é capturado por serial. Uma captura só reproduz numa build com o mesmo layout de registro (`nvn_obj.h`) e, para cada programa, uma biblioteca de shaders com os mesmos registros de binding; senão o replayer para com uma linha `[RED] nxreplay:` que diz para capturar de novo.

### Reproduzindo-o

O `dm/tools/android_replay.py` compila o replayer (CMake com o NDK, na `replay-agent/build` da pasta de trabalho; `--build <dir>` para outra), envia e roda:

```sh
python3 dm/tools/android_replay.py build            # libnxhost.so + nxreplay (about 1 min the first time, seconds after a change)
python3 dm/tools/android_replay.py push             # → /data/local/tmp/nxreplay
python3 dm/tools/android_replay.py run tunnel.nvncap --warm 3
python3 dm/tools/android_replay.py --env DM_VK_NANSCAN=1 run tunnel.nvncap --warm 2   # a switch for the replay (the live run's DM_* switches apply too)
python3 dm/tools/android_replay.py pull out/        # the PNGs beside the captures
```

Um nome de captura sem uma `/` é buscado na pasta de capturas. À mão: `adb shell 'cd /data/local/tmp/nxreplay && LD_LIBRARY_PATH=. ./nxreplay /sdcard/Switch2/040074A01BF12000-work/captures/tunnel.nvncap --warm 3'`. Opções:

| Opção | O quê |
|---|---|
| `--warm n` | n replays num só processo, cada um depois do anterior: o primeiro monta imagens e pipelines, os seguintes mostram o estado estável (use estes para cronometrar) |
| `--repeat n` | n replays, cada um num processo novo |
| `--png <path>`, `--no-png` | para onde vai a imagem apresentada (padrão `<capture>.replay<n>.png` ao lado da captura), ou nenhuma |
| `--list` | as texturas que os registros do frame nomeiam, com as imagens (serial, tamanho, VkFormat, escritas pela GPU) |
| `--dump <serial>,…` | PNGs do nível 0 dessas texturas depois do último replay (`<capture>.r<n>_<serial>.png`) |
| `--dump-rt` | PNGs de toda imagem de cor que a GPU escreveu entre elas |
| `--dump-gpu` | PNGs de toda imagem de cor que a GPU escreveu, também as que nenhum registro nomeia (um alvo de cena antes do upscaler): `<capture>.r<n>_g<serial>_<w>x<h>_f<VkFormat>.png` |
| `--max-pixels n` | um frame que difere do ao vivo em no máximo n pixels ainda conta como coincidência |
| `--sync` | espera a GPU depois de toda submission (o padrão espera só onde a captura tomou estado novo no meio) |
| `--out <dir>`, `--no-flip` | outra pasta para os PNGs; sem virar verticalmente para uma origem embaixo-esquerda |

O que ele imprime (a captura do túnel, `android-replay` na época da escrita):

```
loaded in 9434 ms: 2147 regions (2650 MB, 0 not at their live address), 2070 pools, 1 textures, 190 programs, 2 descriptor pools, 4 submits; 2727 CPU-written textures marked
replay 1: submits CPU 203.97 ms (recording, 4 submits), GPU 80.56 ms, wall 345.05 ms to the last fence; 7458 draws (0 skipped), 10 dispatches, 86 render passes, 0 pipelines made; before the frame: 4627.4 ms applying the captured state (4490 images filled), 866.1 ms preparing
replay 2: submits CPU 59.24 ms (recording, 4 submits), GPU 20.99 ms, wall 123.48 ms to the last fence; …
replay 3: submits CPU 45.15 ms (recording, 4 submits), GPU 22.67 ms, wall 96.50 ms to the last fence; …
best of the warm replays: submits CPU 45.15 ms, GPU 20.99 ms, wall 96.50 ms
```

- **submits CPU**: o tempo em `mtl_sub_begin` … `mtl_sub_commit` para as submissions do frame (gravando os comandos Vulkan: o número que o trabalho de performance move); **GPU**: o tempo de GPU do frame pelos timestamps das submissions (`mtl_gpu_ms`); **wall**: da primeira submission à última fence.
- O Replay 1 paga pelo primeiro uso de tudo (imagens feitas, módulos, as tabelas bindless) e não é uma cronometragem; os replays 2 e seguintes são. "before the frame" (estado aplicado, imagens preenchidas, os uploads de textura da primeira submission) fica fora das cronometragens.
- Uma execução inteira com `--warm 3` leva cerca de 30 s: 9 s carregando a captura (o arquivo está no page cache depois da primeira execução), 5 s para o estado do replay 1 e cerca de 3 s para cada um dos seguintes.
- A linha de digest compara a imagem apresentada com a ao vivo, pixel por pixel, e nomeia o PNG. Status de saída 0 = a mesma imagem (dentro de `--max-pixels`), 1 = diferente, 2 = não consegue reproduzir (`[RED] nxreplay:` diz por quê).

**Conhecido (em aberto):** os replays não são iguais ao frame ao vivo. A imagem do replay 1 está muito errada (a maior parte da cena preta com manchas de contorno branco, 4,6 M pixels diferem); os replays 2 e seguintes estão perto da imagem ao vivo (a mesma cena, poses dos personagens ligeiramente diferentes, 1,1 M pixels diferem) e são iguais entre si bit a bit. Depois do replay 1, cerca de 520 MB de memória de pools (130.000 blocos de 4 KiB) foram escritos pelo replay onde o frame ao vivo os deixou inalterados; os replays 2 e 3 escrevem cerca de 300 MB cada assim. Então algumas escritas de GPU do replay caem onde as da execução ao vivo não caem, ou estado que o frame lê de antes (a saída de GPU de um frame anterior) não é todo capturado; isso é a próxima coisa a achar, com `--dump` dos alvos do frame e a linha de comparação dos pools. Até lá use os replays 2 e seguintes: a cronometragem deles é a do frame, e a imagem deles mostra as mesmas manchas de NaN do jogo.

Para um resultado de compute errado, `--env DM_VK_DISPATCH_LOG=1` registra cada dispatch do frame (o índice, a chave do shader, os grupos, o tamanho do workgroup, a memória compartilhada, as flags) e `DM_VK_DISPATCH_SKIP=<from>[,<to>]` registra aqueles dispatches sem rodá-los; com `--dump-gpu` isso mostra qual dispatch escreve uma imagem.

Para a caça ao NaN (`DM_VK_NANSCAN`): `--env DM_VK_NANSCAN=1` (ou 2) no replay registra a primeira passada escrevendo texels NaN / Inf como no jogo, em segundos por tentativa em vez de um carregamento de nível.
A varredura de passes lê só attachments de cor dos render passes. Para um compute pass (uma rede de upscaler, um pass de empacotamento), acrescente `DM_VK_NANSCAN_CS=<shader key>[,<key>…]` (ou `all`): depois de cada dispatch daqueles shaders, as imagens que ele vincula são varridas, as imagens de storage (saída) e as texturas (entrada) dele, e registradas como `NaN scan: frame … dispatch <key> … storage image|texture binding …: BAD|fine; … NaN + … infinite texels, … all-zero, … big (a component >= 256), largest component …`. Cada binding é varrido pelo formato que a view própria do dispatch o lê (o formato de registro do ID da textura como Vulkan, como o `view_of` o faz), não o da imagem: uma textura `E5B9G9R9` sobre uma imagem `B10G11R11` é lida como RGB9E5; onde a lista de formatos da imagem não tem o formato da view, o dispatch lê o próprio formato da imagem, e a varredura também (a descrição diz `NOT in the image's format list`). Uma view `R32_UINT` é decodificada como palavras RGB9E5, e o RGB9E5 não tem NaN nem infinito, então para ela só all-zero, big e largest contam. As primeiras linhas descrevem cada binding uma vez por imagem e formato de view (ID da textura, `scanned through view format NVN … VkFormat …`, a imagem); uma textura registrada de novo a cada frame sobre a mesma imagem não é descrita de novo. Cada linha de varredura nomeia os formatos Vulkan da view e da imagem. Entrada errada significa o produtor ou o caminho do formato. Entrada certa e saída errada significam a rede. `DM_VK_NANSCAN=3` varre só os dispatches: as varreduras de passes e as de dispatches compartilham 1024 resultados por submission, e com `1` os passes podem enchê-los antes da rede rodar (registrado como `NaN scan: 1024 scans in one submission`). O nível inteiro é varrido: quando o frame preenche só parte da imagem (resolução dinâmica), compare o tamanho da imagem e as primeiras posições com o tamanho do frame. Puxe o log inteiro (`files/logs/`): as descrições dos bindings vêm uma vez, no primeiro dispatch.

## Execuções de teste rápidas

Para uma execução de teste automatizada que só tem que chegar ao primeiro nível, uma linha no `env.txt` faz isso tão rápido quanto o jogo permite:

```sh
adb shell 'f=/sdcard/Android/data/dev.ankanative.android.test/files/env.txt; printf "DM_WATCH_STALL=25\nDM_FASTBOOT=1\n" > $f; chmod 666 $f'
```

O `DM_FASTBOOT=1` define os switches do `fastboot_env` do profile que o `env.txt` já não define (cada um é registrado como `DM_FASTBOOT: KEY=VALUE`). Para o Donkey Kong Bananza:

- `DM_KEYS=f1:A~0.1*3000/12`: A a cada 12 frames apresentados a partir do primeiro, em vez de segundos fixos: cada tela que espera por A o recebe dentro de um quinto de segundo.
- `DM_MARK_DRAWS=3000`: quando 5 frames seguidos têm 3.000 draws ou mais (o título tem 63, a tela de carregamento 6-9, o primeiro nível 5.500 e mais), o log diz `[mark] frame <n>: <draws> draws (…), <s> s from process start, wall clock <epoch>`. Espere por essa linha em vez de um tempo fixo: `adb shell grep -a -m1 '\[mark\]' <newest log>`.
- `DM_MARK_KEYS_STOP=1`: sem pressões depois do mark, então o nível não é jogado pelo script.

Comece de uma cópia nova do save-semente ([Saves](#saves)). Medido no PNM-N49 (2026-11-02, `am start` até o mark): 49,2 s com `DM_KEYS=15:A*38/2`, 44,7, 49,3 e 50,4 s com `DM_FASTBOOT=1`. As pressões tiram cerca de 3 s do tempo do primeiro present até o carregamento do nível (23,2 s → 20,2-20,5 s); a variação de execução para execução do resto é igualmente grande. O que sobra é do jogo ([a nota do jogo](../dm/games/040074A01BF12000/README.md#boot-android)): o primeiro present 6-9 s depois do início, o aviso de autosave antes do título (cerca de 15 s, não aceita input), o título e os menus do save (cerca de 4 s) e o carregamento do nível (18-21 s, 0 pipelines novos; as linhas `[fs]` do `DM_FS=1` colocam o tempo de abrir e ler do host em cerca de 3 s disso)

## Pular a intro

Um auxílio de depuração que leva um jogo pela sua intro (filmes de abertura, título, cutscenes de novo jogo) até o gameplay sem nenhuma pressão do testador. Desligado por padrão; nada muda para um jogo até ele ser armado ou ligado. Lado do host: `dm/host/hle_hid.c` (`DM_SKIP`).

- **Arme-o**: a folha ⋯ Gerenciar do jogo › Configurações do jogo › **Pular a intro (debug)** (`DM_SKIP=1` a partir do próximo início), ou uma linha `DM_SKIP=1` no `env.txt` ou `env-<title ID>.txt`. O log diz `DM_SKIP: armed: hold Minus+Plus for 1 s …`.
- **Dispare-o no jogo**: segure **− e +** juntos por 1 s (controles na tela, um controle ou um teclado no player 1). Segurá-los de novo desliga. Do momento em que ambos estão pressionados até ambos soltarem, nenhum chega ao jogo, então a combinação não abre menu nenhum. O painel dentro do jogo (Voltar) também tem **Pular a intro agora** / **Parar de pular a intro** enquanto está armado (e sempre no app de teste). Uma mensagem curta diz quando liga ou desliga.
- **De um script**: `android/tools/nxctl.sh skip on` (também `off`, `toggle`); isto funciona sem armar. `DM_SKIP=auto` no `env.txt` liga-o no início; `DM_FASTBOOT=1` faz isso para um jogo cujo `fastboot_env` o tem (Ocarina of Time).

Enquanto está ligado:

- o player 1 recebe **A** por 3 frames apresentados a cada 12, somados ao input próprio do player (`DM_SKIP_KEYS=A,Plus` pressiona A e depois Plus, em turnos; `DM_SKIP_EVERY` muda o 12);
- **os filmes terminam cedo**: cada stream `nn::codec` (Switch 2) ou `nn::movie` recebe sua flag de fim de stream no pacote depois da sua primeira imagem, e os pacotes seguintes são descartados, então o jogo vê o que vê no fim natural de um filme (`DM_SKIP: AV1 decoder 1: the stream ends at picture …`). `DM_SKIP_MOVIES=0` deixa os filmes em paz. Experimental: um jogo que também espera pelo som do filme ainda pode esperar pela sua duração inteira;
- ele **se desliga no gameplay**: o `skip_until_draws` do profile (`<draws> [seconds]`, [dm/games/README.md](../dm/games/README.md); `DM_SKIP_UNTIL` sobrepõe) dá os draws de um frame da primeira cena de gameplay; depois de 5 frames seguidos nessa contagem ele loga `DM_SKIP: gameplay: …` e para de pressionar tantos segundos depois (`DM_SKIP: off (gameplay reached)`). Sem a chave ele para depois de `DM_SKIP_MAX` segundos (300) ou manualmente.

Ocarina of Time (`0400486024DEC000`): `skip_until_draws 500 10` (filmes e o título desenham 2-16 por frame, Kokiri Forest 647 ou mais) e `fastboot_env DM_SKIP=auto DM_MARK_DRAWS=500`. Um teste que só tem que chegar à floresta:

```sh
P=dev.ankanative.android.oot; f=/sdcard/Android/data/$P/files/env.txt
adb shell "echo DM_FASTBOOT=1 >> $f; chmod 666 $f"            # or DM_SKIP=auto
adb shell am start -n $P/dev.nxnative.android.MainActivity      # then start the game
NX_PKG=$P android/tools/nxctl.sh log 400 | grep -E 'DM_SKIP|\[mark\]'
```

Antes do skip, a mesma execução levava uns 10 minutos do lançamento à floresta: a maior parte esperando por script, depois o filme de novo jogo de 155 s, que A não pula.

## Controle ao vivo

Enquanto o jogo roda, um script de teste pode mudar os switches do replay, tirar screenshots e ler estatísticas de frame sem reiniciá-lo: o launcher define `DM_CTL=<app files>/ctl.txt` (`/sdcard/Android/data/dev.ankanative.android.test/files/ctl.txt`), o host lê cada linha acrescentada a ele em até 100 ms (`dm/host/hle_hid.c`, o `DM_CTL` do Mac; no Android ele começa no fim do arquivo, então linhas de uma execução antiga não são reproduzidas), e o `android/tools/nxctl.sh` acrescenta uma linha com adb e imprime as linhas de log que respondem a ela (`[vk] ctl: …`):

```sh
android/tools/nxctl.sh env DM_VK_DRAW=0          # draws off from the next frame; env DM_VK_DRAW= clears it again
android/tools/nxctl.sh capture 30                # one line per frame: ms, draws, passes, barriers, clears / copies, submissions, recording ms, GPU ms
android/tools/nxctl.sh screenshot shot.png       # the next game frame (the window texture, before the present scales it) → ./shot.png
android/tools/nxctl.sh texshot 6234 t.png        # one texture by its serial (from the log), level 0 layer 0, at the next present → ./t.png: which step of a frame went wrong
android/tools/nxctl.sh bufshot 12 1 /sdcard/Android/data/<app>/files/b.bin   # storage slot 1 as dispatch 12 of a frame left it (indices from DM_VK_DISPATCH_LOG=1), raw, copied on the GPU right after it
android/tools/nxctl.sh memdump 4185f26320 0x100000 /sdcard/Android/data/<app>/files/m.bin   # guest memory, raw (the host's DM_CTL memdump: hex address, stops at an unreadable page)
android/tools/nxctl.sh stats                     # the last 60 frames' averages, fps, placed memory, the switches set live
android/tools/nxctl.sh fps 30                    # the frame rate cap, as the in-game panel sets it (0 = the game's own)
android/tools/nxctl.sh skip on                   # the intro skip (#intro-skip): A pressed, movies ended early, off by itself at gameplay
android/tools/nxctl.sh A~0.2                     # any other DM_CTL line: a press, dump, ...
android/tools/nxctl.sh log 40                    # the run's last log lines (nothing sent)
```

O `NX_SERIAL` escolhe o celular (padrão: o do adb, `ANDROID_SERIAL` ou o único celular), o `NX_PKG` o app (padrão o app de teste `dev.ankanative.android.test`). Sem o script: `adb shell 'f=/sdcard/Android/data/dev.ankanative.android.test/files/ctl.txt; echo "env DM_VK_STATS=0" >> $f; chmod 666 $f'` (o `chmod` como para o `env.txt`). `DM_CTL=` no `env.txt` desliga o leitor.

Uma linha `env KEY=VALUE` entra na fila e é aplicada no próximo limite de frame: a primeira submissão depois do próximo present, sob a trava de submit do host, então nenhuma submissão fica meio registrada com o valor antigo. Ela define o valor para os switches do replay e no ambiente do processo (switches do host lidos no uso também o veem), então aumenta um contador de geração: cada switch lido através de `VKA_ENV` (`dm/gfx/vk_android/vka.h`) é lido de novo no seu próximo uso. A linha do log diz de que tipo é o switch:

| Tipo | Switches |
|---|---|
| live: o próximo frame | `DM_VK_DRAW`, `DM_VK_STATS`, `DM_VK_HANG_S`, `DM_VK_HANG_EXIT`, `DM_VK_FRAME_RATE` (uma mudança da taxa do jogo depois dela), `DM_VK_FRAME_RATE_HOLD` (a próxima mudança de taxa), `DM_VK_PCACHE_SAVE_S` (o próximo save), `DM_VK_PCACHE_SAVE_MS` (o próximo pipeline feito), `DM_VK_GPL_PROBE`, `DM_VK_GPL_GIVEUP` (a próxima library recusada), `DM_VK_TEX_TRACK` (texturas feitas daqui em diante), `DM_VK_PRETRANSFORM` (faz um swapchain novo), `DM_VK_FSR`, `DM_VK_FSR_SHARP` (o próximo present), `DM_VK_CHUNK`, `DM_VK_PASS_CLEARS`, `DM_VK_PASS_KEEP`, `DM_VK_DYN_FILTER`, `DM_VK_PROF`, `DM_VK_RPROF` (cada um a partir da próxima submissão), `DM_VK_ROWS` (o próximo re-upload), `DM_VK_SYNCLOG` (a próxima espera do jogo), `DM_VK_UBWC` (imagens feitas daqui em diante; as anteriores ficam com a sua), `DM_VK_IMG_UPGRADE` (`0`: uma texture vinculada como image de shader sem a flag IMAGE do NVN deixa de ser recriada com uso de storage; a próxima vinculação), `DM_VK_NANSCAN_DUMP` (lido no uso), `DM_LSFG`, `DM_LSFG_MULT`, `DM_LSFG_TARGET`, `DM_LSFG_FLOW` (como os ajustes dentro do jogo) |
| só reinício (logado como tal) | `DM_VK_VALIDATE`, `DM_VK_PLACED`, `DM_VK_PCACHE`, `DM_VK_SHLIB`, `DM_VK_SHLIB_CHECK`, `DM_VK_GS_CLAIM`, `DM_VK_ADPF`, `DM_VK_NANSCAN`, `DM_VK_NANSCAN_STEP`, `DM_VK_NANSCAN_DRAWS`, `DM_VK_NANSCAN_SERIAL`, `DM_VK_NANSCAN_FS`, `DM_VK_NANSCAN_CS` (os buffers e a probe da varredura são montados uma vez), `DM_VK_WORKER`, `DM_VK_WORKER_FRAMES` (a thread do replay inicia uma vez), `DM_VK_TEST_HANG`, `DM_LSFG_DLL`, `DM_FPS`, `DM_VK_PREWARM`, `DM_VK_PREWARM_MAX`, `DM_VK_PREWARM_FILE` (as threads de prewarm começam com o primeiro pipeline), `DM_VK_PSO_ASYNC` (as threads dela começam com o primeiro pipeline) |

Um switch `DM_VK_*` novo é live quando lido com `VKA_ENV("DM_VK_…", default)` (um número; `vka_env_str` para uma string) em vez de `getenv`; um que dimensiona um objeto de dispositivo ou um cache entra na tabela de só-reinício em `vka_ctl.c`, ou a sua mudança também tem que descartar aquele cache. Um local de chamada `VKA_ENV` pode rodar na thread do jogo e no trabalhador do replay ao mesmo tempo: o seu cache (`vka_envc`) guarda um valor atômico, e só uma thread por vez o atualiza, e só para uma geração mais nova, então nem um valor rasgado nem um mais antigo são lidos como atuais.

Tempo de GPU e a linha do tempo do frame, ao vivo: o `nxctl.sh env DM_VK_GPUPROF=50` cronometra um frame em 50 passo a passo (timestamps de GPU entre as barreiras completas: linhas `gpu prof:` com o tempo de GPU do frame por tipo e os passos mais caros, `DM_VK_GPUPROF_LIST=1` cada passo; ele deixa o frame um pouco mais lento, então não julgue a taxa de quadros com ele ligado); `env DM_VK_SYNCLOG=200` loga as próximas 200 esperas do jogo junto com os tempos de gravação e de enfileiramento de cada submissão, os seus timestamp reports e quando foram escritos (linhas `sync …`, um só relógio em ms). O `DM_VK_GPU_FLOOR` (padrão 100) e o `DM_VK_CHUNK_PASS` (padrão 600) são o piso do relógio da GPU e as divisões de pass ([rendering.md](../docs/reference/rendering.md#gpu-end-held-back-by-recording-and-a-clocked-down-gpu-on-the-vulkan-replay)); no `nxreplay` o piso também mantém os tempos de GPU estáveis de replay para replay (`--env DM_VK_GPU_FLOOR=100`). O `DM_VK_ENC=0` registra os comandos Vulkan na própria thread do replay em vez da thread de codificação de comandos ([rendering.md](../docs/reference/rendering.md#recording-held-back-by-the-drivers-command-recording-on-the-vulkan-replay)); no `nxreplay` use `--env DM_VK_WORKER=0` para o tempo de gravação (com o trabalhador, `submits CPU` é só a entrega). A divisão de um pass, ao vivo: `nxctl.sh env DM_VK_GPUPROF_SPLIT=1` (para um tamanho só, também `DM_VK_GPUPROF_SPLIT_W=683` e `DM_VK_GPUPROF_SPLIT_H=384`, `DM_VK_GPUPROF_SPLIT_K=<k>` para um timestamp a cada k draws também; cada switch é uma linha de controle `env KEY=VALUE`: `nxctl.sh env A=1 B=2` manda duas linhas, enquanto uma linha `env A=1 B=2` escrita à mão é recusada, o replay leva um switch por linha) faz toda segunda frame perfilada ser dividida (uma em 30 quando o `DM_VK_GPUPROF` é 0): os passes dela desse tamanho ganham um timestamp a cada mudança de pipeline, rodam em sysmem (uma barreira de vértice dentro do pass, `DM_VK_GPUPROF_SPLIT_SYSMEM=0` não), e linhas `gpu prof split:` dão os 3 passes mais caros de `DM_VK_GPUPROF_SPLIT_MIN` (32) draws ou mais por pipeline (chaves de vertex e fragment shader: ms, fatia, draws, índices, grupos), com o pass do mesmo tamanho na frame perfilada simples de antes (o GMEM ou sysmem do driver: a razão, sobre todo pass desse tamanho dos dois lados, é quanto o sysmem custa). O `DM_VK_GPUPROF_SPLIT_STATS=1` acrescenta uma consulta de estatísticas de pipeline por grupo: invocações de fragment shader por pipeline, por pixel do pass (camadas sombreadas: overdraw) e ns por invocação. A linha `gpu prof:` dá o relógio da GPU (KGSL sysfs) no relatório e quando a frame foi armada, ou diz que não consegue lê-lo. Frames normais não são mudadas; `env DM_VK_GPUPROF_SPLIT=0` termina. Os buffers de um draw: `nxctl.sh env DM_VK_DRAWDUMP_FS=<chave de fragment shader>` loga, uma vez por conjunto, todo buffer que o próximo draw com aquele fragment shader vincula (faixas guest contra as do host) e salva os primeiros 4 KiB deles embaixo de `files/drawdump/`; para o loop de luzes do Model.Product.110 do Ocarina of Time também as contagens de luzes por cluster contra o teto `c6[113].x`. Os bins não são visíveis ao replay: `TU_DEBUG=perf` (início do processo) faz o Turnip logar por que ele cai para sysmem.

## Teste 2: autoteste gráfico

Não precisa de jogo nem pack: ele confere o dispositivo Vulkan, a memória guest colocada, o replay NVN (clears, cópias, um draw e um dispatch de compute pelo caminho da library de shaders) e o present no celular. Instale o APK (passo 2), comece o log no Mac, abra o nxnative e toque em **Autoteste gráfico**:

```sh
adb logcat -c && adb logcat -v time nxnative:V DEBUG:V libc:V '*:S' | tee nxnative-selftest.txt
```

A tela fica vermelha com um quadrado branco de 128×128 perto do canto inferior esquerdo e, no meio, um triângulo xadrez magenta com células transparentes (o alpha test as descarta) que gira uma vez a cada 6 s, numa imagem 16:9 (barras pretas num painel mais largo), com os controles na tela do jogo, e experimente-os ali (pressões acendem e dão tick, as pontas dos sticks seguem o dedo; eles não controlam nada); os ajustes deles estão no painel lateral (Voltar). O quadrado tem que ficar perto do canto inferior esquerdo nas duas orientações deitadas (vire o celular enquanto roda). O fundo cicla vermelho → amarelo → verde → ciano → azul → magenta, mais ou menos a cada 6 s por volta, por 10 s (600 frames), e então o topo da tela mostra **PASS (return code 0)** em verde. **FAIL** em vermelho mostra o código de retorno: o número de checagens que falharam, ou -1 quando o host não tem autoteste. Voltar retorna à lista de jogos; **Compartilhar log** então manda `selftest-<time>.log`.

As linhas do log, em ordem:

1. `[nxlaunch] graphics selftest: NativeAYN … ; device …` e `nxhost_vk_selftest(0x…, 600)`;
2. `[vk] device Adreno (TM) 840, Vulkan 1.4.… ; placed maps yes …, update-after-bind yes, display timing yes …`;
3. `[vk] draw path: push descriptors yes (…), vertex input dynamic state yes, null descriptors yes, 8-bit indices …, geometry yes …; uniform alignment …, storage alignment …` (o que os draws usam: mande esta linha de volta, diga ela o que disser);
4. `[vk] window 0x…`, `[vk] selftest: 64 MiB of guest memory at 0x…, placed Vulkan memory`, `[vk] selftest: shaders made; …`;
5. `[vk] swapchain 1080x2420, 3 images, format 37, FIFO, transform 0x2 (surface 0x2: pre-rotated), …` (a orientação própria do painel: a imagem é desenhada girada, então o compositor não a gira; o formato 37 é R8G8B8A8_UNORM), e `[vk] window frame rate 60 fps`. Virar o celular (o outro deitado) loga `[vk] surface rotation 0x2 → 0x8: new swapchain`, e a imagem fica na vertical certa. `DM_VK_PRETRANSFORM=0` no `env.txt` desliga a pré-rotação numa execução de jogo (`transform 0x1 (surface 0x2: the compositor rotates)`);
6. `[vk] selftest: fence 1 done; texel (1200,650) ff0000ff (want ff0000ff), texel (100,100) ffffffff (want ffffffff), event 1 (want 1)`;
7. `[vk] selftest: draw: 1 drawn, 0 skipped; triangle centre texel (640,360) ffff00ff (want ffff00ff: a white texel times the tint), texel (685,360) ff0000ff (want ff0000ff: dropped by the alpha test)`: o caminho do draw (módulos de shader de uma library, pipeline, set 0, os pools de textura e sampler, o uniform buffer, os bytes c1, atributos de vértice, a constante de especialização e a referência do alpha test);
8. `[vk] selftest: dispatch: 1 run; storage words 5 and 255: 4005, 4255 (want 4005, 4255)`: um dispatch de compute com um storage buffer e o segmento de driver c0;
9. `[vk] selftest: 600 frames presented to the window; 1 graphics + 1 compute pipelines (0 failed), …; passed`, então `[nxlaunch] graphics selftest returned 0: PASS`.

O código de retorno conta os grupos de checagens que falharam: memória colocada, as leituras do clear / quadrado / event, o draw, o dispatch. Qualquer linha `[RED] vk:` entre 4 e 9 (um pipeline que falhou, um shader recusado, um layout) é o que mandar de volta; também um crash no driver (linhas `DEBUG` nomeando `vulkan.adreno.so`).

No emulador do Android o dispositivo não tem `VK_EXT_map_memory_placed`: o log diz `placed maps no` e `NOT placed (no placed maps): a mapped Vulkan allocation instead, for the other checks`, todas as outras checagens passam (o emulador não tem push descriptors, vertex input dynamic state nem null descriptors, então ele roda os fallbacks desses) e o teste termina com FAIL (código de retorno 1) com o triângulo na tela. Isso é esperado ali; no celular, um `placed maps yes` faltando ou qualquer outra divergência é o achado a mandar de volta. O emulador reporta ROTATE_90 no deitado também, então ele pré-gira; dois `adb emu rotate` exercitam ROTATE_270 e a recriação do swapchain.

### A library de shaders do jogo no compilador do dispositivo

`DM_VK_SHLIB_CHECK=1` no `env.txt` (as execuções de diagnóstico do passo 6) faz a primeira execução do jogo, quando a library dele carrega, construir todo módulo de shader dela e todo pipeline de compute, e logar `[vk] shader library check: 22852 shaders, … modules made, … refused, … without a layout, 205 compute pipelines (… failed), … s`. Os recusados são esperados (os quatro geometry shaders passthrough da NVIDIA; veja android/SHADERS.md); um pipeline de compute ou layout que falha é um achado. Remova a linha depois: os pipelines que ela construiu ficam no pipeline cache (`vk_pipelines.bin` na pasta temporária do jogo), a checagem em si não é necessária de novo.

## Emulador do Android

O emulador Android arm64 num Mac Apple silicon dá boot no lado de CPU do jogo tão longe quanto o celular deveria: pelos oito estágios, o frame loop do menu do título e, com pressões roteirizadas, até o primeiro nível. Ele não tem `VK_EXT_map_memory_placed` (SwiftShader), então a GPU não vê pool de memória nenhum; o host percebe isso sozinho (`placed maps no`, depois `[RED] vk: no VK_EXT_map_memory_placed` e um `[RED] vk: memory pool … is not in placed guest RAM` por pool) e continua, com ou sem `DM_VK_PLACED=0`. O que ele não pode mostrar: memória guest colocada e suas faltas de mprotect / write-protect, o driver Adreno, a velocidade e os limites de memória do celular.

Um AVD com espaço para a pasta do jogo (uns 10 GB) e 8 GB de memória, mantido no drive do dump (o disco da pasta pessoal está quase cheio):

```sh
export ANDROID_AVD_HOME=$D/avd
echo no | ~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager create avd -n nxemu -k 'system-images;android-36.1;google_apis_playstore;arm64-v8a' -d medium_phone
sed -i '' -e 's/^disk.dataPartition.size=.*/disk.dataPartition.size=24G/' -e 's/^hw.ramSize=.*/hw.ramSize=8192/' $ANDROID_AVD_HOME/nxemu.avd/config.ini
~/Library/Android/sdk/emulator/emulator -avd nxemu -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect -partition-size 24576 &
adb -s emulator-5554 wait-for-device
```

Depois os passos 2–4 e 6 com `adb -s emulator-5554` (o push leva uns 5 minutos), e inicie o jogo com a intent do passo 5. Sem display a janela é a virtual do emulador, e eventos de `adb shell input` vêm de um dispositivo virtual, ao qual o launcher não dá um player; pressione botões com o input roteirizado do host, no `env.txt` ([Switches de diagnóstico](#switches-de-diagnostico)), por exemplo A a cada 5 s de 25 s para deixar o título:

```sh
adb -s emulator-5554 shell 'f=/sdcard/Android/data/dev.ankanative.android.test/files/env.txt; printf "DM_KEYS=25:A,30:A,35:A,40:A,45:A,50:A\nDM_FS=1\n" > $f; chmod 666 $f'
```

Uma boa execução no emulador (Donkey Kong Bananza 458752): estágios 1–8 em uns 7 s (2 s quando o RomFS está no page cache), o menu do título a 67 draws por frame; depois das pressões o primeiro nível carrega e a sua abertura roda a uns 740 draws por frame com 3,5 GB residentes. As linhas `[RED]` são os stubs de gráficos de [PORTING.md](PORTING.md#stubs-nesta-build) e os de memória colocada acima. A nota do jogo tem os detalhes ([Boot (Android)](../dm/games/040074A01BF12000/README.md#boot-android)).

## Overlay e geração de frames (LSFG)

O overlay está ligado por padrão: a tag da build no canto superior esquerdo (`NativeAYN v<version>`, a mesma da primeira linha do log `[nxlaunch] build …`) e no canto superior direito o ponto de throttle com a taxa de quadros (`<n> fps`; `-- fps` antes da primeira frame e enquanto nada é apresentado), o tempo do frame e o tempo de GPU, os tetos do relógio da CPU e a bateria (README, Overlay: os limiares do ponto; cada item tem o seu switch). Desligue-o na tela principal ou na seção Overlay do painel lateral; `DM_OVERLAY=0` no `env.txt` esconde-o numa execução.

A geração de frames precisa do `Lossless.dll` próprio do usuário (Lossless Scaling para Windows):

1. Copie-o para o celular, por exemplo `adb push Lossless.dll /sdcard/Documents/Emulation/bios/` (ou `Switch2/`, ou `Download/`); ou escolha-o com **Geração de frames (LSFG)… → Escolher Lossless.dll**. O diálogo então diz onde ele foi achado.
2. Ligue a **Geração de frames (LSFG)** ali (ou nas configurações do jogo), escolha 2x / 3x / 4x ou uma taxa-alvo, e a resolução da estimativa de movimento (menor é mais rápido).
3. Inicie o jogo. O overlay mostra `<game> fps → <presented> fps (LSFG)` quando a taxa de quadros do jogo está estável (o pacer do Eden espera cerca de um segundo, e não gera nada enquanto o jogo roda abaixo de 10 fps). O log: `[nxlaunch] frame generation: on; Lossless.dll from …`, `[vk] frame generation: 25 shaders from …`, `[vk] lsfg: chain WxH, flow …`, `[vk] frame generation: first 1 generated frame(s) …`, e a cada 15 s `[vk] fps: … (frame generation on)`.

Sem uma DLL (ou numa GPU sem o que os shaders precisam), ligá-la deixa o jogo rodando como antes; o overlay diz `LSFG unavailable` e o log `[RED] vk: frame generation unavailable: no Lossless.dll …`. Só com `env.txt`: `DM_LSFG=1`, `DM_LSFG_MULT=2`, `DM_LSFG_TARGET=0`, `DM_LSFG_FLOW=75`, `DM_LSFG_DLL=<path>`.
