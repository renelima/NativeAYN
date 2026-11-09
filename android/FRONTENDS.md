# Frontends de emulador

O NativeAYN inicia um jogo para um frontend de emulador (ES-DE, Daijisho, Beacon, Pegasus, ou qualquer coisa que rode `am start`). O frontend lista os seus arquivos `.nup` ([NUP.md](NUP.md)) e manda o que você escolher para o app. O componente do app para isso é:

```
dev.ankanative.android/dev.ankanative.android.Launch
```

O `Launch` é um alias do `FrontendLaunch`, e o seu nome segue o ID da aplicação, então o do app de teste é `dev.ankanative.android.test/dev.ankanative.android.test.Launch`. O alias, e o `Open` para gerenciadores de arquivos, são as únicas partes exportadas do app que iniciam jogos.

## O que o app aceita

| Forma | Exemplo |
|---|---|
| `ACTION_VIEW` com um URI de armazenamento (SAF) do arquivo, de uma pasta escolhida no frontend | `content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fgame.nup` |
| `ACTION_VIEW` com um URI de provider cujo caminho traz o caminho do arquivo (um caminho "root" de FileProvider) | `content://<frontend>.fileprovider/root/storage/emulated/0/ROMs/game.nup` |
| `ACTION_VIEW` com um URI `file://` | `file:///storage/emulated/0/ROMs/game.nup` |
| O extra `game`: o caminho do arquivo | `-e game /storage/emulated/0/ROMs/game.nup` |
| O extra `title_id`: um jogo que já está na Home | `-e title_id 040074A01BF12000` |

O extra `game` com o caminho do arquivo é a forma mais confiável. O app abre jogos por caminho, então um URI é apenas convertido de volta num caminho (`PathUtil.documentToPath`, `Frontend.embeddedPath`). Um URI de provider que não traz caminho (Recent, um número do MediaStore, um arquivo na nuvem) é recusado e o app diz o porquê. Os aliases `/sdcard` e `/storage/self/primary` são os mesmos arquivos que `/storage/emulated/0` (`Frontend.normal`). Uma pasta de jogo também funciona, dada pelo seu caminho, mas os frontends listam arquivos, então o `.nup` é o formato para eles. Um frontend não pode escolher a pasta do pack nem a pasta do save: esses extras são só do adb ([TESTING.md](TESTING.md)).

## O que acontece

1. Quando o card do jogo está pronto para jogar, o jogo inicia de uma vez e nada do launcher é mostrado. Isso significa que o jogo está na Home, ou que o pack dele é encontrado ao lado dele como o Adicionar jogo o encontra, e que o pack serve: esta build do jogo, código pronto, a biblioteca de shaders deste app, nenhum backup ou restauração de save em execução. Um arquivo novo que está pronto tem o seu card adicionado primeiro. Quando você parar o jogo (o painel dentro do jogo › Parar o jogo, ou Voltar numa inicialização que falhou), o frontend volta.
2. Qualquer coisa que precise de você abre a Home com o jogo, como se você tivesse tocado em Jogar ou o tivesse escolhido com o Adicionar jogo: sem pack, Preparar, shaders a criar ou atualizar, um pack que não serve, Acesso a todos os arquivos. Depois disso, inicie-o de novo pelo frontend.

O Acesso a todos os arquivos tem que estar ligado (o app pede isso na Home).

## Na própria lista de Switch do frontend

Um frontend não precisa de mudança nenhuma própria: cada um deixa você adicionar um emulador pelo componente dele. Até um frontend trazer o NativeAYN na sua lista de emuladores embutida, você o adiciona uma vez, à mão:

- ES-DE: um sistema em `custom_systems/es_systems.xml` com o mesmo `<name>` que um embutido (`switch`) substitui esse sistema. Copie a entrada `switch` embutida do `es_systems.xml` do próprio ES-DE, adicione `.nup` ao `<extension>` dela e adicione as linhas `<command>` do NativeAYN abaixo. Então o NativeAYN passa a ser um dos emuladores alternativos do sistema Switch.
- Daijisho e Beacon: adicione um player à plataforma Switch existente com os argumentos `am start` abaixo, e adicione `nup` às extensões dela.

## ES-DE

O ES-DE lê emuladores customizados de `ES-DE/custom_systems/es_find_rules.xml` e sistemas customizados de `ES-DE/custom_systems/es_systems.xml`, na pasta de dados do aplicativo dele. Adicione o emulador:

```xml
<?xml version="1.0"?>
<ruleList>
    <emulator name="ANKA-NATIVE">
        <rule type="androidpackage">
            <entry>dev.ankanative.android/dev.ankanative.android.Launch</entry>
        </rule>
    </emulator>
</ruleList>
```

Depois adicione um sistema que lista arquivos `.nup` e o usa. Mude o `path` para a sua pasta de ROM:

```xml
<?xml version="1.0"?>
<systemList>
    <system>
        <name>ankanative</name>
        <fullname>NativeAYN</fullname>
        <path>%ROMPATH%/ankanative</path>
        <extension>.nup .NUP</extension>
        <command label="NativeAYN">%EMULATOR_ANKA-NATIVE% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%</command>
        <command label="NativeAYN (path)">%EMULATOR_ANKA-NATIVE% %EXTRA_game%=%ROM%</command>
        <platform>switch</platform>
        <theme>switch</theme>
    </system>
</systemList>
```

O `%ROMSAF%` precisa que a pasta de ROM do ES-DE seja escolhida através do acesso ao armazenamento dele. Se não for, escolha o comando "(path)" nos emuladores alternativos do sistema.

## Daijisho

O Daijisho não tem tela para adicionar um player à mão. As plataformas dele são arquivos JSON: as embutidas vêm do repositório dele (`TapiocaFox/Daijishou`, `platforms/NintendoSwitch.json`), e ele importa um arquivo de plataforma que você der. Uma plataforma Switch com o NativeAYN na sua lista de players é esse arquivo com estas entradas adicionadas no topo da `playerList` e o `revisionNumber` aumentado em um:

```json
{
  "name": "switch - NativeAYN",
  "uniqueId": "switch.dev.ankanative.android",
  "description": "Supported extensions: nup.",
  "acceptedFilenameRegex": "^(.*)\\.(?:nup)$",
  "amStartArguments": "-n dev.ankanative.android/dev.ankanative.android.Launch\n -e game {file.path}",
  "killPackageProcesses": false,
  "killPackageProcessesWarning": false,
  "extra": ""
}
```

Uma segunda entrada com `-a android.intent.action.VIEW\n -d {file.uri}` é a forma de URI, que é a que os outros players de Switch do Daijisho usam. Para o app de teste, use `dev.ankanative.android.test` nos dois lugares. Importe o arquivo no Daijisho (Configurações › Biblioteca › importar uma plataforma), depois escolha o player na plataforma Switch. Quando o repositório do Daijisho tiver a entrada, todo usuário do Daijisho a recebe sem importar.

## Beacon, Pegasus e outros

Qualquer frontend que rode `am start` funciona com os mesmos argumentos. No `metadata.pegasus.txt` do Pegasus:

```
launch: am start -n dev.ankanative.android/dev.ankanative.android.Launch -e game "{file.path}"
```

De um computador, com o celular no adb:

```bash
adb shell am start -n dev.ankanative.android/dev.ankanative.android.Launch -e game /storage/emulated/0/ROMs/game.nup
```

## Abrindo um .nup por um gerenciador de arquivos

Um segundo alias, `<application ID>.Open`, mostra o app em "Abrir com" para um `.nup` de um gerenciador de arquivos. Isso funciona quando o gerenciador de arquivos manda um caminho ou um URI que traz um (veja acima).

## Quando o frontend diz "Unable to find explicit activity class"

No Android 15, um intent explícito chega a um componente exportado que tem intent filters apenas quando o intent casa com um deles, e um intent sem action não casa com nenhum (o shell do adb é isento, então os testes com adb não mostram isso). O Daijisho encontrou ambos: o player `-d {file.uri}` dele manda `VIEW` sem tipo MIME, e o player `-e game {file.path}` dele não manda action. Então o alias `Launch` não tem intent filters nenhuns, e aceita todas as formas. O filter de "Abrir com" para gerenciadores de arquivos fica num segundo alias, `<application ID>.Open` (2026-11-04). Se um frontend ainda receber esta mensagem, o log mostra o que ele mandou: `adb logcat | grep -E "AmStart args|START u0"`.

## Código

- `FrontendLaunch` (a activity do alias): lê o intent, confere o jogo como o Jogar faz (`ready`), então inicia a `GameActivity` com o extra `frontend`, ou manda o jogo para a Home (`MainActivity.fromFrontend`). Ele não desenha nada (uma activity translúcida numa task própria, não nos Recentes).
- `Frontend`: a parte em Java puro (URIs, caminhos, cards, title IDs), conferida na JVM pelo `dm/tools/tests/test_android_frontend.py` (`FrontendJvmTest`).
- `GameActivity.backToFrontend`: quando o jogo termina, a task do app vai para trás do frontend em vez de mostrar a Home.

## Status

- 2026-11-04, celular Honor, app de teste: por adb, o extra `game` (com `/sdcard`), o `ACTION_VIEW` de um URI `file://` e de um URI de documento SAF, e o extra `title_id` iniciam o jogo direto cada um; um arquivo ausente mostra o diálogo dele. Do Daijisho, na plataforma Switch dele importada com os players do NativeAYN, ambos os players (`-e game {file.path}` e `-a VIEW -d {file.uri}`) iniciam o jogo. ES-DE, Beacon e Pegasus não foram tentados.
