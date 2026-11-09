# Contribuindo

Issues e pull requests são bem-vindos. Esta página diz como uma mudança entra no projeto; o que o código faz e onde as coisas ficam está em [docs/development.md](docs/development.md).

## Como relatar um problema

Abra uma issue com o formulário de bug report. Ele pede o que serve para rastrear um problema: o commit do nxnative, a versão da shader chain, o title ID e a versão do jogo, o dispositivo e o sistema operacional, como o jogo foi compilado e executado, as substituições de ambiente e o log completo da execução. [Jogando](docs/playing.md) diz onde fica o log do app do iPhone; um app de Mac escreve em `~/Library/Logs/nxnative/<title ID>.log`.

`python3 scripts/bug_report.py <title ID | path to the .app | NSP folder>` junta a maior parte disso em um zip para anexar: `report.md` com o jogo, o commit, a versão da chain, o dispositivo, as configurações e a lista de arquivos, além dos logs do app de Mac, dos logs do script do NSP em `<title ID>_build/logs` e dos crash reports recentes. Use `--ios-log log.txt` para um log do iPhone. Ele lê apenas logs de texto e configurações, nunca arquivos do jogo nem chaves; escreve sua pasta home como `~` e descarta linhas que pareçam material de chaves. Confira o zip antes de anexá-lo.

## Nunca envie arquivos de jogo ou chaves

Nunca faça commit nem anexe arquivos de jogo, chaves, title keys ou qualquer coisa extraída de um jogo: RomFS, módulos de código, shaders, dumps. Arquivos de jogo e suas extrações ficam na pasta NSP do jogo ([docs/development.md](docs/development.md)). Logs, screenshots, title IDs, build IDs e offsets podem.

## Antes de começar

- Leia [docs/agents/README.md](docs/agents/README.md) e [docs/development.md](docs/development.md): eles listam por onde começar em cada tipo de tarefa e as regras de profiles, de conhecimento por build e da shader chain de tradução.
- Crash, travamento ou imagem errada: procure primeiro no [índice de sintomas](docs/agents/symptoms.md) o texto que está no log.
- Outro jogo: rode `scripts/build_ipa_from_nsp.py` na pasta NSP dele e siga [Portar outro jogo](docs/development.md#port-another-game).
- Prefira uma correção que valha para todos os jogos a uma regra específica de um jogo, e corrija um bug na causa ([docs/development.md](docs/development.md)).

## Um pull request

- **Uma mudança por PR, a partir do `main`.** Faça rebase sobre o `main` em vez de mesclá-lo.
- **Commits.** Cada mensagem de commit diz o que mudou e por quê, em frases simples em inglês, como o histórico faz: o sintoma, a causa, a correção e o que foi medido ou verificado.
- **Para onde vão as descobertas.** Um fato sobre um jogo vai na sua nota (`dm/games/<id>/README.md`). Um mecanismo geral ganha uma entrada em `docs/reference/<subsystem>.md` (Symptom, Cause, Fix, Found on), seguida de `python3 dm/tools/docs_symptoms.py`. Um novo jogo recebe seu profile, sua nota e sua linha na tabela de jogos ([dm/games/README.md](dm/games/README.md)).
- **A shader chain.** Depois de mudar uma de suas ferramentas, rode `python3 dm/gfx/tools/dmsh_chain.py --header` e faça commit de `dm/gfx/nvn/dmsh_chain.h` junto com a mudança. Os shaders de todos os jogos são reconstruídos depois disso, então diga quais jogos você verificou.
- **Testes.** A CI roda os testes do host, os testes de gráficos apenas de CPU, os testes das ferramentas e os builds ([Integração contínua](docs/development.md#continuous-integration)). Os runners do GitHub não têm Metal 4, então rode os testes de GPU em um Mac com Apple silicon: `make -C dm/gfx/tests GAME=<id> run`.
- **O texto do PR.** Preencha o template: o quê e por quê, o que foi verificado (testes, jogos, dispositivos) e quais docs mudaram.

## Mudanças grandes e outras plataformas

- **Combine o escopo primeiro.** Abra uma issue antes de escrever uma mudança que adiciona uma plataforma ou classe de dispositivo, adiciona uma segunda implementação de um caminho existente (por exemplo, um caminho de renderização para GPUs sem Metal 4) ou passa de cerca de 1.000 linhas. Um PR grande feito sobre um `main` antigo raramente consegue ser revisado ou mesclado, por melhor testado que tenha sido.
- **Um assunto por PR.** Um front end novo, uma linha de base de plataforma e as correções de um jogo são PRs separados, cada um sobre o `main` atual.
- **Dispositivos compatíveis** são os que o [README.md](README.md) cita: iOS / iPadOS 26 ou posterior em um iPhone com chip A17 Pro ou posterior ou um iPad com chip M1 ou posterior, e Macs com Apple silicon. Suportar dispositivos mais antigos ou Macs com Intel é decisão do mantenedor, então pergunte primeiro em uma issue: isso adicionaria caminhos de código que toda mudança futura precisa continuar fazendo funcionar e que poucos contribuidores conseguem testar.

## Front ends gráficos

Um app que faz pack de jogos, os lista ou os inicia (uma janela de build, uma biblioteca de jogos) é um projeto separado que usa o nxnative como base, não uma parte deste repositório. O [NX2Apple](https://github.com/LNPANDY/NX2Apple) é um exemplo, e o [README.md](README.md) aponta os usuários para ele. Um app assim não precisa de nenhuma mudança aqui para funcionar: o script do NSP informa suas etapas como linhas JSON (`--progress json`), um app de Mac ou IPA já compilado leva a lista de configurações do seu jogo (`dm/launcher.json`, gerado de `dm/games/settings.tsv`), e o app aceita essas configurações como switches de ambiente ou como escolhas salvas em suas preferências, que toda inicialização lê ([Front ends](docs/frontends.md)). Quando um front end precisar de mais do nxnative (um novo evento, um campo na lista de configurações), abra uma issue ou um PR pequeno só para essa parte.

A tela de configurações dentro do app de iPhone e iPad e a janela de configurações dentro de um app de Mac fazem parte do nxnative ([Configurações](docs/settings.md)): elas desenham a mesma lista e guardam as mesmas escolhas salvas que qualquer front end.

## Como as mudanças entram

O mantenedor revisa todo PR. Um PR costuma entrar como um commit squashado ou refeito no `main` que dá crédito ao autor (`Co-authored-by`), e a PR é fechada em vez de mesclada; isso é esperado. O mantenedor pode fazer rebase ou refazer um PR que conflita com o `main` ao aceitá-lo.
