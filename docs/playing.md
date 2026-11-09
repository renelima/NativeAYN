# Jogando

Como compilar e instalar um jogo: [README](mac-and-iphone.md#como-jogar).

- **Configurações:** uma tela antes do jogo começar define frame rate, resolução, modo docked ou portátil, idioma, rumble e mais. Toque em **Play**; suas escolhas são lembradas. O que cada uma faz: [Configurações](settings.md).
- **Saves e logs** ficam no app Arquivos, sob o nome do jogo: `save/` e `log.txt` (a execução anterior é `log.prev.txt`). Quando algo dá errado, envie o `log.txt`.
- **Reinstalação:** algumas ferramentas de sideloading dão a um app reinstalado uma nova pasta de dados. Copie `save/` e `pso_user.tsv` da pasta antiga para manter seu progresso.

## Controles de toque

Toque em **2D** / **3D** no topo para trocar de layout; o app lembra sua escolha. Wonder, Engage, Dread, 13 Sentinels, Link's Awakening, Ace Attorney Investigations Collection, Unicorn Overlord, Rhythm Heaven Groove, Octopath Traveler 0, Pokémon Legends: Z-A, Cuphead, Absolum, Blasphemous 2, Brain Memory Training Academy, The Great Ace Attorney Chronicles, STEINS;GATE RE:BOOT, Big Brain Academy: Brain vs. Brain, Patrick's Parabox, Celeste, Raging Loop e Mario vs. Donkey Kong começam em 2D, os demais jogos em 3D (o quick save de STEINS;GATE RE:BOOT é R3, no layout 3D).

No 3D, o analógico direito (a câmera na maioria dos jogos) não é desenhado: deslize no terço direito da tela, começando em qualquer ponto fora dos botões. Em Super Mario Galaxy e Super Mario Galaxy 2, outros toques fora dos controles movem o ponteiro; em Captain Toad: Treasure Tracker eles tocam a tela do jogo (blocos de toque, atordoar inimigos), e em Big Brain Academy: Brain vs. Brain também (seus menus e atividades).

Um controle conectado esconde os controles de toque e mantém todos os botões.

## Rumble

A vibração do jogo usa o motor háptico do iPhone ou um controle conectado compatível. Controles com saídas hápticas separadas para esquerda e direita mantêm esses efeitos separados. **Game rumble** e **Rumble strength** na tela de configurações controlam isso de forma independente de **Touch feedback**. O rumble do telefone aproxima os efeitos do Switch; a sensação física e a compatibilidade de controles ainda exigem testes em dispositivos.

## Movimento (giroscópio)

Um controle que fornece dados de giroscópio pelo suporte a controles da Apple (DualSense, DualShock 4) aciona os controles de movimento do jogo, no Mac e no iPhone: mira e puzzles de inclinação em jogos que leem o movimento do controle, como Breath of the Wild com a opção Motion Controls ativada. Um controle sem giroscópio (Backbone One, Razer Kishi e outros controles nos quais o telefone se encaixa) usa o giroscópio do próprio iPhone, como um Switch no modo portátil. O movimento segue o controle no modo docked, o padrão.

`DM_PHONE_MOTION=0` em `Documents/env.txt` desliga o giroscópio do telefone; `DM_MOTION=0` desliga todo o movimento. Se o movimento se comportar mal, faça o pack com `--no-motion` (`MOTION = False` no Spyder) para gerar um app sem ele; `DM_MOTION=1` em `Documents/env.txt` o religa.

O giroscópio do Switch Pro Controller no Mac ainda depende do suporte HID em desenvolvimento no [PR #296](https://github.com/seedds/nxnative/pull/296).

**Sacudir.** Sacuda o iPhone para sacudir o controle, nos jogos que leem o movimento dele: os movimentos de motion de Super Mario Odyssey (como o arremesso do chapéu com um sacudir) precisam disso, e Odyssey só os aceita em seu modo docked padrão. Uma pancada leve não conta; um giro seco do telefone conta.

## Idioma do jogo

Inglês por padrão. Escolha outro ao fazer o pack com `--language` (`-l`), por código ou por nome: `-l ja`, `-l zh-Hans`, `-l French` (`LANGUAGE` no Spyder). Precisa ser um idioma que o jogo tenha; o script imprime os idiomas do jogo e para em um que ele não tem. Para mudar sem reconstruir, escolha outro na tela de configurações.

## Configurações em arquivo

Linhas como `DM_OPMODE=0` (modo portátil: resolução menor, frame rate maior) em `Documents/env.txt` vencem a tela de configurações, que então as mostra acinzentadas ([a lista completa](settings.md#configuracoes-em-arquivo)). `DM_FPS_TARGET=30` mantém Breath of the Wild, Tears of the Kingdom, Octopath Traveler 0 e Octopath Traveler II nos seus 30 fps originais, o que economiza bateria; `DM_PRESENT_INTERVAL=game` faz o mesmo para Monster Train 2. `DM_SHOW_FPS=1` mostra o frame rate no canto superior esquerdo (`--show-fps` ao fazer o pack o liga desde o início). `DM_LAUNCHER=0` pula a tela de configurações.

## No Mac

Um app para Mac mostra as configurações do jogo em uma janela antes do jogo começar ([A janela de configurações no Mac](settings.md#a-janela-de-configuracoes-no-mac)). Seu `env.txt` fica em `~/Library/Application Support/nxnative/<title ID>/env.txt`, e seu log em `~/Library/Logs/nxnative/<title ID>.log`. A biblioteca do [NX2Apple](https://github.com/LNPANDY/NX2Apple) também pode iniciá-lo com as configurações que você escolher lá.

Um controle que o macOS pareia mas o suporte a controles da Apple não lista, como um 8BitDo em seu modo próprio (Ultimate 2C Wireless via Bluetooth), é lido como um gamepad HID bruto, com analógicos completos: ele ocupa um player como qualquer outro controle, e o log diz `controller '<name>' (HID) connected`. Seus botões seguem a numeração da 8BitDo por padrão; o pad de outro fabricante pode numerá-los de outro modo, e o log diz isso quando acontece. `DM_HID_MAP=A=1,B=2,X=4,Y=5` em `env.txt` os remapeia (aqui: as letras impressas em vez das posições), `DM_HID_LOG=1` registra o número de cada botão pressionado, e `DM_HID_PADS=0` desativa isso.

O ponteiro do mouse se esconde quando você pressiona uma tecla ou um botão do controle, ou move um analógico, enquanto ele está sobre a imagem do jogo. Mover o mouse o mostra de novo, assim como trocar para outra janela ou app.

## Shaders na primeira aparição

Pequenas travadas na primeira vez que uma cena nova aparece são normais: o app compila os gráficos dela uma vez e os lembra para o próximo início. O que ele lembra, e o que a lista do jogo nomeia, é construído antes do jogo começar, atrás de "Preparing shaders" e de uma barra de progresso quando isso leva mais de meio segundo.
