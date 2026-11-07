#pragma once
#if defined(__arm__) && !defined(__aarch64__)
#include <simde/arm/neon.h>
typedef simde_float64x1_t float64x1_t;
typedef simde_float64x2_t float64x2_t;
// Ruy uses bounded int8 quantization. ARMv7 lacks a native ties-to-even conversion.
inline int32x4_t mnxRoundEven(float32x4_t input) {
    const int32x4_t integer = vcvtq_s32_f32(input);
    const float32x4_t fraction = vabsq_f32(vsubq_f32(input, vcvtq_f32_s32(integer)));
    const float32x4_t half = vdupq_n_f32(0.5f);
    const uint32x4_t odd = vtstq_s32(integer, vdupq_n_s32(1));
    const uint32x4_t adjust = vorrq_u32(vcgtq_f32(fraction, half), vandq_u32(vceqq_f32(fraction, half), odd));
    const int32x4_t direction = vorrq_s32(vshrq_n_s32(vreinterpretq_s32_f32(input), 31), vdupq_n_s32(1));
    return vaddq_s32(integer, vandq_s32(vreinterpretq_s32_u32(adjust), direction));
}
#define vcvtnq_s32_f32 mnxRoundEven
#define vcvtnq_u32_f32 simde_vcvtnq_u32_f32
#define vfmaq_n_f32 simde_vfmaq_n_f32
#define vfmaq_f32 simde_vfmaq_f32
#define vfmsq_f32 simde_vfmsq_f32
#define vdivq_f32 simde_vdivq_f32
#define vaddvq_f32 simde_vaddvq_f32
#define vdupq_n_f64 simde_vdupq_n_f64
#define vld1q_f64 simde_vld1q_f64
#define vst1q_f64 simde_vst1q_f64
#define vaddq_f64 simde_vaddq_f64
#define vsubq_f64 simde_vsubq_f64
#define vmulq_f64 simde_vmulq_f64
#define vfmaq_f64 simde_vfmaq_f64
#define vdivq_f64 simde_vdivq_f64
#define vgetq_lane_f64 simde_vgetq_lane_f64
#define vcvt_f64_f32 simde_vcvt_f64_f32
#define vcvt_f32_f64 simde_vcvt_f32_f64
#endif