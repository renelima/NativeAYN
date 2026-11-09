# dm/gfx/tex — mapeamento de formatos de textura, deswizzling block-linear, descompressão BC

A parte da camada gráfica que lida com dados de textura, chamada pela camada de replay `../nvn/nvn_mtl.m`. Ela faz três coisas:

1. converte números de formato de textura NVN para formatos de pixel Metal;
2. desempacota o layout "block-linear" do Switch para um layout comum, linha por linha;
3. descomprime texturas BC em GPUs que não suportam texturas comprimidas BC.

Block linear é como o GPU Tegra X1 armazena texturas: os dados não ficam um após o outro por linha, mas entrelaçados em blocos pequenos de 64 bytes × 8 linhas (GOBs), e vários GOBs são então empilhados em blocos maiores conforme um "block height" e uma "block depth". Os arquivos de textura empacotados do jogo estão exatamente nesse layout, e ele precisa ser desfeito antes do upload para o Metal.

## Arquivos

| Arquivo | Conteúdo |
|---|---|
| `nvn_fmt_mtl.h`, `nvn_fmt_mtl.c` | `nvn_fmt_to_mtl()`: formato NVN (0x01–0x9C) → formato de pixel Metal + remapeamento de componentes. Retorna 1 para correspondência exata, 2 para um substituto (precisão, ordem de bits ou interpretação diferem e uma conversão é necessária), 0 quando o Metal não tem formato correspondente. Como cada formato é tratado está escrito nos comentários dentro da tabela. C puro, sem dependência de cabeçalhos do Metal |
| `nvn_deswizzle.h`, `nvn_deswizzle.c` | `nvn_deswizzle()`: deswizzles um nível e uma camada de block linear para linear; `nvn_swizzle()`: o inverso, usado para round trips nos testes de unidade; `nvn_bl_offset()`: o endereço block-linear de um único ponto. C puro |
| `nvn_deswizzle_gpu.h`, `nvn_deswizzle_gpu.m` | Uma versão em compute shader de Metal do mesmo algoritmo de deswizzling, usada pela camada de replay ao fazer upload de texturas empacotadas; quando a inicialização falha ou um formato não suportado é encontrado, a camada de replay recorre à versão em CPU |
| `tests/` | Testes de unidade |

Os offsets de cada nível e camada dentro do armazenamento, e o block height e a block depth realmente usados por cada nível, vêm todos de `../nvn/nvn_tex.c` (`nvn_tex_layout`, `nvn_tex_level_offset`, `nvn_tex_level_tiles`); este diretório só faz endereçamento e movimentação dentro de um nível. Parâmetros empacotados sobrescrevem as dimensões de bloco automáticas: [Dimensões de bloco e linhas de armazenamento linear de texturas empacotadas](../../../docs/reference/nvn-objects.md#packaged-texture-block-dimensions-and-linear-storage-rows).

## Texturas BC

Só GPUs com texturas comprimidas BC são suportadas (`supportsBCTextureCompression` do `MTLDevice`: Macs com Apple silicon e os iPhones mais recentes), então texturas BC são criadas e carregadas como BC; não há caminho de descompressão. Um dispositivo sem elas é recusado no início ([GPUs sem texturas BC](../../../docs/reference/rendering.md#gpus-without-bc-textures-are-refused-at-start)). Uma textura BC nunca é um render target e seus blocos brutos não são amostrados através de uma view não comprimida (o Metal não tem views assim; Breath of the Wild codifica uma textura BC4 32×32 no GPU, GitHub issue #4).

## Testes

```sh
make -C dm/gfx/tex/tests run       # roda t_fmt_mtl e t_tex
```

- **`t_fmt_mtl`**: pega o mapeamento de todo formato NVN em 0x01–0x9C e, para cada um cujo mapeamento não é 0, cria de fato uma textura no dispositivo Metal local; todas devem ser criadas com sucesso. Também verifica que todo formato NVN com sRGB no nome caia num formato sRGB do Metal. Não precisa de arquivos de jogo.
- **`t_tex`**: os testes de deswizzling, em vários grupos:

  | Grupo | Critério |
  |---|---|
  | A round trip | Escreve dados aleatórios em cada nível e camada e os lê de volta; devem ser byte por byte iguais. Cobre 2D, array 2D e 3D, vários valores de bytes por bloco e tamanhos que não são potência de dois |
  | B fórmula de endereço | O resultado de `nvn_bl_offset()` é igual ao endereço calculado à mão pelas regras públicas do layout |
  | D cópias diferenciais | Ambos os sentidos de cópia coincidem com um oráculo de endereço de byte para 1.029 layouts determinísticos, incluindo pitches com padding, sobras de bytes, sobras de mip esparsas, dimensões de bloco empacotadas, arrays e cubes; armazenamento intocado e padding de linha também são comparados |
  | E 3D feito à mão | Coloca marcadores em endereços calculados à mão, verifica onde caem após o deswizzling, e confere block height, block depth e tamanho de armazenamento |

  O round trip A só pode provar "um-para-um e dentro dos limites"; não pode provar que a fórmula do layout esteja certa. O que a sustenta são os endereços calculados à mão dos grupos B e E, e os jogos que renderizam certo.

## Limitações

- `nvn_deswizzle()` não suporta texturas multiamostradas, texturas com a flag LINEAR (que já são lineares) nem o target BUFFER; retorna -1 quando os encontra.
- Cubes e arrays de cube são tratados como texturas em camadas.
- A ordem de bits dos componentes dos formatos compactados de 16 bits (565 / 5551 / 4444) é mapeada conforme os nomes dos formatos e não foi conferida com dados reais; estão marcados como "substituto" na tabela de formatos.
