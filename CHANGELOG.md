# Changelog do NativeAYN

Histórico das releases do **NativeAYN**, o recompilador de Switch 2 para Android. O grosso do comportamento novo fica atrás de switches `DM_*` (todos em
[docs/env_switches.tsv](docs/env_switches.tsv)); os que o Ocarina of Time precisa são armados pelo profile do jogo
([dm/games/0400486024DEC000/game.tsv](dm/games/0400486024DEC000/game.tsv)), então os outros jogos mantêm o comportamento antigo enquanto um switch não for definido.
O app é construído sobre o projeto base ([créditos em NOTICE.md](NOTICE.md)); o histórico dele antes do NativeAYN não faz parte deste arquivo — o versionamento
do NativeAYN começa no 0.1.0. Builds de lançamento: [docs/RELEASING.md](docs/RELEASING.md).

## NativeAYN 0.1.3 (2026-11-09)

### Ajuste

- Os botões **PT / EN** do menu do jogo ficaram menores (26 dp, texto 12) e ganharam um respiro do rótulo "Idioma / Language:" e da régua acima.

### Diagnóstico

- **O app agora grava o stack trace de qualquer crash** na pasta de logs (`<jogo>-<data>-crash.log`), que vai junto com o Compartilhar log.
- Se a montagem do menu do jogo falhar, o jogo não morre junto: o menu mostra a falha em si (e o log registra). Mandem o log pelo Ajustes › Compartilhar log.

## NativeAYN 0.1.2 (2026-11-09)

### Novo

- **Idioma, português ou inglês**: escolha PT / EN no topo do menu do jogo (o menu se refaz no idioma na hora) e também em Home › Ajustes › Idioma. Sem escolha
  feita, o app segue o idioma do celular. Toda a interface tem as duas línguas; o texto técnico (logs, caminhos, o editor do layout dos controles na tela)
  fica em inglês nas duas.
- **Ordem do menu do jogo**: Mapeamento de controle primeiro, depois Controles na tela, Overlay, e o resto.
- **Parar o jogo**: a linha do menu pede uma única confirmação ("Parar o jogo?" → Parar / Deixar rodando) e encerra a sessão; a pausa continua só como opção
  automática ao voltar do segundo plano, ligável/desligável em Ajustes do jogo › Pausar ao voltar.

## NativeAYN 0.1.1 (2026-11-09)

### Corrigido

- **Mapeamento de controle volta a funcionar no painel do jogo**: as seções do painel em forma de folha são diálogos, e um diálogo tem janela própria — com a
  folha do Mapeamento de controle aberta, o botão apertado no controle morria na janela do diálogo e nunca chegava à captura da linha, então nenhuma linha era
  atribuída. A janela da folha agora entrega a tecla física à captura antes de qualquer outro tratamento; fechar a folha também desarma uma espera de tecla.

### Novo desde o 0.1.0

- **Home como prateleira de console** e a paleta "Console Shelf" (fundo carvão liso, box art com sombra leve), com a arte de capa lida do pack, de um `.jpg`
  ao lado do `.nup` ou do `icon.jpg` da pasta do dump.
- **Menu do jogo em pausa**: o painel dentro do jogo vira um menu de uma linha por seção, cada uma abrindo a sua folha; o mapeamento de controle é uma dessas seções.
- **Atualização dentro do app**: um build `-PnxAppId` (`dev.nativeayn.android`) verifica as releases do projeto no GitHub na abertura e nos Ajustes, e instala a
  nova versão por cima sem perder o jogo nem os saves (mesma chave de assinatura).

## NativeAYN 0.1.0 (2026-11-09)

Primeira release. Ocarina of Time (`0400486024DEC000`, update v131072) é o título principal, medido no AYN Odin 3 (Adreno 830, Android 15); os profiles de
Donkey Kong Bananza (`040074A01BF12000`) e Mario Kart World (`0400C3F00006E000`) foram carregados do projeto base e não revalidados nesta release.

### Identidade

- **App próprio**: pacote `dev.nativeayn.android` (build renomeado com `-PnxAppId`), marca própria (o "N sobre um infinito" em laranja e azul), ícone de launcher e uma paleta sempre escura em cinza-carvão
  com acento azul (o estilo "Console Shelf": fundo liso sem estampa, cards compactos, tiles com gradiente; a régua de acento azul só aparece nas telas internas, a Home
  não tem régua nem título de lista). O README e a documentação do projeto
  estão em português brasileiro.
- **Versão aparece, git não**: Home, o overlay e o Sobre mostram `v<versão>` (por exemplo, `v0.1.0`); branch e commit não aparecem em nenhum texto visível ao
  usuário (o id do assinante nos logs ainda identifica um build).

### Jogar

- **Home como prateleira de console**: os jogos ficam lado a lado numa linha rolável, cada um como um tile de box art com sombra leve e o nome e o estado embaixo; o card
  selecionado ganha sombra mais funda e um leve destaque, e o D-pad de um gamepad percorre os cards; tocar no tile joga, pressionar por longo (ou o ⋯ no canto)
  abre Gerenciar; **Adicionar jogo** é o último tile da prateleira (um "+"), e sem jogo nenhum ele é o único tile (o empty state é a própria prateleira); o pedido de
  Acesso a todos os arquivos, quando falta, é o primeiro tile da prateleira, do lado do "+". A arte vem do `icon.jpg` do pack (o `pack_android.py` copia o ícone que o `nx_extract` lê
  dos dados de controle), de um `<nome do jogo>.jpg` ou `<title ID>.jpg` ao lado do `.nup`, ou de um `icon.jpg` na pasta do dump; sem arte, a inicial do nome
  no gradiente azul.
- **Menu dentro do jogo com cara de menu de pausa de console** (`SidePanel`): uma linha por seção — Frame rate cap, Frame generation, Upscaling, On-screen
  controls, Overlay, Controller mapping, Intro skip (onde armado) e Pause or stop — cada uma com um tile de ícone, o nome, o estado
  atual como subtítulo e um chevron; a linha abre um diálogo com os controles completos daquela seção, e toda mudança se aplica ao jogo que está rodando na
  hora e fica guardada para o próximo início. Os ícones são marcas vetoriais próprias no azul do painel: a ampulheta de Ocarina, a Triforce e o pedaço de
  Triforce, um D-pad, o olho Sheikah, uma mira, fast-forward e pausa. Abre pela alça na borda direita, por Back ou pelo botão Home de um gamepad; com gamepad,
  o D-pad percorre as linhas e A abre; B fecha.
- **Mapeamento de controle** (novo): a seção Controller do painel mapeia os botões físicos do portátil para os botões do jogo — toque na linha, aperte o botão,
  salvo por pad conectado, com dead zone ajustável; o mapa se reaplica quando o pad reconecta.
- **Tela sempre ligada** (novo): o jogo e a Home mantêm a tela acesa enquanto abertos; o timer de inatividade do sistema não apaga mais uma sessão em execução.
- **Controles na tela**: o botão redondo sempre visível no centro de cima sumiu; a chave **Show the on-screen buttons** ficou na seção Controles na tela do
  painel, junto com as outras configurações de controle.
- **Geração de frames e FSR 1 ao vivo**: as chaves do painel (geração de frames, upscaling FSR 1 e nitidez) se aplicam ao jogo em execução sem voltar à Home.

### Atualizações

- **Atualização dentro do app funciona para este app**: o app NativeAYN verifica suas próprias releases do GitHub (`renelima/NativeAYN`) no início e em
  Configurações › Atualizações, builds debug incluídos (flag `NX_PRODUCT` no build `-PnxAppId`); antes só o app de lançamento assinado verificava, e os builds
  que este projeto distribui nunca ofereciam atualização. O diálogo mostra a versão e as notas da release; baixar confere o tamanho e o SHA-256 do asset contra
  o digest da API antes de instalar pelo `PackageInstaller`. Uma atualização instala por cima do app em execução, então o app instalado e o APK da release
  precisam estar assinados com a mesma chave (hoje ambos usam a chave debug).

### Compilação

- `-PnxAppId=<id>` compila o app renomeado; o APK de teste do Odin 3 é o build debug (`-PnxGame`/`-PnxGames` fixam o jogo nos testes locais).
- O `pack_android.py` agora copia o `icon.jpg` da extração para o pack (a arte da caixa que a Home mostra).
