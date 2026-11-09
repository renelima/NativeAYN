// agl's NDSR upscaler network replaced by FSR 1 EASU on the Vulkan replay (vka_ndsr.c, DM_VK_NDSR=fsr). Ported from Swap2-Native (github.com/mamcjr/Swap2-Native).
// docs/reference/rendering.md "agl's NDSR upscaler network replaced by FSR 1 EASU on the Vulkan replay". The match rule and the module's binding patch are plain C
// (VKA_NDSR_PURE: the PC test builds them without the host)
#pragma once
#include <stddef.h>
#include <stdint.h>
#include "../nvn/dmsh_abi.h"
#include "../nvn/dmsh_abi_vk.h"

// 1 when entry e (its e->bind_count records at b) has agl's NDSR network interface: a compute entry with the HMMA helper, exactly one combined 2D float sampler
// (its input), one 2D float storage image it writes (its output), one read-only storage buffer (its weights), the push bank (DMSH_PUSH_BANK) among its driver
// segments, and nothing else but the support buffer and driver segments. The bindings of input, output and push bank go to *tex_b, *img_b, *bank_b
int vka_ndsr_match(const dmsh_vk_entry *e, const dmsh_vk_bind *b, uint16_t *tex_b, uint16_t *img_b, uint16_t *bank_b);
// The replacement's SPIR-V (vka_ndsr_spv.h; packed: the variant whose output is an R32_UINT storage image it stores each texel's 32-bit word into) with its placeholder
// bindings 100 (input), 101 (output), 102 (push bank) set to these: malloc'ed, *n words; NULL when the module lacks one of the placeholders
uint32_t *vka_ndsr_words(int packed, uint16_t tex_b, uint16_t img_b, uint16_t bank_b, size_t *n);
// The module's specialization constants (constant_id 0, 1, 2, 3)
enum { VKA_NDSR_ENC_LINEAR = 0, VKA_NDSR_ENC_SQRT = 1 };
enum { VKA_NDSR_MAP_FULL = 0, VKA_NDSR_MAP_PUSH = 1 };
enum { VKA_NDSR_KERNEL_EASU = 0, VKA_NDSR_KERNEL_BILINEAR = 1 };
enum { VKA_NDSR_PACK_NONE = 0, VKA_NDSR_PACK_B10G11R11 = 1, VKA_NDSR_PACK_RGBA8 = 2 };   // PACK (the packed variant only): the output's format
// The packed store for an output of VkFormat vkformat (VKA_NDSR_PACK_*): B10G11R11_UFLOAT_PACK32 (122) and R8G8B8A8_UNORM (37); NONE for any other
int vka_ndsr_pack_of(int vkformat);

#ifndef VKA_NDSR_PURE
#include "vka.h"
// The pipeline that runs in place of compute shader cs's own at this dispatch, or VK_NULL_HANDLE: the network as before (DM_VK_NDSR unset, empty or net; cs not an
// NDSR network; its input or output unbound in walk r[0..n); the pipeline not made). It takes the network's layout, so bind_sets writes the same descriptors.
// This tree keeps no per-dispatch image retyping (vka_imgrt): the float store variant always runs, the output bound as the old bilinear stand-in bound it
// (vka_tex_id_view's storage view, vka_tex.c "Learned view formats"); the packed variant and the format-feature probe stay here for when one is wired.
// cb: the replay's command buffer (the bound textures' images and sizes)
VkPipeline vka_ndsr_pipeline(const vka_dstate *d, vka_shader *cs, const vka_layout *lay, const vka_res *r, int n, VkCommandBuffer cb);
// The DM_VK_NDSR mode read now: 0 net, 1 fsr, 2 bilinear (for the prewarm and the old stand-in's key check, vka_draw.c / vka_pso.c)
int vka_ndsr_mode(void);
#endif
