# Lançando o NativeAYN

Como o dono compila e assina uma release pública. A chave de release pertence só ao dono: ela nunca fica neste repositório, nunca em um pull request e nunca no CI. Nada aqui a cria para você nem escolhe suas senhas: você mesmo executa os comandos e digita as senhas.

O APK de release é o app de release (`-PnxLive=true`): app id `dev.nativeayn.android` (`-PnxAppId=dev.nativeayn.android`), chamado "NativeAYN", não debugável, com o atualizador interno ativado.
Por padrão ele executa Ocarina of Time (`0400486024DEC000`) e não tem packer no telefone (`-PnxPackEngine=fake`); `android/tools/release.sh` o compila.

> **A situação desta máquina (fork, releases v0.1.0–v0.1.4):** ver [6. A chave usada até a v0.1.4](#6-a-chave-usada-até-a-v014-nesta-máquina) no fim do arquivo — as releases publicadas aqui não usam uma chave de release própria; usam a debug key do Android desta máquina, e o OTA de quem tem essas versões instaladas só aceita atualizações assinadas por essa mesma chave.

## 1. Criar a chave de release (uma vez)

Escolha uma pasta fora deste repositório, por exemplo `~/keys/nativeayn`, e execute (ela pede a senha do keystore duas vezes; em um keystore PKCS12, o padrão, a senha da chave é a mesma do store):

```sh
mkdir -p ~/keys/nativeayn && chmod 700 ~/keys/nativeayn
keytool -genkeypair -v \
  -keystore ~/keys/nativeayn/nativeayn-release.jks -storetype PKCS12 \
  -alias nativeayn -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=NativeAYN, O=NativeAYN"
```

- `-validity 10950` são 30 anos: o Android quer uma chave de release válida bem além da vida do app, e uma chave não pode ser estendida depois.
- Os nomes do `-dname` são públicos: qualquer pessoa pode lê-los no APK. Sem `-dname`, o keytool pergunta campo por campo.
- Use uma senha longa e guarde-a em um gerenciador de senhas.

**Mantenha o keystore seguro e com backup.** O Android instala uma atualização apenas quando ela é assinada pela mesma chave do app instalado: se o keystore ou sua senha for perdido, ninguém mais consegue atualizar o app, e cada player precisa desinstalá-lo (perdendo sua pasta de dados) para instalar um novo. Guarde pelo menos duas cópias fora deste repositório (por exemplo um pendrive criptografado e um backup criptografado na nuvem), nunca em um repositório Git, pasta compartilhada ou chat. O `.gitignore` rejeita arquivos `*.jks` e `*.keystore`, e o build do Gradle e o `release.sh` recusam um keystore dentro do repositório.

## 2. Dizer ao build onde fica a chave

O build de release do Gradle lê quatro configurações do ambiente, ou então do seu próprio `~/.gradle/gradle.properties` (nunca do `gradle.properties` do projeto nem de uma opção `-P`):

| Configuração | Valor |
|---|---|
| `ANKA_KEYSTORE` | o caminho do keystore, e.g. `$HOME/keys/nativeayn/nativeayn-release.jks` |
| `ANKA_KEY_ALIAS` | `nativeayn` |
| `ANKA_KEYSTORE_PASSWORD` | a senha do keystore |
| `ANKA_KEY_PASSWORD` | a senha da chave (a do store quando não definida; a mesma no PKCS12) |

Para uma sessão do shell, digitando a senha sem que ela entre no histórico do shell:

```sh
export ANKA_KEYSTORE="$HOME/keys/nativeayn/nativeayn-release.jks"
export ANKA_KEY_ALIAS=nativeayn
read -rs -p 'Keystore password: ' ANKA_KEYSTORE_PASSWORD; echo
export ANKA_KEYSTORE_PASSWORD ANKA_KEY_PASSWORD="$ANKA_KEYSTORE_PASSWORD"
```

Ou guarde o caminho e o alias (e, se você aceitar senha em arquivo, as senhas) em `~/.gradle/gradle.properties`, legível só por você (`chmod 600 ~/.gradle/gradle.properties`):

```properties
ANKA_KEYSTORE=/home/<you>/keys/nativeayn/nativeayn-release.jks
ANKA_KEY_ALIAS=nativeayn
```

## 3. Preencher o certificado (uma vez, depois que a chave existir)

O app termina sua build tag com "official" apenas quando seu certificado de assinatura é o de `Signer.OFFICIAL` (`android/app/src/main/java/dev/nxnative/android/Signer.java`, o bloco marcado THE RELEASE CERTIFICATE). Até ser preenchido, ele é `NOT_SET`, e todo build diz "unofficial build". Para preenchê-lo:

```sh
android/tools/release.sh --fingerprint     # prints 64 lower-case hex digits (keytool asks for the password unless ANKA_KEYSTORE_PASSWORD is set)
```

Coloque-os no lugar de `NOT_SET`, como `static final String OFFICIAL = "<the 64 hex digits>";`, depois confira e faça commit:

```sh
python3 -m unittest dm/tools/tests/test_android_signer.py
git commit -am "Signer: the release certificate"
```

O fingerprint é dado público (qualquer pessoa pode lê-lo no APK); publique-o também nas notas da release, para que os players confiram o APK que baixam (`apksigner verify --print-certs`).

## 4. Compilar a release

A partir de um checkout limpo do commit que você lança (`release.sh` recusa mudanças não commitadas, já que o código-fonte da release é aquele commit):

```sh
android/tools/release.sh
```

Ele compila com um JDK 21 (`JAVA_HOME`, por padrão `/usr/lib/jvm/java-21-openjdk`; o Gradle falha com JDK 27), deixa o Gradle assinar o APK com a chave de release, verifica com `apksigner verify --print-certs` (um signatário), imprime o SHA-256 do certificado e para quando não é o `Signer.OFFICIAL`. O APK (`nativeayn-<version>.apk`) e o `SHA256SUMS.txt` vão para `~/nativeayn-releases/<version>/`, fora do repositório.

Opções (ambiente): `ANKA_VERSION` (padrão `0.1.0`) e `ANKA_VERSION_CODE` (padrão `1`; aumente-a em toda release, o Android só atualiza para um code maior), `ANKA_GAMES` (padrão `0400486024DEC000`; várias são separadas por vírgula), `ANKA_PACK_ENGINE` (`native` por padrão, que precisa do stage do packer no telefone em `NX_PACK_STAGE`, veja [android/README.md](../android/README.md); `fake` compila sem o packer, e o app então não oferece Prepare), `ANKA_RELEASE_DIR` (outra pasta fora do repositório).

## 5. Publicar

1. Dê push no commit lançado (e sua tag) para `github.com/renelima/NativeAYN` **antes ou junto com** o APK: o APK é GPL-3.0-or-later como um todo, então seu código-fonte completo (este repositório no commit da build tag do app) deve estar disponível para todos que o recebem ([NOTICE.md](../NOTICE.md)).
2. Crie uma release no GitHub (tag `v<version>`, marcada como pre-release) e envie apenas o APK e o `SHA256SUMS.txt`. O atualizador interno pega o primeiro asset `.apk` da release e o confere contra o digest SHA-256 que o GitHub calcula dele.
3. Coloque o SHA-256 do certificado nas notas da release.

## 6. A chave usada até a v0.1.4 (nesta máquina)

As releases publicadas neste repositório (`NativeAYN-0.1.0.apk` e `nativeayn-0.1.1.apk` … `nativeayn-0.1.4.apk`) **não** usam uma chave de release criada aqui: todas foram assinadas com a **debug key do Android gerada automaticamente pelo Gradle** nesta máquina.

- Arquivo: `/home/renelima/.android/debug.keystore` no **WSL (Ubuntu-24.04)** — o único lugar onde a chave existe. Uma cópia de backup vive em `keys/debug.keystore` nesta árvore de trabalho (fora do git: o `.gitignore` rejeita `*.keystore`; se esta árvore for movida/copiada, o backup viaja junto).
- Senhas e alias: o padrão público da debug key (`storepass android`, `keypass android`, alias `androiddebugkey`). Não é segredo — o que importa é o **par chave/instalações**: é ela que o Android compara.
- Certificado (público): SHA-256 `f81ff981ae51676847a259abd4fa62371ad207e606d4d9825a74d8557065395a` — conferido de fato entre o keystore e o APK da release v0.1.4 baixado do GitHub (e é o valor de `Signer.OFFICIAL` desde 2026-11-09; o `4d744335…` anterior era de outra chave e não assinou nenhuma release daqui).
- Para o `release.sh` aceitar essa chave, aponte as configurações da seção 2 para ela (a debug key fica fora do repositório, como o script exige):

```sh
export ANKA_KEYSTORE="$HOME/.android/debug.keystore"
export ANKA_KEY_ALIAS=androiddebugkey
export ANKA_KEYSTORE_PASSWORD=android ANKA_KEY_PASSWORD=android
```

Consequências, em uma frase cada:

- **O build de release tem que sair sempre desta máquina/WSL** (mesmo keystore), ou de qualquer lugar que tenha uma cópia idêntica dela; outro keystore ⇒ conflito de assinatura ⇒ o updater recusa e cada telefone precisa desinstalar (perdendo a pasta de dados e os packs) para instalar a versão nova.
- **Perder o arquivo** (`~/.android/debug.keystore`) ⇒ restaure do backup `keys/debug.keystore` (copie de volta para `~/.android/debug.keystore`); se ambos se perderem, é o caso acima.
- A cópia em `keys/` **não é criptografada** e tem senha pública: é backup de conveniência, não cofre. Se um dia o app sair do círculo de testes desta máquina, crie a chave de release verdadeira (seção 1), preencha o `Signer.OFFICIAL` com ela (seção 3) e publique uma release assinada por ela — a *primeira* atualização para essa chave ainda precisa vir assinada pela debug key atual, porque é ela que está instalada nos telefones.

## 7. O rename do app id (de `dev.ankanative.android` para `dev.nativeayn.android`)

As releases v0.1.0–v0.1.4 saíram com o app id antigo, `dev.ankanative.android`. A partir da v0.1.5 o APK de release usa o nome novo, `-PnxAppId=dev.nativeayn.android` (o `build.gradle` lê `nxAppId`; fora do projeto só via `-P` ou `ORG_GRADLE_PROJECT_nxAppId`). Consequência que o Android impõe, não é escolha nossa:

- **Trocar de app id não é atualização: é outro app.** O instalador recusa (`INSTALL_FAILED_INVALID_APK: specified package … inconsistent with …`), e o atualizador interno mostra "Update not installed". O app antigo e o novo **coexistem** no telefone: instale o novo uma vez por fora (APK baixado à mão), e a partir dele o OTA segue normal — o pacote novo é `dev.nativeayn.android` para sempre.
- A pasta de dados (`Android/data/<app id>/`) é por pacote: o `SaveMigrate` copia os saves do app antigo na primeira execução do novo; **os packs preparados não migram sozinhos** — copie `files/packs/<id>` de um pacote para o outro pelo gerenciador de arquivos (ou re-Prepare).
- `Signer.OFFICIAL` e a chave não mudam com o rename; só o pacote.
