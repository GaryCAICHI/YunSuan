#include "../include/gm_common.h"

// VecOutput VGMIntegerMAC::get_expected_output(VecInput input)
// {
//   printf("Enter VIntegerMAC\n");
//   VecOutput output;
//   output = get_output_vimac(input);
//   return output;
// }

// VecOutput VGMIntegerMAC::get_output_vimac(VecInput input)
// {
//   return VPUGoldenModel::get_expected_output(input);
// }

ElementOutput VGMIntegerMAC::calculation_e8(ElementInput input) {
  ElementOutput output;
  __uint128_t  src1Unsigned        = (__uint128_t)(uint8_t)input.src1; 
  __uint128_t  src2Unsigned        = (__uint128_t)(uint8_t)input.src2;
  __uint128_t  src3Unsigned        = (__uint128_t)(uint8_t)input.src3;
  __uint128_t  src3WidenedUnsigned = (__uint128_t)(uint16_t)input.src3;
  __int128_t   src1Signed          = (__int128_t)(int8_t)(uint8_t)input.src1;
  __int128_t   src2Signed          = (__int128_t)(int8_t)(uint8_t)input.src2;
  __int128_t   src3Signed          = (__int128_t)(int8_t)(uint8_t)input.src3;
  __int128_t   src3WidenedSigned   = (__int128_t)(int16_t)(uint16_t)input.src3;

  switch(input.fuOpType) {
    case VMUL:  
      output.result = (uint8_t)(src1Signed * src2Signed); break;
    case VWMUL:
      output.result = (uint16_t)(src1Signed * src2Signed); break;
    case VWMULU:
      output.result = (uint16_t)(src1Unsigned * src2Unsigned); break;
    case VWMULSU:
      output.result = (uint16_t)((__int128_t)src1Unsigned * src2Signed); break;
    case VMULH:
      output.result = (uint8_t)((src1Signed * src2Signed) >> 8); break;
    case VMULHU:
      output.result = (uint8_t)((src1Unsigned * src2Unsigned) >> 8); break;
    case VMULHSU:
      output.result = (uint8_t)(((__int128_t)src1Unsigned * src2Signed) >> 8); break;
    case VMACC:
      output.result = (uint8_t)(src1Signed * src2Signed + src3Signed); break;
    case VWMACCU:
      output.result = (uint16_t)(src1Unsigned * src2Unsigned + src3WidenedUnsigned); break;
    case VWMACC:
      output.result = (uint16_t)(src1Signed * src2Signed + src3WidenedSigned); break;
    case VWMACCSU:
      output.result = (uint16_t)(src1Signed * (__int128_t)src2Unsigned + src3WidenedSigned); break;
    case VWMACCUS:
      output.result = (uint16_t)((__int128_t)src1Unsigned * src2Signed + src3WidenedSigned); break;
    case VNMSAC:
      output.result = (uint8_t)(-(src1Signed * src2Signed) + src3Signed); break;
    case VMADD:
      output.result = (uint8_t)(src1Signed * src3Signed + src2Signed); break;
    case VNMSUB:
      output.result = (uint8_t)(-(src1Signed * src3Signed) + src2Signed); break;
    case VSMUL: {
      if (src1Signed == src2Signed && src1Signed == 0x80) {
        output.result = (uint8_t) 0x7F;
        output.vxsat = 1;
      } else {
        uint16_t outputTemp = src1Signed * src2Signed;
        INT_ROUNDING(outputTemp, input.rm_s, 7);
        output.result = (uint8_t)(outputTemp >> 7);
      }
      break;
    } 
    output.fflags = 0;
    if (verbose) { display_calculation(typeid(this).name(), __func__, input, output); }
    printf("Exit VIntegerMAC e8\n");
    return output;
  }
}

ElementOutput VGMIntegerMAC::calculation_e16(ElementInput input) {
  ElementOutput output;
  __uint128_t src1Unsigned        = (__uint128_t)(uint16_t)input.src1;
  __uint128_t src2Unsigned        = (__uint128_t)(uint16_t)input.src2;
  __uint128_t src3Unsigned        = (__uint128_t)(uint16_t)input.src3;
  __uint128_t src3WidenedUnsigned = (__uint128_t)(uint32_t)input.src3;
  __int128_t  src1Signed          = (__int128_t)(int16_t)(uint16_t)input.src1;
  __int128_t  src2Signed          = (__int128_t)(int16_t)(uint16_t)input.src2;
  __int128_t  src3Signed          = (__int128_t)(int16_t)(uint16_t)input.src3;
  __int128_t  src3WidenedSigned   = (__int128_t)(int32_t)(uint32_t)input.src3;

  switch(input.fuOpType) {
    case VMUL:
      output.result = (uint16_t)(src1Signed * src2Signed); break;
    case VWMUL:
      output.result = (uint32_t)(src1Signed * src2Signed); break;
    case VWMULU:
      output.result = (uint32_t)(src1Unsigned * src2Unsigned); break;
    case VWMULSU:
      output.result = (uint32_t)((__int128_t)src1Unsigned * src2Signed); break;
    case VMULH:
      output.result = (uint16_t)((src1Signed * src2Signed) >> 16); break;
    case VMULHU:
      output.result = (uint16_t)((src1Unsigned * src2Unsigned) >> 16); break;
    case VMULHSU:
      output.result = (uint16_t)(((__int128_t)src1Unsigned * src2Signed) >> 16); break;
    case VMACC:
      output.result = (uint16_t)(src1Signed * src2Signed + src3Signed); break;
    case VWMACCU:
      output.result = (uint32_t)(src1Unsigned * src2Unsigned + src3WidenedUnsigned); break;
    case VWMACC:
      output.result = (uint32_t)(src1Signed * src2Signed + src3WidenedSigned); break;
    case VWMACCSU:
      output.result = (uint32_t)(src1Signed * (__int128_t)src2Unsigned + src3WidenedSigned); break;
    case VWMACCUS:
      output.result = (uint32_t)((__int128_t)src1Unsigned * src2Signed + src3WidenedSigned); break;
    case VNMSAC:
      output.result = (uint16_t)(-(src1Signed * src2Signed) + src3Signed); break;
    case VMADD:
      output.result = (uint16_t)(src1Signed * src3Signed + src2Signed); break;
    case VNMSUB:
      output.result = (uint16_t)(-(src1Signed * src3Signed) + src2Signed); break;
    case VSMUL: {
      if (src1Signed == src2Signed && src1Signed == 0x8000) {
        output.result = (uint16_t) 0x7FFF;
        output.vxsat = 1;
      } else {
        uint32_t outputTemp = src1Signed * src2Signed;
        INT_ROUNDING(outputTemp, input.rm_s, 15);
        output.result = (uint16_t)(outputTemp >> 15);
      }
      break;
    }
    output.fflags = 0;
    if (verbose) { display_calculation(typeid(this).name(), __func__, input, output); }
    printf("Exit VIntegerMAC e16\n");
    return output;
  }
}

ElementOutput VGMIntegerMAC::calculation_e32(ElementInput input) {
  ElementOutput output; 
  __uint128_t src1Unsigned        = (__uint128_t)(uint32_t)input.src1;
  __uint128_t src2Unsigned        = (__uint128_t)(uint32_t)input.src2;
  __uint128_t src3Unsigned        = (__uint128_t)(uint32_t)input.src3;
  __uint128_t src3WidenedUnsigned = (__uint128_t)(uint64_t)input.src3;
  __int128_t  src1Signed          = (__int128_t)(int32_t)(uint32_t)input.src1;
  __int128_t  src2Signed          = (__int128_t)(int32_t)(uint32_t)input.src2;
  __int128_t  src3Signed          = (__int128_t)(int32_t)(uint32_t)input.src3;
  __int128_t  src3WidenedSigned   = (__int128_t)(int64_t)(uint64_t)input.src3;
  switch(input.fuOpType) {
    case VMUL:
      output.result = (uint32_t)(src1Signed * src2Signed); break;
    case VWMUL:
      output.result = (uint64_t)(src1Signed * src2Signed); break;
    case VWMULU:
      output.result = (uint64_t)(src1Unsigned * src2Unsigned); break;
    case VWMULSU:
      output.result = (uint64_t)((__int128_t)src1Unsigned * src2Signed); break;
    case VMULH:
      output.result = (uint32_t)((src1Signed * src2Signed) >> 32); break;
    case VMULHU:
      output.result = (uint32_t)((src1Unsigned * src2Unsigned) >> 32); break;
    case VMULHSU:
      output.result = (uint32_t)(((__int128_t)src1Unsigned * src2Signed) >> 32); break;
    case VMACC:
      output.result = (uint32_t)(src1Signed * src2Signed + src3Signed); break;
    case VWMACCU:
      output.result = (uint64_t)(src1Unsigned * src2Unsigned + src3WidenedUnsigned); break;
    case VWMACC:
      output.result = (uint64_t)(src1Signed * src2Signed + src3WidenedSigned); break;
    case VWMACCSU:
      output.result = (uint64_t)(src1Signed * (__int128_t)src2Unsigned + src3WidenedSigned); break;
    case VWMACCUS:
      output.result = (uint64_t)((__int128_t)src1Unsigned * src2Signed + src3WidenedSigned); break;
    case VNMSAC:
      output.result = (uint32_t)(-(src1Signed * src2Signed) + src3Signed); break;
    case VMADD:
      output.result = (uint32_t)(src1Signed * src3Signed + src2Signed); break;
    case VNMSUB:
      output.result = (uint32_t)(-(src1Signed * src3Signed) + src2Signed); break;
    case VSMUL: {
      if (src1Signed == src2Signed && src1Signed == 0x80000000) {
        output.result = (uint32_t) 0x7FFFFFFF;
        output.vxsat = 1;
      } else {
        uint64_t outputTemp = src1Signed * src2Signed;
        INT_ROUNDING(outputTemp, input.rm_s, 31);
        output.result = (uint32_t)(outputTemp >> 31);
      }
      break;
    }
    output.fflags = 0;
    if (verbose) { display_calculation(typeid(this).name(), __func__, input, output); }
    printf("Exit VIntegerMAC e32\n");
    return output;
  }
}

ElementOutput VGMIntegerMAC::calculation_e64(ElementInput input) {
  ElementOutput output;
  __uint128_t src1Unsigned = (__uint128_t)(uint64_t)input.src1;
  __uint128_t src2Unsigned = (__uint128_t)(uint64_t)input.src2;
  __uint128_t src3Unsigned = (__uint128_t)(uint64_t)input.src3;
  __int128_t  src1Signed   = (__int128_t)(int64_t)(uint64_t)input.src1;
  __int128_t  src2Signed   = (__int128_t)(int64_t)(uint64_t)input.src2;
  __int128_t  src3Signed   = (__int128_t)(int64_t)(uint64_t)input.src3;

  switch (input.fuOpType) {
    case VMUL:
      output.result = (uint64_t)(src1Signed * src2Signed); break;
    case VMULH:
      output.result = (uint64_t)((src1Signed * src2Signed) >> 64); break;
    case VMULHU:
      output.result = (uint64_t)((src1Unsigned * src2Unsigned) >> 64); break;
    case VMULHSU:
      output.result = (uint64_t)(((__int128_t)src1Unsigned * src2Signed) >> 64); break;
    case VMACC:
      output.result = (uint64_t)(src1Signed * src2Signed + src3Signed); break;
    case VNMSAC:
      output.result = (uint64_t)(-(src1Signed * src2Signed) + src3Signed); break;
    case VMADD:
      output.result = (uint64_t)(src1Signed * src3Signed + src2Signed); break;
    case VNMSUB:
      output.result = (uint64_t)(-(src1Signed * src3Signed) + src2Signed); break;
    case VSMUL: {
      if (src1Signed == src2Signed && src1Signed == 0x8000000000000000) {
        output.result = (uint64_t) 0x7FFFFFFFFFFFFFFF;
        output.vxsat = 1;
      } else {
        __int128_t outputTemp = (__int128_t)src1Signed * (__int128_t)src2Signed;
        INT_ROUNDING(outputTemp, input.rm_s, 63);
        output.result = (uint64_t)(outputTemp >> 63);
      }
      break;
    }
    output.fflags = 0;
    if (verbose) { display_calculation(typeid(this).name(), __func__, input, output); }
    printf("Exit VIntegerMAC e64\n");
    return output;
  }
}

