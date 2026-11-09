# Banana Native (build de Android): aviso de licença, mantido enquanto o NativeAYN dá continuidade

O Banana Native (o launcher de Android em `android/app` e o host que ele compila, `libnxhost.so`) é baseado no [nxnative](https://github.com/seedds/nxnative), de seedds,
que tem licença MIT ([LICENSE](../LICENSE)).

**O build de Android que inclui LSFG é GPL-3.0-or-later.** Por padrão o host vincula a geração de frames do Lossless Scaling, um port em C da
implementação do [Eden](https://git.eden-emu.dev/eden-emu/eden) (que por sua vez se baseia no [lsfg-vk](https://github.com/PancakeTAS/lsfg-vk)), em
[`dm/gfx/vk_android/lsfg`](../dm/gfx/vk_android/lsfg/LICENSE.md), licenciada GPL-3.0-or-later. Um APK ou `libnxhost.so` compilado com ela é portanto distribuído como um
todo sob a GNU General Public License, versão 3 ou posterior ([COPYING](../dm/gfx/vk_android/lsfg/COPYING)); quem repassar um APK assim deve oferecer seu
código-fonte completo (este repositório no commit indicado pela tag de build). Os arquivos MIT mantêm sua licença MIT por si; o resto do nxnative (os builds
de Mac e iPhone) continua MIT. `./gradlew apk -PnxLsfg=false` compila um APK sem LSFG; ele ainda é GPL-3.0-or-later como um todo quando traz o packer no celular, cujo tradutor também é GPL (abaixo).

**O tradutor de shaders é GPL-3.0-or-later como um todo.** Ele traduz os programas de task e mesh do Switch 2 com
[`InstEmitAmpereMesh.cs`](../dm/third_party/ryujinx/src/Ryujinx.Graphics.Shader/Instructions/InstEmitAmpereMesh.cs), portado do
`nx2_lower` do [switch2mac](https://github.com/azmasm/switch2mac) (proveniente do [Eglantine](https://github.com/djihads80/eglantine)), GPL-3.0-or-later
([COPYING.GPL-3.0](../dm/third_party/ryujinx/COPYING.GPL-3.0)). O tradutor no celular `libdmsh_xlate.so` e o `dmsh_xlate_bin` do Mac são compilados com ele, então
são obras combinadas sob a GPL (veja [dm/third_party/ryujinx/README.md](../dm/third_party/ryujinx/README.md)); as bibliotecas de shaders que eles produzem são os
shaders dos próprios jogos traduzidos, não parte deste código. Os outros arquivos do tradutor mantêm sua licença MIT. Todo APK que traz o packer no celular (`libdmsh_xlate.so`) é
portanto uma obra GPL-3.0-or-later seja qual for o valor de `nxLsfg`, e os builds de Mac e iPhone continuam MIT apenas como apps: o tradutor commitado `dm/gfx/tools/dmsh_xlate_bin` que eles
executam para compilar bibliotecas de shaders é a mesma obra combinada GPL.

Algumas correções seguem descobertas de outros projetos sem copiar seu código: [kartnative](https://github.com/pahorema/kartnative), de pahorema (MIT), para
texturas esparsas escritas pela GPU (`dm/gfx/vk_android/vka_tex.c`) e o header por primitiva dos programas de mesh (`dm/gfx/tools/shader_split.py`), e
[switch2mac](https://github.com/azmasm/switch2mac), de azmasm (MIT fora de `nx2_lower`), para os entry points de mesh em SPIR-V marcados no tradutor. Agradecimentos a eles.

O `Lossless.dll` pertence ao Lossless Scaling (proprietário, vendido separadamente). Ele nunca está neste repositório nem num APK: o usuário fornece sua própria cópia, que o
launcher encontra no celular ou deixa o usuário escolher, e copia para os arquivos privados do app.

Drivers de GPU customizados também nunca estão neste repositório nem num APK. O **Download…** do launcher (`DriverFetch.java`) baixa um zip de driver somente quando o usuário
toca nele, a partir das páginas públicas de release no GitHub de [MrPurple666/purple-turnip](https://github.com/MrPurple666/purple-turnip),
[K11MCH1/AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers), [whitebelyash/freedreno_turnip-CI](https://github.com/whitebelyash/freedreno_turnip-CI),
[Weab-chan/freedreno_turnip-CI](https://github.com/Weab-chan/freedreno_turnip-CI) e [crueter/GameHub-8Elite-Drivers](https://github.com/crueter/GameHub-8Elite-Drivers);
agradecimentos aos seus autores. Cada download mantém sua própria licença: o driver Turnip da Mesa tem licença MIT, os binários do driver Adreno da Qualcomm são proprietários.
