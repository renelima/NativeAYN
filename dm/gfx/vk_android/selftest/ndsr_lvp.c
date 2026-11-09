// PC test of the NDSR network's FSR 1 stand-in (vka_ndsr.c, fsr/ndsr_easu.comp) on any Vulkan driver, made for Mesa's lavapipe (dm/tools/tests/test_ndsr_standin.py
// builds and runs it in Linux or WSL). It takes the module through vka_ndsr_words with binding numbers of its own (input 1, output 2, push bank 4), so the binding
// patch is exercised, and runs it as the replay does: a 32x36 dispatch of 128-wide groups over an E5B9G9R9 input holding sqrt(colour) (or the colour) and a
// B10G11R11 storage output first filled with a sentinel. Checks: every texel written; the bilinear kernel matches a CPU bilinear-then-square reference; EASU's mean
// within a few per cent of it and close to it on smooth gradients (a missing or doubled square is far off); a 1:1 size is the decode squared; ENC 0 is a plain
// upscale; another input size (1280x720) works; the push bank mapping (pixel and normalized units, with an origin) gives the full-image result; the packed variant
// (an R32_UINT output, as the replay binds a B10G11R11 image without storage support) gives the float store's picture as B10G11R11 words, and RGBA8 words.
// Exit 0 = all passed. Build: gcc -O1 -DVKA_NDSR_PURE -I dm/third_party/vulkan-headers/include -I dm/gfx/vk_android ndsr_lvp.c ../vka_ndsr.c -ldl -lm
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan_core.h>
#include <dlfcn.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "vka_ndsr.h"

#define FNS(X) X(vkEnumeratePhysicalDevices) X(vkGetPhysicalDeviceProperties) X(vkGetPhysicalDeviceQueueFamilyProperties) X(vkGetPhysicalDeviceMemoryProperties) \
    X(vkGetPhysicalDeviceFormatProperties) X(vkGetPhysicalDeviceFeatures) X(vkCreateDevice) X(vkGetDeviceQueue) X(vkCreateBuffer) X(vkGetBufferMemoryRequirements) X(vkAllocateMemory) \
    X(vkBindBufferMemory) X(vkMapMemory) X(vkCreateImage) X(vkGetImageMemoryRequirements) X(vkBindImageMemory) X(vkCreateImageView) X(vkCreateSampler) \
    X(vkCreateDescriptorSetLayout) X(vkCreatePipelineLayout) X(vkCreateShaderModule) X(vkCreateComputePipelines) X(vkCreateDescriptorPool) X(vkAllocateDescriptorSets) \
    X(vkUpdateDescriptorSets) X(vkCreateCommandPool) X(vkAllocateCommandBuffers) X(vkBeginCommandBuffer) X(vkEndCommandBuffer) X(vkCmdPipelineBarrier) \
    X(vkCmdCopyBufferToImage) X(vkCmdCopyImageToBuffer) X(vkCmdClearColorImage) X(vkCmdBindPipeline) X(vkCmdBindDescriptorSets) X(vkCmdDispatch) X(vkQueueSubmit) \
    X(vkQueueWaitIdle) X(vkDestroyPipeline) X(vkDestroyShaderModule) X(vkResetDescriptorPool)
#define DECL(n) static PFN_##n n;
FNS(DECL)
static VkDevice dev; static VkPhysicalDevice pd; static VkQueue q; static VkPhysicalDeviceMemoryProperties memp; static VkCommandPool pool;
static VkDescriptorSetLayout dsl; static VkPipelineLayout pl; static VkDescriptorPool dpool; static VkSampler smp; static VkFormat ofmt;
static int fails;
#define CHECK(c, ...) do { if (!(c)) { printf("FAIL: " __VA_ARGS__); printf("\n"); fails++; } } while (0)
#define VK(x) do { VkResult r_ = (x); if (r_) { printf("FAIL: %s = %d\n", #x, r_); exit(2); } } while (0)

// ---------- Formats on the CPU ----------
static uint32_t rgb9e5(float r, float g, float b) {   // the Vulkan specification's encoding (shared exponent, 9-bit mantissas, bias 15)
    const float mx = 65408.0f; r = fminf(fmaxf(r, 0), mx); g = fminf(fmaxf(g, 0), mx); b = fminf(fmaxf(b, 0), mx);
    float m = fmaxf(r, fmaxf(g, b));
    int e = m > 0 ? (int)floorf(log2f(m)) : -16; if (e < -16) e = -16; e = e + 1 + 15;   // (exp' = max(-B - 1, floor(log2 m)) + 1 + B)
    int mm = (int)floorf(m / ldexpf(1, e - 15 - 9) + 0.5f); if (mm == 512) e++;
    float s = ldexpf(1, e - 15 - 9);
    uint32_t rr = (uint32_t)floorf(r / s + 0.5f), gg = (uint32_t)floorf(g / s + 0.5f), bb = (uint32_t)floorf(b / s + 0.5f);
    return (uint32_t)e << 27 | bb << 18 | gg << 9 | rr;
}
static void de9e5(uint32_t w, float *c) { float s = ldexpf(1, (int)(w >> 27) - 15 - 9); c[0] = (w & 511) * s; c[1] = (w >> 9 & 511) * s; c[2] = (w >> 18 & 511) * s; }
static float uf(uint32_t v, int mb) {   // an unsigned small float: 5-bit exponent (bias 15) and an mb-bit mantissa
    uint32_t e = v >> mb, m = v & ((1u << mb) - 1);
    if (e == 0) return ldexpf((float)m, -14 - mb);
    if (e == 31) return m ? NAN : INFINITY;
    return ldexpf(1.0f + (float)m / (1 << mb), (int)e - 15);
}
static void out_px(const uint8_t *p, float *c, int pack) {   // pack: VKA_NDSR_PACK_* (an R32_UINT output holding that format's words), else ofmt
    if (pack == VKA_NDSR_PACK_RGBA8) { uint32_t w; memcpy(&w, p, 4); for (int i = 0; i < 3; i++) c[i] = w >> 24 ? (w >> 8 * i & 255) / 255.0f : 1000.0f; }   // (alpha 0: the sentinel)
    else if (pack || ofmt == VK_FORMAT_B10G11R11_UFLOAT_PACK32) { uint32_t w; memcpy(&w, p, 4); c[0] = uf(w & 2047, 6); c[1] = uf(w >> 11 & 2047, 6); c[2] = uf(w >> 22, 5); }
    else { uint16_t h[4]; memcpy(h, p, 8); for (int i = 0; i < 3; i++) { uint32_t s = h[i] >> 15, e = h[i] >> 10 & 31, m = h[i] & 1023; float v = e ? ldexpf(1 + m / 1024.0f, (int)e - 15) : ldexpf((float)m, -24); c[i] = s ? -v : v; } }
}

// ---------- Vulkan ----------
static uint32_t mem_type(uint32_t bits, VkMemoryPropertyFlags want) {
    for (uint32_t i = 0; i < memp.memoryTypeCount; i++) if ((bits >> i & 1) && (memp.memoryTypes[i].propertyFlags & want) == want) return i;
    printf("FAIL: no memory type\n"); exit(2);
}
static VkBuffer buffer(VkDeviceSize n, VkBufferUsageFlags use, void **map) {
    VkBufferCreateInfo bi = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, .size = n, .usage = use}; VkBuffer b; VK(vkCreateBuffer(dev, &bi, NULL, &b));
    VkMemoryRequirements mr; vkGetBufferMemoryRequirements(dev, b, &mr);
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = mr.size, .memoryTypeIndex = mem_type(mr.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)};
    VkDeviceMemory m; VK(vkAllocateMemory(dev, &ai, NULL, &m)); VK(vkBindBufferMemory(dev, b, m, 0)); VK(vkMapMemory(dev, m, 0, n, 0, map));
    return b;
}
static VkImage image(VkFormat f, uint32_t w, uint32_t h, VkImageUsageFlags use, VkImageView *view) {
    VkImageCreateInfo ci = {VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .imageType = VK_IMAGE_TYPE_2D, .format = f, .extent = {w, h, 1}, .mipLevels = 1, .arrayLayers = 1,
                            .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_OPTIMAL, .usage = use};
    VkImage im; VK(vkCreateImage(dev, &ci, NULL, &im));
    VkMemoryRequirements mr; vkGetImageMemoryRequirements(dev, im, &mr);
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = mr.size, .memoryTypeIndex = mem_type(mr.memoryTypeBits, 0)};
    VkDeviceMemory m; VK(vkAllocateMemory(dev, &ai, NULL, &m)); VK(vkBindImageMemory(dev, im, m, 0));
    VkImageViewCreateInfo vi = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, .image = im, .viewType = VK_IMAGE_VIEW_TYPE_2D, .format = f, .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
    VK(vkCreateImageView(dev, &vi, NULL, view));
    return im;
}
static void barrier(VkCommandBuffer cb, VkImage im, VkImageLayout from, VkImageLayout to) {
    VkImageMemoryBarrier b = {VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT, .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
                              .oldLayout = from, .newLayout = to, .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .image = im,
                              .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
    vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, NULL, 0, NULL, 1, &b);
}
static int init(void) {
    void *lib = dlopen("libvulkan.so.1", RTLD_NOW); if (!lib) { printf("SKIP: no libvulkan.so.1\n"); return 0; }
    PFN_vkGetInstanceProcAddr gipa = (PFN_vkGetInstanceProcAddr)dlsym(lib, "vkGetInstanceProcAddr");
    PFN_vkCreateInstance ci_ = (PFN_vkCreateInstance)gipa(NULL, "vkCreateInstance");
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO, .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo ii = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app}; VkInstance inst;
    if (ci_(&ii, NULL, &inst)) { printf("SKIP: no Vulkan instance\n"); return 0; }
#define LOAD(n) n = (PFN_##n)gipa(inst, #n); if (!n) { printf("FAIL: no %s\n", #n); exit(2); }
    FNS(LOAD)
    uint32_t n = 1; if (vkEnumeratePhysicalDevices(inst, &n, &pd) < 0 || !n) { printf("SKIP: no Vulkan device\n"); return 0; }
    VkPhysicalDeviceProperties pp; vkGetPhysicalDeviceProperties(pd, &pp); printf("device: %s\n", pp.deviceName);
    VkPhysicalDeviceFeatures have; vkGetPhysicalDeviceFeatures(pd, &have);
    if (!have.shaderStorageImageWriteWithoutFormat) { printf("SKIP: no shaderStorageImageWriteWithoutFormat\n"); return 0; }
    vkGetPhysicalDeviceMemoryProperties(pd, &memp);
    uint32_t nq = 8; VkQueueFamilyProperties qf[8]; vkGetPhysicalDeviceQueueFamilyProperties(pd, &nq, qf);
    uint32_t qi = 0; while (qi < nq && !(qf[qi].queueFlags & VK_QUEUE_COMPUTE_BIT)) qi++;
    float prio = 1; VkDeviceQueueCreateInfo qc = {VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, .queueFamilyIndex = qi, .queueCount = 1, .pQueuePriorities = &prio};
    VkPhysicalDeviceFeatures f = {.shaderStorageImageWriteWithoutFormat = VK_TRUE};
    VkDeviceCreateInfo dc = {VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, .queueCreateInfoCount = 1, .pQueueCreateInfos = &qc, .pEnabledFeatures = &f};
    VK(vkCreateDevice(pd, &dc, NULL, &dev)); vkGetDeviceQueue(dev, qi, 0, &q);
    VkFormatProperties fp; vkGetPhysicalDeviceFormatProperties(pd, VK_FORMAT_E5B9G9R9_UFLOAT_PACK32, &fp);
    if (!(fp.optimalTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)) { printf("SKIP: E5B9G9R9 is not sampled on this device\n"); return 0; }
    vkGetPhysicalDeviceFormatProperties(pd, VK_FORMAT_B10G11R11_UFLOAT_PACK32, &fp);
    ofmt = fp.optimalTilingFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT ? VK_FORMAT_B10G11R11_UFLOAT_PACK32 : VK_FORMAT_R16G16B16A16_SFLOAT;
    printf("output format: %s\n", ofmt == VK_FORMAT_B10G11R11_UFLOAT_PACK32 ? "B10G11R11_UFLOAT" : "R16G16B16A16_SFLOAT (no B10G11R11 storage here)");
    VkCommandPoolCreateInfo pc = {VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT, .queueFamilyIndex = qi};
    VK(vkCreateCommandPool(dev, &pc, NULL, &pool));
    // The layout as the replay's has it for the network: more bindings than the module uses (support 0, weights 3, c0 5)
    VkDescriptorSetLayoutBinding lb[6] = {{0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT}, {1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_COMPUTE_BIT},
                                          {2, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1, VK_SHADER_STAGE_COMPUTE_BIT}, {3, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT},
                                          {4, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT}, {5, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT}};
    VkDescriptorSetLayoutCreateInfo dl = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO, .bindingCount = 6, .pBindings = lb}; VK(vkCreateDescriptorSetLayout(dev, &dl, NULL, &dsl));
    VkPipelineLayoutCreateInfo li = {VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO, .setLayoutCount = 1, .pSetLayouts = &dsl}; VK(vkCreatePipelineLayout(dev, &li, NULL, &pl));
    VkDescriptorPoolSize ps[4] = {{VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 3}, {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1}, {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1}, {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1}};
    VkDescriptorPoolCreateInfo dp = {VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO, .maxSets = 1, .poolSizeCount = 4, .pPoolSizes = ps}; VK(vkCreateDescriptorPool(dev, &dp, NULL, &dpool));
    VkSamplerCreateInfo sc = {VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO, .magFilter = VK_FILTER_LINEAR, .minFilter = VK_FILTER_LINEAR, .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                              .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, .maxLod = 0};
    VK(vkCreateSampler(dev, &sc, NULL, &smp));
    return 1;
}

// ---------- The pattern and the references ----------
static void pattern(int x, int y, int w, int h, float *c) {   // linear colour in [0, 1]: gradients, hard edges, a checkerboard, per-channel ramps
    float u = (x + 0.5f) / w, v = (y + 0.5f) / h;
    if (u < 0.4f) { c[0] = u / 0.4f; c[1] = v; c[2] = 0.5f + 0.4f * sinf(6.0f * u + 3.0f * v); }   // smooth
    else if (u < 0.6f) { int s = (x / 23) & 1; c[0] = c[1] = c[2] = s ? 0.9f : 0.05f; }               // hard vertical edges
    else if (v < 0.5f) { int s = ((x >> 2) ^ (y >> 2)) & 1; c[0] = s ? 1.0f : 0.0f; c[1] = s ? 0.2f : 0.7f; c[2] = 0.3f; }   // checkerboard
    else { c[0] = (u - 0.6f) / 0.4f; c[1] = 1.0f - (v - 0.5f) * 2.0f; c[2] = (x & 1) ? 0.25f : 0.75f; }   // ramps, a 1-pixel blue line pattern
}
typedef struct { int iw, ih, ow, oh, enc, map, kernel, pack; float bank[64]; int org_x, org_y; } run_t;
static float *g_in;   // the decoded input texels (what the GPU reads), iw*ih*3
// Runs the shader once; returns ow*oh*3 floats (the sentinel where nothing was written)
static float *run(const run_t *R) {
    int iw = R->iw, ih = R->ih, ow = R->ow, oh = R->oh;
    void *up; VkBuffer ub = buffer((VkDeviceSize)iw * ih * 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, &up);
    free(g_in); g_in = malloc(sizeof(float) * 3 * iw * ih);
    for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) {
        float c[3]; pattern(x, y, iw, ih, c);
        if (R->enc) for (int i = 0; i < 3; i++) c[i] = sqrtf(c[i]);
        uint32_t w = rgb9e5(c[0], c[1], c[2]); ((uint32_t *)up)[y * iw + x] = w; de9e5(w, &g_in[3 * (y * iw + x)]);
    }
    VkFormat of = R->pack ? VK_FORMAT_R32_UINT : ofmt;
    size_t ob = of == VK_FORMAT_R16G16B16A16_SFLOAT ? 8 : 4;
    void *dn; VkBuffer db = buffer((VkDeviceSize)ow * oh * ob, VK_BUFFER_USAGE_TRANSFER_DST_BIT, &dn);
    void *bk; VkBuffer bb = buffer(256, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, &bk); memcpy(bk, R->bank, 256);
    void *dm; VkBuffer dmy = buffer(256, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, &dm); memset(dm, 0, 256);
    VkImageView iv, ov;
    VkImage si = image(VK_FORMAT_E5B9G9R9_UFLOAT_PACK32, iw, ih, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT, &iv);
    VkImage oi = image(of, ow, oh, VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT, &ov);
    size_t nw; uint32_t *w = vka_ndsr_words(R->pack != 0, 1, 2, 4, &nw);
    if (!w) { printf("FAIL: vka_ndsr_words found no placeholder bindings\n"); exit(2); }
    VkShaderModuleCreateInfo mi = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO, .codeSize = nw * 4, .pCode = w}; VkShaderModule m; VK(vkCreateShaderModule(dev, &mi, NULL, &m)); free(w);
    int32_t sv[4] = {R->enc, R->map, R->kernel, R->pack ? R->pack : 1}; VkSpecializationMapEntry se[4] = {{0, 0, 4}, {1, 4, 4}, {2, 8, 4}, {3, 12, 4}}; VkSpecializationInfo si_ = {4, se, sizeof sv, sv};
    VkComputePipelineCreateInfo pci = {VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO, .layout = pl,
        .stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_COMPUTE_BIT, .module = m, .pName = "main", .pSpecializationInfo = &si_}};
    VkPipeline p; VK(vkCreateComputePipelines(dev, VK_NULL_HANDLE, 1, &pci, NULL, &p));
    VK(vkResetDescriptorPool(dev, dpool, 0));
    VkDescriptorSetAllocateInfo da = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO, .descriptorPool = dpool, .descriptorSetCount = 1, .pSetLayouts = &dsl}; VkDescriptorSet ds; VK(vkAllocateDescriptorSets(dev, &da, &ds));
    VkDescriptorBufferInfo bdm = {dmy, 0, 256}, bbk = {bb, 0, 256};
    VkDescriptorImageInfo di = {smp, iv, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL}, dio = {VK_NULL_HANDLE, ov, VK_IMAGE_LAYOUT_GENERAL};
    VkWriteDescriptorSet wr[6] = {
        {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = ds, .dstBinding = 0, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, .pBufferInfo = &bdm},
        {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = ds, .dstBinding = 1, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, .pImageInfo = &di},
        {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = ds, .dstBinding = 2, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, .pImageInfo = &dio},
        {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = ds, .dstBinding = 3, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .pBufferInfo = &bdm},
        {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = ds, .dstBinding = 4, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, .pBufferInfo = &bbk},
        {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = ds, .dstBinding = 5, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, .pBufferInfo = &bdm}};
    vkUpdateDescriptorSets(dev, 6, wr, 0, NULL);
    VkCommandBufferAllocateInfo ca = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, .commandPool = pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
    VkCommandBuffer cb; VK(vkAllocateCommandBuffers(dev, &ca, &cb));
    VkCommandBufferBeginInfo bi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT}; VK(vkBeginCommandBuffer(cb, &bi));
    barrier(cb, si, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    VkBufferImageCopy rg = {0, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}, {0, 0, 0}, {(uint32_t)iw, (uint32_t)ih, 1}};
    vkCmdCopyBufferToImage(cb, ub, si, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &rg);
    barrier(cb, si, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    barrier(cb, oi, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    // The sentinel: 60000 in a float output; 0x00ffffff in a packed one (a B10G11R11 word with red NaN, an RGBA8 word with alpha 0)
    VkClearColorValue sent = {.float32 = {60000.0f, 60000.0f, 60000.0f, 1.0f}}; VkImageSubresourceRange sr = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    if (R->pack) sent = (VkClearColorValue){.uint32 = {0x00ffffffu, 0, 0, 0}};
    vkCmdClearColorImage(cb, oi, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &sent, 1, &sr);
    barrier(cb, oi, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL);
    vkCmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_COMPUTE, p);
    vkCmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_COMPUTE, pl, 0, 1, &ds, 0, NULL);
    vkCmdDispatch(cb, 32, 36, 1);   // the game's group count for its 2048x1152 output (any count covers any size: the grid stride)
    barrier(cb, oi, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
    VkBufferImageCopy rg2 = {0, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}, {0, 0, 0}, {(uint32_t)ow, (uint32_t)oh, 1}};
    vkCmdCopyImageToBuffer(cb, oi, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, db, 1, &rg2);
    VkMemoryBarrier hb = {VK_STRUCTURE_TYPE_MEMORY_BARRIER, .srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT, .dstAccessMask = VK_ACCESS_HOST_READ_BIT};
    vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &hb, 0, NULL, 0, NULL);
    VK(vkEndCommandBuffer(cb));
    VkSubmitInfo su = {VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &cb}; VK(vkQueueSubmit(q, 1, &su, VK_NULL_HANDLE)); VK(vkQueueWaitIdle(q));
    float *o = malloc(sizeof(float) * 3 * ow * oh);
    for (int i = 0; i < ow * oh; i++) out_px((const uint8_t *)dn + ob * i, &o[3 * i], R->pack);
    vkDestroyPipeline(dev, p, NULL); vkDestroyShaderModule(dev, m, NULL);   // (the rest is left to the process's end)
    return o;
}
static void in_at(int x, int y, int iw, int ih, float *c) { x = x < 0 ? 0 : x >= iw ? iw - 1 : x; y = y < 0 ? 0 : y >= ih ? ih - 1 : y; memcpy(c, &g_in[3 * (y * iw + x)], 12); }
static void ref_bilinear(const run_t *R, int x, int y, float *c) {   // the CPU reference: bilinear of the decoded input (texel centres at integers), then the square with ENC 1
    float px = (x + 0.5f) * R->iw / R->ow - 0.5f, py = (y + 0.5f) * R->ih / R->oh - 0.5f, fx = floorf(px), fy = floorf(py), wx = px - fx, wy = py - fy;
    float a[3], b[3], d[3], e[3]; in_at((int)fx, (int)fy, R->iw, R->ih, a); in_at((int)fx + 1, (int)fy, R->iw, R->ih, b); in_at((int)fx, (int)fy + 1, R->iw, R->ih, d); in_at((int)fx + 1, (int)fy + 1, R->iw, R->ih, e);
    for (int i = 0; i < 3; i++) { float v = (a[i] * (1 - wx) + b[i] * wx) * (1 - wy) + (d[i] * (1 - wx) + e[i] * wx) * wy; v = fmaxf(v, 0); c[i] = R->enc ? v * v : v; }
}
static int unwritten(const float *o, int n) { int k = 0; for (int i = 0; i < 3 * n; i++) if (!(o[i] < 100.0f)) k++; return k; }
static double mean(const float *o, int n) { double s = 0; for (int i = 0; i < 3 * n; i++) s += o[i]; return s / (3.0 * n); }
static double ref_mean(const run_t *R) { double s = 0; for (int y = 0; y < R->oh; y++) for (int x = 0; x < R->ow; x++) { float c[3]; ref_bilinear(R, x, y, c); s += c[0] + c[1] + c[2]; } return s / (3.0 * R->ow * R->oh); }
// The largest error against the reference: relative (to max(|ref|, floor)) over [x0, x1) x [y0, y1) of the output
static double err_vs_ref(const run_t *R, const float *o, int x0, int x1, int y0, int y1, double floor_) {
    double mx = 0;
    for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { float c[3]; ref_bilinear(R, x, y, c); for (int i = 0; i < 3; i++) { double e = fabs(o[3 * (y * R->ow + x) + i] - c[i]) / fmax(fabs(c[i]), floor_); if (e > mx) mx = e; } }
    return mx;
}
static double err_mean_vs_ref(const run_t *R, const float *o, int x0, int x1, int y0, int y1) {
    double s = 0; long n = 0;
    for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { float c[3]; ref_bilinear(R, x, y, c); for (int i = 0; i < 3; i++) { s += fabs(o[3 * (y * R->ow + x) + i] - c[i]); n++; } }
    return s / n;
}
static run_t base(int iw, int ih, int ow, int oh, int enc, int kernel) { run_t r; memset(&r, 0, sizeof r); r.iw = iw; r.ih = ih; r.ow = ow; r.oh = oh; r.enc = enc; r.kernel = kernel; return r; }

// The match rule (vka_ndsr_match) on records shaped like the library's for agl's network (support 0, input 1, output 2, weights 3, c24 4, c0 5) and variations of them
static void match_checks(void) {
    dmsh_vk_bind nb[6] = {{.kind = DMSH_VK_KIND_SUPPORT, .desc = 6, .binding = 0, .count = 1},
                          {.kind = DMSH_VK_KIND_TEX, .desc = 1, .binding = 1, .count = 1, .skind = DMSH_VK_KIND_SAMP_TEX, .dim = 1},
                          {.kind = DMSH_VK_KIND_IMG, .desc = 3, .binding = 2, .count = 1, .flags = DMSH_VK_BF_RW, .dim = 1},
                          {.kind = DMSH_VK_KIND_SBUF, .desc = 7, .binding = 3, .count = 1},
                          {.kind = DMSH_VK_KIND_DRVCB, .desc = 6, .binding = 4, .count = 1, .reg = DMSH_PUSH_BANK},
                          {.kind = DMSH_VK_KIND_DRVCB, .desc = 6, .binding = 5, .count = 1, .reg = 0}};
    dmsh_vk_entry e = {.stage = DMSH_VK_STAGE_CS, .flags = DMSH_VK_F_HMMA | 4, .bind_count = 6};
    uint16_t t = 0, i = 0, k = 0;
    CHECK(vka_ndsr_match(&e, nb, &t, &i, &k) == 1 && t == 1 && i == 2 && k == 4, "match: the network's records (tex %u img %u bank %u)", t, i, k);
    #define NOT(what, ...) do { dmsh_vk_bind b[7]; memcpy(b, nb, sizeof nb); dmsh_vk_entry x = e; __VA_ARGS__; CHECK(!vka_ndsr_match(&x, b, &t, &i, &k), "match: taken with %s", what); } while (0)
    NOT("no HMMA helper", x.flags = 4);
    NOT("a pixel stage", x.stage = DMSH_VK_STAGE_FS);
    NOT("a second texture", b[6] = b[1]; b[6].binding = 6; x.bind_count = 7);
    NOT("a shared memory spill buffer", b[6] = (dmsh_vk_bind){.kind = DMSH_VK_KIND_SHARED_SPILL, .desc = 7, .binding = 6, .count = 1, .bytes = 4096}; x.bind_count = 7);
    NOT("a written storage buffer", b[3].flags = DMSH_VK_BF_RW);
    NOT("a second storage buffer", b[6] = b[3]; b[6].binding = 6; x.bind_count = 7);
    NOT("image atomics", b[2].flags |= DMSH_VK_BF_ATOMIC);
    NOT("an integer output", b[2].dim |= DMSH_VK_DIM_UINT);
    NOT("an array input", b[1].dim |= DMSH_VK_DIM_ARRAY);
    NOT("a separate (not combined) input", b[1].skind = 0);
    NOT("no push bank", b[4].reg = 2);
    NOT("a uniform buffer slot", b[5].kind = DMSH_VK_KIND_CBUF);
    #undef NOT
}

int main(void) {
    match_checks();
    if (fails) { printf("FAILED: %d checks\n", fails); return 1; }
    printf("match rule: all checks passed\n");
    if (!init()) return 77;
    const double qtol = 2.5 / 64;   // R11G11B10F: 6-bit mantissas (5 in blue): a relative step of 1/64 (1/32); 2.5 steps with the GPU's rounding
    // 1. Bilinear kernel, sqrt input: the CPU reference everywhere (the mapping, the decode and the square)
    run_t R = base(1536, 864, 2048, 1152, 1, 1);
    float *o = run(&R); int N = R.ow * R.oh;
    CHECK(unwritten(o, N) == 0, "bilinear 1536x864 -> 2048x1152: %d channels not written", unwritten(o, N));
    double e = err_vs_ref(&R, o, 0, R.ow, 0, R.oh, 0.02); printf("bilinear sqrt 1536x864 -> 2048x1152: max error %.4f (relative, floor 0.02), mean %.5f against %.5f\n", e, mean(o, N), ref_mean(&R));
    CHECK(e < 2 * qtol, "bilinear: max error %.4f against the CPU reference", e);
    free(o);
    // 2. EASU, sqrt input: written everywhere, the mean near the bilinear reference's, close to it on the smooth gradients
    R = base(1536, 864, 2048, 1152, 1, 0);
    o = run(&R); float *easu = o;
    double m = mean(o, N), rm = ref_mean(&R), lin = 0;
    for (int i = 0; i < 3 * N; i++) lin += sqrtf(o[i]);
    lin /= 3.0 * N;
    printf("EASU sqrt 1536x864 -> 2048x1152: mean %.5f, bilinear reference %.5f (%.2f %%); without the square it would be about %.5f\n", m, rm, 100 * (m - rm) / rm, lin);
    CHECK(unwritten(o, N) == 0, "EASU: %d channels not written", unwritten(o, N));
    CHECK(fabs(m - rm) / rm < 0.03, "EASU mean %.5f against the bilinear reference %.5f", m, rm);
    CHECK(fabs(lin - rm) / rm > 0.1, "the check above would not see a missing square (%.5f against %.5f)", lin, rm);
    int sx0 = 16, sx1 = (int)(0.4 * R.ow) - 16, sy0 = 16, sy1 = R.oh - 16;
    double es = err_vs_ref(&R, o, sx0, sx1, sy0, sy1, 0.05), em = err_mean_vs_ref(&R, o, sx0, sx1, sy0, sy1);
    printf("EASU on the smooth gradients: max error %.4f (relative, floor 0.05), mean absolute %.5f\n", es, em);
    CHECK(es < 0.08 && em < 0.006, "EASU away from edges: max %.4f, mean %.5f against bilinear", es, em);
    // 3. The push bank mapping gives the full-image result: pixel units, normalized units, and an origin (the texels before it untouched)
    run_t P = R; P.map = 1; P.bank[20] = (float)P.iw / P.ow; P.bank[21] = (float)P.ih / P.oh;   // c24[5] = (scale x, scale y, 0, 0)
    o = run(&P); double d = 0; for (int i = 0; i < 3 * N; i++) d = fmax(d, fabs(o[i] - easu[i]) / fmax(easu[i], 0.02));
    printf("push bank mapping (input pixels): max difference to the full-image mapping %.5f\n", d); CHECK(d < qtol, "push mapping in pixels differs by %.5f", d); free(o);
    P.bank[20] = 1.0f / P.ow; P.bank[21] = 1.0f / P.oh;   // normalized: (iw / ow) / iw
    o = run(&P); d = 0; for (int i = 0; i < 3 * N; i++) d = fmax(d, fabs(o[i] - easu[i]) / fmax(easu[i], 0.02));
    printf("push bank mapping (normalized): max difference %.5f\n", d); CHECK(d < qtol, "push mapping in normalized units differs by %.5f", d); free(o);
    P.bank[20] = (float)P.iw / P.ow; P.bank[21] = (float)P.ih / P.oh; uint32_t org[2] = {16, 8}; memcpy(&P.bank[4], org, 8);   // c24[1].xy = (16, 8)
    o = run(&P); d = 0; int untouched = 0;
    for (int y = 0; y < R.oh; y++) for (int x = 0; x < R.ow; x++) for (int i = 0; i < 3; i++) {
        float v = o[3 * (y * R.ow + x) + i];
        if (x < 16 || y < 8) { untouched += !(v < 100.0f); continue; }
        float want = easu[3 * ((y - 8) * R.ow + (x - 16)) + i]; d = fmax(d, fabs(v - want) / fmax(want, 0.02));
    }
    int before = 3 * (R.ow * R.oh - (R.ow - 16) * (R.oh - 8));
    printf("push bank origin (16, 8): max difference %.5f, %d of %d channels before it untouched\n", d, untouched, before);
    CHECK(d < qtol && untouched == before, "push origin: difference %.5f, untouched %d of %d", d, untouched, before); free(o); free(easu);
    // 4. 1:1: the decode squared
    R = base(1536, 864, 1536, 864, 1, 0); N = R.ow * R.oh;
    o = run(&R); e = err_vs_ref(&R, o, 0, R.ow, 0, R.oh, 0.01);
    printf("EASU sqrt 1536x864 -> 1536x864 (1:1): max error to the decode squared %.4f\n", e);
    CHECK(unwritten(o, N) == 0 && e < qtol, "1:1: %d not written, error %.4f", unwritten(o, N), e); free(o);
    // 5. ENC 0: a plain upscale (no square)
    R = base(1536, 864, 2048, 1152, 0, 1); N = R.ow * R.oh;
    o = run(&R); e = err_vs_ref(&R, o, 0, R.ow, 0, R.oh, 0.02);
    printf("bilinear linear 1536x864 -> 2048x1152: max error %.4f\n", e); CHECK(e < 2 * qtol, "linear bilinear: error %.4f", e); free(o);
    R.kernel = 0; o = run(&R); m = mean(o, N); rm = ref_mean(&R);
    printf("EASU linear: mean %.5f against %.5f (%.2f %%)\n", m, rm, 100 * (m - rm) / rm); CHECK(fabs(m - rm) / rm < 0.03, "EASU linear mean %.5f against %.5f", m, rm); free(o);
    // 6. Another scene size: 1280x720 -> 2048x1152 (the 720p scene of DM_ROMFS_OVERLAY's palette), and the bilinear kernel there
    R = base(1280, 720, 2048, 1152, 1, 0); N = R.ow * R.oh;
    o = run(&R); m = mean(o, N); rm = ref_mean(&R);
    printf("EASU sqrt 1280x720 -> 2048x1152: mean %.5f against %.5f (%.2f %%)\n", m, rm, 100 * (m - rm) / rm);
    CHECK(unwritten(o, N) == 0 && fabs(m - rm) / rm < 0.03, "1280x720: %d not written, mean %.5f against %.5f", unwritten(o, N), m, rm); free(o);
    R.kernel = 1; o = run(&R); e = err_vs_ref(&R, o, 0, R.ow, 0, R.oh, 0.02);
    printf("bilinear sqrt 1280x720 -> 2048x1152: max error %.4f\n", e); CHECK(e < 2 * qtol, "1280x720 bilinear: error %.4f", e); free(o);
    // 7. The packed variant (vka_ndsr.c: an output format without storage support, bound through an R32_UINT view): B10G11R11 words give the float store's picture
    //    (within a rounding step: the float run's own format, or RGBA16F), every texel written; the bilinear kernel against the CPU reference; RGBA8 words
    R = base(1536, 864, 2048, 1152, 1, 0); N = R.ow * R.oh;
    easu = run(&R); R.pack = VKA_NDSR_PACK_B10G11R11; o = run(&R); d = 0;
    for (int i = 0; i < 3 * N; i++) d = fmax(d, fabs(o[i] - easu[i]) / fmax(easu[i], 0.02));
    printf("packed B10G11R11 EASU sqrt 1536x864 -> 2048x1152: max difference to the float store %.5f, %d channels not written\n", d, unwritten(o, N));
    CHECK(unwritten(o, N) == 0 && d < qtol, "packed B10G11R11: %d not written, difference %.5f", unwritten(o, N), d); free(o); free(easu);
    R.kernel = 1; o = run(&R); e = err_vs_ref(&R, o, 0, R.ow, 0, R.oh, 0.02);
    printf("packed B10G11R11 bilinear sqrt: max error %.4f against the CPU reference\n", e); CHECK(e < 2 * qtol, "packed bilinear: error %.4f", e); free(o);
    R = base(1536, 864, 2048, 1152, 0, 1); R.pack = VKA_NDSR_PACK_RGBA8; o = run(&R); e = err_vs_ref(&R, o, 0, R.ow, 0, R.oh, 1.0);
    printf("packed RGBA8 bilinear linear: max absolute error %.4f, %d channels not written\n", e, unwritten(o, N));
    CHECK(unwritten(o, N) == 0 && e < 2.0 / 255, "packed RGBA8: %d not written, error %.4f", unwritten(o, N), e); free(o);
    CHECK(vka_ndsr_pack_of(VK_FORMAT_B10G11R11_UFLOAT_PACK32) == VKA_NDSR_PACK_B10G11R11 && vka_ndsr_pack_of(VK_FORMAT_R8G8B8A8_UNORM) == VKA_NDSR_PACK_RGBA8
          && vka_ndsr_pack_of(VK_FORMAT_E5B9G9R9_UFLOAT_PACK32) == VKA_NDSR_PACK_NONE, "vka_ndsr_pack_of");
    // 8. The binding patch: the module refuses to come without its placeholders, and takes any numbers (both variants)
    for (int pk = 0; pk < 2; pk++) {
        size_t nw; uint32_t *w = vka_ndsr_words(pk, 7, 9, 11, &nw); int found = 0;
        for (size_t i = 5; w && i < nw; i += w[i] >> 16) if ((w[i] & 0xffff) == 71 && (w[i] >> 16) == 4 && w[i + 2] == 33) { uint32_t v = w[i + 3]; found += v == 7 || v == 9 || v == 11; CHECK(v != 100 && v != 101 && v != 102, "placeholder %u left", v); }
        CHECK(w && found == 3, "binding patch (packed %d): %d of 3 bindings set", pk, found); free(w);
    }
    printf(fails ? "FAILED: %d checks\n" : "all checks passed\n", fails);
    return fails ? 1 : 0;
}
