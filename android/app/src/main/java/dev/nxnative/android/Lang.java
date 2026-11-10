package dev.nxnative.android;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * The app's two languages, English and Brazilian Portuguese. The English text in the code is the key:
 * Lang.t(ctx, "...") returns the table's Portuguese line when the language is pt, the key itself otherwise.
 * The language is the player's choice (the in-game menu's PT / EN, Settings > Language, stored in Prefs);
 * unset it follows the device: a Portuguese device starts in Portuguese. A key missing from the table simply
 * stays English, so an untranslated line never breaks a screen. Code text (logs, notes, paths, keys) is not
 * run through this: only what the player reads.
 */
final class Lang {
    private Lang() {}

    static boolean pt(Context c) { return Prefs.lang(c).startsWith("pt"); }

    /** The UI text for ctx's language: the Portuguese line when there is one, the English key otherwise. A null ctx (log lines, notes) always stays English. */
    static String t(Context c, String en) { return c != null && pt(c) ? TR.getOrDefault(en, en) : en; }

    private static final Map<String, String> TR = new HashMap<>();

    private static void p(String en, String pt) { TR.put(en, pt); }

    static {
        // ---- dialog buttons and shared words ----
        p("Done", "Concluído");
        p("Cancel", "Cancelar");
        p("OK", "OK");
        p("Save", "Salvar");
        p("Clear", "Limpar");
        p("On", "Ligado");
        p("Off", "Desligado");
        p("Auto", "Auto");
        p("Default", "Padrão");
        p("default", "padrão");
        p("Settings", "Ajustes");

        // ---- the in-game menu (SidePanel) ----
        p("In-game menu", "Menu do jogo");
        p("Controller mapping", "Mapeamento de controle");
        p("The gamepad's buttons, per controller", "Os botões do gamepad, por controle");
        p("On-screen controls", "Controles na tela");
        p("Buttons, opacity, layout", "Botões, opacidade, layout");
        p("Overlay", "Overlay");
        p("Shown over the game", "Exibido sobre o jogo");
        p("Hidden", "Oculto");
        p("Frame rate cap", "Limite de taxa de quadros");
        p("Frame rate cap \u00b7 ", "Limite de taxa de quadros \u00b7 ");
        p("Frame generation", "Geração de quadros");
        p("Upscaling", "Upscaling");
        p("FSR 1 on", "FSR 1 ligado");
        p("Intro skip", "Pular abertura");
        p("Intro skip (debug)", "Pular abertura (debug)");
        p("Skipping the intro now", "Pulando a abertura agora");
        p("Off (hold \u2212 and + for 1 s in the game)", "Desligado (segure \u2212 e + por 1 s no jogo)");
        p("Stop", "Parar");
        p("End this session, with a confirmation", "Encerra esta sessão, com confirmação");
        p("This build has no frame rate cap", "Este build não tem limite de taxa de quadros");
        p("Starting\u2026", "Iniciando\u2026");
        p("This game picks its own frame rate", "Este jogo escolhe a própria taxa de quadros");
        p("Now ", "Agora ");
        p(", capped at ", ", limitado a ");
        p(", the game's own", ", a taxa do próprio jogo");
        p("The cap from now on (Auto = the game's own rate)", "O limite daqui para frente (Auto = a taxa do próprio jogo)");
        p("Now: ", "Agora: ");
        p("Presses A every few frames and ends movies early until gameplay, then stops by itself. In the game: hold \u2212 and + together for 1 s.",
                "Aperta A a cada poucos quadros e termina filmes mais cedo até o gameplay, e para sozinho. No jogo: segure \u2212 e + juntos por 1 s.");
        p("Stop the intro skip", "Parar de pular a abertura");
        p("Skip the intro now", "Pular a abertura agora");
        p("Stop the game?", "Parar o jogo?");
        p("The game ends; unsaved progress is lost.", "O jogo termina; progresso não salvo é perdido.");
        p("Keep running", "Deixar rodando");

        // ---- Controller mapping (ControllerControls) ----
        p("No controller is connected. Connect one and press its buttons here to map them; the map is kept for it.",
                "Nenhum controle conectado. Conecte um e aperte seus botões aqui para mapeá-los; o mapa fica guardado para ele.");
        p("Press a button on ", "Aperte um botão em ");
        p(" for \"", " para \"");
        p("\" (Back or Escape cancels)", "\" (Voltar ou Escape cancela)");
        p("Tap a row, then press the button you want for it on ",
                "Toque na linha e depois aperte o botão que você quer para ela em ");
        p("Reset", "Restaurar");
        p("Clear all (back to the default table)", "Limpar tudo (volta à tabela padrão)");
        p("Stick dead zone ", "Zona morta dos analógicos ");
        p("Key ", "A tecla ");
        p(" already does another button: press a different key, or Reset that row first.",
                " já faz outro botão: aperte outra tecla, ou use \"Restaurar\" nessa linha primeiro.");
        p("the controller", "o controle");
        // the rows' game targets and the keys' names
        p("\u2212 (Minus)", "\u2212 (Menos)");
        p("+ (Plus)", "+ (Mais)");
        p("L3 (left stick click)", "L3 (clique do analógico esquerdo)");
        p("R3 (right stick click)", "R3 (clique do analógico direito)");
        p("D-pad up", "D-pad cima");
        p("D-pad down", "D-pad baixo");
        p("D-pad left", "D-pad esquerda");
        p("D-pad right", "D-pad direita");
        p("D-pad centre", "D-pad centro");
        p("Button A (bottom)", "Botão A (de baixo)");
        p("Button B (right)", "Botão B (da direita)");
        p("Button X (left)", "Botão X (da esquerda)");
        p("Button Y (top)", "Botão Y (de cima)");
        p("Button L1", "Botão L1");
        p("Button R1", "Botão R1");
        p("Button L2", "Botão L2");
        p("Button R2", "Botão R2");
        p("Left stick click", "Clique do analógico esquerdo");
        p("Right stick click", "Clique do analógico direito");
        p("Start", "Start");
        p("Select", "Select");
        p("Home / guide", "Home / guia");
        p("Button C", "Botão C");
        p("Button Z", "Botão Z");
        p("Back", "Voltar");
        p("Escape", "Escape");
        p("key ", "tecla ");

        // ---- on-screen controls (TouchControls) ----
        p("Show the on-screen buttons", "Mostrar os botões na tela");
        p("Vibrate on press", "Vibrar ao apertar");
        p("Show the log over the game", "Mostrar o log sobre o jogo");
        p("Opacity ", "Opacidade ");
        p("Size ", "Tamanho ");
        p("Reset the controls", "Restaurar os controles");
        p("Picture position (this screen)", "Posição da imagem (nesta tela)");
        p("Top and Bottom park the picture in a black bar; the controls keep the space it leaves.",
                "Topo e Base deixam a imagem numa faixa preta; os controles ficam com o espaço que sobra.");
        p("Centre", "Centro");
        p("Edit the controls layout", "Editar o layout dos controles");

        // ---- the overlay (Overlay) ----
        p("Show the overlay over the game", "Mostrar o overlay sobre o jogo");
        p("Frame rate and frame time", "Taxa de quadros e tempo de quadro");
        p("Throttle dot (green / yellow / red)", "Ponto de throttle (verde / amarelo / vermelho)");
        p("GPU time a frame", "Tempo da GPU por quadro");
        p("CPU clock caps", "Tetos do clock da CPU");
        p("Battery temperature and level", "Temperatura e nível da bateria");
        p("Memory: the game's and the phone's", "Memória: a do jogo e a do celular");

        // ---- frame generation (Lsfg) ----
        p("Frame generation (Lossless Scaling)", "Geração de quadros (Lossless Scaling)");
        p("Lossless Scaling, your own Lossless.dll", "Lossless Scaling, seu próprio Lossless.dll");
        p("Not in this build (built with -PnxLsfg=false).", "Não neste build (compilado com -PnxLsfg=false).");
        p("Frame generation (LSFG)", "Geração de quadros (LSFG)");
        p("Frames per game frame", "Quadros por quadro do jogo");
        p("Target frame rate (the multiplier follows it)", "Taxa de quadros alvo (o multiplicador acompanha)");
        p("Fixed", "Fixo");
        p("Motion estimation resolution (lower is faster; Auto = the game's own)",
                "Resolução da estimativa de movimento (menor é mais rápido; Auto = a do próprio jogo)");
        p("Mode: performance (the only shader set Eden's LSFG uses)",
                "Modo: performance (o único conjunto de shaders que o LSFG do Eden usa)");

        // ---- upscaling (Fsr) ----
        p("FSR 1 (sharper when the picture is scaled up to the screen)",
                "FSR 1 (mais nítido quando a imagem é ampliada para a tela)");
        p("Sharpness", "Nitidez");

        // ---- Settings (MainActivity) ----
        p("Updates", "Atualizações");
        p("Check for updates", "Verificar atualizações");
        p("Game defaults", "Padrões dos jogos");
        p("Resolution", "Resolução");
        p("A game's own settings (\u22ef on its card) override these.",
                "Os ajustes próprios de um jogo (\u22ef no card dele) mandam sobre estes.");
        p("System", "Sistema");
        p("GPU driver", "Driver da GPU");
        p("Folders", "Pastas");
        p("Game, pack and save folders", "Pastas do jogo, do pack e do save");
        p("Language", "Idioma");
        p("Português / English", "Português / English");
        p("Help", "Ajuda");
        p("Readiness check", "Checagem de prontidão");
        p("Can this phone run your games?", "Este celular roda os seus jogos?");
        p("Graphics self-test", "Autoteste gráfico");
        p("Needs no game", "Não precisa de jogo");
        p("Share log", "Compartilhar log");
        p("The newest log", "O log mais recente");
        p("About", "Sobre");
        p("License: GPL-3.0-or-later as a whole", "Licença: GPL-3.0-or-later como um todo");
        p(". (this build includes GPL-3.0-or-later code: ", ". (este build inclui código GPL-3.0-or-later: ");
        p("LSFG frame generation and the shader translator", "a geração de quadros LSFG e o tradutor de shaders");
        p("LSFG frame generation", "a geração de quadros LSFG");
        p("the shader translator", "o tradutor de shaders");
        p("On \u00b7 ", "Ligado \u00b7 ");
        p(" of ", " de ");
        p(" items", " itens");
        p("Not in this build", "Não neste build");
        p("System (the phone's own driver)", "Sistema (o driver do próprio celular)");

        // ---- the shelf and the cards (MainActivity) ----
        p("Grant file access to read your game dump.", "Conceda acesso a arquivos para ler o seu dump do jogo.");
        p("Allow", "Permitir");
        p("Allow file access", "Permitir acesso a arquivos");
        p("Add game", "Adicionar jogo");
        p("Not supported", "Não suportado");
        p("Checking.", "Checando.");
        p("Play", "Jogar");
        p("Prepare", "Preparar");
        p("Prepare shaders", "Preparar shaders");
        p("Ready", "Pronto");
        p("Needs preparing", "Precisa preparar");
        p("No pack", "Sem pack");
        p("Pack unfinished", "Pack inacabado");
        p("Preparing.", "Preparando.");
        p("Shaders needed", "Faltam shaders");
        p("Shaders need one update", "Shaders precisam de uma atualização");
        p("Ready \u00b7 updating shaders", "Pronto \u00b7 atualizando shaders");
        p("Ready \u00b7 shaders need one update", "Pronto \u00b7 shaders precisam de uma atualização");
        p("Pack from other tools", "Pack de outras ferramentas");
        p("This game's pack was made by other tools than this app's. It usually still runs. If the game misbehaves, ? > Prepare again remakes it from your dump.",
                "O pack deste jogo foi feito por outras ferramentas, não por este app. Normalmente ainda roda. Se o jogo se comportar mal, ? > Preparar de novo o refaz a partir do seu dump.");
        p("Manage ", "Gerenciar ");

        // ---- a game's Manage sheet (MainActivity) ----
        p("Game settings", "Ajustes do jogo");
        p("Back up saves", "Backup dos saves");
        p("Restore saves", "Restaurar saves");
        p("Import save", "Importar save");
        p("Prepare again", "Preparar de novo");
        p("Export pack.", "Exportar pack.");
        p("One .zip, into a folder you pick", "Um .zip, na pasta que você escolher");
        p("Import pack.", "Importar pack.");
        p("A .zip or a pack folder", "Um .zip ou uma pasta de pack");
        p("Remove from list", "Remover da lista");
        p("Title ID: ", "Title ID: ");
        p("Game: ", "Jogo: ");
        p("Pack: ", "Pack: ");
        p("Shaders: ", "Shaders: ");
        p("present (", "presentes (");
        p("Saves: ", "Saves: ");
        p("not set", "não definido");

        // ---- Add game and its dialogs (MainActivity) ----
        p("Add game: your dump is.", "Adicionar jogo: como está o seu dump.");
        p("A folder (with base/, and updates/ if any)", "Uma pasta (com base/, e updates/ se houver)");
        p("A .nup file", "Um arquivo .nup");
        p("Pick your dump folder (with base/, and updates/ if any)", "Escolha a pasta do seu dump (com base/, e updates/ se houver)");
        p("Pick your game's .nup file", "Escolha o arquivo .nup do seu jogo");
        p("Checking ", "Checando ");
        p(" game.", " jogo.");
        p(" games.", " jogos.");
        p("This game", "Este jogo");
        p("Added: ", "Adicionados: ");
        p("Needs Prepare: ", "Precisam de Preparar: ");
        p("Not supported by this app: ", "Não suportados por este app: ");
        p("No pack found: ", "Nenhum pack encontrado: ");
        p("Not added: ", "Não adicionados: ");
        p("Another dump of the same game was on the list already, so both cards are kept: ",
                "Já havia outro dump do mesmo jogo na lista, então os dois cards ficam: ");
        p("No game added", "Nenhum jogo adicionado");
        p("Games added", "Jogos adicionados");
        p("Replace ", "Substituir ");
        p("Another dump of ", "Outro dump de ");
        p(" is on your list:", " está na sua lista:");
        p("Use ", "Usar ");
        p(" instead? Only the list entry changes: no dump, pack or save is touched.",
                " no lugar? Só a entrada da lista muda: nenhum dump, pack ou save é tocado.");
        p("Replace", "Substituir");
        p("Keep both", "Manter os dois");
        p("No pack found for ", "Nenhum pack encontrado para ");
        p(" next to ", " ao lado de ");
        p("No pack found for this ", "Nenhum pack encontrado para este ");
        p(", and it is not a dump Prepare can start from.", ", e não é um dump de que o Preparar possa partir.");
        p("Another version of ", "Outra versão de ");
        p("No pack for this ", "Nenhum pack para este ");
        p("Pick a pack folder", "Escolher uma pasta de pack");
        p("Pick the pack folder (android-pack, with pack.tsv)", "Escolha a pasta do pack (android-pack, com pack.tsv)");
        p("Open settings", "Abrir ajustes");
        p("The app needs All files access to read your game dump. Allow it, then come back here.",
                "O app precisa de acesso a todos os arquivos para ler o seu dump do jogo. Permita e volte aqui.");
        p("That file is not on this phone's storage by a path the app can read (",
                "Esse arquivo não está no armazenamento deste celular por um caminho que o app consiga ler (");
        p("). Pick it from the phone's storage.", "). Escolha-o no armazenamento do celular.");
        p("That folder is not on this phone's storage (", "Essa pasta não está no armazenamento deste celular (");
        p("Lossless.dll installed", "Lossless.dll instalado");
        p("Lossless.dll not installed: ", "Lossless.dll não instalado: ");

        // ---- Game settings (GameSettings) ----
        p("For this game only, from its next start. Default follows Settings.",
                "Só para este jogo, a partir do seu próximo início. Padrão segue os Ajustes.");
        p("Game resolution", "Resolução do jogo");
        p("Scene resolution", "Resolução da cena");
        p("Scene resolution · ", "Resolução da cena · ");
        p("Game default (the game's own palette)", "Padrão do jogo (a paleta do próprio jogo)");
        p(" · 720p patches that palette: the scene costs less, the upscale stays sharp",
                " · 720p corrige essa paleta: a cena custa menos, o upscale continua nítido");
        p("720p (the overlay no longer matches this game: choose 720p again to make it)",
                "720p (o overlay não corresponde mais a este jogo: escolha 720p de novo para refazê-lo)");
        p("Making the 720p scene overlay…", "Criando o overlay 720p da cena…");
        p("The 720p scene overlay is ready", "O overlay 720p da cena está pronto");
        p("The 720p scene overlay is already being made: it turns on when it is ready",
                "O overlay 720p da cena já está sendo criado: ele entra em quando ficar pronto");
        p("Scene 720p failed: this game has no pack or RomFS to make the overlay from",
                "Cena 720p falhou: este jogo não tem pack nem RomFS para criar o overlay");
        p("Scene 720p failed: ", "Cena 720p falhou: ");
        p("This app build has no on-phone packer, so it cannot make the overlay: run the game's own scene resolution",
                "Esta versão do app não tem packer no telefone, então não pode criar o overlay: use a resolução de cena do próprio jogo");
        p("GPU timing", "Timing da GPU");
        p("Pause on return", "Pausar ao voltar");
        p("Coming back from the background, the game waits paused for a tap",
                "Voltando do background, o jogo fica pausado esperando um toque");
        p("RAM saver (uses storage)", "Economia de RAM (usa armazenamento)");
        p("Extra environment", "Ambiente extra");
        p("None (KEY=VALUE lines for the host, after env.txt)", "Nenhum (linhas KEY=VALUE para o host, depois do env.txt)");
        p(" line(s): ", " linha(s): ");
        p("Game settings \u00b7 ", "Ajustes do jogo \u00b7 ");
        p("Default \u00b7 ", "Padrão \u00b7 ");
        p("Per segment", "Por segmento");
        p("Per submission", "Por submissão");
        p("Game default \u00b7 ", "Padrão do jogo \u00b7 ");
        p("Game default (", "Padrão do jogo (");
        p("Default (Settings: ", "Padrão (Ajustes: ");
        p("Default (", "Padrão (");
        p("Default (the main screen's: ", "Padrão (o da tela principal: ");
        p("Game resolution \u00b7 ", "Resolução do jogo \u00b7 ");
        p("GPU timing \u00b7 ", "Timing da GPU \u00b7 ");
        p("GPU driver \u00b7 ", "Driver da GPU \u00b7 ");
        p("Extra environment \u00b7 ", "Ambiente extra \u00b7 ");
        p("KEY=VALUE lines for the host, one a line (# starts a comment). They come after env.txt and win over it, for ",
                "Linhas KEY=VALUE para o host, uma por linha (# inicia comentário). Vêm depois do env.txt e mandam sobre ele, para ");

        // ---- the updater (Updater) ----
        p("This app does not check for updates (only the NativeAYN app and the release do). A test app checks when update.txt in its files folder names a release to compare with.",
                "Este app não verifica atualizações (só o app NativeAYN e o de release fazem). Um app de teste verifica quando o update.txt na sua pasta de arquivos aponta uma release para comparar.");
        p("This build's version (", "A versão deste build (");
        p(") is not a release version: nothing to compare.", ") não é uma versão de release: nada para comparar.");
        p("Checking for updates.", "Checando atualizações.");
        p("Update check failed: ", "A checagem de atualização falhou: ");
        p("You're up to date (v", "Você já está atualizado (v");
        p("Update available", "Atualização disponível");
        p("Version ", "A versão ");
        p(" is available (you have ", " está disponível (você tem ");
        p(").", ").");
        p("Update", "Atualizar");
        p("Later", "Depois");
        p("Starting.", "Iniciando.");
        p("Downloading ", "Baixando ");
        p("No network (cannot reach GitHub)", "Sem rede (não consegue alcançar o GitHub)");
        p("No network (GitHub did not answer in time)", "Sem rede (o GitHub não respondeu a tempo)");
        p("GitHub rate limit, try again later", "Limite de uso do GitHub, tente de novo mais tarde");
        p("GitHub answered HTTP ", "O GitHub respondeu HTTP ");
        p("GitHub's answer could not be read: ", "A resposta do GitHub não pôde ser lida: ");

        // ---- extras found by the coverage check ----
        p(" (this build includes GPL-3.0-or-later code: ", " (este build inclui código GPL-3.0-or-later: ");
        p(" only.", " apenas.");
        p("A game's own settings (\u22ef on its card) override these.",
                "Os ajustes próprios de um jogo (\u22ef no card dele) mandam sobre estes.");
        p("In the game, hold \u2212 and + together for 1 s: presses A through movies and menus, ends movies early, stops at gameplay. Hold again to stop",
                "No jogo, segure \u2212 e + juntos por 1 s: aperta A por filmes e menus, termina filmes mais cedo, para no gameplay. Segure de novo para parar");
        p("Keeps the game's texture data in a file instead of compressed RAM: about 1 GB more free RAM where the phone runs short, but each area load writes ",
                "Mantém os dados de textura do jogo num arquivo em vez de RAM comprimida: cerca de 1 GB a mais de RAM livre onde o celular aperta, mas cada carga de área escreve ");
        p("that data to storage (about 2 GB in Ocarina of Time) and needs that much free space. Off: as before",
                "esses dados no armazenamento (cerca de 2 GB em Ocarina of Time) e precisa desse espaço livre. Desligado: como antes");
        p("Per segment: the game's timestamps see the real GPU time, so its dynamic resolution can adapt. Per submission: one time for the whole ",
                "Por segmento: os timestamps do jogo veem o tempo real da GPU, então a resolução dinâmica pode se adaptar. Por submissão: um tempo para a ");
        p("submission (the older behaviour). A DM_NVN_GPU_TS line in env.txt wins.",
                "submissão inteira (o comportamento antigo). Uma linha DM_NVN_GPU_TS no env.txt manda.");
        p("This game's pack was made by other tools than this app's. It usually still runs. If the game misbehaves, \u22ef \u203a Prepare again remakes it from your dump.",
                "O pack deste jogo foi feito por outras ferramentas, não por este app. Normalmente ainda roda. Se o jogo se comportar mal, \u22ef \u203a Preparar de novo o refaz a partir do seu dump.");
        p("Title IDs: ", "Title IDs: ");

        p(" Credits and the licenses of its parts: NOTICE.md in the source, github.com/",
                " Cr\u00e9ditos e as licen\u00e7as das suas partes: NOTICE.md no c\u00f3digo-fonte, github.com/");
        p(" \u00b7 checks off", " \u00b7 checagem desligada");
        p(" \u00b7 Lossless.dll missing", " \u00b7 Lossless.dll faltando");
        p(" \u00b7 from GitHub", " \u00b7 do GitHub");
    }
}
